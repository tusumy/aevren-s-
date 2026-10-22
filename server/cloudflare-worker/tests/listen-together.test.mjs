import test from "node:test";
import assert from "node:assert/strict";
import worker from "../worker.js";
import { listenTogetherTool } from "../listen-together.js";

class FakeKV {
  constructor() { this.map = new Map(); }
  async put(key, value, opts = {}) {
    this.map.set(key, {value, metadata: opts.metadata || null, expirationTtl: opts.expirationTtl || 0});
  }
  async get(key, mode = "text") {
    const r = this.map.get(key); if (!r) return null;
    if (mode === "json") return JSON.parse(String(r.value));
    return r.value;
  }
  async getWithMetadata(key, mode = "text") {
    const r = this.map.get(key);
    return { value: r?.value || null, metadata: r?.metadata || null };
  }
}
const fakeJpeg = Buffer.concat([Buffer.from([255,216,255]),Buffer.alloc(200),Buffer.from([255,217])]);
const b64=fakeJpeg.toString("base64");
function frames(offsets=[1800,4000]) {
  return {capture_id:"test-capture",captured_at_ms:1792600000000,duration_seconds:12,
          frames:offsets.map(offset_ms=>({offset_ms,jpeg_b64:b64}))};
}
function request(path,{method="GET",body,token="test-token",device="android-phone"}={}) {
  const bytes=body===undefined?undefined:typeof body==="string"?body:JSON.stringify(body);
  return new Request("https://test.workers.dev"+path,{method,body:bytes,
    headers:{"X-Auth-Token":token,"X-Device-Id":device,
             ...(body===undefined?{}:{"Content-Type":"application/json"})}});
}
const env=()=>({LINJIAN_TOKEN:"test-token",SCREENSHOT_KV:new FakeKV()});

test("video upload, metadata, frames, MCP view and isolation",async()=>{
  const e=env();
  const result=await worker.fetch(request("/api/video/clip",{method:"POST",body:frames(),device:"A"}),e);
  assert.equal(result.status,200);
  assert.equal((await result.json()).frame_count,2);
  const metadata=await worker.fetch(request("/api/video/latest.json?device_id=A"),e);
  const meta=await metadata.json();
  assert.deepEqual(meta.offsets_ms,[1800,4000]);
  assert.ok(!("frames" in meta));
  const full=await worker.fetch(request("/api/video/latest?device_id=A"),e);
  const fullClip=await full.json();
  assert.equal(fullClip.frames[1].jpeg_b64,b64);
  const mcp=await listenTogetherTool(e,"listen_together_watch","A");
  assert.equal(mcp.content.filter(c=>c.type==="image").length,2);
  assert.equal(mcp.content[2].data,b64);
  assert.equal((await worker.fetch(request("/api/video/latest?device_id=B"),e)).status,404);
  assert.equal(e.SCREENSHOT_KV.map.get("listen:video:A").expirationTtl,180);
});
test("requires authentication and validates ordering/format",async()=>{
  const e=env();
  assert.equal((await worker.fetch(request("/api/video/clip",{method:"POST",body:frames(),token:"wrong"}),e)).status,403);
  assert.equal((await worker.fetch(request("/api/video/clip",{method:"POST",body:frames([4000,1800])}),e)).status,400);
  const wrong=frames();wrong.frames[0].jpeg_b64=Buffer.from("not-jpeg".repeat(30)).toString("base64");
  assert.equal((await worker.fetch(request("/api/video/clip",{method:"POST",body:wrong}),e)).status,400);
  assert.equal((await worker.fetch(request("/api/video/clip",{method:"POST",body:frames()}),e)).status,200);
  assert.equal((await worker.fetch(request("/api/video/latest", {token:"wrong"}),e)).status,403);
});
test("WAV clip round trip; transcription key absent degrades gracefully",async()=>{
  const e=env(), wav=Buffer.concat([Buffer.from("RIFF"),Buffer.alloc(4),Buffer.from("WAVE"),Buffer.alloc(256)]);
  const upload=await worker.fetch(new Request("https://test.workers.dev/api/audio/clip",
    {method:"POST",body:wav,headers:{"X-Auth-Token":"test-token","X-Device-Id":"A","Content-Type":"audio/wav"}}),e);
  assert.equal(upload.status,200);
  const meta=await (await worker.fetch(request("/api/audio/latest.json?device_id=A"),e)).json();
  assert.equal(meta.size,wav.byteLength);
  const received=await (await worker.fetch(request("/api/audio/latest?device_id=A"),e)).arrayBuffer();
  assert.deepEqual(Buffer.from(received),wav);
  assert.equal(e.SCREENSHOT_KV.map.get("listen:audio:A").expirationTtl,180);
  const mcp=await listenTogetherTool(e,"listen_together_transcribe","A");
  assert.equal(mcp.isError,true);
  assert.match(mcp.content[0].text,/OPENAI_API_KEY/);
});
