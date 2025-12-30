package com.xtrinityviewer.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.xtrinityviewer.ui.CrashActivity
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class GlobalExceptionHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // 1. Extraer el texto del error
            val stringWriter = StringWriter()
            throwable.printStackTrace(PrintWriter(stringWriter))
            val stackTrace = stringWriter.toString()

            Log.e("GlobalCrash", "Error detectado: $stackTrace")

            // 2. Iniciar la CrashActivity
            val intent = Intent(context, CrashActivity::class.java).apply {
                putExtra("error_trace", stackTrace)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)

            // 3. Matar el proceso actual completamente
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)

        } catch (e: Exception) {
            // Si falla nuestro crash handler, dejamos que Android lo maneje normal
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}