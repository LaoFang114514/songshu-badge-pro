package com.laofang.songshushoupaipro.songshu.core

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import com.laofang.songshushoupaipro.songshu.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

data class McpConfig(
    val enabled: Boolean,
    val port: Int,
    val token: String
)

/**
 * 应用内 HTTP MCP 服务器。AI 客户端通过
 * http://<手机局域网 IP>:<端口>/mcp + Authorization: Bearer <token>
 * 即可新建/编辑/删除兽牌及其文件。
 */
object McpServer {
    private const val PREFS_NAME = "mcp_settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PORT = "port"
    private const val KEY_TOKEN = "token"
    private const val DEFAULT_PORT = 8765
    private const val MAX_TEXT_BYTES = 4L * 1024 * 1024
    private const val MAX_BINARY_BYTES = 16L * 1024 * 1024
    private const val MAX_REQUEST_BYTES = 18 * 1024 * 1024
    private const val MAX_LINE_BYTES = 8192
    private const val MAX_HEADER_BYTES = 64 * 1024
    private val secureRandom = SecureRandom()

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var lastError: String? = null
        private set

    private var appContext: Context? = null
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    @Volatile private var clientExecutor: ThreadPoolExecutor? = null
    private var token: String = ""
    private val sessionId: String = UUID.randomUUID().toString()

    private val MUTATING_TOOLS = setOf(
        "create_badge", "delete_badge", "rename_badge", "set_badge_icon",
        "set_selected_badge", "write_file", "write_binary_file",
        "delete_file", "rename_file"
    )

    /** 支持的 MCP 协议版本，按从新到旧排列 */
    private val SUPPORTED_PROTOCOLS = listOf("2025-06-18", "2025-03-26", "2024-11-05")

    private const val SERVER_INSTRUCTIONS =
        "松鼠兽牌 PRO 内置 MCP 服务，用于管理网页兽牌（普通网站的 HTML/CSS/JS 目录）。" +
        "可列出/新建/重命名/删除兽牌，读写/删除/重命名文件，切换当前展示兽牌；" +
        "get_browser_info 可查询系统 WebView 内核信息。兽牌入口文件固定为 index.html。"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadConfig(ctx: Context): McpConfig {
        val p = prefs(ctx)
        var savedToken = p.getString(KEY_TOKEN, "") ?: ""
        if (savedToken.length != 32) {
            savedToken = generateToken()
            p.edit { putString(KEY_TOKEN, savedToken) }
        }
        return McpConfig(
            enabled = p.getBoolean(KEY_ENABLED, false),
            port = p.getInt(KEY_PORT, DEFAULT_PORT),
            token = savedToken
        )
    }

    fun saveConfig(ctx: Context, config: McpConfig) {
        prefs(ctx).edit {
            putBoolean(KEY_ENABLED, config.enabled)
            putInt(KEY_PORT, config.port)
            putString(KEY_TOKEN, config.token)
        }
    }

    fun regenerateToken(ctx: Context): String {
        val newToken = generateToken()
        prefs(ctx).edit { putString(KEY_TOKEN, newToken) }
        return newToken
    }

    /** 返回 null 表示启动成功，否则返回错误描述 */
    @Synchronized
    fun start(ctx: Context, port: Int, accessToken: String): String? {
        if (isRunning) return null
        appContext = ctx.applicationContext
        token = accessToken
        lastError = null
        return try {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(port))
            serverSocket = socket
            isRunning = true
            val executor = ThreadPoolExecutor(4, 4, 30L, TimeUnit.SECONDS, java.util.concurrent.ArrayBlockingQueue(32)).apply {
                threadFactory = Executors.defaultThreadFactory().let { factory ->
                    java.util.concurrent.ThreadFactory { task -> factory.newThread(task).apply { isDaemon = true } }
                }
            }
            clientExecutor = executor
            acceptThread = Thread({
                while (isRunning) {
                    val client = try {
                        socket.accept()
                    } catch (_: Exception) {
                        break
                    }
                    try {
                        executor.execute {
                            try {
                                handleClient(client)
                            } catch (_: Throwable) {
                            } finally {
                                try { client.close() } catch (_: Exception) {}
                            }
                        }
                    } catch (_: java.util.concurrent.RejectedExecutionException) {
                        try { client.close() } catch (_: Exception) {}
                    }
                }
            }, "mcp-accept").apply { isDaemon = true; start() }
            null
        } catch (e: Exception) {
            isRunning = false
            serverSocket = null
            lastError = e.localizedMessage ?: e.javaClass.simpleName
            lastError
        }
    }

    @Synchronized
    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        clientExecutor?.shutdownNow()
        serverSocket = null
        clientExecutor = null
        acceptThread = null
    }

    fun localIp(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            val candidates = Collections.list(interfaces).filter { it.isUp && !it.isLoopback }
            val ordered = candidates.sortedBy { if (it.name.startsWith("wlan", true)) 0 else 1 }
            for (ni in ordered) {
                for (addr in Collections.list(ni.inetAddresses)) {
                    val host = addr.hostAddress ?: continue
                    if (!addr.isLoopbackAddress && !host.contains(':')) return host
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun generateToken(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        return (1..32).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
    }

    private fun tokenMatches(expected: String, provided: String?): Boolean {
        if (expected.isBlank() || provided == null) return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            provided.toByteArray(Charsets.UTF_8)
        )
    }

    private fun handleClient(client: java.net.Socket) {
        client.soTimeout = 15000
        val input = BufferedInputStream(client.getInputStream())
        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2) return
        val method = parts[0].uppercase(Locale.ROOT)
        val rawPath = parts[1]
        val path = rawPath.substringBefore('?')
        val query = rawPath.substringAfter('?', "")
        val headers = HashMap<String, String>()
        var headerBytes = 0
        var headerCount = 0
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            headerBytes += line.length
            if (headerBytes > MAX_HEADER_BYTES || ++headerCount > 100) {
                respondJson(client, 431, errorBody(null, -32600, "Headers too large").toString())
                return
            }
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim().lowercase(Locale.ROOT)] = line.substring(idx + 1).trim()
        }

        if (method == "OPTIONS") {
            respondEmpty(client, 204)
            return
        }
        if (path != "/mcp" && path != "/mcp/") {
            respondJson(client, 404, errorBody(null, -32600, "Not found").toString())
            return
        }

        val provided = headers["authorization"]?.removePrefix("Bearer ")?.trim()
            ?: headers["x-mcp-token"]
            ?: query.split('&').firstOrNull { it.startsWith("token=") }?.substringAfter("token=")
        if (token.isNotBlank() && !tokenMatches(token, provided)) {
            respondJson(client, 401, errorBody(null, -32600, "Unauthorized").toString())
            return
        }

        if (method == "GET") {
            // Streamable HTTP 的 GET 用于服务端推送；这里没有推送，返回空 SSE 流
            respondSse(client, null, empty = true)
            return
        }
        if (method != "POST") {
            respondJson(client, 405, errorBody(null, -32600, "Method not allowed").toString())
            return
        }

        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length <= 0) {
            respondJson(client, 411, errorBody(null, -32600, "Content-Length required").toString())
            return
        }
        if (length > MAX_REQUEST_BYTES) {
            respondJson(client, 413, errorBody(null, -32600, "Request body too large").toString())
            return
        }
        val bodyBytes = readExact(input, length) ?: return
        val body = String(bodyBytes, Charsets.UTF_8)
        val acceptSse = headers["accept"]?.contains("text/event-stream") == true

        val response: String? = try {
            when (val parsed = parseJson(body)) {
                is JSONArray -> {
                    // JSON-RPC 批量请求：逐个处理，返回响应数组
                    val out = JSONArray()
                    for (i in 0 until parsed.length()) {
                        parsed.optJSONObject(i)?.let { item -> handleRpc(item)?.let { out.put(it) } }
                    }
                    if (out.length() == 0) null else out.toString()
                }
                is JSONObject -> handleRpc(parsed)?.toString()
                else -> errorBody(null, -32700, "Parse error").toString()
            }
        } catch (e: Exception) {
            errorBody(null, -32700, e.localizedMessage ?: "Parse error").toString()
        }

        if (response == null) {
            respondEmpty(client, 202)
        } else if (acceptSse) {
            respondSse(client, response)
        } else {
            respondJson(client, 200, response)
        }
    }

    private fun parseJson(body: String): Any {
        val trimmed = body.trimStart()
        return if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
    }

    private fun handleRpc(req: JSONObject): JSONObject? {
        val id = req.opt("id")
        val method = req.optString("method", "")
        val params = req.optJSONObject("params") ?: JSONObject()

        // 通知消息没有 id，不需要返回体
        if (id == null || id == JSONObject.NULL) {
            return when (method) {
                "notifications/initialized", "notifications/cancelled", "notifications/roots/list_changed" -> null
                else -> null
            }
        }

        val result: JSONObject = when (method) {
            "initialize" -> JSONObject().apply {
                val requested = params.optString("protocolVersion", "")
                put("protocolVersion", if (requested in SUPPORTED_PROTOCOLS) requested else SUPPORTED_PROTOCOLS.first())
                put("capabilities", JSONObject().apply {
                    put("tools", JSONObject().apply { put("listChanged", false) })
                })
                put("serverInfo", JSONObject().apply {
                    put("name", "songshu-badge-pro")
                    put("version", BuildConfig.VERSION_NAME)
                })
                put("instructions", SERVER_INSTRUCTIONS)
            }
            "ping" -> JSONObject()
            "tools/list" -> JSONObject().put("tools", toolDefinitions())
            "tools/call" -> {
                val name = params.optString("name", "")
                val args = params.optJSONObject("arguments") ?: JSONObject()
                val call = callTool(name, args)
                if (!call.isError && name in MUTATING_TOOLS) DataEvents.bump()
                JSONObject().put("content", call.content).put("isError", call.isError)
            }
            "resources/list" -> JSONObject().put("resources", JSONArray())
            "prompts/list" -> JSONObject().put("prompts", JSONArray())
            else -> return errorBody(id, -32601, "Method not found: $method")
        }
        return JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("result", result)
        }
    }

    private data class ToolResult(val content: JSONArray, val isError: Boolean)

    /** 网络检测：TCP 连接测延迟；省略参数时跑默认组。 */
    private fun toolNetPing(args: JSONObject): String {
        val host = args.optString("host", "").trim()
        val port = args.optInt("port", 0)
        val results = if (host.isNotBlank()) {
            listOf(NetProbe.ping(host, if (port in 1..65535) port else 443))
        } else {
            NetProbe.pingDefaults()
        }
        return NetProbe.toJson(results).toString(2)
    }

    /** 变更工具成功后，记录对应兽牌的编辑时间，供 UI 显示"MCP服务编辑中"。 */
    private fun markEditedBadge(ctx: Context, tool: String, args: JSONObject) {
        val id = when (tool) {
            "create_badge" -> {
                // create_badge 返回的新 id 在 result 文本里，从管理器按名取回
                val name = args.optString("name")
                WebBadgeManager.getBadges(ctx).firstOrNull { it.name == name }?.id
            }
            "delete_badge", "rename_badge", "set_badge_icon", "set_selected_badge" -> args.optString("id")
            else -> args.optString("badge_id")
        }
        if (!id.isNullOrBlank()) DataEvents.markEditing(id)
    }

    @Synchronized
    private fun callTool(name: String, args: JSONObject): ToolResult {
        val ctx = appContext ?: return ToolResult(contentText("服务器未就绪"), true)
        return try {
            val text = when (name) {
                "list_badges" -> toolListBadges(ctx)
                "create_badge" -> toolCreateBadge(ctx, args)
                "delete_badge" -> toolDeleteBadge(ctx, args)
                "rename_badge" -> toolRenameBadge(ctx, args)
                "set_badge_icon" -> toolSetIcon(ctx, args)
                "set_selected_badge" -> toolSetSelected(ctx, args)
                "list_files" -> toolListFiles(ctx, args)
                "read_file" -> toolReadFile(ctx, args)
                "write_file" -> toolWriteFile(ctx, args, binary = false)
                "write_binary_file" -> toolWriteFile(ctx, args, binary = true)
                "delete_file" -> toolDeleteFile(ctx, args)
                "rename_file" -> toolRenameFile(ctx, args)
                "get_browser_info" -> WebViewInfo.toJson(WebViewInfo.load(ctx)).toString(2)
                "net_ping" -> toolNetPing(args)
                else -> throw IllegalArgumentException("未知工具: $name")
            }
            if (name in MUTATING_TOOLS) markEditedBadge(ctx, name, args)
            ToolResult(contentText(text), false)
        } catch (e: Exception) {
            ToolResult(contentText(e.localizedMessage ?: e.javaClass.simpleName), true)
        }
    }

    private fun toolListBadges(ctx: Context): String {
        val badges = WebBadgeManager.getBadges(ctx)
        val selected = WebBadgeManager.getSelectedIndex(ctx)
        val arr = JSONArray()
        badges.forEachIndexed { index, badge ->
            arr.put(JSONObject().apply {
                put("id", badge.id)
                put("name", badge.name)
                put("selected", index == selected)
                put("icon_index", badge.iconIndex)
                put("has_index", badge.hasIndex)
                put("files", WebBadgeManager.listFiles(badge.folder).size)
            })
        }
        return arr.toString(2)
    }

    private fun toolCreateBadge(ctx: Context, args: JSONObject): String {
        val name = args.optString("name", "").trim()
        if (name.isBlank()) throw IllegalArgumentException("缺少 name")
        val html = args.optString("html", "")
        val icon = args.optInt("icon_index", 0).coerceIn(0, 19)
        val (badge, folder) = WebBadgeManager.newBadgeFolder(ctx, name)
        val content = html.ifBlank { defaultBadgeHtml(name) }
        WebBadgeManager.writeText(File(folder, WebBadgeManager.INDEX_FILE), content)
        WebBadgeManager.registerBadge(ctx, badge.copy(iconIndex = icon))
        if (args.has("selected") && args.optBoolean("selected", false)) {
            val idx = WebBadgeManager.badgeIndex(ctx, badge.id)
            if (idx >= 0) WebBadgeManager.setSelectedIndex(ctx, idx)
        }
        return JSONObject().put("id", badge.id).put("name", name).put("icon_index", icon).toString(2)
    }

    private fun toolDeleteBadge(ctx: Context, args: JSONObject): String {
        val id = requireId(args)
        if (WebBadgeManager.findBadge(ctx, id) == null) throw IllegalArgumentException("兽牌不存在: $id")
        WebBadgeManager.deleteBadge(ctx, id)
        return "已删除兽牌 $id"
    }

    private fun toolRenameBadge(ctx: Context, args: JSONObject): String {
        val id = requireId(args)
        val name = args.optString("name", "").trim()
        if (name.isBlank()) throw IllegalArgumentException("缺少 name")
        WebBadgeManager.renameBadge(ctx, id, name)
        return "已重命名为 $name"
    }

    private fun toolSetIcon(ctx: Context, args: JSONObject): String {
        val id = requireId(args)
        val icon = args.optInt("icon_index", -1)
        if (icon < 0 || icon > 19) throw IllegalArgumentException("icon_index 需在 0..19")
        WebBadgeManager.updateIcon(ctx, id, icon)
        return "图标已设置为 $icon"
    }

    private fun toolSetSelected(ctx: Context, args: JSONObject): String {
        val id = requireId(args)
        val idx = WebBadgeManager.badgeIndex(ctx, id)
        if (idx < 0) throw IllegalArgumentException("兽牌不存在: $id")
        WebBadgeManager.setSelectedIndex(ctx, idx)
        return "已选中 $id"
    }

    private fun toolListFiles(ctx: Context, args: JSONObject): String {
        val badge = requireBadge(ctx, args)
        val files = WebBadgeManager.listFiles(badge.folder)
        val arr = JSONArray()
        files.forEach { f ->
            arr.put(JSONObject().apply {
                put("path", f.relativePath)
                put("size", f.size)
                put("text", f.isText)
            })
        }
        return arr.toString(2)
    }

    private fun toolReadFile(ctx: Context, args: JSONObject): String {
        val badge = requireBadge(ctx, args)
        val file = safeFile(badge.folder, requirePath(args), createParent = false)
        if (!file.isFile) throw IllegalArgumentException("文件不存在: ${file.name}")
        if (file.length() > MAX_TEXT_BYTES) throw IllegalArgumentException("文件超过 4 MB")
        return WebBadgeManager.readText(file)
    }

    private fun toolWriteFile(ctx: Context, args: JSONObject, binary: Boolean): String {
        val badge = requireBadge(ctx, args)
        val file = safeFile(badge.folder, requirePath(args), createParent = true)
        if (binary) {
            val base64 = args.optString("base64", "")
            if (base64.isBlank()) throw IllegalArgumentException("缺少 base64")
            val bytes = try { Base64.decode(base64, Base64.DEFAULT) } catch (_: Exception) {
                throw IllegalArgumentException("base64 无效")
            }
            if (bytes.size > MAX_BINARY_BYTES) throw IllegalArgumentException("二进制文件超过 16 MB")
            file.writeBytes(bytes)
        } else {
            val content = args.optString("content", "")
            if (content.toByteArray().size > MAX_TEXT_BYTES) throw IllegalArgumentException("文本超过 4 MB")
            WebBadgeManager.writeText(file, content)
        }
        return JSONObject().put("path", file.relativeTo(badge.folder).invariantSeparatorsPath)
            .put("size", file.length()).toString(2)
    }

    private fun toolDeleteFile(ctx: Context, args: JSONObject): String {
        val badge = requireBadge(ctx, args)
        val path = requirePath(args)
        if (path == WebBadgeManager.INDEX_FILE) throw IllegalArgumentException("index.html 不能删除")
        WebBadgeManager.deleteFile(badge.folder, path).getOrThrow()
        return "已删除 $path"
    }

    private fun toolRenameFile(ctx: Context, args: JSONObject): String {
        val badge = requireBadge(ctx, args)
        val path = requirePath(args)
        val newName = args.optString("new_name", "").trim()
        if (newName.isBlank()) throw IllegalArgumentException("缺少 new_name")
        val res = WebBadgeManager.renameFile(badge.folder, path, newName)
        return res.getOrThrow()
    }

    private fun requireId(args: JSONObject): String {
        val id = args.optString("id", args.optString("badge_id", "")).trim()
        if (id.isBlank()) throw IllegalArgumentException("缺少 id")
        return id
    }

    private fun requireBadge(ctx: Context, args: JSONObject): WebBadge {
        val id = requireId(args)
        return WebBadgeManager.findBadge(ctx, id) ?: throw IllegalArgumentException("兽牌不存在: $id")
    }

    private fun requirePath(args: JSONObject): String {
        val path = args.optString("path", "").trim()
        if (path.isBlank()) throw IllegalArgumentException("缺少 path")
        return path
    }

    private fun safeFile(folder: File, rawPath: String, createParent: Boolean): File {
        val cleaned = rawPath.replace('\\', '/').trimStart('/')
        if (cleaned.isBlank() || cleaned.contains("..")) throw IllegalArgumentException("非法路径")
        val target = File(folder, cleaned)
        if (!target.canonicalPath.startsWith(folder.canonicalPath + File.separator)) {
            throw IllegalArgumentException("路径超出兽牌目录")
        }
        if (createParent) target.parentFile?.mkdirs()
        return target
    }

    private fun defaultBadgeHtml(name: String): String {
        val safe = name.replace("<", "&lt;").replace(">", "&gt;")
        // 默认模板按多行缩进生成，方便在编辑器里直接阅读和修改
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <title>$safe</title>
              <style>
                html, body {
                  margin: 0;
                  height: 100%;
                }
                body {
                  display: grid;
                  place-items: center;
                  background: #0b1220;
                  color: #eaf4ff;
                  font-family: sans-serif;
                }
                h1 {
                  margin: 0;
                  font-size: 40px;
                }
              </style>
            </head>
            <body>
              <h1>$safe</h1>
            </body>
            </html>
        """.trimIndent()
    }

    private fun contentText(text: String): JSONArray =
        JSONArray().put(JSONObject().apply {
            put("type", "text")
            put("text", text)
        })

    private fun toolDefinitions(): JSONArray = JSONArray().apply {
        put(tool(
            "list_badges",
            "列出所有兽牌（id、名称、是否当前使用、图标、文件数量）",
            emptyMap(),
            emptyList(),
            toolAnnotations(readOnly = true)
        ))
        put(tool("create_badge", "新建一个网页兽牌；html 会按原样写入（保留换行与缩进），省略时用多行默认模板", mapOf(
            "name" to strProp("兽牌名称"),
            "html" to strProp("index.html 内容，可省略"),
            "icon_index" to intProp("图标索引 0-19，可省略"),
            "selected" to boolProp("是否设为当前使用")
        ), listOf("name"), toolAnnotations()))
        put(tool("delete_badge", "删除一个兽牌及其整个文件夹", mapOf("id" to strProp("兽牌 id")), listOf("id"), toolAnnotations(destructive = true)))
        put(tool("rename_badge", "修改兽牌显示名称", mapOf("id" to strProp("兽牌 id"), "name" to strProp("新名称")), listOf("id", "name"), toolAnnotations(idempotent = true)))
        put(tool("set_badge_icon", "修改兽牌图标", mapOf("id" to strProp("兽牌 id"), "icon_index" to intProp("0-19")), listOf("id", "icon_index"), toolAnnotations(idempotent = true)))
        put(tool("set_selected_badge", "设为当前展示的兽牌", mapOf("id" to strProp("兽牌 id")), listOf("id"), toolAnnotations(idempotent = true)))
        put(tool("list_files", "列出兽牌文件夹里的文件", mapOf("badge_id" to strProp("兽牌 id")), listOf("badge_id"), toolAnnotations(readOnly = true)))
        put(tool("read_file", "读取兽牌文件夹里的文本文件", mapOf("badge_id" to strProp("兽牌 id"), "path" to strProp("相对路径")), listOf("badge_id", "path"), toolAnnotations(readOnly = true)))
        put(tool("write_file", "写入文本文件（不存在会自动创建，可含子目录；内容按原样保存，保留换行）", mapOf(
            "badge_id" to strProp("兽牌 id"),
            "path" to strProp("相对路径，如 index.html / css/style.css"),
            "content" to strProp("UTF-8 文本内容")
        ), listOf("badge_id", "path", "content"), toolAnnotations(idempotent = true)))
        put(tool("write_binary_file", "以 base64 写入图片/字体等二进制文件", mapOf(
            "badge_id" to strProp("兽牌 id"),
            "path" to strProp("相对路径"),
            "base64" to strProp("base64 内容")
        ), listOf("badge_id", "path", "base64"), toolAnnotations(idempotent = true)))
        put(tool("delete_file", "删除兽牌文件夹里的文件（index.html 受保护）", mapOf(
            "badge_id" to strProp("兽牌 id"),
            "path" to strProp("相对路径")
        ), listOf("badge_id", "path"), toolAnnotations(destructive = true)))
        put(tool("rename_file", "重命名兽牌文件夹里的文件", mapOf(
            "badge_id" to strProp("兽牌 id"),
            "path" to strProp("相对路径"),
            "new_name" to strProp("新文件名")
        ), listOf("badge_id", "path", "new_name"), toolAnnotations(idempotent = true)))
        put(tool(
            "get_browser_info",
            "查看内置浏览器（Android WebView）的详细信息：内核提供方包名与版本、Chromium 版本、User-Agent、系统 SDK、是否需要更新等",
            emptyMap(),
            emptyList(),
            toolAnnotations(readOnly = true)
        ))
        put(tool(
            "net_ping",
            "网络连通检测（TCP 握手近似 ping）。不带参数时检测默认组（阿里/腾讯 DNS、百度、系统连通性探测点）；带 host（和可选 port）时检测指定目标",
            mapOf(
                "host" to strProp("目标主机名或 IP，可省略"),
                "port" to intProp("端口，默认 443，可省略")
            ),
            emptyList(),
            toolAnnotations(readOnly = true)
        ))
    }

    private fun tool(
        name: String,
        desc: String,
        props: Map<String, JSONObject>,
        required: List<String>,
        annotations: JSONObject? = null
    ): JSONObject {
        val propsJson = JSONObject()
        props.forEach { (k, v) -> propsJson.put(k, v) }
        return JSONObject().apply {
            put("name", name)
            put("description", desc)
            put("inputSchema", JSONObject().apply {
                put("type", "object")
                put("properties", propsJson)
                put("required", JSONArray(required))
            })
            annotations?.let { put("annotations", it) }
        }
    }

    private fun toolAnnotations(
        readOnly: Boolean = false,
        destructive: Boolean = false,
        idempotent: Boolean = false,
        openWorld: Boolean = false
    ): JSONObject = JSONObject().apply {
        put("readOnlyHint", readOnly)
        put("destructiveHint", destructive)
        put("idempotentHint", idempotent)
        put("openWorldHint", openWorld)
    }

    private fun strProp(desc: String) = JSONObject().put("type", "string").put("description", desc)
    private fun intProp(desc: String) = JSONObject().put("type", "integer").put("description", desc)
    private fun boolProp(desc: String) = JSONObject().put("type", "boolean").put("description", desc)

    private fun errorBody(id: Any?, code: Int, message: String): JSONObject = JSONObject().apply {
        put("jsonrpc", "2.0")
        if (id != null) put("id", id)
        put("error", JSONObject().apply {
            put("code", code)
            put("message", message)
        })
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            if (sb.length >= MAX_LINE_BYTES) throw java.io.IOException("HTTP line too large")
            sb.append(b.toChar())
        }
    }

    private fun readExact(input: InputStream, length: Int): ByteArray? {
        val arr = ByteArray(length)
        var readTotal = 0
        while (readTotal < length) {
            val r = input.read(arr, readTotal, length - readTotal)
            if (r <= 0) return null
            readTotal += r
        }
        return arr
    }

    private fun respondEmpty(client: java.net.Socket, code: Int) {
        val msg = when (code) { 202 -> "Accepted"; 204 -> "No Content"; else -> "OK" }
        val header = "HTTP/1.1 $code $msg\r\nContent-Length: 0\r\nConnection: close\r\n" +
                "Access-Control-Allow-Origin: *\r\nAccess-Control-Allow-Headers: Authorization, Content-Type, Mcp-Session-Id, X-MCP-Token\r\n" +
                "Access-Control-Allow-Methods: POST, GET, OPTIONS\r\nMcp-Session-Id: $sessionId\r\n\r\n"
        val out = client.getOutputStream()
        out.write(header.toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    private fun respondJson(client: java.net.Socket, code: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val msg = if (code == 200) "OK" else "Error"
        val header = "HTTP/1.1 $code $msg\r\nContent-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\nConnection: close\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Headers: Authorization, Content-Type, Mcp-Session-Id, X-MCP-Token\r\n" +
                "Access-Control-Allow-Methods: POST, GET, OPTIONS\r\nMcp-Session-Id: $sessionId\r\n\r\n"
        val out = client.getOutputStream()
        out.write(header.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun respondSse(client: java.net.Socket, body: String?, empty: Boolean = false) {
        val header = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream; charset=utf-8\r\n" +
                "Cache-Control: no-cache\r\nConnection: close\r\n" +
                "Access-Control-Allow-Origin: *\r\nMcp-Session-Id: $sessionId\r\n\r\n"
        val out = client.getOutputStream()
        out.write(header.toByteArray(Charsets.US_ASCII))
        if (!empty && body != null) {
            out.write("event: message\ndata: $body\n\n".toByteArray(Charsets.UTF_8))
        }
        out.flush()
    }
}
