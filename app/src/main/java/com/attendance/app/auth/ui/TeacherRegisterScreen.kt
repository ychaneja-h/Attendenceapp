package com.attendance.app.auth.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.attendance.app.auth.presentation.AuthViewModel
import com.attendance.app.core.ui.components.AppTextField
import com.attendance.app.core.ui.components.LoadingButton
import com.attendance.app.core.ui.components.PasswordTextField
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherRegisterScreen(
    onBack: () -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    var name by remember { mutableStateOf("") }
    var facultyId by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPendingDialog by remember { mutableStateOf(false) }

    if (showPendingDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Registration Successful") },
            text = { Text("Your account is pending HOD approval.") },
            confirmButton = {
                TextButton(onClick = {
                    showPendingDialog = false
                    onBack()
                }) { Text("OK") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Teacher Registration") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(text = "Create Teacher Account", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(24.dp))

            AppTextField(value = name, onValueChange = { name = it }, label = "Full Name")
            Spacer(modifier = Modifier.height(16.dp))

            AppTextField(value = facultyId, onValueChange = { facultyId = it }, label = "Faculty / Employee ID")
            Spacer(modifier = Modifier.height(16.dp))

            AppTextField(value = department, onValueChange = { department = it }, label = "Department Invite Code")
            Spacer(modifier = Modifier.height(16.dp))

            PasswordTextField(value = password, onValueChange = { password = it }, label = "Password")
            Spacer(modifier = Modifier.height(24.dp))

            viewModel.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
            }

            LoadingButton(
                text = "Register",
                onClick = {
                    viewModel.registerTeacher(
                        name = name,
                        collegeId = facultyId,
                        departmentCode = department,
                        password = password,
                        onPendingApproval = { showPendingDialog = true }
                    )
                },
                isLoading = viewModel.isLoading,
                enabled = name.isNotBlank() && facultyId.isNotBlank() && password.isNotBlank() && !viewModel.isLoading
            )
        }
    }
}


