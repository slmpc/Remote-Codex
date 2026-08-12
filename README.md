# Remote Codex

在安卓手机上按项目查看电脑中的 Codex Task。服务端通过官方 `codex app-server` JSON-RPC 协议读取线程、Goal、上下文、模型输出与 Subagent 元数据，Android 客户端使用 Material 3 与 Jetpack Compose。

## 运行服务端

要求 Node.js 20+、Codex CLI，并已在电脑端登录 Codex。

```powershell
.\start-server.ps1
```

默认监听 `0.0.0.0:8787`，启动输出会列出可供手机填写的局域网 IP。确保 Windows 防火墙允许 TCP 8787 入站，并且手机与电脑处于同一可信网络。

可选配置：

```powershell
$env:REMOTE_CODEX_PORT = "8787"
$env:REMOTE_CODEX_TOKEN = "使用一个足够长的随机令牌"
.\start-server.ps1
```

接口：

- `GET /healthz`：服务健康状态
- `GET /api/status`：按工作目录聚合的项目、Task、运行状态、Goal 与 Subagent 快照
- `GET /api/tasks/{threadId}`：单个 Task 的 Plan、上下文、模型输出、工具活动与 Subagent 详情
- `GET /api/events`：实时 SSE 状态流

配置令牌后，`/api/*` 需要 `Authorization: Bearer <token>`。未配置令牌时只应在可信局域网内使用，不要将 8787 端口暴露到公网。

## Android 客户端

使用 Android Studio 打开 `Client`，等待同步后运行 `app`。最低 Android 8.0（API 26）。在首屏填写服务端输出的 `IP:端口`；若服务端配置了令牌，同时填写访问令牌。首页按项目列出 Task，点击 Task 后可在“计划、上下文、输出、活动”四个 Material 3 标签页中查看实时详情。

命令行构建：

```powershell
cd Client
.\gradlew.bat assembleDebug
```

APK 输出到 `Client/app/build/outputs/apk/debug/app-debug.apk`。

## 状态语义

Codex 桌面进程与本项目启动的 app-server 是不同进程。app-server 会提供共享线程历史，而进程内运行标志不会跨进程继承。因此服务端结合共享 Goal、官方运行状态与当前 Plan 判断 Task 状态：active Goal 或最近仍有 `inProgress` 步骤的 Plan 被视为“运行中”，等待批准、暂停或受限状态显示为“等待”，并保留原始 `runtimeStatus`。

详情接口使用 `thread/read(includeTurns: true)` 获取用户可见的上下文、模型消息和工具活动。Plan 步骤在跨进程时可能不会由 app-server 还原，因此服务端只对 app-server 已返回的该 Thread rollout 文件做受限 JSONL 解析，以恢复最近一次 `update_plan`。接口不会返回加密推理内容或隐藏思维链。

官方协议说明：[Codex App Server](https://learn.chatgpt.com/docs/app-server)。WebSocket transport 目前是实验性的，本项目使用稳定的 stdio transport，并只对手机暴露自己的只读 HTTP API。
