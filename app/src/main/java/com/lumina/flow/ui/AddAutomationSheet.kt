package com.lumina.flow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumina.flow.data.AutomationEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAutomationSheet(onSave: (AutomationEntity) -> Unit) {
    var name by remember { mutableStateOf("新任务") }
    var triggerType by remember { mutableStateOf("TIME") }
    var hour by remember { mutableStateOf(22) }
    var minute by remember { mutableStateOf(0) }
    var latitude by remember { mutableStateOf(1.3521) }
    var longitude by remember { mutableStateOf(103.8198) }
    var radius by remember { mutableStateOf(100f) }
    var actionsJson by remember { mutableStateOf("""[{"type":"NOTIFICATION","param":"任务已触发"}]""") }

    Column(
        Modifier
            .padding(24.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("新建自动化", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("任务名称") })

        TabRow(selectedTabIndex = if (triggerType == "TIME") 0 else 1) {
            Tab(
                selected = triggerType == "TIME",
                onClick = { triggerType = "TIME" }) { Text("时间触发") }
            Tab(
                selected = triggerType == "LOCATION",
                onClick = { triggerType = "LOCATION" }) { Text("位置触发") }
        }

        if (triggerType == "TIME") {
            TimePicker(state = rememberTimePickerState(hour, minute))
        } else {
            OutlinedTextField(
                value = latitude.toString(),
                onValueChange = { latitude = it.toDoubleOrNull() ?: 0.0 },
                label = { Text("纬度") })
            OutlinedTextField(
                value = longitude.toString(),
                onValueChange = { longitude = it.toDoubleOrNull() ?: 0.0 },
                label = { Text("经度") })
            Slider(value = radius, onValueChange = { radius = it }, valueRange = 50f..500f)
            Text("半径: ${radius.toInt()} 米")
        }

        OutlinedTextField(
            value = actionsJson,
            onValueChange = { actionsJson = it },
            label = { Text("动作 JSON") },
            modifier = Modifier.height(120.dp)
        )

        Button(onClick = {
            onSave(
                AutomationEntity(
                    name = name,
                    triggerType = triggerType,
                    hour = if (triggerType == "TIME") hour else null,
                    minute = if (triggerType == "TIME") minute else null,
                    latitude = if (triggerType == "LOCATION") latitude else null,
                    longitude = if (triggerType == "LOCATION") longitude else null,
                    radius = if (triggerType == "LOCATION") radius else 100f,
                    actionsJson = actionsJson
                )
            )
        }, modifier = Modifier.fillMaxWidth()) {
            Text("保存")
        }
    }
}