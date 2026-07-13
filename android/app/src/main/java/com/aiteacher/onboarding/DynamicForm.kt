package com.aiteacher.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Minimal dynamic form renderer for a small set of question types.
 * - `text` -> single-line text field
 * - `single_choice` -> radio buttons
 * - `multi_choice` -> checkboxes
 * - `availability` -> free text (simple)
 * - `timezone` -> text field
 */
@Composable
fun DynamicForm(schema: AssessmentSchema, answers: MutableMap<String, Any?>) {
    val state = remember { mutableStateMapOf<String, Any?>() }
    // initialize with provided answers
    schema.questions.forEach { q -> if (!state.containsKey(q.id)) state[q.id] = answers[q.id] }

    Column(modifier = Modifier.padding(8.dp)) {
        schema.questions.forEach { q ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = q.prompt)
            when (q.type) {
                "text", "timezone", "availability" -> {
                    val value = (state[q.id] as? String) ?: ""
                    OutlinedTextField(value = value, onValueChange = { v -> state[q.id] = v; answers[q.id] = v }, modifier = Modifier.padding(top = 6.dp), singleLine = true)
                }
                "single_choice" -> {
                    val sel = (state[q.id] as? String)
                    q.options?.forEach { opt ->
                        Row(modifier = Modifier.padding(top = 6.dp)) {
                            RadioButton(selected = sel == opt.id, onClick = { state[q.id] = opt.id; answers[q.id] = opt.id })
                            Spacer(modifier = Modifier.padding(6.dp))
                            Text(opt.label)
                        }
                    }
                }
                "multi_choice" -> {
                    val sel = (state[q.id] as? List<*>)?.toMutableList() ?: mutableListOf<String>()
                    q.options?.forEach { opt ->
                        val checked = sel.contains(opt.id)
                        Row(modifier = Modifier.padding(top = 6.dp)) {
                            Checkbox(checked = checked, onCheckedChange = { ch ->
                                if (ch) sel.add(opt.id) else sel.remove(opt.id)
                                state[q.id] = sel.toList(); answers[q.id] = sel.toList()
                            })
                            Spacer(modifier = Modifier.padding(6.dp))
                            Text(opt.label)
                        }
                    }
                }
                else -> {
                    // fallback to text
                    val value = (state[q.id] as? String) ?: ""
                    OutlinedTextField(value = value, onValueChange = { v -> state[q.id] = v; answers[q.id] = v }, modifier = Modifier.padding(top = 6.dp), singleLine = true)
                }
            }
        }
    }
}
