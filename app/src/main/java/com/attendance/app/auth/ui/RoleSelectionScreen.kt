package com.attendance.app.auth.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.attendance.app.core.ui.components.PasswordTextField
import com.attendance.app.auth.presentation.AuthViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun RoleSelectionScreen(
    onStudentLogin: () -> Unit,
    onStudentRegister: () -> Unit,
    onTeacherLogin: () -> Unit,
    onTeacherRegister: () -> Unit,
    onHodLogin: () -> Unit,
    onHodRegister: () -> Unit,
    onAdminLogin: () -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    var showGatewayDialog by remember { mutableStateOf(false) }
    var gatewayTarget by remember { mutableStateOf<(() -> Unit)?>(null) }
    var gatewayPasscode by remember { mutableStateOf("") }
    var gatewayError by remember { mutableStateOf(false) }

    if (showGatewayDialog) {
        AlertDialog(
            onDismissRequest = { 
                showGatewayDialog = false
                gatewayPasscode = ""
                gatewayError = false 
            },
            title = { Text("Staff Access") },
            text = {
                Column {
                    PasswordTextField(
                        value = gatewayPasscode,
                        onValueChange = { 
                            gatewayPasscode = it
                            gatewayError = false 
                        },
                        label = "Staff Passcode"
                    )
                    if (gatewayError) {
                        Text("Invalid Passcode", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.verifyStaffCode(gatewayPasscode, onSuccess = {
                        showGatewayDialog = false
                        gatewayPasscode = ""
                        gatewayTarget?.invoke()
                    }, onError = {
                        gatewayError = true
                    })
                }) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showGatewayDialog = false 
                    gatewayPasscode = ""
                    gatewayError = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Attendance",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Smart BLE Attendance System",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Select your role to continue",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        RoleCard("Student", Icons.Default.School, "Login with College ID") { onStudentLogin() }
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onStudentRegister,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("New Student? Register Here")
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))

        RoleCard("Teacher", Icons.Default.Person, "Login with Faculty ID") { onTeacherLogin() }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { 
                gatewayTarget = onTeacherRegister
                showGatewayDialog = true
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("New Teacher? Register Here")
        }
        Spacer(modifier = Modifier.height(12.dp))
        RoleCard("HOD", Icons.Default.SupervisorAccount, "Login with HOD ID") { onHodLogin() }
        Spacer(modifier = Modifier.height(12.dp))
        
        OutlinedButton(
            onClick = { 
                gatewayTarget = onHodRegister
                showGatewayDialog = true
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("New HOD? Register Here")
        }
        Spacer(modifier = Modifier.height(12.dp))
        RoleCard("Admin", Icons.Default.AdminPanelSettings, "Login with Admin ID") { 
            gatewayTarget = onAdminLogin
            showGatewayDialog = true
        }
    }
}

@Composable
private fun RoleCard(
    title: String,
    icon: ImageVector,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

