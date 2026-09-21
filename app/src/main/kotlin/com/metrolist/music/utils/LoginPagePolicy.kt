/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

enum class LoginPageAction { Extract, OpenYouTubeMusic, Ignore }

/**
 * Sori: decides what the login WebView does when a page finishes loading.
 *
 * Upstream only reacts to music.youtube.com. YouTube can land a freshly signed-in account
 * on another youtube.com page instead (seen with accounts eligible for a Premium trial, which
 * get a purchase offer), and login then waits forever. Once the auth cookie exists, such a
 * page is steered back to YouTube Music; if YouTube keeps bouncing, the session is extracted
 * in place, since youtube.com pages expose the same ytcfg values.
 */
object LoginPagePolicy {
    const val MAX_REDIRECTS_TO_MUSIC = 2

    fun onPageFinished(
        scheme: String?,
        host: String?,
        hasAuthCookie: Boolean,
        redirectsToMusic: Int,
    ): LoginPageAction {
        if (scheme != "https" || host == null) return LoginPageAction.Ignore
        if (host == "music.youtube.com") return LoginPageAction.Extract

        val isYouTube = host == "youtube.com" || host.endsWith(".youtube.com")
        // accounts.youtube.com is the cookie hand-off step of Google sign-in; let it finish.
        if (!isYouTube || host == "accounts.youtube.com" || !hasAuthCookie) return LoginPageAction.Ignore

        return if (redirectsToMusic < MAX_REDIRECTS_TO_MUSIC) {
            LoginPageAction.OpenYouTubeMusic
        } else {
            LoginPageAction.Extract
        }
    }
}
