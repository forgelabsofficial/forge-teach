package com.aiteacher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AppContent() {
    AiTeacherTheme {
        Surface(
            modifier = Modifier.fillMaxSize().background(forgeColors.bgBase),
            color = forgeColors.bgBase
        ) {
            MainScreen()
        }
    }
}
