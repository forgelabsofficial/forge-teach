package com.aiteacher.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
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
                Column(modifier = Modifier.fillMaxSize().padding(0.dp)) {
                    // Content area
                    Column(modifier = Modifier.fillMaxSize().weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Dashboard (placeholder)")
                    }
                    // Bottom icon navigation
                    NavigationBar {
                        NavigationBarItem(selected = true, onClick = { navController.navigate("welcome") }) {
                            Icon(imageVector = Icons.Filled.Home, contentDescription = "Home")
                        }
                        NavigationBarItem(selected = false, onClick = { navController.navigate("plan") }) {
                            Icon(imageVector = Icons.Filled.List, contentDescription = "Plan")
                        }
                        NavigationBarItem(selected = false, onClick = { navController.navigate("profile") }) {
                            Icon(imageVector = Icons.Filled.Person, contentDescription = "Profile")
                        }
                    }
                }
            }
            composable("settings") {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Settings")
                    Button(onClick = { navController.navigate("profile") }) { Text("Profile") }
                    Button(onClick = { /* future: provider/settings */ }) { Text("Provider settings") }
                }
            }
            composable("profile") {
                // show profile screen (edit student profile and view plans)
                ProfileScreen(onClose = { navController.navigateUp() })
            }
        }
    }
}
