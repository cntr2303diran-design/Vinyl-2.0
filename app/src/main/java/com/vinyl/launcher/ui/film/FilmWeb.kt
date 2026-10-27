package com.vinyl.launcher.ui.film

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.net.http.SslError
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.vinyl.launcher.data.FilmRules
import com.vinyl.launcher.data.NavDecision
import kotlin.math.roundToInt

/*
 * WebView aman untuk layar TV. Prinsip: halaman film adalah isi PIHAK LAIN — ia hanya boleh menampilkan dirinya dan memutar
 * videonya, tidak lebih.
 *  - JavaScript menyala (situs film butuh), tetapi TANPA jembatan ke kode aplikasi (tidak ada addJavascriptInterface)
 *  - hanya https ke situs publik (tautan http dinaikkan, skema lain/alamat IP/localhost diblokir; lihat FilmRules.checkNavigation)
 *  - tanpa akses berkas & content://, tanpa lokasi, tanpa kamera/mikrofon, tanpa unduhan, tanpa pemilih berkas,
 *    tanpa jendela baru, tanpa dialog JavaScript, tanpa cookie pihak ketiga, konten campuran dilarang, Safe Browsing menyala
 *  - proses render yang mati ditangani (tidak ikut mematikan aplikasi)
 * Cookie & penyimpanan situs hanya ada di perangkat ini (tanpa pelacakan); bisa dihapus dari pengaturan.
 */

/** Keadaan halaman film untuk layar TV. */
enum class FilmLoad { IDLE, LOADING, READY, ERROR, CRASHED }

/**
 * Pemegang WebView film. Hidup selama beranda ada (bukan selama satu layar), sehingga film tidak dimuat ulang ketika
 * halaman TV berpindah antara layar TV dan mode Teater: tampilan yang sama dipasang bergantian ke wadah yang berbeda.
 * Semua anggota dipanggil dari thread utama.
 */
@Stable
class FilmWebHolder(private val context: Context) {
    var load by mutableStateOf(FilmLoad.IDLE)
        private set
    var progress by mutableIntStateOf(0)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set

    /** Tampilan layar penuh yang diminta halaman (tombol layar penuh pemutarnya); null = tidak ada. */
    var customView by mutableStateOf<View?>(null)
        private set

    /** Bertambah setiap WebView baru dibuat (mis. setelah proses halaman mati); wadah yang tampil memasang yang baru. */
    var epoch by mutableIntStateOf(0)
        private set

    /** Dipanggil dengan hasil pembacaan video tiap [poll]; diatur oleh pemilik. */
    var onVideo: (VideoStatus?) -> Unit = {}

    private var web: WebView? = null
    private var homeUrl = ""
    private var clearHistoryNext = false
    private var failed = false
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var active = true

    /** WebView untuk dipasang ke wadah; dibuat bila belum ada. */
    fun view(): WebView = web ?: create().also { web = it; epoch++ }

    /** WebView yang ada sekarang, tanpa membuat yang baru. */
    fun current(): WebView? = web

    /** Kaset film masuk: buka [url] (alamat HTTPS yang sudah dinormalkan). Riwayat kaset sebelumnya dibuang. */
    fun open(url: String) {
        homeUrl = url
        failed = false
        error = ""
        load = FilmLoad.LOADING
        progress = 0
        clearHistoryNext = true
        view().loadUrl(url)
    }

    /** Sinyal hilang / proses render mati: coba lagi dari alamat awal kaset. */
    fun retry() {
        if (homeUrl.isEmpty()) return
        open(homeUrl)
    }

    /** Kaset film keluar dari deck: hentikan, buang riwayat, lepaskan WebView. */
    fun close() {
        if (web == null && load == FilmLoad.IDLE) return
        exitCustomView()
        discard()
        homeUrl = ""
        load = FilmLoad.IDLE
        progress = 0
        canGoBack = false
        error = ""
        onVideo(null)
    }

    /**
     * Lepaskan dan hancurkan WebView. Bila proses render-nya mati ([crashed]) ia tidak boleh disentuh lagi selain dilepas
     * dan dihancurkan; selain itu halamannya dikosongkan dulu supaya video & suara berhenti.
     */
    private fun discard(crashed: Boolean = false) {
        val v = web ?: return
        web = null
        (v.parent as? ViewGroup)?.removeView(v)
        if (!crashed) {
            v.stopLoading()
            v.webChromeClient = null
            v.loadUrl("about:blank")
            v.clearHistory()
        }
        v.removeAllViews()
        v.destroy()
    }

    /** Beranda tidak tampil / ruang TV ditinggalkan: jeda video dan proses halaman. Kembali tampil: lanjutkan proses (video tetap jeda). */
    fun setActive(on: Boolean) {
        if (on == active) return
        active = on
        val v = web ?: return
        if (on) {
            v.onResume()
        } else {
            v.evaluateJavascript(FilmProbe.PAUSE_JS, null)
            v.onPause()
        }
    }

    /** Baca keadaan video dan teruskan ke [onVideo]. */
    fun poll() {
        val v = web ?: return
        if (load == FilmLoad.IDLE || load == FilmLoad.CRASHED || !active) return
        v.evaluateJavascript(FilmProbe.STATUS_JS) { raw -> onVideo(FilmProbe.parse(raw)) }
    }

    fun toggle() = command(FilmProbe.TOGGLE_JS)

    fun seekBy(deltaMs: Long) = command(FilmProbe.seekByJs(deltaMs))

    fun seekTo(positionMs: Long) = command(FilmProbe.seekToJs(positionMs))

    private fun command(js: String) {
        val v = web ?: return
        v.evaluateJavascript(js, null)
        // Tampilan (VFD, penghitung) menyusul pembacaan berikutnya, tidak menunggu satu detik penuh
        v.postDelayed({ poll() }, 300L)
    }

    /** Halaman sebelumnya; bila tidak ada, kembali ke alamat awal kaset. */
    fun backOrHome() {
        val v = web ?: return
        if (v.canGoBack()) v.goBack() else if (homeUrl.isNotEmpty()) v.loadUrl(homeUrl)
    }

    fun exitCustomView() {
        val cb = customCallback
        customCallback = null
        customView = null
        cb?.onCustomViewHidden()
    }

    private fun fail(message: String) {
        failed = true
        error = message
        load = FilmLoad.ERROR
    }

    // JavaScript harus menyala (situs film memutar videonya lewat skrip). Risikonya ditekan lewat aturan di atas:
    // tanpa jembatan ke aplikasi, hanya https ke situs publik, tanpa akses berkas.
    @SuppressLint("SetJavaScriptEnabled")
    private fun create(): WebView {
        val v = WebView(context)
        if (!active) v.onPause()
        v.setBackgroundColor(AndroidColor.BLACK)
        v.overScrollMode = View.OVER_SCROLL_NEVER
        v.isVerticalScrollBarEnabled = false
        v.isHorizontalScrollBarEnabled = false
        v.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Tombol PLAY deck menjalankan video lewat skrip (bukan sentuhan di halaman)
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            useWideViewPort = true
            loadWithOverviewMode = true
            // Tata letak halaman tidak ikut skala huruf sistem (layar TV adalah "monitor kecil")
            textZoom = 100
            builtInZoomControls = false
            displayZoomControls = false
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(v, false)
        v.setDownloadListener { _, _, _, _, _ -> }
        v.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val r = request ?: return true
                return when (val d = FilmRules.checkNavigation(r.url.toString())) {
                    NavDecision.Allow -> false
                    is NavDecision.Redirect -> {
                        if (r.isForMainFrame) view?.loadUrl(d.httpsUrl)
                        true
                    }
                    NavDecision.Block -> true
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                failed = false
                error = ""
                load = FilmLoad.LOADING
                canGoBack = view?.canGoBack() == true
            }

            // Dipanggil juga untuk perpindahan halaman tanpa muat ulang (aplikasi satu halaman): tombol Kembali ikut benar
            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                canGoBack = view?.canGoBack() == true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (clearHistoryNext) {
                    clearHistoryNext = false
                    view?.clearHistory()
                }
                canGoBack = view?.canGoBack() == true
                if (!failed) load = FilmLoad.READY
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame != true) return
                fail(describe(error?.errorCode ?: WebViewClient.ERROR_UNKNOWN))
            }

            // Sertifikat yang tidak sah tidak pernah diteruskan
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel()
                fail("Sambungan ke situs ini tidak aman.")
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                exitCustomView()
                discard(crashed = true)
                error = "Tayangan terhenti. Ketuk untuk memuat ulang."
                load = FilmLoad.CRASHED
                onVideo(null)
                return true
            }
        }
        v.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress = newProgress
            }

            override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
                if (view == null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customCallback?.onCustomViewHidden()
                customCallback = callback
                customView = view
            }

            override fun onHideCustomView() {
                customCallback = null
                customView = null
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.deny()
            }

            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                callback?.invoke(origin, false, false)
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                result?.cancel()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                result?.cancel()
                return true
            }

            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                result?.cancel()
                return true
            }

            override fun onJsBeforeUnload(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                result?.confirm()
                return true
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: WebChromeClient.FileChooserParams?,
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                return true
            }
        }
        return v
    }

    private fun describe(code: Int): String = when (code) {
        WebViewClient.ERROR_HOST_LOOKUP, WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_TIMEOUT, WebViewClient.ERROR_IO ->
            "Tidak bisa tersambung ke situs. Periksa internetnya."
        WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> "Sambungan ke situs ini tidak aman."
        else -> "Situs tidak bisa dibuka."
    }
}

/** Cookie & penyimpanan situs film (milik WebView, di perangkat ini saja). */
object FilmSiteData {
    /** Hapus cookie, penyimpanan lokal, dan cache semua situs film. */
    fun clear(context: Context) {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        // Cache disk dibersihkan lewat satu WebView sementara (WebView memakai satu cache untuk seluruh aplikasi)
        WebView(context).apply {
            clearCache(true)
            destroy()
        }
    }
}

/**
 * Wadah yang memasang WebView pada ukuran "monitor" virtual lalu mengecilkannya ke ukuran layar TV: halaman dilukis
 * dengan lebar paling sedikit [minVirtualDp] (supaya tata letak seluler tetap terbaca) dan dikecilkan oleh View itu
 * sendiri (skala View, bukan skala Compose — sentuhan dipetakan sistem View dengan benar). Mode Teater memakai 0: skala 1.
 */
internal class ScaledSlot(context: Context) : FrameLayout(context) {
    var minVirtualDp: Float = 0f
    private var web: View? = null

    fun attach(v: View) {
        (v.parent as? ViewGroup)?.removeView(v)
        web = v
        addView(v, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** Pasang [v] bila belum menjadi isi wadah ini (mis. WebView baru setelah proses halaman mati). */
    fun attachIfNeeded(v: View) {
        if (web === v && v.parent === this) return
        detach()
        attach(v)
    }

    fun detach() {
        web?.let { if (it.parent === this) removeView(it) }
        web = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val c = web?.takeIf { it.parent === this } ?: return
        if (w <= 0 || h <= 0) return
        val minW = minVirtualDp * resources.displayMetrics.density
        val vw = maxOf(w.toFloat(), minW)
        val scale = w / vw
        val vh = h / scale
        c.measure(
            MeasureSpec.makeMeasureSpec(vw.roundToInt().coerceAtLeast(1), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(vh.roundToInt().coerceAtLeast(1), MeasureSpec.EXACTLY),
        )
        c.pivotX = 0f
        c.pivotY = 0f
        c.scaleX = scale
        c.scaleY = scale
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val c = web?.takeIf { it.parent === this } ?: return
        c.layout(0, 0, c.measuredWidth, c.measuredHeight)
    }
}

/** Pasang WebView milik [holder] ke tata letak Compose. */
@Composable
internal fun FilmWebSlot(holder: FilmWebHolder, minVirtualDp: Float, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            ScaledSlot(ctx).also {
                it.minVirtualDp = minVirtualDp
                it.attach(holder.view())
            }
        },
        update = { slot ->
            // epoch dibaca di sini agar wadah memasang WebView baru ketika holder membuatnya (setelah halaman mati lalu dicoba lagi)
            if (holder.epoch >= 0) holder.current()?.let { slot.attachIfNeeded(it) }
            if (slot.minVirtualDp != minVirtualDp) {
                slot.minVirtualDp = minVirtualDp
                slot.requestLayout()
            }
        },
        onRelease = { it.detach() },
        modifier = modifier,
    )
}
