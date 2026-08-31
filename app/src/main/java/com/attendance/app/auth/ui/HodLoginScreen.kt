package com.attendance.app.auth.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.attendance.app.core.ui.components.AppTextField
import com.attendance.app.core.ui.components.LoadingButton
import com.attendance.app.core.ui.components.PasswordTextField

import com.attendance.app.auth.presentation.AuthViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HodLoginScreen(
    onSuccess: (role: String, requiresFace: Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    var hodId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HOD Login") },
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
            Text(text = "HOD Login", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Login with your Employee ID. (Default: hod_cs / 123456)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            AppTextField(value = hodId, onValueChange = { hodId = it }, label = "HOD / Employee ID")
            Spacer(modifier = Modifier.height(16.dp))
            PasswordTextField(value = password, onValueChange = { password = it }, label = "Password")
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
                text = "Continue",
                onClick = {
                    viewModel.login(
                        context = context,
                        collegeId = hodId,
                        password = password,
                        onSuccess = onSuccess,
                        onDeviceMismatch = { deviceId -> showDeviceMismatchDialog = deviceId }
                    )
                },
                isLoading = viewModel.isLoading,
                enabled = hodId.isNotBlank() && password.isNotBlank() && !viewModel.isLoading
            )
        }
    }
}
