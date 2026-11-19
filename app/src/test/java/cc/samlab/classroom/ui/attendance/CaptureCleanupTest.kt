package cc.samlab.classroom.ui.attendance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class CaptureCleanupTest {
    @Test fun removesOnlyExpiredUnreferencedCaptures() {
        val directory = Files.createTempDirectory("attendance-cleanup").toFile()
        try {
            val old = directory.resolve("capture-old.jpg").apply { writeText("old"); setLastModified(1_000L) }
            val active = directory.resolve("capture-active.jpg").apply { writeText("active"); setLastModified(1_000L) }
            val recent = directory.resolve("capture-recent.jpg").apply { writeText("recent"); setLastModified(99_000_000L) }
            val other = directory.resolve("unrelated.txt").apply { writeText("other"); setLastModified(1_000L) }
            cleanExpiredCaptures(directory, active.path, now = 100_000_000L)
            assertFalse(old.exists())
            assertTrue(active.exists())
            assertTrue(recent.exists())
            assertTrue(other.exists())
        } finally { directory.deleteRecursively() }
    }
}
