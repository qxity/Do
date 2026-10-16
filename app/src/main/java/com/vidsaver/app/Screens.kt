@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.vidsaver.app

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val dateFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))

// ============================ ROOT ============================

@Composable
fun AppRoot(vm: MainViewModel) {
    val tasks by Downloader.tasks.collectAsStateWithLifecycle()
    val active = tasks.count { it.isActive }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = vm.tab == 0, onClick = { vm.tab = 0 },
                    icon = { Icon(Icons.Default.Home, null) }, label = { Text("Beranda") }
                )
                NavigationBarItem(
                    selected = vm.tab == 1, onClick = { vm.tab = 1 },
                    icon = {
                        BadgedBox(badge = { if (active > 0) Badge { Text("$active") } }) {
                            Icon(Icons.Default.Downloading, null)
                        }
                    },
                    label = { Text("Unduhan") }
                )
                NavigationBarItem(
                    selected = vm.tab == 2, onClick = { vm.tab = 2 },
                    icon = { Icon(Icons.Default.History, null) }, label = { Text("Riwayat") }
                )
                NavigationBarItem(
                    selected = vm.tab == 3, onClick = { vm.tab = 3 },
                    icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Setelan") }
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (vm.tab) {
                0 -> HomeScreen(vm)
                1 -> DownloadsScreen()
                2 -> HistoryScreen()
                else -> SettingsScreen()
            }
        }
    }
}

// ============================ BERANDA ============================

@Composable
fun HomeScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val engine by Engine.status.collectAsStateWithLifecycle()
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    var clipSuggestion by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        delay(400)
        if (settings.autoClipboard && vm.url.isBlank()) {
            val c = UrlUtil.extract(clipboard.getText()?.text)
            if (c != null && c != vm.lastClip) clipSuggestion = c
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("VidSaver", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Unduh video & audio dari YouTube, TikTok, Instagram, Facebook, X, dan ratusan situs lain.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when (engine) {
            EngineStatus.LOADING -> InfoCard(Icons.Default.HourglassTop, "Menyiapkan mesin unduh…", "Hanya lama saat pertama kali dibuka.") {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            EngineStatus.ERROR -> InfoCard(Icons.Default.ErrorOutline, "Mesin unduh gagal disiapkan", Engine.errorMessage ?: "")
            EngineStatus.READY -> Unit
        }

        clipSuggestion?.let { c ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ContentPaste, null)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Link terdeteksi di clipboard", style = MaterialTheme.typography.labelLarge)
                        Text(c, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        vm.url = c; vm.lastClip = c; clipSuggestion = null; vm.fetch()
                    }) { Text("Pakai") }
                    IconButton(onClick = { vm.lastClip = c; clipSuggestion = null }) {
                        Icon(Icons.Default.Close, "Tutup")
                    }
                }
            }
        }

        OutlinedTextField(
            value = vm.url,
            onValueChange = { vm.url = it },
            label = { Text("Tempel link video") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Link, null) },
            trailingIcon = {
                if (vm.url.isNotEmpty()) {
                    IconButton(onClick = { vm.reset() }) { Icon(Icons.Default.Close, "Hapus") }
                } else {
                    IconButton(onClick = { clipboard.getText()?.text?.let { vm.url = it } }) {
                        Icon(Icons.Default.ContentPaste, "Tempel")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.fetch() })
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.fetch() },
                enabled = !vm.loading && vm.url.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cek Video")
            }
            FilledTonalButton(
                onClick = { vm.quickDownload(ctx) },
                enabled = vm.url.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Bolt, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Unduh Cepat")
            }
        }
        Text(
            "Unduh Cepat memakai kualitas default (${settings.defaultQuality.label}) tanpa pratinjau.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (vm.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Mengambil info video…", style = MaterialTheme.typography.bodySmall)
        }

        vm.error?.let {
            InfoCard(Icons.Default.ErrorOutline, "Gagal mengambil video", it, error = true)
        }

        vm.preview?.let { p ->
            PreviewCard(p)
            Text("Pilih format", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Quality.entries.forEach { q ->
                    FilterChip(
                        selected = vm.quality == q,
                        onClick = { vm.quality = q },
                        label = { Text(q.label) },
                        leadingIcon = {
                            Icon(
                                if (q.isAudio) Icons.Default.MusicNote else Icons.Default.Movie,
                                null, Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
            Button(onClick = { vm.download(ctx) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("Unduh ${vm.quality.label}")
            }
        }

        if (vm.preview == null && !vm.loading) {
            InfoCard(
                Icons.Default.Lightbulb, "Tips cepat",
                "Di aplikasi YouTube/TikTok/Instagram/Facebook, tekan Bagikan → pilih VidSaver. Link langsung terbaca otomatis."
            )
        }
    }
}

@Composable
private fun PreviewCard(p: VideoPreview) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (p.thumbnail != null) {
                AsyncImage(
                    model = p.thumbnail, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
            }
            if (p.duration > 0) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                ) {
                    Text(
                        formatDuration(p.duration.toLong()), color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(
                p.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(p.platform.label, p.uploader).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================ UNDUHAN ============================

@Composable
fun DownloadsScreen() {
    val tasks by Downloader.tasks.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Antrean Unduhan", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (tasks.any { !it.isActive }) {
                TextButton(onClick = { Downloader.clearFinished() }) { Text("Bersihkan") }
            }
        }
        if (tasks.isEmpty()) {
            EmptyState(Icons.Default.Downloading, "Belum ada unduhan", "Tempel link di Beranda untuk mulai")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tasks.asReversed(), key = { it.id }) { TaskCard(it) }
            }
        }
    }
}

@Composable
private fun TaskCard(t: DownloadTask) {
    val ctx = LocalContext.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Thumb(t.thumbnail, t.quality.isAudio, Modifier.size(width = 96.dp, height = 54.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        t.title, style = MaterialTheme.typography.titleSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${t.platform.label} • ${t.quality.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            when (t.status) {
                Status.QUEUED -> Text("Menunggu antrean…", style = MaterialTheme.typography.bodySmall)
                Status.RUNNING -> {
                    LinearProgressIndicator(progress = { t.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text("%.1f%%".format(t.progress), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        Text(
                            listOfNotNull(
                                t.speed.ifBlank { null },
                                t.etaSec.takeIf { it > 0 }?.let { "sisa ${formatDuration(it)}" }
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Status.PROCESSING -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Memproses (gabung/konversi)…", style = MaterialTheme.typography.bodySmall)
                }
                Status.DONE -> Text("Selesai ✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Status.FAILED -> Text(
                    t.error ?: "Gagal", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, maxLines = 4
                )
                Status.CANCELLED -> Text("Dibatalkan", style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (t.isActive) TextButton(onClick = { Downloader.cancel(t.id) }) { Text("Batal") }
                if (t.status == Status.FAILED || t.status == Status.CANCELLED) {
                    TextButton(onClick = { Downloader.retry(ctx, t.id) }) { Text("Coba lagi") }
                }
                if (t.status == Status.DONE) {
                    Downloader.firstUri(t)?.let { u ->
                        TextButton(onClick = { FileActions.share(ctx, u, t.savedMime) }) { Text("Bagikan") }
                        TextButton(onClick = { FileActions.open(ctx, u, t.savedMime) }) { Text("Buka") }
                    }
                }
                if (!t.isActive) TextButton(onClick = { Downloader.remove(t.id) }) { Text("Hapus") }
            }
        }
    }
}

// ============================ RIWAYAT ============================

@Composable
fun HistoryScreen() {
    val items by HistoryStore.items.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val filtered = remember(items, query) {
        if (query.isBlank()) items
        else items.filter { it.title.contains(query, true) || it.platform.label.contains(query, true) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Riwayat", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (items.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) { Icon(Icons.Default.DeleteSweep, "Hapus semua") }
            }
        }
        if (items.isNotEmpty()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Cari judul atau platform") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
        }
        if (filtered.isEmpty()) {
            EmptyState(Icons.Default.History, "Riwayat kosong", "File yang selesai diunduh akan muncul di sini")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { HistoryRow(it) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Hapus semua riwayat?") },
            text = { Text("File yang sudah tersimpan di HP tidak ikut terhapus.") },
            confirmButton = {
                TextButton(onClick = { HistoryStore.clear(); confirmClear = false }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun HistoryRow(h: HistoryItem) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    val uri = remember(h.uri) { Uri.parse(h.uri) }
    val isAudio = h.mime.startsWith("audio")

    Card(Modifier.fillMaxWidth().clickable { FileActions.open(ctx, uri, h.mime) }) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(h.thumbnail, isAudio, Modifier.size(width = 96.dp, height = 54.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(h.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${h.platform.label} • ${if (isAudio) "Audio" else "Video"} • ${dateFmt.format(Date(h.time))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Putar / buka") },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                        onClick = { menu = false; FileActions.open(ctx, uri, h.mime) }
                    )
                    DropdownMenuItem(
                        text = { Text("Bagikan file") },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                        onClick = { menu = false; FileActions.share(ctx, uri, h.mime) }
                    )
                    DropdownMenuItem(
                        text = { Text("Salin link asli") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = {
                            menu = false
                            clipboard.setText(AnnotatedString(h.url))
                            Toast.makeText(ctx, "Link disalin", Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Unduh ulang") },
                        leadingIcon = { Icon(Icons.Default.Refresh, null) },
                        onClick = {
                            menu = false
                            Downloader.enqueue(
                                ctx, h.url, h.title, h.thumbnail,
                                if (isAudio) Quality.MP3 else SettingsStore.state.value.defaultQuality
                            )
                            Toast.makeText(ctx, "Ditambahkan ke antrean", Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus dari riwayat") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { menu = false; HistoryStore.remove(h.id) }
                    )
                }
            }
        }
    }
}

// ============================ SETELAN ============================

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val s by SettingsStore.state.collectAsStateWithLifecycle()
    val engine by Engine.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf<String?>(null) }
    var updating by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        Engine.status.first { it != EngineStatus.LOADING }
        version = withContext(Dispatchers.IO) {
            runCatching { YoutubeDL.getInstance().version(ctx.applicationContext) }.getOrNull()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Setelan", style = MaterialTheme.typography.titleLarge)

        Section("Format default") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Quality.entries.forEach { q ->
                    FilterChip(
                        selected = s.defaultQuality == q,
                        onClick = { SettingsStore.update { it.copy(defaultQuality = q) } },
                        label = { Text(q.label) }
                    )
                }
            }
        }

        Section("Unduhan bersamaan: ${s.concurrent}") {
            Slider(
                value = s.concurrent.toFloat(),
                onValueChange = { v -> SettingsStore.update { it.copy(concurrent = v.roundToInt()) } },
                valueRange = 1f..4f,
                steps = 2
            )
        }

        Section("Opsi unduhan") {
            SwitchRow("Deteksi link di clipboard", "Tawarkan link yang baru kamu salin", s.autoClipboard) { v ->
                SettingsStore.update { it.copy(autoClipboard = v) }
            }
            SwitchRow("Unduh seluruh playlist", "Jika link berupa playlist/album/carousel", s.playlist) { v ->
                SettingsStore.update { it.copy(playlist = v) }
            }
            SwitchRow("Sematkan subtitle", "Bahasa Indonesia & Inggris jika tersedia", s.subtitles) { v ->
                SettingsStore.update { it.copy(subtitles = v) }
            }
            SwitchRow("Cover pada audio", "Tempel thumbnail sebagai sampul MP3/M4A", s.embedThumb) { v ->
                SettingsStore.update { it.copy(embedThumb = v) }
            }
            SwitchRow("Lewati sponsor (YouTube)", "Potong segmen sponsor otomatis via SponsorBlock", s.sponsorBlock) { v ->
                SettingsStore.update { it.copy(sponsorBlock = v) }
            }
        }

        Section("Mesin unduh (yt-dlp)") {
            Text("Versi: ${version ?: "-"}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Perbarui kalau unduhan mulai sering gagal — situs sering mengubah sistemnya.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = !updating && engine == EngineStatus.READY,
                onClick = {
                    scope.launch {
                        updating = true; updateMsg = null
                        updateMsg = try {
                            val st = withContext(Dispatchers.IO) {
                                YoutubeDL.getInstance().updateYoutubeDL(ctx.applicationContext, YoutubeDL.UpdateChannel.STABLE)
                            }
                            version = withContext(Dispatchers.IO) {
                                runCatching { YoutubeDL.getInstance().version(ctx.applicationContext) }.getOrNull()
                            }
                            if (st == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE) "Sudah versi terbaru" else "Berhasil diperbarui ✓"
                        } catch (e: Exception) {
                            "Gagal memperbarui: ${e.message}"
                        }
                        updating = false
                    }
                }
            ) {
                if (updating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.SystemUpdate, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Perbarui yt-dlp")
            }
            updateMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
        }

        Section("Lokasi file") {
            Text("Video: Movies/VidSaver\nAudio: Music/VidSaver", style = MaterialTheme.typography.bodyMedium)
        }

        Text(
            "Gunakan untuk konten milikmu sendiri atau yang diizinkan pemiliknya. Hormati hak cipta dan aturan tiap platform.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ============================ KOMPONEN ============================

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun InfoCard(
    icon: ImageVector,
    title: String,
    text: String,
    error: Boolean = false,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val container = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Row(Modifier.padding(12.dp)) {
            Icon(icon, null)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodySmall)
                extra()
            }
        }
    }
}

@Composable
private fun Thumb(url: String?, audio: Boolean, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(if (audio) Icons.Default.MusicNote else Icons.Default.Movie, null)
        if (url != null) {
            AsyncImage(
                model = url, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, desc: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
