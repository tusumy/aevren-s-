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

try {
  const args = ["wrangler", "deploy", "--config", "wrangler.toml"];

  if (token) {
    secretsPath = join(tmpdir(), `aevren-window-probe-secrets-${process.pid}.json`);
    writeFileSync(secretsPath, JSON.stringify({ LINJIAN_TOKEN: token }), { mode: 0o600 });
    args.push("--secrets-file", secretsPath);
  }

  const deploy = spawnSync("npx", args, {
    stdio: "inherit",
    env: process.env,
  });
  process.exitCode = deploy.status ?? 1;
} finally {
  if (secretsPath) {
    try {
      unlinkSync(secretsPath);
    } catch {}
  }
}
