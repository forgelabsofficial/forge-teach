package com.aiteacher.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiteacher.ai.ModelRegistry
import com.aiteacher.data.PlanRepository
import com.aiteacher.ui.*
import com.aiteacher.work.ScheduleManager
import kotlinx.coroutines.launch

// ─── Shared field colours for dark theme ─────────────────────────────────────
// field colors via forgeFieldColors()

@Composable
fun OnboardingScreen(
    vm: OnboardingViewModel = viewModel(),
    onContinue: (Assessment) -> Unit = {}
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { vm.loadAssessmentSchema(ctx); vm.loadCatalogue(ctx) }
    var step by remember { mutableIntStateOf(-1) } // -1 = loading saved step
    val stepLabels = listOf("AI Setup", "School", "Subjects", "Test", "Plan")

    // Restore saved step on launch
    LaunchedEffect(Unit) {
        val savedStep = com.aiteacher.ui.DataStoreUtils.getOnboardingStep(ctx)
        step = savedStep.coerceIn(0, 4)
    }

    // Persist step changes — clear saved step only when onboarding truly completes
    fun onStepChange(newStep: Int) {
        step = newStep
        scope.launch {
            com.aiteacher.ui.DataStoreUtils.saveOnboardingStep(ctx, newStep)
        }
    }

    // Clear saved step when onboarding completes successfully
    fun onComplete(assessment: Assessment) {
        scope.launch {
            com.aiteacher.ui.DataStoreUtils.clearOnboardingStep(ctx)
        }
        onContinue(assessment)
    }

    ForgeBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            if (step >= 0) {
                StepHeader(currentStep = step, labels = stepLabels)
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        if (targetState > initialState)
                            (slideInHorizontally(tween(320)) { it } + fadeIn(tween(320))) togetherWith
                            (slideOutHorizontally(tween(320)) { -it } + fadeOut(tween(160)))
                        else
                            (slideInHorizontally(tween(320)) { -it } + fadeIn(tween(320))) togetherWith
                            (slideOutHorizontally(tween(320)) { it } + fadeOut(tween(160)))
                    },
                    label = "step",
                    modifier = Modifier.weight(1f)
                ) { s ->
                    when (s) {
                        0 -> ProviderSetupStep(vm, onNext = { onStepChange(1) })
                        1 -> CountryGradeStep(vm, onBack = { onStepChange(0) }, onNext = { onStepChange(2) })
                        2 -> StudentProfileStep(vm, onBack = { onStepChange(1) }, onNext = { onStepChange(3) }, ctx = ctx)
                        3 -> CapabilityTestStep(vm, onBack = { onStepChange(2) }, onNext = { onStepChange(4) })
                        4 -> PlanPreviewStep(vm, onBack = { onStepChange(3) }, onContinue = { assessment -> onComplete(assessment) }, ctx = ctx)
                    }
                }
            }
        }
    }
}

// ─── Step header with numbered pill indicators ────────────────────────────────

@Composable
private fun StepHeader(currentStep: Int, labels: List<String>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Pill indicators row
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { i, label ->
                val isActive = i == currentStep
                val isDone = i < currentStep
                StepPill(
                    number = i + 1,
                    isActive = isActive,
                    isDone = isDone
                )
                if (i < labels.lastIndex) {
                    // Connector line between pills
                    Box(
                        modifier = Modifier
                            .height(2.dp)
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(
                                if (isDone) ForgeBrand.Success else forgeColors.glassBorder
                            )
                    )
                }
            }
        }
        Text(
            text = "${labels[currentStep]}  ·  ${currentStep + 1} / ${labels.size}",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = TextUnit(0.8f, TextUnitType.Sp)
            ),
            color = forgeColors.textMuted
        )
    }
}

// ─── Reusable nav row ──────────────────────────────────────────────────────────

@Composable
private fun NavRow(
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextLabel: String = "Continue",
    nextEnabled: Boolean = true,
    backLabel: String = "Back"
) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GhostButton(
            text = backLabel,
            onClick = onBack,
            modifier = Modifier.weight(1f),
            height = 52.dp
        )
        ForgeButton(
            text = nextLabel,
            onClick = onNext,
            enabled = nextEnabled,
            modifier = Modifier.weight(2f),
            height = 52.dp
        )
    }
}

// ─── Step page scaffold ───────────────────────────────────────────────────────

@Composable
private fun StepPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        content = content
    )
}

@Composable
private fun PageTitle(emoji: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(emoji, style = MaterialTheme.typography.displaySmall)
        Text(title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Black),
            color = forgeColors.textPrimary)
        Text(subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = forgeColors.textSecondary)
    }
}

// ─── Step 0: AI Provider Setup ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderSetupStep(vm: OnboardingViewModel, onNext: () -> Unit) {
    val ctx = LocalContext.current
    val provider by vm.provider.collectAsState()
    val apiKey by vm.apiKey.collectAsState()
    val models by vm.models.collectAsState()
    val selectedModel by vm.selectedModel.collectAsState()
    val modelsLoading by vm.modelsLoading.collectAsState()
    val modelsError by vm.modelsError.collectAsState()
    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }

    StepPage {
        PageTitle("⚙️", "AI Setup",
            "A parent or teacher should complete this step. Choose an AI provider and paste your API key.")

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ExposedDropdownMenuBox(expanded = providerExpanded,
                    onExpandedChange = { providerExpanded = it }) {
                    OutlinedTextField(
                        value = ModelRegistry.providerDisplayNames[provider] ?: provider,
                        onValueChange = {}, readOnly = true,
                        label = { Text("AI Provider") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(providerExpanded)
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(14.dp),
                        colors = forgeFieldColors()
                    )
                    ExposedDropdownMenu(expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false }) {
                        ModelRegistry.providerIds.forEach { id ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        ModelRegistry.providerDisplayNames[id] ?: id,
                                        color = forgeColors.textPrimary
                                    )
                                },
                                onClick = {
                                    vm.setProvider(id)
                                    vm.clearModels()
                                    providerExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = apiKey, onValueChange = { vm.setApiKey(it) },
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = forgeFieldColors()
                )

                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ForgeButton(
                        text = if (modelsLoading) "Loading…" else "Load Models",
                        onClick = { vm.loadModels(ctx) },
                        enabled = apiKey.isNotBlank() && !modelsLoading,
                        modifier = Modifier.weight(1f),
                        height = 44.dp
                    )
                    if (models.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.CheckCircle, null,
                                tint = ForgeBrand.Success,
                                modifier = Modifier.size(18.dp))
                            Text("${models.size} models",
                                style = MaterialTheme.typography.bodySmall,
                                color = ForgeBrand.Success)
                        }
                    }
                }

                if (modelsError != null) {
                    Text(modelsError!!,
                        color = ForgeBrand.Error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (models.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Choose Model",
                        style = MaterialTheme.typography.titleSmall,
                        color = forgeColors.textSecondary)
                    ExposedDropdownMenuBox(expanded = modelExpanded,
                        onExpandedChange = { modelExpanded = it }) {
                        OutlinedTextField(
                            value = selectedModel ?: models.first().id,
                            onValueChange = {}, readOnly = true,
                            label = { Text("Model") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(modelExpanded)
                            },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(14.dp),
                            colors = forgeFieldColors()
                        )
                        ExposedDropdownMenu(expanded = modelExpanded,
                            onDismissRequest = { modelExpanded = false }) {
                            models.forEach { m ->
                                DropdownMenuItem(
                                    text = {
                                        Text(m.id,
                                            color = forgeColors.textPrimary,
                                            style = MaterialTheme.typography.bodyMedium)
                                    },
                                    onClick = {
                                        vm.selectModel(m.id)
                                        modelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        ForgeButton(
            text = "Continue",
            onClick = { vm.saveCredentials(ctx); onNext() },
            enabled = models.isNotEmpty() && selectedModel != null,
            modifier = Modifier.fillMaxWidth(),
            height = 52.dp
        )
        TextButton(onClick = { vm.saveCredentials(ctx); onNext() },
            modifier = Modifier.fillMaxWidth()) {
            Text("Skip — use built-in test questions",
                color = forgeColors.textMuted,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ─── Step 1: Country & Grade ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryGradeStep(
    vm: OnboardingViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val catalogue by vm.catalogue.collectAsState()
    val selectedCountry by vm.selectedCountry.collectAsState()
    val selectedSystem by vm.selectedSystem.collectAsState()
    val selectedGrade by vm.selectedGrade.collectAsState()
    var countryExpanded by remember { mutableStateOf(false) }
    var systemExpanded by remember { mutableStateOf(false) }
    var gradeExpanded by remember { mutableStateOf(false) }

    StepPage {
        PageTitle("🌍", "Where do you go to school?",
            "We'll match your lessons to your exact curriculum and upcoming exams.")

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (catalogue == null) {
                    Row(modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = ForgeBrand.Orange
                        )
                        Text("Loading countries…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = forgeColors.textSecondary)
                    }
                } else {
                    // Country
                    ExposedDropdownMenuBox(expanded = countryExpanded,
                        onExpandedChange = { countryExpanded = it }) {
                        OutlinedTextField(
                            value = if (selectedCountry != null) {
                                "${selectedCountry!!.flag}  ${selectedCountry!!.name}"
                            } else "Select country",
                            onValueChange = {}, readOnly = true,
                            label = { Text("Country") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(countryExpanded)
                            },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(14.dp),
                            colors = forgeFieldColors()
                        )
                        ExposedDropdownMenu(expanded = countryExpanded,
                            onDismissRequest = { countryExpanded = false }) {
                            catalogue!!.countries.forEach { c ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${c.flag}  ${c.name}",
                                            color = forgeColors.textPrimary)
                                    },
                                    onClick = {
                                        vm.selectCountry(c)
                                        countryExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // System (only if multiple)
                    val systems = selectedCountry?.systems ?: emptyList()
                    if (systems.size > 1) {
                        ExposedDropdownMenuBox(expanded = systemExpanded,
                            onExpandedChange = { systemExpanded = it }) {
                            OutlinedTextField(
                                value = selectedSystem?.name ?: "Select curriculum",
                                onValueChange = {}, readOnly = true,
                                label = { Text("Curriculum / Board") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(systemExpanded)
                                },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                shape = RoundedCornerShape(14.dp),
                                colors = forgeFieldColors()
                            )
                            ExposedDropdownMenu(expanded = systemExpanded,
                                onDismissRequest = { systemExpanded = false }) {
                                systems.forEach { s ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(s.name,
                                                color = forgeColors.textPrimary)
                                        },
                                        onClick = {
                                            vm.selectSystem(s)
                                            systemExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Grade
                    val grades = selectedSystem?.levels ?: emptyList()
                    if (grades.isNotEmpty()) {
                        ExposedDropdownMenuBox(expanded = gradeExpanded,
                            onExpandedChange = { gradeExpanded = it }) {
                            OutlinedTextField(
                                value = selectedGrade?.label
                                    ?: "Select your class / year",
                                onValueChange = {}, readOnly = true,
                                label = { Text("Class / Year / Grade") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(gradeExpanded)
                                },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                shape = RoundedCornerShape(14.dp),
                                colors = forgeFieldColors()
                            )
                            ExposedDropdownMenu(expanded = gradeExpanded,
                                onDismissRequest = { gradeExpanded = false }) {
                                grades.forEach { g ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(g.label,
                                                    color = forgeColors.textPrimary,
                                                    style = MaterialTheme.typography.bodyMedium)
                                                if (g.ageRange.isNotBlank())
                                                    Text("Age ${g.ageRange}",
                                                        color = forgeColors.textMuted,
                                                        style = MaterialTheme.typography.bodySmall)
                                            }
                                        },
                                        onClick = {
                                            vm.selectGrade(g)
                                            gradeExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Exams badge
        val exams = selectedSystem?.keyExams ?: emptyList()
        if (exams.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ForgeBrand.Orange.copy(0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📋", style = MaterialTheme.typography.titleMedium)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OrangeAccentHeader(title = "Key Exams")
                        exams.forEach { exam ->
                            Text("· $exam",
                                style = MaterialTheme.typography.bodyMedium,
                                color = forgeColors.textSecondary)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        NavRow(onBack = onBack, onNext = onNext, nextLabel = "Continue",
            nextEnabled = selectedCountry != null && selectedGrade != null)
    }
}

// ─── Step 2: Student Profile ──────────────────────────────────────────────────

@Composable
private fun StudentProfileStep(
    vm: OnboardingViewModel, onBack: () -> Unit, onNext: () -> Unit,
    ctx: android.content.Context
) {
    val schema by vm.schema.collectAsState()
    val answers by vm.answers.collectAsState()
    val name by vm.name.collectAsState()
    val subjectsCatalogue by vm.subjectsCatalogue.collectAsState()
    val mutableAnswers = remember(schema) { answers.toMutableMap() }
    var subjectSearch by remember { mutableStateOf("") }
    @Suppress("UNCHECKED_CAST")
    val selectedIds = remember { mutableStateListOf<String>().also {
        it.addAll((answers["q_goals"] as? List<String>) ?: emptyList())
    }}

    StepPage {
        PageTitle("👋", "Tell us about yourself",
            "This helps us personalise every lesson just for you.")

        // Name field
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Your Name",
                    style = MaterialTheme.typography.labelMedium,
                    color = forgeColors.textMuted)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = name, onValueChange = { vm.setName(it) },
                    placeholder = { Text("e.g. Amara",
                        color = forgeColors.textMuted) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = forgeFieldColors()
                )
            }
        }

        // Subject picker
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    OrangeAccentHeader(title = "Subjects")
                    if (selectedIds.isNotEmpty()) {
                        Surface(shape = RoundedCornerShape(20.dp),
                            color = ForgeBrand.Orange.copy(alpha = 0.15f)) {
                            Text("${selectedIds.size} selected",
                                modifier = Modifier.padding(
                                    horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = ForgeBrand.OrangeLight)
                        }
                    }
                }

                OutlinedTextField(
                    value = subjectSearch,
                    onValueChange = { subjectSearch = it },
                    placeholder = { Text("Search subjects…",
                        color = forgeColors.textMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, null,
                            tint = forgeColors.textMuted,
                            modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = forgeFieldColors()
                )

                if (subjectsCatalogue != null) {
                    val q = subjectSearch.trim().lowercase()
                    subjectsCatalogue!!.categories.forEach { cat ->
                        val filtered = if (q.isBlank()) cat.subjects
                        else cat.subjects.filter {
                            it.label.lowercase().contains(q) ||
                            it.aliases.any { a -> a.lowercase().contains(q) }
                        }
                        if (filtered.isNotEmpty()) {
                            Text(cat.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = TextUnit(
                                        1f, TextUnitType.Sp)),
                                color = forgeColors.textMuted,
                                modifier = Modifier.padding(top = 4.dp))
                            filtered.forEach { subj ->
                                val sel = selectedIds.contains(subj.id)
                                val interactionSource = remember {
                                    MutableInteractionSource()
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (sel) ForgeBrand.Orange.copy(0.12f)
                                            else Color.Transparent
                                        )
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null
                                        ) {
                                            if (sel) {
                                                selectedIds.remove(subj.id)
                                            } else {
                                                selectedIds.add(subj.id)
                                            }
                                            mutableAnswers["q_goals"] =
                                                selectedIds.toList()
                                        }
                                        .padding(
                                            horizontal = 12.dp, vertical = 10.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(22.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(
                                                if (sel) ForgeBrand.Orange
                                                else forgeColors.glassFill
                                            )
                                            .border(1.5.dp,
                                                if (sel) ForgeBrand.Orange
                                                else forgeColors.glassBorder,
                                                RoundedCornerShape(7.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (sel) Icon(Icons.Default.Check, null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(subj.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (sel) forgeColors.textPrimary
                                            else forgeColors.textSecondary)
                                        if (subj.aliases.isNotEmpty())
                                            Text(
                                                subj.aliases.take(2)
                                                    .joinToString(" · "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = forgeColors.textMuted)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp)
                            .align(Alignment.CenterHorizontally),
                        strokeWidth = 2.dp, color = ForgeBrand.Orange)
                }
            }
        }

        // Remaining profile questions (skip q_goals and q_name)
        if (schema != null) {
            val filtered = schema!!.copy(questions = schema!!.questions.filter {
                it.id != "q_goals" && it.id != "q_name"
            })
            if (filtered.questions.isNotEmpty()) {
                DynamicForm(
                    schema = filtered, answers = mutableAnswers)
            }
        }

        Spacer(Modifier.weight(1f))
        NavRow(
            onBack = onBack,
            onNext = {
                mutableAnswers["q_goals"] = selectedIds.toList()
                mutableAnswers.forEach { (k, v) -> vm.setAnswer(k, v) }
                vm.setName(name)
                vm.generateCapabilityTest(ctx)
                onNext()
            },
            nextLabel = "Take the Test →",
            nextEnabled = name.isNotBlank()
        )
    }
}

// ─── Step 3: Capability Test ──────────────────────────────────────────────────

@Composable
private fun CapabilityTestStep(
    vm: OnboardingViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val test by vm.capabilityTest.collectAsState()
    val generating by vm.testGenerating.collectAsState()
    val testError by vm.testError.collectAsState()
    val testAnswers by vm.testAnswers.collectAsState()
    val testResult by vm.testResult.collectAsState()
    var qIndex by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()
        .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {

        when {
            generating -> {
                Column(modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(
                        color = ForgeBrand.Orange,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(24.dp))
                    Text("Building your personalised test…",
                        style = MaterialTheme.typography.titleMedium,
                        color = forgeColors.textPrimary,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("🔍 Searching curriculum · Generating questions",
                        style = MaterialTheme.typography.bodySmall,
                        color = forgeColors.textMuted,
                        textAlign = TextAlign.Center)
                }
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = onNext) {
                        Text("Skip test",
                            color = forgeColors.textMuted,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            testResult != null -> {
                TestResultsView(
                    result = testResult!!,
                    modifier = Modifier.weight(1f),
                    onNext = onNext)
            }

            test != null -> {
                val questions = test!!.questions
                val total = questions.size
                val currentQ = questions[qIndex]
                val answered = testAnswers.containsKey(currentQ.id)
                val isLast = qIndex == total - 1

                // Progress with orange track
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Question ${qIndex + 1} of $total",
                            style = MaterialTheme.typography.labelMedium,
                            color = forgeColors.textMuted)
                        Text(currentQ.subject.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold),
                            color = ForgeBrand.OrangeLight)
                    }
                    LinearProgressIndicator(
                        progress = (qIndex + 1).toFloat() / total,
                        modifier = Modifier.fillMaxWidth()
                            .height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = ForgeBrand.Orange,
                        trackColor = forgeColors.glassBorder
                    )
                }

                // Question card with slide animation
                AnimatedContent(
                    targetState = qIndex,
                    transitionSpec = {
                        if (targetState > initialState)
                            (slideInHorizontally(tween(260)) { it } +
                             fadeIn(tween(260))) togetherWith
                            (slideOutHorizontally(tween(260)) { -it } +
                             fadeOut(tween(130)))
                        else
                            (slideInHorizontally(tween(260)) { -it } +
                             fadeIn(tween(260))) togetherWith
                            (slideOutHorizontally(tween(260)) { it } +
                             fadeOut(tween(130)))
                    },
                    label = "q",
                    modifier = Modifier.weight(1f)
                ) { idx ->
                    val q = questions[idx]
                    QuestionCard(
                        index = idx, question = q,
                        selectedIndex = testAnswers[q.id],
                        onSelect = { vm.answerTestQuestion(q.id, it) }
                    )
                }

                // Hint
                if (!answered) {
                    Text("Choose an answer to continue",
                        style = MaterialTheme.typography.bodySmall,
                        color = forgeColors.textMuted,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center)
                }

                // Nav
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GhostButton(
                        text = if (qIndex > 0) "← Prev" else "← Back",
                        onClick = {
                            if (qIndex > 0) qIndex-- else onBack()
                        },
                        modifier = Modifier.weight(1f),
                        height = 52.dp
                    )
                    if (!isLast) {
                        ForgeButton(
                            text = "Next →",
                            onClick = { qIndex++ },
                            enabled = answered,
                            modifier = Modifier.weight(2f),
                            height = 52.dp
                        )
                    } else {
                        val allAnswered = questions.all {
                            testAnswers.containsKey(it.id)
                        }
                        ForgeButton(
                            text = "Submit ✓",
                            onClick = { vm.submitTest() },
                            enabled = allAnswered,
                            modifier = Modifier.weight(2f),
                            height = 52.dp,
                            shape = RoundedCornerShape(28.dp)
                        )
                    }
                }

                TextButton(onClick = onNext,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Skip test",
                        color = forgeColors.textMuted,
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            else -> {
                Spacer(Modifier.weight(1f))
                if (testError != null)
                    Text(testError!!, color = ForgeBrand.Error,
                        style = MaterialTheme.typography.bodySmall)
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GhostButton(text = "← Back", onClick = onBack,
                        modifier = Modifier.weight(1f), height = 52.dp)
                    TextButton(onClick = onNext,
                        modifier = Modifier.weight(1f).height(52.dp)) {
                        Text("Skip", color = forgeColors.textMuted)
                    }
                }
            }
        }
    }
}

// ─── Question card ────────────────────────────────────────────────────────────

@Composable
private fun QuestionCard(
    index: Int, question: CapabilityQuestion,
    selectedIndex: Int?, onSelect: (Int) -> Unit
) {
    val optionLetters = listOf("A", "B", "C", "D")

    Column(modifier = Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top) {
                    // Numbered badge
                    Box(modifier = Modifier.size(32.dp).clip(CircleShape)
                        .background(ForgeBrand.Orange.copy(0.2f)),
                        contentAlignment = Alignment.Center) {
                        Text("${index + 1}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold),
                            color = ForgeBrand.OrangeLight)
                    }
                    Text(question.questionText,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            color = forgeColors.textPrimary),
                        modifier = Modifier.weight(1f))
                }
            }
        }

        question.options.forEachIndexed { i, opt ->
            val sel = selectedIndex == i
            val interactionSource = remember { MutableInteractionSource() }
            val optionBgMod = if (sel) Modifier.background(
                Brush.horizontalGradient(listOf(
                    ForgeBrand.Orange.copy(0.25f),
                    ForgeBrand.Orange.copy(0.15f)))
            ) else Modifier
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .then(optionBgMod)
                    .border(1.5.dp,
                        if (sel) ForgeBrand.Orange
                        else forgeColors.glassBorder,
                        RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onSelect(i) }
                    )
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(modifier = Modifier.size(34.dp).clip(CircleShape)
                        .background(
                            if (sel) ForgeBrand.Orange
                            else forgeColors.glassFill)
                        .border(1.5.dp,
                            if (sel) ForgeBrand.Orange
                            else forgeColors.glassBorder,
                            CircleShape),
                        contentAlignment = Alignment.Center) {
                        if (sel) {
                            Icon(Icons.Default.Check, null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp))
                        } else {
                            Text(optionLetters[i],
                                style = MaterialTheme.typography.labelMedium,
                                color = forgeColors.textMuted,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(opt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (sel) forgeColors.textPrimary
                        else forgeColors.textSecondary,
                        modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ─── Test results ─────────────────────────────────────────────────────────────

@Composable
private fun TestResultsView(
    result: CapabilityResult,
    modifier: Modifier = Modifier,
    onNext: () -> Unit
) {
    val pct = if (result.maxScore > 0)
        (result.totalScore * 100) / result.maxScore else 0
    val (emoji, headline, sub) = when {
        pct >= 80 -> Triple("🌟", "Outstanding!",
            "You really know your stuff.")
        pct >= 60 -> Triple("👍", "Good work!",
            "A bit of practice and you'll ace it.")
        pct >= 40 -> Triple("📚", "Nice start!",
            "We'll help you fill the gaps.")
        else -> Triple("🚀", "Ready to learn!",
            "That's exactly why we're here.")
    }

    Column(modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // Score hero with orange gradient
        Box(modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(
                ForgeBrand.Orange.copy(0.25f),
                ForgeBrand.Orange.copy(0.10f))))) {
            Row(modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(emoji,
                        style = MaterialTheme.typography.displaySmall)
                    Text(headline,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black),
                        color = forgeColors.textPrimary)
                    Text(sub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = forgeColors.textSecondary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("$pct%",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Black),
                        color = forgeColors.textPrimary)
                    Text("${result.totalScore}/${result.maxScore}",
                        style = MaterialTheme.typography.bodySmall,
                        color = forgeColors.textMuted)
                }
            }
        }

        // Per-subject bars
        if (result.subjectScores.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OrangeAccentHeader(title = "By Subject")
                    result.subjectScores.forEach { (subject, score) ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.SpaceBetween) {
                                Text(
                                    subject.replaceFirstChar {
                                        it.uppercase()
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = forgeColors.textPrimary)
                                Text("$score%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when {
                                        score >= 70 -> ForgeBrand.Success
                                        score >= 40 -> ForgeBrand.Warning
                                        else -> ForgeBrand.Error
                                    })
                            }
                            LinearProgressIndicator(
                                progress = score / 100f,
                                modifier = Modifier.fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = when {
                                    score >= 70 -> ForgeBrand.Success
                                    score >= 40 -> ForgeBrand.Warning
                                    else -> ForgeBrand.Error
                                },
                                trackColor = forgeColors.glassBorder
                            )
                        }
                    }
                }
            }
        }

        ForgeButton(
            text = "See My Learning Plan →",
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            height = 52.dp
        )
    }
}

// ─── Step 4: Plan Preview ─────────────────────────────────────────────────────

@Composable
private fun PlanPreviewStep(
    vm: OnboardingViewModel, onBack: () -> Unit,
    onContinue: (Assessment) -> Unit, ctx: android.content.Context
) {
    val scope = rememberCoroutineScope()
    val plan = remember { vm.generatePreviewPlan() }
    val testResult by vm.testResult.collectAsState()

    Column(modifier = Modifier.fillMaxSize()
        .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {

        PageTitle("📅", "Your Learning Plan",
            "Here are your upcoming study sessions. Accept to save them.")

        if (plan != null && plan.sessions.isNotEmpty()) {
            LazyColumn(modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(plan.sessions) { session ->
                    GlassCard(modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)) {
                        Row(modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(ForgeBrand.Orange.copy(0.15f)),
                                contentAlignment = Alignment.Center) {
                                Text("📖",
                                    style = MaterialTheme.typography.titleLarge)
                            }
                            Column(modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(session.topic,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.SemiBold),
                                    color = forgeColors.textPrimary)
                                Row(horizontalArrangement =
                                    Arrangement.spacedBy(12.dp)) {
                                    Text("📆 ${session.date}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = forgeColors.textMuted)
                                    Text("⏱ ${session.duration}m",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = forgeColors.textMuted)
                                }
                                if (!session.isoDateTime.isNullOrBlank()) {
                                    val time = session.isoDateTime
                                        .take(16).replace("T", " at ")
                                    Text("🕐 $time",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = forgeColors.textMuted)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📆",
                        style = MaterialTheme.typography.displaySmall)
                    Text("No timetable yet",
                        style = MaterialTheme.typography.titleSmall,
                        color = forgeColors.textSecondary)
                    Text("Add your availability in Settings after sign-up.",
                        style = MaterialTheme.typography.bodySmall,
                        color = forgeColors.textMuted,
                        textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.weight(1f))
        }

        // Score badge
        if (testResult != null) {
            val pct = if (testResult!!.maxScore > 0)
                (testResult!!.totalScore * 100) / testResult!!.maxScore
            else 0
            GlassCard(modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)) {
                Row(modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("🌟",
                        style = MaterialTheme.typography.titleLarge)
                    Text("Test score: $pct% — plan calibrated to your level",
                        style = MaterialTheme.typography.bodySmall,
                        color = forgeColors.textSecondary,
                        modifier = Modifier.weight(1f))
                }
            }
        }

        // Actions
        ForgeButton(
            text = "Let's Go! 🚀",
            onClick = {
                scope.launch {
                    if (plan != null) {
                        val repo = PlanRepository(ctx.applicationContext)
                        val tz = vm.answers.value["q_timezone"] as? String
                        val sid = repo.saveStudentProfile(
                            vm.name.value.ifBlank { "Student" }, tz)
                        repo.savePlan(plan, sid)
                        ScheduleManager.schedulePlanNotifications(
                            ctx.applicationContext, plan)
                    }
                    onContinue(vm.buildAssessment())
                }
            },
            modifier = Modifier.fillMaxWidth(),
            height = 52.dp
        )

        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GhostButton(text = "← Back", onClick = onBack,
                modifier = Modifier.weight(1f), height = 48.dp)
            TextButton(onClick = { onContinue(vm.buildAssessment()) },
                modifier = Modifier.weight(1f).height(48.dp)) {
                Text("Skip for now",
                    color = forgeColors.textMuted,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
