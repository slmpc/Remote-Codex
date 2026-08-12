const DEFAULT_ENDPOINT = "http://127.0.0.1:9229";
const DEFAULT_TIMEOUT_MS = 8_000;

function desktopExpression(method, params, timeoutMs) {
  const request = JSON.stringify({ method, params, timeoutMs });
  return `
(async () => {
  const request = ${request};
  const isManager = (value) => value
    && typeof value.sendRequest === "function"
    && typeof value.getConversation === "function"
    && value.getHostId?.() === "local";
  let manager = globalThis.__remoteCodexDesktopManager;
  if (!isManager(manager)) {
    let fiber = globalThis.__codexRoot?._internalRoot?.current ?? null;
    let visited = 0;
    while (fiber && visited < 100000 && !manager) {
      let hook = fiber.memoizedState;
      let hookIndex = 0;
      while (hook && hookIndex < 200) {
        if (isManager(hook.memoizedState)) {
          manager = hook.memoizedState;
          break;
        }
        hook = hook.next;
        hookIndex += 1;
      }
      visited += 1;
      if (fiber.child) {
        fiber = fiber.child;
        continue;
      }
      while (fiber && !fiber.sibling) fiber = fiber.return;
      fiber = fiber?.sibling ?? null;
    }
    if (manager) globalThis.__remoteCodexDesktopManager = manager;
  }
  if (!manager) return { ok: false, error: "未找到 Codex Desktop 本地客户端" };
  try {
    const result = await manager.sendRequest(
      request.method,
      request.params,
      { timeoutMs: request.timeoutMs },
    );
    return { ok: true, result };
  } catch (error) {
    return { ok: false, error: error?.message ?? String(error) };
  }
})()`;
}

async function withTimeout(promise, timeoutMs, message) {
  let timeout;
  try {
    return await Promise.race([
      promise,
      new Promise((_, reject) => {
        timeout = setTimeout(() => reject(new Error(message)), timeoutMs);
      }),
    ]);
  } finally {
    clearTimeout(timeout);
  }
}

export class DesktopCodexClient {
  constructor({
    endpoint = process.env.REMOTE_CODEX_DESKTOP_DEBUG_URL ?? DEFAULT_ENDPOINT,
    timeoutMs = DEFAULT_TIMEOUT_MS,
    fetchImpl = globalThis.fetch,
    WebSocketImpl = globalThis.WebSocket,
  } = {}) {
    this.endpoint = endpoint.replace(/\/$/, "");
    this.timeoutMs = timeoutMs;
    this.fetchImpl = fetchImpl;
    this.WebSocketImpl = WebSocketImpl;
  }

  async steer(threadId, turnId, text) {
    return this.request("turn/steer", {
      threadId,
      expectedTurnId: turnId,
      input: [{ type: "text", text }],
    });
  }

  async request(method, params = {}) {
    if (!this.fetchImpl || !this.WebSocketImpl) {
      throw new Error("当前 Node.js 未启用 WebSocket，无法连接 Codex Desktop");
    }
    const targets = await withTimeout(
      this.fetchImpl(`${this.endpoint}/json/list`).then(async (response) => {
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.json();
      }),
      this.timeoutMs,
      "连接 Codex Desktop 超时",
    );
    const target = targets.find((item) => item.url === "app://-/index.html" && item.webSocketDebuggerUrl)
      ?? targets.find((item) => item.type === "page" && item.webSocketDebuggerUrl);
    if (!target) throw new Error("Codex Desktop 本地调试页面不可用");

    const socket = new this.WebSocketImpl(target.webSocketDebuggerUrl);
    try {
      await withTimeout(new Promise((resolve, reject) => {
        socket.addEventListener("open", resolve, { once: true });
        socket.addEventListener("error", () => reject(new Error("无法连接 Codex Desktop 本地页面")), { once: true });
      }), this.timeoutMs, "连接 Codex Desktop 超时");

      const id = 1;
      const response = await withTimeout(new Promise((resolve, reject) => {
        const onMessage = (event) => {
          let message;
          try {
            message = JSON.parse(event.data);
          } catch {
            return;
          }
          if (message.id !== id) return;
          socket.removeEventListener("message", onMessage);
          if (message.error) reject(new Error(message.error.message ?? "Codex Desktop 请求失败"));
          else resolve(message.result);
        };
        socket.addEventListener("message", onMessage);
        socket.send(JSON.stringify({
          id,
          method: "Runtime.evaluate",
          params: {
            expression: desktopExpression(method, params, this.timeoutMs),
            awaitPromise: true,
            returnByValue: true,
          },
        }));
      }), this.timeoutMs, `${method} 请求 Codex Desktop 超时`);

      if (response.exceptionDetails) {
        throw new Error(response.exceptionDetails.exception?.description ?? "Codex Desktop 执行请求失败");
      }
      const value = response.result?.value;
      if (!value?.ok) throw new Error(value?.error ?? "Codex Desktop 请求失败");
      return value.result;
    } finally {
      socket.close();
    }
  }
}

export { desktopExpression };
