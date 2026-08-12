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
  }

  async request(method, params) {
    this.requests.push({ method, params });
    if (method === "thread/read") {
      return { thread: { id: params.threadId, turns: this.activeTurn ? [this.activeTurn] : [] } };
    }
    if (method === "turn/steer") return { turnId: params.expectedTurnId };
    if (method === "turn/start") return { turn: { id: "turn-next", status: "inProgress" } };
    throw new Error(`Unexpected request: ${method}`);
  }
}

function createService() {
  const codex = new FakeCodex();
  const promptQueue = new PromptQueueStore({ filePath: null });
  return { codex, promptQueue, service: new TaskService(codex, { promptQueue }) };
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

test("immediate prompt starts a new turn when the task is idle", async () => {
  const { codex, service } = createService();
  codex.activeTurn = null;
  const result = await service.submitPrompt("thread-1", "Send now", "intervene");
  assert.equal(result.action, "started");
  assert.equal(codex.requests.some((request) => request.method === "turn/start"), true);
});

test("failed intervention preserves the queued prompt", async () => {
  const { codex, promptQueue, service } = createService();
  const queued = await promptQueue.add("thread-1", "Keep this safe");
  codex.request = async (method, params) => {
    if (method === "thread/read") {
      return { thread: { id: params.threadId, turns: [codex.activeTurn] } };
    }
    throw new Error("steer failed");
  };
  await assert.rejects(service.interveneWithQueuedPrompt("thread-1", queued.id), /steer failed/);
  assert.equal(promptQueue.list("thread-1")[0].id, queued.id);
});

test("idle task starts the first queued prompt", async () => {
  const { codex, promptQueue, service } = createService();
  codex.activeTurn = null;
  const result = await service.submitPrompt("thread-1", "Start next", "queue");
  assert.equal(result.action, "started");
  assert.equal(codex.requests.some((request) => request.method === "turn/start"), true);
  assert.deepEqual(promptQueue.list("thread-1"), []);
});
