## Quick Start Guide

If you want to run a [**blackbox**](#dist) benchmark which is located in the Graal repository, try:

```
mx benchmark jmh-dist:* -- --jvm-config=graal-core
```

If you want to run a Graal [**whitebox**](#whitebox) benchmark, try:

```
mx benchmark jmh-whitebox:* -- --jvm-config=graal-core
```


If you want to run a JMH benchmark in an [**external jar**](#external-jar), try:

```
mx benchmark jmh-jar:* -- --jmh-jar=path/to/benchmarks.jar --jvm-config=graal-core
```

If you do not know or want to learn more, continue reading.

---

# JMH Integration in Graal

This guide describes how use the [JMH] micro benchmark harness in Graal.

There are *three* different methods of executing a [JMH] micro benchmark.
While this seems redundant at first sight, they all differ significantly in
purpose and implementation.
The following sections will discuss in details why this is the case.

## JMH Benchmarks in an external Jar File <a name="external-jar"></a>
In the simplest case the [JMH] are provided as a self-contained jar file, usually built with maven.
The integration in Graal is basically a wrapper for `mx vm -jar path/to/benchmarks.jar`.
The advantage is that it can be used in the same way as other [mx benchmark] based benchmark suites,
such as `mx benchmark dacapo:*`.
This includes JVM configuration selection via the `--jvm` and `--jvm-config` flags.
The optional `--jvm-name` option to set a benchmark suite name suffix in the `results.json` file.

```
mx benchmark jmh-jar:* --results-file=results.json -- --jmh-name=acme-benchmarks --jmh-jar=path/to/benchmarks.jar --jvm-config=graal-core
```
See [mx documentation][mx external-jar] for further in sights.

## *Normal* JMH Benchmarks in the Graal Repository (Blackbox) <a name="dist"></a>

The next category are *normal* JMH benchmarks that are located in the Graal repository.
*Normal* in the sense that they do not depend on Graal internals.
The `ArrayListBenchmark`, for example, benchmarks the performance of array lists.
Although it targets a specific optimization in Graal, it does not know about Graal internals.
I is possible to run it on any JVM.
It could also be in an external jar file, but for convenience it is located next to the Graal code.

Creating such an in-repo benchmark is a two-step procedure.

### Setting up an [mx project]
First, we need an [mx project] in the [`suite.py`] with `mx:JMH_1_21` in its `dependencies` and `annotationProcessors`.
(`JMH_1_21` was the latest [JMH] version available in [mx] at the time of writing.)

```python
"org.graalvm.micro.benchmarks" : {
  # ...
  "dependencies" : [
    "mx:JMH_1_21",
  ],
  "annotationProcessors" : ["mx:JMH_1_21"],
  "checkPackagePrefix" : "false",
}
```

**Warning:** Per default Graal [avoids self-compilation][graalC1Only].
This is controlled via package prefixes (`jdk.vm.ci`, `org.graalvm`, `com.oracle.graal`).
Therefore, placing the benchmarks a different package is advisable to ensure they are actually compiled by Graal.
The benchmarks in the `org.graalvm.micro.benchmarks` project, for instance,
are located in the `micro.benchmarks` package.
Also note the `"checkPackagePrefix" : "false"` in the project definition above.

### Setting up an [mx distribution]

The second step is creating an [mx distribution] that contains the [JMH] project.
```python
"GRAAL_COMPILER_MICRO_BENCHMARKS" : {
  "subDir" : "src",
  "dependencies" : ["org.graalvm.micro.benchmarks"],
},
```

A distribution may contain multiple [JMH] projects, as long as they do no mix [JMH] versions.

### Example:

```
mx benchmark jmh-dist:* --results-file=results.json -- --jvm-config=graal-core
```

The command above will run all [JMH] benchmarks that are not *whitebox benchmarks* (see [below](#whitebox)).
Note that the `bench-suite` dimension in the `results.json` file will be the name
of distribution, prefixed with `jmh-`.
So organizing the benchmarks in multiple distributions might be worth to consider.

For more infos please refer to the [mx documentation][mx in-repo].

## Graal Whitebox Benchmarks <a name="whitebox"></a>

Although the setup of Graal *whitebox* benchmarks is similar to the *blackbox* benchmarks,
their purpose is a very different.

Similar to *whitebox testing*, the goal of a *whitebox benchmark* is to stress an
internal part of the compiler, e.g., a specific *phase*.
To do so, these *benchmark projects* depend on compiler projects.
In order to make those projects visible in the [JMH] forks we need to execute the JVM with the `-XX:-UseJVMCIClassLoader` flag.
From the setup perspective this is the only difference to *blackbox benchmarks*.

It does not make sense to run a *whitebox* benchmark in a non-Graal JVM.
Although, comparing different modes (`hosted`, `jvmci` without `C1-only`) could be interesting,
the common mode is `jvcmi`, i.e., JVMCI is enabled and Graal is the optimizing compiler.
Also, the remark about package prefixes for *blackbox benchmarks* does not really apply.

### Example
```
mx benchmark jmh-whitebox:* --results-file=results.json -- --jvm-config=graal-core
```

The command above runs all *whitebox* benchmarks.
That are all benchmark distributions that have either a dependency on a `GRAAL*` distribution,
or include projects starting with `org.graalvm.compiler` in their distribution jar.


Refer to the [mx documentation][mx in-repo] for further information.

[JMH]: http://openjdk.java.net/projects/code-tools/jmh/
[mx JMH]: https://github.com/graalvm/mx/blob/master/docs/JMH.md
[mx in-repo]: https://github.com/graalvm/mx/blob/master/docs/JMH.md#in-repo
[mx external-jar]: https://github.com/graalvm/mx/blob/master/docs/JMH.md#external-jar
[mx benchmark]: https://github.com/graalvm/mx/blob/master/README.md
[mx project]: https://github.com/graalvm/mx/blob/master/README.md
[mx distribution]: https://github.com/graalvm/mx/blob/master/README.md
[graalC1Only]: https://github.com/graalvm/graal/blob/master/compiler/src/org.graalvm.compiler.hotspot/src/org/graalvm/compiler/hotspot/HotSpotGraalCompilerFactory.java
[`suite.py`]: https://github.com/graalvm/graal/blob/master/compiler/mx.compiler/suite.py
