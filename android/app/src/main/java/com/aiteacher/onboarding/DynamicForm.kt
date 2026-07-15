package com.aiteacher.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aiteacher.ui.ForgeBrand
import com.aiteacher.ui.GlassCard
import com.aiteacher.ui.forgeColors
import com.aiteacher.ui.forgeFieldColors

// field colors via forgeFieldColors()

@Composable
fun DynamicForm(schema: AssessmentSchema, answers: MutableMap<String, Any?>) {
    val state = remember { mutableStateMapOf<String, Any?>() }
    schema.questions.forEach { q -> if (!state.containsKey(q.id)) state[q.id] = answers[q.id] }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        schema.questions.forEach { q ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(q.prompt, style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium), color = forgeColors.textPrimary)

                    when (q.type) {
                        "text", "timezone" -> {
                            val value = (state[q.id] as? String) ?: ""
                            OutlinedTextField(
                                value = value,
                                onValueChange = { v -> state[q.id] = v; answers[q.id] = v },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                shape = RoundedCornerShape(12.dp), colors = forgeFieldColors(),
                                placeholder = {
                                    if (q.type == "timezone") Text("e.g. Africa/Lagos",
                                        color = forgeColors.textMuted)
                                }
                            )
                        }

                        "single_choice" -> {
                            val sel = state[q.id] as? String
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                q.options?.forEach { opt ->
                                    val selected = sel == opt.id
                                    val interactionSource = remember { MutableInteractionSource() }
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (selected) ForgeBrand.Orange.copy(0.12f)
                                                else Color.Transparent)
                                            .border(1.dp,
                                                if (selected) ForgeBrand.Orange else forgeColors.glassBorder,
                                                RoundedCornerShape(12.dp))
                                            .clickable(interactionSource = interactionSource, indication = null) {
                                                state[q.id] = opt.id; answers[q.id] = opt.id
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(modifier = Modifier.size(20.dp).clip(CircleShape)
                                            .background(if (selected) ForgeBrand.Orange else Color.Transparent)
                                            .border(2.dp,
                                                if (selected) ForgeBrand.Orange else forgeColors.glassBorder,
                                                CircleShape), contentAlignment = Alignment.Center) {
                                            if (selected) Box(modifier = Modifier.size(8.dp).clip(CircleShape)
                                                .background(Color.White))
                                        }
                                        Text(opt.label, style = MaterialTheme.typography.bodyMedium,
                                            color = if (selected) forgeColors.textPrimary else forgeColors.textSecondary,
                                            modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        "multi_choice" -> {
                            @Suppress("UNCHECKED_CAST")
                            val sel = (state[q.id] as? List<String>) ?: emptyList()
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                q.options?.forEach { opt ->
                                    val checked = sel.contains(opt.id)
                                    val interactionSource = remember { MutableInteractionSource() }
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (checked) ForgeBrand.Orange.copy(0.12f)
                                                else Color.Transparent)
                                            .border(1.dp,
                                                if (checked) ForgeBrand.Orange else forgeColors.glassBorder,
                                                RoundedCornerShape(12.dp))
                                            .clickable(interactionSource = interactionSource, indication = null) {
                                                val mut = sel.toMutableList()
                                                if (checked) mut.remove(opt.id) else mut.add(opt.id)
                                                state[q.id] = mut.toList(); answers[q.id] = mut.toList()
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(modifier = Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
                                            .background(if (checked) ForgeBrand.Orange else Color.Transparent)
                                            .border(2.dp,
                                                if (checked) ForgeBrand.Orange else forgeColors.glassBorder,
                                                RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                            if (checked) Icon(Icons.Default.Check, null,
                                                tint = Color.White, modifier = Modifier.size(12.dp))
                                        }
                                        Text(opt.label, style = MaterialTheme.typography.bodyMedium,
                                            color = if (checked) forgeColors.textPrimary else forgeColors.textSecondary,
                                            modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        "day_time_picker" -> {
                            DayTimePicker(
                                value = state[q.id] as? String ?: "",
                                onValueChange = { v -> state[q.id] = v; answers[q.id] = v }
                            )
                        }

                        "availability" -> {
                            val value = (state[q.id] as? String) ?: ""
                            OutlinedTextField(
                                value = value,
                                onValueChange = { v -> state[q.id] = v; answers[q.id] = v },
                                modifier = Modifier.fillMaxWidth(), singleLine = false, minLines = 2,
                                placeholder = { Text("e.g. mon: 16:00-17:00, sat: 10:00-12:00",
                                    color = forgeColors.textMuted) },
                                shape = RoundedCornerShape(12.dp), colors = forgeFieldColors()
                            )
                        }

                        else -> {
                            val value = (state[q.id] as? String) ?: ""
                            OutlinedTextField(
                                value = value,
                                onValueChange = { v -> state[q.id] = v; answers[q.id] = v },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                shape = RoundedCornerShape(12.dp), colors = forgeFieldColors()
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Day/time picker ──────────────────────────────────────────────────────────

private val DAYS     = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val DAY_KEYS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

@Composable
private fun DayTimePicker(value: String, onValueChange: (String) -> Unit) {
    val initial = remember(value) { parseAvail(value) }
    val selectedDays = remember { mutableStateMapOf<String, Boolean>().also { m ->
        initial.keys.forEach { m[it] = true }
    }}
    val timeRanges = remember { mutableStateMapOf<String, String>().also { m ->
        initial.forEach { (k, v) -> m[k] = v }
    }}

    fun emit() {
        val r = DAY_KEYS.filter { selectedDays[it] == true }
            .joinToString(", ") { k -> "$k: ${timeRanges[k] ?: "16:00-17:00"}" }
        onValueChange(r)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Day chips
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DAYS.forEachIndexed { i, label ->
                val key = DAY_KEYS[i]
                val sel = selectedDays[key] == true
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier.weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) ForgeBrand.Orange else forgeColors.glassFill)
                        .border(1.dp,
                            if (sel) ForgeBrand.Orange else forgeColors.glassBorder,
                            RoundedCornerShape(8.dp))
                        .clickable(interactionSource = interactionSource, indication = null) {
                            selectedDays[key] = !sel; emit()
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (sel) Color.White else forgeColors.textMuted)
                }
            }
        }

        // Time fields
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DAY_KEYS.forEachIndexed { i, key ->
                if (selectedDays[key] == true) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(DAYS[i], style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold),
                            color = ForgeBrand.Orange,
                            modifier = Modifier.width(36.dp))
                        OutlinedTextField(
                            value = timeRanges[key] ?: "16:00-17:00",
                            onValueChange = { v -> timeRanges[key] = v; emit() },
                            placeholder = { Text("16:00-17:00", color = forgeColors.textMuted) },
                            modifier = Modifier.weight(1f), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            shape = RoundedCornerShape(10.dp), colors = forgeFieldColors()
                        )
                    }
                }
            }
        }

        if (selectedDays.values.none { it }) {
            Text("Tap days above to add study slots",
                style = MaterialTheme.typography.bodySmall, color = forgeColors.textMuted)
        }
    }
}

private fun parseAvail(s: String): Map<String, String> {
    if (s.isBlank()) return emptyMap()
    return s.split(",").mapNotNull { part ->
        val kv = part.trim().split(":").map { it.trim() }
        if (kv.size >= 2) kv[0] to kv.drop(1).joinToString(":") else null
    }.toMap()
}
