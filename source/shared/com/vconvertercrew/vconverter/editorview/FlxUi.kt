package com.vconvertercrew.vconverter.editorview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vconvertercrew.vconverter.overlay.UiSound
import kotlin.math.roundToInt

internal object Flx {
    val gridA = Color(0xFFE7E6E6)
    val gridB = Color(0xFFD9D5D5)
    val chrome = Color(0xFFC8C8C8)
    val chromeDark = Color(0xFF7F7F7F)
    val tabOn = Color(0xFFE6E6E6)
    val tabOff = Color(0xFFA9A9A9)
    val button = Color(0xFFE2E2E2)
    val ink = Color.Black
}

internal fun roundDecimal(value: Double): String {
    val scaled = (value * 100.0).roundToInt()
    val whole = scaled / 100
    val part = kotlin.math.abs(scaled % 100)
    return if (part == 0) "$whole" else if (part % 10 == 0) "$whole.${part / 10}" else "$whole.${if (part < 10) "0$part" else "$part"}"
}

@Composable
internal fun FlxLabel(text: String) {
    Text(text, color = Flx.ink, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
}

@Composable
internal fun FlxButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .width(80.dp)
            .heightIn(min = 20.dp)
            .background(Flx.button)
            .border(1.dp, Flx.chromeDark)
            .pointerInput(text) {
                detectTapGestures {
                    UiSound.click()
                    onClick()
                }
            }
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Flx.ink, fontSize = 8.sp, fontFamily = FontFamily.Monospace) }
}

@Composable
internal fun FlxInput(value: String, width: Int, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    BasicTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        singleLine = true,
        textStyle = TextStyle(color = Flx.ink, fontSize = 9.sp, fontFamily = FontFamily.Monospace),
        cursorBrush = SolidColor(Flx.ink),
        modifier = Modifier.width(width.dp).background(Color.White).border(1.dp, Flx.ink).padding(2.dp).typingField()
    )
}

@Composable
internal fun FlxCheck(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.pointerInput(checked, enabled) {
            detectTapGestures {
                if (enabled) {
                    UiSound.click()
                    onChange(!checked)
                }
            }
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(11.dp).background(Color.White).border(1.dp, Flx.ink), contentAlignment = Alignment.Center) {
            if (checked) Box(Modifier.size(7.dp).background(if (enabled) Flx.ink else Flx.chromeDark))
        }
        Text(label, color = if (enabled) Flx.ink else Flx.chromeDark, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
internal fun FlxStepper(value: Double, step: Double, min: Double, max: Double, decimals: Int, label: String, onValue: (Double) -> Unit) {
    fun format(v: Double) = if (decimals == 0) v.roundToInt().toString() else roundDecimal(v)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.width(40.dp).background(Color.White).border(1.dp, Flx.ink).padding(2.dp)) {
            Text(format(value), color = Flx.ink, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
        FlxButton("+", Modifier.width(14.dp)) { onValue((value + step).coerceIn(min, max)) }
        FlxButton("-", Modifier.width(14.dp)) { onValue((value - step).coerceIn(min, max)) }
        if (label.isNotEmpty()) FlxLabel(label)
    }
}

@Composable
internal fun FlxDropDown(selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.width(120.dp).background(Color.White).border(1.dp, Flx.ink).pointerInput(selected) {
                detectTapGestures {
                    UiSound.click()
                    open = true
                }
            }.padding(2.dp)
        ) { Text("$selected ▾", color = Flx.ink, fontSize = 9.sp, fontFamily = FontFamily.Monospace) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 9.sp, fontFamily = FontFamily.Monospace) },
                    onClick = {
                        UiSound.click()
                        open = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

@Composable
internal fun FlxTabBox(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, width: Dp, height: Dp, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.size(width, height)) {
        Row(Modifier.fillMaxWidth().height(20.dp)) {
            tabs.forEachIndexed { index, title ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(20.dp)
                        .background(if (index == selected) Flx.tabOn else Flx.tabOff)
                        .border(1.dp, Flx.chromeDark)
                        .pointerInput(index) {
                            detectTapGestures {
                                UiSound.click()
                                onSelect(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) { Text(title, color = Flx.ink, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Flx.chrome)
                .border(1.dp, Flx.chromeDark)
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}
