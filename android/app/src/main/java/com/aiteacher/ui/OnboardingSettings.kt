package com.aiteacher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiteacher.security.SecureStorage
import java.time.ZoneId

@Composable
fun OnboardingSettings(onBack: () -> Unit = {}) {
    val ctx = LocalContext.current
    val savedModel = SecureStorage.getApiModel(ctx)
    var preferredHour by remember { mutableStateOf(9) }
    var preferredMinute by remember { mutableStateOf(0) }
    var tz by remember { mutableStateOf(ZoneId.systemDefault().id) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Session preferences", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = preferredHour.toString(), onValueChange = { v -> preferredHour = v.toIntOrNull() ?: preferredHour }, label = { Text("Hour (0-23)") })
            OutlinedTextField(value = preferredMinute.toString(), onValueChange = { v -> preferredMinute = v.toIntOrNull() ?: preferredMinute }, label = { Text("Minute") })
        }
        Text("Timezone")
        // Simple timezone selection: show a few common zones and allow manual edit
        var tzInput by remember { mutableStateOf(tz) }
        OutlinedTextField(value = tzInput, onValueChange = { tzInput = it }, label = { Text("Timezone (IANA) e.g. Europe/London") })

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                tz = tzInput
                // persist to SecureStorage as a sentinel in model for now
                SecureStorage.saveApiModel(ctx, savedModel ?: "")
            }) { Text("Save") }
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
    }
}
