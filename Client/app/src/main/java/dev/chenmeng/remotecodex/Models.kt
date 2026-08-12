package dev.chenmeng.remotecodex

data class GoalInfo(
    val objective: String,
    val status: String,
    val tokensUsed: Long,
    val timeUsedSeconds: Long,
)
data class CodexTask(
    val id: String,
    val name: String,
    val preview: String,
    val cwd: String,
    val source: String,
    val state: String,
    val runtimeStatus: String,
    val updatedAt: Long,
    val isSubagent: Boolean,
    val agentNickname: String?,
    val agentRole: String?,
    val goal: GoalInfo?,
)

data class TaskSummary(
    val total: Int = 0,
    val running: Int = 0,
    val waiting: Int = 0,
    val errors: Int = 0,
    val subagents: Int = 0,
)

data class RemoteSnapshot(
    val generatedAt: Long,
    val summary: TaskSummary,
    val tasks: List<CodexTask>,
)
