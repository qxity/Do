package com.vidsaver.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class AppSettings(
    val defaultQuality: Quality = Quality.P720,
    val concurrent: Int = 2,
    val autoClipboard: Boolean = true,
    val playlist: Boolean = false,
    val subtitles: Boolean = false,
    val embedThumb: Boolean = true,
    val sponsorBlock: Boolean = false,
)

object SettingsStore {
    private lateinit var p: SharedPreferences
    private val _state = MutableStateFlow(AppSettings())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    fun init(ctx: Context) {
        p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _state.value = AppSettings(
            defaultQuality = runCatching { Quality.valueOf(p.getString("quality", "P720")!!) }
                .getOrDefault(Quality.P720),
            concurrent = p.getInt("concurrent", 2),
            autoClipboard = p.getBoolean("clip", true),
            playlist = p.getBoolean("playlist", false),
            subtitles = p.getBoolean("subs", false),
            embedThumb = p.getBoolean("thumb", true),
            sponsorBlock = p.getBoolean("sponsor", false),
        )
    }

    fun update(f: (AppSettings) -> AppSettings) {
        val s = f(_state.value)
        _state.value = s
        p.edit()
            .putString("quality", s.defaultQuality.name)
            .putInt("concurrent", s.concurrent)
            .putBoolean("clip", s.autoClipboard)
            .putBoolean("playlist", s.playlist)
            .putBoolean("subs", s.subtitles)
            .putBoolean("thumb", s.embedThumb)
            .putBoolean("sponsor", s.sponsorBlock)
            .apply()
    }
}

object HistoryStore {
    private lateinit var p: SharedPreferences
    private val _items = MutableStateFlow<List<HistoryItem>>(emptyList())
    val items: StateFlow<List<HistoryItem>> = _items.asStateFlow()

    fun init(ctx: Context) {
        p = ctx.getSharedPreferences("history", Context.MODE_PRIVATE)
        _items.value = load()
    }

    @Synchronized fun add(item: HistoryItem) {
        _items.value = (listOf(item) + _items.value).take(500); save()
    }

    @Synchronized fun remove(id: String) {
        _items.value = _items.value.filterNot { it.id == id }; save()
    }

    @Synchronized fun clear() {
        _items.value = emptyList(); save()
    }

    private fun save() {
        val arr = JSONArray()
        _items.value.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("title", it.title).put("url", it.url)
                    .put("thumb", it.thumbnail ?: "").put("uri", it.uri).put("mime", it.mime)
                    .put("pf", it.platform.name).put("t", it.time)
            )
        }
        p.edit().putString("items", arr.toString()).apply()
    }

    private fun load(): List<HistoryItem> = runCatching {
        val arr = JSONArray(p.getString("items", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            HistoryItem(
                id = o.getString("id"),
                title = o.getString("title"),
                url = o.getString("url"),
                thumbnail = o.optString("thumb").ifBlank { null },
                uri = o.getString("uri"),
                mime = o.optString("mime"),
                platform = runCatching { Platform.valueOf(o.getString("pf")) }.getOrDefault(Platform.OTHER),
                time = o.getLong("t"),
            )
        }
    }.getOrDefault(emptyList())
}
