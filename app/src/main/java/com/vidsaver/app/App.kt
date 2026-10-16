package com.vidsaver.app

import android.app.Application
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class EngineStatus { LOADING, READY, ERROR }

/** Menyiapkan yt-dlp + ffmpeg di latar belakang saat aplikasi dibuka. */
object Engine {
    val status = MutableStateFlow(EngineStatus.LOADING)
    var errorMessage: String? = null
        private set

    fun init(app: Application) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                status.value = EngineStatus.READY
            } catch (e: Throwable) {
                errorMessage = e.message ?: e.toString()
                status.value = EngineStatus.ERROR
            }
        }
    }

    suspend fun awaitReady() {
        val s = status.first { it != EngineStatus.LOADING }
        if (s == EngineStatus.ERROR) {
            throw IllegalStateException("Mesin unduh gagal disiapkan: $errorMessage")
        }
    }
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        HistoryStore.init(this)
        Notif.createChannels(this)
        Engine.init(this)
    }
}
