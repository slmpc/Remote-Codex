package dev.chenmeng.remotecodex

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RemoteCodexApi {
    fun load(endpoint: String, token: String): RemoteSnapshot {
        val connection = URL("${normalizeEndpoint(endpoint)}/api/status").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 4_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("Accept", "application/json")
        if (token.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer ${token.trim()}")

        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(body).optString("error") }.getOrNull()
                throw IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "HTTP $status")
            }
            parseSnapshot(JSONObject(body))
        } finally {
            connection.disconnect()
        }
    }

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
        val tasks = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val goalJson = item.optJSONObject("goal")
                add(
                    CodexTask(
                        id = item.getString("id"),
                        name = item.optString("name", "Untitled task"),
                        preview = item.optString("preview"),
                        cwd = item.optString("cwd"),
                        source = item.optString("source", "unknown"),
                        state = item.optString("state", "idle"),
                        runtimeStatus = item.optString("runtimeStatus", "unknown"),
                        updatedAt = item.optLong("updatedAt"),
                        isSubagent = item.optBoolean("isSubagent"),
                        agentNickname = item.optNullableString("agentNickname"),
                        agentRole = item.optNullableString("agentRole"),
                        goal = goalJson?.let {
                            GoalInfo(
                                objective = it.optString("objective"),
                                status = it.optString("status"),
                                tokensUsed = it.optLong("tokensUsed"),
                                timeUsedSeconds = it.optLong("timeUsedSeconds"),
                            )
                        },
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
            tasks = tasks,
        )
    }
}

private fun JSONObject.optNullableString(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
