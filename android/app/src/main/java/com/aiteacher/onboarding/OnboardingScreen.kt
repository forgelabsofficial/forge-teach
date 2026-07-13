package com.aiteacher.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun OnboardingScreen(
    vm: OnboardingViewModel = viewModel(),
    onContinue: (Assessment) -> Unit = {}
) {
    val name by vm.name.collectAsState()
    val topics by vm.topics.collectAsState()
    val provider by vm.provider.collectAsState()
    val apiKey by vm.apiKey.collectAsState()
    val models by vm.models.collectAsState()
    val selectedModel by vm.selectedModel.collectAsState()

    Surface(modifier = Modifier.fillMaxWidth().padding(12.dp), shape = androidx.compose.material3.MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = "AI Provider Setup (adult help required)", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            Text(text = "Select provider and paste your API key. Models will be auto-loaded.")

            OutlinedTextField(value = provider, onValueChange = { vm.setProvider(it) }, label = { Text("Provider (e.g. openai, anthropic, google, mistral)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = apiKey, onValueChange = { vm.setApiKey(it) }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.loadModels(androidx.compose.ui.platform.LocalContext.current) }) { Text("Load models") }
                if (models.isNotEmpty()) {
                    Text(text = "${models.size} models")
                }
            }

            if (models.isNotEmpty()) {
                Text(text = "Choose model", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    items(models.size) { idx ->
                        val m = models[idx]
                        val selected = m.id == selectedModel
                        androidx.compose.material3.Card(
                            modifier = Modifier
                                .padding(4.dp)
                                .clickable { vm.selectModel(m.id) },
                            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = if (selected) androidx.compose.ui.graphics.Color(0xFF2563EB) else androidx.compose.ui.graphics.Color(0xFFF8FAFC))
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(text = m.id, color = if (selected) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF111827))
                            }
                        }
                    }
                }
            }

            Text(text = "Create Student Profile", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            OutlinedTextField(value = name, onValueChange = { vm.setName(it) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            Text(text = "Choose topics", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                items(topics.size) { idx ->
                    val t = topics[idx]
                    val selected = t.selected
                    androidx.compose.material3.Card(
                        modifier = Modifier
                            .padding(4.dp)
                            .clickable { vm.toggleTopic(t.id) },
                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = if (selected) androidx.compose.ui.graphics.Color(0xFF2563EB) else androidx.compose.ui.graphics.Color(0xFFF8FAFC))
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(text = t.name, color = if (selected) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF111827))
                        }
                    }
                }
            }

            Text(text = "Availability (sample)", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.AssistChip(onClick = {}) { Text("Mon 18:00-19:00") }
                androidx.compose.material3.AssistChip(onClick = {}) { Text("Wed 18:00-19:00") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.material3.Button(onClick = { onContinue(vm.buildAssessment()) }) { Text("Continue") }
                androidx.compose.material3.OutlinedButton(onClick = { /* save draft - implement later */ }) { Text("Save draft") }
            }
        }
    }
}
