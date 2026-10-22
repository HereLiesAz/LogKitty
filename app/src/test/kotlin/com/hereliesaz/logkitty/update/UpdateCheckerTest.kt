package com.hereliesaz.logkitty.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerTest {

    private val releases = """
        [
          {"tag_name":"latest-debug-v0.14","html_url":"https://x/debug14","prerelease":true,"draft":false},
          {"tag_name":"latest-release-v0.9","html_url":"https://x/lr9","prerelease":true,"draft":false},
          {"tag_name":"v0.15.0.3","html_url":"https://x/draft","prerelease":false,"draft":true},
          {"tag_name":"v0.10.1.2","html_url":"https://x/v0.10.1.2","prerelease":false,"draft":false},
          {"tag_name":"v0.9.0.1","html_url":"https://x/v0.9","prerelease":false,"draft":false}
        ]
    """.trimIndent()

    @Test fun stableBuildSeesNewerStableReleaseOnly() {
        val found = UpdateChecker.newestRelease(releases, "0.10.0.5", debug = false)!!
        assertEquals("0.10.1.2", found.version)
        assertEquals("https://x/v0.10.1.2", found.url)
        assertEquals(UpdateChecker.Source.GITHUB, found.source)
    }

    @Test fun stableBuildUpToDateIgnoresDraftsAndPrereleases() {
        assertNull(UpdateChecker.newestRelease(releases, "0.10.1.2", debug = false))
        assertNull(UpdateChecker.newestRelease(releases, "0.14.1.1", debug = false))
    }

    @Test fun debugBuildComparesMajorMinorOfDebugTags() {
        assertEquals("0.14", UpdateChecker.newestRelease(releases, "0.13.0.9", debug = true)!!.version)
        assertNull(UpdateChecker.newestRelease(releases, "0.14.1.1", debug = true))
    }

    @Test fun garbageIsNoUpdate() {
        assertNull(UpdateChecker.newestRelease("not json", "0.1.0.0", debug = false))
    }
}
