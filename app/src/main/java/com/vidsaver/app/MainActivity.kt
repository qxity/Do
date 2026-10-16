package com.vidsaver.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {
    var tab by mutableIntStateOf(0)
    var url by mutableStateOf("")
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var preview by mutableStateOf<VideoPreview?>(null)
    var quality by mutableStateOf(SettingsStore.state.value.defaultQuality)
    var lastClip: String? = null

    fun onSharedUrl(u: String) {
        tab = 0; url = u; lastClip = u
        fetch()
    }

    fun reset() {
        url = ""; preview = null; error = null
    }

    fun fetch() {
        val u = UrlUtil.extract(url) ?: run {
            error = "Link tidak valid. Pastikan diawali http:// atau https://"
            return
        }
        url = u; loading = true; error = null; preview = null
        quality = SettingsStore.state.value.defaultQuality
        viewModelScope.launch {
            try {
                Engine.awaitReady()
                val info = withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().getInfo(YoutubeDLRequest(u).apply { addOption("--no-playlist") })
                }
                preview = VideoPreview(
                    url = u,
                    title = info.title ?: "Tanpa judul",
                    thumbnail = info.thumbnail,
                    uploader = info.uploader,
                    duration = (info.duration as Number?)?.toInt() ?: 0,
                    platform = Platform.of(u),
                )
            } catch (e: Exception) {
                error = Downloader.cleanError(e)
            } finally {
                loading = false
            }
        }
    }

    fun download(ctx: Context) {
        val p = preview ?: return
        Downloader.enqueue(ctx, p.url, p.title, p.thumbnail, quality)
        Toast.makeText(ctx, "Ditambahkan ke antrean", Toast.LENGTH_SHORT).show()
        tab = 1
    }

    fun quickDownload(ctx: Context) {
        val u = UrlUtil.extract(url) ?: run { error = "Link tidak valid"; return }
        val p = preview?.takeIf { it.url == u }
        Downloader.enqueue(ctx, u, p?.title, p?.thumbnail, SettingsStore.state.value.defaultQuality)
        Toast.makeText(ctx, "Unduh cepat dimulai", Toast.LENGTH_SHORT).show()
        reset()
        tab = 1
    }
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        askPermissions()
        setContent { VidSaverTheme { AppRoot(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        val text = when (i?.action) {
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> i.dataString
            else -> null
        }
        UrlUtil.extract(text)?.let { vm.onSharedUrl(it) }
    }

    private fun askPermissions() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT < 29) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (perms.isNotEmpty()) permLauncher.launch(perms.toTypedArray())
    }
}
