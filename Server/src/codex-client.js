import { EventEmitter } from "node:events";
import { accessSync, constants } from "node:fs";
import { spawn } from "node:child_process";
import { createInterface } from "node:readline";
import path from "node:path";

const REQUEST_TIMEOUT_MS = 15_000;

function defaultCodexCommand() {
  if (process.env.CODEX_BIN) return process.env.CODEX_BIN;
  if (process.platform !== "win32") return "codex";

  const candidate = path.join(process.env.APPDATA ?? "", "npm", "codex.cmd");
  try {
    accessSync(candidate, constants.X_OK);
    return candidate;
  } catch {
    return "codex.cmd";
  }
}

export class CodexAppServer extends EventEmitter {
  #process;
  #pending = new Map();
  #nextId = 1;
  #readyPromise;
  #stopped = false;

  constructor({ command = defaultCodexCommand() } = {}) {
    super();
    this.command = command;
  }

  async start() {
    if (this.#readyPromise) return this.#readyPromise;
    this.#readyPromise = this.#start();
    return this.#readyPromise;
  }

  async #start() {
    const isBatchFile = process.platform === "win32" && /\.(cmd|bat)$/i.test(this.command);
    this.#process = spawn(this.command, ["app-server"], {
      cwd: process.cwd(),
      env: process.env,
      stdio: ["pipe", "pipe", "pipe"],
      windowsHide: true,
      shell: isBatchFile,
    });

    this.#process.once("error", (error) => this.#failAll(error));
    this.#process.once("exit", (code, signal) => {
      if (!this.#stopped) {
        this.#failAll(new Error(`Codex app-server exited (${code ?? signal})`));
      }
    });

    createInterface({ input: this.#process.stdout }).on("line", (line) => {
      let message;
      try {
        message = JSON.parse(line);
      } catch {
        return;
      }
      this.#handleMessage(message);
    });

    createInterface({ input: this.#process.stderr }).on("line", (line) => {
      if (process.env.REMOTE_CODEX_DEBUG === "1") console.error(`[codex] ${line}`);
    });

    const result = await this.request("initialize", {
      clientInfo: {
        name: "remote_codex",
        title: "Remote Codex",
        version: "1.3.0",
      },
    });
    this.notify("initialized", {});
    return result;
  }

  request(method, params = {}) {
    if (!this.#process?.stdin?.writable) {
      return Promise.reject(new Error("Codex app-server is not running"));
    }

    const id = this.#nextId++;
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.#pending.delete(id);
        reject(new Error(`${method} timed out`));
      }, REQUEST_TIMEOUT_MS);
      this.#pending.set(id, { resolve, reject, timeout });
      this.#write({ method, id, params });
    });
  }

  notify(method, params = {}) {
    this.#write({ method, params });
  }

  stop() {
    this.#stopped = true;
    this.#process?.kill();
    for (const pending of this.#pending.values()) {
      clearTimeout(pending.timeout);
      pending.reject(new Error("Codex app-server stopped"));
    }
    this.#pending.clear();
  }

  #write(message) {
    this.#process.stdin.write(`${JSON.stringify(message)}\n`);
  }

  #handleMessage(message) {
    if (message.id !== undefined) {
      const pending = this.#pending.get(message.id);
      if (!pending) return;
      clearTimeout(pending.timeout);
      this.#pending.delete(message.id);
      if (message.error) pending.reject(new Error(message.error.message ?? "Codex request failed"));
      else pending.resolve(message.result);
      return;
    }

    if (message.method) this.emit("notification", message);
  }

  #failAll(error) {
    for (const pending of this.#pending.values()) {
      clearTimeout(pending.timeout);
      pending.reject(error);
    }
    this.#pending.clear();
    this.emit("error", error);
  }
}
