# Aevren Sensory

Remote sensory layer for the Aevren/掌心窗 project.

It gives an MCP client or the Android companion an explicit-media "ear/eye" path:

- `sense_audio`: speech + non-speech events + timeline
- `sense_music`: structure, rhythm, instrumentation, notable moments
- `sense_video`: visual events + soundtrack + aligned timeline
- `sense_image`: image/GIF visual inspection
- `sensory_status`: redacted configuration status

The service does **not** continuously monitor the device. It only inspects media explicitly supplied by URL or base64.

## Environment

```env
SENSORY_GEMINI_API_KEY=...
SENSORY_MODEL=gemini-2.5-flash
SENSORY_ACCESS_TOKEN=long-random-token
SENSORY_MAX_MEDIA_BYTES=18874368
```

## Endpoints

- `GET /health`
- `POST /mcp` or `POST /mcp/TOKEN`
- `POST /sense` with `Authorization: Bearer TOKEN`
- `POST /sense/TOKEN`

Example direct request:

```json
{
  "kind": "audio",
  "source_url": "https://example.com/clip.mp3",
  "question": "What is being said and when does laughter happen?",
  "language": "zh-CN"
}
```

Small media can also be sent as `data_base64` with `mime_type`.

## Security

Remote URLs must be direct HTTPS media URLs. Credentials in URLs, localhost, .local and resolved private-network addresses are blocked, and redirects are re-checked. Keep `SENSORY_ACCESS_TOKEN` and provider API keys in Render environment variables, never in the Android APK or repository.

Version 0.1 intentionally caps inline media size conservatively. Larger-file upload/storage can be added separately without changing the MCP tools.
