import { McpAgent } from "agents/mcp";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

const DEFAULT_DEVICE = "android-phone";

function textResult(payload) {
  return {
    content: [{ type: "text", text: JSON.stringify(payload, null, 2) }],
  };
}

export class WindowProbeMCP extends McpAgent {
  server = new McpServer(
    { name: "aevren-window-probe", version: "0.3.0" },
    {
      instructions:
        "Isolated MCP probe used to rebuild 掌心窗 one verified layer at a time.",
    },
  );

  async init() {
    this.server.registerTool(
      "ping_window_probe",
      {
        title: "Ping Window Probe",
        description: "Return a tiny success payload. This tool does not access the phone, D1, KV, or any private data.",
        inputSchema: {
          text: z.string().max(120).optional(),
        },
        annotations: {
          readOnlyHint: true,
          destructiveHint: false,
          openWorldHint: false,
        },
      },
      async ({ text }) =>
        textResult({
          ok: true,
          service: "aevren-window-probe",
          echo: text || "pong",
          at: new Date().toISOString(),
        }),
    );

    this.server.registerTool(
      "get_phone_state",
      {
        title: "读取手机最近状态",
        description: "从掌心窗现有 D1 device_state 表读取手机最近一次上报状态。只读，不下发任何控制命令。",
        inputSchema: {
          device_id: z.string().min(1).max(80).optional().default(DEFAULT_DEVICE),
        },
        annotations: {
          readOnlyHint: true,
          destructiveHint: false,
          openWorldHint: false,
        },
      },
      async ({ device_id }) => {
        const chosenDevice = String(device_id || DEFAULT_DEVICE).trim() || DEFAULT_DEVICE;
        try {
          if (!this.env?.DB) {
            return {
              isError: true,
              ...textResult({ ok: false, error: "PROBE_DB_BINDING_MISSING" }),
            };
          }

          const row = await this.env.DB.prepare(
            "SELECT state_json FROM device_state WHERE device_id=?",
          )
            .bind(chosenDevice)
            .first();

          let state = null;
          if (row?.state_json) {
            try {
              state = JSON.parse(row.state_json);
            } catch {
              state = { raw_state_json: String(row.state_json) };
            }
          }

          return textResult({
            ok: true,
            device_id: chosenDevice,
            state,
            life_state: state,
            source: "probe_d1_device_state",
          });
        } catch (error) {
          return {
            isError: true,
            ...textResult({
              ok: false,
              error: "PROBE_DB_READ_FAILED",
              message: error instanceof Error ? error.message : String(error),
            }),
          };
        }
      },
    );
  }
}

const publicMcpHandler = WindowProbeMCP.serve("/mcp", { binding: "WindowProbeMCP" });
const tokenMcpHandler = WindowProbeMCP.serve("/mcp-token", { binding: "WindowProbeMCP" });

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "POST, GET, DELETE, OPTIONS",
    "Access-Control-Allow-Headers":
      "content-type, mcp-session-id, mcp-protocol-version, last-event-id, authorization, x-auth-token, x-linjian-token",
    "Access-Control-Expose-Headers": "Mcp-Session-Id",
  };
}

function tokenOk(request, env, url) {
  const expected = String(env?.LINJIAN_TOKEN || "");
  if (!expected) return false;
  const auth = request.headers.get("Authorization") || "";
  const bearer = auth.match(/^Bearer\s+(.+)$/i)?.[1]?.trim() || "";
  const supplied =
    request.headers.get("X-Auth-Token") ||
    request.headers.get("X-Linjian-Token") ||
    bearer ||
    url.searchParams.get("token") ||
    "";
  return supplied === expected;
}

function json(payload, status = 200) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      ...corsHeaders(),
    },
  });
}

async function runHandler(handler, request, env, ctx, label) {
  try {
    return await handler.fetch(request, env, ctx);
  } catch (error) {
    console.error(`${label} MCP handler failure`, error);
    return json(
      {
        ok: false,
        stage: label,
        name: error instanceof Error ? error.name : "Error",
        message: error instanceof Error ? error.message : String(error),
      },
      500,
    );
  }
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    if (url.pathname === "/health") {
      let binding = "missing";
      try {
        if (env?.WindowProbeMCP) {
          const id = env.WindowProbeMCP.idFromName("health-check");
          env.WindowProbeMCP.get(id);
          binding = "ok";
        }
      } catch (error) {
        binding = `error:${error instanceof Error ? error.message : String(error)}`;
      }

      return Response.json({
        ok: true,
        name: "aevren-window-probe",
        version: "0.3.0",
        runtime: "cloudflare-workers",
        transport: "sessionful-streamable-http",
        binding,
        db_configured: Boolean(env?.DB),
        token_configured: Boolean(env?.LINJIAN_TOKEN),
        tools: ["ping_window_probe", "get_phone_state"],
        routes: {
          public: "/mcp",
          token: "/mcp-token?token=...",
        },
      });
    }

    if (url.pathname === "/mcp" || url.pathname === "/mcp-token") {
      if (request.method === "OPTIONS") {
        return new Response(null, { status: 204, headers: corsHeaders() });
      }

      if (url.pathname === "/mcp-token") {
        if (!env?.LINJIAN_TOKEN) {
          return json({ ok: false, error: "PROBE_TOKEN_NOT_CONFIGURED" }, 503);
        }
        if (!tokenOk(request, env, url)) {
          return json(
            { jsonrpc: "2.0", id: null, error: { code: -32001, message: "LINJIAN_ERR_BAD_TOKEN" } },
            401,
          );
        }
        return runHandler(tokenMcpHandler, request, env, ctx, "token-probe");
      }

      return runHandler(publicMcpHandler, request, env, ctx, "public-probe");
    }

    if (url.pathname === "/" && (request.method === "GET" || request.method === "HEAD")) {
      return new Response(request.method === "HEAD" ? null : "Aevren Window MCP Probe", {
        status: 200,
        headers: { "content-type": "text/plain; charset=utf-8" },
      });
    }

    if (url.pathname.startsWith("/.well-known/")) {
      return new Response("Not Found", { status: 404 });
    }

    return new Response("Not Found", { status: 404 });
  },
};
