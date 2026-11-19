package cc.samlab.classroom.ui.attendance

import java.io.File

internal fun cleanExpiredCaptures(directory: File, activePath: String?, now: Long = System.currentTimeMillis()) {
    val active = activePath?.let { runCatching { File(it).canonicalFile }.getOrNull() }
    directory.listFiles().orEmpty().filter {
        it.isFile && it.name.startsWith("capture-") && it.name.endsWith(".jpg") &&
            runCatching { it.canonicalFile != active }.getOrDefault(false) &&
            it.lastModified() < now - 24 * 60 * 60 * 1000L
    }.forEach { it.delete() }
}
