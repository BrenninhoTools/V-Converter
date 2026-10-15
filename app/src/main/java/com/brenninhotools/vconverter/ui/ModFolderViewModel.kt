package com.brenninhotools.vconverter.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import com.brenninhotools.vconverter.io.DocumentIO

class ModFolderViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("v_converter", Context.MODE_PRIVATE)

    var folder by mutableStateOf<Uri?>(null)
        private set
    var folderName by mutableStateOf("")
        private set

    init {
        prefs.getString(KEY_FOLDER, null)?.let(Uri::parse)?.let { saved ->
            if (DocumentIO.hasAccess(application, saved)) {
                folder = saved
                folderName = DocumentIO.folderName(application, saved)
            } else {
                prefs.edit { remove(KEY_FOLDER) }
            }
        }
    }

    fun select(uri: Uri) {
        val app = getApplication<Application>()
        DocumentIO.keepAccess(app, uri)
        folder = uri
        folderName = DocumentIO.folderName(app, uri)
        prefs.edit { putString(KEY_FOLDER, uri.toString()) }
    }

    private companion object {
        const val KEY_FOLDER = "mod_folder"
    }
}
