# Remote Codex 1.3.0

`1.3.0` 将 Remote Codex 从只读监控工具扩展为可在 Android 手机上继续操作已有 Codex Task 的局域网客户端。

## 本版内容

- 在统一“对话”页按真实顺序查看用户输入和模型输出。
- 向运行中的 Turn 发送 Prompt 进行立即干预。
- 将多个 Prompt 加入持久化队列，在当前 Turn 结束后按顺序自动继续。
- 支持删除待发送 Prompt，或将队列项一键改为立即干预。
- Prompt 队列保存在电脑本地，服务重启后仍可恢复。
- 最新消息优先可切换；使用时间顺序时默认滚动到底部并智能跟随新消息。
- Prompt 输入区固定在底部，并适配 Android 输入法，发送按钮不会被键盘遮挡。
- 详情页使用四等分 Material 3 标签栏，修复选项对齐问题。
- Prompt 队列管理固定在输入区上方，可随时展开、立即发送或删除。
- 立即发送按钮不再因跨进程状态误判而禁用；Task 空闲时会直接开始新 Turn。
- 空 Prompt 输入框缩为单行，输入多行内容时再自动增高。

## 分发文件

- `Remote-Codex-v1.3.0.zip`：包含 APK、服务端、使用文档和校验文件的总发行包。
- `Remote-Codex-Android-v1.3.0.apk`：Android 8.0 及以上设备安装包。
- `Remote-Codex-Server-v1.3.0.zip`：电脑端服务和完整使用文档。
- `SHA256SUMS.txt`：分发文件的 SHA-256 校验值。

## 升级提示

已安装 `1.2.x` 正式版时可直接覆盖升级 Android APK。电脑端服务必须同步升级到 `1.3.0`，旧服务端不提供 Prompt 写入和队列接口。

早期测试 APK 使用 Android 调试密钥签名，若手机提示签名冲突，请先卸载旧测试版再安装正式版。

## 开源协议

Remote Codex 采用 GNU General Public License v3.0（`GPL-3.0-only`）开源。完整源代码和许可证见 [GitHub 仓库](https://github.com/slmpc/Remote-Codex)。
