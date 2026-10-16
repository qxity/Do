package com.vidsaver.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/** Menyimpan hasil ke Movies/VidSaver (video) dan Music/VidSaver (audio) agar muncul di galeri. */
object Storage {
    data class Saved(val uri: Uri, val mime: String)

    fun mimeOf(ext: String): String = when (ext.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "3gp" -> "video/3gpp"
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "opus", "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase()) ?: "video/mp4"
    }

    fun save(ctx: Context, file: File): Saved {
        val mime = mimeOf(file.extension)
        val audio = mime.startsWith("audio")
        val folder = if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES

        val uri = if (Build.VERSION.SDK_INT >= 29) {
            val resolver = ctx.contentResolver
            val collection = if (audio)
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/VidSaver")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val u = resolver.insert(collection, values) ?: throw IOException("Gagal membuat file di penyimpanan")
            try {
                val out = resolver.openOutputStream(u) ?: throw IOException("Gagal menulis file")
                out.use { o -> file.inputStream().use { it.copyTo(o) } }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(u, values, null, null)
            } catch (e: Exception) {
                resolver.delete(u, null, null)
                throw e
            }
            u
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(folder), "VidSaver").apply { mkdirs() }
            var dest = File(dir, file.name)
            var i = 1
            while (dest.exists()) {
                dest = File(dir, "${file.nameWithoutExtension} ($i).${file.extension}"); i++
            }
            file.copyTo(dest)
            MediaScannerConnection.scanFile(ctx, arrayOf(dest.absolutePath), arrayOf(mime), null)
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", dest)
        }
        return Saved(uri, mime)
    }
}

object FileActions {
    private fun typeOf(ctx: Context, uri: Uri, mime: String?): String =
        mime?.takeIf { it.isNotBlank() }
            ?: runCatching { ctx.contentResolver.getType(uri) }.getOrNull()
            ?: "*/*"

    fun open(ctx: Context, uri: Uri, mime: String?) {
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, typeOf(ctx, uri, mime))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i) } catch (e: Exception) {
            Toast.makeText(ctx, "File tidak bisa dibuka (mungkin sudah dihapus)", Toast.LENGTH_SHORT).show()
        }
    }

    fun share(ctx: Context, uri: Uri, mime: String?) {
        val i = Intent(Intent.ACTION_SEND)
            .setType(typeOf(ctx, uri, mime))
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            ctx.startActivity(Intent.createChooser(i, "Bagikan via").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Tidak bisa membagikan file", Toast.LENGTH_SHORT).show()
        }
    }
}
