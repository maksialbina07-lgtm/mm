// pyembed.cpp — JNI-мост между Kotlin-интерфейсом и встроенным CPython.
//
// CPython поставляется как нативные бинарники:
//   libpython3.11.so и зависимости (libssl, libcrypto, libffi, sqlite…)
//   лежат в app/src/main/jniLibs/<abi>/ и загружаются системой при
//   System.loadLibrary("pyembed").
//
// Python-пакеты (aiogram, aiohttp, pydantic, …) распространяются в
// assets/python и перед запуском копируются во внутреннее хранилище
// приложения (filesDir/python), где к ним есть read/write доступ.

#include <jni.h>
#include <python3.11/Python.h>
#include <android/log.h>
#include <string>

#define LOG_TAG "PyDroid"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static PyThreadState *g_main_tstate = nullptr;
static bool g_initialized = false;

extern "C" {

/**
 * Инициализация встроенного интерпретатора.
 * home — путь к скопированной python-среде (filesDir/python):
 *   home/lib/python3.11    — stdlib
 *   home/site-packages     — aiogram, aiohttp, pydantic и зависимости
 */
JNIEXPORT void JNICALL
Java_com_pydroid_ide_PythonEngine_nativeInit(JNIEnv *env, jobject /*thiz*/,
                                             jstring home_) {
    if (g_initialized) return;
    const char *home = env->GetStringUTFChars(home_, nullptr);

    // Корень среды задаётся ДО Py_Initialize: по нему CPython находит stdlib.
    static wchar_t *home_buf = nullptr;
    home_buf = Py_DecodeLocale(home, nullptr);
    Py_SetPythonHome(home_buf);
    Py_NoUserSiteDirectory = 1;   // среда полностью контролируемая
    Py_IgnoreEnvironmentFlag = 1;

    Py_Initialize();

    PyObject *sys_path = PySys_GetObject("path"); // borrowed
    if (sys_path && PyList_Check(sys_path)) {
        std::string site = std::string(home) + "/site-packages";
        PyObject *p1 = PyUnicode_DecodeFSDefault(site.c_str());
        PyList_Append(sys_path, p1);
        Py_XDECREF(p1);
    }

    // Сохраняем главный thread state, затем освобождаем GIL: рабочие потоки
    // будут входить в Python через PyEval_RestoreThread / PyGILState_* API.
    g_main_tstate = PyThreadState_Get();
    PyEval_SaveThread();

    g_initialized = true;
    env->ReleaseStringUTFChars(home_, home);
    LOGI("Embedded CPython initialized, home=%s", home);
}

/**
 * Синхронный запуск пользовательского кода из текущего потока.
 * Возвращает строку — перехваченные stdout/stderr или текст исключения.
 */
JNIEXPORT jstring JNICALL
Java_com_pydroid_ide_PythonEngine_nativeRun(JNIEnv *env, jobject /*thiz*/,
                                            jstring code_) {
    if (!g_initialized) {
        return env->NewStringUTF("FATAL: interpreter not initialized");
    }

    jsize len = env->GetStringUTFLength(code_);
    const char *code = env->GetStringUTFChars(code_, nullptr);
    std::string src(code, (size_t) len);
    env->ReleaseStringUTFChars(code_, code);

    PyThreadState *ts = PyEval_RestoreThread(g_main_tstate);

    PyObject *main_mod = PyImport_AddModule("__main__");
    PyObject *globals = PyModule_GetDict(main_mod);

    PyObject *user = PyUnicode_DecodeUTF8(src.c_str(),
                                          (Py_ssize_t) src.size(), "replace");
    PyDict_SetItemString(globals, "_USER_CODE", user);
    Py_DECREF(user);

    // Перехват stdout/stderr в io.StringIO + exec кода пользователя.
    static const char *BOOTSTRAP =
        "import sys, io\n"
        "_buf = io.StringIO()\n"
        "sys.stdout = sys.stderr = _buf\n"
        "try:\n"
        "    exec(compile(_USER_CODE, '<user>', 'exec'), globals())\n"
        "except BaseException:\n"
        "    import traceback\n"
        "    _buf.write(traceback.format_exc())\n"
        "finally:\n"
        "    sys.stdout = sys.__stdout__\n"
        "    sys.stderr = sys.__stderr__\n"
        "result = _buf.getvalue()\n";

    std::string output;
    PyObject *run = PyRun_String(BOOTSTRAP, Py_file_input, globals, globals);
    if (run == nullptr) {
        PyErr_Print();
        output = "Syntax error in bootstrap";
    } else {
        Py_DECREF(run);
        PyObject *res = PyDict_GetItemString(globals, "result"); // borrowed
        if (res && PyUnicode_Check(res)) {
            const char *c = PyUnicode_AsUTF8(res);
            if (c) output = c;
        }
    }

    PyEval_ReleaseThread(ts);
    LOGI("User code finished, %zu bytes of output", output.size());
    return env->NewStringUTF(output.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_pydroid_ide_PythonEngine_nativeIsReady(JNIEnv *, jobject) {
    return g_initialized ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
