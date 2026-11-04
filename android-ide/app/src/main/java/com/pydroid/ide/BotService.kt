package com.pydroid.ide

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground-сервис, который держит процесс приложения живым 24/7:
 * Android не убивает foreground-процессы при нехватке памяти, а
 * START_STICKY перезапускает сервис после принудительной остановки.
 * Именно в этом процессе крутится event loop aiogram внутри CPython.
 */
class BotService : Service() {

    companion object {
        const val CHANNEL_ID = "pydroid_bot"
        const val NOTIF_ID = 101
        const val ACTION_START = "com.pydroid.ide.START_BOT"
        const val EXTRA_CODE = "bot_code"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getStringExtra(EXTRA_CODE) ?: ""
        startForeground(NOTIF_ID, buildNotification("Бот запущен"))

        if (code.isNotBlank() && !BotRuntime.isRunning) {
            // Сохраняем код, чтобы перезапустить бота при реконструкции
            // сервиса (START_STICKY присылает onStartCommand с intent=null).
            getSharedPreferences("bot", MODE_PRIVATE).edit()
                .putString("last_code", code).apply()
            BotRuntime.start(code)
        } else if (intent != null && code.isNotBlank()) {
            // Новый код поверх работающего бота: стоп → старт.
            BotRuntime.stop()
            getSharedPreferences("bot", MODE_PRIVATE).edit()
                .putString("last_code", code).apply()
            while (BotRuntime.isRunning) Thread.sleep(50)
            BotRuntime.start(code)
        } else if (!BotRuntime.isRunning) {
            val saved = getSharedPreferences("bot", MODE_PRIVATE)
                .getString("last_code", "") ?: ""
            if (saved.isNotBlank()) {
                BotRuntime.start(saved)
            }
        }
        // 24/7: система пересоздаст сервис, если его всё же убьют.
        return START_STICKY
    }

    override fun onDestroy() {
        BotRuntime.stop()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Python Bot",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PyDroid IDE")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
