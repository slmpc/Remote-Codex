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

data class ProjectSummary(
    val total: Int = 0,
    val running: Int = 0,
    val waiting: Int = 0,
    val subagents: Int = 0,
)

data class CodexProject(
    val id: String,
    val name: String,
    val path: String,
    val summary: ProjectSummary,
    val tasks: List<CodexTask>,
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
    val projects: List<CodexProject>,
    val tasks: List<CodexTask>,
)

data class PlanStep(val step: String, val status: String)

data class TaskPlan(
    val explanation: String?,
    val steps: List<PlanStep>,
    val source: String,
)

data class ExecutionInfo(
    val currentTurnStatus: String?,
    val turnCount: Int,
    val itemCount: Int,
    val startedAt: Long?,
    val completedAt: Long?,
    val durationMs: Long?,
)

data class UserContextMessage(val id: String, val turnId: String, val text: String)

data class ModelOutput(
    val id: String,
    val turnId: String,
    val text: String,
    val phase: String?,
)

data class TaskActivity(
    val id: String,
    val type: String,
    val title: String,
    val status: String,
    val detail: String?,
)

data class GitInfo(val branch: String?, val sha: String?, val originUrl: String?)

data class TaskContext(
    val cwd: String,
    val source: String,
    val modelProvider: String,
    val cliVersion: String,
    val gitInfo: GitInfo?,
    val createdAt: Long,
    val updatedAt: Long,
    val userMessages: List<UserContextMessage>,
    val compactionCount: Int,
)

data class TaskDetail(
    val generatedAt: Long,
    val task: CodexTask,
    val projectName: String,
    val projectPath: String,
    val execution: ExecutionInfo,
    val plan: TaskPlan?,
    val context: TaskContext,
    val modelOutputs: List<ModelOutput>,
    val activities: List<TaskActivity>,
    val subagents: List<CodexTask>,
)
