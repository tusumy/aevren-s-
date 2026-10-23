import assert from "node:assert/strict";
import worker from "./worker.js";

class MemoryKV {
  constructor() { this.values = new Map(); }
  async put(key, value, options = {}) {
    this.values.set(key, { value, expires: options.expirationTtl ? Date.now() + options.expirationTtl * 1000 : null });
  }
  async delete(key) { this.values.delete(key); }
  async get(key, type) {
    const entry = this.values.get(key);
    if (!entry || (entry.expires && entry.expires < Date.now())) return null;
    if (type === "json") return JSON.parse(entry.value);
    if (type === "arrayBuffer") return entry.value instanceof ArrayBuffer ? entry.value : new TextEncoder().encode(entry.value).buffer;
    return entry.value;
  }
}
const env = { LINJIAN_TOKEN: "test-secret", SCREENSHOT_KV: new MemoryKV() };
const sid = "70b2a397-992a-4c2c-b21e-73f8f0390241";
async function req(path, method = "GET", body = undefined, mime = undefined, token = true) {
  const headers = new Headers();
  if (token) headers.set("X-Auth-Token", "test-secret");
  if (mime) headers.set("Content-Type", mime);
  return worker.fetch(new Request("https://watch.test" + path, { method, body, headers }), env);
}
const image = new Uint8Array(160); image[0] = 0xff; image[1] = 0xd8;
const audio = new Uint8Array(120); audio.set([82,73,70,70], 0);
assert.equal((await req("/api/watch/latest", "GET", undefined, undefined, false)).status, 403);
for (let i = 0; i < 6; i++) {
  const r = await req("/api/watch/frame?session_id=" + sid + "&index=" + i, "POST", image, "image/jpeg");
  assert.equal(r.status, 200, "frame upload " + i);
}
const a = await req("/api/watch/audio?session_id=" + sid, "POST", audio, "audio/wav");
assert.equal(a.status, 200);
const response = await req("/api/watch/latest");
const meta = await response.json();
assert.deepEqual(meta.frames, [0,1,2,3,4,5]);
assert.equal(meta.audio_uploaded, true);
const f = await req("/api/watch/frame?session_id=" + sid + "&index=3");
assert.equal(f.headers.get("content-type"), "image/jpeg");
assert.equal((await f.arrayBuffer()).byteLength, image.length);
const audioRead = await req("/api/watch/audio?session_id=" + sid);
assert.equal((await audioRead.arrayBuffer()).byteLength, audio.length);
const wrong = await req("/api/watch/frame?session_id=../bad&index=0");
assert.equal(wrong.status, 404);
const invalid = await req("/api/watch/frame?session_id=" + sid + "&index=6", "POST", image, "image/jpeg");
assert.equal(invalid.status, 400);
for (let i = 6; i < 12; i++) {
  const slot = i % 6;
  const next = new Uint8Array(image);
  next[2] = i;
  const r = await req("/api/watch/frame?session_id=" + sid + "&index=" + slot +
    "&sequence=" + i, "POST", next, "image/jpeg");
  assert.equal(r.status, 200, "rolling frame " + i);
}
const rolling = await (await req("/api/watch/latest")).json();
assert.deepEqual(rolling.frames.map(i => rolling.frame_sequence[i]), [6,7,8,9,10,11]);
const stop = await req("/api/watch/stop?session_id=" + sid, "POST");
assert.equal(stop.status, 200);
const empty = await (await req("/api/watch/latest")).json();
assert.equal(empty.ok, false);
assert.equal((await req("/api/watch/frame?session_id=" + sid + "&index=0")).status, 404);
console.log("Together Watch live rolling and stop cleanup OK");
