package com.lumina.flow.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.lumina.flow.automation.AutomationJsonCodec
import com.lumina.flow.automation.AutomationPlanner
import com.lumina.flow.data.AutomationEntity
import com.lumina.flow.model.ActionType
import com.lumina.flow.model.AutomationActionConfig
import com.lumina.flow.model.AutomationConditions
import com.lumina.flow.model.TriggerType
import com.lumina.flow.viewmodel.AutomationViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: AutomationViewModel = hiltViewModel()) {
    val automations by viewModel.automations.collectAsState()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<AutomationEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var exportContent by remember { mutableStateOf<String?>(null) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("LuminaFlow", fontWeight = FontWeight.SemiBold)
                        Text(
                            "可配置自动化任务中心",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "导入")
                    }
                    IconButton(onClick = {
                        viewModel.exportToYaml { message, yaml ->
                            exportContent = yaml
                            toast(message)
                        }
                    }) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "导出")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                showEditor = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "添加任务")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Dashboard(automations) }
            item {
                TemplateSection { entity ->
                    editing = entity
                    showEditor = true
                }
            }
            if (automations.isEmpty()) {
                item { EmptyState() }
            } else {
                items(automations, key = { it.id }) { item ->
                    AutomationCard(
                        entity = item,
                        onToggle = { enabled -> viewModel.toggleEnabled(item, enabled) },
                        onRunNow = { viewModel.runNow(item, ::toast) },
                        onEdit = {
                            editing = item
                            showEditor = true
                        },
                        onDelete = {
                            viewModel.delete(item)
                            toast("任务已删除")
                        }
                    )
                }
            }
        }
    }

    if (showEditor) {
        AddAutomationSheet(
            entity = editing,
            onDismiss = { showEditor = false },
            onSave = { entity ->
                viewModel.save(entity) { message ->
                    toast(message)
                    showEditor = false
                }
            },
            onTestRun = { entity ->
                viewModel.testRunDraft(entity, ::toast)
            }
        )
    }

    if (showImportDialog) {
        RemoteImportDialog(
            onDismiss = { showImportDialog = false },
            onImport = { url ->
                viewModel.importFromUrl(url) { message ->
                    toast(message)
                    showImportDialog = false
                }
            }
        )
    }

    exportContent?.let { yaml ->
        AlertDialog(
            onDismissRequest = { exportContent = null },
            title = { Text("导出 YAML") },
            text = {
                OutlinedTextField(
                    value = yaml,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    readOnly = true
                )
            },
            confirmButton = { TextButton(onClick = { exportContent = null }) { Text("关闭") } }
        )
    }
}

@Composable
private fun Dashboard(automations: List<AutomationEntity>) {
    val enabledCount = automations.count { it.enabled }
    val scheduledCount = automations.count { it.enabled && it.nextRunAt != null }
    val actionCount = automations.sumOf { AutomationJsonCodec.decodeActions(it.actionsJson).size }

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("控制台", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("已启用", enabledCount.toString(), Modifier.weight(1f))
                MetricCard("待调度", scheduledCount.toString(), Modifier.weight(1f))
                MetricCard("动作数", actionCount.toString(), Modifier.weight(1f))
            }
            Text(
                "保存后自动排程；“立即执行”会单独入队，不影响原计划。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateSection(onUseTemplate: (AutomationEntity) -> Unit) {
    val templates = remember {
        listOf(
            AutomationEntity(
                name = "晨间启动",
                description = "工作日 08:30 弹提醒并打开打卡页",
                triggerType = TriggerType.TIME.value,
                hour = 8,
                minute = 30,
                daysOfWeek = AutomationPlanner.encodeDays(setOf(2, 3, 4, 5, 6)),
                actionsJson = AutomationJsonCodec.encodeActions(
                    listOf(
                        AutomationActionConfig(ActionType.NOTIFICATION, "早上好", "准备开始一天的任务"),
                        AutomationActionConfig(ActionType.OPEN_URL, target = "https://example.com/checkin")
                    )
                )
            ),
            AutomationEntity(
                name = "喝水提醒",
                description = "每 90 分钟提醒喝水一次",
                triggerType = TriggerType.INTERVAL.value,
                intervalMinutes = 90,
                actionsJson = AutomationJsonCodec.encodeActions(
                    listOf(
                        AutomationActionConfig(ActionType.NOTIFICATION, "补水提醒", "起来走两步，喝点水"),
                        AutomationActionConfig(ActionType.VIBRATE, durationMs = 800L)
                    )
                )
            ),
            AutomationEntity(
                name = "随机开关应用窗口",
                description = "08:30 到 09:00 内随机等待后打开应用，再随机等待后退回桌面",
                triggerType = TriggerType.TIME.value,
                hour = 8,
                minute = 30,
                repeatUntilWindowEnd = true,
                windowEndHour = 9,
                windowEndMinute = 0,
                actionsJson = AutomationJsonCodec.encodeActions(
                    listOf(
                        AutomationActionConfig(ActionType.RANDOM_DELAY, rangeStart = 5, rangeEnd = 25),
                        AutomationActionConfig(ActionType.OPEN_APP, target = "com.tencent.mm"),
                        AutomationActionConfig(ActionType.RANDOM_DELAY, rangeStart = 6, rangeEnd = 20),
                        AutomationActionConfig(ActionType.CLOSE_APP)
                    )
                )
            ),
            AutomationEntity(
                name = "下班收尾",
                description = "18:45 复制日报模板到剪贴板",
                triggerType = TriggerType.TIME.value,
                hour = 18,
                minute = 45,
                actionsJson = AutomationJsonCodec.encodeActions(
                    listOf(
                        AutomationActionConfig(ActionType.CLIPBOARD, message = "今天完成：\n1.\n2.\n3."),
                        AutomationActionConfig(ActionType.NOTIFICATION, "日报模板已准备", "可以直接粘贴发送")
                    )
                ),
                conditionsJson = AutomationJsonCodec.encodeConditions(
                    AutomationConditions(minimumBattery = 20)
                )
            )
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("常用模板", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            templates.forEach { template ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            template.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f)
                        )
                        TextButton(onClick = { onUseTemplate(template.copy(id = 0L)) }) {
                            Text("套用模板")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutomationCard(
    entity: AutomationEntity,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val actions = remember(entity.actionsJson) { AutomationJsonCodec.decodeActions(entity.actionsJson) }
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (entity.enabled) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entity.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (entity.description.isNotBlank()) {
                        Text(
                            entity.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(checked = entity.enabled, onCheckedChange = onToggle)
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(triggerLabel(entity)) })
                AssistChip(onClick = {}, label = { Text("动作 ${actions.size}") })
                actions.take(2).forEach { action ->
                    FilterChip(selected = false, onClick = {}, label = { Text(action.type.label) })
                }
            }

            InfoLine("下次执行", entity.nextRunAt?.let(::formatDateTime) ?: missingScheduleLabel(entity))
            InfoLine("最近结果", entity.lastResult)
            InfoLine("条件", conditionLabel(entity.conditionsJson))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRunNow) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("立即执行")
                }
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Text("编辑")
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyState() {
    Card(shape = RoundedCornerShape(30.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("还没有任务", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "点右下角创建，或者先套用上面的模板。现在这版已经不需要你手写 JSON。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun triggerLabel(entity: AutomationEntity): String =
    when (TriggerType.fromValue(entity.triggerType)) {
        TriggerType.TIME -> {
            val time = "%02d:%02d".format(entity.hour ?: 0, entity.minute ?: 0)
            val days = AutomationPlanner.decodeDays(entity.daysOfWeek)
            val prefix = if (days.isEmpty()) "每日 $time" else "${daysLabel(days)} $time"
            if (entity.repeatUntilWindowEnd && entity.windowEndHour != null && entity.windowEndMinute != null) {
                "$prefix -> %02d:%02d 循环结束".format(entity.windowEndHour, entity.windowEndMinute)
            } else {
                prefix
            }
        }
        TriggerType.INTERVAL -> "每 ${entity.intervalMinutes ?: 0} 分钟"
        TriggerType.LOCATION -> "地理围栏"
    }

private fun conditionLabel(raw: String): String {
    val conditions = AutomationJsonCodec.decodeConditions(raw)
    val items = buildList {
        if (conditions.requireCharging) add("充电中")
        if (conditions.wifiOnly) add("仅 Wi-Fi")
        conditions.minimumBattery?.let { add("电量 >= $it%") }
    }
    return if (items.isEmpty()) "无附加条件" else items.joinToString(" / ")
}

private fun missingScheduleLabel(entity: AutomationEntity): String =
    if (TriggerType.fromValue(entity.triggerType) == TriggerType.LOCATION) {
        "已配置，等待围栏能力接入"
    } else {
        "当前配置不足以排程"
    }

private fun formatDateTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(timestamp)

private fun daysLabel(days: Set<Int>): String = when (days.sorted()) {
    listOf(2, 3, 4, 5, 6) -> "工作日"
    listOf(1, 7) -> "周末"
    else -> days.sorted().joinToString("") { day ->
        when (day) {
            1 -> "日"
            2 -> "一"
            3 -> "二"
            4 -> "三"
            5 -> "四"
            6 -> "五"
            else -> "六"
        }
    }.let { "周$it" }
}
