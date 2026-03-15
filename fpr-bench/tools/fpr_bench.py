#!/usr/bin/env python3
from __future__ import print_function

import argparse
import json
from datetime import datetime
import subprocess
import sys
import shutil
import os
import tempfile

try:
    from pathlib import Path
except ImportError:
    print("Error: Python 3.4+ is required to run this tool.", file=sys.stderr)
    sys.exit(2)

BENCH_TYPES = {
    "throughput": "dev.relism.fpr.bench.RouterBenchThroughput",
    "latency": "dev.relism.fpr.bench.RouterBenchLatency",
    "common": "dev.relism.fpr.bench.RouterBench",
}
BENCH_ORDER = ["throughput", "latency", "common"]


def parse_args(argv):
    parser = argparse.ArgumentParser(
        description="Run FPR JMH benchmarks and export JSON results."
    )
    subparsers = parser.add_subparsers(dest="command")

    run_parser = subparsers.add_parser("run", help="Build and run JMH benchmarks.")
    run_parser.add_argument("--tag", default="default", help="Tag for the output run folder.")
    run_parser.add_argument(
        "--no-build",
        action="store_true",
        help="Skip the Maven build step.",
    )
    run_parser.add_argument("--mvn", default="mvn", help="Maven executable path.")
    run_parser.add_argument("--java", default="java", help="Java executable path.")
    run_parser.add_argument("--jar", help="Path to the shaded JMH jar.")
    run_parser.add_argument(
        "--types",
        help="Comma-separated benchmark types: throughput, latency, common.",
    )
    run_parser.add_argument(
        "--prof-gc",
        action="store_true",
        help="Enable JMH GC profiler (-prof gc).",
    )
    run_parser.add_argument(
        "jmh_args",
        nargs=argparse.REMAINDER,
        help="Arguments passed to JMH (use -- to separate).",
    )

    args = parser.parse_args(argv)
    if args.command is None:
        parser.print_help()
        return None
    return args


def repo_root():
    # assumes this script lives under fpr-bench/tools (or similar)
    return Path(__file__).resolve().parents[2]


def validate_tag(tag):
    tag = tag.strip() if tag else ""
    if not tag:
        return "default"
    invalid_chars = set('<>:"/\\|?*')
    if any(ch in invalid_chars for ch in tag):
        raise ValueError('Tag contains invalid filename characters: <>:"/\\|?*')
    return tag


def resolve_executable(executable, label):
    resolved = shutil.which(executable)
    if resolved:
        return resolved
    if Path(executable).is_file():
        return str(Path(executable))
    raise RuntimeError("{} executable not found: {}".format(label, executable))


def run_subprocess(cmd, cwd, failure_message):
    try:
        return_code = subprocess.call(cmd, cwd=cwd)
    except OSError as exc:
        print("{}: {}".format(failure_message, exc), file=sys.stderr)
        return 2
    if return_code != 0:
        print(
            "{} (exit code {})".format(failure_message, return_code),
            file=sys.stderr,
        )
    return return_code


def run_subprocess_capture(cmd, cwd, log_path, failure_message):
    """
    Runs a subprocess, captures combined stdout+stderr, and always writes it to log_path.
    Returns the process exit code (or 2 on OS errors).
    """
    try:
        proc = subprocess.Popen(
            cmd,
            cwd=cwd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )
    except OSError as exc:
        try:
            log_path.write_text(str(exc) + "\n", encoding="utf-8")
        except OSError:
            pass
        print("{}: {}".format(failure_message, exc), file=sys.stderr)
        print("Log: {}".format(log_path), file=sys.stderr)
        return 2

    try:
        with log_path.open("wb") as log_file:
            stream = proc.stdout
            if stream is None:
                proc.wait()
            else:
                for chunk in iter(lambda: stream.read(4096), b""):
                    log_file.write(chunk)
                    try:
                        sys.stdout.buffer.write(chunk)
                        sys.stdout.buffer.flush()
                    except AttributeError:
                        sys.stdout.write(chunk.decode("utf-8", errors="replace"))
                        sys.stdout.flush()
        return_code = proc.wait()
    except OSError as exc:
        print("Warning: could not write log {}: {}".format(log_path, exc), file=sys.stderr)
        return_code = proc.wait()

    if return_code != 0:
        print("{} (exit code {})".format(failure_message, return_code), file=sys.stderr)
        print("Log: {}".format(log_path), file=sys.stderr)
    return return_code


def prompt_tag(default_tag):
    raw = input("Tag [{}]: ".format(default_tag)).strip()
    return raw if raw else default_tag


def prompt_int(label, default_value):
    while True:
        raw = input("{} [{}]: ".format(label, default_value)).strip()
        if not raw:
            return default_value
        try:
            return int(raw)
        except ValueError:
            print("Please enter a whole number.")


def prompt_optional(label):
    raw = input("{} (blank to skip): ".format(label)).strip()
    return raw if raw else None


def prompt_bench_types():
    while True:
        print("Select benchmark types:")
        for idx, name in enumerate(BENCH_ORDER, start=1):
            print("  {}) {}".format(idx, name))
        print("  a) all")
        raw = input("Choice [a]: ").strip().lower()
        if raw in ("", "a", "all"):
            return list(BENCH_ORDER)

        tokens = [token for token in raw.replace(",", " ").split() if token]
        selected = []
        invalid = None
        for token in tokens:
            if token.isdigit():
                index = int(token)
                if 1 <= index <= len(BENCH_ORDER):
                    name = BENCH_ORDER[index - 1]
                    if name not in selected:
                        selected.append(name)
                    continue
            if token in BENCH_TYPES:
                if token not in selected:
                    selected.append(token)
                continue
            invalid = token
            break

        if selected and invalid is None:
            return selected
        if invalid:
            print("Unknown selection: {}".format(invalid))
        else:
            print("No valid selections provided.")


def prompt_jmh_args():
    jmh_args = []
    wi = prompt_int("Warmup iterations (-wi)", 5)
    i = prompt_int("Measurement iterations (-i)", 5)
    f = prompt_int("Forks (-f)", 1)
    w = prompt_optional("Warmup time (-w, e.g. 1s)")
    r = prompt_optional("Measurement time (-r, e.g. 1s)")
    t = prompt_optional("Threads (-t)")
    tu = prompt_optional("Time unit (-tu, e.g. us)")

    if wi is not None:
        jmh_args.extend(["-wi", str(wi)])
    if i is not None:
        jmh_args.extend(["-i", str(i)])
    if f is not None:
        jmh_args.extend(["-f", str(f)])
    if w:
        jmh_args.extend(["-w", w])
    if r:
        jmh_args.extend(["-r", r])
    if t:
        jmh_args.extend(["-t", t])
    if tu:
        jmh_args.extend(["-tu", tu])
    return jmh_args


def parse_types_arg(value):
    if not value:
        return None
    tokens = [token.strip().lower() for token in value.split(",") if token.strip()]
    if not tokens:
        return None
    if "all" in tokens:
        return list(BENCH_ORDER)
    selected = []
    for token in tokens:
        if token not in BENCH_TYPES:
            raise ValueError("Unknown benchmark type: {}".format(token))
        if token not in selected:
            selected.append(token)
    return selected


def find_jmh_pids_windows():
    command = [
        "powershell",
        "-NoProfile",
        "-Command",
        (
            "Get-CimInstance Win32_Process | "
            "Where-Object { $_.Name -match 'java' -and $_.CommandLine -and "
            "($_.CommandLine -match 'org\\.openjdk\\.jmh' -or "
            "$_.CommandLine -match 'jmh' -or $_.CommandLine -match 'fpr-bench') } | "
            "Select-Object -ExpandProperty ProcessId"
        ),
    ]
    try:
        output = subprocess.check_output(command, universal_newlines=True)
    except (OSError, subprocess.CalledProcessError):
        return None
    pids = []
    for line in output.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            pids.append(int(line))
        except ValueError:
            continue
    return pids


def find_jmh_pids_unix():
    try:
        output = subprocess.check_output(
            ["ps", "-ax", "-o", "pid=,command="], universal_newlines=True
        )
    except (OSError, subprocess.CalledProcessError):
        return None
    pids = []
    for line in output.splitlines():
        line = line.strip()
        if not line:
            continue
        parts = line.split(None, 1)
        if len(parts) != 2:
            continue
        pid_str, cmdline = parts
        cmd_lower = cmdline.lower()
        if "java" not in cmd_lower:
            continue
        if "org.openjdk.jmh" in cmd_lower or "jmh" in cmd_lower or "fpr-bench" in cmd_lower:
            try:
                pids.append(int(pid_str))
            except ValueError:
                continue
    return pids


def kill_jmh_processes():
    if os.name == "nt":
        pids = find_jmh_pids_windows()
    else:
        pids = find_jmh_pids_unix()

    if pids is None:
        return False
    if not pids:
        return True

    success = True
    if os.name == "nt":
        for pid in pids:
            return_code = subprocess.call(
                ["taskkill", "/PID", str(pid), "/T", "/F"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            if return_code != 0:
                success = False
    else:
        for pid in pids:
            return_code = subprocess.call(
                ["kill", "-9", str(pid)],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            if return_code != 0:
                success = False
    return success


def should_ignore_lock(kill_success):
    if not kill_success:
        return True
    lock_path = Path(tempfile.gettempdir()) / "jmh.lock"
    return lock_path.exists()


def find_shaded_jar(bench_dir, override_path):
    if override_path:
        jar_path = Path(override_path).expanduser()
        if not jar_path.is_absolute():
            jar_path = (Path.cwd() / jar_path).resolve()
        if not jar_path.is_file():
            raise RuntimeError("Jar not found: {}".format(jar_path))
        return jar_path

    target_dir = bench_dir / "target"
    if not target_dir.exists():
        raise RuntimeError("Jar not found: {} does not exist".format(target_dir))
    candidates = sorted(
        target_dir.glob("*shaded*.jar"),
        key=lambda path: path.stat().st_mtime,
        reverse=True,
    )
    if not candidates:
        raise RuntimeError("Jar not found: no shaded JMH jar under {}".format(target_dir))
    return candidates[0].resolve()


def ensure_results_dir(results_dir):
    if results_dir.exists() and not results_dir.is_dir():
        raise RuntimeError("Results path exists but is not a directory: {}".format(results_dir))
    results_dir.mkdir(parents=True, exist_ok=True)


def prepare_run_dir(results_dir, timestamp, tag):
    safe_tag = validate_tag(tag)
    folder_name = "{}__{}".format(timestamp, safe_tag)
    run_dir = results_dir / folder_name
    run_dir.mkdir(parents=True, exist_ok=True)
    return run_dir.resolve()


def output_paths_for(run_dir, bench_type, prof_gc):
    suffix = "__gc" if prof_gc else ""
    json_path = run_dir / "{}{}.json".format(bench_type, suffix)
    log_path = run_dir / "{}{}.log".format(bench_type, suffix)
    return json_path.resolve(), log_path.resolve()


def run_benchmarks(args):
    root = repo_root()
    bench_dir = root / "fpr-bench"
    results_dir = bench_dir / "results"
    try:
        ensure_results_dir(results_dir)
    except RuntimeError as exc:
        print("Error: {}".format(exc), file=sys.stderr)
        return 2

    jmh_args = list(args.jmh_args or [])
    if jmh_args and jmh_args[0] == "--":
        jmh_args = jmh_args[1:]

    interactive = not jmh_args and sys.stdin.isatty()
    if interactive:
        tag = prompt_tag(args.tag or "default")
    else:
        tag = args.tag

    try:
        tag = validate_tag(tag)
    except ValueError as exc:
        print("Error: {}".format(exc), file=sys.stderr)
        return 2

    try:
        selected_types = parse_types_arg(args.types)
    except ValueError as exc:
        print("Error: {}".format(exc), file=sys.stderr)
        return 2
    if selected_types is None:
        if interactive:
            selected_types = prompt_bench_types()
        else:
            selected_types = list(BENCH_ORDER)

    if interactive:
        jmh_args = prompt_jmh_args()

    if not args.no_build:
        try:
            mvn_exec = resolve_executable(args.mvn, "Maven")
        except RuntimeError as exc:
            print("Error: {}".format(exc), file=sys.stderr)
            return 2

        mvn_cmd = [mvn_exec, "-q", "-pl", "fpr-bench", "-am", "package", "-Dfile.encoding=UTF-8"]
        exit_code = run_subprocess(mvn_cmd, root, "Maven build failed")
        if exit_code != 0:
            return exit_code

    try:
        jar_path = find_shaded_jar(bench_dir, args.jar)
    except RuntimeError as exc:
        print("Error: {}".format(exc), file=sys.stderr)
        return 2

    ignore_lock = should_ignore_lock(kill_jmh_processes())
    if ignore_lock:
        print(
            "Warning: Unable to terminate existing JMH instance or lock file exists; using -Djmh.ignoreLock=true",
            file=sys.stderr,
        )

    try:
        java_exec = resolve_executable(args.java, "Java")
    except RuntimeError as exc:
        print("Error: {}".format(exc), file=sys.stderr)
        return 2

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    run_dir = prepare_run_dir(results_dir, timestamp, tag)
    print("Run directory: {}".format(run_dir))

    prof_gc = bool(getattr(args, "prof_gc", False))

    for bench_type in selected_types:
        bench_class = BENCH_TYPES[bench_type]
        json_path, log_path = output_paths_for(run_dir, bench_type, prof_gc)

        # overwrite if exists
        try:
            if json_path.exists():
                json_path.unlink()
        except OSError:
            pass
        try:
            if log_path.exists():
                log_path.unlink()
        except OSError:
            pass

        jmh_cmd = [java_exec]
        if ignore_lock:
            jmh_cmd.append("-Djmh.ignoreLock=true")
        jmh_cmd.extend(["-jar", str(jar_path)])

        if prof_gc:
            jmh_cmd.extend(["-prof", "gc"])

        if jmh_args:
            jmh_cmd.extend(jmh_args)

        jmh_cmd.append("^{}\\.".format(bench_class))
        jmh_cmd.extend(["-rf", "json", "-rff", str(json_path)])

        exit_code = run_subprocess_capture(
            jmh_cmd, root, log_path, "JMH run failed ({})".format(bench_type)
        )
        if exit_code != 0:
            return exit_code

        # validate JSON
        try:
            with json_path.open("r", encoding="utf-8") as handle:
                data = json.load(handle)
            if not isinstance(data, list):
                raise ValueError("JSON root is not an array")
        except (OSError, ValueError, json.JSONDecodeError) as exc:
            print("Error: JSON could not be parsed: {}".format(exc), file=sys.stderr)
            print("JSON: {}".format(json_path), file=sys.stderr)
            print("Log : {}".format(log_path), file=sys.stderr)
            return 2

        size_bytes = json_path.stat().st_size
        print("JMH run completed ({})".format(bench_type))
        print("JSON: {}".format(json_path))
        print("LOG : {}".format(log_path))
        print("File size: {} bytes".format(size_bytes))
        print("Benchmarks: {}".format(len(data)))

    print("All selected benchmarks completed. Results folder: {}".format(run_dir))
    return 0


def main(argv):
    args = parse_args(argv)
    if args is None:
        return 2
    if args.command == "run":
        return run_benchmarks(args)
    print("Unknown command: {}".format(args.command), file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
