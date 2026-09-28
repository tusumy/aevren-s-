import { McpAgent } from "agents/mcp";
import { createMcpHandler } from "agents/mcp/server";
import { McpServer as LegacyMcpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { McpServer as StatelessMcpServer } from "@modelcontextprotocol/server";
import { env as workerEnv } from "cloudflare:workers";
import { z } from "zod";
import legacyWorker from "./worker.js";

const MCP_TRANSPORT = "stateless-createMcpHandler-v2";
const MCP_SERVER_VERSION = "0.3.8.12-cf";

function mcpCorsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "POST, GET, DELETE, OPTIONS",
    "Access-Control-Allow-Headers":
      "authorization, content-type, x-auth-token, x-linjian-token, mcp-session-id, mcp-protocol-version, last-event-id",
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
      ...mcpCorsHeaders(),
    },
  });
}

function zodForSchema(schema = {}) {
  const rawType = schema?.type;
  const types = Array.isArray(rawType) ? rawType : [rawType || "string"];
  const nullable = types.includes("null");
  const type = types.find((value) => value !== "null") || "string";

  let out;
  if (type === "string") out = z.string();
  else if (type === "integer") out = z.number().int();
  else if (type === "number") out = z.number();
  else if (type === "boolean") out = z.boolean();
  else if (type === "array") out = z.array(zodForSchema(schema.items || {}));
  else if (type === "object") {
    const properties = schema.properties || {};
    if (!Object.keys(properties).length && schema.additionalProperties === true) {
      out = z.record(z.string(), z.any());
    } else {
      const required = new Set(Array.isArray(schema.required) ? schema.required : []);
      const shape = {};
      for (const [key, childSchema] of Object.entries(properties)) {
        let child = zodForSchema(childSchema || {});
        if (Object.prototype.hasOwnProperty.call(childSchema || {}, "default")) {
          child = child.default(childSchema.default);
        } else if (!required.has(key)) {
          child = child.optional();
        }
        shape[key] = child;
      }
      out = z.object(shape);
      if (schema.additionalProperties === true) out = out.passthrough();
    }
  } else {
    out = z.any();
  }

  if (nullable) out = out.nullable();
  return out;
}

function rootShape(schema = {}) {
  const properties = schema?.properties || {};
  const required = new Set(Array.isArray(schema?.required) ? schema.required : []);
  const shape = {};
  for (const [key, childSchema] of Object.entries(properties)) {
    let child = zodForSchema(childSchema || {});
    if (Object.prototype.hasOwnProperty.call(childSchema || {}, "default")) {
      child = child.default(childSchema.default);
    } else if (!required.has(key)) {
      child = child.optional();
    }
    shape[key] = child;
  }
  return shape;
}

async function legacyRpcWithEnv(env, method, params = {}) {
  const token = String(env?.LINJIAN_TOKEN || "");
  if (!token) throw new Error("LINJIAN_TOKEN is not configured in runtime.");

  const request = new Request(
    `https://legacy.internal/mcp?token=${encodeURIComponent(token)}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ jsonrpc: "2.0", id: crypto.randomUUID(), method, params }),
    },
  );
  const response = await legacyWorker.fetch(request, env);
  const payload = await response.json();
  if (!response.ok || payload?.error) {
    const message = payload?.error?.message || payload?.error || `Legacy MCP RPC failed with ${response.status}`;
    throw new Error(typeof message === "string" ? message : JSON.stringify(message));
  }
  return payload?.result;
}

async function createStatelessServer() {
  const server = new StatelessMcpServer(
    { name: "aevren-window", version: MCP_SERVER_VERSION },
    {
      instructions:
        "掌心窗连接阿毛的手机状态与已授权控制能力。读取状态时优先使用 get_life_state / get_phone_state；执行动作前遵守工具描述和确认要求。",
    },
  );

  const listed = await legacyRpcWithEnv(workerEnv, "tools/list");
  const tools = Array.isArray(listed?.tools) ? listed.tools : [];
  for (const tool of tools) {
    server.registerTool(
      tool.name,
      {
        title: tool.title,
        description: tool.description || "",
        inputSchema: rootShape(tool.inputSchema || { type: "object", properties: {} }),
        annotations: tool.annotations,
        _meta: tool._meta,
      },
      async (args) => {
        try {
          return await legacyRpcWithEnv(workerEnv, "tools/call", {
            name: tool.name,
            arguments: args || {},
          });
        } catch (error) {
          return {
            isError: true,
            content: [
              {
                type: "text",
                text: error instanceof Error ? error.message : String(error),
              },
            ],
          };
        }
      },
    );
  }

  const listedResources = await legacyRpcWithEnv(workerEnv, "resources/list");
  const resources = Array.isArray(listedResources?.resources) ? listedResources.resources : [];
  for (const resource of resources) {
    server.registerResource(
      resource.name || resource.uri,
      resource.uri,
      {
        title: resource.name || resource.uri,
        description: resource.description || "",
        mimeType: resource.mimeType,
      },
      async () => legacyRpcWithEnv(workerEnv, "resources/read", { uri: resource.uri }),
    );
  }

  return server;
}

const statelessHandler = createMcpHandler(createStatelessServer, {
  route: "/mcp",
  legacy: "stateless",
  responseMode: "auto",
  onerror(error) {
    console.error("stateless MCP handler failure", error);
  },
});

// Keep the old Durable Object class exported for the already-created binding/migration.
// The public /mcp route below no longer uses it.
export class WindowMCP extends McpAgent {
  server = new LegacyMcpServer({ name: "aevren-window-legacy", version: MCP_SERVER_VERSION });
  async init() {}
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    if (url.pathname === "/mcp-transport") {
      return json({
        ok: true,
        transport: MCP_TRANSPORT,
        token_configured: Boolean(env?.LINJIAN_TOKEN),
        old_render_transport: "stateless-streamable-http",
      });
    }

    if (url.pathname !== "/mcp") {
      return legacyWorker.fetch(request, env, ctx);
    }

    if (request.method !== "OPTIONS" && !tokenOk(request, env, url)) {
      return json(
        { jsonrpc: "2.0", id: null, error: { code: -32001, message: "LINJIAN_ERR_BAD_TOKEN" } },
        401,
      );
    }

    try {
      return await statelessHandler(request, env, ctx);
    } catch (error) {
      console.error("stateless MCP route failure", error);
      return json(
        {
          ok: false,
          stage: "stateless-mcp-handler",
          name: error instanceof Error ? error.name : "Error",
          message: error instanceof Error ? error.message : String(error),
        },
        500,
      );
    }
  },
};
