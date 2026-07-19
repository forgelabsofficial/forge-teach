package com.aiteacher.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiteacher.onboarding.SessionItem
import com.aiteacher.ai.XpEngine
import java.time.Instant
import java.time.OffsetDateTime

@Composable
fun DashboardScreen(
    onNavigateToPlan: () -> Unit,
    onNavigateToQuiz: (subject: String, topic: String) -> Unit,
    onNavigateToExam: () -> Unit
) {
    val vm: DashboardViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val plan by vm.plan.collectAsState()
    val studentName by vm.studentName.collectAsState()
    val streak by vm.streak.collectAsState()
    val totalXp by vm.totalXp.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val weekComp by vm.weekComparison.collectAsState()
    val mentorMsg by vm.mentorMessage.collectAsState()
    val subjectScores by vm.subjectScores.collectAsState()
    val ctx = LocalContext.current

    val level = XpEngine.levelFromXp(totalXp)
    val levelProgress = XpEngine.progressInLevel(totalXp)

    val firstUpcoming = plan?.sessions?.firstOrNull {
        try { OffsetDateTime.parse(it.isoDateTime ?: it.date).toInstant().isAfter(Instant.now()) }
        catch (_: Exception) { true }
    }
    val nextSessions = plan?.sessions?.filter {
        try { OffsetDateTime.parse(it.isoDateTime ?: it.date).toInstant().isAfter(Instant.now()) }
        catch (_: Exception) { true }
    }?.drop(1)?.take(4) ?: emptyList()

    val greeting = remember {
        val greetings = listOf(
            "Ready to level up?", "Let's unlock something new.",
            "Keep your streak alive!", "One mission today.",
            "Today's going to be fun!", "Your adventure awaits!"
        )
        greetings.random()
    }

    // ── Mentor message animation ─────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "mentorPulse")
    val mentorGlow by infiniteTransition.animateFloat(
        initialValue = 0.06f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "mentorGlow"
    )

    ForgeBackground {
        if (error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(40.dp)) {
                    Text("⚠️", style = MaterialTheme.typography.displaySmall)
                    Text(error!!, style = MaterialTheme.typography.bodyLarge, color = forgeColors.textSecondary,
                        textAlign = TextAlign.Center)
                    ForgeButton(text = "Retry", onClick = { vm.loadData() }, modifier = Modifier.width(160.dp), height = 48.dp)
                }
            }
            return@ForgeBackground
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(color = ForgeBrand.Orange, strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
                    Text("Loading your adventure…", style = MaterialTheme.typography.bodyLarge, color = forgeColors.textSecondary)
                }
            }
            return@ForgeBackground
        }

        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ─── Compact top bar: avatar + level + streak ─────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(ForgeBrand.Orange, ForgeBrand.OrangeDark))),
                        contentAlignment = Alignment.Center) {
                        Text(studentName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(studentName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = forgeColors.textPrimary)
                        Text(greeting, style = MaterialTheme.typography.bodySmall, color = forgeColors.textMuted)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        .background(ForgeBrand.Orange.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("Lv.$level", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = ForgeBrand.OrangeLight)
                    }
                    if (streak >= 2) {
                        Text("🔥$streak", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = ForgeBrand.Warning)
                    }
                }
            }

            // ─── AI Mentor Message Card ──────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(ForgeBrand.Orange.copy(alpha = mentorGlow))
                .border(1.dp, ForgeBrand.Orange.copy(alpha = 0.2f), RoundedCornerShape(18.dp))) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape)
                        .background(ForgeBrand.Orange.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center) {
                        Text("🧠", style = MaterialTheme.typography.titleMedium)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("AI Mentor", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = ForgeBrand.OrangeLight)
                        Text(mentorMsg, style = MaterialTheme.typography.bodyMedium,
                            color = forgeColors.textPrimary)
                    }
                }
            }

            // ─── HERO CARD: Today's Mission ─────────────────────────────────
            if (firstUpcoming != null) {
                val missionEmoji = when {
                    firstUpcoming.isRevision -> "🔄"
                    firstUpcoming.weaknessScore >= 70 -> "⚔️"
                    else -> "📖"
                }
                val missionTitle = if (firstUpcoming.isRevision) "Memory Boost" else "Today's Adventure"
                val missionSubtitle = firstUpcoming.topic
                val missionWorld = firstUpcoming.subject.replaceFirstChar { it.uppercase() }
                    .let { when (it) {
                        "Math", "Mathematics" -> "🏰 Math Kingdom"
                        "Science" -> "🧪 Science Lab"
                        "English" -> "📚 English Library"
                        "History" -> "🌍 History Explorer"
                        "Ict", "Computing" -> "💻 Code Factory"
                        "Arts" -> "🎨 Creative Studio"
                        else -> it
                    }}
                val xpReward = XpEngine.XP_SESSION_COMPLETE + if (firstUpcoming.weaknessScore >= 60) 20 else 0

                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(Brush.verticalGradient(
                        0.0f to ForgeBrand.Orange,
                        0.5f to ForgeBrand.OrangeDark,
                        1.0f to Color(0xFF1A0A00)
                    ))) {
                    Column(modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(missionEmoji, style = MaterialTheme.typography.titleLarge)
                            Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.15f)) {
                                Text(missionWorld, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White)
                            }
                        }
                        Text(missionTitle, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                            color = Color.White)
                        Text(missionSubtitle, style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.9f))

                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LinearProgressIndicator(
                                progress = if (plan?.sessions?.isNotEmpty() == true)
                                    plan!!.sessions.count { s ->
                                        try { OffsetDateTime.parse(s.isoDateTime ?: s.date).toInstant().isBefore(Instant.now()) }
                                        catch (_: Exception) { false }
                                    }.toFloat() / plan!!.sessions.size else 0f,
                                modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = Color.White.copy(alpha = 0.8f), trackColor = Color.White.copy(alpha = 0.2f)
                            )
                            Text("${firstUpcoming.duration} min", style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.8f))
                        }

                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("⭐", style = MaterialTheme.typography.titleSmall)
                                Text("+$xpReward XP", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White)
                            }
                            if (firstUpcoming.weaknessScore >= 50) {
                                Surface(shape = RoundedCornerShape(10.dp), color = ForgeBrand.Warning.copy(alpha = 0.3f)) {
                                    Text("Focus area", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall, color = ForgeBrand.Warning)
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable { onNavigateToPlan() }
                            .padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                            Text(if (firstUpcoming.isRevision) "▶ Start Memory Boost" else "▶ Continue Adventure",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White)
                        }
                    }
                }
            }

            // ─── XP progress bar ──────────────────────────────────────────────
            GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("⭐", style = MaterialTheme.typography.titleSmall)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Level $level", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = forgeColors.textPrimary)
                            Text("$totalXp / ${level * 200} XP", style = MaterialTheme.typography.labelSmall,
                                color = forgeColors.textMuted)
                        }
                        LinearProgressIndicator(progress = levelProgress,
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = ForgeBrand.Orange, trackColor = forgeColors.glassBorder)
                    }
                }
            }

            // ─── This Week vs Last Week ─────────────────────────────────────
            GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your Growth", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = forgeColors.textSecondary)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        GrowthMetric(
                            label = "Sessions",
                            current = weekComp.thisWeekSessions.toString(),
                            previous = weekComp.lastWeekSessions.toString(),
                            change = weekComp.sessionChange
                        )
                        GrowthMetric(
                            label = "Minutes",
                            current = "${weekComp.thisWeekMinutes}",
                            previous = "${weekComp.lastWeekMinutes}",
                            change = weekComp.minuteChange
                        )
                        GrowthMetric(
                            label = "XP",
                            current = "${weekComp.thisWeekXp}",
                            previous = "${weekComp.lastWeekXp}",
                            change = weekComp.xpChange
                        )
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text(
                            text = if (weekComp.sessionChange >= 0f) "📈 This week beats last week" else "📉 This week lags behind last",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (weekComp.sessionChange >= 0f) ForgeBrand.Success else forgeColors.textMuted
                        )
                    }
                }
            }

            // ─── Subject mastery bars ──────────────────────────────────────────
            if (subjectScores.isNotEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Subject Progress", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = forgeColors.textSecondary)
                        subjectScores.entries.take(4).forEach { (subject, score) ->
                            val world = when {
                                subject.contains("math", ignoreCase = true) -> "🏰 Math"
                                subject.contains("science", ignoreCase = true) -> "🧪 Science"
                                subject.contains("english", ignoreCase = true) -> "📚 English"
                                subject.contains("history", ignoreCase = true) -> "🌍 History"
                                subject.contains("ict", ignoreCase = true) || subject.contains("comput", ignoreCase = true) -> "💻 Code"
                                else -> "📖 ${subject.replaceFirstChar { it.uppercase() }}"
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(world, style = MaterialTheme.typography.bodySmall, color = forgeColors.textPrimary,
                                    modifier = Modifier.width(90.dp))
                                LinearProgressIndicator(progress = score / 100f,
                                    modifier = Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(4.dp)),
                                    color = when { score >= 70 -> ForgeBrand.Success; score >= 40 -> ForgeBrand.Warning; else -> ForgeBrand.Error },
                                    trackColor = forgeColors.glassBorder)
                                Text("$score%", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = forgeColors.textMuted, modifier = Modifier.width(34.dp), textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            }

            // ─── Next Unlocks ─────────────────────────────────────────────────
            if (nextSessions.isNotEmpty()) {
                Text("Next Unlocks", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = forgeColors.textSecondary)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    nextSessions.forEach { session ->
                        val emoji = when {
                            session.isRevision -> "🔄"
                            session.weaknessScore >= 70 -> "⚔️"
                            else -> "📖"
                        }
                        GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Row(modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
                                    .background(ForgeBrand.Orange.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center) {
                                    Text(emoji, style = MaterialTheme.typography.titleMedium)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(session.topic, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = forgeColors.textPrimary)
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(session.date, style = MaterialTheme.typography.bodySmall, color = forgeColors.textMuted)
                                        Text("${session.duration}m", style = MaterialTheme.typography.bodySmall, color = forgeColors.textMuted)
                                    }
                                }
                                if (session.isRevision) {
                                    Surface(shape = RoundedCornerShape(8.dp), color = ForgeBrand.Teal.copy(alpha = 0.15f)) {
                                        Text("Boost", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = ForgeBrand.Teal)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GrowthMetric(
    label: String,
    current: String,
    previous: String,
    change: Float
) {
    val changeEmoji = when {
        change > 0.05f -> "↑"
        change < -0.05f -> "↓"
        else -> "→"
    }
    val changeColor = when {
        change > 0.05f -> ForgeBrand.Success
        change < -0.05f -> ForgeBrand.Error
        else -> forgeColors.textMuted
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = forgeColors.textMuted)
        Text(current, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
            color = forgeColors.textPrimary)
        Text("$changeEmoji $previous",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = changeColor)
    }
}
