import express from "express";
import dns from "node:dns/promises";
import net from "node:net";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { z } from "zod";

const PORT = Number(process.env.PORT || 8791);
const GEMINI_API_KEY = (process.env.SENSORY_GEMINI_API_KEY || "").trim();
const MODEL = (process.env.SENSORY_MODEL || "gemini-2.5-flash").trim();
const ACCESS_TOKEN = (process.env.SENSORY_ACCESS_TOKEN || "").trim();
const MAX_MEDIA_BYTES = Number(process.env.SENSORY_MAX_MEDIA_BYTES || 18 * 1024 * 1024);
const MEDIA_TIMEOUT_MS = Number(process.env.SENSORY_MEDIA_TIMEOUT_MS || 20000);
const PROVIDER_TIMEOUT_MS = Number(process.env.SENSORY_PROVIDER_TIMEOUT_MS || 60000);

function jsonText(value) {
  return {
    content: [{ type: "text", text: JSON.stringify(value, null, 2) }],
    structuredContent: value
  };
}

function isPrivateIp(ip) {
  if (!net.isIP(ip)) return false;
  if (ip === "::1" || ip === "::" || ip.startsWith("fe80:") || ip.startsWith("fc") || ip.startsWith("fd")) return true;
  if (net.isIPv4(ip)) {
    const [a, b] = ip.split(".").map(Number);
    if (a === 10 || a === 127 || a === 0) return true;
    if (a === 169 && b === 254) return true;
    if (a === 172 && b >= 16 && b <= 31) return true;
    if (a === 192 && b === 168) return true;
    if (a >= 224) return true;
  }
  return false;
}

async function assertSafeHttpsUrl(raw) {
  let url;
  try {
    url = new URL(raw);
  } catch {
    throw new Error("source_url must be a valid URL");
  }
  if (url.protocol !== "https:") throw new Error("source_url must use HTTPS");
  if (url.username || url.password) throw new Error("source_url must not contain credentials");
  const host = url.hostname.toLowerCase();
  if (host === "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) {
    throw new Error("localhost/private media URLs are not allowed");
  }
  if (net.isIP(host) && isPrivateIp(host)) throw new Error("private network media URLs are not allowed");
  try {
    const addresses = await dns.lookup(host, { all: true, verbatim: true });
    if (!addresses.length || addresses.some((x) => isPrivateIp(x.address))) {
      throw new Error("private network media URLs are not allowed");
    }
  } catch (error) {
    if (String(error?.message || error).includes("private network")) throw error;
    throw new Error("source_url hostname could not be resolved");
  }
  return url;
}

async function readResponseLimited(response, maxBytes) {
  const declared = Number(response.headers.get("content-length") || 0);
  if (declared && declared > maxBytes) throw new Error(`media exceeds ${maxBytes} byte limit`);
  if (!response.body) return Buffer.alloc(0);
  const reader = response.body.getReader();
  const chunks = [];
  let total = 0;
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > maxBytes) throw new Error(`media exceeds ${maxBytes} byte limit`);
      chunks.push(Buffer.from(value));
    }
  } finally {
    reader.releaseLock();
  }
  return Buffer.concat(chunks);
}

async function fetchMedia(sourceUrl) {
  let current = await assertSafeHttpsUrl(sourceUrl);
  for (let hop = 0; hop <= 3; hop += 1) {
    const response = await fetch(current, {
      redirect: "manual",
      signal: AbortSignal.timeout(MEDIA_TIMEOUT_MS),
      headers: { "user-agent": "aevren-sensory/0.1" }
    });
    if ([301, 302, 303, 307, 308].includes(response.status)) {
      const location = response.headers.get("location");
      if (!location) throw new Error("media redirect has no location");
      current = await assertSafeHttpsUrl(new URL(location, current).toString());
      continue;
    }
    if (!response.ok) throw new Error(`media fetch failed: HTTP ${response.status}`);
    const mimeType = (response.headers.get("content-type") || "").split(";")[0].trim().toLowerCase();
    const buffer = await readResponseLimited(response, MAX_MEDIA_BYTES);
    return { buffer, mimeType, finalUrl: current.toString() };
  }
  throw new Error("too many media redirects");
}

function decodeBase64Media(data, mimeType) {
  const clean = String(data || "").replace(/^data:[^;]+;base64,/, "").trim();
  if (!clean) throw new Error("data_base64 is empty");
  const estimated = Math.floor((clean.length * 3) / 4);
  if (estimated > MAX_MEDIA_BYTES) throw new Error(`media exceeds ${MAX_MEDIA_BYTES} byte limit`);
  const buffer = Buffer.from(clean, "base64");
  if (!buffer.length) throw new Error("data_base64 is invalid");
  return { buffer, mimeType: String(mimeType || "").trim().toLowerCase(), finalUrl: "" };
}

function normalizeKind(kind, mimeType = "") {
  const requested = String(kind || "").toLowerCase();
  if (["audio", "music", "video", "image"].includes(requested)) return requested;
  if (mimeType.startsWith("video/")) return "video";
  if (mimeType.startsWith("audio/")) return "audio";
  if (mimeType.startsWith("image/")) return "image";
  throw new Error("kind must be audio, music, video, or image");
}

function validateMime(kind, mimeType) {
  if (!mimeType) throw new Error("media MIME type is missing");
  if (kind === "video" && !mimeType.startsWith("video/")) throw new Error("video sensing requires video/* media");
  if ((kind === "audio" || kind === "music") && !mimeType.startsWith("audio/")) throw new Error("audio/music sensing requires audio/* media");
  if (kind === "image" && !mimeType.startsWith("image/")) throw new Error("image sensing requires image/* media");
}

function promptFor({ kind, question, language, detail }) {
  const specific = String(question || "").trim();
  const lang = String(language || "zh-CN").trim();
  const level = String(detail || "auto").trim();
  const shared = `You are a sensory observation layer, not a conversational assistant. Inspect only the supplied ${kind} media. Return factual observations in ${lang}. Do not invent inaudible speech or unseen details. If uncertain, mark uncertainty. Detail level: ${level}.`;
  const schemas = {
    audio: 'Return JSON with keys: summary, transcript, speakers, non_speech_events, timeline, uncertainty. timeline items should use start_seconds/end_seconds when reasonably inferable.',
    music: 'Return JSON with keys: summary, vocals_or_lyrics, structure, rhythm, instrumentation, notable_moments, timeline, uncertainty. Do not reproduce long copyrighted lyrics; summarize them.',
    video: 'Return JSON with keys: summary, visual_events, speech_or_audio, timeline, notable_moments, uncertainty. Fuse picture and soundtrack by time when possible.',
    image: 'Return JSON with keys: summary, visible_text, objects, spatial_details, uncertainty.'
  };
  return [shared, schemas[kind], specific ? `User focus: ${specific}` : ""].filter(Boolean).join("\n");
}

function parseProviderJson(text) {
  const raw = String(text || "").trim();
  const cleaned = raw.replace(/^\`\`\`(?:json)?\s*/i, "").replace(/\s*\`\`\`$/, "");
  try {
    return JSON.parse(cleaned);
  } catch {
    return { summary: cleaned || "Provider returned no text.", raw_text: cleaned, parse_warning: true };
  }
}

async function callGemini({ kind, buffer, mimeType, question = "", language = "zh-CN", detail = "auto" }) {
  if (!GEMINI_API_KEY) throw new Error("SENSORY_GEMINI_API_KEY is not configured");
  const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(MODEL)}:generateContent`;
  const body = {
    contents: [{
      role: "user",
      parts: [
        { inlineData: { mimeType, data: buffer.toString("base64") } },
        { text: promptFor({ kind, question, language, detail }) }
      ]
    }],
    generationConfig: {
      responseMimeType: "application/json",
      temperature: 0.1
    }
  };
  const response = await fetch(endpoint, {
    method: "POST",
    signal: AbortSignal.timeout(PROVIDER_TIMEOUT_MS),
    headers: {
      "content-type": "application/json",
      "x-goog-api-key": GEMINI_API_KEY
    },
    body: JSON.stringify(body)
  });
  const raw = await response.text();
  if (!response.ok) {
    let detailText = raw;
    try {
      const parsed = JSON.parse(raw);
      detailText = parsed?.error?.message || raw;
    } catch {}
    throw new Error(`Gemini HTTP ${response.status}: ${String(detailText).slice(0, 500)}`);
  }
  const data = JSON.parse(raw);
  const text = (data.candidates?.[0]?.content?.parts || []).map((p) => p.text || "").join("\n").trim();
  return {
    provider: "gemini",
    model: MODEL,
    observation: parseProviderJson(text),
    usage: data.usageMetadata || null
  };
}

async function prepareAndSense(args = {}) {
  const hasUrl = Boolean(String(args.source_url || "").trim());
  const hasData = Boolean(String(args.data_base64 || "").trim());
  if (hasUrl === hasData) throw new Error("Provide exactly one of source_url or data_base64");

  const media = hasUrl
    ? await fetchMedia(String(args.source_url).trim())
    : decodeBase64Media(args.data_base64, args.mime_type);

  const mimeType = media.mimeType || String(args.mime_type || "").trim().toLowerCase();
  const kind = normalizeKind(args.kind, mimeType);
  validateMime(kind, mimeType);

  const result = await callGemini({
    kind,
    buffer: media.buffer,
    mimeType,
    question: args.question,
    language: args.language,
    detail: args.detail
  });

  return {
    ok: true,
    kind,
    mime_type: mimeType,
    bytes: media.buffer.length,
    source_url: media.finalUrl || undefined,
    ...result
  };
}

const commonSchema = {
  source_url: z.string().default("").describe("Direct HTTPS URL to media. Use exactly one of source_url or data_base64."),
  data_base64: z.string().default("").describe("Base64 media bytes for small media. Use exactly one of data_base64 or source_url."),
  mime_type: z.string().default("").describe("Required with data_base64, e.g. audio/mpeg or video/mp4. For URLs it can override a missing server Content-Type."),
  question: z.string().default("").describe("What to pay special attention to."),
  language: z.string().default("zh-CN"),
  detail: z.enum(["low", "auto", "high"]).default("auto")
};

function makeServer() {
  const server = new McpServer({ name: "aevren-sensory", version: "0.1.0" });

  server.tool("sensory_status", "Check whether Aevren sensory service is configured. Does not expose secrets.", {}, async () =>
    jsonText({
      ok: true,
      service: "aevren-sensory",
      version: "0.1.0",
      provider: "gemini",
      model: MODEL,
      provider_configured: Boolean(GEMINI_API_KEY),
      access_protected: Boolean(ACCESS_TOKEN),
      max_media_bytes: MAX_MEDIA_BYTES,
      supports: ["audio", "music", "video", "image"]
    })
  );

  server.tool("sense_audio", "Listen to an explicitly supplied audio clip. Returns transcript, non-speech events and a time-oriented observation.", commonSchema, async (args) => {
    try { return jsonText(await prepareAndSense({ ...args, kind: "audio" })); }
    catch (error) { return jsonText({ ok: false, error: String(error?.message || error) }); }
  });

  server.tool("sense_music", "Inspect an explicitly supplied music clip: structure, rhythm, instrumentation and notable moments.", commonSchema, async (args) => {
    try { return jsonText(await prepareAndSense({ ...args, kind: "music" })); }
    catch (error) { return jsonText({ ok: false, error: String(error?.message || error) }); }
  });

  server.tool("sense_video", "Watch and listen to an explicitly supplied video clip and align notable visual/audio moments.", commonSchema, async (args) => {
    try { return jsonText(await prepareAndSense({ ...args, kind: "video" })); }
    catch (error) { return jsonText({ ok: false, error: String(error?.message || error) }); }
  });

  server.tool("sense_image", "Inspect an explicitly supplied image or GIF frame as visual media.", commonSchema, async (args) => {
    try { return jsonText(await prepareAndSense({ ...args, kind: "image" })); }
    catch (error) { return jsonText({ ok: false, error: String(error?.message || error) }); }
  });

  return server;
}

function tokenFromRequest(req) {
  const auth = String(req.headers.authorization || "");
  const bearer = auth.match(/^Bearer\s+(.+)$/i)?.[1] || "";
  return bearer || String(req.params?.token || req.query?.token || "");
}

function authorized(req) {
  return !ACCESS_TOKEN || tokenFromRequest(req) === ACCESS_TOKEN;
}

const app = express();
app.disable("x-powered-by");
app.use((req, res, next) => {
  const origin = req.headers.origin || "*";
  res.setHeader("Access-Control-Allow-Origin", origin);
  res.setHeader("Vary", "Origin");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, MCP-Protocol-Version, MCP-Session-Id, Mcp-Session-Id, Accept");
  res.setHeader("Access-Control-Expose-Headers", "MCP-Session-Id, Mcp-Session-Id, WWW-Authenticate");
  if (req.method === "OPTIONS") return res.status(204).end();
  next();
});
app.use(express.json({ limit: "26mb" }));

app.get("/", (_req, res) => res.type("text/plain").send("Aevren sensory is running. Use /health, /mcp[/TOKEN], or POST /sense."));
app.get("/health", (_req, res) => res.json({
  ok: true,
  service: "aevren-sensory",
  version: "0.1.0",
  provider: "gemini",
  model: MODEL,
  provider_configured: Boolean(GEMINI_API_KEY),
  access_protected: Boolean(ACCESS_TOKEN),
  max_media_bytes: MAX_MEDIA_BYTES
}));

async function handleMcp(req, res) {
  if (!authorized(req)) return res.status(401).json({ error: "Unauthorized" });
  try {
    const server = makeServer();
    const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined });
    res.on("close", () => transport.close());
    await server.connect(transport);
    await transport.handleRequest(req, res, req.body);
  } catch (error) {
    console.error("MCP error:", error);
    if (!res.headersSent) res.status(500).json({ jsonrpc: "2.0", error: { code: -32603, message: String(error?.message || error) }, id: null });
  }
}

app.post("/mcp", handleMcp);
app.post("/mcp/:token", handleMcp);
app.get("/mcp", (_req, res) => res.status(405).json({ error: "Use POST /mcp" }));
app.get("/mcp/:token", (_req, res) => res.status(405).json({ error: "Use POST /mcp/TOKEN" }));

async function handleSense(req, res) {
  if (!authorized(req)) return res.status(401).json({ ok: false, error: "Unauthorized" });
  try {
    const result = await prepareAndSense(req.body || {});
    res.json(result);
  } catch (error) {
    console.error("Sense error:", error);
    res.status(400).json({ ok: false, error: String(error?.message || error) });
  }
}
app.post("/sense", handleSense);
app.post("/sense/:token", handleSense);

app.listen(PORT, "0.0.0.0", () => {
  console.log(`Aevren sensory listening on 0.0.0.0:${PORT}`);
  console.log(`Gemini model: ${MODEL}; configured=${Boolean(GEMINI_API_KEY)}; protected=${Boolean(ACCESS_TOKEN)}`);
});
