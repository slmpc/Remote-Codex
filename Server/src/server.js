import http from "node:http";
import os from "node:os";
import { CodexAppServer } from "./codex-client.js";
import { DesktopCodexClient } from "./desktop-codex-client.js";
import { TaskService } from "./task-service.js";

const host = process.env.REMOTE_CODEX_HOST ?? "0.0.0.0";
const port = Number(process.env.REMOTE_CODEX_PORT ?? 8787);
const version = "1.3.1";
const clients = new Set();
const taskService = new TaskService(new CodexAppServer(), { desktopCodex: new DesktopCodexClient() });

function localAddresses() {
  return Object.values(os.networkInterfaces())
    .flat()
    .filter((item) => item?.family === "IPv4" && !item.internal)
    .map((item) => item.address);
}
function writeJson(response, status, value) {
  const body = JSON.stringify(value);
  response.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(body),
    "Cache-Control": "no-store",
  });
  response.end(body);
}

async function readJson(request) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > 64 * 1024) throw Object.assign(new Error("请求内容过大"), { statusCode: 413 });
    chunks.push(chunk);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8") || "{}");
  } catch {
    throw Object.assign(new Error("JSON 格式无效"), { statusCode: 400 });
  }
}

function writeError(response, error) {
  return writeJson(response, error.statusCode ?? 503, { error: error.message });
}

function broadcast(snapshot) {
  const frame = `event: status\ndata: ${JSON.stringify(snapshot)}\n\n`;
  for (const response of clients) response.write(frame);
}

taskService.on("snapshot", broadcast);
taskService.on("error", (error) => console.error(error));

const server = http.createServer(async (request, response) => {
  const url = new URL(request.url, `http://${request.headers.host ?? "localhost"}`);

  if (request.method === "GET" && url.pathname === "/healthz") {
    return writeJson(response, taskService.snapshot ? 200 : 503, {
      ok: Boolean(taskService.snapshot),
      service: "remote-codex",
      version,
    });
  }

  if (request.method === "GET" && url.pathname === "/api/status") {
    try {
      const snapshot = await taskService.refresh();
      return writeJson(response, 200, snapshot);
    } catch (error) {
      return writeJson(response, 503, { error: error.message });
    }
  }

  const taskDetailMatch = url.pathname.match(/^\/api\/tasks\/([^/]+)$/);
  if (request.method === "GET" && taskDetailMatch) {
    try {
      const detail = await taskService.detail(decodeURIComponent(taskDetailMatch[1]));
      return detail
        ? writeJson(response, 200, detail)
        : writeJson(response, 404, { error: "Task not found" });
    } catch (error) {
      return writeJson(response, 503, { error: error.message });
    }
  }

  const promptMatch = url.pathname.match(/^\/api\/tasks\/([^/]+)\/prompts$/);
  if (request.method === "POST" && promptMatch) {
    try {
      const body = await readJson(request);
      const result = await taskService.submitPrompt(
        decodeURIComponent(promptMatch[1]),
        body.text,
        body.mode,
      );
      return writeJson(response, 200, result);
    } catch (error) {
      return writeError(response, error);
    }
  }

  const queuedPromptMatch = url.pathname.match(/^\/api\/tasks\/([^/]+)\/prompts\/([^/]+)$/);
  if (request.method === "DELETE" && queuedPromptMatch) {
    try {
      const result = await taskService.removeQueuedPrompt(
        decodeURIComponent(queuedPromptMatch[1]),
        decodeURIComponent(queuedPromptMatch[2]),
      );
      return writeJson(response, 200, result);
    } catch (error) {
      return writeError(response, error);
    }
  }

  const intervenePromptMatch = url.pathname.match(/^\/api\/tasks\/([^/]+)\/prompts\/([^/]+)\/intervene$/);
  if (request.method === "POST" && intervenePromptMatch) {
    try {
      const result = await taskService.interveneWithQueuedPrompt(
        decodeURIComponent(intervenePromptMatch[1]),
        decodeURIComponent(intervenePromptMatch[2]),
      );
      return writeJson(response, 200, result);
    } catch (error) {
      return writeError(response, error);
    }
  }

  if (request.method === "GET" && url.pathname === "/api/events") {
    response.writeHead(200, {
      "Content-Type": "text/event-stream; charset=utf-8",
      "Cache-Control": "no-cache",
      Connection: "keep-alive",
    });
    response.write("retry: 2500\n\n");
    if (taskService.snapshot) {
      response.write(`event: status\ndata: ${JSON.stringify(taskService.snapshot)}\n\n`);
    }
    clients.add(response);
    request.on("close", () => clients.delete(response));
    return;
  }

  return writeJson(response, 404, { error: "Not found" });
});

async function shutdown() {
  server.close();
  taskService.stop();
}

process.once("SIGINT", shutdown);
process.once("SIGTERM", shutdown);

try {
  await taskService.start();
  server.listen(port, host, () => {
    console.log(`Remote Codex server: http://${host}:${port}`);
    for (const address of localAddresses()) console.log(`Phone URL: http://${address}:${port}`);
    console.log("LAN access: no authentication");
  });
} catch (error) {
  console.error(`Failed to start: ${error.message}`);
  process.exitCode = 1;
}
