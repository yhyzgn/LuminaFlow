package com.lumina.flow.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.lumina.flow.data.AutomationEntity
import com.lumina.flow.viewmodel.AutomationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: AutomationViewModel = hiltViewModel()) {
    val automations by viewModel.automations.collectAsState(initial = emptyList())
    val context = LocalContext.current
    var showAddSheet by remember { mutableStateOf(false) }
    var showRemoteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("LuminaFlow v2.0") },
                actions = {
                    IconButton(onClick = { showRemoteDialog = true }) {
                        Icon(Icons.Default.CloudDownload, "远程导入")
                    }
                    IconButton(onClick = {
                        viewModel.exportToYaml { success ->
                            val msg =
                                if (success) "已导出到 Downloads/lumina_flow.yml" else "导出失败"
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }) {
                        Icon(Icons.Default.CloudUpload, "导出 YAML")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, "添加")
            }
        }
    ) { padding ->
        if (automations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "暂无自动化任务\n点击 + 添加或使用远程/导出",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(automations) { item ->
                    AutomationItem(item, onDelete = { viewModel.delete(item) })
                }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { }) {
            AddAutomationSheet(onSave = { viewModel.save(it); })
        }
    }

    if (showRemoteDialog) {
        RemoteImportDialog(onImport = { url ->
            viewModel.importFromUrl(url)
        })
    }
}

@Composable
fun AutomationItem(item: AutomationEntity, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(), shape = RoundedCornerShape(24.dp)
    ) {
        ListItem(
            headlineContent = { Text(item.name) },
            supportingContent = { Text("${item.triggerType} | 动作: ${item.actionsJson.take(50)}...") },
            trailingContent = { TextButton(onClick = onDelete) { Text("删除") } }
        )
    }
}