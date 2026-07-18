package com.aiteacher.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiteacher.onboarding.CapabilityQuestion
import com.aiteacher.onboarding.StudentProfile

@Composable
fun QuizScreen(
    subject: String,
    topic: String,
    profile: StudentProfile,
    onFinished: () -> Unit
) {
    val vm: QuizViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current

    LaunchedEffect(subject, topic) {
        if (state == QuizState.IDLE) vm.startQuiz(ctx, subject, topic, profile)
    }

    ForgeBackground {
        when (state) {
            QuizState.IDLE, QuizState.LOADING -> QuizLoadingState()
            QuizState.IN_PROGRESS -> QuizInProgress(vm = vm)
            QuizState.FINISHED -> QuizResultScreen(vm = vm, onDone = {
                vm.reset()
                onFinished()
            })
        }
    }
}

@Composable
private fun QuizLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = ForgeBrand.Teal, strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
            Text("Generating quiz…", style = MaterialTheme.typography.bodyLarge, color = forgeColors.textSecondary)
        }
    }
}

@Composable
private fun QuizInProgress(vm: QuizViewModel) {
    val questions by vm.questions.collectAsState()
    val currentIndex by vm.currentIndex.collectAsState()
    val selectedAnswers by vm.selectedAnswers.collectAsState()

    if (questions.isEmpty()) return
    val q = questions[currentIndex]
    val selected = selectedAnswers[currentIndex]
    val answered = selected != null

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Question ${currentIndex + 1} of ${questions.size}",
                style = MaterialTheme.typography.labelLarge, color = forgeColors.textMuted)
            Surface(shape = RoundedCornerShape(10.dp), color = ForgeBrand.Teal.copy(0.15f)) {
                Text(q.subject.replaceFirstChar { it.uppercase() },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = ForgeBrand.Teal)
            }
        }

        // Progress bar
        LinearProgressIndicator(
            progress = (currentIndex + 1f) / questions.size,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = ForgeBrand.Teal, trackColor = forgeColors.glassBorder
        )

        // Question card
        GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Text(q.questionText,
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = forgeColors.textPrimary)
        }

        // Options
        q.options.forEachIndexed { idx, option ->
            val isSelected = selected == idx
            val isCorrect = answered && idx == q.correctIndex
            val isWrong = answered && isSelected && idx != q.correctIndex
            val borderColor = when {
                isCorrect -> ForgeBrand.Success
                isWrong   -> ForgeBrand.Error
                isSelected -> ForgeBrand.Teal
                else       -> forgeColors.glassBorder
            }
            val bgColor = when {
                isCorrect -> ForgeBrand.Success.copy(0.12f)
                isWrong   -> ForgeBrand.Error.copy(0.12f)
                isSelected -> ForgeBrand.Teal.copy(0.12f)
                else       -> forgeColors.glassFill
            }
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(bgColor)
                    .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
                    .clickable(enabled = !answered) { vm.selectAnswer(currentIndex, idx) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape)
                        .background(borderColor.copy(0.2f))
                        .border(1.dp, borderColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(listOf("A","B","C","D").getOrElse(idx) { "$idx" },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = borderColor)
                }
                Text(option, style = MaterialTheme.typography.bodyMedium, color = forgeColors.textPrimary, modifier = Modifier.weight(1f))
                if (isCorrect) Text("✓", style = MaterialTheme.typography.titleMedium, color = ForgeBrand.Success)
                if (isWrong)   Text("✗", style = MaterialTheme.typography.titleMedium, color = ForgeBrand.Error)
            }
        }

        if (answered) {
            ForgeButton(
                text = if (currentIndex == questions.lastIndex) "See Results" else "Next →",
                onClick = { vm.nextQuestion() },
                modifier = Modifier.fillMaxWidth(), height = 52.dp
            )
        }
    }
}

@Composable
private fun QuizResultScreen(vm: QuizViewModel, onDone: () -> Unit) {
    val score by vm.score.collectAsState()
    val xp by vm.xpEarned.collectAsState()
    val questions by vm.questions.collectAsState()
    val answers by vm.selectedAnswers.collectAsState()

    val correct = questions.count { q -> answers[questions.indexOf(q)] == q.correctIndex }
    val emoji = when {
        score >= 80 -> "🏆"
        score >= 60 -> "👍"
        score >= 40 -> "📚"
        else        -> "💪"
    }
    val message = when {
        score >= 80 -> "Excellent work!"
        score >= 60 -> "Good effort!"
        score >= 40 -> "Keep practising"
        else        -> "Don't give up!"
    }
    val ringColor = when {
        score >= 80 -> ForgeBrand.Success
        score >= 60 -> ForgeBrand.Teal
        score >= 40 -> ForgeBrand.Warning
        else        -> ForgeBrand.Error
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(emoji, style = MaterialTheme.typography.displayMedium)
        Text(message, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
            color = forgeColors.textPrimary)

        // Score ring
        Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = score / 100f,
                modifier = Modifier.size(140.dp),
                color = ringColor, trackColor = forgeColors.glassBorder,
                strokeWidth = 10.dp
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$score%", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                    color = forgeColors.textPrimary)
                Text("score", style = MaterialTheme.typography.labelSmall, color = forgeColors.textMuted)
            }
        }

        // Stats row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuizStatCard(Modifier.weight(1f), "✅", "$correct/${questions.size}", "Correct", ForgeBrand.Success)
            QuizStatCard(Modifier.weight(1f), "⚡", "+$xp", "XP Earned", ForgeBrand.Gold)
        }

        // Per-question breakdown
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Review", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = forgeColors.textSecondary)
                questions.forEachIndexed { idx, q ->
                    val userAnswer = answers[idx]
                    val isCorrect = userAnswer == q.correctIndex
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top) {
                        Text(if (isCorrect) "✓" else "✗",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isCorrect) ForgeBrand.Success else ForgeBrand.Error)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(q.questionText, style = MaterialTheme.typography.bodySmall, color = forgeColors.textPrimary)
                            if (!isCorrect) {
                                Text("Correct: ${q.options.getOrElse(q.correctIndex) { "?" }}",
                                    style = MaterialTheme.typography.labelSmall, color = ForgeBrand.Success)
                            }
                        }
                    }
                }
            }
        }

        ForgeButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth(), height = 52.dp)
    }
}

@Composable
private fun QuizStatCard(modifier: Modifier, emoji: String, value: String, label: String, color: Color) {
    GlassCard(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = forgeColors.textPrimary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}
