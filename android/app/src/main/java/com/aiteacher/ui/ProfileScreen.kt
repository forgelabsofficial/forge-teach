package com.aiteacher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.aiteacher.data.PlanRepository
import com.aiteacher.onboarding.Plan
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(repo: PlanRepository? = null, onClose: () -> Unit = {}) {
    val ctx = LocalContext.current
    val repository = repo ?: remember { PlanRepository(ctx.applicationContext) }
    val scope = rememberCoroutineScope()

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

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val scaffoldState = remember { snackbarHostState }
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Student Profile")
        OutlinedTextField(value = studentName, onValueChange = { studentName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = timezone, onValueChange = { timezone = it }, label = { Text("Timezone") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch {
                    val s = repository.getLatestStudentProfile()
                    if (s != null) {
                        repository.updateStudentProfile(s.id, studentName, timezone.ifBlank { null })
                        // reload plans
                        val s2 = repository.getLatestStudentProfile()
                        if (s2 != null) plans = repository.getPlansForStudent(s2.id)
                        snackbarHostState.showSnackbar("Profile saved")
                    }
                }
            }) { Text("Save") }
            Button(onClick = onClose) { Text("Close") }
        }

        Text("Plans")
        LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp)) {
            items(plans) { p ->
                Column(modifier = Modifier.fillMaxWidth().padding(6.dp)) {
                    Text("Weeks: ${p.weeks}")
                    p.sessions.forEach { s -> Text("- ${s.date}: ${s.topic} (${s.duration}m)") }
                }
            }
        }
    }
}
