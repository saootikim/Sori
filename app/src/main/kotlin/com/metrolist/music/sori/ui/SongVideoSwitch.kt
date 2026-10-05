/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.sori.SoriMusicVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import java.lang.ref.WeakReference

/**
 * YouTube Music's Song / Video switch. The video is youtube.com's own player in a WebView over
 * the album art. The app's player pauses while the video shows and the player's time bar and
 * play button work the video instead. The page reports the video's time twice a second, so
 * whatever ends the video (the Song button, collapsing the player, leaving the app, playback
 * started elsewhere) continues the song from that point.
 */
object SoriVideoMode {
    data class Showing(
        val songId: String,
        val videoId: String,
        val startMs: Long,
        // The video starts the way the song was: playing, or paused at the song's position.
        val autoplay: Boolean,
    )

    data class VideoTime(
        val positionMs: Long,
        val durationMs: Long,
    )

    val showing = MutableStateFlow<Showing?>(null)

    /** Where the video is; null until it has started. */
    val videoTime = MutableStateFlow<VideoTime?>(null)

    /** Whether the video is playing; null until it has started. */
    val videoPlaying = MutableStateFlow<Boolean?>(null)

    internal var webView = WeakReference<WebView>(null)

    fun enter(
        playerConnection: PlayerConnection,
        songId: String,
        videoId: String,
    ) {
        videoTime.value = null
        videoPlaying.value = null
        val playing = playerConnection.player.playWhenReady
        val position = playerConnection.player.currentPosition
        playerConnection.pause()
        showing.value = Showing(songId, videoId, position, autoplay = playing)
    }

    internal fun report(
        from: Showing,
        seconds: Double,
        playing: Boolean,
        durationSeconds: Double,
    ) {
        if (showing.value != from) return
        videoTime.value = VideoTime((seconds * 1000).toLong(), (durationSeconds * 1000).toLong())
        videoPlaying.value = playing
    }

    /** The player's play button while the video shows; false when no video shows. */
    fun togglePlayPause(): Boolean {
        if (showing.value == null) return false
        val view = webView.get() ?: return false
        view.evaluateJavascript(TOGGLE_JS, null)
        return true
    }

    /** A position picked on the player's time bar while the video shows. */
    fun seek(positionMs: Long) {
        if (showing.value == null) return
        val view = webView.get() ?: return
        view.evaluateJavascript("$SEEK_JS(${positionMs / 1000.0})", null)
        // Shown right away; the page's next report confirms it.
        videoTime.value?.let { videoTime.value = it.copy(positionMs = positionMs) }
    }

    /**
     * Back to the song at the video's position (if it is still the same song), playing if the
     * video was. [alreadyPlaying]: playback was started elsewhere, only the position is synced.
     */
    fun leave(
        playerConnection: PlayerConnection,
        alreadyPlaying: Boolean = false,
    ) {
        val current = showing.value ?: return
        showing.value = null
        val time = videoTime.value
        if (time != null && playerConnection.mediaMetadata.value?.id == current.songId) {
            var position = time.positionMs
            playerConnection.player.duration.takeIf { it > 0 }?.let { position = position.coerceAtMost(it - 1000) }
            playerConnection.seekTo(position.coerceAtLeast(0))
        }
        if (!alreadyPlaying && (videoPlaying.value ?: current.autoplay)) playerConnection.play()
    }

    // Both work on the IFrame player and on the mobile watch page's <video>.
    private const val TOGGLE_JS =
        "(function(){var v=document.querySelector('video');if(v){if(v.paused)v.play();else v.pause();return;}" +
            "var p=window.YT&&YT.get&&YT.get('p');if(p){if(p.getPlayerState()===1)p.pauseVideo();else p.playVideo();}})()"
    private const val SEEK_JS =
        "(function(s){var v=document.querySelector('video');if(v){v.currentTime=s;return;}" +
            "var p=window.YT&&YT.get&&YT.get('p');if(p)p.seekTo(s,true);})"

    /** The video finished: on to the next song, like the end of a song. */
    internal fun ended(
        playerConnection: PlayerConnection,
        from: Showing,
    ) {
        if (showing.value != from) return
        showing.value = null
        playerConnection.seekToNext()
        playerConnection.play()
    }
}

/** Song / Video pill under the player's header. Keeps its height while the video is looked up. */
@Composable
fun SoriSongVideoSwitch(
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val metadata by playerConnection.mediaMetadata.collectAsState()
    val showing by SoriVideoMode.showing.collectAsState()
    val song = metadata
    val videoId by produceState<String?>(null, song?.id) {
        value = null
        if (song == null) return@produceState
        value =
            SoriMusicVideo.find(
                songId = song.id,
                title = song.title,
                artist = song.artists.firstOrNull()?.name,
                duration = song.duration.takeIf { it > 0 },
                isVideo = song.isVideoSong,
            )
    }

    Box(modifier = modifier.height(40.dp), contentAlignment = Alignment.Center) {
        val id = videoId
        if (song != null && id != null) {
            val isVideo = showing?.songId == song.id
            Row(
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .background(textColor.copy(alpha = 0.12f))
                        .padding(3.dp),
            ) {
                SwitchSegment(stringResource(R.string.sori_song), selected = !isVideo, textColor = textColor) {
                    SoriVideoMode.leave(playerConnection)
                }
                SwitchSegment(stringResource(R.string.sori_video), selected = isVideo, textColor = textColor) {
                    if (!isVideo) SoriVideoMode.enter(playerConnection, song.id, id)
                }
            }
        }
    }
}

@Composable
private fun SwitchSegment(
    label: String,
    selected: Boolean,
    textColor: Color,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) Color.Black else textColor.copy(alpha = 0.8f),
        modifier =
            Modifier
                .clip(CircleShape)
                .background(if (selected) Color.White else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 6.dp),
    )
}

/** The video over the album art, sized like it, while Video is selected. */
@Composable
fun SoriVideoPane(
    size: Dp,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val metadata by playerConnection.mediaMetadata.collectAsState()
    val showingState by SoriVideoMode.showing.collectAsState()
    val showing = showingState?.takeIf { it.songId == metadata?.id } ?: return

    // Playback started from the controls, notification or headset: the song takes over.
    LaunchedEffect(showing) {
        playerConnection.isPlaying.dropWhile { it }.first { it }
        SoriVideoMode.leave(playerConnection, alreadyPlaying = true)
    }
    // Like a video app, the screen stays on while the video plays. This uses the view's flag, not
    // the window flag that the player and lyrics screens set and clear.
    val view = LocalView.current
    val videoPlaying by SoriVideoMode.videoPlaying.collectAsState()
    DisposableEffect(view, videoPlaying) {
        view.keepScreenOn = videoPlaying == true
        onDispose { view.keepScreenOn = false }
    }
    // A WebView can't play in the background, and the pane goes away when the player collapses
    // or the song changes: in all of these the song continues instead.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, showing) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) SoriVideoMode.leave(playerConnection)
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (SoriVideoMode.showing.value == showing) SoriVideoMode.leave(playerConnection)
        }
    }

    Box(
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
                .background(Color.Black)
                // Being hit is enough to keep swipes from reaching the album art pager underneath.
                // Nothing is consumed: consumed moves make Compose cancel the WebView's touches.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent()
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { context ->
                soriVideoWebView(context, showing, playerConnection).also { SoriVideoMode.webView = WeakReference(it) }
            },
            onRelease = { view ->
                if (SoriVideoMode.webView.get() === view) SoriVideoMode.webView.clear()
                view.destroy()
            },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun soriVideoWebView(
    context: Context,
    showing: SoriVideoMode.Showing,
    playerConnection: PlayerConnection,
): WebView {
    val main = Handler(Looper.getMainLooper())
    val startSeconds = showing.startMs / 1000.0
    return WebView(context).apply {
        // AndroidView's default WRAP_CONTENT makes WebView lay pages out with a zero viewport
        // height, which collapses the player's height: 100% to nothing.
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(android.graphics.Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        webChromeClient = WebChromeClient()
        webViewClient =
            object : WebViewClient() {
                override fun onPageFinished(
                    view: WebView,
                    url: String?,
                ) {
                    if (url?.startsWith(WATCH_PAGE) == true) view.evaluateJavascript(WATCH_PAGE_REPORTER, null)
                }
            }
        addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onTime(
                    seconds: Double,
                    playing: Boolean,
                    durationSeconds: Double,
                ) = SoriVideoMode.report(showing, seconds, playing, durationSeconds)

                @JavascriptInterface
                fun onEnded() = main.post { SoriVideoMode.ended(playerConnection, showing) }

                // Embedding disabled by the uploader (101, 150) or refused (152, 153): the mobile
                // watch page plays everything.
                @JavascriptInterface
                fun onError(code: Int) =
                    main.post {
                        if (SoriVideoMode.showing.value == showing) {
                            loadUrl("$WATCH_PAGE?v=${showing.videoId}&t=${startSeconds.toInt()}s")
                        }
                    }
            },
            "Sori",
        )
        val origin = "https://${context.packageName}"
        loadDataWithBaseURL(origin, iframePlayerHtml(showing.videoId, startSeconds, showing.autoplay, origin), "text/html", "utf-8", null)
    }
}

private const val WATCH_PAGE = "https://m.youtube.com/watch"

private const val WATCH_PAGE_REPORTER =
    "(function(){if(window.soriPoll)return;window.soriPoll=setInterval(function(){" +
        "var v=document.querySelector('video');if(!v)return;Sori.onTime(v.currentTime,!v.paused&&!v.ended,v.duration||0);" +
        "if(v.ended&&!window.soriEnded){window.soriEnded=true;Sori.onEnded();}},500);})()"

// Only a started video reports its time (a cued one says 0). Ending right after it started means
// it started past its end (a song longer than its video), so it plays from the top instead.
private fun iframePlayerHtml(
    videoId: String,
    startSeconds: Double,
    autoplay: Boolean,
    origin: String,
): String =
    """
    <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
    <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{width:100%;height:100%}</style>
    </head><body><div id="p"></div>
    <script src="https://www.youtube.com/iframe_api"></script>
    <script>
    var playingSince=0;
    function onYouTubeIframeAPIReady(){
      new YT.Player('p',{videoId:'$videoId',
        playerVars:{autoplay:${if (autoplay) 1 else 0},start:${startSeconds.toInt()},playsinline:1,rel:0,origin:'$origin'},
        events:{
          onReady:function(e){
            if($autoplay){e.target.seekTo($startSeconds,true);e.target.playVideo();}
            setInterval(function(){
              var st=e.target.getPlayerState();
              if(st===1||st===2||st===3)Sori.onTime(e.target.getCurrentTime(),st===1,e.target.getDuration()||0);
            },500);
          },
          onStateChange:function(e){
            if(e.data===1&&!playingSince)playingSince=Date.now();
            if(e.data===0){
              if(!playingSince||Date.now()-playingSince<3000){e.target.seekTo(0,true);e.target.playVideo();}
              else Sori.onEnded();
            }
          },
          onError:function(e){Sori.onError(e.data);}
        }});
    }
    </script></body></html>
    """.trimIndent()
