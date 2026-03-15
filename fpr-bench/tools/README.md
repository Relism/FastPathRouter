# FPR Bench CLI

Run JMH benchmarks via a single Python CLI and export JSON results.

Requires Python 3.4+.

## Run a benchmark
From the repo root:

```sh
python fpr-bench/tools/fpr_bench.py run
```

This builds the shaded JMH jar and writes results to `fpr-bench/results/`.
You will be prompted for benchmark types and common JMH settings.

Available types: `throughput`, `latency`, `common` (runs the combined bench).

## Pass JMH arguments
Use `--` to pass flags directly to JMH (this skips the menu):

```sh
python fpr-bench/tools/fpr_bench.py run -- --wi 5 -i 5 -f 5 -tu us
```

## Results location
JSON output is stored in:

```
fpr-bench/results/YYYYMMDD_HHMMSS__<tag>__<type>.json
```

If multiple types are selected, one JSON file is produced per type.

## Examples
Skip the build and use a custom tag:

```sh
python fpr-bench/tools/fpr_bench.py run --no-build --tag smoke
```

Run only latency and throughput types non-interactively:

```sh
python fpr-bench/tools/fpr_bench.py run --types latency,throughput -- --wi 3 -i 3 -f 1
```

Use custom `mvn` and `java` executables:

```sh
python fpr-bench/tools/fpr_bench.py run --mvn mvn.cmd --java java
```

If another JMH instance is detected, the tool will try to terminate it. If that
fails, it runs with `-Djmh.ignoreLock=true`.
