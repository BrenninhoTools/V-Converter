package com.brenninhotools.vconverter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.brenninhotools.vconverter.ui.ConverterScreen
import com.brenninhotools.vconverter.ui.ConverterViewModel
import com.brenninhotools.vconverter.ui.theme.VConverterTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ConverterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            VConverterTheme {
                ConverterScreen(viewModel)
            }
        }
    }
}
