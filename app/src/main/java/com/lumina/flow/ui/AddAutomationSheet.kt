package com.lumina.flow.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
    val durationMs: String = "600",
    val rangeStart: String = "3",
    val rangeEnd: String = "15"
)

private data class LaunchableApp(
    val label: String,
    val packageName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean = false,
    val useCount: Int = 0,
    val recentOrder: Int? = null
)

private data class AppPickerUiState(
    val loading: Boolean = true,
    val apps: List<LaunchableApp> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddAutomationSheet(
    entity: AutomationEntity?,
    onSave: (AutomationEntity) -> Unit,
    onDismiss: () -> Unit,
    onTestRun: (AutomationEntity) -> Unit
) {
    val context = LocalContext.current
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
    var repeatUntilWindowEnd by rememberSaveable(entity?.id) {
        mutableStateOf(entity?.repeatUntilWindowEnd ?: false)
    }
    var windowEndHour by rememberSaveable(entity?.id) { mutableIntStateOf(entity?.windowEndHour ?: 9) }
    var windowEndMinute by rememberSaveable(entity?.id) { mutableIntStateOf(entity?.windowEndMinute ?: 0) }
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
                add(defaultNotificationAction(1))
            } else {
                decoded.forEachIndexed { index, action ->
                    add(
                        EditableAction(
                            id = index + 1,
                            type = action.type,
                            title = action.title,
                            message = action.message,
                            target = action.target,
                            durationMs = action.durationMs.toString(),
                            rangeStart = action.rangeStart.toString(),
                            rangeEnd = action.rangeEnd.toString()
                        )
                    )
                }
            }
        }
    }
    var showAddAction by remember { mutableStateOf(false) }
    var appPickerTargetId by remember { mutableStateOf<Int?>(null) }
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
                SwitchRow("启用任务", enabled) { enabled = it }
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
                            NumberField("开始小时", hour.toString(), "0-23", { value ->
                                hour = value.toIntOrNull()?.coerceIn(0, 23) ?: hour
                            }, Modifier.weight(1f))
                            NumberField("开始分钟", minute.toString(), "0-59", { value ->
                                minute = value.toIntOrNull()?.coerceIn(0, 59) ?: minute
                            }, Modifier.weight(1f))
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
                        SwitchRow("在截止时间前循环执行动作序列", repeatUntilWindowEnd) {
                            repeatUntilWindowEnd = it
                        }
                        if (repeatUntilWindowEnd) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                NumberField("截止小时", windowEndHour.toString(), "0-23", { value ->
                                    windowEndHour = value.toIntOrNull()?.coerceIn(0, 23) ?: windowEndHour
                                }, Modifier.weight(1f))
                                NumberField("截止分钟", windowEndMinute.toString(), "0-59", { value ->
                                    windowEndMinute = value.toIntOrNull()?.coerceIn(0, 59) ?: windowEndMinute
                                }, Modifier.weight(1f))
                            }
                            Text(
                                "例如 08:30 启动后，在 09:00 前不断重复执行这组动作。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    TriggerType.INTERVAL -> {
                        NumberField(
                            "间隔分钟",
                            intervalMinutes,
                            "例如 30 / 90 / 240",
                            { intervalMinutes = it },
                            Modifier.fillMaxWidth()
                        )
                    }

                    TriggerType.LOCATION -> {
                        NumberField("纬度", latitude, "如 31.2304", { latitude = it }, Modifier.fillMaxWidth())
                        NumberField("经度", longitude, "如 121.4737", { longitude = it }, Modifier.fillMaxWidth())
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
                        onDelete = { if (actions.size > 1) actions.removeAt(index) },
                        onPickApp = { appPickerTargetId = action.id },
                        onOpenAccessibilitySettings = { openAccessibilitySettings(context) }
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
                NumberField("最低电量", minimumBattery, "留空表示不限制", { minimumBattery = it }, Modifier.fillMaxWidth())
            }

            Section("预览") {
                Text(previewLabel(triggerType, hour, minute, repeatUntilWindowEnd, windowEndHour, windowEndMinute, intervalMinutes))
                Text(
                    if (repeatUntilWindowEnd) "循环任务建议至少包含一个随机延迟动作，避免高频空转。"
                    else "保存后会自动计算下次执行时间。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
                OutlinedButton(
                    onClick = {
                        val built = buildEntity(
                            original = entity,
                            name = name,
                            description = description,
                            enabled = enabled,
                            triggerType = triggerType,
                            hour = hour,
                            minute = minute,
                            repeatUntilWindowEnd = repeatUntilWindowEnd,
                            windowEndHour = windowEndHour,
                            windowEndMinute = windowEndMinute,
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
                        error = when {
                            built == null -> "请先补全配置后再试运行"
                            repeatUntilWindowEnd && actions.none { it.type == ActionType.RANDOM_DELAY } ->
                                "循环任务至少加一个随机延迟动作，避免无间隔高速循环"
                            else -> null
                        }
                        if (error == null && built != null) onTestRun(built)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("调试")
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
                            repeatUntilWindowEnd = repeatUntilWindowEnd,
                            windowEndHour = windowEndHour,
                            windowEndMinute = windowEndMinute,
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
                        error = when {
                            built == null -> "请补全当前触发器需要的关键字段"
                            repeatUntilWindowEnd && actions.none { it.type == ActionType.RANDOM_DELAY } ->
                                "循环任务至少加一个随机延迟动作，避免无间隔高速循环"
                            else -> null
                        }
                        if (error == null && built != null) onSave(built)
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
                                actions.add(defaultActionForType(nextId, type))
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

    appPickerTargetId?.let { actionId ->
        AppPickerDialog(
            context = context,
            onDismiss = { appPickerTargetId = null },
            onPick = { app ->
                val index = actions.indexOfFirst { it.id == actionId }
                if (index >= 0) {
                    actions[index] = actions[index].copy(target = app.packageName)
                }
                rememberAppSelection(context, app.packageName)
                appPickerTargetId = null
            }
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
    onDelete: () -> Unit,
    onPickApp: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
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
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    readOnly = true,
                    label = { Text("动作类型") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    ActionType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label) },
                            onClick = {
                                onChange(defaultActionForType(action.id, type).copy(
                                    title = if (type == action.type) action.title else defaultActionForType(action.id, type).title,
                                    message = if (type == action.type) action.message else defaultActionForType(action.id, type).message,
                                    target = if (type == action.type) action.target else defaultActionForType(action.id, type).target,
                                    durationMs = if (type == action.type) action.durationMs else defaultActionForType(action.id, type).durationMs,
                                    rangeStart = if (type == action.type) action.rangeStart else defaultActionForType(action.id, type).rangeStart,
                                    rangeEnd = if (type == action.type) action.rangeEnd else defaultActionForType(action.id, type).rangeEnd
                                ))
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
                    OutlinedButton(onClick = onPickApp) {
                        Text("从应用列表选择")
                    }
                }

                ActionType.GO_HOME -> {
                    Text(
                        "启用无障碍后会执行真正的全局 HOME。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onOpenAccessibilitySettings) {
                        Text("打开无障碍设置")
                    }
                }

                ActionType.CLOSE_APP -> {
                    OutlinedTextField(
                        value = action.target,
                        onValueChange = { onChange(action.copy(target = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("目标应用包名") },
                        placeholder = { Text("com.tencent.mm") }
                    )
                    OutlinedButton(onClick = onPickApp) {
                        Text("选择要关闭的应用")
                    }
                    Text(
                        "启用无障碍后会自动进入应用信息页并点击“强行停止”。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onOpenAccessibilitySettings) {
                        Text("打开无障碍设置")
                    }
                }

                ActionType.RANDOM_DELAY -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(
                            "最小秒数",
                            action.rangeStart,
                            "例如 3",
                            { onChange(action.copy(rangeStart = it)) },
                            Modifier.weight(1f)
                        )
                        NumberField(
                            "最大秒数",
                            action.rangeEnd,
                            "例如 18",
                            { onChange(action.copy(rangeEnd = it)) },
                            Modifier.weight(1f)
                        )
                    }
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
                        "振动时长(ms)",
                        action.durationMs,
                        "建议 300-1200",
                        { onChange(action.copy(durationMs = it)) },
                        Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun AppPickerDialog(
    context: Context,
    onDismiss: () -> Unit,
    onPick: (LaunchableApp) -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    val pickerState by produceState(initialValue = AppPickerUiState(), context) {
        value = AppPickerUiState(loading = true)
        value = AppPickerUiState(loading = false, apps = loadLaunchableApps(context))
    }
    val apps = pickerState.apps
    val filtered = remember(apps, keyword) {
        if (keyword.isBlank()) {
            apps
        } else {
            val term = keyword.trim().lowercase()
            apps.filter {
                it.label.lowercase().contains(term) || it.packageName.lowercase().contains(term)
            }
        }
    }
    val recentApps = remember(apps) { apps.filter { it.recentOrder != null }.take(6) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("选择应用")
                Text(
                    "直接搜索名称或包名，点一下即可绑定到动作",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("搜索应用") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
                if (pickerState.loading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.size(12.dp))
                        Text(
                            "正在读取可启动应用列表…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    if (recentApps.isNotEmpty()) {
                        Text(
                            "最近选择",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recentApps.forEach { app ->
                                AssistChip(
                                    onClick = { onPick(app) },
                                    label = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                        }
                    }
                    Text(
                        "找到 ${filtered.size} 个可启动应用",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            AppListItem(app = app, onClick = { onPick(app) })
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun AppListItem(app: LaunchableApp, onClick: () -> Unit) {
    val painter = remember(app.packageName) { app.icon?.toPainter() }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        app.label.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (app.recentOrder != null) {
                        AppTag("最近", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    if (app.useCount >= 2) {
                        AppTag("常用", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    AppTag(
                        if (app.isSystemApp) "系统应用" else "用户应用",
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun AppTag(label: String, containerColor: androidx.compose.ui.graphics.Color, textColor: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, color = textColor, style = MaterialTheme.typography.labelSmall)
    }
}

private fun defaultNotificationAction(id: Int) =
    EditableAction(id, ActionType.NOTIFICATION, title = "LuminaFlow", message = "任务已触发")

private fun defaultActionForType(id: Int, type: ActionType): EditableAction =
    when (type) {
        ActionType.NOTIFICATION -> defaultNotificationAction(id)
        ActionType.RANDOM_DELAY -> EditableAction(id, type, rangeStart = "3", rangeEnd = "15")
        ActionType.VIBRATE -> EditableAction(id, type, durationMs = "600")
        else -> EditableAction(id, type)
    }

private fun loadLaunchableApps(context: Context): List<LaunchableApp> {
    val packageManager = context.packageManager
    val prefs = context.getSharedPreferences("lumina_flow_app_picker", Context.MODE_PRIVATE)
    val recentPackages = prefs.getString("recent_packages", "").orEmpty()
        .split('|')
        .map { it.trim() }
        .filter { it.isNotBlank() }
    val recentOrderMap = recentPackages.withIndex().associate { it.value to it.index }
    val useCountMap = prefs.getString("usage_counts", "").orEmpty()
        .split('|')
        .mapNotNull {
            val parts = it.split('=')
            if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 0) else null
        }
        .toMap()
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(0)
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(intent, 0)
    }

    return resolveInfos
        .mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
            LaunchableApp(
                label = label.ifBlank { packageName },
                packageName = packageName,
                icon = runCatching { info.loadIcon(packageManager) }.getOrNull(),
                isSystemApp = (info.activityInfo?.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) ?: 0) != 0,
                useCount = useCountMap[packageName] ?: 0,
                recentOrder = recentOrderMap[packageName]
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(
            compareBy<LaunchableApp> { it.recentOrder ?: Int.MAX_VALUE }
                .thenByDescending { it.useCount }
                .thenBy { it.isSystemApp }
                .thenBy(String.CASE_INSENSITIVE_ORDER, LaunchableApp::label)
        )
}

private fun rememberAppSelection(context: Context, packageName: String) {
    val prefs = context.getSharedPreferences("lumina_flow_app_picker", Context.MODE_PRIVATE)
    val recent = prefs.getString("recent_packages", "").orEmpty()
        .split('|')
        .map { it.trim() }
        .filter { it.isNotBlank() && it != packageName }
        .toMutableList()
    recent.add(0, packageName)
    val trimmedRecent = recent.take(8)

    val counts = prefs.getString("usage_counts", "").orEmpty()
        .split('|')
        .mapNotNull {
            val parts = it.split('=')
            if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 0) else null
        }
        .associate { pair -> pair }
        .toMutableMap()
    counts[packageName] = (counts[packageName] ?: 0) + 1

    prefs.edit()
        .putString("recent_packages", trimmedRecent.joinToString("|"))
        .putString("usage_counts", counts.entries.joinToString("|") { entry -> "${entry.key}=${entry.value}" })
        .apply()
}

private fun openAccessibilitySettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun Drawable.toPainter(): Painter {
    if (this is BitmapDrawable && bitmap != null) {
        return BitmapPainter(bitmap.asImageBitmap())
    }
    val width = intrinsicWidth.takeIf { it > 0 } ?: 96
    val height = intrinsicHeight.takeIf { it > 0 } ?: 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return BitmapPainter(bitmap.asImageBitmap())
}

private fun buildEntity(
    original: AutomationEntity?,
    name: String,
    description: String,
    enabled: Boolean,
    triggerType: TriggerType,
    hour: Int,
    minute: Int,
    repeatUntilWindowEnd: Boolean,
    windowEndHour: Int,
    windowEndMinute: Int,
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
            durationMs = it.durationMs.toLongOrNull() ?: 600L,
            rangeStart = it.rangeStart.toLongOrNull() ?: 0L,
            rangeEnd = it.rangeEnd.toLongOrNull() ?: 0L
        )
    }
    if (builtActions.isEmpty()) return null

    val triggerValid = when (triggerType) {
        TriggerType.TIME -> true
        TriggerType.INTERVAL -> intervalMinutes.toIntOrNull()?.let { it > 0 } == true
        TriggerType.LOCATION -> latitude.toDoubleOrNull() != null && longitude.toDoubleOrNull() != null
    }
    if (!triggerValid) return null
    if (repeatUntilWindowEnd && triggerType != TriggerType.TIME) return null
    if (repeatUntilWindowEnd && (windowEndHour < hour || (windowEndHour == hour && windowEndMinute <= minute))) return null
    if (repeatUntilWindowEnd && builtActions.none { it.type == ActionType.RANDOM_DELAY }) return null

    return AutomationEntity(
        id = original?.id ?: 0L,
        name = name.trim(),
        description = description.trim(),
        triggerType = triggerType.value,
        hour = if (triggerType == TriggerType.TIME) hour else null,
        minute = if (triggerType == TriggerType.TIME) minute else null,
        daysOfWeek = if (triggerType == TriggerType.TIME) AutomationPlanner.encodeDays(selectedDays) else "",
        repeatUntilWindowEnd = triggerType == TriggerType.TIME && repeatUntilWindowEnd,
        windowEndHour = if (triggerType == TriggerType.TIME && repeatUntilWindowEnd) windowEndHour else null,
        windowEndMinute = if (triggerType == TriggerType.TIME && repeatUntilWindowEnd) windowEndMinute else null,
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
    repeatUntilWindowEnd: Boolean,
    windowEndHour: Int,
    windowEndMinute: Int,
    intervalMinutes: String
): String = when (triggerType) {
    TriggerType.TIME -> {
        if (repeatUntilWindowEnd) {
            "每天 %02d:%02d 启动，循环到 %02d:%02d".format(hour, minute, windowEndHour, windowEndMinute)
        } else {
            "每天 %02d:%02d".format(hour, minute)
        }
    }
    TriggerType.INTERVAL -> "每 ${intervalMinutes.ifBlank { "?" }} 分钟轮询一次"
    TriggerType.LOCATION -> "进入指定半径范围时触发"
}
