package com.example.automateclone.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.automateclone.model.AutomationFlow
import com.example.automateclone.model.FlowRepository

@Composable
fun FlowListScreen(onOpenFlow: (AutomationFlow) -> Unit) {
    val context = LocalContext.current
    val repo = remember { FlowRepository(context) }
    var flows by remember { mutableStateOf(repo.loadAll()) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Flowmate") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val newFlow = AutomationFlow(name = "New Flow ${flows.size + 1}")
                repo.upsert(newFlow)
                flows = repo.loadAll()
                onOpenFlow(newFlow)
            }) { Icon(Icons.Filled.Add, contentDescription = "New flow") }
        }
    ) { padding ->
        if (flows.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Tap + to build your first automation flow")
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(flows, key = { it.id }) { flow ->
                    ListItem(
                        headlineContent = { Text(flow.name) },
                        supportingContent = { Text("${flow.blocks.size} blocks") },
                        trailingContent = {
                            Switch(
                                checked = flow.enabled,
                                onCheckedChange = { checked ->
                                    flow.enabled = checked
                                    repo.upsert(flow)
                                    flows = repo.loadAll()
                                }
                            )
                        },
                        modifier = Modifier.clickable { onOpenFlow(flow) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
