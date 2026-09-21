import { createHash, createCipheriv, createDecipheriv, randomBytes } from "node:crypto";
import { spawn } from "node:child_process";
import { mkdir, mkdtemp, readFile, readdir, rm, stat, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { GetObjectCommand, PutObjectCommand, S3Client } from "@aws-sdk/client-s3";
import * as tar from "tar";

const CONFIG_DIR = process.env.AGENTLY_CLI_CONFIG_DIR || "/data/agently-cli";
const ACCOUNT_ID = process.env.R2_ACCOUNT_ID?.trim();
const ACCESS_KEY_ID = process.env.R2_ACCESS_KEY_ID?.trim();
const SECRET_ACCESS_KEY = process.env.R2_SECRET_ACCESS_KEY?.trim();
const BUCKET = process.env.R2_BUCKET?.trim();
const BACKUP_SECRET = process.env.R2_BACKUP_SECRET?.trim();
const PREFIX = (process.env.R2_PREFIX || "agent-mail").replace(/^\/+|\/+$/g, "");
const OBJECT_KEY = `${PREFIX}/runtime.amr`;

const r2Enabled = Boolean(
  ACCOUNT_ID && ACCESS_KEY_ID && SECRET_ACCESS_KEY && BUCKET && BACKUP_SECRET
);

const s3 = r2Enabled
  ? new S3Client({
      region: "auto",
      endpoint: `https://${ACCOUNT_ID}.r2.cloudflarestorage.com`,
      credentials: { accessKeyId: ACCESS_KEY_ID, secretAccessKey: SECRET_ACCESS_KEY },
    })
  : null;

function log(event, extra = {}) {
  console.log(JSON.stringify({ event, ...extra }));
}

function cryptoKey() {
  return createHash("sha256").update(BACKUP_SECRET, "utf8").digest();
}

function encrypt(buffer) {
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", cryptoKey(), iv);
  const ciphertext = Buffer.concat([cipher.update(buffer), cipher.final()]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([Buffer.from("AMR1"), iv, tag, ciphertext]);
}

function decrypt(buffer) {
  if (buffer.subarray(0, 4).toString("utf8") !== "AMR1") {
    throw new Error("Invalid R2 backup magic");
  }
  const iv = buffer.subarray(4, 16);
  const tag = buffer.subarray(16, 32);
  const ciphertext = buffer.subarray(32);
  const decipher = createDecipheriv("aes-256-gcm", cryptoKey(), iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(ciphertext), decipher.final()]);
}

async function bodyToBuffer(body) {
  if (!body) return Buffer.alloc(0);
  if (typeof body.transformToByteArray === "function") {
    return Buffer.from(await body.transformToByteArray());
  }
  const chunks = [];
  for await (const chunk of body) chunks.push(Buffer.from(chunk));
  return Buffer.concat(chunks);
}

async function walk(dir, base = dir) {
  let entries = [];
  try {
    entries = await readdir(dir, { withFileTypes: true });
  } catch {
    return [];
  }
  const out = [];
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      out.push(...(await walk(full, base)));
    } else if (entry.isFile()) {
      const s = await stat(full);
      out.push({
        path: path.relative(base, full).replaceAll(path.sep, "/"),
        size: s.size,
        mtimeMs: Math.trunc(s.mtimeMs),
      });
    }
  }
  return out.sort((a, b) => a.path.localeCompare(b.path));
}

async function signature() {
  const files = await walk(CONFIG_DIR);
  return {
    count: files.length,
    hash: createHash("sha256").update(JSON.stringify(files)).digest("hex"),
  };
}

async function restoreFromR2() {
  await mkdir(CONFIG_DIR, { recursive: true });
  if (!r2Enabled) {
    log("r2_disabled", { reason: "missing_r2_env" });
    return;
  }
  try {
    const response = await s3.send(new GetObjectCommand({ Bucket: BUCKET, Key: OBJECT_KEY }));
    const encrypted = await bodyToBuffer(response.Body);
    const archive = decrypt(encrypted);
    const tempDir = await mkdtemp(path.join(os.tmpdir(), "agent-mail-restore-"));
    const archivePath = path.join(tempDir, "runtime.tar.gz");
    await writeFile(archivePath, archive, { mode: 0o600 });
    await tar.x({ cwd: CONFIG_DIR, file: archivePath, gzip: true, strict: true });
    await rm(tempDir, { recursive: true, force: true });
    const sig = await signature();
    log("r2_restored", { key: OBJECT_KEY, files: sig.count });
  } catch (error) {
    const code = error?.name || error?.Code || error?.$metadata?.httpStatusCode;
    if (code === "NoSuchKey" || code === "NotFound" || code === 404) {
      log("r2_restore_empty", { key: OBJECT_KEY });
      return;
    }
    log("r2_restore_failed", { message: String(error?.message || error) });
  }
}

let backupInFlight = null;

async function backupToR2(reason = "change") {
  if (!r2Enabled) return false;
  if (backupInFlight) return backupInFlight;

  backupInFlight = (async () => {
    const sig = await signature();
    if (!sig.count) return false;

    const tempDir = await mkdtemp(path.join(os.tmpdir(), "agent-mail-backup-"));
    const archivePath = path.join(tempDir, "runtime.tar.gz");
    try {
      await tar.c({ cwd: CONFIG_DIR, file: archivePath, gzip: true, portable: true }, ["."]);
      const archive = await readFile(archivePath);
      const encrypted = encrypt(archive);
      await s3.send(
        new PutObjectCommand({
          Bucket: BUCKET,
          Key: OBJECT_KEY,
          Body: encrypted,
          ContentType: "application/octet-stream",
          Metadata: {
            format: "aevren-agent-mail-r2-v1",
            reason: String(reason).slice(0, 64),
          },
        })
      );
      log("r2_backed_up", { key: OBJECT_KEY, files: sig.count, reason });
      return true;
    } catch (error) {
      log("r2_backup_failed", { message: String(error?.message || error), reason });
      return false;
    } finally {
      await rm(tempDir, { recursive: true, force: true });
    }
  })();

  try {
    return await backupInFlight;
  } finally {
    backupInFlight = null;
  }
}

await restoreFromR2();
let lastSignature = await signature();

const upstreamDir = path.join(process.cwd(), "upstream");
const cliPath = path.join(upstreamDir, "node_modules", ".bin", "agently-cli");
const child = spawn(process.execPath, ["dist/index.js"], {
  cwd: upstreamDir,
  stdio: "inherit",
  env: {
    ...process.env,
    AGENTLY_CLI_CONFIG_DIR: CONFIG_DIR,
    AGENTLY_BIN: cliPath,
  },
});

const watcher = setInterval(async () => {
  try {
    const current = await signature();
    if (current.count && current.hash !== lastSignature.hash) {
      const ok = await backupToR2("filesystem-change");
      if (ok) lastSignature = await signature();
    }
  } catch (error) {
    log("r2_watch_error", { message: String(error?.message || error) });
  }
}, 3000);

let stopping = false;
async function shutdown(signal) {
  if (stopping) return;
  stopping = true;
  clearInterval(watcher);
  await backupToR2(`shutdown-${signal}`);
  child.kill(signal);
  setTimeout(() => process.exit(0), 5000).unref();
}

process.on("SIGTERM", () => void shutdown("SIGTERM"));
process.on("SIGINT", () => void shutdown("SIGINT"));

child.on("exit", async (code, signal) => {
  clearInterval(watcher);
  await backupToR2("child-exit");
  log("gateway_exited", { code, signal });
  process.exit(code ?? (signal ? 1 : 0));
});
