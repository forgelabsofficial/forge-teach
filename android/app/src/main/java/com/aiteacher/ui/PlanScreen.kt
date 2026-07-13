package com.aiteacher.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiteacher.ai.AiClient
import com.aiteacher.onboarding.Assessment
import com.aiteacher.onboarding.Plan
import com.aiteacher.data.PlanRepository
import com.aiteacher.work.ScheduleManager
import kotlinx.coroutines.launch

@Composable
fun PlanScreen(onAccepted: () -> Unit = {}) {
    val ctx = LocalContext.current
    val context = ctx.applicationContext
    var loading by remember { mutableStateOf(true) }
    var plan by remember { mutableStateOf<Plan?>(null) }
    var assessment by remember { mutableStateOf<Assessment?>(null) }
    val scope = rememberCoroutineScope()
    val repo = remember { PlanRepository(context) }

    // local editable copy of sessions to allow user to tweak isoDateTime before accepting
    var editableSessions by remember { mutableStateOf<List<com.aiteacher.onboarding.SessionItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        loading = true
        val a = DataStoreUtils.loadAssessment(ctx)
        assessment = a
        if (a != null) {
            plan = AiClient.generatePlan(ctx, a)
            editableSessions = plan?.sessions ?: emptyList()
        }
        loading = false
    }

    if (loading) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text("Generating plan…")
        }
        return
    }

    if (plan == null) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text("No assessment found. Please complete onboarding first.")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { /* navigate to onboarding */ }) { Text("Start assessment") }
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Text("Your Learning Plan", modifier = Modifier.padding(bottom = 8.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(editableSessions) { idx, s ->
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Text(text = "${s.date} — ${s.topic} (${s.duration}m)")
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = s.isoDateTime ?: "",
                        onValueChange = { newVal ->
                            val mutable = editableSessions.toMutableList()
                            mutable[idx] = s.copy(isoDateTime = newVal)
                            editableSessions = mutable.toList()
                        },
                        label = { Text("Optional ISO date/time (e.g. 2026-07-20T09:00:00+01:00)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { /* regenerate */ }) { Text("Regenerate") }
            Button(onClick = {
                // persist plan (using editableSessions) and schedule notifications
                scope.launch {
                    val newPlan = Plan(weeks = plan!!.weeks, sessions = editableSessions)
                    repo.savePlan(newPlan)
                    ScheduleManager.schedulePlanNotifications(context, newPlan)
                }
                onAccepted()
            }) { Text("Accept plan") }
        }
    }
}
