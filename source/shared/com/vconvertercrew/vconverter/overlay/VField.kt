package com.vconvertercrew.vconverter.overlay

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vconvertercrew.vconverter.editorview.typingField
import com.vconvertercrew.vconverterui.VTextField

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, accent: Color, enabled: Boolean = true, modifier: Modifier = Modifier) {
    VTextField(value, onChange, label, accent, enabled, modifier.typingField())
}
