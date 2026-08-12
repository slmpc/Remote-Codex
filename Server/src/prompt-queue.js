import { randomUUID } from "node:crypto";
import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";

function defaultQueuePath() {
  if (process.env.REMOTE_CODEX_DATA_DIR) {
    return path.join(process.env.REMOTE_CODEX_DATA_DIR, "prompt-queue.json");
  }
  const directory = process.platform === "win32" && process.env.LOCALAPPDATA
    ? path.join(process.env.LOCALAPPDATA, "RemoteCodex")
    : path.join(os.homedir(), ".remote-codex");
  return path.join(directory, "prompt-queue.json");
}

export class PromptQueueStore {
  #queues = new Map();
  #writeChain = Promise.resolve();
  #writeSequence = 0;

  constructor({ filePath = defaultQueuePath() } = {}) {
    this.filePath = filePath;
  }

  async load() {
    if (!this.filePath) return;
    try {
      const data = JSON.parse(await readFile(this.filePath, "utf8"));
      this.#queues = new Map(
        Object.entries(data.queues ?? {}).map(([threadId, prompts]) => [
          threadId,
          Array.isArray(prompts)
            ? prompts
              .filter((item) => item?.id && item?.text)
              .map((item) => ({
                ...item,
                lastError: item.lastError ?? null,
                lastAttemptAt: item.lastAttemptAt ?? null,
              }))
            : [],
        ]),
      );
    } catch (error) {
      if (error.code !== "ENOENT") throw error;
    }
  }

  list(threadId) {
    return [...(this.#queues.get(threadId) ?? [])];
  }

  threadIds() {
    return [...this.#queues.entries()]
      .filter(([, prompts]) => prompts.length > 0)
      .map(([threadId]) => threadId);
  }

  async add(threadId, text) {
    const prompt = {
      id: randomUUID(),
      threadId,
      text,
      createdAt: Date.now(),
      lastError: null,
      lastAttemptAt: null,
    };
    const prompts = this.#queues.get(threadId) ?? [];
    prompts.push(prompt);
    this.#queues.set(threadId, prompts);
    await this.#persist();
    return prompt;
  }

  async update(threadId, promptId, patch) {
    const prompts = this.#queues.get(threadId) ?? [];
    const index = prompts.findIndex((item) => item.id === promptId);
    if (index < 0) return null;
    const updated = {
      ...prompts[index],
      ...patch,
      id: prompts[index].id,
      threadId: prompts[index].threadId,
      text: prompts[index].text,
      createdAt: prompts[index].createdAt,
    };
    prompts[index] = updated;
    await this.#persist();
    return updated;
  }

  async remove(threadId, promptId) {
    const prompts = this.#queues.get(threadId) ?? [];
    const index = prompts.findIndex((item) => item.id === promptId);
    if (index < 0) return null;
    const [removed] = prompts.splice(index, 1);
    if (prompts.length === 0) this.#queues.delete(threadId);
    await this.#persist();
    return removed;
  }

  async #persist() {
    if (!this.filePath) return;
    const queues = Object.fromEntries(this.#queues);
    const content = `${JSON.stringify({ version: 1, queues }, null, 2)}\n`;
    const sequence = ++this.#writeSequence;
    this.#writeChain = this.#writeChain.then(async () => {
      const directory = path.dirname(this.filePath);
      const temporaryPath = `${this.filePath}.${process.pid}.${sequence}.tmp`;
      await mkdir(directory, { recursive: true });
      await writeFile(temporaryPath, content, "utf8");
      await rename(temporaryPath, this.filePath);
    });
    await this.#writeChain;
  }
}
