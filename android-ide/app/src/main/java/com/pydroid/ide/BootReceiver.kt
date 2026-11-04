package com.pydroid.ide

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * После reboot процесс убит, но сохранённый код бота есть в SharedPreferences —
 * поднимаем foreground-сервис снова, чтобы бот работал 24/7 без пользователя.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val code = context.getSharedPreferences("bot", Context.MODE_PRIVATE)
            .getString("last_code", "") ?: ""
        if (code.isBlank()) return

        val svc = Intent(context, BotService::class.java).apply {
            action = BotService.ACTION_START
            putExtra(BotService.EXTRA_CODE, code)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }
    }
}
