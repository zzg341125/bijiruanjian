package io.github.latticenotes.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.latticenotes.MainActivity
import io.github.latticenotes.R
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    fun schedule(context: Context, noteId: Long, title: String, atMillis: Long) {
        val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(1_000)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putLong("noteId", noteId)
                    .putString("title", title.ifBlank { "笔记提醒" })
                    .build(),
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("note-reminder-$noteId", ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, noteId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("note-reminder-$noteId")
    }
}

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val noteId = inputData.getLong("noteId", 0)
        val title = inputData.getString("title") ?: "笔记提醒"
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra("noteId", noteId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            noteId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, "note_reminders")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("你为该笔记设置的提醒时间已到")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        if (canNotify) {
            runCatching {
                NotificationManagerCompat.from(applicationContext)
                    .notify(noteId.toInt(), notification)
            }
        }
        return Result.success()
    }
}
