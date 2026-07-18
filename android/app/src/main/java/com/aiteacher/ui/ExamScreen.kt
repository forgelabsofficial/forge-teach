package com.aiteacher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.aiteacher.onboarding.StudentProfile

@Composable
fun ExamScreen(
    profile: StudentProfile,
    availableSubjects: List<String>,
    onFinished: () -> Unit
) {
    val vm: ExamViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current

    ForgeBackground {
        when (state) {
            ExamState.CONFIG  -> ExamConfigScreen(availableSubjects, onStart = { cfg ->
                vm.startExam(ctx, cfg, profile)
            })
            ExamState.LOADING -> ExamLoadingState()
            ExamState.IN_PROGRESS, ExamState.PAUSED -> ExamInProgress(vm)
            ExamState.FINISHED -> ExamReportCard(vm, onDone = { vm.reset(); onFinished() })
        }
    }
}

@Composable
private fun ExamConfigScreen(subjects: List<String>, onStart: (ExamConfig) -> Unit) {
    var examName by remember { mutableStateOf("Mock Exam") }
    var selectedSubjects by remember { mutableStateOf(subjects.take(3).toSet()) }
    var durationMinutes by remember { mutableStateOf(60) }
    var questionCount by remember { mutableStateOf(30) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Exam Setup", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
            color = forgeColors.textPrimary)
        Text("Configure your mock exam conditions", style = MaterialTheme.typography.bodyMedium, color = forgeColors.textSecondary)

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(value = examName, onValueChange = { examName = it },
                    label = { Text("Exam Name") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(14.dp), colors = forgeFieldColors())

                Text("Subjects", style = MaterialTheme.typography.labelLarge, color = forgeColors.textSecondary)
                subjects.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { sub ->
                            val sel = sub in selectedSubjects
                            Surface(
                                modifier = Modifier.weight(1f).clickable {
                                    selectedSubjects = if (sel) selectedSubjects - sub else selectedSubjects + sub
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (sel) ForgeBrand.Pink.copy(0.15f) else forgeColors.glassFill,
                                border = if (sel) BorderStroke(1.5.dp, ForgeBrand.Pink) else BorderStroke(1.dp, forgeColors.glassBorder)
                            ) {
                                Text(sub.replaceFirstChar { it.uppercase() },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).fillMaxWidth(),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (sel) ForgeBrand.Pink else forgeColors.textSecondary,
                                    textAlign = TextAlign.Center)
                            }
                        }
                        // fill remaining slots
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                Text("Duration: $durationMinutes min", style = MaterialTheme.typography.labelLarge, color = forgeColors.textSecondary)
                Slider(value = durationMinutes.toFloat(), onValueChange = { durationMinutes = it.toInt() },
                    valueRange = 15f..180f, steps = 10,
                    colors = SliderDefaults.colors(thumbColor = ForgeBrand.Pink, activeTrackColor = ForgeBrand.Pink))

                Text("Questions: $questionCount", style = MaterialTheme.typography.labelLarge, color = forgeColors.textSecondary)
                Slider(value = questionCount.toFloat(), onValueChange = { questionCount = it.toInt() },
                    valueRange = 10f..60f, steps = 9,
                    colors = SliderDefaults.colors(thumbColor = ForgeBrand.Pink, activeTrackColor = ForgeBrand.Pink))
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brush.horizontalGradient(listOf(ForgeBrand.Pink, Color(0xFFCC0055))))
                .clickable(enabled = selectedSubjects.isNotEmpty()) {
                    onStart(ExamConfig(examName, selectedSubjects.toList(), durationMinutes, questionCount))
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Start Exam", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
        }
    }
}

@Composable
private fun ExamLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = ForgeBrand.Pink, strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
            Text("Preparing exam…", style = MaterialTheme.typography.bodyLarge, color = forgeColors.textSecondary)
        }
    }
}

@Composable
private fun ExamInProgress(vm: ExamViewModel) {
    val questions by vm.questions.collectAsState()
    val currentIndex by vm.currentIndex.collectAsState()
    val selectedAnswers by vm.selectedAnswers.collectAsState()
    val remainingSeconds by vm.remainingSeconds.collectAsState()
    val state by vm.state.collectAsState()
    val isPaused = state == ExamState.PAUSED

    val mins = remainingSeconds / 60
    val secs = remainingSeconds % 60
    val timerColor by animateColorAsState(
        targetValue = if (remainingSeconds < 300) ForgeBrand.Error else ForgeBrand.Pink,
        animationSpec = tween(500), label = "timerColor"
    )

    if (questions.isEmpty()) return
    val q = questions[currentIndex]
    val selected = selectedAnswers[currentIndex]

    Column(Modifier.fillMaxSize()) {
        // Top bar
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(ForgeBrand.Pink, Color(0xFFCC0055))))
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${currentIndex + 1} / ${questions.size}",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                Text(if (isPaused) "PAUSED" else "%02d:%02d".format(mins, secs),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black), color = timerColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(0.2f),
                        modifier = Modifier.clickable { vm.pauseResume() }) {
                        Text(if (isPaused) "▶" else "⏸", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                    Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(0.2f),
                        modifier = Modifier.clickable { vm.submitExam() }) {
                        Text("Submit", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }
                }
            }
        }

        // Question navigator
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(questions) { idx, _ ->
                val isAnswered = selectedAnswers.containsKey(idx)
                val isCurrent = idx == currentIndex
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape)
                        .background(when {
                            isCurrent  -> ForgeBrand.Pink
                            isAnswered -> ForgeBrand.Success.copy(0.3f)
                            else       -> forgeColors.glassFill
                        })
                        .border(1.dp, if (isCurrent) ForgeBrand.Pink else forgeColors.glassBorder, CircleShape)
                        .clickable { vm.navigateTo(idx) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("${idx + 1}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isCurrent) Color.White else forgeColors.textMuted)
                }
            }
        }

        // Question + options
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = ForgeBrand.Pink.copy(0.15f)) {
                        Text(q.subject.replaceFirstChar { it.uppercase() },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ForgeBrand.Pink)
                    }
                    Text(q.questionText, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = forgeColors.textPrimary)
                }
            }

            q.options.forEachIndexed { idx, option ->
                val isSelected = selected == idx
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) ForgeBrand.Pink.copy(0.12f) else forgeColors.glassFill)
                        .border(1.5.dp, if (isSelected) ForgeBrand.Pink else forgeColors.glassBorder, RoundedCornerShape(14.dp))
                        .clickable { vm.selectAnswer(currentIndex, idx) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.size(26.dp).clip(CircleShape)
                            .background(if (isSelected) ForgeBrand.Pink.copy(0.2f) else forgeColors.glassFill)
                            .border(1.dp, if (isSelected) ForgeBrand.Pink else forgeColors.glassBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(listOf("A","B","C","D").getOrElse(idx) { "$idx" },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) ForgeBrand.Pink else forgeColors.textMuted)
                    }
                    Text(option, style = MaterialTheme.typography.bodyMedium, color = forgeColors.textPrimary, modifier = Modifier.weight(1f))
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (currentIndex > 0) {
                    GhostButton("← Prev", onClick = { vm.navigateTo(currentIndex - 1) }, modifier = Modifier.weight(1f), height = 48.dp)
                }
                if (currentIndex < questions.lastIndex) {
                    ForgeButton("Next →", onClick = { vm.navigateTo(currentIndex + 1) }, modifier = Modifier.weight(1f), height = 48.dp)
                }
            }
        }
    }
}

@Composable
private fun ExamReportCard(vm: ExamViewModel, onDone: () -> Unit) {
    val score by vm.scorePercent.collectAsState()
    val subjectScores by vm.subjectScores.collectAsState()
    val xp by vm.xpEarned.collectAsState()
    val config by vm.config.collectAsState()
    val questions by vm.questions.collectAsState()
    val answers by vm.selectedAnswers.collectAsState()
    val correct = questions.count { q -> answers[questions.indexOf(q)] == q.correctIndex }

    val grade = when {
        score >= 90 -> "A+" to ForgeBrand.Success
        score >= 80 -> "A"  to ForgeBrand.Success
        score >= 70 -> "B"  to ForgeBrand.Teal
        score >= 60 -> "C"  to ForgeBrand.Warning
        score >= 50 -> "D"  to ForgeBrand.Warning
        else        -> "F"  to ForgeBrand.Error
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.horizontalGradient(listOf(ForgeBrand.Pink, Color(0xFFCC0055))))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Exam Report", style = MaterialTheme.typography.titleSmall, color = Color.White.copy(0.8f))
                Text(config?.examName ?: "Mock Exam",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black), color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("$correct/${questions.size} correct", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.85f))
                    Text("+$xp XP", style = MaterialTheme.typography.bodyMedium, color = ForgeBrand.Gold)
                }
            }
        }

        // Grade + score
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(grade.first, style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black), color = grade.second)
                    Text("Grade", style = MaterialTheme.typography.labelSmall, color = forgeColors.textMuted)
                }
            }
            GlassCard(Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("$score%", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), color = forgeColors.textPrimary)
                    Text("Score", style = MaterialTheme.typography.labelSmall, color = forgeColors.textMuted)
                }
            }
        }

        // Per-subject breakdown
        if (subjectScores.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Subject Breakdown", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = forgeColors.textSecondary)
                    subjectScores.forEach { (subject, pct) ->
                        val barColor = when {
                            pct >= 70 -> ForgeBrand.Success
                            pct >= 50 -> ForgeBrand.Warning
                            else      -> ForgeBrand.Error
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(subject.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyMedium, color = forgeColors.textPrimary)
                                Text("$pct%", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = barColor)
                            }
                            LinearProgressIndicator(
                                progress = pct / 100f,
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = barColor, trackColor = forgeColors.glassBorder
                            )
                        }
                    }
                }
            }
        }

        ForgeButton("Done", onClick = onDone, modifier = Modifier.fillMaxWidth(), height = 52.dp)
    }
}
