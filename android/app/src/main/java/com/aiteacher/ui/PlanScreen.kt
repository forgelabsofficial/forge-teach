package com.aiteacher.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiteacher.onboarding.Plan

@Composable
fun PlanScreen(onAccepted: () -> Unit = {}) {
    val vm: PlanViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val loading by vm.loading.collectAsState()
    val plan by vm.plan.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val completedKeys by vm.completedKeys.collectAsState()
    val savedToast by vm.savedToast.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(savedToast) {
        if (savedToast) snackbar.showSnackbar("Auto-saved ✓")
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = Color.Transparent) { padding ->
        ForgeBackground {
            when {
                loading -> LoadingState()
                plan == null -> EmptyState()
                else -> {
                    val total = sessions.size
                    val doneCount = sessions.count { s -> completedKeys.contains("${s.date}|${s.topic}") }
                    val progress = if (total > 0) doneCount.toFloat() / total else 0f

                    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                        Column(modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Learning Plan",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                                color = forgeColors.textPrimary)
                            Text("${plan!!.weeks} weeks · $total sessions",
                                style = MaterialTheme.typography.bodyMedium,
                                color = forgeColors.textSecondary)
                        }

                        GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            shape = RoundedCornerShape(18.dp)) {
                            Column(modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Progress", style = MaterialTheme.typography.labelLarge,
                                        color = forgeColors.textPrimary)
                                    Text("${(progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                        color = ForgeBrand.Success)
                                }
                                LinearProgressIndicator(progress = progress,
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = ForgeBrand.Success, trackColor = forgeColors.glassBorder)
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    LegendDot(ForgeBrand.Success, "Done ($doneCount)")
                                    LegendDot(ForgeBrand.Orange, "Upcoming (${total - doneCount})")
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            itemsIndexed(sessions) { idx, session ->
                                val key = "${session.date}|${session.topic}"
                                val isDone = completedKeys.contains(key)
                                PlanTimelineItem(
                                    session = session, index = idx,
                                    isDone = isDone,
                                    isLast = idx == sessions.lastIndex,
                                    onDateTimeChange = { newVal -> vm.updateSessionDateTime(idx, newVal) },
                                    onDurationChange = { newDur -> vm.updateSessionDuration(idx, newDur) },
                                    onDelete = { vm.deleteSession(idx) },
                                    onToggleComplete = { vm.toggleComplete(key) }
                                )
                            }
                        }

                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GhostButton(text = "Redo", onClick = { vm.regenerate() },
                                    modifier = Modifier.weight(1f), height = 50.dp)
                                ForgeButton(text = "Accept Plan", onClick = { vm.acceptPlan(onAccepted) },
                                    modifier = Modifier.weight(2f), height = 50.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanTimelineItem(
    session: com.aiteacher.onboarding.SessionItem, index: Int,
    isDone: Boolean, isLast: Boolean,
    onDateTimeChange: (String) -> Unit,
    onDurationChange: (Int) -> Unit = {},
    onDelete: () -> Unit = {},
    onToggleComplete: () -> Unit
) {
    val fc = forgeColors
    val statusColor = if (isDone) ForgeBrand.Success else ForgeBrand.Orange
    val statusText = if (isDone) "Done" else "Upcoming"

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(34.dp).padding(top = 4.dp)) {
            if (isDone) {
                Box(modifier = Modifier.size(22.dp).clip(CircleShape)
                    .background(ForgeBrand.Success.copy(0.15f)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.CheckCircle, null, tint = ForgeBrand.Success, modifier = Modifier.size(18.dp))
                }
            } else {
                Box(modifier = Modifier.size(16.dp).clip(CircleShape)
                    .background(statusColor).border(3.dp, statusColor.copy(0.25f), CircleShape))
            }
            if (!isLast) {
                Box(modifier = Modifier.width(3.dp).height(100.dp)
                    .background(Brush.verticalGradient(listOf(statusColor, fc.glassBorder))))
            }
        }
        Spacer(Modifier.width(10.dp))
        GlassCard(modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 4.dp),
            shape = RoundedCornerShape(18.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (isDone) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = if (isDone) "Mark incomplete" else "Mark complete",
                            tint = if (isDone) ForgeBrand.Success else fc.textMuted,
                            modifier = Modifier.size(24.dp).clickable { onToggleComplete() })
                        Text(session.topic,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isDone) fc.textMuted else fc.textPrimary,
                            modifier = Modifier.weight(1f))
                    }
                    Surface(shape = RoundedCornerShape(10.dp), color = statusColor.copy(0.12f)) {
                        Text(statusText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = statusColor)
                    }
                }
                // Tags: subject · rank · revision · weakness
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (session.subject.isNotBlank())
                        SessionTag(session.subject.replaceFirstChar { it.uppercase() }, ForgeBrand.Orange)
                    if (session.topicRank > 0)
                        SessionTag("#${session.topicRank}", fc.textMuted)
                    if (session.isRevision)
                        SessionTag("Revision", ForgeBrand.Warning)
                    when {
                        session.weaknessScore >= 70 -> SessionTag("Focus area", ForgeBrand.Error)
                        session.weaknessScore in 40..69 -> SessionTag("Needs work", ForgeBrand.Warning)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("📅 ${session.date}", style = MaterialTheme.typography.bodySmall, color = fc.textMuted)
                    Text("⏱ ${session.duration} min", style = MaterialTheme.typography.bodySmall, color = fc.textMuted)
                }
                if (!isDone) {
                    OutlinedTextField(value = session.isoDateTime ?: "", onValueChange = onDateTimeChange,
                        label = { Text("Schedule", color = forgeColors.textMuted, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = fc.textPrimary, unfocusedTextColor = fc.textSecondary,
                            cursorColor = ForgeBrand.Orange, focusedBorderColor = ForgeBrand.Orange, unfocusedBorderColor = fc.glassBorder,
                            focusedContainerColor = fc.glassFill, unfocusedContainerColor = fc.glassFill,
                            focusedLabelColor = ForgeBrand.OrangeLight, unfocusedLabelColor = fc.textMuted))
                    // Duration & delete row
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = session.duration.toString(),
                            onValueChange = { s -> s.toIntOrNull()?.let(onDurationChange) },
                            label = { Text("Min", color = forgeColors.textMuted, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = fc.textPrimary, unfocusedTextColor = fc.textSecondary,
                                cursorColor = ForgeBrand.Orange, focusedBorderColor = ForgeBrand.Orange, unfocusedBorderColor = fc.glassBorder,
                                focusedContainerColor = fc.glassFill, unfocusedContainerColor = fc.glassFill,
                                focusedLabelColor = ForgeBrand.OrangeLight, unfocusedLabelColor = fc.textMuted))
                        Box(modifier = Modifier.size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ForgeBrand.Error.copy(0.12f))
                            .clickable { onDelete() },
                            contentAlignment = Alignment.Center) {
                            Text("✕", style = MaterialTheme.typography.titleSmall, color = ForgeBrand.Error)
                        }
                    }
                } else if (!session.isoDateTime.isNullOrBlank()) {
                    Text("🕐 ${session.isoDateTime.take(16).replace("T", " at ")}",
                        style = MaterialTheme.typography.bodySmall, color = fc.textMuted)
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = ForgeBrand.Orange, strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
            Text("Generating your plan…", style = MaterialTheme.typography.bodyLarge, color = forgeColors.textSecondary)
        }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(40.dp)) {
            Text("📋", style = MaterialTheme.typography.displayMedium)
            Text("No Plan Yet", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                color = forgeColors.textPrimary)
            Text("Complete onboarding to generate your personalised study plan.",
                style = MaterialTheme.typography.bodyMedium, color = forgeColors.textSecondary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SessionTag(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.13f)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = color
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = forgeColors.textMuted)
    }
}
