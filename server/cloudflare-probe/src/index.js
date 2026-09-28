import { McpAgent } from "agents/mcp";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import legacyWorker from "../../cloudflare-worker/worker.js";

const PROBE_VERSION = "0.4.0-full-bridge";

function textResult(payload) {
  return {
    content: [{ type: "text", text: JSON.stringify(payload, null, 2) }],
  };
}

function zodForSchema(schema = {}) {
  if (Object.prototype.hasOwnProperty.call(schema || {}, "const")) {
    return z.literal(schema.const);
  }

  if (Array.isArray(schema?.enum) && schema.enum.length) {
    const values = schema.enum;
    if (values.every((value) => typeof value === "string")) {
      return z.enum(values);
    }
    return z.union(values.map((value) => z.literal(value)));
  }

  const variants = schema?.anyOf || schema?.oneOf;
  if (Array.isArray(variants) && variants.length) {
    const converted = variants.map((part) => zodForSchema(part || {}));
    return converted.length === 1 ? converted[0] : z.union(converted);
  }

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
  if (!token) throw new Error("LINJIAN_TOKEN is not configured in probe runtime.");

  const request = new Request(
    `https://legacy.internal/mcp?token=${encodeURIComponent(token)}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        jsonrpc: "2.0",
        id: crypto.randomUUID(),
        method,
        params,
      }),
    },
  );

  const response = await legacyWorker.fetch(request, env);
  const payload = await response.json();
  if (!response.ok || payload?.error) {
    const message =
      payload?.error?.message ||
      payload?.error ||
      `Legacy MCP RPC failed with ${response.status}`;
    throw new Error(typeof message === "string" ? message : JSON.stringify(message));
  }
  return payload?.result;
}

export class WindowProbeMCP extends McpAgent {
  bridgeStatus = {
    initialized: false,
    tool_count: 0,
    resource_count: 0,
    tool_errors: [],
    resource_errors: [],
    fatal_error: "",
  };

  server = new McpServer(
    { name: "aevren-window-probe", version: PROBE_VERSION },
    {
      instructions:
        "掌心窗验证外壳：使用已经验证可连接的 Cloudflare McpAgent transport，整块复用现有掌心窗业务工具。",
    },
  );

  async init() {
    this.server.registerTool(
      "ping_window_probe",
      {
        title: "Ping Window Probe",
        description: "Return a tiny success payload from the verified MCP transport.",
        inputSchema: { text: z.string().max(120).optional() },
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
          version: PROBE_VERSION,
          echo: text || "pong",
          at: new Date().toISOString(),
        }),
    );

    this.server.registerTool(
      "probe_bridge_status",
      {
        title: "掌心窗桥接状态",
        description: "只读查看本次探针启动时批量桥接旧掌心窗工具和资源的结果。",
        inputSchema: {},
        annotations: {
          readOnlyHint: true,
          destructiveHint: false,
          openWorldHint: false,
        },
      },
      async () => textResult(this.bridgeStatus),
    );

    try {
      const listed = await legacyRpcWithEnv(this.env, "tools/list");
      const tools = Array.isArray(listed?.tools) ? listed.tools : [];

      for (const tool of tools) {
        try {
          this.server.registerTool(
            tool.name,
            {
              title: tool.title,
              description: tool.description || "",
              inputSchema: rootShape(
                tool.inputSchema || { type: "object", properties: {} },
              ),
              annotations: tool.annotations,
              _meta: tool._meta,
            },
            async (args) => {
              try {
                return await legacyRpcWithEnv(this.env, "tools/call", {
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
          this.bridgeStatus.tool_count += 1;
        } catch (error) {
          this.bridgeStatus.tool_errors.push({
            name: tool?.name || "unknown",
            message: error instanceof Error ? error.message : String(error),
          });
        }
      }

      try {
        const listedResources = await legacyRpcWithEnv(this.env, "resources/list");
        const resources = Array.isArray(listedResources?.resources)
          ? listedResources.resources
          : [];

        for (const resource of resources) {
          try {
            this.server.registerResource(
              resource.name || resource.uri,
              resource.uri,
              {
                title: resource.name || resource.uri,
                description: resource.description || "",
                mimeType: resource.mimeType,
              },
              async () =>
                legacyRpcWithEnv(this.env, "resources/read", {
                  uri: resource.uri,
                }),
            );
            this.bridgeStatus.resource_count += 1;
          } catch (error) {
            this.bridgeStatus.resource_errors.push({
              uri: resource?.uri || "unknown",
              message: error instanceof Error ? error.message : String(error),
            });
          }
        }
      } catch (error) {
        this.bridgeStatus.resource_errors.push({
          uri: "resources/list",
          message: error instanceof Error ? error.message : String(error),
        });
      }

      this.bridgeStatus.initialized = true;
    } catch (error) {
      this.bridgeStatus.fatal_error =
        error instanceof Error ? error.message : String(error);
      console.error("Probe legacy bridge initialization failed", error);
    }
  }
}

const mcpHandler = WindowProbeMCP.serve("/mcp", { binding: "WindowProbeMCP" });

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
        version: PROBE_VERSION,
        runtime: "cloudflare-workers",
        transport: "sessionful-streamable-http",
        binding,
        db_configured: Boolean(env?.DB),
        kv_configured: Boolean(env?.SCREENSHOT_KV),
        token_configured: Boolean(env?.LINJIAN_TOKEN),
        route: "/mcp?token=...",
      });
    }

    if (url.pathname === "/mcp") {
      if (request.method === "OPTIONS") {
        return new Response(null, { status: 204, headers: corsHeaders() });
      }

      if (!env?.LINJIAN_TOKEN) {
        return json({ ok: false, error: "PROBE_TOKEN_NOT_CONFIGURED" }, 503);
      }
      if (!tokenOk(request, env, url)) {
        return json(
          {
            jsonrpc: "2.0",
            id: null,
            error: { code: -32001, message: "LINJIAN_ERR_BAD_TOKEN" },
          },
          401,
        );
      }

      try {
        return await mcpHandler.fetch(request, env, ctx);
      } catch (error) {
        console.error("Window probe MCP handler failure", error);
        return json(
          {
            ok: false,
            stage: "mcp-handler",
            name: error instanceof Error ? error.name : "Error",
            message: error instanceof Error ? error.message : String(error),
          },
          500,
        );
      }
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
