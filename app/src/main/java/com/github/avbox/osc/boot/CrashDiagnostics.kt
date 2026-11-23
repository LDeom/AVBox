package com.github.avbox.osc.boot

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import com.github.avbox.osc.util.AppLog
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures crash evidence without logging exception messages, which may contain
 * request URLs, credentials, or user-provided data.
 */
object CrashDiagnostics {
    private const val CRASH_FILE = "avbox_crash.log"
    private const val MAX_CRASH_FILE_BYTES = 512L * 1024L
    private const val MAX_CAUSE_DEPTH = 24
    private const val MAX_FRAMES_PER_CAUSE = 80

    @Volatile
    private var installed = false

    @JvmStatic
    fun install(application: android.app.Application) {
        if (installed) return
        installed = true
        reportPreviousProcessExits(application)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrash(application, thread, throwable)
            try {
                previous?.uncaughtException(thread, throwable)
            } catch (ignored: Throwable) {
                // Never replace the original process failure with diagnostics failure.
            }
        }
        AppLog.i("echo-crash: handler-installed pid=" + Process.myPid())
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable?) {
        if (!AppLog.isFileLoggingEnabled()) return
        try {
            val file = File(context.filesDir, CRASH_FILE)
            if (file.length() >= MAX_CRASH_FILE_BYTES) {
                val backup = File(context.filesDir, CRASH_FILE + ".1")
                if (backup.exists()) backup.delete()
                file.renameTo(backup)
            }
            FileOutputStream(file, true).use { output ->
                fun line(value: String) {
                    output.write((value + "\n").toByteArray(Charsets.UTF_8))
                }
                line("=== AVBox uncaught exception ===")
                line("time=" + SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date()))
                line("pid=" + Process.myPid() + " thread=" + thread.name + " threadId=" + thread.id)
                var current = throwable
                val seen = HashSet<Throwable>()
                var depth = 0
                while (current != null && depth < MAX_CAUSE_DEPTH && seen.add(current)) {
                    line("cause[$depth]=" + current.javaClass.name)
                    current.stackTrace.take(MAX_FRAMES_PER_CAUSE).forEach { frame ->
                        line("  at " + frame.className + "." + frame.methodName +
                            "(" + (frame.fileName ?: "Unknown Source") +
                            if (frame.lineNumber >= 0) ":" + frame.lineNumber else "" + ")")
                    }
                    current = current.cause
                    depth++
                }
                line("=== end crash ===")
                output.fd.sync()
            }
        } catch (ignored: Throwable) {
            // Crash capture is best-effort and must not interfere with process termination.
        }
    }

    private fun reportPreviousProcessExits(context: Context) {
        if (!AppLog.isFileLoggingEnabled() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
            val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 8) ?: return
            exits.forEach { exit ->
                val reason = when (exit.reason) {
                    ApplicationExitInfo.REASON_CRASH -> "crash"
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> "native_crash"
                    ApplicationExitInfo.REASON_ANR -> "anr"
                    ApplicationExitInfo.REASON_LOW_MEMORY -> "low_memory"
                    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "resource_usage"
                    ApplicationExitInfo.REASON_USER_REQUESTED -> "user_requested"
                    ApplicationExitInfo.REASON_USER_STOPPED -> "user_stopped"
                    ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency_died"
                    ApplicationExitInfo.REASON_OTHER -> "other"
                    else -> "reason_" + exit.reason
                }
                AppLog.i(
                    "echo-crash-exit: process=" + exit.processName +
                        " time=" + exit.timestamp +
                        " reason=" + reason +
                        " status=" + exit.status +
                        " importance=" + exit.importance
                )
            }
        } catch (ignored: Throwable) {
            AppLog.i("echo-crash-exit: history-unavailable")
        }
    }
}
