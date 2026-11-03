package com.brenninhotools.vconverter

import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
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

    override fun onResume() {
        super.onResume()
        requestLocalNetwork()
    }

    override fun onStop() {
        com.brenninhotools.vconverter.ui.editor.AndroidSongPlayer.pauseAll()
        super.onStop()
    }

    fun requestLocalNetwork() {
        val url = Online.serverUrl
        if (Build.VERSION.SDK_INT < 37 || localNetworkAsked == url) return
        if (!isLocalAddress(url)) return
        if (checkSelfPermission(LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) return
        localNetworkAsked = url
        requestPermissions(arrayOf(LOCAL_NETWORK), 37)
    }

    private fun isLocalAddress(url: String): Boolean {
        val host = runCatching { java.net.URI(url.trim()).host }.getOrNull()?.lowercase() ?: return false
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".lan")) return true
        val parts = host.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        return parts[0] == 10 || parts[0] == 127 || (parts[0] == 192 && parts[1] == 168) || (parts[0] == 172 && parts[1] in 16..31) || (parts[0] == 169 && parts[1] == 254)
    }

    private fun setupOnline() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        Online.platform = "android"
        Online.appVersion = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "0.0.2"
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
        var localNetworkAsked: String? = null
        const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
