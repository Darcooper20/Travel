package com.travelbenefits.app.data.local

import android.content.Context
import android.os.Build
import com.travelbenefits.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records the last uncaught exception to a plain file so a sideloaded build
 * can say why it died. There is no Play Console behind this app and no crash
 * service, so without this a crash is invisible: the user sees "keeps
 * stopping" and nobody can act on it.
 *
 * Deliberately a plain file, not [SecurePrefs]: if the encrypted store or the
 * keystore is what failed, writing the report through it would fail too.
 * Nothing sensitive is written - a stack trace, the device model and the build
 * identifiers. Keys and tokens live in SecurePrefs and never appear in traces.
 */
@Singleton
class CrashLog @Inject constructor(@ApplicationContext private val context: Context) {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    /** The last crash report, or null when the app has not crashed since it was last cleared. */
    fun lastCrash(): String? = runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()?.takeIf { it.isNotBlank() }

    fun clear() {
        runCatching { file.delete() }
    }

    /**
     * Chains a handler in front of whatever is already installed, so the
     * system still gets to show its dialog and kill the process. Writing the
     * report is best-effort: a failure here must not replace the original
     * crash with a less useful one.
     */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(thread: Thread, throwable: Throwable) {
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val report = buildString {
            appendLine("Travel Benefits crash report")
            appendLine("When:    ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), ${BuildConfig.BUILD_TYPE}")
            appendLine("App id:  ${BuildConfig.APPLICATION_ID}")
            appendLine("Device:  ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Thread:  ${thread.name}")
            appendLine()
            append(trace)
        }
        file.writeText(report.take(MAX_CHARS))
    }

    private companion object {
        const val FILE_NAME = "last_crash.txt"
        const val MAX_CHARS = 60_000
    }
}
