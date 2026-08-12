import assert from "node:assert/strict";
import test from "node:test";
import { desktopExpression } from "../src/desktop-codex-client.js";

test("desktop expression sends the requested method through the local host manager", async () => {
  const calls = [];
  globalThis.__remoteCodexDesktopManager = {
    getHostId: () => "local",
    getConversation: () => null,
    sendRequest: async (method, params, options) => {
      calls.push({ method, params, options });
      return { turnId: params.expectedTurnId };
    },
  };
  try {
    const expression = desktopExpression("turn/steer", {
      threadId: "thread-1",
      expectedTurnId: "turn-1",
      input: [{ type: "text", text: "hello" }],
    }, 1234);
    const result = await eval(expression);
    assert.deepEqual(result, { ok: true, result: { turnId: "turn-1" } });
    assert.deepEqual(calls, [{
      method: "turn/steer",
      params: {
        threadId: "thread-1",
        expectedTurnId: "turn-1",
        input: [{ type: "text", text: "hello" }],
      },
      options: { timeoutMs: 1234 },
    }]);
  } finally {
    delete globalThis.__remoteCodexDesktopManager;
  }
});
