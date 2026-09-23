package com.attendance.app.auth.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.attendance.app.auth.presentation.AuthViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    onPasswordChanged: () -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    val context = LocalContext.current
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    val validNewPassword = newPassword.length >= 8 && newPassword.toByteArray(Charsets.UTF_8).size <= 72

    LaunchedEffect(viewModel.resetSuccess) {
        if (viewModel.resetSuccess) {
            currentPassword = ""
            newPassword = ""
            viewModel.clearResetSuccess()
            Toast.makeText(context, "Password changed. Sign in with your new password.", Toast.LENGTH_LONG).show()
            onPasswordChanged()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Change Password") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !viewModel.isLoading) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Enter your current password to choose a new one. You will be signed out after the change.")
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = currentPassword,
                onValueChange = { currentPassword = it },
                label = { Text("Current Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.isLoading,
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("New Password") },
                supportingText = { Text("Use at least 8 characters (up to 72 UTF-8 bytes).") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.isLoading,
                singleLine = true
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { viewModel.changePassword(newPassword, currentPassword) },
                enabled = currentPassword.isNotEmpty() && validNewPassword && !viewModel.isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (viewModel.isLoading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else Text("Change Password")
            }
            viewModel.error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}