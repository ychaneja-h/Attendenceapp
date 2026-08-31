package com.attendance.app.teacher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.attendance.app.core.ui.navigation.Screen
import kotlinx.coroutines.launch
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import com.attendance.app.core.network.Constants
import org.koin.compose.koinInject

@Serializable
data class TeacherRequestDto(
    val id: String,
    val studentId: String,
    val requestType: String,
    val requestedData: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherRequestsScreen(
    navController: NavController,
    httpClient: HttpClient = koinInject()
) {
    var requests by remember { mutableStateOf<List<TeacherRequestDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val response: HttpResponse = httpClient.get("${Constants.BASE_URL}/requests/teacher")
            if (response.status.isSuccess()) {
                requests = response.body()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Teacher Requests") }) },
        bottomBar = { TeacherBottomBar(navController = navController, selectedIndex = 3) }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (requests.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No pending requests")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(requests) { request ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val titleText = if (request.requestType == "FACE_REGISTRATION") {
                                "Face Registration Request"
                            } else {
                                "Device Change Request"
                            }
                            Text("$titleText from ${request.studentId.substring(0, 8)}...", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Type: ${request.requestType}", style = MaterialTheme.typography.bodyMedium)
                            Text("Data: ${request.requestedData}", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { /* Reject */ }) {
                                    Text("Reject", color = MaterialTheme.colorScheme.error)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = {
                                    scope.launch {
                                        try {
                                            val res = httpClient.post("${Constants.BASE_URL}/requests/${request.id}/approve")
                                            if (res.status.isSuccess()) {
                                                requests = requests.filter { it.id != request.id }
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }) {
                                    Text("Approve")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
