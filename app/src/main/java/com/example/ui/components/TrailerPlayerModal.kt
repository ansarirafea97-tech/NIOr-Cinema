package com.example.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.viewmodel.MovieTrailerCandidate
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TrailerPlayerModal(
    movieTitle: String,
    candidates: List<MovieTrailerCandidate>,
    onClose: () -> Unit
) {
    if (candidates.isEmpty()) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var activeIndex by remember(candidates) { mutableIntStateOf(0) }
    val currentCandidate = candidates.getOrElse(activeIndex) { candidates.first() }

    var isFullscreen by remember { mutableStateOf(false) }
    var isLoading by remember(currentCandidate.key) { mutableStateOf(true) }
    var hasPlaybackError by remember(currentCandidate.key) { mutableStateOf(false) }
    var retryNonce by remember(candidates) { mutableIntStateOf(0) }

    var customFullscreenView by remember { mutableStateOf<View?>(null) }
    var customFullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }

    val closeSafely = remember(onClose) {
        {
            try {
                customFullscreenCallback?.onCustomViewHidden()
            } catch (_: Exception) {}
            customFullscreenView = null
            customFullscreenCallback = null
            activeWebView?.let { wv ->
                try {
                    wv.evaluateJavascript("try { if (player && player.stopVideo) player.stopVideo(); } catch(e) {}", null)
                } catch (_: Exception) {}
            }
            onClose()
        }
    }

    // Safety fallback so loading indicator never hangs indefinitely
    LaunchedEffect(currentCandidate.key, retryNonce) {
        isLoading = true
        hasPlaybackError = false
        delay(6500L)
        if (isLoading) {
            isLoading = false
        }
    }

    // Pause/resume WebView with lifecycle
    DisposableEffect(lifecycleOwner, activeWebView) {
        val observer = LifecycleEventObserver { _, event ->
            val wv = activeWebView ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    try {
                        wv.evaluateJavascript("try { if (player && player.pauseVideo) player.pauseVideo(); } catch(e) {}", null)
                    } catch (_: Exception) {}
                    wv.onPause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    wv.onResume()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Ensure Activity window is hardware-accelerated and manage system bar visibility in fullscreen
    val hostView = LocalView.current
    val activityWindow = remember(context) { context.findActivity()?.window }

    DisposableEffect(activityWindow) {
        activityWindow?.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        onDispose {}
    }

    DisposableEffect(isFullscreen, customFullscreenView, activityWindow) {
        if (activityWindow != null) {
            val controller = WindowCompat.getInsetsController(activityWindow, hostView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isFullscreen || customFullscreenView != null) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (activityWindow != null) {
                WindowCompat.getInsetsController(activityWindow, hostView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler {
        when {
            customFullscreenView != null -> {
                try {
                    customFullscreenCallback?.onCustomViewHidden()
                } catch (_: Exception) {}
                customFullscreenView = null
                customFullscreenCallback = null
            }
            isFullscreen -> {
                isFullscreen = false
            }
            else -> {
                closeSafely()
            }
        }
    }

    // In-window full-screen modal overlay (avoids separate Dialog window & Surface offscreen clipping
    // so hardware-accelerated WebView video surfaces attach and render cleanly with audio)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (isFullscreen) 1f else 0.90f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !isFullscreen && customFullscreenView == null
            ) {
                closeSafely()
            },
        contentAlignment = Alignment.Center
    ) {
        if (customFullscreenView != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            (customFullscreenView?.parent as? ViewGroup)?.removeView(customFullscreenView)
                            customFullscreenView?.let { addView(it) }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Floating Close Button inside custom fullscreen view
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        .clickable {
                            try {
                                customFullscreenCallback?.onCustomViewHidden()
                            } catch (_: Exception) {}
                            customFullscreenView = null
                            customFullscreenCallback = null
                        }
                        .testTag("trailer_close_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit Fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        } else {
            val cardShape = if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(22.dp)
            Box(
                modifier = if (isFullscreen) {
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .widthIn(max = 640.dp)
                        .background(Color(0xFF141416), shape = cardShape)
                        .border(1.dp, Color.White.copy(alpha = 0.14f), shape = cardShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* consume clicks inside modal card */ }
                }
            ) {
                Column(
                    modifier = if (isFullscreen) {
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                    } else {
                        Modifier.fillMaxWidth()
                    }
                ) {
                        // Modal Header (Movie Title, Official Badge, Fullscreen Toggle, Close Button)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                            ) {
                                Text(
                                    text = movieTitle,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${currentCandidate.type} • ${currentCandidate.name}",
                                    color = Color.White.copy(alpha = 0.65f),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.5.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Fullscreen Toggle Button
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .border(0.75.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                                        .clickable { isFullscreen = !isFullscreen }
                                        .testTag("trailer_fullscreen_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // Clear Close Button
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .border(0.75.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                                        .clickable { closeSafely() }
                                        .testTag("trailer_close_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Trailer",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        // Video Container
                        Box(
                            modifier = if (isFullscreen) {
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(Color.Black)
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .background(Color.Black)
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!hasPlaybackError) {
                                key(currentCandidate.key, retryNonce) {
                                    YouTubeTrailerWebView(
                                        videoKey = currentCandidate.key,
                                        retryNonce = retryNonce,
                                        onWebViewReady = { wv -> activeWebView = wv },
                                        onWebViewDisposed = { wv ->
                                            if (activeWebView === wv) {
                                                activeWebView = null
                                            }
                                        },
                                        onReady = {
                                            mainHandler.post { isLoading = false }
                                        },
                                        onError = {
                                            mainHandler.post {
                                                isLoading = false
                                                if (activeIndex + 1 < candidates.size) {
                                                    activeIndex += 1
                                                } else {
                                                    hasPlaybackError = true
                                                }
                                            }
                                        },
                                        onShowCustomView = { view, callback ->
                                            mainHandler.post {
                                                customFullscreenView = view
                                                customFullscreenCallback = callback
                                            }
                                        },
                                        onHideCustomView = {
                                            mainHandler.post {
                                                customFullscreenView = null
                                                customFullscreenCallback = null
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                // Clean Graceful Playback Error Fallback
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF0E0E10))
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = "Trailer error",
                                        tint = Color.White.copy(alpha = 0.75f),
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Unable to play embedded trailer",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                activeIndex = 0
                                                hasPlaybackError = false
                                                isLoading = true
                                                retryNonce += 1
                                            },
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                            shape = RoundedCornerShape(percent = 50)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Retry", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }

                                        Button(
                                            onClick = {
                                                openYouTubeTrailer(context, currentCandidate.key)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.White,
                                                contentColor = Color.Black
                                            ),
                                            shape = RoundedCornerShape(percent = 50)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Open YouTube", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // Smooth Loading Overlay
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isLoading && !hasPlaybackError,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF0A0A0C)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.5.dp,
                                            modifier = Modifier.size(30.dp)
                                        )
                                        Text(
                                            text = "Loading official trailer...",
                                            color = Color.White.copy(alpha = 0.72f),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        if (!isFullscreen) {
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }
                }
            }
        }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeTrailerWebView(
    videoKey: String,
    retryNonce: Int,
    onWebViewReady: (WebView) -> Unit,
    onWebViewDisposed: (WebView) -> Unit,
    onReady: () -> Unit,
    onError: (Int) -> Unit,
    onShowCustomView: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onHideCustomView: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val safeKey = remember(videoKey) {
        videoKey.replace(Regex("[^A-Za-z0-9_-]"), "")
    }
    val appOrigin = remember(context) {
        "https://${context.packageName}"
    }
    val baseUrl = remember(appOrigin) {
        "$appOrigin/"
    }

    val htmlContent = remember(safeKey, appOrigin) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
          <meta name="referrer" content="strict-origin-when-cross-origin" />
          <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            html, body { width: 100%; height: 100%; background-color: #000000; overflow: hidden; }
            .container { position: relative; width: 100vw; height: 100vh; background: #000000; }
            #ytplayer, iframe { position: absolute; top: 0; left: 0; width: 100% !important; height: 100% !important; border: 0; display: block; }
          </style>
        </head>
        <body>
          <div class="container">
            <div id="ytplayer"></div>
          </div>
          <script>
            var player = null;
            var fallbackLoaded = false;

            function mountDirectIframe() {
              if (fallbackLoaded) return;
              fallbackLoaded = true;
              var container = document.querySelector('.container');
              if (!container) return;
              container.innerHTML = '';
              var iframe = document.createElement('iframe');
              iframe.id = 'ytplayer';
              iframe.setAttribute('referrerpolicy', 'strict-origin-when-cross-origin');
              iframe.setAttribute('allow', 'autoplay; encrypted-media; fullscreen; picture-in-picture');
              iframe.setAttribute('allowfullscreen', '');
              iframe.src = 'https://www.youtube.com/embed/$safeKey?autoplay=1&playsinline=1&rel=0&fs=1&modestbranding=1';
              iframe.onload = function() {
                try { AndroidTrailerBridge.onReady(); } catch(e) {}
              };
              iframe.onerror = function() {
                try { AndroidTrailerBridge.onError(-2); } catch(e) {}
              };
              container.appendChild(iframe);
            }

            var tag = document.createElement('script');
            tag.src = 'https://www.youtube.com/iframe_api';
            tag.onerror = function() {
              mountDirectIframe();
            };
            var firstScriptTag = document.getElementsByTagName('script')[0];
            firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

            function onYouTubeIframeAPIReady() {
              try {
                player = new YT.Player('ytplayer', {
                  width: '100%',
                  height: '100%',
                  videoId: '$safeKey',
                  playerVars: {
                    'autoplay': 1,
                    'playsinline': 1,
                    'rel': 0,
                    'modestbranding': 1,
                    'fs': 1,
                    'enablejsapi': 1,
                    'origin': '$appOrigin',
                    'widget_referrer': '$appOrigin/'
                  },
                  events: {
                    'onReady': function(event) {
                      try {
                        var iframeEl = event.target.getIframe && event.target.getIframe();
                        if (iframeEl) {
                          iframeEl.setAttribute('referrerpolicy', 'strict-origin-when-cross-origin');
                          iframeEl.setAttribute('allow', 'autoplay; encrypted-media; fullscreen; picture-in-picture');
                        }
                      } catch(e) {}
                      try { AndroidTrailerBridge.onReady(); } catch(e) {}
                      try { event.target.playVideo(); } catch(e) {}
                    },
                    'onStateChange': function(event) {
                      if (event.data === 1 || event.data === 2 || event.data === 3 || event.data === 5) {
                        try { AndroidTrailerBridge.onReady(); } catch(e) {}
                      }
                    },
                    'onError': function(event) {
                      try { AndroidTrailerBridge.onError(event.data || -1); } catch(e) {}
                    }
                  }
                });
              } catch(e) {
                mountDirectIframe();
              }
            }

            setTimeout(function() {
              if (!player) {
                mountDirectIframe();
              }
            }, 4000);
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    val webView = remember(safeKey, retryNonce) {
        var triedDirectUrlFallback = false
        var refreshedVideoSurface = false
        WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            setBackgroundColor(AndroidColor.BLACK)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadsImagesAutomatically = true
                useWideViewPort = true
                loadWithOverviewMode = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun onReady() {
                        if (!refreshedVideoSurface) {
                            refreshedVideoSurface = true
                            post {
                                requestLayout()
                                invalidate()
                            }
                        }
                        onReady()
                    }

                    @JavascriptInterface
                    fun onError(code: Int) {
                        Log.e("TRAILER_DEBUG", "YouTube embedded player error code=$code for videoKey=$safeKey")
                        if (code == 153 && !triedDirectUrlFallback) {
                            triedDirectUrlFallback = true
                            post {
                                val embedUrl = "https://www.youtube.com/embed/$safeKey?autoplay=1&playsinline=1&rel=0&modestbranding=1&fs=1"
                                loadUrl(embedUrl, mapOf("Referer" to baseUrl))
                            }
                        } else {
                            onError(code)
                        }
                    }
                },
                "AndroidTrailerBridge"
            )

            webChromeClient = object : WebChromeClient() {
                override fun getDefaultVideoPoster(): Bitmap {
                    return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
                        eraseColor(AndroidColor.BLACK)
                    }
                }

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view != null && callback != null) {
                        onShowCustomView(view, callback)
                    } else {
                        super.onShowCustomView(view, callback)
                    }
                }

                override fun onHideCustomView() {
                    onHideCustomView()
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.post {
                        view.requestLayout()
                        view.invalidate()
                    }
                    if (url != null && url.contains("/embed/")) {
                        onReady()
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        Log.e(
                            "TRAILER_DEBUG",
                            "WebView main frame error code=${error?.errorCode} desc=${error?.description} url=${request.url}"
                        )
                        onError(error?.errorCode ?: -1)
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    val host = uri.host?.lowercase().orEmpty()
                    val path = uri.path?.lowercase().orEmpty()
                    val isYouTubeWatchNav = (host.contains("youtube.com") && path.startsWith("/watch")) ||
                        host == "youtu.be" ||
                        uri.scheme == "vnd.youtube"
                    if (isYouTubeWatchNav) {
                        val extractedKey = uri.getQueryParameter("v")?.takeIf { it.isNotBlank() }
                            ?: uri.lastPathSegment?.takeIf { host == "youtu.be" && it.isNotBlank() }
                            ?: safeKey
                        openYouTubeTrailer(context, extractedKey)
                        return true
                    }
                    if (host.contains("youtube.com") ||
                        host.contains("youtube-nocookie.com") ||
                        host.contains("googlevideo.com") ||
                        host.contains("ytimg.com") ||
                        host.contains("google.com") ||
                        host == context.packageName.lowercase()
                    ) {
                        return false
                    }
                    return try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
            }

            onResume()
            resumeTimers()
            loadDataWithBaseURL(
                baseUrl,
                htmlContent,
                "text/html",
                "utf-8",
                null
            )
        }
    }

    DisposableEffect(webView) {
        onWebViewReady(webView)
        onDispose {
            onWebViewDisposed(webView)
            try {
                webView.evaluateJavascript("try { if (player && player.stopVideo) player.stopVideo(); } catch(e) {}", null)
            } catch (_: Exception) {}
            try {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.onPause()
                webView.removeJavascriptInterface("AndroidTrailerBridge")
                webView.webChromeClient = null
                webView.removeAllViews()
                webView.destroy()
            } catch (_: Exception) {}
        }
    }

    AndroidView(
        factory = { webView },
        update = { view ->
            view.requestLayout()
        },
        modifier = modifier
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun openYouTubeTrailer(context: Context, videoKey: String) {
    val safeKey = videoKey.replace(Regex("[^A-Za-z0-9_-]"), "")
    if (safeKey.isBlank()) return

    val youtubeWebUrl = Uri.parse("https://www.youtube.com/watch?v=$safeKey")
    val youtubeAppSchemeUrl = Uri.parse("vnd.youtube:$safeKey")
    val pm = context.packageManager

    // 1. Prefer official YouTube Android app with https watch URL
    val youtubeAppIntent = Intent(Intent.ACTION_VIEW, youtubeWebUrl).apply {
        setPackage("com.google.android.youtube")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        if (youtubeAppIntent.resolveActivity(pm) != null) {
            context.startActivity(youtubeAppIntent)
            return
        }
    } catch (_: Exception) {}

    // 2. Try vnd.youtube deep link targeted at official YouTube Android app
    val youtubeDeepLinkIntent = Intent(Intent.ACTION_VIEW, youtubeAppSchemeUrl).apply {
        setPackage("com.google.android.youtube")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(youtubeDeepLinkIntent)
        return
    } catch (_: ActivityNotFoundException) {
    } catch (_: Exception) {}

    // 3. Direct startAttempt on package-pinned web URL in case package visibility hid resolveActivity
    try {
        context.startActivity(youtubeAppIntent)
        return
    } catch (_: ActivityNotFoundException) {
    } catch (_: Exception) {}

    // 4. Graceful browser fallback only if YouTube Android app is unavailable
    val browserFallbackIntent = Intent(Intent.ACTION_VIEW, youtubeWebUrl).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(browserFallbackIntent)
    } catch (e: Exception) {
        Log.e("TRAILER_DEBUG", "Failed to open trailer in YouTube or browser for videoKey=$safeKey", e)
    }
}

