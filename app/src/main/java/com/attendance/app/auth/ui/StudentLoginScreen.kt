package com.attendance.app.auth.ui

import androidx.compose.foundation.layout.*
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
fun StudentLoginScreen(
    onSuccess: (role: String, requiresFace: Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    var collegeId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Student Login") },
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Welcome back",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Login with your College ID and password",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            AppTextField(
                value = collegeId,
                onValueChange = { collegeId = it },
                label = "College ID",
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            )
            Spacer(modifier = Modifier.height(16.dp))

            PasswordTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password"
            )
            Spacer(modifier = Modifier.height(16.dp))

            viewModel.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
            }

            val context = androidx.compose.ui.platform.LocalContext.current
            var showDeviceMismatchDialog by remember { mutableStateOf<String?>(null) }
            
            showDeviceMismatchDialog?.let { deviceId ->
                AlertDialog(
                    onDismissRequest = { showDeviceMismatchDialog = null },
                    title = { Text("Device Mismatch") },
                    text = { Text("This account is bound to another device. Request a device transfer?") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.requestDeviceTransfer(deviceId) {
                                showDeviceMismatchDialog = null
                            }
                        }) { Text("Yes") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeviceMismatchDialog = null }) { Text("No") }
                    }
                )
            }

            LoadingButton(
                text = "Login",
                onClick = {
                    viewModel.login(
                        context = context,
                        collegeId = collegeId,
                        password = password,
                        onSuccess = onSuccess,
                        onDeviceMismatch = { deviceId -> showDeviceMismatchDialog = deviceId }
                    )
                },
                isLoading = viewModel.isLoading,
                enabled = collegeId.isNotBlank() && password.isNotBlank() && !viewModel.isLoading
            )
        }
    }
}
