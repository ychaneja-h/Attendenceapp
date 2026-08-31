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
fun HodRegisterScreen(
    onOtpRequired: (phone: String, sessionToken: String) -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    var name by remember { mutableStateOf("") }
    var collegeId by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var departmentCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HOD Registration") },
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
            Text(text = "Create HOD Account", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(24.dp))

            AppTextField(value = name, onValueChange = { name = it }, label = "Full Name")
            Spacer(modifier = Modifier.height(16.dp))

            AppTextField(value = collegeId, onValueChange = { collegeId = it }, label = "College ID (Unique)")
            Spacer(modifier = Modifier.height(16.dp))

            AppTextField(value = phone, onValueChange = { phone = it }, label = "Phone Number")
            Spacer(modifier = Modifier.height(16.dp))

            AppTextField(value = department, onValueChange = { department = it }, label = "Department Name")
            Spacer(modifier = Modifier.height(16.dp))

            AppTextField(value = departmentCode, onValueChange = { departmentCode = it }, label = "Department Code (Invite Code)")
            Spacer(modifier = Modifier.height(16.dp))

            PasswordTextField(value = password, onValueChange = { 
                password = it 
                if (password == confirmPassword) passwordError = null
            }, label = "Password")
            Spacer(modifier = Modifier.height(16.dp))

            PasswordTextField(value = confirmPassword, onValueChange = { 
                confirmPassword = it 
                if (password == confirmPassword) passwordError = null
            }, label = "Confirm Password")
            
            passwordError?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(24.dp))

            viewModel.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
            }

            LoadingButton(
                text = "Register",
                onClick = {
                    if (password != confirmPassword) {
                        passwordError = "Passwords do not match"
                        return@LoadingButton
                    }
                    viewModel.registerHod(
                        name = name,
                        collegeId = collegeId,
                        phone = phone,
                        department = department,
                        departmentCode = departmentCode,
                        password = password
                    ) { sessionToken ->
                        onOtpRequired(phone, sessionToken)
                    }
                },
                isLoading = viewModel.isLoading,
                enabled = name.isNotBlank() && collegeId.isNotBlank() && phone.isNotBlank() && department.isNotBlank() && departmentCode.isNotBlank() && password.isNotBlank() && confirmPassword.isNotBlank() && !viewModel.isLoading
            )
        }
    }
}
