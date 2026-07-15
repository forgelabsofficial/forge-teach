package com.aiteacher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiteacher.data.PlanRepository
import com.aiteacher.onboarding.Plan
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.OffsetDateTime

@Composable
fun ProfileScreen(
    repo: PlanRepository? = null,
    onClose: () -> Unit = {},
    onRedoOnboarding: () -> Unit = {},
    onRegeneratePlan: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val repository = repo ?: remember { PlanRepository(ctx.applicationContext) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var studentName by remember { mutableStateOf("") }
    var timezone by remember { mutableStateOf("") }
    var plans by remember { mutableStateOf<List<Plan>>(emptyList()) }

    LaunchedEffect(Unit) {
        val s = repository.getLatestStudentProfile()
        if (s != null) {
            studentName = s.name
            timezone = s.timezone ?: ""
            plans = repository.getPlansForStudent(s.id)
        }
    }

    val activePlan = plans.firstOrNull()
    val totalSessions = activePlan?.sessions?.size ?: 0
    val completed = activePlan?.sessions?.count {
        try { OffsetDateTime.parse(it.isoDateTime ?: it.date).toInstant().isBefore(Instant.now()) }
        catch (_: Exception) { false }
    } ?: 0
    val studyHours = (activePlan?.sessions?.sumOf { it.duration } ?: 0) / 60f

    Scaffold(snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Transparent) { padding ->
        ForgeBackground {
            Column(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // ─── Header ───────────────────────────────────────────────────
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Profile",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black),
                        color = forgeColors.textPrimary)
                    IconButton(onClick = onClose,
                        modifier = Modifier.size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(forgeColors.glassFill)) {
                        Text("✕", style = MaterialTheme.typography.titleMedium,
                            color = forgeColors.textSecondary)
                    }
                }

                // ─── Avatar + stats ───────────────────────────────────────────
                GlassCard(modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp)) {
                    Column(modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {

                        // Avatar with glow
                        Box(modifier = Modifier.size(80.dp),
                            contentAlignment = Alignment.Center) {
                            Box(modifier = Modifier.size(80.dp).clip(CircleShape)
                                .background(ForgeBrand.Orange.copy(alpha = 0.10f)))
                            Box(modifier = Modifier.size(68.dp).clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(
                                        ForgeBrand.Orange, ForgeBrand.OrangeDark
                                    ))
                                ),
                                contentAlignment = Alignment.Center) {
                                Text(studentName.take(2).uppercase(),
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Black),
                                    color = Color.White)
                            }
                        }

                        Text(studentName.ifBlank { "Student" },
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold),
                            color = forgeColors.textPrimary)

                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly) {
                            ProfileStat("$completed/$totalSessions", "Lessons",
                                "✅", ForgeBrand.Success)
                            ProfileStat("%.1fh".format(studyHours), "Study time",
                                "⭐", ForgeBrand.Orange)
                            ProfileStat("${plans.size}", "Plans",
                                "📅", ForgeBrand.Orange)
                        }
                    }
                }

                // ─── Edit fields ──────────────────────────────────────────────
                GlassCard(modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)) {
                    Column(modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Edit Profile",
                            style = MaterialTheme.typography.titleSmall,
                            color = forgeColors.textSecondary)

                        OutlinedTextField(
                            value = studentName, onValueChange = { studentName = it },
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = forgeFieldColors()
                        )
                        OutlinedTextField(
                            value = timezone, onValueChange = { timezone = it },
                            label = { Text("Timezone (e.g. Africa/Lagos)") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = forgeFieldColors()
                        )

                        ForgeButton(
                            text = "Save Changes",
                            onClick = {
                                scope.launch {
                                    val s = repository.getLatestStudentProfile()
                                    if (s != null) {
                                        repository.updateStudentProfile(s.id, studentName,
                                            timezone.ifBlank { null })
                                        val s2 = repository.getLatestStudentProfile()
                                        if (s2 != null) plans = repository.getPlansForStudent(s2.id)
                                        snackbar.showSnackbar("Saved ✓")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            height = 50.dp
                        )
                    }
                }

                // ─── Actions ──────────────────────────────────────────────────
                GlassCard(modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)) {
                    Column(modifier = Modifier.padding(4.dp)) {
                        Text("Actions",
                            style = MaterialTheme.typography.titleSmall,
                            color = forgeColors.textSecondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                        ProfileActionRow(
                            emoji = "🔄",
                            label = "Redo Onboarding",
                            desc = "Update country, subjects, or retake the test",
                            onClick = onRedoOnboarding
                        )
                        ForgeDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ProfileActionRow(
                            emoji = "📋",
                            label = "Regenerate Plan",
                            desc = "Create a fresh study plan with current settings",
                            onClick = onRegeneratePlan
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun ProfileActionRow(emoji: String, label: String, desc: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }
        .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = forgeColors.textPrimary)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = forgeColors.textMuted)
        }
        Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(forgeColors.glassFill),
            contentAlignment = Alignment.Center) {
            Text("›", style = MaterialTheme.typography.titleMedium, color = forgeColors.textMuted)
        }
    }
}

@Composable
private fun ProfileStat(
    value: String,
    label: String,
    emoji: String,
    tint: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Text(value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
            color = forgeColors.textPrimary)
        Text(label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = androidx.compose.ui.unit.TextUnit(
                    0.8f, androidx.compose.ui.unit.TextUnitType.Sp
                )
            ),
            color = tint)
    }
}
