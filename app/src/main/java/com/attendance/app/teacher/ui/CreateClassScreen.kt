package com.attendance.app.teacher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.attendance.app.core.ui.components.AppTextField
import com.attendance.app.core.ui.components.LoadingButton
import com.attendance.app.classes.presentation.ClassViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateClassScreen(
    onBack: () -> Unit,
    viewModel: ClassViewModel = koinViewModel()
) {
    var className by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var semester by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Class") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            AppTextField(value = className, onValueChange = { className = it }, label = "Class Name (e.g. Data Structures)")
            Spacer(modifier = Modifier.height(12.dp))
            AppTextField(value = section, onValueChange = { section = it }, label = "Section (e.g. CSE-3)")
            Spacer(modifier = Modifier.height(12.dp))
            AppTextField(value = semester, onValueChange = { semester = it }, label = "Semester", keyboardType = KeyboardType.Number)
            Spacer(modifier = Modifier.height(16.dp))
            
            viewModel.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
            }

            LoadingButton(
                text = "Create Class", 
                onClick = { 
                    viewModel.createClass(
                        name = className.trim(),
                        section = section.trim(),
                        semester = semester.toIntOrNull() ?: 1
                    ) {
                        onBack()
                    }
                }, 
                isLoading = viewModel.isLoading, 
                enabled = className.isNotBlank() && section.isNotBlank() && !viewModel.isLoading
            )
        }
    }
}
