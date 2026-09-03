package com.attendance.app.teacher.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attendance.app.core.data.local.room.AttendanceDao
import com.attendance.app.core.data.local.room.ClassDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class HistorySessionItem(
    val classId: String,
    val className: String,
    val dateString: String,
    val timestamp: Long,
    val studentCount: Int
)

class TeacherHistoryViewModel(
    private val attendanceDao: AttendanceDao,
    private val classDao: ClassDao
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<HistorySessionItem>>(emptyList())
    val sessions: StateFlow<List<HistorySessionItem>> = _sessions.asStateFlow()

        init {
        viewModelScope.launch {
            try {
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                attendanceDao.deleteOldSyncedRecords(calendar.timeInMillis)
            } catch(e: Exception) { e.printStackTrace() }
            
            combine(
                attendanceDao.getAllRecords(),
                classDao.getAllClasses()
            ) { records, classes ->
                val classMap = classes.associateBy { it.id }
                
                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                val grouped: Map<Pair<String, String>, List<com.attendance.app.core.data.local.room.AttendanceRecordEntity>> = records.groupBy { record ->
                    val date = Date(record.timestamp)
                    Pair(record.classId, dateFormat.format(date))
                }
                
                val items = mutableListOf<HistorySessionItem>()
                grouped.forEach { (key, sessionRecords) ->
                    val classId = key.first
                    val dateStr = key.second
                    
                    val className = classMap[classId]?.name ?: "Unknown Class"
                    val uniqueStudents = sessionRecords.map { it.studentId }.distinct().size
                    val latestTime = sessionRecords.maxOf { it.timestamp }
                    
                    items.add(HistorySessionItem(
                        classId = classId,
                        className = className,
                        dateString = dateStr,
                        timestamp = latestTime,
                        studentCount = uniqueStudents
                    ))
                }
                items.sortedByDescending { it.timestamp }
            }.collect { list ->
                _sessions.value = list
            }
        }
    }
}


