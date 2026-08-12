package dev.chenmeng.remotecodex

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    error = Color(0xFFB42318),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE8EEF2),
    onPrimary = Color(0xFF17212B),
    secondary = Color(0xFF52C79B),
    tertiary = Color(0xFFF2B84B),
    background = Color(0xFF101417),
    surface = Color(0xFF1A2024),
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .background(
                                    if (state.connected) MaterialTheme.colorScheme.secondary else Color(0xFF98A2B3),
                                    CircleShape,
                                ),
                        )
                        Spacer(Modifier.size(10.dp))
                        Text("Remote Codex", fontWeight = FontWeight.SemiBold)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshNow, enabled = !state.connecting) {
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
                ConnectionPanel(
                    state = state,
                    onEndpointChange = viewModel::updateEndpoint,
                    onTokenChange = viewModel::updateToken,
                    onConnect = viewModel::connect,
                )
            }
            state.snapshot?.let { snapshot ->
                item { SummaryBand(snapshot.summary, snapshot.generatedAt) }
                items(snapshot.tasks, key = { it.id }) { task ->
                    TaskCard(task)
                }
            }
            if (state.connecting) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                }
            }
        }
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
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
            Text("任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
private fun TaskCard(task: CodexTask) {
    val (statusLabel, statusColor) = when (task.state) {
        "running" -> "运行中" to MaterialTheme.colorScheme.secondary
        "waiting" -> "等待" to MaterialTheme.colorScheme.tertiary
        "error" -> "错误" to MaterialTheme.colorScheme.error
        else -> "空闲" to Color(0xFF667085)
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (task.isSubagent) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = "Subagent",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.size(7.dp))
                }
                Text(
                    task.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(10.dp))
                Text(statusLabel, color = statusColor, style = MaterialTheme.typography.labelMedium)
            }
            task.goal?.let {
                Text(
                    it.objective,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            } ?: Text(
                task.preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    task.agentNickname ?: task.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatTime(task.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatTime(epochSeconds: Long): String {
    if (epochSeconds <= 0) return ""
    return DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochSecond(epochSeconds))
}
