import { spawnSync } from "node:child_process";
import * as fs from "node:fs";
import * as path from "node:path";

/**
 * Backend formatting / lint check runner.
 *
 * Runs `spotless:check` (Google Java Format) in `./backend` via `mvn` or `mvnw`.
 * If neither is available locally, gracefully prints a warning and delegates
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

const JDK21_MAVEN_IMAGE = "maven:3.9.9-eclipse-temurin-21-alpine";

/**
 * Runs `spotless:check` under JDK 21 in Docker. Returns true when it passes, exits with the
 * check's status when it finds violations, and returns false when Docker can't be used.
 */
function runSpotlessInDocker(backendDir: string): boolean {
  const docker = spawnSync("docker", ["info"], { stdio: "ignore", timeout: 10000 });
  if (docker.status !== 0) {
    return false;
  }
  console.log("[pre-commit] Local JDK can't run google-java-format; checking in a JDK 21 container...");
  const result = spawnSync(
    "docker",
    [
      "run",
      "--rm",
      "-v",
      `${backendDir}:/w`,
      "-v",
      "servicedesk-m2:/root/.m2",
      "-w",
      "/w",
      JDK21_MAVEN_IMAGE,
      "mvn",
      "-B",
      "-q",
      "spotless:check",
    ],
    { encoding: "utf-8", env: { ...process.env, MSYS_NO_PATHCONV: "1" } }
  );
  if (result.status === 0) {
    return true;
  }
  process.stdout.write(result.stdout ?? "");
  process.stderr.write(result.stderr ?? "");
  console.error(
    "\n[ERROR] Backend code formatting check failed!\n" +
      `  Fix with: docker run --rm -v "${backendDir}:/w" -v servicedesk-m2:/root/.m2 -w /w ${JDK21_MAVEN_IMAGE} mvn -B -q spotless:apply\n`
  );
  process.exit(result.status ?? 1);
}

function runBackendLint(): void {
  const backendDir = path.resolve(process.cwd(), "backend");
  const mvnCmd = findMavenCommand(backendDir);

  if (!mvnCmd) {
    console.warn(
      "\n[WARNING] Neither Apache Maven ('mvn') nor a runnable Maven Wrapper was found in PATH.\n" +
        "  Skipping local backend Spotless formatting check. GitHub Actions CI will enforce it.\n"
    );
    process.exit(0);
  }

  console.log("\n[pre-commit] Running backend Spotless code formatting check...");

  const result = spawnSync(mvnCmd, ["-B", "spotless:check"], {
    cwd: backendDir,
    shell: true,
    encoding: "utf-8",
  });

  const fullOutput = (result.stdout ?? "") + (result.stderr ?? "");

  if (result.status !== 0) {
    if (
      fullOutput.includes("NoSuchMethodError") ||
      fullOutput.includes("DeferredDiagnosticHandler") ||
      fullOutput.includes("API incompatibility")
    ) {
      // google-java-format needs JDK 21; newer JDKs break its internal javac calls. Run the same
      // check in a JDK 21 container instead of silently skipping, so CI never sees surprises.
      if (runSpotlessInDocker(backendDir)) {
        console.log("[pre-commit] Backend formatting check passed (JDK 21 container).\n");
        process.exit(0);
      }
      console.warn(
        "\n[WARNING] Your JDK is incompatible with google-java-format and Docker is not available.\n" +
          "  Skipping local Spotless enforcement. GitHub Actions CI (JDK 21 Temurin) will enforce formatting.\n" +
          "  Install JDK 21 or start Docker to check locally.\n"
      );
      process.exit(0);
    }

    if (result.stdout) {
      process.stdout.write(result.stdout);
    }
    if (result.stderr) {
      process.stderr.write(result.stderr);
    }

    console.error(
      "\n[ERROR] Backend code formatting check failed!\n" +
        "  Run 'mvn spotless:apply' (or './mvnw spotless:apply') to automatically format Java files with Google Java Format.\n"
    );
    process.exit(result.status ?? 1);
  }

  console.log("[pre-commit] Backend formatting check passed.\n");
}

runBackendLint();

