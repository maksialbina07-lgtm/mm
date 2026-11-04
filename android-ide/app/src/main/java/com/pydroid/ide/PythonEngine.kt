package com.pydroid.ide

import android.content.Context
import java.io.File

/**
 * Обёртка над JNI-мостом к встроенному CPython.
 *
 * Бинарники Python лежат в jniLibs:
 *   libpython3.11.so, libssl.so, libcrypto.so, libffi.so — доставляются
 *   системой вместе с APK и загружаются при loadLibrary("pyembed").
 *
 * Пакеты (aiogram, aiohttp, pydantic…) лежат в assets/python и при первом
 * запуске копируются во filesDir/python — только там разрешено читать
 * .so-расширения пакетов и писать __pycache__.
 */
object PythonEngine {

    @Volatile
    private var loaded = false

    init {
        // Порядок важен: зависимости stdlib-расширений грузим до pyembed.
        listOf("ffi", "crypto", "ssl", "sqlite3", "python3.11", "pyembed").forEach {
            try {
                System.loadLibrary(it)
            } catch (e: UnsatisfiedLinkError) {
                // some libs may be statically linked into libpython — ignore
            }
        }
        loaded = true
    }

    external fun nativeInit(home: String)
    external fun nativeRun(code: String): String
    external fun nativeIsReady(): Boolean

    /** Копирует python-среду из assets во внутреннее хранилище (однократно). */
    fun ensureEnvironment(context: Context): File {
        val destRoot = File(context.filesDir, "python")
        val marker = File(destRoot, ".ready")
        if (!marker.exists()) {
            copyAssetTree(context, "python", destRoot)
            marker.createNewFile()
        }
        return destRoot
    }

    private fun copyAssetTree(ctx: Context, assetPath: String, dest: File) {
        dest.mkdirs()
        val entries = ctx.assets.list(assetPath) ?: return
        if (entries.isEmpty()) return // это файл, а не папка
        for (name in entries) {
            val childAsset = "$assetPath/$name"
            val childDest = File(dest, name)
            val kids = ctx.assets.list(childAsset)
            if (kids != null && kids.isNotEmpty()) {
                copyAssetTree(ctx, childAsset, childDest)
            } else {
                ctx.assets.open(childAsset).use { input ->
                    childDest.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    fun initialize(context: Context) {
        val home = ensureEnvironment(context)
        nativeInit(home.absolutePath)
    }

    /** Синхронный запуск кода; вызывать только из фонового потока! */
    fun runCode(code: String): String = nativeRun(code)
}
