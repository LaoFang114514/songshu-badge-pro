package com.laofang.songshushoupaipro.songshu.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import com.laofang.songshushoupaipro.songshu.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

data class WebBadge(
    val id: String,
    val name: String,
    val folder: File,
    val iconIndex: Int = 0
) {
    val indexHtml: File get() = File(folder, WebBadgeManager.INDEX_FILE)
    val thumbnail: File get() = File(folder, WebBadgeManager.THUMB_FILE)
    val hasIndex: Boolean get() = indexHtml.isFile
}

data class WebFile(
    val relativePath: String,
    val file: File
) {
    val size: Long get() = file.length()
    val isText: Boolean get() = WebBadgeManager.isTextPath(relativePath)
}

object WebBadgeManager {
    const val INDEX_FILE = "index.html"
    const val THUMB_FILE = ".thumb.png"
    const val DEFAULT_ID = "default"
    const val DEFAULT_NAME = "默认兽牌"
    const val BLACK_VINYL_ID = "heijiaoshou_egg"
    const val BLACK_VINYL_NAME = "小黑胶兽"
    private const val BLACK_VINYL_IMAGE = "heijiaoshou.jpg"
    private const val PREFS_NAME = "web_badge_data"
    private const val KEY_LIST = "badges_json"
    private const val KEY_SELECTED = "selected_index"
    private const val KEY_INITIALIZED = "initialized"
    private const val MAX_IMPORT_BYTES = 64L * 1024 * 1024
    private const val MAX_IMPORT_FILES = 2000

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun root(ctx: Context): File = File(ctx.filesDir, "web_badges").apply { mkdirs() }

    fun getBadges(ctx: Context): List<WebBadge> {
        ensureInitialized(ctx)
        val raw = prefs(ctx).getString(KEY_LIST, null) ?: return emptyList()
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        val base = root(ctx)
        val list = ArrayList<WebBadge>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id", "")
            if (id.isEmpty()) continue
            val folder = File(base, id)
            if (!folder.isDirectory) continue
            val name = obj.optString("name", "").ifBlank { id }
            val iconIndex = obj.optInt("icon", 0)
            list.add(WebBadge(id, name, folder, iconIndex))
        }
        return list
    }

    fun getSelectedIndex(ctx: Context): Int = prefs(ctx).getInt(KEY_SELECTED, 0)

    fun setSelectedIndex(ctx: Context, index: Int) {
        prefs(ctx).edit { putInt(KEY_SELECTED, index) }
    }

    fun getSelectedBadge(ctx: Context): WebBadge? {
        val list = getBadges(ctx)
        if (list.isEmpty()) return null
        val idx = getSelectedIndex(ctx).coerceIn(0, list.size - 1)
        return list[idx]
    }

    fun findBadge(ctx: Context, id: String): WebBadge? =
        getBadges(ctx).firstOrNull { it.id == id }

    fun badgeIndex(ctx: Context, id: String): Int =
        getBadges(ctx).indexOfFirst { it.id == id }

    @Synchronized
    fun renameBadge(ctx: Context, id: String, newName: String) {
        val list = getBadges(ctx).toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        list[idx] = list[idx].copy(name = newName.trim().ifBlank { list[idx].name })
        saveMeta(ctx, list)
    }

    @Synchronized
    fun moveBadge(ctx: Context, from: Int, to: Int) {
        val list = getBadges(ctx).toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val selected = getSelectedIndex(ctx)
        val item = list.removeAt(from)
        list.add(to, item)
        saveMeta(ctx, list)
        val newSelected = when (selected) {
            from -> to
            in (from + 1)..to -> selected - 1
            in to..<from -> selected + 1
            else -> selected
        }
        setSelectedIndex(ctx, newSelected)
    }

    @Synchronized
    fun deleteBadge(ctx: Context, id: String) {
        val list = getBadges(ctx).toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        list[idx].folder.deleteRecursively()
        list.removeAt(idx)
        saveMeta(ctx, list)
        val selected = getSelectedIndex(ctx)
        val newSelected = when {
            list.isEmpty() -> 0
            selected == idx -> idx.coerceAtMost(list.size - 1)
            selected > idx -> selected - 1
            else -> selected
        }
        setSelectedIndex(ctx, newSelected)
    }

    @Synchronized
    fun updateIcon(ctx: Context, id: String, iconIndex: Int) {
        val list = getBadges(ctx).toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        list[idx] = list[idx].copy(iconIndex = iconIndex)
        saveMeta(ctx, list)
    }

    fun restoreBadges(ctx: Context, items: List<Triple<String, String, Int>>, selected: Int) {
        restoreBackup(ctx, root(ctx), items, selected, null)
    }

    /** 仅在暂存数据全部校验通过后替换正式目录；任一步失败都恢复原目录和元数据。 */
    @Synchronized
    fun restoreBackup(
        ctx: Context,
        stagedRoot: File,
        items: List<Triple<String, String, Int>>,
        selected: Int,
        settings: JSONObject?
    ) {
        val base = root(ctx)
        require(stagedRoot.canonicalFile.parentFile == base.canonicalFile.parentFile) { "暂存目录位置无效" }
        val list = items.map { (id, name, icon) ->
            WebBadge(id, name.ifBlank { id }, File(base, id), icon)
        }
        val p = prefs(ctx)
        val oldMeta = p.getString(KEY_LIST, null)
        val oldSelected = p.getInt(KEY_SELECTED, 0)
        val rollback = File(base.parentFile, ".web_badges_rollback_${System.currentTimeMillis()}")
        base.parentFile?.mkdirs()
        val hadBase = base.exists()
        var movedOld = false
        var movedNew = false
        var rollbackNeeded = false
        try {
            if (hadBase) {
                check(base.renameTo(rollback)) { "无法暂存当前兽牌" }
                movedOld = true
                rollbackNeeded = true
            }
            check(stagedRoot.renameTo(base)) { "无法启用恢复的兽牌" }
            movedNew = true
            saveMeta(ctx, list)
            setSelectedIndex(ctx, selected.coerceIn(0, (list.size - 1).coerceAtLeast(0)))
            p.edit { putBoolean(KEY_INITIALIZED, true) }
            if (settings != null) SettingsManager.importJson(ctx, settings)
            rollbackNeeded = false
        } catch (e: Exception) {
            if (movedNew) base.deleteRecursively()
            if (movedOld && rollback.renameTo(base)) rollbackNeeded = false
            p.edit {
                if (oldMeta == null) remove(KEY_LIST) else putString(KEY_LIST, oldMeta)
                putInt(KEY_SELECTED, oldSelected)
            }
            throw e
        } finally {
            if (!rollbackNeeded) rollback.deleteRecursively()
        }
    }

    private fun saveMeta(ctx: Context, list: List<WebBadge>) {
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(JSONObject().apply {
                put("id", b.id)
                put("name", b.name)
                put("icon", b.iconIndex)
            })
        }
        prefs(ctx).edit { putString(KEY_LIST, arr.toString()) }
    }

    private fun ensureInitialized(ctx: Context) {
        val p = prefs(ctx)
        if (p.getBoolean(KEY_INITIALIZED, false)) return
        val base = root(ctx)
        val dirs = base.listFiles()?.filter { it.isDirectory } ?: emptyList()
        val list = dirs.map { WebBadge(it.name, it.name, it) }
        p.edit {
            if (list.isEmpty()) {
                // 首次启动内置一个默认兽牌（固定 id，可删除；删掉后只能从「导入方式」重新创建）
                try {
                    val folder = File(base, DEFAULT_ID)
                    folder.mkdirs()
                    File(folder, INDEX_FILE).writeText(DEFAULT_HTML, Charsets.UTF_8)
                } catch (_: Exception) {}
                val arr = JSONArray()
                arr.put(JSONObject().apply { put("id", DEFAULT_ID); put("name", DEFAULT_NAME) })
                putString(KEY_LIST, arr.toString())
                putInt(KEY_SELECTED, 0)
            } else {
                val arr = JSONArray()
                list.forEach { b -> arr.put(JSONObject().apply { put("id", b.id); put("name", b.name) }) }
                putString(KEY_LIST, arr.toString())
            }
            putBoolean(KEY_INITIALIZED, true)
        }
    }

    /** 通过「导入方式」创建默认兽牌；已存在时不再创建 */
    fun createDefaultBadge(ctx: Context): Result<WebBadge> = try {
        if (findBadge(ctx, DEFAULT_ID) != null) {
            Result.failure(IllegalStateException("默认兽牌已存在"))
        } else {
            val folder = File(root(ctx), DEFAULT_ID)
            folder.mkdirs()
            File(folder, INDEX_FILE).writeText(DEFAULT_HTML, Charsets.UTF_8)
            val badge = WebBadge(DEFAULT_ID, DEFAULT_NAME, folder)
            registerBadge(ctx, badge)
            Result.success(badge)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** 关于页彩蛋：只创建一个小黑胶兽牌，重复触发不会生成第二份。 */
    @Synchronized
    fun ensureBlackVinylBadge(ctx: Context): WebBadge {
        findBadge(ctx, BLACK_VINYL_ID)?.let { return it }
        val folder = File(root(ctx), BLACK_VINYL_ID)
        val created = !folder.exists()
        return try {
            folder.mkdirs()
            ctx.resources.openRawResource(R.drawable.heijiaoshou).use { input ->
                File(folder, BLACK_VINYL_IMAGE).outputStream().use { output -> input.copyTo(output) }
            }
            File(folder, INDEX_FILE).writeText(BLACK_VINYL_HTML, Charsets.UTF_8)
            val badge = WebBadge(BLACK_VINYL_ID, BLACK_VINYL_NAME, folder, iconIndex = 9)
            registerBadge(ctx, badge)
            badge
        } catch (e: Exception) {
            if (created) folder.deleteRecursively()
            throw e
        }
    }

    fun newBadgeFolder(ctx: Context, displayName: String): Pair<WebBadge, File> {
        val base = root(ctx)
        var id = System.currentTimeMillis().toString()
        while (File(base, id).exists()) id = "${System.currentTimeMillis()}_${(0..999).random()}"
        val folder = File(base, id)
        folder.mkdirs()
        val name = sanitizeName(displayName).ifBlank { "网页兽牌" }
        return WebBadge(id, name, folder) to folder
    }

    @Synchronized
    fun registerBadge(ctx: Context, badge: WebBadge) {
        val list = getBadges(ctx).toMutableList()
        if (list.none { it.id == badge.id }) list.add(badge)
        saveMeta(ctx, list)
        if (list.size == 1) setSelectedIndex(ctx, 0)
    }

    fun sanitizeName(raw: String): String {
        val name = raw.trim()
            .replace('/', '_').replace('\\', '_')
            .replace(Regex("[\\p{Cntrl}]"), "")
            .replace(Regex("[:*?\"<>|]"), "_")
            .trim()
        return name.take(40)
    }

    fun displayName(ctx: Context, uri: Uri): String {
        return try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            } ?: uri.lastPathSegment ?: ""
        } catch (_: Exception) {
            uri.lastPathSegment ?: ""
        }
    }

    fun importHtml(ctx: Context, uri: Uri): Result<WebBadge> {
        var createdFolder: File? = null
        return try {
        val raw = displayName(ctx, uri)
        val name = raw.substringBeforeLast('.', raw).ifBlank { "网页兽牌" }
        val (badge, folder) = newBadgeFolder(ctx, name)
        createdFolder = folder
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            File(folder, INDEX_FILE).outputStream().use { out -> input.copyTo(out) }
        } ?: error("无法读取所选文件")
        if (File(folder, INDEX_FILE).length() == 0L) {
            folder.deleteRecursively()
            return Result.failure(IllegalArgumentException("文件内容为空"))
        }
        registerBadge(ctx, badge)
        Result.success(badge)
    } catch (e: Exception) {
        createdFolder?.deleteRecursively()
        Result.failure(e)
    }
    }

    fun importZip(ctx: Context, uri: Uri): Result<WebBadge> {
        val temp = File(ctx.cacheDir, "zip_import_${System.currentTimeMillis()}")
        var createdFolder: File? = null
        return try {
        val raw = displayName(ctx, uri)
        val name = raw.substringBeforeLast('.', raw).ifBlank { "网页兽牌" }
        temp.mkdirs()
        var total = 0L
        var count = 0
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val entryName = entry.name.replace('\\', '/')
                    val target = File(temp, entryName)
                    if (!target.canonicalPath.startsWith(temp.canonicalPath + File.separator)) {
                        zis.closeEntry(); continue
                    }
                    if (entryName.startsWith("__MACOSX") || entryName.endsWith("/.DS_Store")) {
                        zis.closeEntry(); continue
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        count++
                        if (count > MAX_IMPORT_FILES) error("文件数量过多")
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { out ->
                            total += copyLimited(zis, out, MAX_IMPORT_BYTES - total)
                        }
                        if (total >= MAX_IMPORT_BYTES) error("压缩包超过 64 MB 限制")
                    }
                    zis.closeEntry()
                }
            }
        } ?: error("无法读取所选文件")
        val siteRoot = findSiteRoot(temp)
        if (siteRoot == null) {
            temp.deleteRecursively()
            return Result.failure(IllegalArgumentException("没有找到网页文件（.html）"))
        }
        val (badge, folder) = newBadgeFolder(ctx, name)
        createdFolder = folder
        copyDirectory(siteRoot, folder, skipThumb = true)
        ensureIndexFile(folder)
        registerBadge(ctx, badge)
        Result.success(badge)
    } catch (e: Exception) {
        createdFolder?.deleteRecursively()
        Result.failure(e)
    } finally {
        temp.deleteRecursively()
    }
    }

    fun importFolder(ctx: Context, uri: Uri): Result<WebBadge> {
        val temp = File(ctx.cacheDir, "dir_import_${System.currentTimeMillis()}")
        var createdFolder: File? = null
        return try {
        val tree = DocumentFile.fromTreeUri(ctx, uri) ?: error("无法访问所选文件夹")
        val raw = tree.name ?: displayName(ctx, uri)
        val name = raw.ifBlank { "网页兽牌" }
        temp.mkdirs()
        val budget = longArrayOf(0L, 0L) // size, count
        copyDocumentTree(ctx, tree, temp, 0, budget)
        val siteRoot = findSiteRoot(temp)
        if (siteRoot == null) {
            temp.deleteRecursively()
            return Result.failure(IllegalArgumentException("没有找到网页文件（.html）"))
        }
        val (badge, folder) = newBadgeFolder(ctx, name)
        createdFolder = folder
        copyDirectory(siteRoot, folder, skipThumb = true)
        ensureIndexFile(folder)
        registerBadge(ctx, badge)
        Result.success(badge)
    } catch (e: Exception) {
        createdFolder?.deleteRecursively()
        Result.failure(e)
    } finally {
        temp.deleteRecursively()
    }
    }

    fun importFileInto(ctx: Context, folder: File, uri: Uri): Result<String> = try {
        val raw = displayName(ctx, uri).ifBlank { error("无法获取文件名") }
        val name = sanitizeName(raw).ifBlank { "file.txt" }
        var target = File(folder, name)
        var suffix = 2
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) ".${name.substringAfterLast('.')}" else ""
        while (target.exists()) {
            target = File(folder, "$base ($suffix)$ext")
            suffix++
        }
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { out -> input.copyTo(out) }
        } ?: error("无法读取文件")
        Result.success(target.name)
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun listFiles(folder: File): List<WebFile> {
        if (!folder.isDirectory) return emptyList()
        val out = ArrayList<WebFile>()
        folder.walkTopDown().forEach { f ->
            if (!f.isFile) return@forEach
            if (f.name == THUMB_FILE) return@forEach
            val rel = f.relativeTo(folder).invariantSeparatorsPath
            out.add(WebFile(rel, f))
        }
        return out.sortedWith(compareBy({ !it.relativePath.contains('/') }, { it.relativePath.lowercase() }))
    }

    fun fileCount(folder: File): Int = listFiles(folder).size

    fun folderSize(folder: File): Long = listFiles(folder).sumOf { it.size }

    fun createEmptyFile(folder: File, rawName: String): Result<String> = try {
        val name = sanitizeName(rawName)
        if (name.isEmpty() || name.contains("..")) error("文件名不合法")
        val target = File(folder, name)
        if (target.exists()) error("同名文件已存在")
        target.parentFile?.mkdirs()
        target.writeText("", Charsets.UTF_8)
        Result.success(name)
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun deleteFile(folder: File, relPath: String): Result<Unit> = try {
        if (relPath == INDEX_FILE) error("index.html 不能删除")
        val target = File(folder, relPath)
        if (!target.canonicalPath.startsWith(folder.canonicalPath + File.separator)) error("非法路径")
        if (target.exists()) target.delete()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun renameFile(folder: File, relPath: String, newNameRaw: String): Result<String> = try {
        val newName = sanitizeName(newNameRaw)
        if (newName.isEmpty() || newName.contains("..")) error("文件名不合法")
        val source = File(folder, relPath)
        if (!source.canonicalPath.startsWith(folder.canonicalPath + File.separator)) error("非法路径")
        if (!source.isFile) error("文件不存在")
        val target = File(source.parentFile, newName)
        if (!target.canonicalPath.startsWith(folder.canonicalPath + File.separator)) error("非法路径")
        if (target.exists() && target != source) error("同名文件已存在")
        if (!source.renameTo(target)) error("重命名失败")
        Result.success(WebFile(target.relativeTo(folder).invariantSeparatorsPath, target).relativePath)
    } catch (e: Exception) {
        Result.failure(e)
    }

    class FileChangedException : Exception("文件已被其他方式修改")

    @Synchronized
    fun readText(file: File): String {
        return FileInputStream(file).use { input ->
            val bytes = input.readBytes()
            String(bytes, Charsets.UTF_8)
        }
    }

    @Synchronized
    fun writeText(file: File, text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text, Charsets.UTF_8)
    }

    /** 在编辑器打开期间，禁止旧快照覆盖 MCP 或其他页面刚写入的内容。 */
    @Synchronized
    fun writeTextIfUnchanged(file: File, expected: String, text: String): Result<Unit> = try {
        if (!file.isFile || readText(file) != expected) {
            Result.failure(FileChangedException())
        } else {
            writeText(file, text)
            Result.success(Unit)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun isTextPath(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in setOf("html", "htm", "css", "js", "mjs", "json", "txt", "md", "xml", "svg", "csv", "log", "yml", "yaml", "webmanifest")
    }

    fun sizeLabel(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.1f MB", mb)
    }

    private fun copyLimited(input: InputStream, out: FileOutputStream, limit: Long): Long {
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > limit) error("超过大小限制")
            out.write(buffer, 0, read)
        }
        return total
    }

    private fun copyDocumentTree(ctx: Context, doc: DocumentFile, out: File, depth: Int, budget: LongArray) {
        if (depth > 8) return
        val children = doc.listFiles()
        for (child in children) {
            val name = child.name ?: continue
            if (name == "__MACOSX" || name == ".DS_Store") continue
            val target = File(out, name)
            if (!target.canonicalPath.startsWith(out.canonicalPath + File.separator)) continue
            if (child.isDirectory) {
                target.mkdirs()
                copyDocumentTree(ctx, child, target, depth + 1, budget)
            } else {
                budget[1] += 1
                if (budget[1] > MAX_IMPORT_FILES) error("文件数量过多")
                target.parentFile?.mkdirs()
                val size = child.length()
                if (budget[0] + size > MAX_IMPORT_BYTES) error("文件夹超过 64 MB 限制")
                ctx.contentResolver.openInputStream(child.uri)?.use { input ->
                    target.outputStream().use { o -> budget[0] += input.copyTo(o) }
                }
            }
        }
    }

    private fun copyDirectory(src: File, dst: File, skipThumb: Boolean) {
        if (!dst.exists()) dst.mkdirs()
        src.listFiles()?.forEach { f ->
            if (skipThumb && f.name == THUMB_FILE) return@forEach
            val target = File(dst, f.name)
            if (f.isDirectory) copyDirectory(f, target, skipThumb)
            else f.copyTo(target, overwrite = true)
        }
    }

    private fun hasIndex(dir: File): Boolean {
        val files = dir.listFiles() ?: return false
        return files.any { it.isFile && (it.name.equals("index.html", true) || it.name.equals("index.htm", true)) }
    }

    /** 找到网页根目录：优先含 index.html 的目录；否则找单个顶层目录；最后退回第一个 html 所在目录 */
    private fun findSiteRoot(dir: File): File? {
        if (hasIndex(dir)) return dir
        val children = dir.listFiles()?.filter { it.isDirectory && it.name != "__MACOSX" } ?: emptyList()
        if (children.size == 1) {
            var d = children[0]
            var depth = 0
            while (depth < 3) {
                if (hasIndex(d)) return d
                val sub = d.listFiles()?.filter { it.isDirectory } ?: emptyList()
                if (sub.size != 1) break
                d = sub[0]
                depth++
            }
        }
        if (children.size == 1 && hasIndex(children[0])) return children[0]
        val html = findFirstHtml(dir, 0)
        return html?.parentFile
    }

    private fun findFirstHtml(dir: File, depth: Int): File? {
        if (depth > 3) return null
        val files = dir.listFiles() ?: return null
        files.firstOrNull { it.isFile && it.name.endsWith(".html", true) }?.let { return it }
        files.filter { it.isDirectory && it.name != "__MACOSX" }.forEach { d ->
            findFirstHtml(d, depth + 1)?.let { return it }
        }
        return null
    }

    private fun ensureIndexFile(folder: File) {
        val index = File(folder, INDEX_FILE)
        if (index.isFile) return
        val files = folder.listFiles() ?: return
        val htm = files.firstOrNull { it.isFile && it.name.equals("index.htm", true) }
        if (htm != null) {
            htm.copyTo(index, overwrite = true)
            return
        }
        val firstHtml = files.firstOrNull { it.isFile && it.name.endsWith(".html", true) }
        if (firstHtml != null) {
            firstHtml.copyTo(index, overwrite = true)
            return
        }
        index.writeText(
            "<!DOCTYPE html><html><head><meta charset=\"utf-8\"></head><body style=\"background:#111;color:#eee;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0\"><p>这个兽牌还没有 index.html</p></body></html>",
            Charsets.UTF_8
        )
    }

    private val BLACK_VINYL_HTML = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<title>小黑胶兽</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  html, body { width: 100%; height: 100%; overflow: hidden; background: #000; }
  body { font-family: sans-serif; }
  #stage {
    position: fixed; inset: 0; display: grid; place-items: center;
    background: #000; touch-action: none; cursor: pointer;
    -webkit-tap-highlight-color: transparent;
  }
  #beast {
    width: min(30vmin, 180px); height: auto; aspect-ratio: 1;
    object-fit: contain; image-rendering: pixelated;
    user-select: none; -webkit-user-drag: none; will-change: transform;
  }
</style>
</head>
<body>
  <div id="stage" role="button" tabindex="0" aria-label="小黑胶兽，点击跳跃">
    <img id="beast" src="heijiaoshou.jpg" alt="小黑胶兽" draggable="false">
  </div>
  <script>
    const stage = document.getElementById('stage');
    const beast = document.getElementById('beast');
    let jump = null;
    function hop() {
      if (jump) jump.cancel();
      jump = beast.animate([
        { transform: 'translateY(0) scale(1)' },
        { transform: 'translateY(-22vmin) scale(1.03)', offset: 0.45 },
        { transform: 'translateY(0) scale(.97, 1.03)', offset: 0.8 },
        { transform: 'translateY(0) scale(1)' }
      ], { duration: 480, easing: 'cubic-bezier(.2,.75,.25,1)' });
    }
    stage.addEventListener('pointerdown', function (event) {
      event.preventDefault();
      hop();
    });
    stage.addEventListener('keydown', function (event) {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        hop();
      }
    });
    stage.addEventListener('contextmenu', function (event) { event.preventDefault(); });
  </script>
</body>
</html>
""".trimIndent()

    private val DEFAULT_HTML = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<title>默认兽牌</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  html, body { width: 100%; height: 100%; overflow: hidden; background: transparent; }
  body {
    display: flex; align-items: center; justify-content: center;
    font-family: -apple-system, "Segoe UI", "Noto Sans SC", sans-serif;
    color: #666;
    user-select: none;
  }
  .text {
    font-size: 6vmin;
    letter-spacing: .5vmin;
    text-align: center;
    writing-mode: vertical-rl;
    line-height: 2;
  }
</style>
</head>
<body>
  <div class="text">这是默认兽牌</div>
</body>
</html>
""".trimIndent()
}
