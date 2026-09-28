import legacyWorker from "./worker.js";

const MCP_PROTOCOL_VERSION = "2025-06-18";
const MCP_TRANSPORT = "sessionful-shim-v1";

function mcpCorsHeaders(extra = {}) {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "POST, GET, DELETE, OPTIONS",
    "Access-Control-Allow-Headers":
      "authorization, content-type, x-auth-token, x-linjian-token, mcp-session-id, mcp-protocol-version, last-event-id",
    "Access-Control-Expose-Headers": "Mcp-Session-Id, MCP-Protocol-Version",
    ...extra,
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

function withMcpHeaders(response, sessionId = "") {
  const headers = new Headers(response.headers);
  for (const [key, value] of Object.entries(mcpCorsHeaders())) {
    headers.set(key, value);
  }
  headers.set("MCP-Protocol-Version", MCP_PROTOCOL_VERSION);
  if (sessionId) headers.set("Mcp-Session-Id", sessionId);
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}

function json(payload, status = 200, extra = {}) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: mcpCorsHeaders({
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "MCP-Protocol-Version": MCP_PROTOCOL_VERSION,
      ...extra,
    }),
  });
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    if (url.pathname === "/mcp-transport") {
      return json({
        ok: true,
        transport: MCP_TRANSPORT,
        protocol_version: MCP_PROTOCOL_VERSION,
        token_configured: Boolean(env?.LINJIAN_TOKEN),
      });
    }

    if (url.pathname !== "/mcp") {
      return legacyWorker.fetch(request, env, ctx);
    }

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: mcpCorsHeaders() });
    }

    // Streamable HTTP clients may probe GET. This server does not need a
    // server-initiated SSE stream, so advertise POST-only operation cleanly.
    if (request.method === "GET") {
      return new Response(null, {
        status: 405,
        headers: mcpCorsHeaders({
          Allow: "POST, DELETE, OPTIONS",
          "MCP-Protocol-Version": MCP_PROTOCOL_VERSION,
        }),
      });
    }

    if (request.method === "DELETE") {
      if (!tokenOk(request, env, url)) {
        return json(
          { jsonrpc: "2.0", id: null, error: { code: -32001, message: "LINJIAN_ERR_BAD_TOKEN" } },
          401,
        );
      }
      return new Response(null, {
        status: 204,
        headers: mcpCorsHeaders({ "MCP-Protocol-Version": MCP_PROTOCOL_VERSION }),
      });
    }

    if (request.method !== "POST") {
      return json(
        { jsonrpc: "2.0", id: null, error: { code: -32000, message: "Use POST /mcp for MCP JSON-RPC." } },
        405,
      );
    }

    if (!tokenOk(request, env, url)) {
      return json(
        { jsonrpc: "2.0", id: null, error: { code: -32001, message: "LINJIAN_ERR_BAD_TOKEN" } },
        401,
      );
    }

    let message = null;
    try {
      message = await request.clone().json();
    } catch (_) {
      return json(
        { jsonrpc: "2.0", id: null, error: { code: -32700, message: "Parse error" } },
        400,
      );
    }

    const incomingSession = request.headers.get("Mcp-Session-Id") || "";
    const method = Array.isArray(message) ? "" : String(message?.method || "");

    // Keep the existing tool implementation, but make the transport match the
    // sessionful Streamable HTTP behaviour ChatGPT accepted for heartbar.
    const legacyResponse = await legacyWorker.fetch(request, env, ctx);

    if (method === "initialize" && legacyResponse.ok) {
      const sessionId = crypto.randomUUID();
      return withMcpHeaders(legacyResponse, sessionId);
    }

    if (method === "notifications/initialized" && legacyResponse.status === 204) {
      return new Response(null, {
        status: 202,
        headers: mcpCorsHeaders({
          "MCP-Protocol-Version": MCP_PROTOCOL_VERSION,
          ...(incomingSession ? { "Mcp-Session-Id": incomingSession } : {}),
        }),
      });
    }

    return withMcpHeaders(legacyResponse, incomingSession);
  },
};
