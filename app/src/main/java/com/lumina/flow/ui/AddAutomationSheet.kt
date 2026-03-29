package com.lumina.flow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lumina.flow.automation.AutomationJsonCodec
import com.lumina.flow.automation.AutomationPlanner
import com.lumina.flow.data.AutomationEntity
import com.lumina.flow.model.ActionType
import com.lumina.flow.model.AutomationActionConfig
import com.lumina.flow.model.AutomationConditions
import com.lumina.flow.model.TriggerType

private data class EditableAction(
    val id: Int,
    val type: ActionType,
    val title: String = "",
    val message: String = "",
    val target: String = "",
    val durationMs: String = "600"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddAutomationSheet(
    entity: AutomationEntity?,
    onSave: (AutomationEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val conditions = remember(entity?.id) {
        AutomationJsonCodec.decodeConditions(entity?.conditionsJson.orEmpty())
    }
    var name by rememberSaveable(entity?.id) { mutableStateOf(entity?.name ?: "") }
    var description by rememberSaveable(entity?.id) { mutableStateOf(entity?.description ?: "") }
    var enabled by rememberSaveable(entity?.id) { mutableStateOf(entity?.enabled ?: true) }
    var triggerType by rememberSaveable(entity?.id) {
        mutableStateOf(TriggerType.fromValue(entity?.triggerType ?: TriggerType.TIME.value))
    }
    var hour by rememberSaveable(entity?.id) { mutableIntStateOf(entity?.hour ?: 8) }
    var minute by rememberSaveable(entity?.id) { mutableIntStateOf(entity?.minute ?: 0) }
    var intervalMinutes by rememberSaveable(entity?.id) {
        mutableStateOf(entity?.intervalMinutes?.toString() ?: "60")
    }
    var latitude by rememberSaveable(entity?.id) { mutableStateOf(entity?.latitude?.toString() ?: "") }
    var longitude by rememberSaveable(entity?.id) { mutableStateOf(entity?.longitude?.toString() ?: "") }
    var radius by rememberSaveable(entity?.id) { mutableStateOf(entity?.radius ?: 150f) }
    var requireCharging by rememberSaveable(entity?.id) { mutableStateOf(conditions.requireCharging) }
    var wifiOnly by rememberSaveable(entity?.id) { mutableStateOf(conditions.wifiOnly) }
    var minimumBattery by rememberSaveable(entity?.id) { mutableStateOf(conditions.minimumBattery?.toString() ?: "") }
    val selectedDays = remember(entity?.id) {
        mutableStateListOf<Int>().apply {
            addAll(AutomationPlanner.decodeDays(entity?.daysOfWeek.orEmpty()))
        }
    }
    val actions = remember(entity?.id) {
        mutableStateListOf<EditableAction>().apply {
            val decoded = AutomationJsonCodec.decodeActions(entity?.actionsJson.orEmpty())
            if (decoded.isEmpty()) {
                add(EditableAction(1, ActionType.NOTIFICATION, title = "LuminaFlow", message = "任务已触发"))
            } else {
                decoded.forEachIndexed { index, action ->
                    add(
                        EditableAction(
                            id = index + 1,
                            type = action.type,
                            title = action.title,
                            message = action.message,
                            target = action.target,
                            durationMs = action.durationMs.toString()
                        )
                    )
                }
            }
        }
    }
    var showAddAction by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                if (entity == null) "新建自动化任务" else "编辑自动化任务",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Section("基础信息") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("任务名称") }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("描述") },
                    minLines = 2
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用任务", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }

            Section("触发方式") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TriggerType.entries.forEachIndexed { index, item ->
                        SegmentedButton(
                            selected = triggerType == item,
                            onClick = { triggerType = item },
                            shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = TriggerType.entries.size
                            )
                        ) {
                            Text(
                                when (item) {
                                    TriggerType.TIME -> "定时"
                                    TriggerType.INTERVAL -> "循环"
                                    TriggerType.LOCATION -> "位置"
                                }
                            )
                        }
                    }
                }

                when (triggerType) {
                    TriggerType.TIME -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NumberField(
                                label = "小时",
                                value = hour.toString(),
                                hint = "0-23",
                                onValueChange = { value -> hour = value.toIntOrNull()?.coerceIn(0, 23) ?: hour },
                                modifier = Modifier.weight(1f)
                            )
                            NumberField(
                                label = "分钟",
                                value = minute.toString(),
                                hint = "0-59",
                                onValueChange = { value -> minute = value.toIntOrNull()?.coerceIn(0, 59) ?: minute },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text("重复日期", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(2 to "一", 3 to "二", 4 to "三", 5 to "四", 6 to "五", 7 to "六", 1 to "日")
                                .forEach { (value, label) ->
                                    FilterChip(
                                        selected = value in selectedDays,
                                        onClick = {
                                            if (value in selectedDays) selectedDays.remove(value) else selectedDays.add(value)
                                        },
                                        label = { Text(label) }
                                    )
                                }
                        }
                        Text(
                            "不选日期默认每天执行。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TriggerType.INTERVAL -> {
                        NumberField(
                            label = "间隔分钟",
                            value = intervalMinutes,
                            hint = "例如 30 / 90 / 240",
                            onValueChange = { intervalMinutes = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    TriggerType.LOCATION -> {
                        NumberField(
                            label = "纬度",
                            value = latitude,
                            hint = "如 31.2304",
                            onValueChange = { latitude = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                        NumberField(
                            label = "经度",
                            value = longitude,
                            hint = "如 121.4737",
                            onValueChange = { longitude = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("半径 ${radius.toInt()} 米")
                        Slider(value = radius, onValueChange = { radius = it }, valueRange = 50f..1000f)
                        Text(
                            "地理围栏模型已接入保存与展示，当前版本暂不自动监听位置变化。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Section("动作编排") {
                actions.forEachIndexed { index, action ->
                    ActionEditorCard(
                        index = index + 1,
                        action = action,
                        onChange = { updated -> actions[index] = updated },
                        onDelete = { if (actions.size > 1) actions.removeAt(index) }
                    )
                }
                OutlinedButton(onClick = { showAddAction = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("添加动作")
                }
            }

            Section("执行条件") {
                SwitchRow("仅充电时执行", requireCharging) { requireCharging = it }
                SwitchRow("仅 Wi-Fi 下执行", wifiOnly) { wifiOnly = it }
                NumberField(
                    label = "最低电量",
                    value = minimumBattery,
                    hint = "留空表示不限制",
                    onValueChange = { minimumBattery = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Section("预览") {
                Text(previewLabel(triggerType, hour, minute, intervalMinutes))
                Text(
                    "动作数 ${actions.size}，保存后自动计算下次执行时间。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        val built = buildEntity(
                            original = entity,
                            name = name,
                            description = description,
                            enabled = enabled,
                            triggerType = triggerType,
                            hour = hour,
                            minute = minute,
                            intervalMinutes = intervalMinutes,
                            selectedDays = selectedDays.toSet(),
                            latitude = latitude,
                            longitude = longitude,
                            radius = radius,
                            actions = actions,
                            requireCharging = requireCharging,
                            wifiOnly = wifiOnly,
                            minimumBattery = minimumBattery
                        )
                        if (built == null) error = "请补全当前触发器需要的关键字段" else onSave(built)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存任务")
                }
            }
        }
    }

    if (showAddAction) {
        AlertDialog(
            onDismissRequest = { showAddAction = false },
            title = { Text("添加动作") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionType.entries.forEach { type ->
                        OutlinedButton(
                            onClick = {
                                val nextId = (actions.maxOfOrNull { it.id } ?: 0) + 1
                                actions.add(EditableAction(nextId, type))
                                showAddAction = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(type.label)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAddAction = false }) { Text("关闭") } }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        placeholder = { Text(hint) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionEditorCard(
    index: Int,
    action: EditableAction,
    onChange: (EditableAction) -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("动作 $index", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除动作")
                }
            }

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = action.type.label,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    label = { Text("动作类型") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    ActionType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label) },
                            onClick = {
                                onChange(action.copy(type = type))
                                expanded = false
                            }
                        )
                    }
                }
            }

            when (action.type) {
                ActionType.NOTIFICATION -> {
                    OutlinedTextField(
                        value = action.title,
                        onValueChange = { onChange(action.copy(title = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("通知标题") }
                    )
                    OutlinedTextField(
                        value = action.message,
                        onValueChange = { onChange(action.copy(message = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("通知内容") }
                    )
                }
                ActionType.OPEN_URL -> {
                    OutlinedTextField(
                        value = action.target,
                        onValueChange = { onChange(action.copy(target = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("目标链接") },
                        placeholder = { Text("https://example.com") }
                    )
                }
                ActionType.OPEN_APP -> {
                    OutlinedTextField(
                        value = action.target,
                        onValueChange = { onChange(action.copy(target = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("应用包名") },
                        placeholder = { Text("com.tencent.mm") }
                    )
                }
                ActionType.CLIPBOARD -> {
                    OutlinedTextField(
                        value = action.message,
                        onValueChange = { onChange(action.copy(message = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("要复制的文本") },
                        minLines = 2
                    )
                }
                ActionType.VIBRATE -> {
                    NumberField(
                        label = "振动时长(ms)",
                        value = action.durationMs,
                        hint = "建议 300-1200",
                        onValueChange = { onChange(action.copy(durationMs = it)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun buildEntity(
    original: AutomationEntity?,
    name: String,
    description: String,
    enabled: Boolean,
    triggerType: TriggerType,
    hour: Int,
    minute: Int,
    intervalMinutes: String,
    selectedDays: Set<Int>,
    latitude: String,
    longitude: String,
    radius: Float,
    actions: List<EditableAction>,
    requireCharging: Boolean,
    wifiOnly: Boolean,
    minimumBattery: String
): AutomationEntity? {
    if (name.isBlank()) return null

    val builtActions = actions.map {
        AutomationActionConfig(
            type = it.type,
            title = it.title.trim(),
            message = it.message.trim(),
            target = it.target.trim(),
            durationMs = it.durationMs.toLongOrNull() ?: 600L
        )
    }
    if (builtActions.isEmpty()) return null

    val triggerValid = when (triggerType) {
        TriggerType.TIME -> true
        TriggerType.INTERVAL -> intervalMinutes.toIntOrNull()?.let { it > 0 } == true
        TriggerType.LOCATION -> latitude.toDoubleOrNull() != null && longitude.toDoubleOrNull() != null
    }
    if (!triggerValid) return null

    return AutomationEntity(
        id = original?.id ?: 0L,
        name = name.trim(),
        description = description.trim(),
        triggerType = triggerType.value,
        hour = if (triggerType == TriggerType.TIME) hour else null,
        minute = if (triggerType == TriggerType.TIME) minute else null,
        daysOfWeek = if (triggerType == TriggerType.TIME) AutomationPlanner.encodeDays(selectedDays) else "",
        intervalMinutes = if (triggerType == TriggerType.INTERVAL) intervalMinutes.toIntOrNull() else null,
        latitude = if (triggerType == TriggerType.LOCATION) latitude.toDoubleOrNull() else null,
        longitude = if (triggerType == TriggerType.LOCATION) longitude.toDoubleOrNull() else null,
        radius = radius,
        enabled = enabled,
        actionsJson = AutomationJsonCodec.encodeActions(builtActions),
        conditionsJson = AutomationJsonCodec.encodeConditions(
            AutomationConditions(
                requireCharging = requireCharging,
                wifiOnly = wifiOnly,
                minimumBattery = minimumBattery.toIntOrNull()
            )
        ),
        lastRunAt = original?.lastRunAt,
        nextRunAt = original?.nextRunAt,
        lastResult = original?.lastResult ?: "未执行",
        createdAt = original?.createdAt ?: System.currentTimeMillis()
    )
}

private fun previewLabel(
    triggerType: TriggerType,
    hour: Int,
    minute: Int,
    intervalMinutes: String
): String = when (triggerType) {
    TriggerType.TIME -> "每天 %02d:%02d".format(hour, minute)
    TriggerType.INTERVAL -> "每 ${intervalMinutes.ifBlank { "?" }} 分钟轮询一次"
    TriggerType.LOCATION -> "进入指定半径范围时触发"
}
