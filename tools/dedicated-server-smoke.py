#!/usr/bin/env python3
"""Start the isolated NeoForge development server and require a clean Done marker."""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SUCCESS_PATTERN = re.compile(r"Done \([^)]+\)! For help")
FATAL_MARKERS = (
    "Exception in server tick loop",
    "Failed to start the minecraft server",
    "Mixin apply failed",
    "MixinApplyError",
    "NoClassDefFoundError",
    "Could not load class",
    "ModLoadingException",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dependency-dir", type=Path, required=True)
    parser.add_argument("--run-dir", type=Path, default=ROOT / "build/smoke-server")
    parser.add_argument("--timeout", type=int, default=240)
    parser.add_argument("--keep-run-dir", action="store_true")
    return parser.parse_args()


def gradle_command() -> str:
    return "gradlew.bat" if os.name == "nt" else "./gradlew"


def safe_reset(directory: Path) -> None:
    resolved = directory.resolve()
    build_root = (ROOT / "build").resolve()
    if resolved == build_root or build_root not in resolved.parents:
        raise ValueError(f"Refusing to delete smoke directory outside {build_root}: {resolved}")
    shutil.rmtree(resolved, ignore_errors=True)
    resolved.mkdir(parents=True, exist_ok=True)


def read_evidence(run_dir: Path, gradle_output: Path) -> str:
    chunks: list[str] = []
    for path in (gradle_output, run_dir / "logs/latest.log"):
        try:
            chunks.append(path.read_text(encoding="utf-8", errors="replace"))
        except FileNotFoundError:
            continue
    return "\n".join(chunks)


def stop_server(process: subprocess.Popen[bytes]) -> None:
    if process.poll() is not None:
        return
    if process.stdin is not None:
        try:
            process.stdin.write(b"stop\n")
            process.stdin.flush()
            process.wait(timeout=30)
            return
        except (BrokenPipeError, subprocess.TimeoutExpired):
            pass
    process.terminate()
    try:
        process.wait(timeout=15)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=10)


def print_tail(text: str, lines: int = 120) -> None:
    print("\n".join(text.splitlines()[-lines:]), file=sys.stderr)


def main() -> int:
    args = parse_args()
    dependency_dir = args.dependency_dir.expanduser().resolve()
    run_dir = args.run_dir.expanduser().resolve()
    if not dependency_dir.is_dir():
        print(f"ERROR: dependency directory does not exist: {dependency_dir}", file=sys.stderr)
        return 2
    if args.timeout < 30:
        print("ERROR: timeout must be at least 30 seconds", file=sys.stderr)
        return 2

    if args.keep_run_dir:
        run_dir.mkdir(parents=True, exist_ok=True)
    else:
        safe_reset(run_dir)

    (run_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (run_dir / "server.properties").write_text(
        "online-mode=false\n"
        "motd=CC-Aeroworks smoke test\n"
        "max-tick-time=60000\n"
        "view-distance=4\n"
        "simulation-distance=4\n",
        encoding="utf-8",
    )

    result_dir = ROOT / "build/test-results/server-smoke"
    result_dir.mkdir(parents=True, exist_ok=True)
    gradle_output = result_dir / "gradle-output.log"

    command = [
        gradle_command(),
        f"-Pmod_dependency_dir={dependency_dir}",
        f"-Psmoke_server_dir={run_dir}",
        "runSmokeServer",
        "--no-daemon",
        "--stacktrace",
    ]
    print("+", " ".join(str(part) for part in command), flush=True)

    with gradle_output.open("wb") as output:
        process = subprocess.Popen(
            command,
            cwd=ROOT,
            stdin=subprocess.PIPE,
            stdout=output,
            stderr=subprocess.STDOUT,
        )

    deadline = time.monotonic() + args.timeout
    status = "timeout"
    try:
        while time.monotonic() < deadline:
            evidence = read_evidence(run_dir, gradle_output)
            fatal = next((marker for marker in FATAL_MARKERS if marker in evidence), None)
            if fatal:
                status = f"fatal marker: {fatal}"
                break
            if SUCCESS_PATTERN.search(evidence):
                status = "success"
                break
            exit_code = process.poll()
            if exit_code is not None:
                status = f"process exited before Done marker with code {exit_code}"
                break
            time.sleep(1)
    finally:
        stop_server(process)

    evidence = read_evidence(run_dir, gradle_output)
    if status == "success":
        print("Dedicated server reached the Done marker without a known fatal marker.")
        return 0

    print(f"ERROR: dedicated server smoke test failed: {status}", file=sys.stderr)
    print_tail(evidence)
    return 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError) as exception:
        print(f"ERROR: {exception}", file=sys.stderr)
        raise SystemExit(2)
