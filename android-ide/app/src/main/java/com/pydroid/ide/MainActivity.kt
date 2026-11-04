package com.pydroid.ide

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pydroid.ide.databinding.ActivityMainBinding

/**
 * Минимальный интерфейс по ТЗ: серый фон, текст (поле с Python-кодом)
 * и кнопка «Запустить код». Всё тяжёлое — в foreground-сервисе.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val REQ_NOTIF = 42
        private const val PREFS = "editor"
        private const val KEY_CODE = "code"

        private val DEFAULT_CODE = """
            from aiogram import Bot, Dispatcher, Router
            from aiogram.types import Message

            API_TOKEN = "PASTE_YOUR_TOKEN"

            router = Router()

            @router.message()
            async def echo(m: Message):
                await m.answer(m.text or "")

            dp = Dispatcher()
            dp.include_router(router)
            dp.run(Bot(API_TOKEN))
        """.trimIndent()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root) // корневой layout уже серого цвета (#9E9E9E)

        // Восстановление последнего кода между запусками приложения.
        val saved = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(KEY_CODE, DEFAULT_CODE)!!
        binding.codeText.setText(saved)

        binding.runButton.setOnClickListener { runCode() }

        requestNotificationPermissionIfNeeded()
    }

    private fun runCode() {
        val code = binding.codeText.text.toString()
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_CODE, code).apply()

        val intent = Intent(this, BotService::class.java).apply {
            action = BotService.ACTION_START
            putExtra(BotService.EXTRA_CODE, code)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Код запущен в фоне (24/7)", Toast.LENGTH_SHORT).show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF
            )
        }
    }
}
