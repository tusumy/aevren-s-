import { McpAgent } from "agents/mcp";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

export class WindowProbeMCP extends McpAgent {
  server = new McpServer(
    { name: "aevren-window-probe", version: "0.1.0" },
    {
      instructions:
        "Minimal isolated MCP probe used only to verify ChatGPT ↔ Cloudflare MCP connectivity.",
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
      async ({ text }) => ({
        content: [
          {
            type: "text",
            text: JSON.stringify({
              ok: true,
              service: "aevren-window-probe",
              echo: text || "pong",
              at: new Date().toISOString(),
            }),
          },
        ],
      }),
    );
  }
}

const mcpHandler = WindowProbeMCP.serve("/mcp", { binding: "WindowProbeMCP" });

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
        version: "0.1.0",
        runtime: "cloudflare-workers",
        transport: "sessionful-streamable-http",
        binding,
      });
    }

    if (url.pathname === "/mcp") {
      if (request.method === "OPTIONS") {
        return new Response(null, {
          status: 204,
          headers: {
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "POST, GET, DELETE, OPTIONS",
            "Access-Control-Allow-Headers":
              "content-type, mcp-session-id, mcp-protocol-version, last-event-id, authorization",
            "Access-Control-Expose-Headers": "Mcp-Session-Id",
          },
        });
      }

      try {
        return await mcpHandler.fetch(request, env, ctx);
      } catch (error) {
        console.error("Window probe MCP handler failure", error);
        return Response.json(
          {
            ok: false,
            stage: "mcp-handler",
            name: error instanceof Error ? error.name : "Error",
            message: error instanceof Error ? error.message : String(error),
          },
          { status: 500 },
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
