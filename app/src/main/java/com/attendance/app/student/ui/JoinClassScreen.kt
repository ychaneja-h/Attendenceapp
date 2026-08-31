package com.attendance.app.student.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.attendance.app.core.ui.components.AppTextField
import com.attendance.app.core.ui.components.LoadingButton
import com.attendance.app.classes.presentation.ClassViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinClassScreen(
    onBack: () -> Unit,
    viewModel: ClassViewModel = koinViewModel()
) {
    var classCode by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join Class") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)
        ) {
            Text("Enter Class Code", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Ask your teacher for the class code", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(32.dp))
            AppTextField(value = classCode, onValueChange = { classCode = it.uppercase() }, label = "Class Code (e.g. DS7K4P)")
            Spacer(modifier = Modifier.height(16.dp))
            
            viewModel.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
            }

            LoadingButton(
                text = "Join", 
                onClick = { 
                    viewModel.joinClass(classCode) {
                        onBack()
                    } 
                }, 
                isLoading = viewModel.isLoading, 
                enabled = classCode.length >= 6 && !viewModel.isLoading
            )
        }
    }
}
