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

function deploy(label, extraArgs = []) {
  console.log(`=== Deploying ${label} ===`);
  const args = ["wrangler", "deploy", "--config", "wrangler.toml", ...extraArgs];
  if (secretsPath) args.push("--secrets-file", secretsPath);
  const result = spawnSync("npx", args, { stdio: "inherit", env: process.env });
  return result.status ?? 1;
}

try {
  if (token) {
    secretsPath = join(tmpdir(), `aevren-window-probe-secrets-${process.pid}.json`);
    writeFileSync(secretsPath, JSON.stringify({ LINJIAN_TOKEN: token }), { mode: 0o600 });
  }

  // Workers Builds requires the connected Worker name to match the base
  // Wrangler name. Use a Wrangler environment for the sibling backend so
  // Cloudflare accepts the aevren-window-probe-backend suffix.
  const backendStatus = deploy(
    "aevren-window-probe-backend",
    ["--env", "backend"],
  );
  if (backendStatus !== 0) process.exit(backendStatus);

  const probeStatus = deploy("aevren-window-probe");
  process.exitCode = probeStatus;
} finally {
  if (secretsPath) {
    try { unlinkSync(secretsPath); } catch {}
  }
}
