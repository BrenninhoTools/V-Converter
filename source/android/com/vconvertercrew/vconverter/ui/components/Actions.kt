package com.vconvertercrew.vconverter.ui.components

import com.vconvertercrew.vconverter.overlay.soundClickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vconvertercrew.vconverter.R
import com.vconvertercrew.vconverter.io.Downloads
import com.vconvertercrew.vconverter.launcher.FunkinApp
import com.vconvertercrew.vconverter.launcher.FunkinOpen
import com.vconvertercrew.vconverter.ui.BaseConverterViewModel
import com.vconvertercrew.vconverter.ui.theme.Funkin

@Composable
fun rememberDownloader(viewModel: BaseConverterViewModel): () -> Unit {
    val current by rememberUpdatedState(viewModel)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Downloads.MIME)) { uri ->
        if (uri != null) current.download(uri)
    }
    return {
        if (Downloads.needsPicker) picker.launch(current.downloadName()) else current.download(null)
    }
}

@Composable
fun OutlineButton(
    text: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(16.dp)
    val tint = if (enabled) accent else Funkin.Muted
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(tint.copy(alpha = 0.08f))
            .border(2.dp, tint.copy(alpha = if (enabled) 0.8f else 0.3f), shape)
            .soundClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SaveActions(
    viewModel: BaseConverterViewModel,
    accent: Color,
    modFolderName: String?,
    onSave: () -> Unit,
    saveLabel: String = if (modFolderName != null) "Save into $modFolderName" else "Save into a mod folder"
) {
    val download = rememberDownloader(viewModel)
    val busy = viewModel.busy
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChunkyButton(
            text = "Download .zip",
            accent = accent,
            icon = R.drawable.ic_download,
            enabled = viewModel.canDownload && !busy,
            loading = busy,
            onClick = download
        )
        OutlineButton(
            text = saveLabel,
            accent = Funkin.Up,
            icon = R.drawable.ic_save,
            enabled = viewModel.canDownload && !busy,
            onClick = onSave
        )
        DownloadCard(viewModel, accent)
    }
}

@Composable
fun DownloadCard(viewModel: BaseConverterViewModel, accent: Color) {
    val file = viewModel.download
    AnimatedVisibility(visible = file != null, enter = fadeIn() + expandVertically()) {
        if (file == null) return@AnimatedVisibility
        val shape = RoundedCornerShape(18.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Brush.horizontalGradient(listOf(Funkin.Up.copy(alpha = 0.16f), accent.copy(alpha = 0.10f))))
                .border(2.dp, Funkin.Up.copy(alpha = 0.6f), shape)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Funkin.Up)
                        .border(2.dp, Funkin.Ink, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(R.drawable.ic_zip), contentDescription = null, tint = Funkin.Ink, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("DOWNLOADED", style = MaterialTheme.typography.labelSmall, color = Funkin.Up)
                    Text(file.name, style = MaterialTheme.typography.titleSmall, color = Funkin.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${file.entries} files · ${file.location}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Funkin.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton("Share", accent, viewModel::shareDownload, Modifier.weight(1f), R.drawable.ic_share)
                if (!Downloads.needsPicker) {
                    OutlineButton("Downloads", Funkin.Up, viewModel::openDownloads, Modifier.weight(1f), R.drawable.ic_folder)
                }
            }
            if (viewModel.vsliceOutput) FunkinTestPanel()
        }
    }
}

@Composable
fun rememberFunkinTester(onProblem: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val report by rememberUpdatedState(onProblem)
    return {
        report(
            when (FunkinApp.open(context)) {
                FunkinOpen.OPENED -> null
                FunkinOpen.MISSING -> if (FunkinApp.openStore(context)) "Friday Night Funkin' isn't installed, so its store page was opened." else "Friday Night Funkin' isn't installed."
                FunkinOpen.FAILED -> "Could not open Friday Night Funkin'."
            }
        )
    }
}

@Composable
fun FunkinTestPanel() {
    val context = LocalContext.current
    var problem by remember { mutableStateOf<FunkinOpen?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Put the mod in the game's mods folder, then switch to Friday Night Funkin' to try it.",
            style = MaterialTheme.typography.bodySmall,
            color = Funkin.Muted
        )
        OutlineButton(
            "Test In Friday Night Funkin'",
            Funkin.Down,
            { problem = FunkinApp.open(context).takeIf { it != FunkinOpen.OPENED } },
            icon = R.drawable.ic_arrow_forward
        )
        when (problem) {
            FunkinOpen.MISSING -> {
                Text("Friday Night Funkin' isn't installed on this device.", style = MaterialTheme.typography.bodySmall, color = Funkin.Right)
                OutlineButton("Get Friday Night Funkin'", Funkin.Up, { if (!FunkinApp.openStore(context)) problem = FunkinOpen.FAILED }, icon = R.drawable.ic_download)
            }
            FunkinOpen.FAILED -> Text("Could not open Friday Night Funkin'.", style = MaterialTheme.typography.bodySmall, color = Funkin.Right)
            else -> Unit
        }
    }
}
