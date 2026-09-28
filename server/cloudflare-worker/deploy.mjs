import { writeFileSync, unlinkSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawnSync } from "node:child_process";

const token = process.env.LINJIAN_TOKEN;
if (!token) {
  console.error("Missing build secret LINJIAN_TOKEN. Add it under Cloudflare Workers Builds > Build variables and secrets.");
  process.exit(1);
}

const secretsPath = join(tmpdir(), `aevren-window-secrets-${process.pid}.json`);
writeFileSync(secretsPath, JSON.stringify({ LINJIAN_TOKEN: token }), { mode: 0o600 });

try {
  const result = spawnSync(
    "npx",
    ["wrangler", "deploy", "--config", "wrangler.toml", "--secrets-file", secretsPath],
    { stdio: "inherit", env: process.env },
  );
  process.exitCode = result.status ?? 1;
} finally {
  try {
    unlinkSync(secretsPath);
  } catch {}
}
