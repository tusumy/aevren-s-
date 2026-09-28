import { McpAgent } from "agents/mcp";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

const PROBE_VERSION = "0.7.0-toy-bridge";
const DEFAULT_DEVICE = "android-phone";

function textResult(payload) {
  return { content: [{ type: "text", text: JSON.stringify(payload, null, 2) }] };
}

async function parseRpcResponse(response) {
  const raw = await response.text();
  const contentType = String(response.headers.get("content-type") || "").toLowerCase();
  const looksLikeSse = contentType.includes("text/event-stream") || raw.trimStart().startsWith("event:") || /(^|\n)data:/m.test(raw);
  if (!looksLikeSse) {
    try { return JSON.parse(raw); }
    catch (error) { throw new Error(`MCP returned invalid JSON: ${error instanceof Error ? error.message : String(error)}`); }
  }
  for (const event of raw.split(/\r?\n\r?\n/)) {
    const data = event.split(/\r?\n/).filter((line) => line.startsWith("data:")).map((line) => line.slice(5).trimStart()).join("\n").trim();
    if (!data || data === "[DONE]") continue;
    try { return JSON.parse(data); } catch { /* keep scanning */ }
  }
  throw new Error("MCP returned unreadable SSE payload");
}

async function legacyRpcWithEnv(env, method, params = {}) {
  const token = String(env?.LINJIAN_TOKEN || "");
  if (!token) throw new Error("LINJIAN_TOKEN is not configured in probe runtime.");
  if (!env?.WINDOW_BACKEND) throw new Error("WINDOW_BACKEND service binding is missing.");
  const response = await env.WINDOW_BACKEND.fetch(new Request(
    `https://backend.internal/mcp?token=${encodeURIComponent(token)}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json", "Accept": "application/json, text/event-stream" },
      body: JSON.stringify({ jsonrpc: "2.0", id: crypto.randomUUID(), method, params }),
    },
  ));
  const payload = await parseRpcResponse(response);
  if (!response.ok || payload?.error) {
    const message = payload?.error?.message || payload?.error || `Legacy MCP RPC failed with ${response.status}`;
    throw new Error(typeof message === "string" ? message : JSON.stringify(message));
  }
  return payload?.result;
}

async function listLegacyTools(env) {
  const listed = await legacyRpcWithEnv(env, "tools/list");
  return Array.isArray(listed?.tools) ? listed.tools : [];
}

function compactTool(tool, includeSchema = false) {
  const out = { name: tool?.name || "", title: tool?.title || "", description: tool?.description || "" };
  if (includeSchema) {
    out.inputSchema = tool?.inputSchema || { type: "object", properties: {} };
    out.annotations = tool?.annotations || null;
  }
  return out;
}

async function toyFetch(env, path, init = {}) {
  if (!env?.TOY_BACKEND) throw new Error("TOY_BACKEND service binding is missing.");
  const token = String(env?.TOY_CONTROL_TOKEN || "");
  if (!token) throw new Error("TOY_CONTROL_TOKEN is not configured in probe runtime.");
  const headers = new Headers(init.headers || {});
  headers.set("Authorization", `Bearer ${token}`);
  if (init.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  const response = await env.TOY_BACKEND.fetch(new Request(`https://toy.internal${path}`, { ...init, headers }));
  let payload = null;
  try { payload = await response.json(); }
  catch { payload = { error: `Toy backend returned HTTP ${response.status}` }; }
  if (!response.ok) throw new Error(payload?.detail || payload?.error || `Toy backend failed (${response.status})`);
  return payload;
}

async function toyStatus(env) {
  return toyFetch(env, "/status", { method: "GET" });
}

async function toyCommand(env, command) {
  return toyFetch(env, "/command", { method: "POST", body: JSON.stringify(command) });
}

export class WindowProbeMCP extends McpAgent {
  server = new McpServer(
    { name: "aevren-window-probe", version: PROBE_VERSION },
    { instructions: "掌心窗轻量桥接：统一调用掌心窗与 aevren-toy；涉及设备停止时，stop_device / stop_all 优先立即执行。" },
  );

  async init() {
    this.server.registerTool("ping_window_probe", {
      title: "Ping Window Probe",
      description: "验证当前 MCP transport 是否在线。",
      inputSchema: { text: z.string().max(120).optional() },
      annotations: { readOnlyHint: true, destructiveHint: false, openWorldHint: false },
    }, async ({ text }) => textResult({ ok: true, service: "aevren-window-probe", version: PROBE_VERSION, echo: text || "pong", at: new Date().toISOString() }));

    this.server.registerTool("get_phone_state", {
      title: "读取手机最近状态",
      description: "从现有掌心窗 D1 读取手机最近一次上报状态。只读。",
      inputSchema: { device_id: z.string().min(1).max(80).optional().default(DEFAULT_DEVICE) },
      annotations: { readOnlyHint: true, destructiveHint: false, openWorldHint: false },
    }, async ({ device_id }) => {
      const chosenDevice = String(device_id || DEFAULT_DEVICE).trim() || DEFAULT_DEVICE;
      try {
        if (!this.env?.DB) return { isError: true, ...textResult({ ok: false, error: "PROBE_DB_BINDING_MISSING" }) };
        const row = await this.env.DB.prepare("SELECT state_json FROM device_state WHERE device_id=?").bind(chosenDevice).first();
        let state = null;
        if (row?.state_json) {
          try { state = JSON.parse(row.state_json); } catch { state = { raw_state_json: String(row.state_json) }; }
        }
        return textResult({ ok: true, device_id: chosenDevice, state, life_state: state, source: "probe_d1_device_state" });
      } catch (error) {
        return { isError: true, ...textResult({ ok: false, error: "PROBE_DB_READ_FAILED", message: error instanceof Error ? error.message : String(error) }) };
      }
    });

    this.server.registerTool("window_bridge_stats", {
      title: "掌心窗旧工具统计",
      description: "按需读取旧掌心窗 tools/list，只返回统计。",
      inputSchema: {},
      annotations: { readOnlyHint: true, destructiveHint: false, openWorldHint: false },
    }, async () => {
      try {
        const tools = await listLegacyTools(this.env);
        const rows = tools.map((tool) => ({
          name: tool?.name || "",
          schema_bytes: JSON.stringify(tool?.inputSchema || {}).length,
          total_bytes: JSON.stringify(tool || {}).length,
          has_meta: Boolean(tool?._meta),
          has_annotations: Boolean(tool?.annotations),
        })).sort((a, b) => b.total_bytes - a.total_bytes);
        return textResult({ ok: true, tool_count: tools.length, tools_json_bytes: JSON.stringify(tools).length, tools_with_meta: rows.filter((r) => r.has_meta).length, tools_with_annotations: rows.filter((r) => r.has_annotations).length, largest_tools: rows.slice(0, 20) });
      } catch (error) {
        return { isError: true, ...textResult({ ok: false, error: "LEGACY_TOOL_STATS_FAILED", message: error instanceof Error ? error.message : String(error) }) };
      }
    });

    this.server.registerTool("window_action_help", {
      title: "查询掌心窗动作",
      description: "按名称或关键词查询旧掌心窗工具。精确名称返回完整 inputSchema。",
      inputSchema: { name: z.string().max(120).optional(), query: z.string().max(120).optional(), limit: z.number().int().min(1).max(20).optional().default(10) },
      annotations: { readOnlyHint: true, destructiveHint: false, openWorldHint: false },
    }, async ({ name, query, limit }) => {
      try {
        const tools = await listLegacyTools(this.env);
        const exact = String(name || "").trim();
        if (exact) {
          const hit = tools.find((tool) => tool?.name === exact);
          return textResult({ ok: true, found: Boolean(hit), tool: hit ? compactTool(hit, true) : null });
        }
        const needle = String(query || "").trim().toLowerCase();
        const matches = tools.filter((tool) => !needle || `${tool?.name || ""} ${tool?.title || ""} ${tool?.description || ""}`.toLowerCase().includes(needle)).slice(0, Number(limit || 10)).map((tool) => compactTool(tool, false));
        return textResult({ ok: true, total_tools: tools.length, query: needle, count: matches.length, tools: matches });
      } catch (error) {
        return { isError: true, ...textResult({ ok: false, error: "LEGACY_TOOL_HELP_FAILED", message: error instanceof Error ? error.message : String(error) }) };
      }
    });

    this.server.registerTool("window_call", {
      title: "调用掌心窗旧动作",
      description: "通过内部 backend 调用现有掌心窗 tools/call。控制或修改动作必须遵守用户当前意图与确认要求。",
      inputSchema: { name: z.string().min(1).max(120), arguments: z.record(z.string(), z.any()).optional() },
    }, async ({ name, arguments: args }) => {
      try {
        const tools = await listLegacyTools(this.env);
        if (!tools.some((item) => item?.name === name)) return { isError: true, ...textResult({ ok: false, error: "UNKNOWN_WINDOW_TOOL", name }) };
        return await legacyRpcWithEnv(this.env, "tools/call", { name, arguments: args || {} });
      } catch (error) {
        return { isError: true, ...textResult({ ok: false, error: "LEGACY_TOOL_CALL_FAILED", name, message: error instanceof Error ? error.message : String(error) }) };
      }
    });

    this.server.registerTool("toy_status", {
      title: "读取 Toy 状态",
      description: "读取 aevren-toy 手机桥接是否在线及当前 deviceId。只读。",
      inputSchema: {},
      annotations: { readOnlyHint: true, destructiveHint: false, openWorldHint: false },
    }, async () => {
      try { return textResult({ ok: true, ...(await toyStatus(this.env)) }); }
      catch (error) { return { isError: true, ...textResult({ ok: false, error: "TOY_STATUS_FAILED", message: error instanceof Error ? error.message : String(error) }) }; }
    });

    this.server.registerTool("toy_set", {
      title: "控制 Toy",
      description: "向已连接的 aevren-toy 设备发送短时 set 指令。",
      inputSchema: {
        intensity: z.number().int().min(0).max(100).optional(),
        mode: z.number().int().min(0).max(100).optional(),
        duration_sec: z.number().int().min(1).max(15).optional(),
        device_id: z.string().max(128).optional(),
      },
    }, async ({ intensity, mode, duration_sec, device_id }) => {
      try {
        return textResult(await toyCommand(this.env, { action: "set", intensity, mode, durationSec: duration_sec, deviceId: device_id }));
      } catch (error) {
        return { isError: true, ...textResult({ sent: false, error: "TOY_SET_FAILED", message: error instanceof Error ? error.message : String(error) }) };
      }
    });

    this.server.registerTool("toy_stop", {
      title: "停止 Toy",
      description: "立即停止一个 aevren-toy 设备。用户要求停止时应优先调用。",
      inputSchema: { device_id: z.string().max(128).optional() },
    }, async ({ device_id }) => {
      try { return textResult(await toyCommand(this.env, { action: "stop", deviceId: device_id })); }
      catch (error) { return { isError: true, ...textResult({ sent: false, error: "TOY_STOP_FAILED", message: error instanceof Error ? error.message : String(error) }) }; }
    });

    this.server.registerTool("toy_stop_all", {
      title: "全部停止 Toy",
      description: "立即停止所有 aevren-toy 设备。用户要求停止时应优先调用。",
      inputSchema: {},
    }, async () => {
      try { return textResult(await toyCommand(this.env, { action: "stop_all" })); }
      catch (error) { return { isError: true, ...textResult({ sent: false, error: "TOY_STOP_ALL_FAILED", message: error instanceof Error ? error.message : String(error) }) }; }
    });
  }
}

const mcpHandler = WindowProbeMCP.serve("/mcp", { binding: "WindowProbeMCP" });

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "POST, GET, DELETE, OPTIONS",
    "Access-Control-Allow-Headers": "content-type, mcp-session-id, mcp-protocol-version, last-event-id, authorization, x-auth-token, x-linjian-token",
    "Access-Control-Expose-Headers": "Mcp-Session-Id",
  };
}

function pathToken(url) {
  const prefix = "/mcp/";
  if (!url.pathname.startsWith(prefix)) return "";
  try { return decodeURIComponent(url.pathname.slice(prefix.length)); } catch { return ""; }
}

function tokenOk(request, env, url) {
  const expected = String(env?.LINJIAN_TOKEN || "");
  if (!expected) return false;
  const auth = request.headers.get("Authorization") || "";
  const bearer = auth.match(/^Bearer\s+(.+)$/i)?.[1]?.trim() || "";
  const supplied = request.headers.get("X-Auth-Token") || request.headers.get("X-Linjian-Token") || bearer || pathToken(url) || url.searchParams.get("token") || "";
  return supplied === expected;
}

function json(payload, status = 200) {
  return new Response(JSON.stringify(payload), { status, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", ...corsHeaders() } });
}

function cleanMcpRequest(request) {
  const cleanUrl = new URL(request.url);
  cleanUrl.pathname = "/mcp";
  cleanUrl.search = "";
  return new Request(cleanUrl.toString(), request);
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    if (url.pathname === "/health") {
      let mcpBinding = "missing";
      try {
        if (env?.WindowProbeMCP) {
          const id = env.WindowProbeMCP.idFromName("health-check");
          env.WindowProbeMCP.get(id);
          mcpBinding = "ok";
        }
      } catch (error) {
        mcpBinding = `error:${error instanceof Error ? error.message : String(error)}`;
      }
      return Response.json({
        ok: true,
        name: "aevren-window-probe",
        version: PROBE_VERSION,
        runtime: "cloudflare-workers",
        transport: "sessionful-streamable-http",
        mcp_binding: mcpBinding,
        db_configured: Boolean(env?.DB),
        kv_configured: Boolean(env?.SCREENSHOT_KV),
        token_configured: Boolean(env?.LINJIAN_TOKEN),
        backend_configured: Boolean(env?.WINDOW_BACKEND),
        toy_backend_configured: Boolean(env?.TOY_BACKEND),
        toy_token_configured: Boolean(env?.TOY_CONTROL_TOKEN),
        route: "/mcp/<LINJIAN_TOKEN>",
      });
    }

    const isMcpRoute = url.pathname === "/mcp" || url.pathname.startsWith("/mcp/");
    if (isMcpRoute) {
      if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders() });
      if (!env?.LINJIAN_TOKEN) return json({ ok: false, error: "PROBE_TOKEN_NOT_CONFIGURED" }, 503);
      const hasSession = Boolean(request.headers.get("Mcp-Session-Id"));
      if (!hasSession && !tokenOk(request, env, url)) return json({ jsonrpc: "2.0", id: null, error: { code: -32001, message: "LINJIAN_ERR_BAD_TOKEN" } }, 401);
      try { return await mcpHandler.fetch(cleanMcpRequest(request), env, ctx); }
      catch (error) {
        console.error("Window probe MCP handler failure", error);
        return json({ ok: false, stage: "mcp-handler", name: error instanceof Error ? error.name : "Error", message: error instanceof Error ? error.message : String(error) }, 500);
      }
    }

    if (url.pathname === "/" && (request.method === "GET" || request.method === "HEAD")) {
      return new Response(request.method === "HEAD" ? null : "Aevren Window MCP Probe", { status: 200, headers: { "content-type": "text/plain; charset=utf-8" } });
    }

    if (url.pathname.startsWith("/.well-known/")) return new Response("Not Found", { status: 404 });
    return new Response("Not Found", { status: 404 });
  },
};
