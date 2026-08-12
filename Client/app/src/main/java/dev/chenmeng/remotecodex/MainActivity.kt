package dev.chenmeng.remotecodex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RemoteCodexTheme { RemoteCodexScreen() } }
    }
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF17212B),
    onPrimary = Color.White,
    secondary = Color(0xFF087F5B),
    tertiary = Color(0xFFD97706),
    background = Color(0xFFF5F7F8),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9EDF0),
    error = Color(0xFFB42318),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE8EEF2),
    onPrimary = Color(0xFF17212B),
    secondary = Color(0xFF52C79B),
    tertiary = Color(0xFFF2B84B),
    background = Color(0xFF101417),
    surface = Color(0xFF1A2024),
    surfaceVariant = Color(0xFF283036),
    error = Color(0xFFFF8A80),
)

@Composable
private fun RemoteCodexTheme(content: @Composable () -> Unit) {
    val dark = (LocalContext.current.resources.configuration.uiMode and 0x30) == 0x20
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteCodexScreen(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.selectedTaskId != null, onBack = viewModel::closeTask)

    if (state.selectedTaskId != null) {
        TaskDetailScreen(
            state = state,
            onBack = viewModel::closeTask,
            onRefresh = viewModel::refreshNow,
            onTaskClick = viewModel::openTask,
        )
    } else {
        ProjectListScreen(
            state = state,
            onEndpointChange = viewModel::updateEndpoint,
            onTokenChange = viewModel::updateToken,
            onConnect = viewModel::connect,
            onRefresh = viewModel::refreshNow,
            onTaskClick = viewModel::openTask,
            onHideIdleTasksChange = viewModel::setHideIdleTasks,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectListScreen(
    state: MainUiState,
    onEndpointChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onTaskClick: (String) -> Unit,
    onHideIdleTasksChange: (Boolean) -> Unit,
) {
    val expandedTasks = remember { mutableStateMapOf<String, Boolean>() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { ConnectionTitle(state.connected) },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.connecting) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                ConnectionPanel(state, onEndpointChange, onTokenChange, onConnect)
            }
            state.snapshot?.let { snapshot ->
                item { SummaryBand(snapshot.summary, snapshot.generatedAt) }
                item {
                    FilterBand(
                        hideIdleTasks = state.hideIdleTasks,
                        onHideIdleTasksChange = onHideIdleTasksChange,
                    )
                }
                snapshot.projects.forEach { project ->
                    val visibleTasks = project.tasks.filter { !state.hideIdleTasks || it.hasActiveBranch() }
                    if (visibleTasks.isNotEmpty()) {
                        item(key = "project-${project.id}") { ProjectHeader(project) }
                        visibleTasks.forEach { task ->
                            taskTreeItems(
                                task = task,
                                depth = 0,
                                hideIdleTasks = state.hideIdleTasks,
                                expandedTasks = expandedTasks,
                                onTaskClick = onTaskClick,
                            )
                        }
                    }
                }
            }
            if (state.connecting) item { LoadingBand() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDetailScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onTaskClick: (String) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        DetailTab("计划", Icons.Default.AccountTree),
        DetailTab("上下文", Icons.Default.Description),
        DetailTab("输出", Icons.Default.SmartToy),
        DetailTab("活动", Icons.Default.Terminal),
    )
    val detail = state.detail

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                detail?.task?.name ?: "Task 详情",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            detail?.let {
                                Text(
                                    it.projectName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = onRefresh, enabled = !state.detailLoading) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新详情")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                if (detail != null) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.background,
                        edgePadding = 8.dp,
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                text = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        when {
            detail != null -> when (selectedTab) {
                0 -> PlanTab(detail, Modifier.padding(padding), onTaskClick)
                1 -> ContextTab(detail, Modifier.padding(padding))
                2 -> OutputTab(detail, Modifier.padding(padding))
                else -> ActivityTab(detail, Modifier.padding(padding))
            }
            state.detailLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            else -> ErrorState(state.detailError ?: "无法获取 Task 详情", Modifier.padding(padding), onRefresh)
        }
    }
}

private data class DetailTab(val label: String, val icon: ImageVector)

@Composable
private fun ConnectionTitle(connected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(9.dp)
                .background(if (connected) MaterialTheme.colorScheme.secondary else Color(0xFF98A2B3), CircleShape),
        )
        Spacer(Modifier.size(10.dp))
        Text("Remote Codex", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ConnectionPanel(
    state: MainUiState,
    onEndpointChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = state.endpoint,
            onValueChange = onEndpointChange,
            label = { Text("电脑 IP") },
            placeholder = { Text("192.168.1.10:8787") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.token,
            onValueChange = onTokenChange,
            label = { Text("访问令牌（可选）") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.error ?: if (state.connected) "已连接" else "未连接",
                color = when {
                    state.error != null -> MaterialTheme.colorScheme.error
                    state.connected -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            Button(onClick = onConnect, enabled = !state.connecting) {
                Text(if (state.connected) "重新连接" else "连接")
            }
        }
    }
}

@Composable
private fun SummaryBand(summary: TaskSummary, generatedAt: Long) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("项目与 Task", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(formatTime(generatedAt / 1000), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SummaryValue(summary.running, "运行", MaterialTheme.colorScheme.secondary)
            SummaryValue(summary.waiting, "等待", MaterialTheme.colorScheme.tertiary)
            SummaryValue(summary.subagents, "Subagent", MaterialTheme.colorScheme.primary)
            SummaryValue(summary.total, "全部", MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SummaryValue(value: Int, label: String, color: Color) {
    Column {
        Text(value.toString(), color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FilterBand(hideIdleTasks: Boolean, onHideIdleTasksChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("隐藏空闲 Task", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = hideIdleTasks,
            onCheckedChange = onHideIdleTasksChange,
        )
    }
}

private fun CodexTask.hasActiveBranch(): Boolean =
    state != "idle" || subagents.any(CodexTask::hasActiveBranch)

private fun androidx.compose.foundation.lazy.LazyListScope.taskTreeItems(
    task: CodexTask,
    depth: Int,
    hideIdleTasks: Boolean,
    expandedTasks: MutableMap<String, Boolean>,
    onTaskClick: (String) -> Unit,
) {
    val children = task.subagents.filter { !hideIdleTasks || it.hasActiveBranch() }
    item(key = "task-${task.id}") {
        TaskCard(
            task = task,
            depth = depth,
            expanded = expandedTasks[task.id] == true,
            visibleChildCount = children.size,
            onExpand = { expandedTasks[task.id] = expandedTasks[task.id] != true },
            onClick = { onTaskClick(task.id) },
        )
    }
    if (expandedTasks[task.id] == true) {
        children.forEach { child ->
            taskTreeItems(child, depth + 1, hideIdleTasks, expandedTasks, onTaskClick)
        }
    }
}

@Composable
private fun ProjectHeader(project: CodexProject) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(9.dp))
            Text(project.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(
                "${project.summary.total} Tasks",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (project.path.isNotBlank()) {
            Text(
                project.path,
                modifier = Modifier.padding(start = 29.dp, top = 3.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TaskCard(
    task: CodexTask,
    onClick: () -> Unit,
    depth: Int = 0,
    expanded: Boolean = false,
    visibleChildCount: Int = 0,
    onExpand: () -> Unit = {},
) {
    val (statusLabel, statusColor) = taskStatus(task.state)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (16 + depth.coerceAtMost(4) * 18).dp, end = 16.dp, top = 5.dp, bottom = 5.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(statusColor)
            Spacer(Modifier.size(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.isSubagent) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = "Subagent",
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(Modifier.size(6.dp))
                    }
                    Text(
                        task.agentNickname?.takeIf { task.isSubagent } ?: task.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    task.goal?.objective ?: task.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(statusLabel, color = statusColor, style = MaterialTheme.typography.labelSmall)
                    Text(formatTime(task.updatedAt), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (visibleChildCount > 0) {
                IconButton(onClick = onExpand) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起 Subagent" else "展开 $visibleChildCount 个 Subagent",
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "查看详情",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlanTab(detail: TaskDetail, modifier: Modifier, onTaskClick: (String) -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { ExecutionHeader(detail) }
        item { SectionTitle("当前 Plan") }
        detail.plan?.let { plan ->
            plan.explanation?.takeIf { it.isNotBlank() }?.let { explanation ->
                item { BodyBand(explanation) }
            }
            items(plan.steps) { step -> PlanStepRow(step) }
        } ?: item { EmptyBand("这个 Task 尚未创建 Plan") }
        detail.task.goal?.let { goal ->
            item { SectionTitle("Goal") }
            item {
                KeyValueBand(
                    listOf(
                        "状态" to goal.status,
                        "目标" to goal.objective,
                        "Tokens" to goal.tokensUsed.toString(),
                        "耗时" to formatDuration(goal.timeUsedSeconds * 1000),
                    ),
                )
            }
        }
        if (detail.subagents.isNotEmpty()) {
            item { SectionTitle("Subagents") }
            items(detail.subagents, key = { it.id }) { task ->
                TaskCard(task, onClick = { onTaskClick(task.id) })
            }
        }
    }
}

@Composable
private fun ExecutionHeader(detail: TaskDetail) {
    val (statusLabel, statusColor) = taskStatus(detail.task.state)
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(statusColor)
            Spacer(Modifier.size(9.dp))
            Text(statusLabel, color = statusColor, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                "${detail.execution.turnCount} Turns · ${detail.execution.itemCount} Items",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            detail.task.goal?.objective ?: detail.task.preview,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        detail.execution.durationMs?.let {
            Text(
                "当前 Turn ${detail.execution.currentTurnStatus.orEmpty()} · ${formatDuration(it)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlanStepRow(step: PlanStep) {
    val icon: ImageVector
    val color: Color
    when (step.status) {
        "completed" -> {
            icon = Icons.Default.CheckCircle
            color = MaterialTheme.colorScheme.secondary
        }
        "inProgress" -> {
            icon = Icons.Default.Pending
            color = MaterialTheme.colorScheme.tertiary
        }
        else -> {
            icon = Icons.Default.RadioButtonUnchecked
            color = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = step.status, tint = color, modifier = Modifier.size(21.dp))
        Spacer(Modifier.size(12.dp))
        Text(step.step, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun ContextTab(detail: TaskDetail, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { SectionTitle("运行上下文") }
        item {
            KeyValueBand(
                listOf(
                    "项目" to detail.projectName,
                    "工作目录" to detail.context.cwd,
                    "来源" to detail.context.source,
                    "模型提供方" to detail.context.modelProvider,
                    "Codex CLI" to detail.context.cliVersion,
                    "上下文压缩" to detail.context.compactionCount.toString(),
                    "更新时间" to formatTime(detail.context.updatedAt),
                ),
            )
        }
        detail.context.gitInfo?.let { git ->
            item { SectionTitle("Git") }
            item {
                KeyValueBand(
                    listOfNotNull(
                        git.branch?.let { "分支" to it },
                        git.sha?.let { "Commit" to it.take(12) },
                        git.originUrl?.let { "远端" to it },
                    ),
                )
            }
        }
        item { SectionTitle("用户上下文") }
        if (detail.context.userMessages.isEmpty()) item { EmptyBand("没有可见的用户上下文") }
        items(detail.context.userMessages, key = { it.id }) { message ->
            MessageBand(label = "USER", text = message.text, accent = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun OutputTab(detail: TaskDetail, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            SectionTitle("模型输出 · ${detail.modelOutputs.size}")
        }
        if (detail.modelOutputs.isEmpty()) item { EmptyBand("模型还没有输出") }
        items(detail.modelOutputs, key = { it.id }) { output ->
            MessageBand(
                label = when (output.phase) {
                    "commentary" -> "PROGRESS"
                    "final_answer" -> "FINAL"
                    else -> "ASSISTANT"
                },
                text = output.text,
                accent = if (output.phase == "final_answer") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun ActivityTab(detail: TaskDetail, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { SectionTitle("工具活动 · ${detail.activities.size}") }
        if (detail.activities.isEmpty()) item { EmptyBand("没有工具活动") }
        items(detail.activities, key = { it.id }) { activity -> ActivityRow(activity) }
    }
}

@Composable
private fun ActivityRow(activity: TaskActivity) {
    val icon = when (activity.type) {
        "commandExecution" -> Icons.Default.Terminal
        "fileChange" -> Icons.Default.Description
        "collabAgentToolCall" -> Icons.Default.SmartToy
        else -> Icons.Default.AccountTree
    }
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = activity.type, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(11.dp))
            Text(activity.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.size(8.dp))
            Text(activity.status, style = MaterialTheme.typography.labelSmall, color = statusColor(activity.status))
        }
        activity.detail?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                modifier = Modifier.padding(start = 31.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 12,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun BodyBand(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun KeyValueBand(values: List<Pair<String, String>>) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        values.forEachIndexed { index, (label, value) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.Top) {
                Text(
                    label,
                    modifier = Modifier.weight(0.32f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    value,
                    modifier = Modifier.weight(0.68f),
                    style = MaterialTheme.typography.bodySmall,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (index < values.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

@Composable
private fun MessageBand(label: String, text: String, accent: Color) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(label, color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun EmptyBand(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(24.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun ErrorState(message: String, modifier: Modifier, onRetry: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun LoadingBand() {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(Modifier.size(9.dp).background(color, CircleShape))
}

@Composable
private fun taskStatus(state: String): Pair<String, Color> = when (state) {
    "running" -> "运行中" to MaterialTheme.colorScheme.secondary
    "waiting" -> "等待" to MaterialTheme.colorScheme.tertiary
    "error" -> "错误" to MaterialTheme.colorScheme.error
    else -> "空闲" to Color(0xFF667085)
}

@Composable
private fun statusColor(status: String): Color = when (status.lowercase()) {
    "completed", "success" -> MaterialTheme.colorScheme.secondary
    "failed", "error", "declined" -> MaterialTheme.colorScheme.error
    "inprogress", "in_progress", "running" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun formatTime(epochSeconds: Long): String {
    if (epochSeconds <= 0) return ""
    return DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochSecond(epochSeconds))
}

private fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}
