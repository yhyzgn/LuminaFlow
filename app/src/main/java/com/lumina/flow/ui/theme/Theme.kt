package com.lumina.flow.ui.theme

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun LuminaFlowTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = dynamicDarkColorScheme(context)
    MaterialTheme(colorScheme = colorScheme, content = content)
}