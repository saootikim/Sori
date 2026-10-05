/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
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

/**
 * YouTube Music's Song / Video switch. The video is youtube.com's own player in a WebView over
 * the album art. The app's player pauses while the video shows; the page reports the video's
 * time twice a second, so whatever ends the video (the Song button, collapsing the player,
 * leaving the app, playback started elsewhere) continues the song from that point.
 */
object SoriVideoMode {
    data class Showing(
        val songId: String,
        val videoId: String,
        val startMs: Long,
    )

    val showing = MutableStateFlow<Showing?>(null)

    @Volatile private var videoSeconds: Double? = null

    @Volatile private var videoPlaying: Boolean? = null
    private var songWasPlaying = false

    fun enter(
        playerConnection: PlayerConnection,
        songId: String,
        videoId: String,
    ) {
        videoSeconds = null
        videoPlaying = null
        songWasPlaying = playerConnection.player.playWhenReady
        val position = playerConnection.player.currentPosition
        playerConnection.pause()
        showing.value = Showing(songId, videoId, position)
    }

    internal fun report(
        from: Showing,
        seconds: Double,
        playing: Boolean,
    ) {
        if (showing.value != from) return
        videoSeconds = seconds
        videoPlaying = playing
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
        val seconds = videoSeconds
        if (seconds != null && seconds >= 0 && playerConnection.mediaMetadata.value?.id == current.songId) {
            var position = (seconds * 1000).toLong()
            playerConnection.player.duration.takeIf { it > 0 }?.let { position = position.coerceAtMost(it - 1000) }
            playerConnection.seekTo(position.coerceAtLeast(0))
        }
        if (!alreadyPlaying && (videoPlaying ?: songWasPlaying)) playerConnection.play()
    }

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
                // Keeps swipes on the video from changing the song underneath.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { context -> soriVideoWebView(context, showing, playerConnection) },
            onRelease = { it.destroy() },
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
                ) = SoriVideoMode.report(showing, seconds, playing)

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
        loadDataWithBaseURL(origin, iframePlayerHtml(showing.videoId, startSeconds, origin), "text/html", "utf-8", null)
    }
}

private const val WATCH_PAGE = "https://m.youtube.com/watch"

private const val WATCH_PAGE_REPORTER =
    "(function(){if(window.soriPoll)return;window.soriPoll=setInterval(function(){" +
        "var v=document.querySelector('video');if(!v)return;Sori.onTime(v.currentTime,!v.paused&&!v.ended);" +
        "if(v.ended&&!window.soriEnded){window.soriEnded=true;Sori.onEnded();}},500);})()"

// A start past the video's end (a song longer than its video) starts the video from the top.
private fun iframePlayerHtml(
    videoId: String,
    startSeconds: Double,
    origin: String,
): String =
    """
    <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
    <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{width:100%;height:100%}</style>
    </head><body><div id="p"></div>
    <script src="https://www.youtube.com/iframe_api"></script>
    <script>
    var player;
    function onYouTubeIframeAPIReady(){
      player=new YT.Player('p',{videoId:'$videoId',
        playerVars:{autoplay:1,playsinline:1,rel:0,origin:'$origin'},
        events:{
          onReady:function(e){
            var d=e.target.getDuration(),s=$startSeconds;
            if(d&&s>d-5)s=0;
            e.target.seekTo(s,true);e.target.playVideo();
            setInterval(function(){Sori.onTime(e.target.getCurrentTime(),e.target.getPlayerState()===1);},500);
          },
          onStateChange:function(e){if(e.data===0)Sori.onEnded();},
          onError:function(e){Sori.onError(e.data);}
        }});
    }
    </script></body></html>
    """.trimIndent()
