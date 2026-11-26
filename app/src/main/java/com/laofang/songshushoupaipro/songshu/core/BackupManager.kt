package com.laofang.songshushoupaipro.songshu.core

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class WebDavConfig(val url: String = "", val username: String = "", val password: String = "")

object BackupManager {
    private const val MAX_BYTES = 256L * 1024 * 1024
    private const val BACKUP_FILE_NAME = "songshu-badge-pro_backup.zip"

    const val ERR_AUTH_FAIL = "auth_fail"
    const val ERR_NO_PERMISSION = "no_permission"
    const val ERR_CONNECTION_FAILED = "conn_failed"
    const val ERR_NETWORK_ERROR = "network_error"
    const val ERR_UPLOAD_AUTH_FAIL = "upload_auth_fail"
    const val ERR_UPLOAD_NO_PERMISSION = "upload_no_permission"
    const val ERR_UPLOAD_FAILED = "upload_failed"
    const val ERR_UPLOAD_FAILED_SIMPLE = "upload_failed_simple"
    const val ERR_REDIRECT_NO_LOCATION = "redirect_no_location"
    const val ERR_BACKUP_NOT_FOUND = "backup_not_found"
    const val ERR_DOWNLOAD_NO_PERMISSION = "download_no_permission"
    const val ERR_SERVER_RESPONSE = "server_response"
    const val ERR_NO_CONFIG_IN_ZIP = "no_config_in_zip"
    const val ERR_UNKNOWN_ERROR = "unknown_error"

    init {
        CookieHandler.setDefault(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) })
    }

    fun totalDataSize(ctx: Context): Long {
        val badges = WebBadgeManager.getBadges(ctx)
        return badges.sumOf { WebBadgeManager.folderSize(it.folder) }
    }

    // ── WebDAV 配置 ─────────────────────────────────────────────

    fun saveWebDavConfig(ctx: Context, cfg: WebDavConfig) {
        ctx.getSharedPreferences("backup_config", Context.MODE_PRIVATE).edit {
            putString("webdav_url", cfg.url)
            putString("webdav_user", cfg.username)
            putString("webdav_pass", cfg.password)
        }
    }

    fun loadWebDavConfig(ctx: Context): WebDavConfig {
        val p = ctx.getSharedPreferences("backup_config", Context.MODE_PRIVATE)
        return WebDavConfig(
            p.getString("webdav_url", "") ?: "",
            p.getString("webdav_user", "") ?: "",
            p.getString("webdav_pass", "") ?: ""
        )
    }

    // ── 本地导出 / 导入 ─────────────────────────────────────────

    /** 返回 null 表示成功，否则为错误描述 */
    fun exportToZip(ctx: Context, uri: Uri): String? {
        return try {
            ctx.contentResolver.openOutputStream(uri)?.use { stream ->
                writeBackupZip(ctx, stream)
            } ?: "无法写入所选位置"
            null
        } catch (e: Exception) {
            e.localizedMessage ?: e.javaClass.simpleName
        }
    }

    private fun writeBackupZip(ctx: Context, stream: OutputStream) {
        ZipOutputStream(stream.buffered()).use { zos ->
            val config = JSONObject().apply {
                put("settings", SettingsManager.exportJson(ctx))
                put("selected", WebBadgeManager.getSelectedIndex(ctx))
                put("badges", JSONArray().apply {
                    WebBadgeManager.getBadges(ctx).forEach { b ->
                        put(JSONObject().apply {
                            put("id", b.id)
                            put("name", b.name)
                            put("icon", b.iconIndex)
                        })
                    }
                })
            }
            zos.putNextEntry(java.util.zip.ZipEntry("config.json"))
            zos.write(config.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            WebBadgeManager.getBadges(ctx).forEach { badge ->
                val folder = badge.folder
                folder.walkTopDown().forEach { f ->
                    if (!f.isFile) return@forEach
                    if (f.name == WebBadgeManager.THUMB_FILE) return@forEach
                    val rel = f.relativeTo(folder).invariantSeparatorsPath
                    zos.putNextEntry(java.util.zip.ZipEntry("web_badges/${badge.id}/$rel"))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    /** 返回 null 表示成功，否则为错误描述 */
    fun importFromZip(ctx: Context, uri: Uri): String? {
        return try {
            val input = ctx.contentResolver.openInputStream(uri)
                ?: return "无法读取备份文件"
            input.use { importFromStream(ctx, it) }
        } catch (e: Exception) {
            e.localizedMessage ?: e.javaClass.simpleName
        }
    }

    /** 返回 null 表示成功，否则为错误描述 */
    fun importFromFile(ctx: Context, file: File): String? {
        return try {
            file.inputStream().use { importFromStream(ctx, it) }
        } catch (e: Exception) {
            e.localizedMessage ?: e.javaClass.simpleName
        }
    }

    private fun importFromStream(ctx: Context, input: InputStream): String? {
        val temp = File(ctx.cacheDir, "backup_restore_${System.currentTimeMillis()}")
        var stagedRoot: File? = null
        return try {
            temp.mkdirs()
            var total = 0L
            var count = 0
            ZipInputStream(input.buffered()).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val name = entry.name.replace('\\', '/')
                    val target = File(temp, name)
                    if (!target.canonicalPath.startsWith(temp.canonicalPath + File.separator)) {
                        zis.closeEntry(); continue
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        count++
                        if (count > 5000) error("备份包含的文件过多")
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { out ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val read = zis.read(buffer)
                                if (read <= 0) break
                                total += read
                                if (total > MAX_BYTES) error("备份文件过大")
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                    zis.closeEntry()
                }
            }

            val configFile = File(temp, "config.json")
            if (!configFile.isFile) return ERR_NO_CONFIG_IN_ZIP
            if (configFile.length() > 1024 * 1024) error("备份配置过大")

            val config = JSONObject(configFile.readText(Charsets.UTF_8))
            val settings = config.optJSONObject("settings")
            val badgeArray = config.optJSONArray("badges") ?: JSONArray()
            if (badgeArray.length() > 1000) error("兽牌数量过多")
            val selected = config.optInt("selected", 0)
            val sourceRoot = File(temp, "web_badges").canonicalFile
            val items = ArrayList<Triple<String, String, Int>>()
            val ids = HashSet<String>()

            for (i in 0 until badgeArray.length()) {
                val obj = badgeArray.optJSONObject(i) ?: error("兽牌配置格式错误")
                val id = obj.optString("id", "")
                if (!isSafeBadgeId(id)) error("兽牌标识不合法")
                if (!ids.add(id)) error("兽牌标识重复")
                val source = File(sourceRoot, id).canonicalFile
                if (source.parentFile != sourceRoot || !source.isDirectory ||
                    !File(source, WebBadgeManager.INDEX_FILE).isFile
                ) error("兽牌数据不完整: $id")
                items.add(Triple(id, obj.optString("name", id), obj.optInt("icon", 0).coerceIn(0, 19)))
            }

            // 与正式目录同盘暂存，保证最后一步是原子重命名，而不是边删边复制。
            val base = WebBadgeManager.root(ctx)
            base.parentFile?.mkdirs()
            stagedRoot = File(base.parentFile, ".backup_staged_${System.currentTimeMillis()}")
            check(stagedRoot.mkdirs()) { "无法创建恢复暂存目录" }
            if (sourceRoot.isDirectory) {
                sourceRoot.listFiles()?.forEach { dir ->
                    if (!isSafeBadgeId(dir.name)) error("备份目录名不合法")
                    if (dir.isDirectory) copyDir(dir, File(stagedRoot, dir.name))
                }
            }

            if (badgeArray.length() == 0) {
                stagedRoot.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                    if (File(dir, WebBadgeManager.INDEX_FILE).isFile) {
                        items.add(Triple(dir.name, dir.name, 0))
                    }
                }
            }
            WebBadgeManager.restoreBackup(ctx, stagedRoot, items, selected, settings)
            stagedRoot = null
            null
        } catch (e: Exception) {
            e.localizedMessage ?: e.javaClass.simpleName
        } finally {
            temp.deleteRecursively()
            stagedRoot?.deleteRecursively()
        }
    }

    // ── WebDAV 测试 / 上传 / 下载恢复 ───────────────────────────

    /** 返回 null 表示连接正常，否则为错误码 */
    suspend fun webdavTestConnection(cfg: WebDavConfig): String? = withContext(Dispatchers.IO) {
        try {
            val conn = httpConn(fileUrl(cfg))
            try {
                conn.requestMethod = "HEAD"
                basicAuth(conn, cfg)
                when (val code = conn.responseCode) {
                    in 200..299 -> null
                    301, 302 -> null
                    401 -> ERR_AUTH_FAIL
                    403 -> ERR_NO_PERMISSION
                    404 -> null
                    405 -> null
                    else -> "$ERR_CONNECTION_FAILED:$code"
                }
            } finally { conn.disconnect() }
        } catch (_: Exception) { ERR_NETWORK_ERROR }
    }

    /** 返回 null 表示成功，否则为错误码 */
    fun webdavUpload(ctx: Context, cfg: WebDavConfig): String? {
        val tmp = File(ctx.cacheDir, "backup_${System.currentTimeMillis()}.zip")
        return try {
            FileOutputStream(tmp).use { fos -> writeBackupZip(ctx, fos) }
            val conn = httpConn(fileUrl(cfg))
            try {
                conn.requestMethod = "PUT"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/zip")
                conn.setRequestProperty("Content-Length", tmp.length().toString())
                basicAuth(conn, cfg)
                tmp.inputStream().use { it.copyTo(conn.outputStream) }
                when (val code = conn.responseCode) {
                    in 200..299 -> null
                    401 -> ERR_UPLOAD_AUTH_FAIL
                    403 -> ERR_UPLOAD_NO_PERMISSION
                    else -> "$ERR_UPLOAD_FAILED:$code"
                }
            } finally { conn.disconnect() }
        } catch (_: Exception) {
            ERR_UPLOAD_FAILED_SIMPLE
        } finally {
            tmp.delete()
        }
    }

    /** 返回 null 表示成功，否则为错误码或错误描述 */
    fun webdavDownload(ctx: Context, cfg: WebDavConfig): String? {
        val tmp = File(ctx.cacheDir, "webdav_restore_${System.currentTimeMillis()}.zip")
        return try {
            var url = fileUrl(cfg)
            var conn = httpConn(url)
            try {
                conn.requestMethod = "GET"
                basicAuth(conn, cfg)
                var code = conn.responseCode
                val origHost = try { URI(url).host } catch (_: Exception) { "" }
                var redirects = 0
                while (code in listOf(301, 302, 307, 308) && redirects < 5) {
                    redirects++
                    val loc = conn.getHeaderField("Location") ?: return ERR_REDIRECT_NO_LOCATION
                    conn.disconnect()
                    url = if (loc.startsWith("http")) loc
                    else "${cfg.url.trimEnd('/')}${if (loc.startsWith("/")) "" else "/"}$loc"
                    conn = httpConn(url)
                    conn.requestMethod = "GET"
                    val rHost = try { URI(url).host } catch (_: Exception) { "" }
                    if (rHost.isNotEmpty() && rHost == origHost) basicAuth(conn, cfg)
                    else conn.setRequestProperty("Referer", cfg.url.trimEnd('/'))
                    code = conn.responseCode
                }
                if (code != 200) {
                    return when (code) {
                        401 -> ERR_AUTH_FAIL
                        404 -> ERR_BACKUP_NOT_FOUND
                        403 -> ERR_DOWNLOAD_NO_PERMISSION
                        else -> "$ERR_SERVER_RESPONSE:$code"
                    }
                }
                conn.inputStream.use { input ->
                    FileOutputStream(tmp).use { out ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_BYTES) return ERR_UNKNOWN_ERROR
                            out.write(buffer, 0, read)
                        }
                    }
                }
            } finally { conn.disconnect() }
            if (tmp.length() <= 0) return ERR_UNKNOWN_ERROR
            importFromFile(ctx, tmp)
        } catch (_: Exception) {
            ERR_UNKNOWN_ERROR
        } finally {
            tmp.delete()
        }
    }

    private fun httpConn(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 120000
        c.useCaches = false
        c.doInput = true
        c.instanceFollowRedirects = false
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        return c
    }

    private fun basicAuth(conn: HttpURLConnection, cfg: WebDavConfig) {
        val encoded = Base64.encodeToString(
            "${cfg.username}:${cfg.password}".toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        conn.setRequestProperty("Authorization", "Basic $encoded")
    }

    private fun fileUrl(cfg: WebDavConfig): String {
        val base = cfg.url.trimEnd('/')
        return try {
            URI("$base/$BACKUP_FILE_NAME").toASCIIString()
        } catch (_: Exception) {
            "$base/$BACKUP_FILE_NAME"
        }
    }

    // ── 工具 ────────────────────────────────────────────────────

    private fun isSafeBadgeId(id: String): Boolean =
        id.length in 1..128 && Regex("[A-Za-z0-9_-]+").matches(id)

    private fun copyDir(src: File, dst: File) {
        if (!dst.exists()) dst.mkdirs()
        src.listFiles()?.forEach { f ->
            if (f.isDirectory) copyDir(f, File(dst, f.name))
            else f.copyTo(File(dst, f.name), overwrite = true)
        }
    }

    private fun InputStream.copyTo(out: java.io.OutputStream) {
        val buffer = ByteArray(8192)
        while (true) {
            val read = this.read(buffer)
            if (read <= 0) break
            out.write(buffer, 0, read)
        }
    }
}