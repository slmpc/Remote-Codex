import { EventEmitter } from "node:events";

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

export function normalizeTask(thread, goal = null) {
  const runtimeStatus = statusType(thread.status);
  const activeFlags = thread.status?.activeFlags ?? [];
  const goalStatus = goal?.status ?? null;
  let state = "idle";

  if (runtimeStatus === "systemError") state = "error";
  else if (activeFlags.length > 0 || WAITING_GOAL_STATUSES.has(goalStatus)) state = "waiting";
  else if (runtimeStatus === "active" || LIVE_GOAL_STATUSES.has(goalStatus)) state = "running";

  return {
    id: thread.id,
    name: thread.name || thread.preview?.slice(0, 80) || "Untitled task",
    preview: thread.preview ?? "",
    cwd: thread.cwd ?? "",
    source: thread.source ?? "unknown",
    updatedAt: thread.updatedAt ?? thread.createdAt ?? 0,
    runtimeStatus,
    activeFlags,
    state,
    parentThreadId: thread.parentThreadId ?? null,
    agentNickname: thread.agentNickname ?? null,
    agentRole: thread.agentRole ?? null,
    isSubagent: Boolean(thread.parentThreadId),
    goal,
  };
}

export class TaskService extends EventEmitter {
  #snapshot = null;
  #refreshing = null;
  #timer = null;

  constructor(codex, { refreshMs = 2500, limit = 50 } = {}) {
    super();
    this.codex = codex;
    this.codex.on("error", (error) => this.emit("error", error));
    this.refreshMs = refreshMs;
    this.limit = limit;
  }

  async start() {
    await this.codex.start();
    this.codex.on("notification", ({ method }) => {
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

    const tasks = threads.map((thread, index) => normalizeTask(thread, goals[index]));
    tasks.sort((a, b) => {
      const priority = { running: 0, waiting: 1, error: 2, idle: 3 };
      return priority[a.state] - priority[b.state] || b.updatedAt - a.updatedAt;
    });

    const count = (state) => tasks.filter((task) => task.state === state).length;
    this.#snapshot = {
      generatedAt: Date.now(),
      summary: {
        total: tasks.length,
        running: count("running"),
        waiting: count("waiting"),
        errors: count("error"),
        subagents: tasks.filter((task) => task.isSubagent).length,
      },
      tasks,
    };
    this.emit("snapshot", this.#snapshot);
    return this.#snapshot;
  }

  stop() {
    clearInterval(this.#timer);
    this.codex.stop();
  }
}
