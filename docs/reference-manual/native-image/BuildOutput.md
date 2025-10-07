---
layout: docs
toc_group: build-overview
link_title: Build Output
permalink: /reference-manual/native-image/overview/BuildOutput/
redirect_from: /reference-manual/native-image/BuildOutput/
---

# Native Image Build Output

* [Build Stages](#build-stages)
* [Security Report](#security-report)
* [Recommendations](#recommendations)
* [Resource Usage Statistics](#resource-usage-statistics)
* [Build Artifacts](#build-artifacts)
* [Machine-Readable Build Output](#machine-readable-build-output)
* [PGO Profile Format](#pgo-profile-format)

Here you will find information about the build output of GraalVM Native Image.
Below is the example output when building a native executable of the `HelloWorld` class:


<!--
To update the output below:
$ cd substratevm-enterprise
Ensure to set JAVA_HOME to a labsjdk-ee
$ mx build
$ (stty cols 80 && mx helloworld -g)
Remove the `experimental option(s)` section
Remove the `# Printing compilation-target` and `# Printing native-library` lines
Replace the absolute paths at the end to start with `/home/janedoe/helloworld`
-->

```
================================================================================
GraalVM Native Image: Generating 'helloworld' (executable)...
================================================================================
[1/8] Initializing...                                           (4.4s @ 0.29GiB)
 Builder configuration:
 - Java version: 26+13, vendor version: Oracle GraalVM 26-dev+13.1
 - Graal compiler: optimization level: 2, target machine: x86-64-v3, PGO: ML-inferred
 - C compiler: gcc (linux, x86_64, 13.3.0)
 - Assertions: enabled, system assertions: enabled
 - 1 user-specific feature(s):
   - com.oracle.svm.thirdparty.gson.GsonFeature
 Image configuration:
 - Garbage collector: Serial GC (max heap size: 80% of RAM)
 - Assertions: disabled (class-specific config may apply), system assertions: disabled
--------------------------------------------------------------------------------
Build resources:
 - 30.00GiB of memory (48.0% of system memory, capped at 30GiB)
 - 32 thread(s) (88.9% of 36 available processor(s), determined at start)
[2/8] Performing analysis...  [*******]                         (3.7s @ 0.58GiB)
    2,140 types,   1,939 fields, and   8,997 methods found reachable
      775 types,      35 fields, and     244 methods registered for reflection
       49 types,      35 fields, and      48 methods registered for JNI access
       52 resource accesses registered with 107B total size
        4 native libraries: dl, pthread, rt, z
[3/8] Building universe...                                      (0.9s @ 0.74GiB)
[4/8] Parsing methods...      [*]                               (1.6s @ 0.72GiB)
[5/8] Inlining methods...     [***]                             (0.5s @ 0.66GiB)
[6/8] Compiling methods...    [***]                             (9.9s @ 0.81GiB)
[7/8] Laying out methods...   [*]                               (1.1s @ 0.67GiB)
[8/8] Creating image...       [**]                              (2.5s @ 0.90GiB)
   2.86MiB (21.13%) for code area:     4,078 compilation units
   3.56MiB (26.33%) for image heap:   63,478 objects and 1 resource
   6.01MiB (44.40%) for debug info generated in 0.4s
   7.11MiB (52.55%) for other data
  13.53MiB in total image size, 6.88MiB in total file size
--------------------------------------------------------------------------------
Top 10 origins of code area:            Top 10 object types in image heap:
 342.94KiB java.base/java.util           820.99KiB byte[] for string data
 289.94KiB java.base/java.lang           750.97KiB byte[] for code metadata
 270.50KiB o.g.n.~e/c.o.svm.core.code    347.48KiB java.base/java.lang.String
 189.67KiB o.g.n.~e/c.o.s.c.genscavenge  217.34KiB o.g.n.~e/c.o.s.c.h.Dyna~anion
 129.09KiB java.base/j.util.concurrent   209.51KiB java.base/java.lang.Class
  83.00KiB o.g.n.~e/c.o.s.c.j.functions  184.75KiB java.base/j.u.HashMap$Node
  81.76KiB java.base/java.util.stream    115.53KiB java.base/char[]
  76.73KiB o.g.n.~e/com.oracle.svm.core  107.66KiB java.base/j.i.u.SoftR~nceKey
  60.32KiB o.g.n.~e/c.o.svm.core.thread  105.09KiB java.base/java.lang.Object[]
  58.18KiB o.g.n.~e/c.o.svm.graal.stubs   88.63KiB java.base/j.u.c.Concu~p$Node
   1.25MiB for 119 more packages         700.05KiB for 585 more object types
        Use '--emit build-report' to create a report with more details.
--------------------------------------------------------------------------------
Security report:
 - Binary includes Java deserialization.
 - CycloneDX SBOM with 5 component(s) is embedded in binary (406B). 6 type(s) could not be associated to a component.
 - Advanced obfuscation not enabled; enable with '-H:AdvancedObfuscation=""' (experimental support).
--------------------------------------------------------------------------------
Recommendations:
 G1GC: Use the G1 GC ('--gc=G1') for improved latency and throughput.
 PGO:  Use Profile-Guided Optimizations ('--pgo') for improved throughput.
 FUTR: Use '--future-defaults=all' to prepare for future releases.
 HEAP: Set max heap for improved and more predictable memory usage.
 CPU:  Enable more CPU features with '-march=native' for improved performance.
--------------------------------------------------------------------------------
   1.3s (4.8% of total time) in 88 GCs | Peak RSS: 2.14GiB | CPU load: 18.03
--------------------------------------------------------------------------------
Build artifacts:
 /home/janedoe/helloworld/gdb-debughelpers.py (debug_info, 80.60KiB)
 /home/janedoe/helloworld/helloworld (executable, 6.88MiB)
 /home/janedoe/helloworld/helloworld.debug (debug_info, 6.66MiB)
 /home/janedoe/helloworld/sources (debug_info, 37.61MiB)
================================================================================
Finished generating 'helloworld' in 25.5s.
```

## Build Stages

### <a name="stage-initializing"></a>Initializing
In this stage, the Native Image build process is set up and [`Features`](https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/hosted/Feature.html) are initialized.

#### <a name="glossary-imagekind"></a>Native Image Kind
By default, Native Image generates *executables* but it can also generate [*native shared libraries*](InteropWithNativeCode.md) and [*static executables*](guides/build-static-and-mostly-static-executable.md).

#### <a name="glossary-java-info"></a>Java Version Info
The Java and vendor version of the Native Image process.
Both are also used for the `java.vm.version` and `java.vendor.version` properties within the generated native binary.
Please report version and vendor when you [file issues](https://github.com/oracle/graal/issues/new).

#### <a name="glossary-graal-compiler"></a>Graal Compiler
The selected optimization level and targeted machine type used by the Graal compiler.
The optimization level can be controlled with the `-O` option and defaults to `2`, which enables aggressive optimizations.
Use `-Ob` to enable quick build mode, which speeds up the [compilation stage](#stage-compiling).
This is useful during development to reduce image build time.
Use `-Os` to optimize for size.
The targeted machine type can be selected with the `-march` option and defaults to `x86-64-v3` on AMD64 and `armv8-a` on AArch64.
See [here](#recommendation-cpu) for recommendations on how to use this option.

On Oracle GraalVM, the line also shows information about [Profile-Guided Optimization (PGO)](#recommendation-pgo).
- `off`: PGO is not used
- `instrument`: The generated executable or shared library is instrumented to collect data for PGO (`--pgo-instrument`)
- `user-provided`: PGO is enabled and uses a user-provided profile (for example `--pgo default.iprof`)
- `ML-inferred`: A machine learning (ML) model is used to infer profiles for control split branches statically.

#### <a name="glossary-ccompiler"></a>C Compiler
The C compiler executable, vendor, target architecture, and version info used by the Native Image build process.

#### <a name="glossary-builder-assertions"></a>Assertions in the Builder
This shows whether Java assertions and system assertions are enabled for the Native Image Builder process.
Enabling them can help the GraalVM team identify and debug problems in the Builder.

#### <a name="glossary-user-specific-features"></a>User-Specific Features
All [`Features`](https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/hosted/Feature.html) that are provided or explicitly enabled by the user, or implicitly registered on the user's behalf (for example, by a framework).
Internal features used by GraalVM Native Image are not included in this list.

#### <a name="glossary-gc"></a>Garbage Collector
The garbage collector used within the generated executable:
- The *Serial GC* is the default GC and optimized for low memory footprint and small Java heap sizes.
- The *G1 GC* (not available in GraalVM Community Edition) is a multithreaded GC that is optimized to reduce stop-the-world pauses and therefore improve latency while achieving high throughput.
- The *Epsilon GC* does not perform any garbage collection and is designed for very short-running applications that only allocate a small amount of memory.

For more information see the [docs on Memory Management](MemoryManagement.md).

#### <a name="glossary-gc-max-heap-size"></a>Maximum Heap Size
By default, the heap size is limited to a certain percentage of your system memory, allowing the garbage collector to freely allocate memory according to its policy.
Use the `-Xmx` option when invoking your native executable (for example `./myapp -Xmx64m` for 64MB) to limit the maximum heap size for a lower and more predictable memory footprint.
This can also improve latency in some cases.
Use the `-R:MaxHeapSize` option when building with Native Image to preconfigure the maximum heap size.

#### <a name="glossary-image-assertions"></a>Assertions in the Generated Image
This shows whether Java assertions and system assertions are enabled in the generated image.
Enabling them can help identifying and debugging problems in the Java code built into the image.

#### <a name="glossary-experimental-options"></a>Experimental Options
A list of all active experimental options, including their origin and possible API option alternatives if available.

Using experimental options should be avoided in production and can change in any release.
If you rely on experimental features and would like an option to be considered stable, please file an issue.

#### <a name="glossary-picked-up-ni-options"></a>Picked up `NATIVE_IMAGE_OPTIONS`
Additional build options picked up via the `NATIVE_IMAGE_OPTIONS` environment variable.
Similar to `JAVA_TOOL_OPTIONS`, the value of the environment variable is prefixed to the options supplied to `native-image`.
Argument files are not allowed to be passed via `NATIVE_IMAGE_OPTIONS`.
The `NATIVE_IMAGE_OPTIONS` environment variable is designed to be used by users, build environments, or tools to inject additional build options.

#### <a name="glossary-build-resources"></a>Build Resources
The memory limit and number of threads used by the build process.

More precisely, the memory limit of the Java heap, so actual memory consumption can be higher.
Please check the [peak RSS](#glossary-peak-rss) reported at the end of the build to understand how much memory was actually used.
The actual memory consumption can also be lower than the limit set, as the GC only commits memory that it needs.
By default, the build process uses the dedicated mode (which uses 85% of system memory) in containers or CI environments (when the `$CI` environment variable is set to `true`), but never more than 30GiB of memory.
Otherwise, it uses shared mode, which uses the available memory to avoid memory pressure on developer machines.
If less than 8GiB of memory are available, the build process falls back to the dedicated mode.
Therefore, consider freeing up memory if your machine is slow during a build, for example, by closing applications that you do not need.
It is possible to override the default behavior and set relative or absolute memory limits, for example with `-J-XX:MaxRAMPercentage=60.0` or `-J-Xmx16g`.
`Xms` (for example, `-J-Xms9g`) can also be used to ensure a minimum for the limit, if you know the image needs at least that much memory to build.

By default, the build process uses all available processors to maximize speed, but not more than 32 threads.
Use the `--parallelism` option to set the number of threads explicitly (for example, `--parallelism=4`).
Use fewer threads to reduce load on your system as well as memory consumption (at the cost of a slower build process).

### <a name="stage-analysis"></a>Performing Analysis
In this stage, a [points-to analysis](https://dl.acm.org/doi/10.1145/3360610) is performed.
The progress indicator visualizes the number of analysis iterations.
A large number of iterations can indicate problems in the analysis likely caused by misconfiguration or a misbehaving feature.

#### <a name="glossary-reachability"></a>Reachable Types, Fields, and Methods
The number of types (primitives, classes, interfaces, and arrays), fields, and methods that are found reachable by the static analysis.
The reachability metrics give an impression of how small or large the application is.
These metrics can be helpful when compared before and after merging code changes or adding, removing, or upgrading dependencies of an application.
This can help to understand the impact that certain code changes or dependencies have on the overall native binary.
A larger number of reachable types, fields, and methods will also result in a larger native binary.

#### <a name="glossary-reflection-registrations"></a>Reflection Registrations
The number of types, fields, and methods that are registered for reflection.
Large numbers can cause significant reflection overheads, slow down the build process, and increase the size of the native binary (see [reflection metadata](#glossary-reflection-metadata)).

#### <a name="glossary-jni-access-registrations"></a>JNI Access Registrations
The number of types, fields, and methods that are registered for [JNI](JNI.md) access.

#### <a name="glossary-foreign-downcall-and-upcall-registrations"></a>Foreign Access Registrations
The number of downcalls and upcalls registered for [foreign function access](FFM-API.md).

#### <a name="glossary-runtime-methods"></a>Runtime Compiled Methods
The number of methods marked for runtime compilation.
This number is only shown if runtime compilation is built into the executable, for example, when building a [Truffle](https://github.com/oracle/graal/tree/master/truffle) language.
Runtime-compiled methods account for [graph encodings](#glossary-graph-encodings) in the heap.

### <a name="stage-universe"></a>Building Universe
In this stage, a universe with all types, fields, and methods is built, which is then used to create the native binary.

### <a name="stage-parsing"></a>Parsing Methods
In this stage, the Graal compiler parses all reachable methods.
The progress indicator is printed periodically at an increasing interval.

### <a name="stage-inlining"></a>Inlining Methods
In this stage, trivial method inlining is performed.
The progress indicator visualizes the number of inlining iterations.

### <a name="stage-compiling"></a>Compiling Methods
In this stage, the Graal compiler compiles all reachable methods to machine code.
The progress indicator is printed periodically at an increasing interval.

### <a name="stage-layouting"></a>Laying Out Methods
In this stage, compiled methods are laid out.
The progress indicator is printed periodically at an increasing interval.

### <a name="stage-creating"></a>Creating Image
In this stage, the native binary is created and written to disk.
Debug info is also generated as part of this stage (if requested).
This section breaks down the total image size as well as [code area](#glossary-code-area) and [image heap](#glossary-image-heap) (see below for more details).
The total image size is calculated before linking by summing the sizes of the code area, image heap, debug information (if requested and embedded in the binary), and other data.
The total file size is the actual size of the image on disk after linking.
Typically, the file size is slightly smaller than the image size due to additional link time optimizations.

#### <a name="glossary-code-area"></a>Code Area
The code area contains machine code produced by the Graal compiler for all reachable methods.
Therefore, reducing the number of [reachable methods](#glossary-reachability) also reduces the size of the code area.

##### <a name="glossary-code-area-origins"></a>Origins of Code Area
To help users understand where the machine code of the code area comes from, the build output shows a breakdown of the top origins.
An origin is a group of Java sources and can be a JAR file, a package name, or a class name, depending on the information available.
The [`java.base` module](https://docs.oracle.com/en/java/javase/22/docs/api/java.base/module-summary.html), for example, contains base classes from the JDK.
The `svm.jar` file, the `org.graalvm.nativeimage.base` module, and similar origins contain internal sources for the Native Image runtime.
To reduce the size of the code area and with that, the total size of the native executable, re-evaluate the dependencies of your application based on the code area breakdown.
Some libraries and frameworks are better prepared for Native Image than others, and newer versions of a library or framework may improve (or worsen) their code footprint.

#### <a name="glossary-image-heap"></a>Image Heap
The heap contains reachable objects such as static application data, metadata, and `byte[]` for different purposes (see below).

##### <a name="glossary-general-heap-data"></a>General Heap Data Stored in `byte[]`
The total size of all `byte[]` objects that are neither used for `java.lang.String`, nor [code metadata](#glossary-code-metadata), nor [reflection metadata](#glossary-reflection-metadata), nor [graph encodings](#glossary-graph-encodings).
Therefore, this can also include `byte[]` objects from application code.

##### <a name="glossary-embedded-resources"></a>Embedded Resources Stored in `byte[]`
The total size of all `byte[]` objects used for storing resources (for example, files accessed via `Class.getResource()`) within the native binary.
The number of resources is shown in the [Heap](#glossary-image-heap) section.
A list of all resources including additional information such as their module, name, origin, and size are included in the [build reports](BuildOptions.md#build-output-and-build-report).
This information can also be requested in the JSON format using the `-H:+GenerateEmbeddedResourcesFile` option.
Such a JSON file validates against the JSON schema defined in [`embedded-resources-schema-v1.0.0.json`](https://github.com/oracle/graal/tree/master/docs/reference-manual/native-image/assets/embedded-resources-schema-v1.0.0.json).

##### <a name="glossary-code-metadata"></a>Code Metadata Stored in `byte[]`
The total size of all `byte[]` objects used for metadata for the [code area](#glossary-code-area).
Therefore, reducing the number of [reachable methods](#glossary-reachability) also reduces the size of this metadata.

##### <a name="glossary-reflection-metadata"></a>Reflection Metadata Stored in `byte[]`
The total size of all `byte[]` objects used for reflection metadata, including types, field, method, and constructor data.
To reduce the amount of reflection metadata, reduce the number of [elements registered for reflection](#glossary-reflection-registrations).

##### <a name="glossary-graph-encodings"></a>Graph Encodings Stored in `byte[]`
The total size of all `byte[]` objects used for graph encodings.
These encodings are a result of [runtime compiled methods](#glossary-runtime-methods).
Therefore, reducing the number of such methods also reduces the size of corresponding graph encodings.

##### <a name="glossary-heap-alignment"></a>Heap Alignment
Additional space reserved to align the heap for the [selected garbage collector](#glossary-gc).
The heap alignment may also contain GC-specific data structures.
Its size can therefore only be influenced by switching to a different garbage collector.

#### <a name="glossary-debug-info"></a>Debug Info
The total size of generated debug information (if enabled).

#### <a name="glossary-other-data"></a>Other Data
The amount of data in the binary that is neither in the [code area](#glossary-code-area), nor in the [heap](#glossary-image-heap), nor [debug info](#glossary-debug-info).
This data typically contains internal information for Native Image and should not be dominating.

## Security Report

*This section is not available in GraalVM Community Edition.*

#### <a name="glossary-deserialization"></a>Deserialization
This shows whether Java deserialization is included in the native executable or not.
If not included, the attack surface of the executable is reduced as the executable cannot be exploited with attacks based on Java deserialization.

#### <a name="glossary-sbom"></a><a name="glossary-embedded-sbom"></a>Software Bill of Material (SBOM)
This section indicates whether an SBOM was assembled and in what ways it was stored.
The storage formats include: `embed`, which embeds the SBOM in the binary; `classpath`, which saves the SBOM to the classpath; and `export`, which includes the SBOM as a JSON build artifact.
The SBOM feature is enabled by default and defaults to the `embed` option.
When embedded, the SBOM size is displayed.
The number of components is always displayed.
The SBOM feature can be disabled with `--enable-sbom=false`.

Unassociated types are displayed when certain types (such as classes, interfaces, or annotations) cannot be linked to an SBOM component.
If these types contain vulnerabilities, SBOM scanning will not detect them.
To fix this, ensure that proper GAV coordinates (Group ID, Artifact ID, and Version) are defined in the project POM's properties or in _MANIFEST.MF_ using standard formats.

Hashes are computed for JAR inputs and GraalVM internal components if the `hashes` option is used.
Hashes are not computed for directories.
If `hashes` is enabled and hashes cannot be computed, the number of components without hashes is displayed.
To list these components, use:
```bash
jq '.components[] | select(.hashes == null)' /path/to/app.sbom.json
```

Use the [build report](BuildReport.md) to view included components, their dependencies, and any unassociated types.
For more information, see [Software Bill of Materials (SBOM) in Native Image](../../security/SBOM.md).

#### <a name="glossary-obfuscation"></a>Advanced Obfuscation
This section indicates whether advanced obfuscation was applied.
Advanced obfuscation is applied to your application code and third-party dependencies, but not to the JDK or [Substrate VM](https://github.com/oracle/graal/tree/master/substratevm) code.

**Obfuscated elements include:**
* Module, package, and class names
* Method and source file names (as seen in stack traces)
* Field names (as seen in heap dumps)

**Elements that are _not_ obfuscated:**
* Names affected by registrations in reachability metadata
* Names in preserved code (via `-H:Preserve`)
* Module and package names containing a class that loads a resource
* Names of annotations, lambdas, and proxies

To export a mapping from original to obfuscated names, use `-H:AdvancedObfuscation=export-mapping`.
Use the mapping file and the `native-image-configure deobfuscate` command to deobfuscate stack traces.
See the [build report](BuildReport.md) for summary statistics, such as the percentage of class and method names that were obfuscated.

For more information, see [Advanced Obfuscation in Native Image](../../security/Obfuscation.md).

> Native Image obfuscates binaries by removing class files, applying aggressive optimizations, and eliminating dead code. The advanced obfuscation feature also obfuscates symbol names.

#### <a name="glossary-backwards-edge-cfi"></a>Backwards-Edge Control-Flow Integrity (CFI)
Control-Flow Integrity (CFI) can be enforced with the experimental `-H:CFI=HW` option.
This feature is currently only available for code compiled by Graal for Linux AArch64 and leverages pointer authentication codes (PAC) to ensure integrity of a function's return address.

#### <a name="glossary-sw-cfi"></a>Software Control-Flow Integrity (CFI)
Control-Flow Integrity (CFI) can be enforced in software with the experimental `-H:CFI=SW_NONATIVE` option.
This feature is currently only available for code compiled by Graal for Linux AMD64 and validates targets of indirect branches and method returns.

## Recommendations

The build output may contain one or more of the following recommendations that help you get the best out of Native Image.

#### <a name="recommendation-futr"></a>`FUTR`: Use the Correct Semantics and Prepare for Future Releases

Use `--future-defaults=all` to enable all features that are planned to be default in a future GraalVM release.
This option is unlikely to affect your program's behavior but guarantees that it adheres to the correct execution semantics.
Additionally, it safeguards against unexpected changes in future GraalVM updates.

#### <a name="recommendation-awt"></a>`AWT`: Missing Reachability Metadata for Abstract Window Toolkit

The Native Image analysis has included classes from the [`java.awt` package](https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/java/awt/package-summary.html) but could not find any reachability metadata for it.
Use the [Tracing Agent](AutomaticMetadataCollection.md) to collect such metadata for your application.
Otherwise, your application is unlikely to work properly.
If your application is not a desktop application (for example using Swing or AWT directly), you may want to re-evaluate whether the dependency on AWT is actually needed.

#### <a name="recommendation-home"></a>`HOME`: Set `java.home` When Running the Binary

The Native Image analysis has detected the usage of `System.getProperty("java.home")`.
To ensure it returns a valid value, set `java.home` by passing the `-Djava.home=<path>` option to the binary. If not set, `System.getProperty("java.home")` will return `null`.

#### <a name="recommendation-cpu"></a>`CPU`: Enable More CPU Features for Improved Performance

The Native Image build process has determined that your CPU supports more features, such as AES or LSE, than currently enabled.
If you deploy your application on the same machine or a similar machine with support for the same CPU features, consider using `-march=native` at build time.
This option allows the Graal compiler to use all CPU features available, which in turn can significantly improve the performance of your application.
Use `-march=list` to list all available machine types that can be targeted explicitly.

#### <a name="recommendation-g1gc"></a>`G1GC`: Use G1 Garbage Collector for Improved Latency and Throughput

The G1 garbage collector is available for your platform.
Consider enabling it using `--gc=G1` at build time to improve the latency and throughput of your application.
For more information see the [docs on Memory Management](MemoryManagement.md).
For best peak performance, also consider using [Profile-Guided Optimization](#recommendation-pgo).

#### <a name="recommendation-heap"></a>`HEAP`: Specify a Maximum Heap Size

Please refer to [Maximum Heap Size](#glossary-gc-max-heap-size).

#### <a name="recommendation-pgo"></a>`PGO`: Use Profile-Guided Optimization for Improved Throughput

Consider using Profile-Guided Optimization (PGO) to optimize your application for improved throughput.
These optimizations allow the Graal compiler to leverage profiling information, similar to when it is running as a JIT compiler, when AOT-compiling your application.
For this, perform the following steps:

1. Build your application with `--pgo-instrument`.
2. Run your instrumented application with a representative workload to generate profiling information in the form of an `.iprof` file.
3. Re-build your application and pass in the profiling information with `--pgo=<your>.iprof` to generate an optimized version of your application.

Relevant guide: [Optimize a Native Executable with Profile-Guided Optimization](guides/optimize-native-executable-with-pgo.md).

For best peak performance, also consider using the [G1 garbage collector](#recommendation-g1gc).

#### <a name="recommendation-qbm"></a>`QBM`: Use Quick Build Mode for Faster Builds

Consider using the quick build mode (`-Ob`) to speed up your builds during development.
More precisely, this mode reduces the number of optimizations performed by the Graal compiler and thus reduces the overall time of the [compilation stage](#stage-compiling).
The quick build mode is not only useful for development, it can also cause the generated executable file to be smaller in size.
Note, however, that the overall peak throughput of the executable may be lower due to the reduced number of optimizations.

#### <a name="recommendation-init"></a>`INIT`: Use the Strict Image Heap Configuration

Start using `--strict-image-heap` to reduce the amount of configuration and prepare for future GraalVM releases where this will be the default.
This mode requires only the classes that are stored in the image heap to be marked with `--initialize-at-build-time`.
This effectively reduces the number of configuration entries necessary to achieve build-time initialization.
When adopting the new mode it is best to start introducing build-time initialization from scratch.
During this process, it is best to select individual classes (as opposed to whole packages) for build time initialization.
Also, before migrating to the new flag make sure to update all framework dependencies to the latest versions as they might need to migrate too.

> Note that `--strict-image-heap` is enabled by default in Native Image starting from GraalVM for JDK 22.

## Resource Usage Statistics

#### <a name="glossary-garbage-collections"></a>Garbage Collections
The total time spent in all garbage collectors, total GC time divided by the total process time as a percentage, and the total number of garbage collections.
A large number of collections or time spent in collectors usually indicates that the system is under memory pressure.
Increase the amount of available memory to reduce the time to build the native binary.

#### <a name="glossary-peak-rss"></a>Peak RSS
Peak [resident set size](https://en.wikipedia.org/wiki/Resident_set_size) as reported by the operating system.
This value indicates the maximum amount of memory consumed by the build process.
You may want to compare this value to the memory limit reported in the [build resources section](#glossary-build-resources).
If there is enough headroom and the [GC statistics](#glossary-garbage-collections) do not show any problems, the amount of total memory of the system can be reduced to a value closer to the peak RSS to lower operational costs.

#### <a name="glossary-cpu-load"></a>CPU load
The CPU time used by the process divided by the total process time.
Increase the number of CPU cores to reduce the time to build the native binary.

## <a name="glossary-build-artifacts"></a>Build Artifacts

The list of all build artifacts.
This includes the generated native binary, but it can also contain other artifacts such as additional libraries, C header files, or debug info.
Some of these artifacts must remain in the same location with the native binary as they are needed at run time.
For applications using AWT, for example, the build process will also output libraries from the JDK and shims to provide compatible AWT support.
These libraries need to be copied and distributed together with the native binary.
Use the `-H:+GenerateBuildArtifactsFile` option to instruct the builder to produce a machine-readable version of the build artifact list in JSON format.
Such a JSON file validates against the JSON schema defined in [`build-artifacts-schema-v0.9.0.json`](https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/assets/build-artifacts-schema-v0.9.0.json).
This schema also contains descriptions for each possible artifact type and explains whether they are needed at run time or not.

## Machine-Readable Build Output

The build output produced by the `native-image` builder is designed for humans, can evolve with new releases, and should thus not be parsed in any way by tools.
Instead, use the `-H:BuildOutputJSONFile=<file.json>` option to instruct the builder to produce machine-readable build output in JSON format that can be used, for example, for building monitoring tools.
Such a JSON file validates against the JSON schema defined in [`build-output-schema-v0.9.4.json`](https://github.com/oracle/graal/tree/master/docs/reference-manual/native-image/assets/build-output-schema-v0.9.4.json).
Note that a JSON file is produced if and only if a build succeeds.

The following example illustrates how this could be used in a CI/CD build pipeline to check that the number of reachable methods does not exceed a certain threshold:

```bash
native-image -H:BuildOutputJSONFile=build.json HelloWorld
# ...
cat build.json | python3 -c "import json,sys;c = json.load(sys.stdin)['analysis_results']['methods']['reachable']; assert c < 12000, f'Too many reachable methods: {c}'"
Traceback (most recent call last):
  File "<string>", line 1, in <module>
AssertionError: Too many reachable methods: 12128
```

## Colorful Build Output

By default, the `native-image` builder colors the build output for better readability when it finds an appropriate terminal.
It also honors the <a href="https://no-color.org" target="_target">`NO_COLOR`</a>, `CI`, and `TERM` environment variables when checking for color support.
To explicitly control colorful output, set the `--color` option to `always`, `never`, or `auto` (default).

## Related Documentation

- [Build a Native Shared Library](guides/build-native-shared-library.md)
- [Build a Statically Linked or Mostly-Statically Linked Native Executable](guides/build-static-and-mostly-static-executable.md)
- [Feature](https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/hosted/Feature.html)
- [Interoperability with Native Code](InteropWithNativeCode.md)
- [Java Native Interface (JNI) in Native Image](JNI.md)
- [Memory Management](MemoryManagement.md)
- [Native Image Build Overview](BuildOverview.md)
- [Native Image Build Configuration](BuildConfiguration.md)
