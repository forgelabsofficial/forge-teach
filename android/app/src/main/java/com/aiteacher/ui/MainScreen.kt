package com.aiteacher.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aiteacher.onboarding.OnboardingScreen
import com.aiteacher.onboarding.OnboardingViewModel
import kotlinx.coroutines.launch

@Composable
fun MainScreen() {
    AiTeacherTheme {
        val navController = rememberNavController()
        val scope = rememberCoroutineScope()
        val ctx = LocalContext.current

        NavHost(navController = navController, startDestination = "welcome") {
            composable("welcome") {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Welcome to AI Teacher")
                    Button(onClick = { navController.navigate("onboarding") }) { Text("Get started") }
                }
            }
            composable("onboarding") {
                val vm: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                OnboardingScreen(vm = vm, onContinue = { assessment ->
                    // save assessment to DataStore and navigate to plan
                    scope.launch { DataStoreUtils.saveAssessment(ctx as Context, assessment) }
                    navController.navigate("plan")
                })
            }
            composable("plan") {
                PlanScreen(onAccepted = { navController.navigate("dashboard") })
            }
            composable("dashboard") {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Dashboard (placeholder)")
                }
            }
        }
    }
}
