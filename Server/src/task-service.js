import { EventEmitter } from "node:events";
import { readFile, stat } from "node:fs/promises";
import path from "node:path";

const LIVE_GOAL_STATUSES = new Set(["active", "paused", "usageLimited", "budgetLimited"]);
const WAITING_GOAL_STATUSES = new Set(["paused", "blocked", "usageLimited", "budgetLimited"]);
const ALL_THREAD_SOURCES = [
  "cli",
  "vscode",
  "exec",
  "appServer",
  "subAgent",
  "subAgentReview",
  "subAgentCompact",
  "subAgentThreadSpawn",
  "subAgentOther",
  "unknown",
];

function statusType(value) {
  return typeof value === "string" ? value : value?.type ?? "unknown";
}

function projectName(cwd) {
  if (!cwd) return "未分类";
  return path.basename(cwd) || cwd;
}

function normalizePlanStatus(status) {
  if (status === "in_progress") return "inProgress";
  return ["pending", "inProgress", "completed"].includes(status) ? status : "pending";
}

function decodeQuotedText(value) {
  try {
    return JSON.parse(`"${value}"`);
  } catch {
    return value;
  }
}

function planFromToolSource(source) {
  if (typeof source !== "string" || !source.includes("update_plan")) return null;
  const steps = [];
  const pattern = /["']?step["']?\s*:\s*"((?:\\.|[^"\\])*)"[\s\S]*?["']?status["']?\s*:\s*"([^"\\]+)"/g;
  for (const match of source.matchAll(pattern)) {
    steps.push({ step: decodeQuotedText(match[1]), status: normalizePlanStatus(match[2]) });
  }
  if (steps.length === 0) return null;
  const explanationMatch = source.match(/["']?explanation["']?\s*:\s*"((?:\\.|[^"\\])*)"/);
  return {
    explanation: explanationMatch ? decodeQuotedText(explanationMatch[1]) : null,
    steps,
  };
}

export function parseLatestPlanFromRollout(content) {
  const lines = content.split(/\r?\n/);
  for (let index = lines.length - 1; index >= 0; index -= 1) {
    let entry;
    try {
      entry = JSON.parse(lines[index]);
    } catch {
      continue;
    }
    const payload = entry?.payload;
    if (!payload) continue;

    if (payload.name === "update_plan") {
      try {
        const input = typeof payload.input === "string" ? JSON.parse(payload.input) : payload.input;
        if (Array.isArray(input?.plan)) {
          return {
            explanation: input.explanation ?? null,
            steps: input.plan.map((item) => ({ step: item.step, status: normalizePlanStatus(item.status) })),
            updatedAt: Date.parse(entry.timestamp) || null,
            source: "rollout",
          };
        }
      } catch {
        // Fall through to source parsing.
      }
    }

    const parsed = planFromToolSource(payload.input);
    if (parsed) {
      return {
        ...parsed,
        updatedAt: Date.parse(entry.timestamp) || null,
        source: "rollout",
      };
    }
  }
  return null;
}

export function normalizeTask(thread, goal = null, plan = null) {
  const spawn = thread.source?.subAgent?.thread_spawn ?? null;
  const parentThreadId = thread.parentThreadId ?? spawn?.parent_thread_id ?? null;
  const runtimeStatus = statusType(thread.status);
  const activeFlags = thread.status?.activeFlags ?? [];
  const goalStatus = goal?.status ?? null;
  const recentlyUpdated = Date.now() - (thread.updatedAt ?? 0) * 1000 < 10 * 60 * 1000;
  const activePlan = recentlyUpdated && plan?.steps?.some((step) => step.status === "inProgress");
  let state = "idle";

  if (runtimeStatus === "systemError") state = "error";
  else if (activeFlags.length > 0 || WAITING_GOAL_STATUSES.has(goalStatus)) state = "waiting";
  else if (runtimeStatus === "active" || LIVE_GOAL_STATUSES.has(goalStatus) || activePlan) state = "running";

  return {
    id: thread.id,
    name: thread.name || thread.preview?.slice(0, 80) || "Untitled task",
    preview: thread.preview ?? "",
    cwd: thread.cwd ?? "",
    source: thread.source ?? "unknown",
    projectPath: thread.cwd ?? "",
    updatedAt: thread.updatedAt ?? thread.createdAt ?? 0,
    runtimeStatus,
    activeFlags,
    state,
    parentThreadId,
    agentNickname: thread.agentNickname ?? spawn?.agent_nickname ?? null,
    agentRole: thread.agentRole ?? spawn?.agent_role ?? null,
    agentPath: thread.agentPath ?? spawn?.agent_path ?? null,
    subagentDepth: thread.subagentDepth ?? spawn?.depth ?? 0,
    isSubagent: Boolean(parentThreadId || spawn),
    goal,
  };
}

export function buildTaskTree(tasks) {
  const byId = new Map(tasks.map((task) => [task.id, { ...task, subagents: [] }]));
  const roots = [];

  for (const sourceTask of tasks) {
    const task = byId.get(sourceTask.id);
    const parent = task.parentThreadId ? byId.get(task.parentThreadId) : null;
    let ancestor = parent;
    const seen = new Set();
    let cyclic = false;
    while (ancestor && !seen.has(ancestor.id)) {
      if (ancestor.id === task.id) {
        cyclic = true;
        break;
      }
      seen.add(ancestor.id);
      ancestor = ancestor.parentThreadId ? byId.get(ancestor.parentThreadId) : null;
    }

    if (parent && !cyclic) parent.subagents.push(task);
    else roots.push(task);
  }
  return roots;
}

function taskBranch(root) {
  return [root, ...root.subagents.flatMap(taskBranch)];
}

export function buildProjects(tasks) {
  const tasksByProject = new Map();
  for (const task of buildTaskTree(tasks).filter((item) => !item.isSubagent)) {
    const key = task.projectPath || "";
    const projectTasks = tasksByProject.get(key) ?? [];
    projectTasks.push(task);
    tasksByProject.set(key, projectTasks);
  }

  return [...tasksByProject.entries()]
    .map(([projectPath, projectTasks]) => {
      const allProjectTasks = projectTasks.flatMap(taskBranch);
      return {
        id: projectPath || "unclassified",
        name: projectName(projectPath),
        path: projectPath,
        summary: {
          total: projectTasks.filter((task) => !task.isSubagent).length,
          running: allProjectTasks.filter((task) => task.state === "running").length,
          waiting: allProjectTasks.filter((task) => task.state === "waiting").length,
          subagents: allProjectTasks.filter((task) => task.isSubagent).length,
        },
        tasks: projectTasks,
      };
    })
    .sort((a, b) => {
      const priority = (project) => (project.summary.running > 0 ? 0 : project.summary.waiting > 0 ? 1 : 2);
      return priority(a) - priority(b) || a.name.localeCompare(b.name);
    });
}

function findTaskInTree(tasks, threadId) {
  for (const task of tasks) {
    if (task.id === threadId) return task;
    const nested = findTaskInTree(task.subagents, threadId);
    if (nested) return nested;
  }
  return null;
}

export class TaskService extends EventEmitter {
  #snapshot = null;
  #refreshing = null;
  #timer = null;
  #threads = new Map();
  #runtimePlans = new Map();
  #rolloutPlans = new Map();

  constructor(codex, { refreshMs = 2500, limit = 50 } = {}) {
    super();
    this.codex = codex;
    this.codex.on("error", (error) => this.emit("error", error));
    this.refreshMs = refreshMs;
    this.limit = limit;
  }

  async start() {
    await this.codex.start();
    this.codex.on("notification", ({ method, params }) => {
      if (method === "turn/plan/updated") {
        this.#runtimePlans.set(params.threadId, {
          explanation: params.explanation ?? null,
          steps: (params.plan ?? []).map((item) => ({
            step: item.step,
            status: normalizePlanStatus(item.status),
          })),
          updatedAt: Date.now(),
          source: "live",
        });
      }
      if (/^(thread|turn)\//.test(method)) this.refresh().catch(() => {});
    });
    await this.refresh();
    this.#timer = setInterval(() => this.refresh().catch(() => {}), this.refreshMs);
    this.#timer.unref();
  }

  get snapshot() {
    return this.#snapshot;
  }

  async refresh() {
    if (this.#refreshing) return this.#refreshing;
    this.#refreshing = this.#doRefresh().finally(() => {
      this.#refreshing = null;
    });
    return this.#refreshing;
  }

  async #doRefresh() {
    const result = await this.codex.request("thread/list", {
      limit: this.limit,
      sortKey: "updated_at",
      sortDirection: "desc",
      useStateDbOnly: true,
      sourceKinds: ALL_THREAD_SOURCES,
    });
    const threads = result?.data ?? [];
    this.#threads = new Map(threads.map((thread) => [thread.id, thread]));
    const goals = await Promise.all(
      threads.map(async (thread) => {
        try {
          const response = await this.codex.request("thread/goal/get", { threadId: thread.id });
          return response?.goal ?? null;
        } catch {
          return null;
        }
      }),
    );
    const plans = await Promise.all(
      threads.map((thread) => this.#runtimePlans.get(thread.id) ?? this.#readPersistedPlan(thread.path)),
    );

    const tasks = threads.map((thread, index) => normalizeTask(thread, goals[index], plans[index]));
    tasks.sort((a, b) => {
      const priority = { running: 0, waiting: 1, error: 2, idle: 3 };
      return priority[a.state] - priority[b.state] || b.updatedAt - a.updatedAt;
    });

    const count = (state) => tasks.filter((task) => task.state === state).length;
    const projects = buildProjects(tasks);
    this.#snapshot = {
      generatedAt: Date.now(),
      summary: {
        total: tasks.filter((task) => !task.isSubagent).length,
        running: count("running"),
        waiting: count("waiting"),
        errors: count("error"),
        subagents: tasks.filter((task) => task.isSubagent).length,
      },
      projects,
      tasks,
    };
    this.emit("snapshot", this.#snapshot);
    return this.#snapshot;
  }

  async detail(threadId) {
    if (!this.#snapshot) await this.refresh();
    const summaryTask = this.#snapshot.tasks.find((task) => task.id === threadId);
    const storedThread = this.#threads.get(threadId);
    if (!summaryTask || !storedThread) return null;

    const [readResult, goalResult, persistedPlan] = await Promise.all([
      this.codex.request("thread/read", { threadId, includeTurns: true }),
      this.codex.request("thread/goal/get", { threadId }).catch(() => ({ goal: null })),
      this.#readPersistedPlan(storedThread.path),
    ]);
    const thread = readResult.thread;
    const turns = thread.turns ?? [];
    const allItems = turns.flatMap((turn) =>
      (turn.items ?? []).map((item) => ({ ...item, turnId: turn.id, turnStatus: turn.status })),
    );
    const latestTurn = turns.at(-1) ?? null;
    const persistedPlanItem = allItems.findLast((item) => item.type === "plan");
    const plan = this.#runtimePlans.get(threadId) ?? persistedPlan ?? (persistedPlanItem
      ? {
          explanation: null,
          steps: [{ step: persistedPlanItem.text, status: latestTurn?.status === "completed" ? "completed" : "inProgress" }],
          updatedAt: null,
          source: "threadItem",
        }
      : null);

    const userMessages = allItems
      .filter((item) => item.type === "userMessage")
      .map((item) => ({
        id: item.id,
        turnId: item.turnId,
        text: (item.content ?? []).filter((part) => part.type === "text").map((part) => part.text).join("\n"),
      }))
      .filter((item) => item.text)
      .slice(-20);
    const modelOutputs = allItems
      .filter((item) => item.type === "agentMessage" && item.text)
      .map((item) => ({ id: item.id, turnId: item.turnId, text: item.text, phase: item.phase ?? null }))
      .slice(-40);
    const activities = allItems.map((item) => this.#activity(item)).filter(Boolean).slice(-40);

    return {
      generatedAt: Date.now(),
      task: normalizeTask(thread, goalResult.goal ?? null, plan),
      project: { name: projectName(thread.cwd), path: thread.cwd ?? "" },
      execution: {
        currentTurnStatus: latestTurn?.status ?? null,
        turnCount: turns.length,
        itemCount: allItems.length,
        startedAt: latestTurn?.startedAt ?? null,
        completedAt: latestTurn?.completedAt ?? null,
        durationMs: latestTurn?.durationMs ?? null,
      },
      plan,
      context: {
        cwd: thread.cwd ?? "",
        source: thread.source ?? "unknown",
        modelProvider: thread.modelProvider ?? "unknown",
        cliVersion: thread.cliVersion ?? "",
        gitInfo: thread.gitInfo ?? null,
        createdAt: thread.createdAt ?? 0,
        updatedAt: thread.updatedAt ?? 0,
        userMessages,
        compactionCount: allItems.filter((item) => item.type === "contextCompaction").length,
      },
      modelOutputs,
      activities,
      subagents: findTaskInTree(buildTaskTree(this.#snapshot.tasks), threadId)?.subagents ?? [],
    };
  }

  #activity(item) {
    if (item.type === "commandExecution") {
      return { id: item.id, type: item.type, title: item.command, status: item.status, detail: item.aggregatedOutput?.slice(-1500) ?? null };
    }
    if (item.type === "fileChange") {
      return { id: item.id, type: item.type, title: `修改 ${item.changes?.length ?? 0} 个文件`, status: item.status, detail: null };
    }
    if (item.type === "mcpToolCall") {
      return { id: item.id, type: item.type, title: `${item.server}/${item.tool}`, status: item.status, detail: null };
    }
    if (item.type === "dynamicToolCall") {
      return { id: item.id, type: item.type, title: item.tool, status: item.status, detail: null };
    }
    if (item.type === "collabAgentToolCall") {
      return { id: item.id, type: item.type, title: `Subagent: ${item.tool}`, status: item.status, detail: item.prompt ?? null };
    }
    if (item.type === "webSearch") {
      return { id: item.id, type: item.type, title: `搜索: ${item.query}`, status: "completed", detail: null };
    }
    return null;
  }

  async #readPersistedPlan(rolloutPath) {
    if (!rolloutPath) return null;
    try {
      const info = await stat(rolloutPath);
      const cached = this.#rolloutPlans.get(rolloutPath);
      if (cached?.mtimeMs === info.mtimeMs && cached?.size === info.size) return cached.plan;
      const plan = parseLatestPlanFromRollout(await readFile(rolloutPath, "utf8"));
      this.#rolloutPlans.set(rolloutPath, { mtimeMs: info.mtimeMs, size: info.size, plan });
      return plan;
    } catch {
      return null;
    }
  }

  stop() {
    clearInterval(this.#timer);
    this.codex.stop();
  }
}
