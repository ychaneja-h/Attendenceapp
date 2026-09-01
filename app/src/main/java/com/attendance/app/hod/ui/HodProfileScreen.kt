package com.attendance.app.hod.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.attendance.app.auth.presentation.AuthViewModel
import com.attendance.app.core.ui.navigation.Screen
import com.attendance.app.profile.presentation.ProfileUiState
import com.attendance.app.profile.presentation.ProfileViewModel
import org.koin.androidx.compose.koinViewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HodProfileScreen(
    navController: NavController,
    authViewModel: AuthViewModel = koinViewModel(),
    profileViewModel: ProfileViewModel = koinViewModel()
) {
    val uiState by profileViewModel.uiState.collectAsState()
    val isSaving by profileViewModel.isSaving.collectAsState()
    val saveMessage by profileViewModel.saveMessage.collectAsState()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var batch by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is ProfileUiState.Success) {
            val profile = (uiState as ProfileUiState.Success).profile
            name = profile.name
            email = profile.email
            department = profile.department ?: ""
            section = profile.section ?: ""
            batch = profile.batch ?: ""
            year = profile.year ?: ""
            photoUrl = profile.photoUrl ?: ""
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("HOD Profile") }) },
        bottomBar = { HodBottomBar(navController, 3) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (uiState) {
                is ProfileUiState.Loading -> CircularProgressIndicator()
                is ProfileUiState.Error -> Text("Error loading profile")
                is ProfileUiState.Success -> {
                    
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = department, onValueChange = { department = it }, label = { Text("Department") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = section, onValueChange = { section = it }, label = { Text("Section") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = batch, onValueChange = { batch = it }, label = { Text("Batch") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Year") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { profileViewModel.updateProfile(name, email, department, section, batch, year, photoUrl) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving
                    ) {
                        Text(if (isSaving) "Saving..." else "Save Profile")
                    }

                    saveMessage?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.primary)
                        profileViewModel.clearSaveMessage()
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = {
                    authViewModel.logout()
                    navController.navigate(Screen.RoleSelection) {
                        popUpTo(0) { inclusive = true }
                    }
                }, 
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Logout")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Made by Yogesh K. Chaneja",
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


