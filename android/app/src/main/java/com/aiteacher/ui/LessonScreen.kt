package com.aiteacher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiteacher.ai.LearningStyleAgent
import com.aiteacher.ai.Lesson
import com.aiteacher.ai.LessonBuilderAgent
import com.aiteacher.ai.ReflectionAgent
import com.aiteacher.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LessonPhase {
    LEARN, CHALLENGE, EXIT_CHECK, COMPLETED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(
    subject: String,
    topic: String,
    onFinished: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(LessonPhase.LEARN) }
    var lesson by remember { mutableStateOf<Lesson?>(null) }
    var dominantStyle by remember { mutableStateOf("analogy") }
    var loading by remember { mutableStateOf(true) }

    var conceptIndex by remember { mutableStateOf(0) }
    var exerciseIndex by remember { mutableStateOf(0) }

    var userExitResponse by remember { mutableStateOf("") }
    var selectedAnswerIndex by remember { mutableStateOf<Int?>(null) }
    var scoreCount by remember { mutableStateOf(0) }

    val startTime = remember { System.currentTimeMillis() }
    var lessonReadTimeSec by remember { mutableStateOf(0) }

    LaunchedEffect(topic) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(ctx)
            val style = LearningStyleAgent.getDominantStyle(db)
            val topicId = "${subject.lowercase()}_${topic.lowercase().replace(" ", "_")}"
            val l = LessonBuilderAgent.getLesson(ctx, db, topicId, subject, topic, style)
            dominantStyle = style
            lesson = l
            loading = false
        }
    }

    ForgeBackground {
        if (loading || lesson == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(color = ForgeBrand.Orange)
                    Text("Building tailored lesson...", style = MaterialTheme.typography.bodyMedium, color = forgeColors.textSecondary)
                }
            }
            return@ForgeBackground
        }

        val currentLesson = lesson!!

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(currentLesson.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = forgeColors.textPrimary)
                            Text("Phase: ${phase.name} · Format: $dominantStyle", style = MaterialTheme.typography.labelSmall, color = ForgeBrand.Orange)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp)
            ) {
                when (phase) {
                    LessonPhase.LEARN -> {
                        val concepts = currentLesson.keyConcepts
                        val concept = concepts.getOrNull(conceptIndex)

                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            LinearProgressIndicator(
                                progress = (conceptIndex + 1).toFloat() / concepts.size,
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                color = ForgeBrand.Orange,
                                trackColor = forgeColors.glassBorder
                            )

                            GlassCard(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("🎯 Objectives:", style = MaterialTheme.typography.labelLarge, color = ForgeBrand.Teal)
                                    currentLesson.objectives.forEach { obj ->
                                        Text("• $obj", style = MaterialTheme.typography.bodySmall, color = forgeColors.textSecondary)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Divider(color = forgeColors.divider)

                                    Text(concept?.title ?: "Concept", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = forgeColors.textPrimary)
                                    Text(concept?.explanation ?: "", style = MaterialTheme.typography.bodyMedium, color = forgeColors.textPrimary)

                                    if (concept?.examples?.isNotEmpty() == true) {
                                        Text("💡 Example:", style = MaterialTheme.typography.labelMedium, color = ForgeBrand.Gold)
                                        concept.examples.forEach { ex ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(ForgeBrand.Orange.copy(alpha = 0.08f))
                                                    .padding(12.dp)
                                            ) {
                                                Text(ex, style = MaterialTheme.typography.bodySmall, color = forgeColors.textPrimary)
                                            }
                                        }
                                    }
                                }
                            }

                            ForgeButton(
                                text = if (conceptIndex < concepts.lastIndex) "Next Concept →" else "Start Practice Challenge →",
                                onClick = {
                                    if (conceptIndex < concepts.lastIndex) {
                                        conceptIndex++
                                    } else {
                                        lessonReadTimeSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                                        phase = LessonPhase.CHALLENGE
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    LessonPhase.CHALLENGE -> {
                        val exercises = currentLesson.exercises
                        val exercise = exercises.getOrNull(exerciseIndex)

                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            LinearProgressIndicator(
                                progress = (exerciseIndex + 1).toFloat() / exercises.size,
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                color = ForgeBrand.Teal,
                                trackColor = forgeColors.glassBorder
                            )

                            GlassCard(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("⚔️ Challenge ${exerciseIndex + 1} of ${exercises.size}", style = MaterialTheme.typography.labelLarge, color = ForgeBrand.Teal)
                                        Text(exercise?.difficulty?.uppercase() ?: "MEDIUM", style = MaterialTheme.typography.labelSmall, color = ForgeBrand.Warning)
                                    }

                                    Text(exercise?.title ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = forgeColors.textPrimary)
                                    Text(exercise?.description ?: "", style = MaterialTheme.typography.bodyMedium, color = forgeColors.textPrimary)

                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Select the correct application:", style = MaterialTheme.typography.labelSmall, color = forgeColors.textMuted)

                                    listOf("Option A: Standard formula apply", "Option B: Novel transfer concept", "Option C: Edge case resolution").forEachIndexed { idx, opt ->
                                        val isSel = selectedAnswerIndex == idx
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSel) ForgeBrand.Orange.copy(alpha = 0.2f) else forgeColors.glassFill)
                                                .border(1.dp, if (isSel) ForgeBrand.Orange else forgeColors.glassBorder, RoundedCornerShape(12.dp))
                                                .clickable { selectedAnswerIndex = idx }
                                                .padding(14.dp)
                                        ) {
                                            Text(opt, color = if (isSel) ForgeBrand.Orange else forgeColors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }

                            ForgeButton(
                                text = if (exerciseIndex < exercises.lastIndex) "Next Challenge →" else "Proceed to Exit Check →",
                                onClick = {
                                    if (selectedAnswerIndex != null) scoreCount++
                                    selectedAnswerIndex = null
                                    if (exerciseIndex < exercises.lastIndex) {
                                        exerciseIndex++
                                    } else {
                                        phase = LessonPhase.EXIT_CHECK
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    LessonPhase.EXIT_CHECK -> {
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            GlassCard(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text("🚪 Exit Check", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ForgeBrand.Orange)
                                    Text(currentLesson.exitQuestion, style = MaterialTheme.typography.bodyLarge, color = forgeColors.textPrimary)

                                    OutlinedTextField(
                                        value = userExitResponse,
                                        onValueChange = { userExitResponse = it },
                                        placeholder = { Text("Explain in your own words...") },
                                        modifier = Modifier.fillMaxWidth().height(140.dp)
                                    )
                                }
                            }

                            ForgeButton(
                                text = "Complete Lesson 🎉",
                                onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            val db = AppDatabase.getInstance(ctx)
                                            val scorePercent = ((scoreCount.toFloat() / currentLesson.exercises.size.coerceAtLeast(1)) * 100).toInt()
                                            val totalDurationSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()

                                            ReflectionAgent.reflect(
                                                db = db,
                                                subject = subject,
                                                topic = topic,
                                                scorePercent = scorePercent,
                                                responseTimeMs = (totalDurationSec * 1000) / currentLesson.exercises.size.coerceAtLeast(1),
                                                isGraded = true,
                                                activityType = "lesson"
                                            )

                                            LearningStyleAgent.processSessionBehavior(
                                                db = db,
                                                lessonStyleUsed = dominantStyle,
                                                lessonReadTimeSec = lessonReadTimeSec,
                                                exerciseScorePercent = scorePercent,
                                                avgResponseTimeMs = (totalDurationSec * 1000) / currentLesson.exercises.size.coerceAtLeast(1),
                                                masteryDelta = 10
                                            )
                                        }
                                        phase = LessonPhase.COMPLETED
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    LessonPhase.COMPLETED -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🎉 Lesson Completed!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ForgeBrand.Success)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Your student model and learning style preference ($dominantStyle) have been updated.", style = MaterialTheme.typography.bodyMedium, color = forgeColors.textSecondary)
                            Spacer(modifier = Modifier.height(24.dp))
                            ForgeButton(text = "Return to Dashboard", onClick = onFinished, modifier = Modifier.width(200.dp))
                        }
                    }
                }
            }
        }
    }
}
