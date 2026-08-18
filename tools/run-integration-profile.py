#!/usr/bin/env python3
"""Run a reproducible CC-Aeroworks integration profile and record its result."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import platform
import subprocess
import sys
import time
from pathlib import Path
from typing import Any, Sequence

ROOT = Path(__file__).resolve().parents[1]
RESULT_DIR = ROOT / "build/test-results/integration"
SUPPORTED_PROFILES = {
    "BASE-CLIENT",
    "BASE-SERVER",
    "FALLBACK-CLIENT",
    "MULTI-COMPUTER",
    "CC-120",
    "SABLE-STATIC",
    "SABLE-MOVING",
    "DRIVEBYWIRE",
    "FULL-SERVER",
}


def command_name() -> str:
    return "gradlew.bat" if os.name == "nt" else "./gradlew"


def git_output(*args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def run_command(command: Sequence[str], environment: dict[str, str]) -> dict[str, Any]:
    started = time.monotonic()
    print("+", " ".join(command), flush=True)
    result = subprocess.run(
        list(command),
        cwd=ROOT,
        env=environment,
        text=True,
    )
    return {
        "command": list(command),
        "exitCode": result.returncode,
        "durationSeconds": round(time.monotonic() - started, 3),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("profile", choices=sorted(SUPPORTED_PROFILES))
    parser.add_argument(
        "--dependency-dir",
        type=Path,
        required=True,
        help="Directory containing exactly the mod JAR set for this profile",
    )
    parser.add_argument(
        "--no-clean",
        action="store_true",
        help="Do not run Gradle clean before tests and build",
    )
    parser.add_argument(
        "--server-smoke",
        action="store_true",
        help="Run tools/dedicated-server-smoke.py after a successful build",
    )
    parser.add_argument(
        "--result",
        type=Path,
        help="Override the JSON result path",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    dependency_dir = args.dependency_dir.expanduser().resolve()
    if not dependency_dir.is_dir():
        raise SystemExit(f"Dependency directory does not exist: {dependency_dir}")

    result_path = args.result or RESULT_DIR / f"{args.profile.lower()}.json"
    result_path = result_path.expanduser().resolve()
    result_path.parent.mkdir(parents=True, exist_ok=True)

    environment = os.environ.copy()
    environment["CC_AEROWORKS_TEST_PROFILE"] = args.profile

    gradle_property = f"-Pmod_dependency_dir={dependency_dir}"
    gradle = command_name()
    commands: list[list[str]] = [
        [gradle, gradle_property, "verifyModDependencies", "--no-daemon"],
    ]
    build_tasks = [] if args.no_clean else ["clean"]
    build_tasks.extend(["test", "build"])
    commands.append([gradle, gradle_property, *build_tasks, "--no-daemon", "--stacktrace"])

    record: dict[str, Any] = {
        "schemaVersion": 1,
        "profile": args.profile,
        "status": "RUNNING",
        "startedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
        "repository": "TeutonStudio/CC-Aeroworks",
        "commit": git_output("rev-parse", "HEAD"),
        "branch": git_output("branch", "--show-current"),
        "dirty": bool(git_output("status", "--porcelain")),
        "dependencyDirectory": str(dependency_dir),
        "platform": {
            "system": platform.system(),
            "release": platform.release(),
            "machine": platform.machine(),
            "python": platform.python_version(),
        },
        "steps": [],
    }

    exit_code = 0
    try:
        for command in commands:
            step = run_command(command, environment)
            record["steps"].append(step)
            if step["exitCode"] != 0:
                exit_code = int(step["exitCode"])
                record["status"] = "FAIL"
                break

        if exit_code == 0 and args.server_smoke:
            smoke_command = [
                sys.executable,
                str(ROOT / "tools/dedicated-server-smoke.py"),
                "--dependency-dir",
                str(dependency_dir),
            ]
            step = run_command(smoke_command, environment)
            record["steps"].append(step)
            if step["exitCode"] != 0:
                exit_code = int(step["exitCode"])
                record["status"] = "FAIL"

        if exit_code == 0:
            record["status"] = "PASS"
    except KeyboardInterrupt:
        record["status"] = "ABORTED"
        exit_code = 130
    finally:
        record["finishedAt"] = dt.datetime.now(dt.timezone.utc).isoformat()
        # A normal profile includes Gradle clean, which removes build/ after the initial directory
        # creation above. Recreate the result directory here so failures are always recorded.
        result_path.parent.mkdir(parents=True, exist_ok=True)
        result_path.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
        print(f"Integration result: {result_path}")
        print(f"Status: {record['status']}")

    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
