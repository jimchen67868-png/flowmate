package com.example.automateclone.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.automateclone.engine.FlowLog
import com.example.automateclone.engine.LogLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogScreen(modifier: Modifier = Modifier) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Box(modifier = modifier.fillMaxSize()) {
        if (FlowLog.entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No log entries yet — run a flow to see activity here.",
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(FlowLog.entries) { entry ->
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = entry.message,
                            fontSize = 13.sp,
                            color = if (entry.level == LogLevel.ERROR) MaterialTheme.colorScheme.error else Color.Unspecified
                        )
                        Text(
                            text = "${entry.flowName} • ${formatter.format(Date(entry.timestamp))}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
