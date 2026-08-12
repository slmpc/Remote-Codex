import assert from "node:assert/strict";
import test from "node:test";
import { buildProjects, buildTaskTree, normalizeTask, parseLatestPlanFromRollout } from "../src/task-service.js";

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

test("thread spawn source supplies subagent hierarchy metadata", () => {
  const task = normalizeTask({
    id: "child",
    status: { type: "idle" },
    source: {
      subAgent: {
        thread_spawn: {
          parent_thread_id: "parent",
          depth: 2,
          agent_path: "/root/reviewer",
          agent_nickname: "Reviewer",
          agent_role: "review",
        },
      },
    },
  });
  assert.equal(task.parentThreadId, "parent");
  assert.equal(task.isSubagent, true);
  assert.equal(task.agentNickname, "Reviewer");
  assert.equal(task.agentPath, "/root/reviewer");
  assert.equal(task.subagentDepth, 2);
});

test("projects contain only root tasks with recursively nested subagents", () => {
  const tasks = [
    { id: "root", projectPath: "C:/work/app", state: "idle", isSubagent: false, parentThreadId: null },
    { id: "child", projectPath: "C:/work/app", state: "running", isSubagent: true, parentThreadId: "root" },
    { id: "grandchild", projectPath: "C:/work/app", state: "idle", isSubagent: true, parentThreadId: "child" },
    { id: "orphan", projectPath: "C:/work/app", state: "idle", isSubagent: true, parentThreadId: "missing" },
  ];
  const tree = buildTaskTree(tasks);
  assert.equal(tree.length, 2);
  const root = tree.find((task) => task.id === "root");
  assert.equal(root.subagents[0].id, "child");
  assert.equal(root.subagents[0].subagents[0].id, "grandchild");

  const projects = buildProjects(tasks);
  assert.deepEqual(projects[0].tasks.map((task) => task.id), ["root"]);
  assert.equal(projects[0].summary.total, 1);
  assert.equal(projects[0].summary.subagents, 2);
  assert.equal(projects[0].summary.running, 1);
});

test("a recent in-progress plan marks the task as running", () => {
  const task = normalizeTask(
    { id: "4", updatedAt: Math.floor(Date.now() / 1000), status: { type: "notLoaded" } },
    null,
    { steps: [{ step: "Build", status: "inProgress" }] },
  );
  assert.equal(task.state, "running");
});

test("reads the latest update_plan call from a rollout", () => {
  const oldEntry = JSON.stringify({
    timestamp: "2026-01-01T00:00:00Z",
    payload: { input: 'tools.update_plan({plan:[{step:"Old",status:"pending"}]})' },
  });
  const latestEntry = JSON.stringify({
    timestamp: "2026-01-01T00:01:00Z",
    payload: {
      input: 'tools.update_plan({explanation:"Now",plan:[{step:"Build",status:"in_progress"},{step:"Test",status:"pending"}]})',
    },
  });
  const plan = parseLatestPlanFromRollout(`${oldEntry}\n${latestEntry}`);
  assert.equal(plan.explanation, "Now");
  assert.deepEqual(plan.steps, [
    { step: "Build", status: "inProgress" },
    { step: "Test", status: "pending" },
  ]);
});
