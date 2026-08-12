package dev.chenmeng.remotecodex

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class RemoteCodexApi {
    fun load(endpoint: String): RemoteSnapshot {
        return parseSnapshot(getJson(endpoint, "/api/status"))
    }

    fun loadDetail(endpoint: String, taskId: String): TaskDetail {
        val encodedId = URLEncoder.encode(taskId, StandardCharsets.UTF_8.toString())
        return parseDetail(getJson(endpoint, "/api/tasks/$encodedId"))
    }

    fun submitPrompt(endpoint: String, taskId: String, text: String, mode: String) {
        val encodedId = encode(taskId)
        requestJson(
            endpoint,
            "/api/tasks/$encodedId/prompts",
            "POST",
            JSONObject().put("text", text).put("mode", mode).toString(),
        )
    }

    fun deleteQueuedPrompt(endpoint: String, taskId: String, promptId: String) {
        requestJson(endpoint, "/api/tasks/${encode(taskId)}/prompts/${encode(promptId)}", "DELETE")
    }

    fun interveneQueuedPrompt(endpoint: String, taskId: String, promptId: String) {
        requestJson(endpoint, "/api/tasks/${encode(taskId)}/prompts/${encode(promptId)}/intervene", "POST", "{}")
    }

    private fun getJson(endpoint: String, path: String): JSONObject {
        return requestJson(endpoint, path, "GET")
    }

    private fun requestJson(endpoint: String, path: String, method: String, body: String? = null): JSONObject {
        val connection = URL("${normalizeEndpoint(endpoint)}$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 4_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("Accept", "application/json")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        }

        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(body).optString("error") }.getOrNull()
                throw IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "HTTP $status")
            }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun normalizeEndpoint(value: String): String {
        var result = value.trim().removeSuffix("/")
        if (!result.startsWith("http://") && !result.startsWith("https://")) result = "http://$result"
        val url = URL(result)
        if (url.port == -1) result = result.replace(url.host, "${url.host}:8787")
        return result
    }

    private fun parseSnapshot(root: JSONObject): RemoteSnapshot {
        val summaryJson = root.getJSONObject("summary")
        val array = root.getJSONArray("tasks")
        val tasks = parseTasks(array)
        val projectsArray = root.optJSONArray("projects")
        val projects = buildList {
            if (projectsArray != null) for (index in 0 until projectsArray.length()) {
                val item = projectsArray.getJSONObject(index)
                val projectSummary = item.getJSONObject("summary")
                add(
                    CodexProject(
                        id = item.getString("id"),
                        name = item.optString("name", "未分类"),
                        path = item.optString("path"),
                        summary = ProjectSummary(
                            total = projectSummary.optInt("total"),
                            running = projectSummary.optInt("running"),
                            waiting = projectSummary.optInt("waiting"),
                            subagents = projectSummary.optInt("subagents"),
                        ),
                        tasks = parseTasks(item.getJSONArray("tasks")),
                    ),
                )
            }
        }
        return RemoteSnapshot(
            generatedAt = root.optLong("generatedAt"),
            summary = TaskSummary(
                total = summaryJson.optInt("total"),
                running = summaryJson.optInt("running"),
                waiting = summaryJson.optInt("waiting"),
                errors = summaryJson.optInt("errors"),
                subagents = summaryJson.optInt("subagents"),
            ),
            projects = projects,
            tasks = tasks,
        )
    }

    private fun parseTasks(array: org.json.JSONArray): List<CodexTask> = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(parseTask(item))
            }
        }

    private fun parseTask(item: JSONObject): CodexTask {
        val goalJson = item.optJSONObject("goal")
        return CodexTask(
            id = item.getString("id"),
            name = item.optString("name", "Untitled task"),
            preview = item.optString("preview"),
            cwd = item.optString("cwd"),
            source = item.optString("source", "unknown"),
            state = item.optString("state", "idle"),
            runtimeStatus = item.optString("runtimeStatus", "unknown"),
            updatedAt = item.optLong("updatedAt"),
            isSubagent = item.optBoolean("isSubagent"),
            parentThreadId = item.optNullableString("parentThreadId"),
            agentNickname = item.optNullableString("agentNickname"),
            agentRole = item.optNullableString("agentRole"),
            agentPath = item.optNullableString("agentPath"),
            subagentDepth = item.optInt("subagentDepth"),
            goal = goalJson?.let {
                GoalInfo(
                    objective = it.optString("objective"),
                    status = it.optString("status"),
                    tokensUsed = it.optLong("tokensUsed"),
                    timeUsedSeconds = it.optLong("timeUsedSeconds"),
                )
            },
            subagents = item.optJSONArray("subagents")?.let(::parseTasks).orEmpty(),
        )
    }

    private fun parseDetail(root: JSONObject): TaskDetail {
        val executionJson = root.getJSONObject("execution")
        val contextJson = root.getJSONObject("context")
        val projectJson = root.getJSONObject("project")
        val planJson = root.optJSONObject("plan")
        val gitJson = contextJson.optJSONObject("gitInfo")
        return TaskDetail(
            generatedAt = root.optLong("generatedAt"),
            task = parseTask(root.getJSONObject("task")),
            projectName = projectJson.optString("name", "未分类"),
            projectPath = projectJson.optString("path"),
            execution = ExecutionInfo(
                currentTurnStatus = executionJson.optNullableString("currentTurnStatus"),
                turnCount = executionJson.optInt("turnCount"),
                itemCount = executionJson.optInt("itemCount"),
                startedAt = executionJson.optNullableLong("startedAt"),
                completedAt = executionJson.optNullableLong("completedAt"),
                durationMs = executionJson.optNullableLong("durationMs"),
            ),
            plan = planJson?.let {
                TaskPlan(
                    explanation = it.optNullableString("explanation"),
                    source = it.optString("source", "unknown"),
                    steps = it.getJSONArray("steps").mapObjects { step ->
                        PlanStep(step = step.optString("step"), status = step.optString("status", "pending"))
                    },
                )
            },
            context = TaskContext(
                cwd = contextJson.optString("cwd"),
                source = contextJson.optString("source", "unknown"),
                modelProvider = contextJson.optString("modelProvider", "unknown"),
                cliVersion = contextJson.optString("cliVersion"),
                gitInfo = gitJson?.let {
                    GitInfo(
                        branch = it.optNullableString("branch"),
                        sha = it.optNullableString("sha"),
                        originUrl = it.optNullableString("originUrl"),
                    )
                },
                createdAt = contextJson.optLong("createdAt"),
                updatedAt = contextJson.optLong("updatedAt"),
                userMessages = contextJson.getJSONArray("userMessages").mapObjects {
                    UserContextMessage(it.getString("id"), it.optString("turnId"), it.optString("text"))
                },
                compactionCount = contextJson.optInt("compactionCount"),
            ),
            modelOutputs = root.getJSONArray("modelOutputs").mapObjects {
                ModelOutput(
                    id = it.getString("id"),
                    turnId = it.optString("turnId"),
                    text = it.optString("text"),
                    phase = it.optNullableString("phase"),
                )
            },
            conversation = root.optJSONArray("conversation")?.mapObjects {
                ConversationMessage(
                    id = it.getString("id"),
                    turnId = it.optString("turnId"),
                    role = it.optString("role", "assistant"),
                    text = it.optString("text"),
                    phase = it.optNullableString("phase"),
                )
            }.orEmpty(),
            activities = root.getJSONArray("activities").mapObjects {
                TaskActivity(
                    id = it.getString("id"),
                    type = it.optString("type"),
                    title = it.optString("title"),
                    status = it.optString("status"),
                    detail = it.optNullableString("detail"),
                )
            },
            promptQueue = root.optJSONArray("promptQueue")?.mapObjects {
                QueuedPrompt(
                    id = it.getString("id"),
                    text = it.optString("text"),
                    createdAt = it.optLong("createdAt"),
                )
            }.orEmpty(),
            subagents = parseTasks(root.getJSONArray("subagents")),
        )
    }
}

private fun JSONObject.optNullableString(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

private fun JSONObject.optNullableLong(name: String): Long? =
    if (isNull(name) || !has(name)) null else optLong(name)

private inline fun <T> org.json.JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    buildList { for (index in 0 until length()) add(transform(getJSONObject(index))) }
