import assert from "node:assert/strict";
import test from "node:test";
import { normalizeTask } from "../src/task-service.js";

test("active goal marks a separately loaded desktop thread as running", () => {
  const task = normalizeTask(
    { id: "1", name: "Build app", status: { type: "notLoaded" }, updatedAt: 10 },
    { status: "active", objective: "Ship it" },
  );
  assert.equal(task.state, "running");
});
test("approval flags take precedence over active state", () => {
  const task = normalizeTask({
    id: "2",
    status: { type: "active", activeFlags: ["waitingOnApproval"] },
  });
  assert.equal(task.state, "waiting");
});

test("parent thread marks a task as a subagent", () => {
  const task = normalizeTask({ id: "3", parentThreadId: "1", status: { type: "idle" } });
  assert.equal(task.isSubagent, true);
});
