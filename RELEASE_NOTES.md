# Remote Codex 1.2.2

这是第一个可直接分发的正式版本。电脑运行只读状态服务，Android 手机通过局域网连接后，可以按项目查看 Codex 主 Task，并从主 Task 逐层展开 Subagent。

`1.2.2` 修复 Task 正在持续产生模型输出，但首页仍显示“空闲”的问题。服务端现在会结合 rollout 生命周期和最近输出活动识别跨进程运行状态。

## 本版内容

- 项目与主 Task 分组。
- 多层 Subagent 父子树，不再与主 Task 并排。
- Task Plan、Goal、上下文、模型输出和工具活动详情。
- 可持久化的“隐藏空闲 Task”过滤器。
- 使用独立长期密钥签名的 Android Release APK。

## 分发文件

- `Remote-Codex-v1.2.2.zip`：包含 APK、服务端、使用文档和校验文件的总发行包。
- `Remote-Codex-Android-v1.2.2.apk`：Android 8.0 及以上设备安装包。
- `Remote-Codex-Server-v1.2.2.zip`：电脑端服务和完整使用文档。
- `SHA256SUMS.txt`：分发文件的 SHA-256 校验值。

## 升级提示

已安装 `1.2.0` 或 `1.2.1` 正式版时可直接覆盖升级。早期测试 APK 使用 Android 调试密钥签名，若手机提示签名冲突，请先卸载旧测试版再安装正式版。
