import assert from "node:assert/strict";
import { EventEmitter } from "node:events";
import test from "node:test";
import { PromptQueueStore } from "../src/prompt-queue.js";
import { TaskService } from "../src/task-service.js";

class FakeCodex extends EventEmitter {
  constructor() {
    super();
    this.activeTurn = { id: "turn-1", status: "inProgress" };
    this.requests = [];
    this.loaded = false;
    this.resumeError = null;
    this.startError = null;
    this.steerError = null;
  }

  async request(method, params) {
    this.requests.push({ method, params });
    if (method === "thread/read") {
      return { thread: { id: params.threadId, turns: this.activeTurn ? [this.activeTurn] : [] } };
    }
    if (method === "thread/resume") {
      if (this.resumeError) throw this.resumeError;
      this.loaded = true;
      return { thread: { id: params.threadId, turns: this.activeTurn ? [this.activeTurn] : [] } };
    }
    if (method === "turn/steer") {
      if (!this.loaded) throw new Error("thread not found");
      if (this.steerError) throw this.steerError;
      return { turnId: params.expectedTurnId };
    }
    if (method === "turn/start") {
      if (!this.loaded) throw new Error("thread not found");
      if (this.startError) throw this.startError;
      return { turn: { id: "turn-next", status: "inProgress" } };
    }
    throw new Error(`Unexpected request: ${method}`);
  }
}

function createService(options = {}) {
  const codex = new FakeCodex();
  const promptQueue = new PromptQueueStore({ filePath: null });
  return { codex, promptQueue, service: new TaskService(codex, { promptQueue, ...options }) };
}

test("queue keeps a prompt while the current turn is running", async () => {
  const { promptQueue, service } = createService();
  const result = await service.submitPrompt("thread-1", "Continue after this", "queue");
  assert.equal(result.action, "queued");
  assert.equal(promptQueue.list("thread-1")[0].text, "Continue after this");
});

test("queued prompt can be deleted", async () => {
  const { promptQueue, service } = createService();
  const queued = await promptQueue.add("thread-1", "No longer needed");
  await service.removeQueuedPrompt("thread-1", queued.id);
  assert.deepEqual(promptQueue.list("thread-1"), []);
});

test("queued prompt is removed only after successful intervention", async () => {
  const { codex, promptQueue, service } = createService();
  const queued = await promptQueue.add("thread-1", "Change direction");
  await service.interveneWithQueuedPrompt("thread-1", queued.id);
  const steer = codex.requests.find((request) => request.method === "turn/steer");
  assert.equal(steer.params.expectedTurnId, "turn-1");
  assert.equal(steer.params.input[0].text, "Change direction");
  assert.deepEqual(promptQueue.list("thread-1"), []);
});

test("running task is resumed before immediate intervention", async () => {
  const { codex, service } = createService();
  const result = await service.submitPrompt("thread-1", "Change direction now", "intervene");
  assert.equal(result.action, "intervened");
  assert.deepEqual(
    codex.requests.filter((request) => ["thread/resume", "turn/steer"].includes(request.method)).map((request) => request.method),
    ["thread/resume", "turn/steer"],
  );
});

test("running desktop task uses the desktop client before the secondary app-server", async () => {
  const requests = [];
  const desktopCodex = {
    async steer(threadId, turnId, text) {
      requests.push({ threadId, turnId, text });
      return { turnId };
    },
  };
  const { codex, service } = createService({ desktopCodex });
  const result = await service.submitPrompt("thread-1", "Change the running task", "intervene");
  assert.equal(result.action, "intervened");
  assert.equal(result.transport, "desktop");
  assert.deepEqual(requests, [{ threadId: "thread-1", turnId: "turn-1", text: "Change the running task" }]);
  assert.equal(codex.requests.some((request) => request.method === "thread/resume"), false);
});

test("desktop bridge failure reports a clear conflict for a desktop-owned running task", async () => {
  const desktopCodex = {
    async steer() {
      throw new Error("本地调试页面不可用");
    },
  };
  const { codex, service } = createService({ desktopCodex });
  codex.resumeError = new Error("thread has an active writer");

  await assert.rejects(
    service.submitPrompt("thread-1", "Change the running task", "intervene"),
    (error) => {
      assert.equal(error.statusCode, 409);
      assert.match(error.message, /Codex Desktop 正在执行此 Task/);
      assert.match(error.message, /本地调试页面不可用/);
      return true;
    },
  );
});

test("immediate prompt starts a new turn when the task is idle", async () => {
  const { codex, service } = createService();
  codex.activeTurn = null;
  const result = await service.submitPrompt("thread-1", "Send now", "intervene");
  assert.equal(result.action, "started");
  assert.deepEqual(
    codex.requests.filter((request) => ["thread/resume", "turn/start"].includes(request.method)).map((request) => request.method),
    ["thread/resume", "turn/start"],
  );
});

test("failed intervention preserves the queued prompt", async () => {
  const { codex, promptQueue, service } = createService();
  const queued = await promptQueue.add("thread-1", "Keep this safe");
  codex.steerError = new Error("steer failed");
  await assert.rejects(service.interveneWithQueuedPrompt("thread-1", queued.id), /steer failed/);
  assert.equal(promptQueue.list("thread-1")[0].id, queued.id);
  assert.equal(promptQueue.list("thread-1")[0].lastError, "steer failed");
  assert.equal(typeof promptQueue.list("thread-1")[0].lastAttemptAt, "number");
});

test("idle task starts the first queued prompt", async () => {
  const { codex, promptQueue, service } = createService();
  codex.activeTurn = null;
  const result = await service.submitPrompt("thread-1", "Start next", "queue");
  assert.equal(result.action, "started");
  assert.deepEqual(
    codex.requests.filter((request) => ["thread/resume", "turn/start"].includes(request.method)).map((request) => request.method),
    ["thread/resume", "turn/start"],
  );
  assert.deepEqual(promptQueue.list("thread-1"), []);
});

test("failed queued dispatch keeps the prompt and exposes the error", async () => {
  const { codex, promptQueue, service } = createService();
  codex.activeTurn = null;
  codex.resumeError = new Error("thread not found");
  const result = await service.submitPrompt("thread-1", "Retry this later", "queue");
  const queued = promptQueue.list("thread-1")[0];
  assert.equal(result.action, "queued");
  assert.equal(queued.text, "Retry this later");
  assert.match(queued.lastError, /无法恢复 Task.*thread not found/);
  assert.equal(typeof queued.lastAttemptAt, "number");
});
