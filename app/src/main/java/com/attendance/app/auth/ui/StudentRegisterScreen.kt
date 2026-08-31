package com.attendance.app.auth.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.attendance.app.core.ui.components.AppTextField
import com.attendance.app.core.ui.components.LoadingButton
import com.attendance.app.core.ui.components.PasswordTextField
import com.attendance.app.auth.presentation.AuthViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentRegisterScreen(
    onSuccess: (role: String, requiresFace: Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    var collegeId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var semester by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Student Registration") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Create Account", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Register with your college details. Your account will need teacher approval.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            AppTextField(value = collegeId, onValueChange = { collegeId = it }, label = "College ID (Unique)")
            Spacer(modifier = Modifier.height(12.dp))
            AppTextField(value = name, onValueChange = { name = it }, label = "Full Name")
            Spacer(modifier = Modifier.height(12.dp))
            AppTextField(value = department, onValueChange = { department = it }, label = "Department")
            Spacer(modifier = Modifier.height(12.dp))
            AppTextField(value = section, onValueChange = { section = it }, label = "Section")
            Spacer(modifier = Modifier.height(12.dp))
            AppTextField(value = semester, onValueChange = { semester = it }, label = "Semester", keyboardType = KeyboardType.Number)
            Spacer(modifier = Modifier.height(12.dp))
            PasswordTextField(value = password, onValueChange = { password = it }, label = "Password", imeAction = ImeAction.Next)
            Spacer(modifier = Modifier.height(12.dp))
            PasswordTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = "Confirm Password")
            Spacer(modifier = Modifier.height(16.dp))

            viewModel.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
            }

            LoadingButton(
                text = "Register",
                onClick = {
                    val parts = name.trim().split(" ")
                    val fName = parts.firstOrNull() ?: ""
                    val lName = if (parts.size > 1) parts.drop(1).joinToString(" ") else ""
                    val sem = semester.toIntOrNull() ?: 1
                    
                    viewModel.register(
                        name = name.trim(),
                        collegeId = collegeId,
                        phone = "",
                        department = department,
                        section = section,
                        semester = sem,
                        academicYear = "2024-2025",
                        password = password,
                        onSuccess = onSuccess
                    )
                },
                isLoading = viewModel.isLoading,
                enabled = collegeId.isNotBlank() && name.isNotBlank() && password.isNotBlank() && password == confirmPassword && !viewModel.isLoading
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
