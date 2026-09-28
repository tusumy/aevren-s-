import { writeFileSync, unlinkSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawnSync } from "node:child_process";

const install = spawnSync(
  "npm",
  ["install", "--no-audit", "--no-fund"],
  { stdio: "inherit", env: process.env },
);
if ((install.status ?? 1) !== 0) process.exit(install.status ?? 1);

const token = process.env.LINJIAN_TOKEN || "";
let secretsPath = "";

function deploy(config) {
  const args = ["wrangler", "deploy", "--config", config];
  if (secretsPath) args.push("--secrets-file", secretsPath);
  const result = spawnSync("npx", args, { stdio: "inherit", env: process.env });
  return result.status ?? 1;
}

try {
  if (token) {
    secretsPath = join(tmpdir(), `aevren-window-probe-secrets-${process.pid}.json`);
    writeFileSync(secretsPath, JSON.stringify({ LINJIAN_TOKEN: token }), { mode: 0o600 });
  }

  const backendStatus = deploy("wrangler.backend.toml");
  if (backendStatus !== 0) process.exit(backendStatus);

  const probeStatus = deploy("wrangler.toml");
  process.exitCode = probeStatus;
} finally {
  if (secretsPath) {
    try { unlinkSync(secretsPath); } catch {}
  }
}
