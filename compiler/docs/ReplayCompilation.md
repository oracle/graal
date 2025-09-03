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

## Replay with JVM Arguments

JVM arguments, including compiler options, can be passed directly to the `replaycomp` command.

```shell
mx replaycomp -Djdk.graal.Dump=:1 ./replay-files
```

Any `-ea`, `-esa`, and `-X` arguments from the command line are passed to the JVM as well.

## Jargraal vs. Libgraal

Jargraal can replay both jargraal and libgraal compilations. Libgraal can replay only libgraal compilations.

It is necessary to explicitly enable the replay launcher entry point when building libgraal using the VM argument
`-Ddebug.jdk.graal.enableReplayLauncher=true`.

```shell
EXTRA_IMAGE_BUILDER_ARGUMENTS=-Ddebug.jdk.graal.enableReplayLauncher=true mx --env libgraal build
```

When the `--libgraal` argument is passed to `mx replaycomp`, the previously built libgraal native library is loaded, and
the native launcher is invoked instead of the Java launcher. With the below command, all replay related processing
(including JSON parsing) is performed by libgraal code.

```shell
mx replaycomp --libgraal ./replay-files
```

## Replay Options

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
