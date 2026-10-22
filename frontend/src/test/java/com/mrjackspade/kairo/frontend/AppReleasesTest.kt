package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AppReleasesTest {
    private fun release(tag: String, draft: Boolean = false) =
        """{"tag_name": "$tag", "draft": $draft, "html_url": "https://github.com/MrJackSpade/Kairo98/releases/tag/$tag"}"""

    private fun list(vararg releases: String) = releases.joinToString(",", "[", "]")

    @Test
    fun offersTheNewestStableRelease() {
        val newer = AppReleases.newer(list(release("v1.2.0"), release("v1.10.0"), release("v1.3.0")), "1.2.0")
        assertEquals("v1.10.0", newer?.tag)
        assertEquals("1.10.0", newer?.version)
        assertEquals("https://github.com/MrJackSpade/Kairo98/releases/tag/v1.10.0", newer?.url)
    }

    @Test
    fun skipsHyphenatedVersionsAndDrafts() {
        val releases = list(release("v2.0.0-beta"), release("v1.9.0", draft = true), release("v1.1.0"))
        assertNull(AppReleases.newer(releases, "1.1.0"))
        assertEquals("v1.1.0", AppReleases.newer(releases, "1.0.0")?.tag)
    }

    @Test
    fun offersNothingWhenInstalledIsCurrentOrNewer() {
        assertNull(AppReleases.newer(list(release("v1.2.0")), "1.2.0"))
        assertNull(AppReleases.newer(list(release("v1.2.0")), "1.3.0"))
        assertNull(AppReleases.newer(list(), "1.0.0"))
    }

    @Test
    fun aStableReleaseIsNewerThanItsOwnPrerelease() {
        assertEquals("v1.2.0", AppReleases.newer(list(release("v1.2.0")), "1.2.0-ci")?.tag)
        assertNull(AppReleases.newer(list(release("v1.1.0")), "1.2.0-ci"))
    }

    @Test
    fun comparesMissingPartsAsZero() {
        assertNull(AppReleases.newer(list(release("v1.2")), "1.2.0"))
        assertEquals("v1.2.1", AppReleases.newer(list(release("v1.2.1")), "1.2")?.tag)
    }

    @Test
    fun aMalformedStableTagFails() {
        assertThrows(IllegalArgumentException::class.java) {
            AppReleases.newer(list(release("latest")), "1.0.0")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppReleases.newer(list(release("v1.x")), "1.0.0")
        }
    }
}
