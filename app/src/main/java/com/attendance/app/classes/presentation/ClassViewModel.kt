package com.attendance.app.classes.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attendance.app.classes.data.ClassRepository
import com.attendance.shared.dto.ClassResponse
import com.attendance.shared.dto.CreateClassRequest
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

class ClassViewModel(
    private val repository: ClassRepository
) : ViewModel() {
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
        
    val classes: StateFlow<List<ClassResponse>> = repository.getLocalClasses()
        .catch { e -> error = e.message }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun fetchTeacherClasses() {
        viewModelScope.launch {
            isLoading = true
            error = null
            val result = repository.fetchTeacherClasses()
            result.onFailure { 
                error = it.message ?: "Failed to load classes" 
            }
            isLoading = false
        }
    }

    fun fetchStudentClasses() {
        viewModelScope.launch {
            isLoading = true
            error = null
            val result = repository.fetchStudentClasses()
            result.onFailure { 
                error = it.message ?: "Failed to load classes" 
            }
            isLoading = false
        }
    }

    fun createClass(
        name: String,
        section: String,
        semester: Int,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            isLoading = true
            error = null
            val result = repository.createClass(
                CreateClassRequest(
                    name = name,
                    section = section,
                    semester = semester
                )
            )
            result.onSuccess {
                fetchTeacherClasses()
                onSuccess()
            }.onFailure {
                error = it.message ?: "Failed to create class"
            }
            isLoading = false
        }
    }

    fun joinClass(classCode: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            error = null
            val result = repository.joinClass(classCode.uppercase())
            result.onSuccess {
                fetchStudentClasses()
                onSuccess()
            }.onFailure {
                error = it.message ?: "Failed to join class"
            }
            isLoading = false
        }
    }

    var pendingRequests by mutableStateOf<List<com.attendance.shared.dto.JoinRequestResponse>>(emptyList())
        private set
        
    var roster by mutableStateOf<List<com.attendance.shared.dto.JoinRequestResponse>>(emptyList())
        private set

    fun fetchPendingRequests(classId: String) {
        viewModelScope.launch {
            isLoading = true
            val result = repository.getPendingRequests(classId)
            result.onSuccess {
                pendingRequests = it
            }.onFailure {
                error = it.message ?: "Failed to fetch pending requests"
            }
            isLoading = false
        }
    }

    fun fetchRoster(classId: String) {
        viewModelScope.launch {
            isLoading = true
            val result = repository.getRoster(classId)
            result.onSuccess {
                roster = it
                // Backwards compatibility
                pendingRequests = it.filter { req -> req.status == "PENDING" }
            }.onFailure {
                error = it.message ?: "Failed to fetch roster"
            }
            isLoading = false
        }
    }

    fun approveRequest(classId: String, requestId: String) {
        viewModelScope.launch {
            val result = repository.approveRequest(classId, requestId)
            result.onSuccess {
                fetchRoster(classId)
                fetchTeacherClasses()
            }.onFailure {
                error = it.message ?: "Failed to approve request"
            }
        }
    }
    
    fun rejectRequest(classId: String, requestId: String) {
        viewModelScope.launch {
            val result = repository.rejectRequest(classId, requestId)
            result.onSuccess {
                fetchRoster(classId)
                fetchTeacherClasses()
            }.onFailure {
                error = it.message ?: "Failed to reject request"
            }
        }
    }
    
    fun deleteClass(classId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            val result = repository.deleteClass(classId)
            result.onSuccess {
                fetchTeacherClasses()
                onSuccess()
            }.onFailure {
                error = it.message ?: "Failed to delete class"
            }
            isLoading = false
        }
    }

    fun removeStudent(classId: String, studentId: String) {
        viewModelScope.launch {
            isLoading = true
            val result = repository.removeStudent(classId, studentId)
            result.onSuccess {
                fetchRoster(classId)
                fetchTeacherClasses()
            }.onFailure {
                error = it.message ?: "Failed to remove student"
            }
            isLoading = false
        }
    }
}
