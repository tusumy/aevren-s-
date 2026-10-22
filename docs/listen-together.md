# 砚团 · 一起看（12秒视听关键帧 MVP）

通过 Android 系统 MediaProjection **手机主人主动点击 + 每次系统同意授权**，同步取得约 12 秒的画面关键帧和播放声音，然后由 MCP 把画面与可用台词交给陪伴者理解短视频里的场景、人物动作、镜头变化与无台词剧情。

这不是连续视频流或完整录像：每段最多取 **6 张按时间排序的 JPEG 关键帧**，约每隔 2 秒一帧；声音仍是至多 12 秒 PCM 16 kHz 单声道 WAV。每次授权自动结束，不提供远程录制入口，也不改变原有手动截图与读屏功能。

## 使用步骤

1. Android 10+ 手机升级砚团后，在「陪伴 → 看见」按 **一起看 · 录12秒**。首次允许录音权限（只用于系统播放声，不使用麦克风）。
2. 每次 Android 弹出 MediaProjection 画面/声音录制授权时由手机主人同意；录制过程有前台通知可立即停止。返回正在播放的视频，等待约 12 秒。
3. 手机将在录制结束后先上传有序画面帧，再在可录到系统播放声音时上传 WAV；如播放器禁止内录，**画面仍尝试上传**。每次有结果/错误写入砚团调试日志。
4. 如果砚团连接的是 **Cloudflare aevren-window Worker**，应部署本分支的 `server/cloudflare-worker/` 源码：它已新增 `/api/video/clip`、`/api/video/latest(.json)`、`/api/audio/clip`、`/api/audio/latest(.json)` 以及 `listen_together_watch` / `listen_together_transcribe` MCP 工具。不需要部署 Render；只装 APK 仍无法获得关键帧。若使用 Python/Node 服务版，需部署 `server/` 与 `mcp/`。
5. 在聊天里叫陪伴者使用 `listen_together_watch`，即可取得最多6张按偏移时间排列的画面并分析剧情。若要台词，再调用 `listen_together_transcribe`，该工具仍要求 MCP 部署环境配置 `OPENAI_API_KEY`（只在部署环境中设置，别写进仓库）。

## 隐私和限制

- **手机端用户每次主动操作**；MCP 没有开始、延长、定时或远程触发录制的接口；中途停止丢弃当前未完成片段。画面与声音都暂存在本机内存后经已配置的服务器上传，**不在手机写入媒体文件**。
- Python 服务版的画面与声音按设备 ID 分开、临时留在 RAM，180 秒后接口不返回，新片段覆盖旧片段。**Cloudflare 版改用已有 SCREENSHOT_KV**，为画面与声音分别设置 `expirationTtl=180`；过期后不可从接口读取，但 KV 后台的物理清理可能晚于过期时刻，因此不承诺严格物理清除。视频截帧会包含当时可见的聊天浮窗、通知等：录制前请自行检查屏幕内容，只分享愿意上传的画面。手机与 server 之间优先使用 HTTPS。
- DRM、FLAG_SECURE 或禁止共享的播放器可能呈黑屏、截不到画面或录不到声音，不保证绕过限制。帧间快速动作和字幕可能被错过，MCP 不应假装看过完整视频。
- `listen_together_watch` 读取图片**不需要** OpenAI 转录密钥；但聊天端必须支持并接收 MCP 图片返回。声音使用第三方识别服务时片段会上传该服务。没有密钥时仍能看图分析。
- 不是持续后台看剧、非实时通话。未来如需更长片段，应由用户选择录制时长并再次明确授权；不能偷录或永久存片。

## 验证

CI：`python -m unittest discover -s server/tests -p 'test_audio_clip.py'`、`python -m py_compile server/linjian_server.py`、`node --check mcp/server.js` 和 Android Debug 构建。

仅用户个人安装测试；发布需遵守仓库 AGENTS.md 要求。真机 MediaProjection、系统通知停止、DRM 黑屏和播放器兼容性仍需实测。

## Cloudflare 砚团（当前用户的部署架构）

- 已有 `aevren-window` Worker 配置的 D1 / `SCREENSHOT_KV` / `LINJIAN_TOKEN` 可复用，不必新建 Render 服务或 KV namespace。注意不要覆盖正在用的正式 Worker，直到分支测试通过、真机验证并安排发布。
- 对 **Cloudflare** 运行的 `/mcp`，新工具定义在 `server/cloudflare-worker/worker.js`，具体实现是 `listen-together.js`。只分析画面不需要新增密钥；如需把媒体播放声转台词，再通过 Cloudflare Workers Secret 设置 `OPENAI_API_KEY`。
- 可以在仓库的 `server/cloudflare-worker/` 中运行 `node --test tests/listen-together.test.mjs` 测试 API 合约。当前自动化测试不能替代真机授权、视频应用 DRM、Cloudflare KV 跨区域一致性和 ChatGPT MCP 图片展示的验收。
