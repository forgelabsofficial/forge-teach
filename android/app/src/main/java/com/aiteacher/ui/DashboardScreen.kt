package com.aiteacher.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiteacher.onboarding.SessionItem
import java.time.Instant
import java.time.OffsetDateTime

import com.aiteacher.ai.XpEngine

@Composable
fun DashboardScreen(
    onNavigateToPlan: () -> Unit,
    onNavigateToQuiz: (subject: String, topic: String) -> Unit,
    onNavigateToExam: () -> Unit
) {
    val vm: DashboardViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val plan by vm.plan.collectAsState()
    val studentName by vm.studentName.collectAsState()
    val completedCount by vm.completedCount.collectAsState()
    val totalCount by vm.totalCount.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val ctx = LocalContext.current

    val totalXp by vm.totalXp.collectAsState()
    val streak by vm.streak.collectAsState()
    val level = XpEngine.levelFromXp(totalXp)
    val levelProgress = XpEngine.progressInLevel(totalXp)
    val xpToNext = XpEngine.xpToNextLevel(totalXp)

    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1400, easing = FastOutSlowInEasing),
        label = "ring"
    )

    ForgeBackground {
        // Error state with retry
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

        // Loading skeleton
        if (loading) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ShimmerBlock(width = 140, height = 40)
                    ShimmerBlock(width = 48, height = 48, shape = CircleShape)
                }
                ShimmerBlock(width = 300, height = 140, shape = RoundedCornerShape(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(3) { ShimmerBlock(width = 110, height = 100, shape = RoundedCornerShape(22.dp)) }
                }
                ShimmerBlock(width = 200, height = 140, shape = RoundedCornerShape(24.dp))
            }
            return@ForgeBackground
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ─── Greeting + Level badge ───────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Good day,",
                        style = MaterialTheme.typography.bodyMedium,
                        color = forgeColors.textMuted)
                    Text(studentName,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black),
                        color = forgeColors.textPrimary)
                    // XP level + progress bar
                    GlassCard(modifier = Modifier.width(160.dp), shape = RoundedCornerShape(14.dp)) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("Lv.$level", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = ForgeBrand.OrangeLight)
                                Text("$totalXp XP", style = MaterialTheme.typography.labelSmall,
                                    color = forgeColors.textMuted)
                            }
                            LinearProgressIndicator(
                                progress = levelProgress,
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = ForgeBrand.Orange, trackColor = forgeColors.glassBorder
                            )
                            if (streak >= 2) {
                                Text("🔥 $streak-day streak",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ForgeBrand.Warning)
                            }
                        }
                    }
                }
                // Avatar with orange glow
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                            .background(ForgeBrand.Orange.copy(alpha = 0.12f))
                    )
                    Box(
                        modifier = Modifier.size(42.dp).clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(
                                    ForgeBrand.Orange, ForgeBrand.OrangeDark
                                ))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(studentName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold),
                            color = Color.White)
                    }
                }
            }

            // ─── Progress ring card ────────────────────────────────────────────
            GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OrangeAccentHeader(title = "Study Progress")
                        Text("$completedCount of $totalCount sessions",
                            style = MaterialTheme.typography.bodyMedium,
                            color = forgeColors.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        ForgeButton(
                            text = "View Plan",
                            onClick = onNavigateToPlan,
                            modifier = Modifier.width(140.dp),
                            height = 44.dp
                        )
                    }
                    DashboardProgressRing(progress = animatedProgress)
                }
            }

            // ─── Stats row ────────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(modifier = Modifier.weight(1f),
                    emoji = "✅", tint = ForgeBrand.Success,
                    value = "$completedCount", label = "Done")
                StatCard(modifier = Modifier.weight(1f),
                    emoji = "⭐", tint = ForgeBrand.Gold,
                    value = "${totalCount - completedCount}", label = "Left")
                StatCard(modifier = Modifier.weight(1f),
                    emoji = "📅", tint = ForgeBrand.Orange,
                    value = "${plan?.weeks ?: 0}", label = "Weeks")
            }

            // ─── Next session ─────────────────────────────────────────────────
            val nextSession = plan?.sessions?.firstOrNull {
                try { OffsetDateTime.parse(it.isoDateTime ?: it.date)
                    .toInstant().isAfter(Instant.now())
                } catch (_: Exception) { true }
            }

            if (nextSession != null) {
                SectionLabel("Next Session")
                NextSessionBanner(session = nextSession)
            }

            // ─── Upcoming list ────────────────────────────────────────────────
            val upcoming = plan?.sessions?.filter {
                try { OffsetDateTime.parse(it.isoDateTime ?: it.date)
                    .toInstant().isAfter(Instant.now())
                } catch (_: Exception) { true }
            }?.drop(1)?.take(5) ?: emptyList()

            if (upcoming.isNotEmpty()) {
                SectionLabel("Upcoming")
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    upcoming.forEachIndexed { idx, session ->
                        UpcomingRow(session = session, isLast = idx == upcoming.lastIndex)
                    }
                }
            }

            // ─── Welcome image ────────────────────────────────────────────────
            val bitmap = remember {
                try { BitmapFactory.decodeStream(ctx.assets.open("dashboard_welcome.png")) }
                catch (_: Exception) { null }
            }
            if (bitmap != null) {
                Image(bitmap = bitmap.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                        .clip(RoundedCornerShape(24.dp)))
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─── Dashboard progress ring ───────────────────────────────────────────────────

@Composable
private fun DashboardProgressRing(progress: Float) {
    val fc = forgeColors
    var pulse by remember { mutableStateOf(false) }
    val pulseAlpha by animateFloatAsState(
        targetValue = if (progress > 0.5f || pulse) 1f else 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringPulse"
    )
    LaunchedEffect(progress) {
        if (progress >= 1f) pulse = true
    }

    Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
        // Outer glow ring
        Canvas(modifier = Modifier.size(110.dp)) {
            val outerStroke = 14.dp.toPx()
            val outerInset = outerStroke / 2
            val outerArcSize = Size(size.width - outerStroke, size.height - outerStroke)
            drawArc(
                color = ForgeBrand.Orange.copy(alpha = 0.06f),
                startAngle = -90f, sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(outerInset, outerInset),
                size = outerArcSize,
                style = Stroke(outerStroke, cap = StrokeCap.Round)
            )
        }
        // Main ring
        Canvas(modifier = Modifier.size(92.dp)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            // Track
            drawArc(color = fc.glassBorder, startAngle = -90f, sweepAngle = 360f,
                useCenter = false, topLeft = topLeft, size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
            if (progress > 0f) {
                // Gradient arc
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to ForgeBrand.Orange,
                        0.5f to ForgeBrand.Orange,
                        1f to ForgeBrand.Success,
                        center = Offset(size.width / 2, size.height / 2)
                    ),
                    startAngle = -90f, sweepAngle = progress * 360f,
                    useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        // Percentage text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                color = fc.textPrimary)
            Text("complete",
                style = MaterialTheme.typography.labelSmall,
                color = fc.textMuted)
        }
    }
}

// ─── Stat card ────────────────────────────────────────────────────────────────

@Composable
private fun StatCard(
    modifier: Modifier,
    emoji: String,
    tint: Color,
    value: String,
    label: String
) {
    GlassCard(modifier = modifier, shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Text(value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
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
}

// ─── Next session banner ──────────────────────────────────────────────────────

@Composable
private fun NextSessionBanner(session: SessionItem) {
    Box(modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(Brush.horizontalGradient(listOf(
            ForgeBrand.Orange, ForgeBrand.OrangeDark
        )))
    ) {
        Column(modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(session.topic,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black),
                color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("📆 ${session.date}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f))
                Text("⏱ ${session.duration} min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f))
            }
            if (!session.isoDateTime.isNullOrBlank()) {
                val t = session.isoDateTime.take(16).replace("T", " at ")
                Text("🕐 $t",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

// ─── Upcoming row ─────────────────────────────────────────────────────────────

@Composable
private fun UpcomingRow(session: SessionItem, isLast: Boolean) {
    val fc = forgeColors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // Timeline gutter with gradient connector
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp)) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape)
                .background(ForgeBrand.Success)
                .border(2.dp, ForgeBrand.Success.copy(0.3f), CircleShape))
            if (!isLast) {
                Box(modifier = Modifier.width(3.dp).height(60.dp)
                    .background(
                        Brush.verticalGradient(listOf(
                            ForgeBrand.Success, fc.glassBorder
                        ))
                    ))
            }
        }
        Spacer(Modifier.width(12.dp))
        GlassCard(modifier = Modifier.weight(1f)
            .padding(bottom = if (isLast) 0.dp else 8.dp),
            shape = RoundedCornerShape(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(session.topic,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold),
                        color = fc.textPrimary)
                    Text(session.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = fc.textMuted)
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ForgeBrand.Success.copy(alpha = 0.12f)
                ) {
                    Text("${session.duration}m",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold),
                        color = ForgeBrand.Success)
                }
            }
        }
    }
}
