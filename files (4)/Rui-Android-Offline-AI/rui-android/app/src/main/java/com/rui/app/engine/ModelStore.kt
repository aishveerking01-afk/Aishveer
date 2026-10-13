package com.rui.app.engine

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

data class LocalModel(val file: File) {
    val name: String get() = file.name
    val sizeBytes: Long get() = file.length()
    /** Weights + KV cache + runtime overhead. Rough, but good enough to warn before loading. */
    val estimatedRamBytes: Long get() = (sizeBytes * 1.2).toLong()
}

data class DeviceStats(
    val totalRam: Long,
    val availRam: Long,
    val storageTotal: Long,
    val storageFree: Long,
    val modelsBytes: Long,
)

/**
 * Models live in app-private storage (filesDir/models): no storage permission needed,
 * excluded from backup, removed automatically on uninstall.
 * They arrive through the system file picker (no network involved).
 */
class ModelStore(private val context: Context) {

    private val dir = File(context.filesDir, "models").apply { mkdirs() }
    private val allowed = setOf("task", "bin")

    fun list(): List<LocalModel> =
        dir.listFiles { f -> f.isFile && f.extension in allowed }
            ?.sortedBy { it.name }?.map(::LocalModel) ?: emptyList()

    fun delete(model: LocalModel) = model.file.delete()

    fun deviceStats(): DeviceStats {
        val am = context.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val sf = StatFs(dir.path)
        return DeviceStats(mi.totalMem, mi.availMem, sf.totalBytes, sf.availableBytes, list().sumOf { it.sizeBytes })
    }

    /** Streams the picked file into app storage with progress. Atomic: a .part file is renamed at the end. */
    suspend fun import(uri: Uri, onProgress: (Float) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val cr = context.contentResolver
            var name = "model.task"
            var size = -1L
            cr.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val s = c.getColumnIndex(OpenableColumns.SIZE)
                    if (n >= 0) name = c.getString(n)
                    if (s >= 0) size = c.getLong(s)
                }
            }
            require(name.substringAfterLast('.', "").lowercase() in allowed) {
                "Unsupported file. Pick a .task model bundle."
            }
            if (size > 0) {
                require(StatFs(dir.path).availableBytes > size + 256L * 1024 * 1024) {
                    "Not enough free storage for this model."
                }
            }
            val tmp = File(dir, "$name.part")
            val out = File(dir, name)
            try {
                cr.openInputStream(uri)!!.use { input ->
                    FileOutputStream(tmp).use { o ->
                        val buf = ByteArray(1 shl 20)
                        var done = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            done += n
                            if (size > 0) onProgress(done.toFloat() / size)
                        }
                        o.fd.sync()
                    }
                }
                if (out.exists()) out.delete()
                check(tmp.renameTo(out)) { "Could not save the model" }
                out
            } finally {
                if (tmp.exists()) tmp.delete()
            }
        }
    }
}
