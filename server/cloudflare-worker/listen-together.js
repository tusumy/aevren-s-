// User-initiated 12-second listening: Cloudflare KV adapter.
// No API here can remotely start media capture. Each device clip expires after 180 seconds.
const EXPIRY_SECONDS = 180;
const MAX_VIDEO_BYTES = 1600 * 1024;
const MAX_WAV_BYTES = 1024 * 1024;

function response(payload, status = 200) {
  return new Response(JSON.stringify(payload), {
    status, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" }
  });
}
function key(media, device) { return `listen:${media}:${device}`; }
function deviceId(value = "android-phone") {
  const s = String(value || "android-phone").trim().slice(0, 64);
  return s && /^[\w.:-]+$/.test(s) ? s : "android-phone";
}
function byteLength64(input) {
  if (typeof input !== "string" || input.length > 240000 || input.length % 4 !== 0
      || !/^[A-Za-z0-9+/]*={0,2}$/.test(input)) throw new Error("invalid_base64");
  return atob(input);
}
async function jsonBody(request, maxBytes) {
  const len = Number(request.headers.get("Content-Length") || 0);
  if (len > maxBytes) throw new Error("too_large");
  const data = await request.arrayBuffer();
  if (data.byteLength > maxBytes || data.byteLength === 0) throw new Error("too_large");
  return JSON.parse(new TextDecoder().decode(data));
}

export async function handleListenHttp(env, request, url) {
  if (!env.SCREENSHOT_KV) return response({ok:false,error:"MISSING_KV_BINDING"}, 501);
  const p = url.pathname;
  const post = request.method === "POST";
  const id = deviceId(post ? request.headers.get("X-Device-Id") : url.searchParams.get("device_id"));
  if (post && p === "/api/video/clip") {
    let clip;
    try {
      clip = await jsonBody(request, MAX_VIDEO_BYTES);
      if (!clip || !Array.isArray(clip.frames) || clip.frames.length < 1 || clip.frames.length > 6)
        throw new Error("bad_frame_count");
      if (!Number.isSafeInteger(clip.captured_at_ms) || clip.captured_at_ms <= 0
          || !Number.isFinite(clip.duration_seconds) || clip.duration_seconds < 1
          || clip.duration_seconds > 15 || typeof clip.capture_id !== "string"
          || clip.capture_id.length < 1 || clip.capture_id.length > 80)
        throw new Error("bad_metadata");
      let prev = -1;
      for (const f of clip.frames) {
        if (!Number.isInteger(f.offset_ms) || f.offset_ms < 0 || f.offset_ms <= prev
            || f.offset_ms > clip.duration_seconds * 1000 + 500)
          throw new Error("bad_frame_order");
        const jpeg = byteLength64(f.jpeg_b64);
        if (jpeg.length < 100 || jpeg.length > 170 * 1024
            || jpeg.charCodeAt(0) !== 255 || jpeg.charCodeAt(1) !== 216
            || jpeg.charCodeAt(2) !== 255
            || jpeg.charCodeAt(jpeg.length-2) !== 255
            || jpeg.charCodeAt(jpeg.length-1) !== 217)
          throw new Error("bad_jpeg");
        prev = f.offset_ms;
      }
    } catch (error) {
      return response({ok:false,error:"VIDEO_FORMAT_INVALID",detail:String(error?.message||error).slice(0,90)}, 400);
    }
    const mtime = Date.now() / 1000;
    const saved = {capture_id:clip.capture_id,captured_at_ms:clip.captured_at_ms,
      duration_seconds:clip.duration_seconds,mtime,frames:clip.frames};
    await env.SCREENSHOT_KV.put(key("video",id), JSON.stringify(saved), {expirationTtl:EXPIRY_SECONDS});
    return response({ok:true,device_id:id,frame_count:saved.frames.length,expires_in_seconds:EXPIRY_SECONDS});
  }
  if (post && p === "/api/audio/clip") {
    const len = Number(request.headers.get("Content-Length")||0);
    if (len > MAX_WAV_BYTES) return response({ok:false,error:"AUDIO_SIZE_INVALID"}, 413);
    const bytes = await request.arrayBuffer();
    const u = new Uint8Array(bytes);
    if (u.length < 44 || u.length > MAX_WAV_BYTES)
      return response({ok:false,error:"AUDIO_SIZE_INVALID"}, 413);
    if ((len > 0 && u.length !== len) || String.fromCharCode(...u.slice(0,4)) !== "RIFF"
        || String.fromCharCode(...u.slice(8,12)) !== "WAVE")
      return response({ok:false,error:"AUDIO_FORMAT_INVALID"},400);
    const mtime = Date.now()/1000;
    const duration = Math.max(0,Math.round((u.length-44)/320)/100);
    await env.SCREENSHOT_KV.put(key("audio",id),bytes,{
      expirationTtl:EXPIRY_SECONDS, metadata:{mtime:String(mtime),duration:String(duration)}
    });
    return response({ok:true,device_id:id,duration_seconds:duration,expires_in_seconds:EXPIRY_SECONDS});
  }
  if (!post && (p === "/api/video/latest" || p === "/api/video/latest.json")) {
    const clip = await env.SCREENSHOT_KV.get(key("video",id),"json");
    if (!clip) return response({ok:false,error:"NO_RECENT_VIDEO"},404);
    const meta={ok:true,device_id:id,capture_id:clip.capture_id,captured_at_ms:clip.captured_at_ms,
      duration_seconds:clip.duration_seconds,frame_count:clip.frames.length,mtime:clip.mtime,
      expires_in_seconds:Math.max(0,Math.round(EXPIRY_SECONDS-(Date.now()/1000-clip.mtime)))};
    return response(p.endsWith(".json")
      ? {...meta,offsets_ms:clip.frames.map(f=>f.offset_ms)}
      : {...meta,frames:clip.frames});
  }
  if (!post && (p === "/api/audio/latest" || p === "/api/audio/latest.json")) {
    const result=await env.SCREENSHOT_KV.getWithMetadata(key("audio",id),"arrayBuffer");
    if (!result?.value) return response({ok:false,error:"NO_RECENT_AUDIO"},404);
    const mtime=Number(result.metadata?.mtime||0);
    const duration=Number(result.metadata?.duration||0);
    const meta={ok:true,device_id:id,mtime,duration_seconds:duration,size:result.value.byteLength,
      expires_in_seconds:Math.max(0,Math.round(EXPIRY_SECONDS-(Date.now()/1000-mtime)))};
    if (p.endsWith(".json")) return response(meta);
    return new Response(result.value,{headers:{"Content-Type":"audio/wav","Cache-Control":"no-store"}});
  }
  return response({ok:false,error:"INVALID_LISTEN_ROUTE"},404);
}

export async function listenTogetherTool(env, name, device = "android-phone") {
  const id=deviceId(device);
  if (!env.SCREENSHOT_KV) return {isError:true,content:[{type:"text",text:"砚团未绑定 SCREENSHOT_KV"}]};
  if (name === "listen_together_watch") {
    const clip = await env.SCREENSHOT_KV.get(key("video",id),"json");
    if (!clip) return {isError:true,content:[{type:"text",text:"还没有可用的视频关键帧，或3分钟有效期已过。请在手机砚团里点击「一起看·录12秒」。"}]};
    const content=[{type:"text",text:JSON.stringify({
      ok:true,device_id:id,captured_at_ms:clip.captured_at_ms,duration_seconds:clip.duration_seconds,
      frame_count:clip.frames.length,note:"仅有按时间排列的关键帧，非完整视频；帧间动作不能凭空推断。"
    })}];
    clip.frames.forEach((f,i)=>{
      content.push({type:"text",text:`关键帧 ${i+1} / +${f.offset_ms} ms`});
      content.push({type:"image",mimeType:"image/jpeg",data:f.jpeg_b64});
    });
    return {isError:false,content,structuredContent:{ok:true,device_id:id,frame_count:clip.frames.length,
      captured_at_ms:clip.captured_at_ms,source:"listen_together_watch"}};
  }
  if (name === "listen_together_transcribe") {
    if (!env.OPENAI_API_KEY) return {isError:true,content:[{type:"text",text:"Cloudflare Worker 未设置 OPENAI_API_KEY，声音不能转写；画面分析可直接用 listen_together_watch。"}]};
    const clip=await env.SCREENSHOT_KV.getWithMetadata(key("audio",id),"arrayBuffer");
    if (!clip?.value) return {isError:true,content:[{type:"text",text:"暂时没有最近3分钟内的声音片段，可能播放器禁止系统内录；画面仍可以分析。"}]};
    const form=new FormData();
    form.append("model","gpt-4o-mini-transcribe");
    form.append("file",new Blob([clip.value],{type:"audio/wav"}),"listen.wav");
    const upstream=await fetch("https://api.openai.com/v1/audio/transcriptions",{
      method:"POST",headers:{Authorization:"Bearer "+env.OPENAI_API_KEY},
      body:form,signal:AbortSignal.timeout(35000)
    });
    if (!upstream.ok) return {isError:true,content:[{type:"text",text:JSON.stringify({ok:false,error:"STT_FAILED",status:upstream.status})}]};
    const data=await upstream.json();
    return {isError:false,content:[{type:"text",text:JSON.stringify({
      ok:true,device_id:id,transcript:String(data.text||""),captured_at:Number(clip.metadata?.mtime||0),
      note:"仅针对用户主动授权的短片段；空白可能代表无对白。"
    })}]};
  }
  return {isError:true,content:[{type:"text",text:"UNKNOWN_LISTEN_TOOL"}]};
}
