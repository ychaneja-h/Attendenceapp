package com.attendance.app.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AttendanceCounts(present: Int, absent: Int, review: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple("Present", present, Color(0xFF16834A)), Triple("Absent", absent, Color(0xFFC33D49)), Triple("Review", review, Color(0xFFB47712))).forEach { (label, count, color) ->
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.10f)) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

fun attendanceLabel(status: String): String = when (status) {
    "PRESENT" -> "Present"
    "ABSENT" -> "Absent"
    "UNDER_REVIEW" -> "Under review"
    else -> status.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
fun AttendanceStatus(status: String, provisional: Boolean = false) {
    val color = when (status) {
        "PRESENT" -> Color(0xFF16834A)
        "ABSENT" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }
    Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.12f)) {
        Text(attendanceLabel(status) + if (provisional) " · pending" else "",
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun AttendanceDetails(content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(if (expanded) "Hide details" else "View details")
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(18.dp))
        }
        AnimatedVisibility(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
        }
    }
}

@Composable
fun ClassArtwork() {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.School, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun AttendanceSyncNote(message: String?) {
    val label = when {
        message == null || message == "Attendance updated from server." -> null
        message.startsWith("Syncing") -> "Updating attendance…"
        message.startsWith("Saved on this phone") || message.startsWith("Saved evidence") -> "Saved on this phone · waiting to sync"
        message.startsWith("Some evidence") || message.startsWith("Evidence needs review") -> "Some records need review"
        else -> message
    }
    label?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
