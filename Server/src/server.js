import http from "node:http";
import os from "node:os";
import { CodexAppServer } from "./codex-client.js";
import { TaskService } from "./task-service.js";

const host = process.env.REMOTE_CODEX_HOST ?? "0.0.0.0";
const port = Number(process.env.REMOTE_CODEX_PORT ?? 8787);
const token = process.env.REMOTE_CODEX_TOKEN ?? "";
const clients = new Set();
const taskService = new TaskService(new CodexAppServer());

function localAddresses() {
  return Object.values(os.networkInterfaces())
    .flat()
    .filter((item) => item?.family === "IPv4" && !item.internal)
    .map((item) => item.address);
}
function authorized(request) {
  if (!token) return true;
  return request.headers.authorization === `Bearer ${token}`;
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
    });
  }

  if (!authorized(request)) {
    response.setHeader("WWW-Authenticate", "Bearer");
    return writeJson(response, 401, { error: "Unauthorized" });
  }

  if (request.method === "GET" && url.pathname === "/api/status") {
    try {
      const snapshot = await taskService.refresh();
      return writeJson(response, 200, snapshot);
    } catch (error) {
      return writeJson(response, 503, { error: error.message });
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
    console.log(token ? "Bearer token authentication: enabled" : "Bearer token authentication: disabled (trusted LAN only)");
  });
} catch (error) {
  console.error(`Failed to start: ${error.message}`);
  process.exitCode = 1;
}
