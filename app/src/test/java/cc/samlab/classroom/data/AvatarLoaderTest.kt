package cc.samlab.classroom.data

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class AvatarLoaderTest {
    @Test fun resolvesBackendRelativeAndAbsoluteImages() {
        assertEquals("http://localhost:3001/uploads/a.png",
            resolveAvatarUri("http://localhost:3001", "/uploads/a.png").toString())
        assertEquals("http://localhost:3001/uploads/a.png",
            resolveAvatarUri("http://localhost:3001", "uploads/a.png").toString())
        assertEquals("https://images.example/a.png",
            resolveAvatarUri("http://localhost:3001", "https://images.example/a.png").toString())
    }

    @Test fun rejectsLocalFilesAndEmbeddedCredentials() {
        assertNull(resolveAvatarUri("http://localhost:3001", "file:///private/photo.png"))
        assertNull(resolveAvatarUri("http://localhost:3001", "https://user:password@example.com/a.png"))
    }

    @Test fun sessionCookiesAreRestrictedToBackendOrigin() {
        val server = URI("https://example.com")
        assertTrue(sameAvatarOrigin(server, URI("https://example.com:443/a.png")))
        assertFalse(sameAvatarOrigin(server, URI("http://example.com/a.png")))
        assertFalse(sameAvatarOrigin(server, URI("https://example.com:444/a.png")))
        assertFalse(sameAvatarOrigin(server, URI("https://cdn.example.com/a.png")))
    }
}
