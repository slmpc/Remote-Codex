# Remote Codex 1.3.0

Remote Codex 用于在 Android 手机上查看和干预电脑正在执行的 Codex Task。电脑运行一个局域网服务，手机连接电脑 IP 后，即可按项目查看主 Task、Subagent、Plan、统一对话和执行活动。

## 功能

- 按工作目录自动分类项目。
- 项目顶层只显示主 Task。
- 从主 Task 递归展开多层 Subagent。
- 点击任意 Task 或 Subagent 查看独立执行状态。
- 查看 Plan、Goal、运行上下文、输入输出对话和工具活动。
- 立即发送 Prompt 干预运行中的 Task。
- 将 Prompt 加入队列，当前 Turn 结束后自动继续；队列项可删除或改为立即干预。
- 隐藏空闲 Task，并保留包含活跃 Subagent 的父 Task。
- 每 2.5 秒自动刷新。
- Material 3 界面，跟随系统浅色或深色主题。

## 分发文件

- `Remote-Codex-v1.3.0.zip`：包含下面所有内容的总发行包，转发这个文件即可。
- `Remote-Codex-Android-v1.3.0.apk`：安装到 Android 手机。
- `Remote-Codex-Server-v1.3.0.zip`：解压到运行 Codex 的电脑。
- `SHA256SUMS.txt`：文件完整性校验值。

APK 需要连接电脑端服务，不能脱离电脑单独使用。发给其他人时，最简单的方式是只发送总发行包。

## 系统要求

电脑：

- Windows 10 或 Windows 11。
- Node.js 20 或更高版本。
- 已安装并登录 Codex CLI。
- 电脑和手机在同一局域网。

手机：

- Android 8.0 或更高版本。

Codex CLI 安装方式以 [OpenAI Codex 官方文档](https://developers.openai.com/codex/) 为准。本版本已使用 `codex-cli 0.144.5` 验证。

## 安装和使用

### 1. 检查电脑环境

打开 PowerShell：

```powershell
node --version
codex --version
codex login status
```

Node.js 应为 20 或更高版本，Codex 应显示已登录。未登录时运行：

```powershell
codex login
```

### 2. 启动服务端

解压 `Remote-Codex-Server-v1.3.0.zip`，双击：

```text
start-server.cmd
```

也可以在解压目录打开 PowerShell：

```powershell
.\start-server.ps1
```

启动后窗口会显示手机可用的地址：

```text
Phone URL: http://192.168.1.10:8787
```

保持窗口开启。按 `Ctrl+C` 或关闭窗口即可停止服务。

Windows 首次弹出防火墙提示时，允许 Node.js 在专用网络通信。

### 3. 安装 Android 客户端

把 `Remote-Codex-Android-v1.3.0.apk` 发送到手机并打开安装。若 Android 拦截安装，为当前浏览器或文件管理器开启“允许安装未知应用”。

如果安装时提示签名冲突，请先卸载旧的测试版 Remote Codex，再安装正式版。旧 Debug APK 与正式版签名不同，不能直接覆盖。

### 4. 连接电脑

打开手机上的 Remote Codex：

1. “电脑 IP”填写服务端显示的地址，例如 `192.168.1.10:8787`。
2. 点击“连接”。

客户端会保存电脑地址和“隐藏空闲 Task”设置。

## 界面说明

首页按项目显示主 Task。Task 状态：

- “运行中”：正在执行，或 Goal、Plan、最近输出活动仍处于运行状态。
- “等待”：等待批准、暂停、阻塞或受限。
- “错误”：Task 发生系统错误。
- “空闲”：当前没有执行活动。

有 Subagent 的 Task 会显示展开按钮。点击展开按钮查看子级；点击 Task 卡片进入详情。Subagent 还可以继续展开下一层。

详情页包括：

- “计划”：Plan 步骤、执行统计、Goal 和直接 Subagent。
- “对话”：用户输入、模型输出、Prompt 输入框和待发送队列。
- “上下文”：项目目录、来源、Codex 版本和 Git 信息。
- “活动”：命令、文件修改、MCP 和 Subagent 等工具活动。

“隐藏空闲 Task”开启后，空闲 Task 会隐藏。如果父 Task 自身空闲但子 Subagent 正在运行，父 Task仍会显示，避免丢失活跃任务入口。

“对话”页可开启“最新消息优先”，将最新输入和输出移到上方。关闭时按对话顺序显示并默认滚动到最下面；新消息到达时，只有当前位于底部才自动跟随。该选择会保存在手机中，并应用于所有 Task。

## 更换端口

默认端口是 `8787`。需要更换时：

```powershell
.\start-server.ps1 -Port 9000
```

手机地址也要填写新端口，例如 `192.168.1.10:9000`。

## 常见问题

### 手机无法连接

依次检查：

1. 服务端窗口是否仍在运行。
2. 手机和电脑是否连接同一 Wi-Fi。
3. 手机填写的是否为 `Phone URL` 中的 WLAN 或以太网地址。
4. Windows 防火墙是否允许 Node.js 在专用网络通信。
5. 电脑浏览器能否打开 `http://127.0.0.1:8787/healthz`。

访客 Wi-Fi 可能隔离不同设备，导致手机无法访问电脑。

### 服务端提示找不到 Node.js 或 Codex

安装完成后重新打开 PowerShell，再检查：

```powershell
node --version
codex --version
codex login status
```

### 页面没有 Task

先在同一个 Windows 用户下使用 Codex Desktop、CLI 或 IDE 创建一个 Task。Remote Codex 读取该用户的 Codex 线程记录。

### IP 地址变化

电脑重新连接网络后 IP 可能改变。重新启动服务端或查看窗口中的 `Phone URL`，然后在手机修改地址并点击“重新连接”。

### 状态与电脑端短暂不同

手机每 2.5 秒刷新一次。Remote Codex 服务与 Codex Desktop 是不同进程，状态可能有短暂延迟。

## 数据说明

服务端通过 Codex `app-server` 读取当前 Windows 用户的线程信息，并允许手机向已有 Task 发送 Prompt。“立即发送”会干预运行中的 Turn；Task 空闲时则直接开始新 Turn。也可以把 Prompt 加入队列，在当前 Turn 结束后自动继续。

Prompt 队列保存在电脑当前用户的本地应用数据目录（Windows 默认为 `%LOCALAPPDATA%\RemoteCodex\prompt-queue.json`）。队列项可以在发送前删除；Task 运行时也可以把队列项改为立即干预。服务端仅通过 Codex 执行 Prompt，不直接提供任意命令或文件操作接口。

详情页可能显示工作目录、用户提示、模型输出、命令结果和 Git 信息。只建议在自己的局域网中使用，不要将端口映射到公网。

本项目不会显示加密推理内容或隐藏思维链。

## HTTP API

- `GET /healthz`：服务健康状态和版本。
- `GET /api/status`：项目、主 Task、Subagent 树和状态汇总。
- `GET /api/tasks/{threadId}`：单个 Task 详情。
- `POST /api/tasks/{threadId}/prompts`：发送 Prompt；`mode` 为 `intervene` 或 `queue`。
- `DELETE /api/tasks/{threadId}/prompts/{promptId}`：删除待发送 Prompt。
- `POST /api/tasks/{threadId}/prompts/{promptId}/intervene`：将队列项立即改为干预。
- `GET /api/events`：SSE 状态流。

示例：

```powershell
Invoke-RestMethod http://127.0.0.1:8787/api/status
```

立即干预运行中的 Task：

```powershell
$body = @{ text = "先停止当前方向，优先修复编译错误"; mode = "intervene" } | ConvertTo-Json
Invoke-RestMethod -Method Post -ContentType "application/json" -Body $body `
  http://127.0.0.1:8787/api/tasks/<threadId>/prompts
```

## 开发和构建

服务端测试：

```powershell
cd Server
npm test
```

Android Debug APK：

```powershell
cd Client
.\gradlew.bat assembleDebug
```

构建完整正式版：

```powershell
.\build-release.ps1
```

脚本会运行测试、构建并验证正式签名 APK、打包服务端和生成 SHA-256。输出目录为 `dist`。

Android 正式签名保存在当前用户的 `%LOCALAPPDATA%\RemoteCodex\signing`，用于以后发布可覆盖安装的升级版本。请备份这个目录，不要发送给其他人。

## 版本信息

- Remote Codex：1.3.0
- Android versionCode：6
- 最低 Android：8.0 / API 26
- Node.js：20+

版本变化见 `CHANGELOG.md`，本版摘要见 `RELEASE_NOTES.md`。

## 开源协议

Copyright (C) 2026 Chen Meng。

本项目采用 [GNU General Public License v3.0](LICENSE) 开源，SPDX 标识为 `GPL-3.0-only`。你可以在 GPLv3 条款下使用、研究、修改和分发本项目；本软件不提供任何担保。

源代码仓库：[github.com/slmpc/Remote-Codex](https://github.com/slmpc/Remote-Codex)
