# mx polybench

The `mx_polybench` package implements polybench's integration with `mx benchmark`.

The package defines `mx polybench`, a helper command for running polybench benchmarks.
The `mx polybench` command is a simple wrapper around `mx benchmark` (the benchmarks can also be invoked using
`mx benchmark` directly).

The package also defines the interface that languages should use
to [register polybench benchmarks](#registering-benchmarks).

## Usage examples

The `mx polybench` command is a utility for listing and running polybench benchmarks.
Some usage examples are listed below.
In case the commands below become of sync with this document, you should use `--help` for complete and up-to-date
details.


```commandline
# Use '--list' to list available benchmarks.
$ cd graal/truffle
$ mx polybench --list
Listing all available polybench benchmarks.
Benchmark files (run using "mx polybench <glob_pattern>"):
        interpreter/fibonacci.sl
Suites (run using "mx polybench --suite <suite_name>" or "mx polybench --suite <suite_name>:<tag1>,<tag2>,..."):
        sl: {'benchmark', 'gate'}

# The list is populated based on the registrations of the loaded suites. When different suites are loaded, different benchmarks may become available.
$ cd graal/sulong
$ mx polybench --list
Listing all available polybench benchmarks.
Benchmark files (run using "mx polybench <glob_pattern>"):
        interpreter/deltablue.c.native.bc
        interpreter/sieve.c.native.bc
        interpreter/richards.c.native.bc
        interpreter/fibonacci.c.native.bc
        interpreter/fibonacci.sl
Suites (run using "mx polybench --suite <suite_name>" or "mx polybench --suite <suite_name>:<tag1>,<tag2>,..."):
        sulong: {'gate', 'benchmark'}
        sl: {'gate', 'benchmark'}

# Use 'run' to run benchmarks. Below are some examples.

# Run fibonacci.sl on the JVM (default, but you can specify --jvm to be explicit)
$ mx polybench interpreter/fibonacci.sl

# Run all interpreter benchmarks in native mode.
$ mx polybench --native 'interpreter/*'

# Polybench always uses the mx Java home as its host VM. It is recommended to use '--java-home' to change the host VM.
$ mx --java-home $CUSTOM_GRAALVM_HOME polybench interpreter/fibonacci.sl

# Run the sl 'gate' suite (suite definitions are described below, in the "Registering benchmarks" section).
$ mx polybench --suite sl:gate

# 'run' accepts any number of arguments after the benchmark/suite name.
# These arguments are passed to the polybench launcher (default), mx benchmark, or the VM:
$ mx polybench interpreter/fibonacci.sl polybenchArg1 --mx-benchmark-args mxBenchmarkArg --vm-args vmArg --polybench-args polybenchArg2
```

## Registering benchmarks

To use, a language should:
1. Invoke `mx_polybench.register_polybench_language` to register itself as a polybench language. 
The registration defines the Java distributions, native distributions, etc. required to run the language:
```
# Register a language.
mx_polybench.register_polybench_language(
    mx_suite=_suite,
    language="sl",
    distributions=["TRUFFLE_SL"],
)
```

2. Invoke `mx_polybench.register_polybench_benchmark_suite` to register a suite of polybench benchmarks.
   The registration declares a layout distribution containing benchmark files.
   The relative path of each benchmark file in the distribution is registered as a benchmark that can be invoked by
   name.
```
# Register a suite of benchmarks.
mx_polybench.register_polybench_benchmark_suite(
    mx_suite=_suite,
    name="sl",
    languages=["sl"],
    benchmark_distribution="SL_BENCHMARKS",
    benchmark_file_filter=".*sl",
)
```

When a suite is loaded by mx, these registrations will run and be picked up by polybench, making the languages and
benchmarks accessible to run using `mx polybench` and `mx benchmark`.

Optionally, the suite can declare a _suite runner_ that invokes a series of benchmarks.
Like with gates, the benchmarks can be filtered using an input set of tags.
A suite runner is intended to be a flexible way to define the semantics of CI jobs:
```
# Define a suite runner.
def sl_polybench_runner(polybench_run: mx_polybench.PolybenchRunFunction, tags: Set[str]) -> None:
    if "gate" in tags:
        polybench_run(["--jvm", "*.sl", "-w", "1", "-i", "1"])
        polybench_run(["--native", "*.sl", "-w", "1", "-i", "1"])
    if "benchmark" in tags:
        polybench_run(["--jvm", "*.sl", "-w", "10", "-i", "10"])
        polybench_run(["--native", "*.sl", "-w", "10", "-i", "10"])
        
# Update the suite registration.
mx_polybench.register_polybench_benchmark_suite(
    ...,
    runner=sl_polybench_runner,
    tags={"gate", "benchmark"},
)
```
