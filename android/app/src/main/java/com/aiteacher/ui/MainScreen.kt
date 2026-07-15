package com.aiteacher.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aiteacher.onboarding.OnboardingScreen
import com.aiteacher.onboarding.OnboardingViewModel
import com.aiteacher.data.PlanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MainScreen() {
    AiTeacherTheme {
        val navController = rememberNavController()
        val scope = rememberCoroutineScope()
        val ctx = LocalContext.current
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        val bottomNavRoutes = setOf("dashboard", "plan", "profile", "settings")
        var startDest by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            val repo = PlanRepository(ctx.applicationContext)
            val existingPlan = withContext(Dispatchers.IO) { repo.loadLatestPlan() }
            val savedStep = DataStoreUtils.getOnboardingStep(ctx)
            startDest = when {
                existingPlan != null -> "dashboard"
                savedStep > 0 -> "onboarding"
                else -> "welcome"
            }
        }

        ForgeBackgroundWithBlob(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    if (startDest != null) {
                        NavHost(navController = navController, startDestination = startDest!!) {
                            composable("welcome") {
                                WelcomeScreen(onGetStarted = { navController.navigate("onboarding") })
                            }
                            composable("onboarding") {
                                val vm: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                                OnboardingScreen(vm = vm, onContinue = { assessment ->
                                    scope.launch { DataStoreUtils.saveAssessment(ctx as Context, assessment) }
                                    navController.navigate("dashboard") {
                                        popUpTo("welcome") { inclusive = true }
                                    }
                                })
                            }
                            composable("plan") {
                                PlanScreen(onAccepted = {
                                    navController.navigate("dashboard") {
                                        popUpTo("dashboard") { inclusive = true }
                                    }
                                })
                            }
                            composable("dashboard") {
                                DashboardScreen(onNavigateToPlan = { navController.navigate("plan") })
                            }
                            composable("settings") {
                                SettingsScreen(
                                    onNavigateToProfile = { navController.navigate("profile") },
                                    onNavigateToAiProvider = { navController.navigate("ai-provider-settings") },
                                    onNavigateToNotificationSettings = {
                                        ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                        })
                                    }
                                )
                            }
                            composable("profile") {
                                ProfileScreen(
                                    onClose = { navController.navigateUp() },
                                    onRedoOnboarding = {
                                        navController.navigate("onboarding") {
                                            popUpTo("dashboard") { saveState = false }
                                            launchSingleTop = true
                                        }
                                    },
                                    onRegeneratePlan = {
                                        navController.navigate("plan") {
                                            popUpTo("dashboard") { saveState = false }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                            composable("ai-provider-settings") {
                                AiProviderSettingsScreen(onClose = { navController.navigateUp() })
                            }
                        }
                    }
                }

                if (currentRoute in bottomNavRoutes) {
                    ForgeNavBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            if (currentRoute != route) navController.navigate(route) {
                                popUpTo("dashboard") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    }
}

// ─── Bottom navigation ─────────────────────────────────────────────────────────

private data class NavItem(val route: String, val icon: ImageVector, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForgeNavBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    val items = listOf(
        NavItem("dashboard", Icons.Filled.Home, "Home"),
        NavItem("plan",      Icons.Filled.List,   "Plan"),
        NavItem("profile",   Icons.Filled.Person, "Profile"),
        NavItem("settings",  Icons.Filled.Settings,"Settings")
    )
    val fc = forgeColors
    Box(modifier = Modifier.fillMaxWidth().background(fc.bgDeep).navigationBarsPadding()) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(fc.glassBorder))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                val scale by animateFloatAsState(targetValue = if (selected) 1.05f else 1f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f), label = "navScale")
                Column(modifier = Modifier.weight(1f).scale(scale).padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) ForgeBrand.Orange.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { onNavigate(item.route) }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(item.icon, contentDescription = item.label,
                        tint = if (selected) ForgeBrand.Orange else fc.textMuted, modifier = Modifier.size(22.dp))
                    Text(item.label, style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                        color = if (selected) ForgeBrand.Orange else fc.textMuted)
                }
            }
        }
    }
}

// ─── Welcome screen ────────────────────────────────────────────────────────────

@Composable
private fun WelcomeScreen(onGetStarted: () -> Unit) {
    val fc = forgeColors
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Spacer(Modifier.weight(1f))
        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(120.dp).clip(CircleShape).background(ForgeBrand.Orange.copy(alpha = 0.08f)))
            Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(ForgeBrand.Orange.copy(alpha = 0.12f)))
            Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(ForgeBrand.Orange.copy(0.3f), ForgeBrand.Orange.copy(0.15f)))),
                contentAlignment = Alignment.Center) { Text("🎓", style = MaterialTheme.typography.displayMedium) }
        }
        Spacer(Modifier.height(28.dp))
        Text("Forge Teach", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black), color = fc.textPrimary)
        Spacer(Modifier.height(10.dp))
        Text("Your AI-powered study companion — calibrated to your curriculum, your grade, your pace.",
            style = MaterialTheme.typography.bodyLarge, color = fc.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(48.dp))
        listOf("🌍" to "Supports 50+ countries & curricula",
            "🧠" to "AI capability test tailors your plan",
            "📅" to "Auto-schedules sessions around you"
        ).forEachIndexed { idx, (emoji, text) ->
            val accentColor = when (idx) { 0 -> ForgeBrand.Teal; 1 -> ForgeBrand.Pink; else -> ForgeBrand.Gold }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Text(emoji, style = MaterialTheme.typography.titleLarge) }
                Text(text, style = MaterialTheme.typography.bodyMedium, color = fc.textSecondary, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.weight(1f))
        ForgeButton(text = "Get Started", onClick = onGetStarted, modifier = Modifier.fillMaxWidth(), height = 56.dp)
        Spacer(Modifier.height(16.dp))
        Text("Free to use · No account required", style = MaterialTheme.typography.bodySmall, color = forgeColors.textMuted)
        Spacer(Modifier.height(32.dp))
    }
}

// ─── Settings screen ───────────────────────────────────────────────────────────

@Composable
private fun SettingsScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToAiProvider: () -> Unit,
    onNavigateToNotificationSettings: () -> Unit
) {
    ForgeBackground {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = forgeColors.textPrimary)
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(4.dp)) {
                    SettingsRow(emoji = "👤", label = "Student Profile", onClick = onNavigateToProfile)
                    ForgeDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(emoji = "🤖", label = "AI Provider", onClick = onNavigateToAiProvider)
                    ForgeDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(emoji = "🔔", label = "Notifications", onClick = onNavigateToNotificationSettings)
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(emoji: String, label: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }
        .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = forgeColors.textPrimary, modifier = Modifier.weight(1f))
        Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(forgeColors.glassFill),
            contentAlignment = Alignment.Center) { Text("›", style = MaterialTheme.typography.titleMedium, color = forgeColors.textMuted) }
    }
}

// ─── AI Provider re-configuration screen ───────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiProviderSettingsScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val vm: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    LaunchedEffect(Unit) { ctx.let { vm.loadModels(it) } }
    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    val provider by vm.provider.collectAsState()
    val apiKey by vm.apiKey.collectAsState()
    val models by vm.models.collectAsState()
    val selectedModel by vm.selectedModel.collectAsState()
    val modelsLoading by vm.modelsLoading.collectAsState()
    val modelsError by vm.modelsError.collectAsState()

    ForgeBackground {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("AI Provider", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                    color = forgeColors.textPrimary)
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)
                    .clip(RoundedCornerShape(14.dp)).background(forgeColors.glassFill)) {
                    Text("✕", style = MaterialTheme.typography.titleMedium, color = forgeColors.textSecondary)
                }
            }
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Provider & API Key", style = MaterialTheme.typography.titleSmall, color = forgeColors.textSecondary)
                    ExposedDropdownMenuBox(expanded = providerExpanded, onExpandedChange = { providerExpanded = it }) {
                        OutlinedTextField(value = com.aiteacher.ai.ModelRegistry.providerDisplayNames[provider] ?: provider,
                            onValueChange = {}, readOnly = true, label = { Text("AI Provider") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(14.dp), colors = forgeFieldColors())
                        ExposedDropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                            com.aiteacher.ai.ModelRegistry.providerIds.forEach { id ->
                                DropdownMenuItem(text = { Text(com.aiteacher.ai.ModelRegistry.providerDisplayNames[id] ?: id, color = forgeColors.textPrimary) },
                                    onClick = { vm.setProvider(id); vm.clearModels(); providerExpanded = false })
                            }
                        }
                    }
                    OutlinedTextField(value = apiKey, onValueChange = { vm.setApiKey(it) },
                        label = { Text("API Key") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp), colors = forgeFieldColors())
                    ForgeButton(text = if (modelsLoading) "Loading…" else "Save & Load Models",
                        onClick = { vm.saveCredentials(ctx); vm.loadModels(ctx) },
                        enabled = apiKey.isNotBlank() && !modelsLoading, modifier = Modifier.fillMaxWidth(), height = 48.dp)
                    if (modelsError != null) Text(modelsError!!, color = ForgeBrand.Error, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (models.isNotEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Choose Model", style = MaterialTheme.typography.titleSmall, color = forgeColors.textSecondary)
                        ExposedDropdownMenuBox(expanded = modelExpanded, onExpandedChange = { modelExpanded = it }) {
                            OutlinedTextField(value = selectedModel ?: models.first().id, onValueChange = {}, readOnly = true,
                                label = { Text("Model") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modelExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(14.dp), colors = forgeFieldColors())
                            ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                                models.forEach { m -> DropdownMenuItem(text = { Text(m.id, color = forgeColors.textPrimary, style = MaterialTheme.typography.bodyMedium) },
                                    onClick = { vm.selectModel(m.id); vm.saveCredentials(ctx); modelExpanded = false }) }
                            }
                        }
                    }
                }
            }
        }
    }
}
