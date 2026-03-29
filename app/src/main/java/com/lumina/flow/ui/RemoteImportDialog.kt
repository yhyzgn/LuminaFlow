package com.lumina.flow.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteImportDialog(onImport: (String) -> Unit) {
    var url by remember { mutableStateOf("https://example.com/automations.yml") }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("远程 YAML 配置导入") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("YAML URL") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onImport(url) }) { Text("下载并导入") }
        },
        dismissButton = {
            TextButton(onClick = {}) { Text("取消") }
        }
    )
}