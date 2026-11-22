import { spawnSync } from "node:child_process";
import * as fs from "node:fs";
import * as path from "node:path";

/**
 * Pre-push backend test runner.
 *
 * Runs `mvn -B -q test` in `./backend` if `mvn` or `mvnw` is available.
 * If neither is installed locally, gracefully prints a warning and delegates
 * enforcement to GitHub Actions CI.
 */
function findMavenCommand(backendDir: string): string | null {
  try {
    const check = spawnSync("mvn", ["-version"], {
      shell: true,
      stdio: "ignore",
      timeout: 5000,
    });
    if (check.status === 0) {
      return "mvn";
    }
  } catch {}

  const isWindows = process.platform === "win32";
  const wrapperScript = isWindows
    ? path.join(backendDir, "mvnw.cmd")
    : path.join(backendDir, "mvnw");
  const wrapperCommand = isWindows ? ".\\mvnw.cmd" : "./mvnw";

  if (fs.existsSync(wrapperScript)) {
    try {
      const check = spawnSync(wrapperCommand, ["-version"], {
        cwd: backendDir,
        shell: true,
        stdio: "ignore",
        timeout: 5000,
      });
      if (check.status === 0) {
        return wrapperCommand;
      }
    } catch {}
  }

  return null;
}

/**
 * Bun loads the project's .env into process.env before this script runs. Those values are meant
 * for Docker (for example SPRING_DATASOURCE_URL pointing at the "postgres" container) and must not
 * leak into the test JVM, so every key defined in .env is dropped from the child's environment.
 */
function environmentWithoutDotEnv(): NodeJS.ProcessEnv {
  const env = { ...process.env };
  const dotEnv = path.resolve(process.cwd(), ".env");
  if (!fs.existsSync(dotEnv)) return env;
  for (const line of fs.readFileSync(dotEnv, "utf8").split(/\r?\n/)) {
    const match = /^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=/.exec(line);
    if (match?.[1]) delete env[match[1]];
  }
  return env;
}

function runBackendTests(): void {
  const backendDir = path.resolve(process.cwd(), "backend");
  const mvnCmd = findMavenCommand(backendDir);

  if (!mvnCmd) {
    console.warn(
      "\n[WARNING] Neither Apache Maven ('mvn') nor a runnable Maven Wrapper was found in PATH.\n" +
        "  Skipping local pre-push backend tests. GitHub Actions CI will run full verification.\n"
    );
    process.exit(0);
  }

  console.log("\n[pre-push] Running backend test suite (test)...");

  const result = spawnSync(mvnCmd, ["-B", "--no-transfer-progress", "test"], {
    cwd: backendDir,
    shell: true,
    stdio: "inherit",
    env: environmentWithoutDotEnv(),
  });

  if (result.status !== 0) {
    console.error(
      "\n[ERROR] Backend tests failed!\n" +
        "  Please review the test failures above and fix them before pushing.\n"
    );
    process.exit(result.status ?? 1);
  }

  console.log("[pre-push] Backend tests passed successfully.\n");
}

runBackendTests();
