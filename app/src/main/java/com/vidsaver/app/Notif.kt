package com.vidsaver.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object Notif {
    private const val CH_PROGRESS = "downloads"
    private const val CH_DONE = "done"

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH_PROGRESS, "Progres unduhan", NotificationManager.IMPORTANCE_LOW)
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_DONE, "Unduhan selesai", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun appIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun progress(ctx: Context, tasks: List<DownloadTask>): Notification {
        val active = tasks.filter { it.isActive }
        val working = active.filter { it.status != Status.QUEUED }
        val avg = if (working.isEmpty()) 0 else working.map { it.progress }.average().toInt()
        val title = if (active.size == 1) active[0].title else "${active.size} unduhan aktif"
        val text = working.firstOrNull()?.let {
            if (it.status == Status.PROCESSING) "Memproses file..."
            else listOf("${it.progress.toInt()}%", it.speed).filter { s -> s.isNotBlank() }.joinToString(" • ")
        } ?: "Menunggu antrean..."
        val indeterminate = working.isEmpty() || working.all { it.status == Status.PROCESSING }
        return NotificationCompat.Builder(ctx, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, avg, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(appIntent(ctx))
            .build()
    }

    fun done(ctx: Context, title: String, uri: Uri, mime: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            ctx, uri.hashCode(), view,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Unduhan selesai ✓")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(uri.hashCode(), n)
    }
}
