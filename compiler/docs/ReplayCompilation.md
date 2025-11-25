# Replay Compilation

The GraalVM compiler can record the inputs to a compilation task, serialize these inputs into a JSON file, and reproduce
the compilation using the same inputs. Replay compilation is based on instrumenting the JVM Compiler Interface (JVMCI).
Truffle compilations are currently not supported. It is not a goal to execute the replayed code.

This file is a manual from the user's perspective. To learn how replay compilation is implemented, start by reading
`ReplayCompilationSupport.java` and `CompilerInterfaceDeclarations.java`.

## Example

Recording is enabled with the option `-Djdk.graal.RecordForReplay=*`. The value is a method filter selecting the methods
to be recorded. The syntax of method filters is explained `MethodFilter.java`.

The below command records and serializes every compilation into `./replay-files/replaycomp/<compile-id>.json`.
The directories are created if they do not exist.

```shell
mx benchmark renaissance:scrabble -- -Djdk.graal.RecordForReplay='*' -Djdk.graal.DumpPath=$PWD/replay-files -- -r 1
```

It is recommended to select specific methods rather than `*` to avoid slowing the compiler down and producing gigabytes
of data. The compile speed overhead for recorded methods can be on the order of 10x. The size of a typical compilation
unit is between 1 and 10 MB but compression saves 95% of space. Note that if the VM exits during an ongoing compilation,
some of the JSON files may be incomplete.

The `mx replaycomp` command finds all JSON replay files found in a given directory (and subdirectories) and invokes
the replay compiler on each. The command also accepts a path to a single file.

```shell
mx replaycomp ./replay-files
```

## Debugging

A replayed compilation can be debugged using a standard Java debugger (see `Debugging.md`).

```shell
mx -d replaycomp ./replay-files
```

## Record Crashed Compilations

Using the `-Djdk.graal.CompilationFailureAction=Diagnose` option, the compiler retries and records crashed compilations.
The command below exercises this behavior by forcing a crash. The JSON replay file can then be found in the `replaycomp`
directory inside the diagnostic zip archive.

```shell
mx benchmark renaissance:scrabble -- -Djdk.graal.CompilationFailureAction=Diagnose -Djdk.graal.CrashAt=hashCode -- -r 1
```

It is possible to **not** record retried compilations with the option `-Djdk.graal.DiagnoseOptions=RecordForReplay=`.

When a recorded compilation ends with an exception, the type and stack trace of the exception is saved in the replay
file. During replay, the launcher verifies that the replayed compilation throws an exception of the same type. Use the
`--verbose=true` option to print the stack trace of the recorded exception.

```shell
mx replaycomp --verbose=true ./replay-files
```

## Replay with JVM Arguments

JVM arguments, including compiler options, can be passed directly to the `replaycomp` command.

```shell
mx replaycomp -Djdk.graal.Dump=:1 ./replay-files
```

Any `-ea`, `-esa`, and `-X` arguments from the command line are passed to the JVM as well.

## Jargraal vs. Libgraal

Jargraal can replay both jargraal and libgraal compilations. Libgraal can replay only libgraal compilations.

To replay on libgraal, build libgraal first. Then, pass the `--libgraal` argument to `mx replaycomp`, which invokes the
native launcher. With the below commands, all replay related processing (including JSON parsing) is performed by
libgraal code.

```shell
mx -p ../vm --env libgraal build
mx replaycomp --libgraal ./replay-files
```

It is possible to specify a different JDK for the replay with the `--jdk-home` argument. The below command runs replay
using the libgraal image that is part of the built JDK in `$GRAALVM_HOME`.

```shell
GRAALVM_HOME=$(mx -p ../vm --env libgraal graalvm-home)
mx replaycomp --jdk-home $GRAALVM_HOME ./replay-files
```

## Replay Options

`--verbose=true` prints additional information for every compilation, including:
* the system properties of the recorded compilation (the options include the VM command from the recorded run),
* the final canonical graph of the recorded/replayed compilation,
* the stack trace of the exception thrown during the recorded/replayed compilation.

```shell
mx replaycomp --verbose=true ./replay-files
```

`--compare-graphs=true` compares the final canonical graph of the replayed compilation to the recorded one, which is
included in the JSON replay file. If there is a mismatch, the command exits with a non-zero status.

```shell
mx replaycomp --compare-graphs=true ./replay-files
```

If the replayed compilation diverges from the recorded one, the compiler may query JVMCI information that is not
recorded in the JSON file. The default behavior is to return default values, which may not cause a compiler crash.
The `-Djdk.graal.ReplayDivergenceIsFailure=true` argument prevents using default values and instead causes a crash.

```shell
mx replaycomp -Djdk.graal.ReplayDivergenceIsFailure=true ./replay-files
```

## Performance Counters

When replaying with the `--benchmark` command on the AMD64 Linux platform, the replay launcher can count hardware
performance events via the [PAPI](https://github.com/icl-utk-edu/papi) library. To enable this, it is necessary to set
up PAPI and build the optional PAPI bridge library. Note that recent architectures may not be supported; see the list
here: https://github.com/icl-utk-edu/papi/wiki/Supported-Architectures.

### PAPI Setup

PAPI can usually be installed with the package manager (`papi-devel` on Fedora, `libpapi-dev` on Ubuntu). The PAPI
bridge links against the PAPI available on the system.

To monitor hardware events, it may be necessary to lower the system's restrictions for accessing hardware performance
counters like shown below.

```shell
sudo sysctl kernel.perf_event_paranoid=-1
```

Additionally, the performance monitoring library (libpfm) may fail to select the appropriate performance monitoring unit
(PMU). The selection can be forced to `amd64` using an environment variable.

```shell
export LIBPFM_FORCE_PMU=amd64
```

To discover the available counters, use the `papi_avail` and `papi_native_avail` commands, which are part of the PAPI
installation. Verify that a particular event like `PAPI_TOT_INS` (retired instruction count) is counted using the
`papi_command_line` utility.

```shell
papi_avail
papi_native_avail
papi_command_line PAPI_TOT_INS
```

### PAPI Bridge

The below command builds the PAPI bridge library using the PAPI library available on the system.

```shell
ENABLE_PAPI_BRIDGE=true mx build --dependencies PAPI_BRIDGE
```

The launcher accepts a comma-separated list of event names. The event counts are reported for every benchmark iteration.

```shell
ENABLE_PAPI_BRIDGE=true mx replaycomp ./replay-files --benchmark --event-names PAPI_TOT_INS
```
