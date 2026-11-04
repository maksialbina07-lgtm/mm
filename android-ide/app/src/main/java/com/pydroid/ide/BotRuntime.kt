package com.pydroid.ide

import android.content.Context

/**
 * Глобальный рантайм Python-бота. Живёт в процессе приложения, который
 * удерживается foreground-сервисом BotService — поэтому event loop aiogram
 * крутится 24/7, даже когда UI закрыт.
 */
object BotRuntime {

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var lastOutput: String = ""
        private set

    private var runningThread: Thread? = null

    fun start(code: String) {
        if (isRunning) return
        isRunning = true

        val ctx = PyDroidApp.instance
        // Инициализация CPython тяжёлая — делаем на фоновом потоке.
        val t = Thread({
            try {
                PythonEngine.initialize(ctx)
                // Блокирующий вызов: exec() внутри запустит
                // asyncio.run(dp.start_polling(...)) и не вернётся,
                // пока бот работает. GIL освобождается только при
                // завершении — это нормально для одного процесса.
                lastOutput = PythonEngine.runCode(code)
            } catch (e: Throwable) {
                lastOutput = "FATAL: ${e.message}"
            } finally {
                isRunning = false
            }
        }, "python-bot-loop")
        runningThread = t
        t.start()
    }

    fun stop() {
        // Python-поток завершится, когда event loop остановится
        // (пользовательский код может слушать сигнал или просто
        // перезапуск интерпретатора убивает процесс вместе с сервисом).
        isRunning = false
    }
}
