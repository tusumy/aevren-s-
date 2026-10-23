# 砚团 · 一起看屏幕（v0.3.9.9）

## 手机使用

1. 在砚团配置服务器地址与 Token，开启无障碍服务以便检测正在显示的应用。
2. 陪伴页 →「看见」→「一起看 · 去 X 或其他应用共享屏幕」。
3. 选择「只录画面」或「画面＋应用声音」，在 Android 系统界面明确允许屏幕共享；不要选择砚团。
4. 砚团切到 X 或你选的应用，检测到稳定显示约 1.5 秒后开始共享。没检测到目标应用会在 30 秒后取消，且不采集砚团自身页面。
5. 每约 2 秒发送一张缩小的 JPEG，云端仅存最近 6 帧（循环覆盖）。如目标应用及 Android 允许抓取播放声音，每约 8 秒上传最新一段 WAV；不录麦克风。
6. 聊天里的 AI 可以多次调用 `latest_watch` 获取新帧。**MCP 工具只在被调用时读取画面，并不是自动播放 30 FPS 的视频流**。如果切走正在共享的目标应用，砚团暂停取帧。
7. 在 Android 前台通知点「立即停止」，砚团便会结束投屏会话、停止上传，并发送删除服务器缓冲区的请求；最长 1 小时自动结束（防遗忘保护）。

## 存储与隐私

- Android MediaProjection 必须由用户在手机上主动授权；MCP 不提供远程开启共享的操作。
- 云端仅保留最多 6 张最新截图，以及最近一次允许捕获的应用音频段。服务端不存完整 MP4，也不会将内容写到 GitHub。
- 结束时服务器清理当前会话媒体：Python 后端删除文件，Cloudflare 删除相关 KV 项目。网络故障可能使立即删除失败；未清理的缓存依然会在最多约 30 分钟后过期或在后续请求时清理。Cloudflare KV 删除的可见性受其一致性模型影响。
- 数据从手机上传到用户配置的服务器，由 AI 客户端调用 MCP 后再发送给所用模型。已经发送到聊天平台的工具结果，不受砚团的服务器删除操作控制。
- 某些 App 禁止录取内部音频、私密窗口可能显示黑屏；不绕过 Android 安全保护。

## API 兼容

- `POST /api/watch/frame?session_id=<uuid>&index=0..5&sequence=<nonnegative>`：JPEG。旧版无 `sequence` 的 12 秒片段仍被接收。
- `POST /api/watch/audio?session_id=<uuid>`：WAV，重复请求覆盖最近音频段。
- `POST /api/watch/stop?session_id=<uuid>`：仅持有效 Token 才能请求，清除与该会话相关的云端媒体和索引。
- `GET /api/watch/latest`：包含 `frames`（按采样时间排序）、`frame_sequence`、`active` 等元数据。
- `GET /api/watch/frame`、`GET /api/watch/audio`：仅当当前授权会话仍然存在且媒体尚未清除时可取。

**升级说明：** Android APK、Python Server 或 Cloudflare Worker、MCP 服务需分别更新。只更新 APK 而未部署新服务器会导致超过 6 帧后无法正确持续共享或无法清除云端媒体。

## 验证

```bash
for test_file in android/tests/test_*.py; do python "$test_file"; done
node server/cloudflare-worker/test-watch.mjs
node --check mcp/server.js
node --check server/cloudflare-worker/worker.js
python -m py_compile server/linjian_server.py
bash android/build.sh
```
