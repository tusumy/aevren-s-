# Aevren Window MCP Probe

This directory is an isolated MCP connectivity probe. It does not access the phone, D1, KV, LINJIAN_TOKEN, or the production aevren-window Worker.

## Cloudflare Workers Builds

Create a separate Worker project from `tusumy/aevren-s-` using branch `window-probe`.

- Build command: leave blank
- Deploy command: `cd server/cloudflare-probe && node deploy.mjs`
- Root directory: `/`
- No variables or secrets are required

After deployment, verify:

- `/health` returns `ok: true`, `transport: sessionful-streamable-http`, `binding: ok`
- ChatGPT connector URL: `https://<probe-worker>.workers.dev/mcp`
- Authentication: None

The only MCP tool is `ping_window_probe`.
