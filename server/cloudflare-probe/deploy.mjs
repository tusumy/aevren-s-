import { spawnSync } from "node:child_process";

const install = spawnSync(
  "npm",
  ["install", "--no-audit", "--no-fund"],
  { stdio: "inherit", env: process.env },
);
if ((install.status ?? 1) !== 0) process.exit(install.status ?? 1);

const deploy = spawnSync(
  "npx",
  ["wrangler", "deploy", "--config", "wrangler.toml"],
  { stdio: "inherit", env: process.env },
);
process.exit(deploy.status ?? 1);
