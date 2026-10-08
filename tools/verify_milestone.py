"""Run the spec section 19 "done when" checks and judge them by their logs, not by Gradle's exit code.

Steps, in order:
  build          gradlew build                       -> BUILD SUCCESSFUL
  data           gradlew runData                     -> data gatherer ran for skyseam
  gametest       gradlew runGameTestServer           -> "All N required tests passed"
  server         gradlew runServer -PbootCheck       -> "Done (" and the Skyseam boot-check marker

Every step also fails on an ERROR line or a stack frame from Skyseam's own code, or on a crash report.
Logs are written to build/verify/<step>.log. Gradle can report success when the game-test server never
started (docs/ENVIRONMENT.md, note A), which is why the log text decides.

Run: tools/.venv/Scripts/python.exe tools/verify_milestone.py [step ...]
"""
import os
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOG_DIR = ROOT / "build" / "verify"
GRADLEW = str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))
TIMEOUT_SECONDS = 900

OWN_ERROR = re.compile(r"/ERROR\].*skyseam|at com\.selluastar\.skyseam\.|Minecraft Crash Report", re.IGNORECASE)
GAMETEST_PASSED = re.compile(r"All (\d+) required tests passed")
GAMETEST_FAILED = re.compile(r"(\d+) required tests? failed")


def check_build(log):
    return "BUILD SUCCESSFUL" in log, "BUILD SUCCESSFUL" if "BUILD SUCCESSFUL" in log else "no BUILD SUCCESSFUL"


def check_data(log):
    ran = "Initializing Data Gatherer for mods [skyseam]" in log
    return ran and "BUILD SUCCESSFUL" in log, "data gatherer ran" if ran else "data gatherer did not start"


def check_gametest(log):
    failed = GAMETEST_FAILED.search(log)
    if failed:
        return False, failed.group(0)
    passed = GAMETEST_PASSED.search(log)
    if not passed:
        started = "Started game test server" in log
        return False, "tests never finished" if started else "game test server did not start"
    return True, passed.group(0)


def check_server(log):
    done = re.search(r"Done \([\d.]+s\)!", log)
    marker = "Skyseam boot check passed" in log
    if not done:
        return False, "server never reached Done"
    if not marker:
        return False, "boot-check marker missing"
    return True, f"{done.group(0)} + boot check"


STEPS = {
    "build": (["build"], check_build),
    "data": (["runData"], check_data),
    "gametest": (["runGameTestServer"], check_gametest),
    "server": (["runServer", "-PbootCheck"], check_server),
}


def kill_dev_minecraft():
    """Stop any dev Minecraft JVM of this project left behind by a timed-out run."""
    if os.name != "nt":
        return
    script = (
        "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | "
        f"Where-Object {{ $_.CommandLine -like '*{ROOT.name}*' -and $_.CommandLine -like '*RunVmArgs*' }} | "
        "ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"
    )
    subprocess.run(["powershell", "-NoProfile", "-Command", script], check=False)


def without_project_path(line):
    """The project folder is called Skyseam, so drop it from paths before looking for Skyseam errors."""
    for root in {str(ROOT), ROOT.as_posix(), str(ROOT).replace("\\", "\\\\")}:
        line = line.replace(root, "<project>")
    return line


def run_step(name):
    args, check = STEPS[name]
    log_path = LOG_DIR / f"{name}.log"
    started = time.time()
    with open(log_path, "w", encoding="utf-8", errors="replace") as out:
        process = subprocess.Popen([GRADLEW, *args, "--console=plain"], cwd=ROOT, stdout=out, stderr=subprocess.STDOUT)
        try:
            exit_code = process.wait(timeout=TIMEOUT_SECONDS)
        except subprocess.TimeoutExpired:
            process.kill()
            kill_dev_minecraft()
            exit_code = None
    log = log_path.read_text(encoding="utf-8", errors="replace")
    ok, detail = check(log)
    own_errors = [line.strip() for line in log.splitlines() if OWN_ERROR.search(without_project_path(line))]
    if own_errors:
        ok, detail = False, f"{len(own_errors)} Skyseam error line(s), first: {own_errors[0][:160]}"
    if exit_code is None:
        ok, detail = False, f"timed out after {TIMEOUT_SECONDS}s ({detail})"
    return ok, detail, exit_code, time.time() - started


def main(selected):
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    results = []
    for name in selected:
        print(f"-> {name} ...", flush=True)
        ok, detail, exit_code, seconds = run_step(name)
        results.append((name, ok, detail, exit_code, seconds))
        print(f"   {'PASS' if ok else 'FAIL'}  {detail}  (exit {exit_code}, {seconds:.0f}s, log build/verify/{name}.log)", flush=True)
    print()
    print(f"{'step':<10} {'result':<6} detail")
    for name, ok, detail, _, _ in results:
        print(f"{name:<10} {'PASS' if ok else 'FAIL':<6} {detail}")
    return 0 if all(r[1] for r in results) else 1


if __name__ == "__main__":
    requested = sys.argv[1:] or list(STEPS)
    unknown = [s for s in requested if s not in STEPS]
    if unknown:
        sys.exit(f"unknown step(s): {', '.join(unknown)}. Known: {', '.join(STEPS)}")
    sys.exit(main(requested))
