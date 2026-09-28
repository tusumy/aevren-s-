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

function deploy(config, name, { allowCiNameOverride = true } = {}) {
  const childEnv = { ...process.env };

  // Cloudflare Workers Builds injects WRANGLER_CI_OVERRIDE_NAME for the
  // connected project. That override wins over the name in wrangler.toml
  // and can even defeat --name. Remove it only for the sidecar backend,
  // otherwise Wrangler tries to upload backend code into aevren-window-probe
  // and Cloudflare rejects it because backend code does not export
  // WindowProbeMCP, which owns the existing Durable Object namespace.
  if (!allowCiNameOverride) {
    delete childEnv.WRANGLER_CI_OVERRIDE_NAME;
  }

  const args = [
    "wrangler",
    "deploy",
    "--config",
    config,
    "--name",
    name,
  ];
  if (secretsPath) args.push("--secrets-file", secretsPath);

  console.log(`\n=== Deploying ${name} with ${config} ===`);
  const result = spawnSync("npx", args, { stdio: "inherit", env: childEnv });
  return result.status ?? 1;
}

try {
  if (token) {
    secretsPath = join(tmpdir(), `aevren-window-probe-secrets-${process.pid}.json`);
    writeFileSync(secretsPath, JSON.stringify({ LINJIAN_TOKEN: token }), { mode: 0o600 });
  }

  // Deploy the backend with the CI name override removed, so it is created as
  // its own Worker instead of overwriting the Git-connected probe Worker.
  const backendStatus = deploy(
    "wrangler.backend.toml",
    "aevren-window-probe-backend",
    { allowCiNameOverride: false },
  );
  if (backendStatus !== 0) process.exit(backendStatus);

  // The front MCP shell is the Git-connected Worker, so keeping Cloudflare's
  // CI name override here is correct and should resolve to aevren-window-probe.
  const probeStatus = deploy(
    "wrangler.toml",
    "aevren-window-probe",
    { allowCiNameOverride: true },
  );
  process.exitCode = probeStatus;
} finally {
  if (secretsPath) {
    try { unlinkSync(secretsPath); } catch {}
  }
}
