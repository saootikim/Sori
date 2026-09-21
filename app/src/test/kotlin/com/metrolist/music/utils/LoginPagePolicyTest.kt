package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class LoginPagePolicyTest {
    private fun action(
        host: String?,
        hasAuthCookie: Boolean = true,
        redirects: Int = 0,
        scheme: String? = "https",
    ) = LoginPagePolicy.onPageFinished(scheme, host, hasAuthCookie, redirects)

    @Test
    fun youTubeMusicPageIsExtractedAsBefore() {
        assertEquals(LoginPageAction.Extract, action("music.youtube.com"))
        assertEquals(LoginPageAction.Extract, action("music.youtube.com", hasAuthCookie = false))
    }

    @Test
    fun signedInOnOtherYouTubePageOpensYouTubeMusic() {
        assertEquals(LoginPageAction.OpenYouTubeMusic, action("www.youtube.com"))
        assertEquals(LoginPageAction.OpenYouTubeMusic, action("m.youtube.com", redirects = 1))
        assertEquals(LoginPageAction.OpenYouTubeMusic, action("youtube.com"))
    }

    @Test
    fun extractsInPlaceOnceRedirectBudgetIsSpent() {
        assertEquals(
            LoginPageAction.Extract,
            action("www.youtube.com", redirects = LoginPagePolicy.MAX_REDIRECTS_TO_MUSIC),
        )
    }

    @Test
    fun ignoresPagesThatAreNotSignedInYouTube() {
        assertEquals(LoginPageAction.Ignore, action("www.youtube.com", hasAuthCookie = false))
        assertEquals(LoginPageAction.Ignore, action("accounts.google.com"))
        assertEquals(LoginPageAction.Ignore, action("accounts.youtube.com"))
        assertEquals(LoginPageAction.Ignore, action("notyoutube.com"))
        assertEquals(LoginPageAction.Ignore, action("www.youtube.com", scheme = "http"))
        assertEquals(LoginPageAction.Ignore, action(null))
    }
}
