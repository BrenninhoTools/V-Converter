package com.brenninhotools.vconverter

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.brenninhotools.vconverter.io.Importer
import com.brenninhotools.vconverter.online.ErrorReports
import com.brenninhotools.vconverter.online.Online
import java.io.File
import com.brenninhotools.vconverter.ui.MainScreen
import com.brenninhotools.vconverter.ui.theme.VConverterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) Importer.clearCache(this)
        setupOnline()
        setContent {
            VConverterTheme {
                MainScreen()
            }
        }
    }

    private fun setupOnline() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        Online.platform = "android"
        Online.appVersion = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "0.0.1"
        Online.serverUrl = prefs.getString("server_url", "") ?: ""
        Online.reportsEnabled = prefs.getBoolean("error_reports", false)
        val crashFile = File(filesDir, "last-crash.txt")
        if (crashFile.exists()) {
            val text = runCatching { crashFile.readText() }.getOrDefault("")
            crashFile.delete()
            if (text.isNotBlank()) ErrorReports.send("crash", text.lineSequence().firstOrNull()?.take(300) ?: "Crash", stack = text)
        }
        if (crashHandlerInstalled) return
        crashHandlerInstalled = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val target = File(filesDir, "last-crash.txt")
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { target.writeText(error.stackTraceToString()) }
            previous?.uncaughtException(thread, error)
        }
    }

    private companion object {
        var crashHandlerInstalled = false
    }
}
