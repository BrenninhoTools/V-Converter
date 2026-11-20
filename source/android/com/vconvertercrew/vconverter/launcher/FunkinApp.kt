package com.vconvertercrew.vconverter.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

enum class FunkinOpen { OPENED, MISSING, FAILED }

object FunkinApp {
    fun installed(context: Context): Boolean =
        if (NativeLauncher.loaded) NativeLauncher.installed(context, FunkinGame.PACKAGE)
        else context.packageManager.getLaunchIntentForPackage(FunkinGame.PACKAGE) != null

    fun open(context: Context): FunkinOpen {
        val code = if (NativeLauncher.loaded) NativeLauncher.launchPackage(context, FunkinGame.PACKAGE) else fallbackOpen(context)
        return when (code) {
            NativeLauncher.OK -> FunkinOpen.OPENED
            NativeLauncher.NOT_FOUND -> FunkinOpen.MISSING
            else -> FunkinOpen.FAILED
        }
    }

    fun openStore(context: Context): Boolean =
        if (NativeLauncher.loaded) NativeLauncher.openStore(context, FunkinGame.PACKAGE) == NativeLauncher.OK
        else listOf("market://details?id=${FunkinGame.PACKAGE}", "https://play.google.com/store/apps/details?id=${FunkinGame.PACKAGE}").any { link ->
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }

    private fun fallbackOpen(context: Context): Int {
        val intent = context.packageManager.getLaunchIntentForPackage(FunkinGame.PACKAGE) ?: return NativeLauncher.NOT_FOUND
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            NativeLauncher.OK
        } catch (e: ActivityNotFoundException) {
            NativeLauncher.FAILED
        } catch (e: SecurityException) {
            NativeLauncher.FAILED
        }
    }
}
