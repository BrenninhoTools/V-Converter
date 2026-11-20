package com.vconvertercrew.vconverter.launcher

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.vconvertercrew.vconverter.io.DocumentIO

object FunkinFolder {
    const val AUTHORITY = "${FunkinGame.PACKAGE}.docprovider"
    private const val PREFS = "funkin"
    private const val KEY = "modsTree"

    fun initialUri(): Uri = DocumentsContract.buildDocumentUri(AUTHORITY, "/storage/emulated/0/Android/data/${FunkinGame.PACKAGE}/files")

    fun saved(context: Context): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?.takeIf { DocumentIO.hasAccess(context, it) }

    fun keep(context: Context, tree: Uri) {
        DocumentIO.keepAccess(context, tree)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tree.toString()).apply()
    }

    fun forget(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    fun insideMods(context: Context, tree: Uri): Boolean =
        DocumentIO.folderName(context, tree).equals("mods", true)
}
