package com.vidsaver.app

import android.content.Context
import android.net.Uri
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** Antrean unduhan dengan batas unduhan bersamaan. */
object Downloader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val running = mutableSetOf<String>()
    private val lock = Any()

    private val mediaExt = setOf(
        "mp4", "mkv", "webm", "mov", "m4v", "3gp",
        "mp3", "m4a", "aac", "opus", "ogg", "flac", "wav"
    )
    private val speedRegex = Regex("""at\s+~?\s*(\d[\d.]*\s*[KMG]?i?B/s)""")
    private val processingTags = listOf(
        "[Merger]", "[ExtractAudio]", "[EmbedThumbnail]", "[EmbedSubtitle]",
        "[Metadata]", "[SponsorBlock]", "[ModifyChapters]", "[VideoRemuxer]", "[FixupM3u8]"
    )

    fun enqueue(ctx: Context, url: String, title: String?, thumbnail: String?, quality: Quality) {
        val app = ctx.applicationContext
        val task = DownloadTask(
            id = UUID.randomUUID().toString().substring(0, 8),
            url = url,
            title = title?.takeIf { it.isNotBlank() } ?: url,
            thumbnail = thumbnail,
            quality = quality,
            platform = Platform.of(url),
        )
        _tasks.update { it + task }
        DownloadService.start(app)
        pump(app)
    }

    fun cancel(id: String) {
        val t = get(id) ?: return
        update(id) { it.copy(status = Status.CANCELLED) }
        if (t.status != Status.QUEUED) {
            runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
        }
    }

    fun retry(ctx: Context, id: String) {
        update(id) { it.copy(status = Status.QUEUED, progress = 0f, error = null, speed = "", etaSec = -1) }
        DownloadService.start(ctx.applicationContext)
        pump(ctx.applicationContext)
    }

    fun remove(id: String) = _tasks.update { list -> list.filterNot { it.id == id } }

    fun clearFinished() = _tasks.update { list -> list.filter { it.isActive } }

    fun cleanError(e: Throwable): String {
        val lines = (e.message ?: e.toString()).lines().map { it.trim() }.filter { it.isNotEmpty() }
        val line = lines.lastOrNull { it.startsWith("ERROR") } ?: lines.lastOrNull() ?: "Terjadi kesalahan"
        return line.removePrefix("ERROR:").trim().take(300)
    }

    private fun get(id: String) = _tasks.value.find { it.id == id }

    private fun update(id: String, f: (DownloadTask) -> DownloadTask) =
        _tasks.update { list -> list.map { if (it.id == id) f(it) else it } }

    private fun pump(ctx: Context) {
        val toStart = synchronized(lock) {
            val limit = SettingsStore.state.value.concurrent.coerceIn(1, 4)
            val free = (limit - running.size).coerceAtLeast(0)
            val queued = _tasks.value.filter { it.status == Status.QUEUED && it.id !in running }
            queued.take(free).also { list -> list.forEach { running += it.id } }
        }
        toStart.forEach { t -> scope.launch { runTask(ctx, t) } }
    }

    private suspend fun runTask(ctx: Context, t: DownloadTask) {
        val dir = File(ctx.cacheDir, "dl/${t.id}")
        try {
            Engine.awaitReady()
            if (get(t.id)?.status != Status.QUEUED) return
            update(t.id) { it.copy(status = Status.RUNNING, progress = 0f, error = null) }
            dir.deleteRecursively()
            dir.mkdirs()

            val request = buildRequest(t, dir, SettingsStore.state.value)
            YoutubeDL.getInstance().execute(request, t.id) { progress, eta, line ->
                if (get(t.id)?.status == Status.CANCELLED) return@execute
                val processing = progress >= 100f || processingTags.any { line.contains(it) }
                update(t.id) {
                    it.copy(
                        progress = if (progress >= 0f) progress.coerceAtMost(100f) else it.progress,
                        etaSec = eta,
                        speed = speedRegex.find(line)?.groupValues?.get(1) ?: it.speed,
                        status = if (processing) Status.PROCESSING else Status.RUNNING,
                    )
                }
            }

            val files = dir.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in mediaExt }
                .sortedBy { it.name }
                .toList()
            if (files.isEmpty()) throw IllegalStateException("File hasil unduhan tidak ditemukan")

            val saved = files.map { f ->
                val s = Storage.save(ctx, f)
                val cleanTitle = f.nameWithoutExtension.replace(Regex("""\s\[[^\]]+]$"""), "")
                HistoryStore.add(
                    HistoryItem(
                        id = UUID.randomUUID().toString(),
                        title = if (files.size == 1 && t.title != t.url) t.title else cleanTitle,
                        url = t.url,
                        thumbnail = t.thumbnail,
                        uri = s.uri.toString(),
                        mime = s.mime,
                        platform = t.platform,
                        time = System.currentTimeMillis(),
                    )
                )
                s to cleanTitle
            }

            val first = saved.first()
            val finalTitle = if (t.title == t.url) first.second else t.title
            update(t.id) {
                it.copy(
                    status = Status.DONE, progress = 100f, title = finalTitle,
                    savedUris = saved.map { s -> s.first.uri.toString() },
                    savedMime = first.first.mime,
                )
            }
            val doneText = if (saved.size > 1) "$finalTitle (+${saved.size - 1} file)" else finalTitle
            Notif.done(ctx, doneText, first.first.uri, first.first.mime)
        } catch (e: Throwable) {
            if (get(t.id)?.status != Status.CANCELLED) {
                update(t.id) { it.copy(status = Status.FAILED, error = cleanError(e)) }
            }
        } finally {
            dir.deleteRecursively()
            synchronized(lock) { running -= t.id }
            pump(ctx)
        }
    }

    private fun buildRequest(t: DownloadTask, dir: File, s: AppSettings) = YoutubeDLRequest(t.url).apply {
        addOption("-o", "${dir.absolutePath}/%(title).100B [%(id)s].%(ext)s")
        addOption("--no-mtime")
        addOption(if (s.playlist) "--yes-playlist" else "--no-playlist")
        addOption("--embed-metadata")

        if (t.quality.isAudio) {
            addOption("-x")
            addOption("--audio-format", t.quality.audioFormat!!)
            addOption("--audio-quality", "0")
            if (s.embedThumb) addOption("--embed-thumbnail")
        } else {
            val h = t.quality.height
            if (h == null) {
                addOption("-f", "bv*+ba/b")
            } else {
                addOption("-f", "bv*[height<=$h]+ba/b[height<=$h]/b")
                addOption("-S", "res,vcodec:h264,acodec:m4a")
            }
            addOption("--merge-output-format", "mp4")
            if (s.subtitles) {
                addOption("--write-subs")
                addOption("--sub-langs", "id.*,en.*")
                addOption("--embed-subs")
            }
        }
        if (s.sponsorBlock && t.platform == Platform.YOUTUBE) {
            addOption("--sponsorblock-remove", "sponsor,selfpromo,interaction")
        }
    }

    fun firstUri(t: DownloadTask): Uri? = t.savedUris.firstOrNull()?.let { Uri.parse(it) }
}
