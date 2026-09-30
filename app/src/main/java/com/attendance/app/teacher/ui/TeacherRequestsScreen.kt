package com.attendance.app.teacher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import com.attendance.app.core.network.Constants
import org.koin.compose.koinInject

@Serializable
data class TeacherRequestDto(val id: String, val studentId: String, val studentName: String? = null,
    val requestType: String, val requestedData: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherRequestsScreen(navController: NavController, httpClient: HttpClient = koinInject()) {
    var requests by remember { mutableStateOf<List<TeacherRequestDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(refresh) {
        isLoading = true
        error = null
        try {
            val response = httpClient.get("${Constants.BASE_URL}/requests/teacher")
            check(response.status.isSuccess()) { "Unable to load requests. Please retry." }
            requests = response.body()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Unable to load requests. Check your connection and retry." }
        finally { isLoading = false }
    }
    fun decide(request: TeacherRequestDto, action: String) {
        if (busyId != null) return
        busyId = request.id
        scope.launch {
            try {
                val response = httpClient.post("${Constants.BASE_URL}/requests/${request.id}/$action")
                check(response.status.isSuccess())
                requests = requests.filter { it.id != request.id }
                error = null
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Decision was not confirmed. Refresh requests before trying again." }
            finally { busyId = null }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Teacher Requests") }, actions = {
        TextButton(onClick = { refresh++ }, enabled = !isLoading && busyId == null) { Text("Refresh") }
    }) }, bottomBar = { TeacherBottomBar(navController, 3) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
            if (isLoading) item { CircularProgressIndicator() }
            if (!isLoading && error == null && requests.isEmpty()) item { Text("No pending requests") }
            items(requests, key = { it.id }) { request ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(request.studentName ?: request.studentId, style = MaterialTheme.typography.titleMedium)
                        Text(if (request.requestType == "FACE_REGISTRATION") "Face replacement approval requested" else "Device change requested")
                        Text(if (request.requestType == "FACE_REGISTRATION") "Verify the student's identity in person before approving. The replacement face remains inactive until approval." else request.requestedData)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { decide(request, "reject") }, enabled = busyId == null) { Text("Reject") }
                            Button(onClick = { decide(request, "approve") }, enabled = busyId == null) { Text(if (busyId == request.id) "Saving…" else "Approve") }
                        }
                    }
                }
            }
        }
    }
}
