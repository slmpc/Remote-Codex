# Remote Codex 1.3.1

`1.3.1` 增加正式 Android 应用图标，并修复 Codex Desktop 运行中 Task 的立即干预链路。

## 本版内容

- 使用全新的 Remote Codex 自适应启动图标，并提供 Android 13 单色主题图标。
- 运行中的 Codex Desktop Task 通过本机 Desktop 桥接接收立即干预，不再返回 `thread not found`。
- Task 已结束或未加载时会先恢复线程，再开始新的 Turn。
- Prompt 发送失败时保留队列项并显示错误，避免静默丢失。
- 默认进入“对话”页，成功操作使用 Snackbar 显示，不再挤压输入区。
- 空 Prompt 队列不再显示管理栏，时间顺序的自动滚动行为更加稳定。

## 分发文件

- `Remote-Codex-v1.3.1.zip`：包含 APK、服务端、使用文档和校验文件的总发行包。
- `Remote-Codex-Android-v1.3.1.apk`：Android 8.0 及以上设备安装包。
- `Remote-Codex-Server-v1.3.1.zip`：电脑端服务和完整使用文档。
- `SHA256SUMS.txt`：分发文件的 SHA-256 校验值。

## 升级提示

已安装 `1.3.0` 正式版时可直接覆盖升级 Android APK。电脑端服务必须同步升级到 `1.3.1`，才能使用修复后的 Desktop 立即干预链路。

早期测试 APK 使用 Android 调试密钥签名，若手机提示签名冲突，请先卸载旧测试版再安装正式版。

## 开源协议

Remote Codex 采用 GNU General Public License v3.0（`GPL-3.0-only`）开源。完整源代码和许可证见 [GitHub 仓库](https://github.com/slmpc/Remote-Codex)。
