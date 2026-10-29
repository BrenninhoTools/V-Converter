package com.brenninhotools.vconverter.ui.components

import com.brenninhotools.vconverter.overlay.UiSound
import com.brenninhotools.vconverter.overlay.soundClickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import androidx.annotation.DrawableRes
import com.brenninhotools.vconverter.R
import com.brenninhotools.vconverter.ui.theme.Funkin

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    decimal: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() || it == '.' || it == '-' }) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            focusedLabelColor = accent,
            cursorColor = accent,
            unfocusedBorderColor = Funkin.Outline
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun FieldRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, accent: Color, hint: String? = null, enabled: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .soundClickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Funkin.Text)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = Funkin.Muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = { UiSound.click(); onChange(it) },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Funkin.Ink,
                checkedTrackColor = accent,
                uncheckedThumbColor = Funkin.Muted,
                uncheckedTrackColor = Funkin.SurfaceHigh,
                uncheckedBorderColor = Funkin.Outline
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    label: (T) -> String,
    isSelected: (T) -> Boolean,
    onClick: (T) -> Unit,
    accent: Color,
    enabled: Boolean = true
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option ->
            Chip(label(option), isSelected(option), accent, enabled) { onClick(option) }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, accent: Color, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) Funkin.Ink else Funkin.Text,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) accent else Funkin.Surface)
            .border(1.5.dp, if (selected) Funkin.Ink else Funkin.Outline, shape)
            .soundClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
fun FieldLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Funkin.Muted)
}

@Composable
fun AssetPicker(
    label: String,
    fileName: String?,
    accent: Color,
    mimeTypes: Array<String>,
    onPick: (Uri) -> Unit,
    onClear: () -> Unit,
    @DrawableRes icon: Int = R.drawable.ic_add,
    enabled: Boolean = true
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onPick(uri) }
    val shape = RoundedCornerShape(14.dp)
    val picked = fileName != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (picked) accent.copy(alpha = 0.10f) else Funkin.SurfaceHigh)
            .border(1.5.dp, if (picked) accent.copy(alpha = 0.6f) else Funkin.Outline, shape)
            .soundClickable(enabled = enabled) { launcher.launch(mimeTypes) }
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (picked) accent else Funkin.Surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(if (picked) R.drawable.ic_check else icon),
                contentDescription = null,
                tint = if (picked) Funkin.Ink else Funkin.Muted,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Funkin.Muted)
            Text(
                fileName ?: "Tap to pick (optional)",
                style = MaterialTheme.typography.bodyMedium,
                color = if (picked) Funkin.Text else Funkin.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (picked) {
            IconButton(onClick = UiSound.wrap(onClear), enabled = enabled) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = "Remove $label", tint = Funkin.Muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun CodePreview(code: String, title: String = "Preview", accent: Color = Funkin.Down) = CodeView(code, title, accent)
