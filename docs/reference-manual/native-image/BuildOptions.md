---
layout: docs
toc_group: build-overview
link_title: Command-line Options
permalink: /reference-manual/native-image/overview/Options/
redirect_from:
  - /reference-manual/native-image/overview/BuildOptions/
  - /reference-manual/native-image/Options/
  - /reference-manual/native-image/guides/use-system-properties/
---

# Command-line Options

Options to configure Native Image are provided in the following categories:

- Build options: run `native-image --help` for help on build options.
- Extra build options: run `native-image --help-extra` for help on extra build options.
- Expert build options: run `native-image --expert-options` for help on expert options.
- Table of available options: run `native-image --print-options` (table), `native-image --print-options=md` (Markdown), or `native-image --print-options=json` (machine-readable JSON).

Depending on the GraalVM version, the options to the `native-image` builder may differ.
Native Image options can also be categorized as **hosted** or **runtime** options:

- **Hosted options**: to configure the build process and set default values for run-time behavior. These options use the prefix `-H:`. For example, `-H:MaxHeapSize=2g` sets the default maximum heap size for the native executable.
- **Runtime options**: to provide explicit values when building the native binary, using the prefix `-R:`. At run time, the default prefix is `-XX:` (this is application-specific and not mandated by Native Image).

You can use `-H:` options at build time to configure both build-time behavior and run-time defaults. For most use cases, `-H:` options are sufficient and you typically do not need to distinguish between build-time and run-time configuration.

For more information describing how to define and use these options, read the [`com.oracle.svm.core.option`](https://github.com/oracle/graal/tree/master/substratevm/src/com.oracle.svm.core/src/com/oracle/svm/core/option) package documentation.

## Build Options

Run `native-image --print-options` to generate a table of the available options like this one below:
<!-- BEGIN: build-options-table -->
| Command | Type | Description | Default | Usage |
|---------|------|-------------|---------|-------|
| `--add-exports` | String | value <module>/<package>=<target-module>(,<target-module>)* updates <module> to export <package> to <target-module>, regardless of module declaration. <target-module> can be ALL-UNNAMED to export to all unnamed modules. | None | `--add-exports=add-exports` |
| `--add-opens` | String | value <module>/<package>=<target-module>(,<target-module>)* updates <module> to open <package> to <target-module>, regardless of module declaration. | None | `--add-opens=add-opens` |
| `--add-reads` | String | value <module>=<target-module>(,<target-module>)* updates <module> to read <target-module>, regardless of module declaration. <target-module> can be ALL-UNNAMED to read all unnamed modules. | None | `--add-reads=add-reads` |
| `--color` | String | color build output ('always', 'never', or 'auto') | None | `--color=color` |
| `--emit` | String | emit additional data as a result of the build. Use 'build-report' to emit a detailed Build Report, for example: '--emit build-report' or '--emit build-report=/tmp/report.html' | None | `--emit=emit` |
| `--enable-all-security-services` | String | add all security service classes to the generated image. | None | `--enable-all-security-services=enable-all-security-services` |
| `--enable-http` | String | enable http support in the generated image | http | `--enable-http=enable-http` |
| `--enable-https` | String | enable https support in the generated image | https | `--enable-https=enable-https` |
| `--enable-monitoring` | String | enable monitoring features that allow the VM to be inspected at run time. Comma-separated list can contain 'heapdump', 'jfr', 'jvmstat', 'jmxserver' (experimental), 'jmxclient' (experimental), 'threaddump', 'nmt' (experimental), 'jcmd' (experimental), or 'all' (deprecated behavior: defaults to 'all' if no argument is provided). For example: '--enable-monitoring=heapdump,jfr'. | <deprecated-default> | `--enable-monitoring=enable-monitoring` |
| `--enable-native-access` | String | a comma-separated list of modules that are permitted to perform restricted native operations. The module name can also be ALL-UNNAMED. | None | `--enable-native-access=enable-native-access` |
| `--enable-sbom` | String | assemble a Software Bill of Materials (SBOM) for the executable or shared library based on the results from the static analysis. Comma-separated list can contain 'embed' to store the SBOM in data sections of the binary, 'export' to save the SBOM in the output directory, 'classpath' to include the SBOM as a Java resource on the classpath at 'META-INF/native-image/sbom.json', 'hashes' to include component hashes, 'strict' to abort the build if any type (such as a class, interface, or annotation) cannot be matched to an SBOM component or if a component hash could not be created, 'cyclonedx' (the only format currently supported), and 'class-level' to include class-level metadata. Defaults to embedding an SBOM: '--enable-sbom=embed'. To disable the SBOM feature, use '--enable-sbom=false' on the command line. | embed | `--enable-sbom=--enable-sbom` |
| `--enable-url-protocols` | String | list of comma separated URL protocols to enable. | None | `--enable-url-protocols=enable-url-protocols` |
| `--exact-reachability-metadata` | String | enables exact and user-friendly handling of reflection, resources, JNI, and serialization. |  | `--exact-reachability-metadata=exact-reachability-metadata` |
| `--exact-reachability-metadata-path` | String | trigger exact handling of reflection, resources, JNI, and serialization from all types in the given class-path or module-path entries. | None | `--exact-reachability-metadata-path=exact-reachability-metadata-path` |
| `--features` | String | a comma-separated list of fully qualified Feature implementation classes | None | `--features=features` |
| `--future-defaults` | String | enable options that are planned to become defaults in future releases. Comma-separated list can contain 'all', 'none', 'run-time-initialize-jdk', 'class-for-name-respects-class-loader', 'run-time-initialize-file-system-providers', 'run-time-initialize-security-providers', 'run-time-initialize-resource-bundles'. The preferred usage is '--future-defaults=all'. | <default-value> | `--future-defaults=future-defaults` |
| `--initialize-at-build-time` | String | a comma-separated list of packages and classes (and implicitly all of their superclasses) that are initialized during image generation. An empty string designates all packages. |  | `--initialize-at-build-time=initialize-at-build-time` |
| `--initialize-at-run-time` | String | a comma-separated list of packages and classes (and implicitly all of their subclasses) that must be initialized at runtime and not during image building. An empty string is currently not supported. |  | `--initialize-at-run-time=initialize-at-run-time` |
| `--libc` | String | selects the libc implementation to use. Available implementations: glibc, musl, bionic | None | `--libc=libc` |
| `--link-at-build-time` | String | require types to be fully defined at image build-time. If used without args, all classes in scope of the option are required to be fully defined. |  | `--link-at-build-time=link-at-build-time` |
| `--link-at-build-time-paths` | String | require all types in given class or module-path entries to be fully defined at image build-time. | None | `--link-at-build-time-paths=link-at-build-time-paths` |
| `--list-cpu-features` | String | show CPU features specific to the target platform and exit. | None | `--list-cpu-features=list-cpu-features` |
| `--list-modules` | String | list observable modules and exit. | None | `--list-modules=list-modules` |
| `--native-compiler-options` | String | provide custom C compiler option used for query code compilation. | None | `--native-compiler-options=native-compiler-options` |
| `--native-compiler-path` | String | provide custom path to C compiler used for query code compilation and linking. | None | `--native-compiler-path=native-compiler-path` |
| `--native-image-info` | String | show native-toolchain information and image-build settings | None | `--native-image-info=native-image-info` |
| `--parallelism` | String | the maximum number of threads the build process is allowed to use. | None | `--parallelism=parallelism` |
| `--pgo` | String | a comma-separated list of files from which to read the data collected for profile-guided optimization of AOT compiled code (reads from default.iprof if nothing is specified). Each file must contain a single PGOProfiles object, serialized in JSON format, optionally compressed by gzip. | default.iprof | `--pgo=pgo` |
| `--pgo-instrument` | String | instrument AOT compiled code to collect data for profile-guided optimization into default.iprof file | None | `--pgo-instrument=pgo-instrument` |
| `--pgo-sampling` | String | perform profiling by sampling the AOT compiled code to collect data for profile-guided optimization. | None | `--pgo-sampling=pgo-sampling` |
| `--shared` | String | build shared library | None | `--shared=shared` |
| `--silent` | String | silence build output | None | `--silent=silent` |
| `--static` | String | build statically linked executable (requires static libc and zlib) | None | `--static=static` |
| `--static-nolibc` | String | build statically linked executable with libc dynamically linked | None | `--static-nolibc=static-nolibc` |
| `--target` | String | selects native-image compilation target (in <OS>-<architecture> format). Defaults to host's OS-architecture pair. | None | `--target=target` |
| `--trace-object-instantiation` | String | comma-separated list of fully-qualified class names that object instantiation is traced for. | None | `--trace-object-instantiation=trace-object-instantiation` |
| `-O` | String | control code optimizations: b - optimize for fastest build time, s - optimize for size, 0 - no optimizations, 1 - basic optimizations, 2 - advanced optimizations, 3 - all optimizations for best performance. | None | `-O=-O` |
| `-Werror` | String | treat warnings as errors and terminate build. | all | `-Werror=-Werror` |
| `-da` | String | also -da[:[packagename]...\\|:classname] or -disableassertions[:[packagename]...\\|:classname]. Disable assertions with specified granularity at run time. |  | `-da=-da` |
| `-dsa` | String | also -disablesystemassertions. Disables assertions in all system classes at run time. | None | `-dsa=-dsa` |
| `-ea` | String | also -ea[:[packagename]...\\|:classname] or -enableassertions[:[packagename]...\\|:classname]. Enable assertions with specified granularity at run time. |  | `-ea=-ea` |
| `-esa` | String | also -enablesystemassertions. Enables assertions in all system classes at run time. | None | `-esa=-esa` |
| `-g` | String | generate debugging information | 2 | `-g=-g` |
| `-march` | String | generate instructions for a specific machine type. Defaults to 'x86-64-v3' on AMD64 and 'armv8.1-a' on AArch64. Use -march=compatibility for best compatibility, or -march=native for best performance if the native executable is deployed on the same machine or on a machine with the same CPU features. To list all available machine types, use -march=list. | None | `-march=-march` |
| `-o` | String | name of the output file to be generated | None | `-o=-o` |
| `--gc` | Enum | select native-image garbage collector implementation. Allowed values: 'epsilon', 'serial', 'G1'. | serial | `--gc=<value>` |
| `--add-modules` | String | root modules to resolve in addition to the initial module. <module name> can also be ALL-DEFAULT, ALL-SYSTEM, ALL-MODULE-PATH. |  | `--add-modules <module name>[,<module name>...]` |
| `--bundle-apply` | String | build an image from the given bundle file using the original arguments and files. If --bundle-create is passed after --bundle-apply, a new bundle is written with the applied plus additional arguments. |  | `--bundle-apply=some-bundle.nib[,dry-run][,container[=<container-tool>][,dockerfile=<Dockerfile>]]` |
| `--bundle-create` | String | in addition to image building, create a Native Image bundle file (*.nib file) that allows rebuilding of that image again at a later point. If a bundle-file gets passed, the bundle will be created with the given name; otherwise, the bundle-file name is derived from the image name. Bundle options can be extended with ',dry-run' and ',container'; 'dockerfile=<Dockerfile>' uses a user-provided Dockerfile. |  | `--bundle-create[=new-bundle.nib][,dry-run][,container[=<container-tool>][,dockerfile=<Dockerfile>]]` |
| `--class-path` | Path | A : separated list of directories, JAR archives, and ZIP archives to search for class files. |  | `--class-path <class search path of directories and zip/jar files>` |
| `--configurations-path` | Path | A : separated list of directories to be treated as option-configuration directories. |  | `--configurations-path <search path of option-configuration directories>` |
| `--debug-attach` | String | attach to debugger during image building (default port is 8000) |  | `--debug-attach[=<port or host:port (* can be used as host meaning bind to all interfaces)>]` |
| `--diagnostics-mode` | Boolean | Enables logging of image-build information to a diagnostics folder. |  | `--diagnostics-mode` |
| `--dry-run` | Boolean | output the command line that would be used for building |  | `--dry-run` |
| `--enable-preview` | Boolean | allow classes to depend on preview features of this release |  | `--enable-preview` |
| `--exclude-config` | String | exclude configuration for a space-separated pair of classpath/modulepath pattern and resource pattern. For example: '--exclude-config foo.jar META-INF\\/native-image\\/.*.properties' ignores all .properties files in 'META-INF/native-image' in all JARs named 'foo.jar'. |  | `--exclude-config` |
| `--expert-options` | Boolean | lists image build options for experts |  | `--expert-options` |
| `--expert-options-all` | Boolean | lists all image build options for experts (use at your own risk). Options marked with [Extra help available] contain help that can be shown with --expert-options-detail. |  | `--expert-options-all` |
| `--expert-options-detail` | String | displays all available help for a comma-separated list of option names. Pass * to show extra help for all options that contain it. |  | `--expert-options-detail` |
| `--help` | Boolean | print this help message |  | `--help` |
| `--help-extra` | Boolean | print help on non-standard options |  | `--help-extra` |
| `--module-path` | Path | A : separated list of directories, each directory is a directory of modules. |  | `--module-path <module path>...` |
| `--print-options` | String | print comprehensive options table. Available formats: 'table' (default), 'markdown' or 'md', and 'json'. This eliminates duplication with manual documentation tables. |  | `--print-options[=<format>]` |
| `--verbose` | Boolean | enable verbose output |  | `--verbose` |
| `--version` | Boolean | print product version and exit |  | `--version` |
| `-D` | String | set a system property for image build time only |  | `-D<name>=<value>` |
| `-E` | String | allow native-image to access the given environment variable during image build. If <env-var-value> is omitted, the value is taken from the environment native-image was invoked from. |  | `-E<env-var-key>[=<env-var-value>]` |
| `-J` | String | pass <flag> directly to the JVM running the image generator |  | `-J<flag>` |
| `-V` | String | provide values for placeholders in native-image.properties files |  | `-V<key>=<value>` |
| `-classpath` | Path | class search path of directories and zip/jar files |  | `-classpath <class search path of directories and zip/jar files>` |
| `-cp` | Path | class search path of directories and zip/jar files |  | `-cp <class search path of directories and zip/jar files>` |
| `-p` | Path | module path |  | `-p <module path>` |
| `@argument` | String | one or more argument files containing options |  | `@argument files` |
<!-- END: build-options-table -->

## List of Useful Options

There are some expert level options that a user may find useful or needed. For example, the option to dump graphs of the `native-image` builder, or to print various statistics during the build process.

### Build Output and Build Report

Native Image provides an informative [build output](BuildOutput.md) including various statistics during the build process.
The build output in a JSON-based, machine-readable format can be requested using the `-H:BuildOutputJSONFile` option, and later processed by a monitoring tool.
The JSON files validate against the JSON schema defined in [build-output-schema-v0.9.4.json](https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/assets/build-output-schema-v0.9.4.json).
A comprehensive report with additional information can be requested using the `--emit build-report` option.

> Note: The `--emit build-report` option is not available in GraalVM Community Edition.

### Graph Dumping

Native Image re-used the options for graph dumping, logging, counters, and everything else from the GraalVM debug environment.
These GraalVM options can be used both as **hosted options** (if you want to dump graphs of the `native-image` builder), and as **runtime** options (if you want to dump graphs during dynamic compilation at runtime).

The Graal compiler options that work as expected include `Dump`, `DumpOnError`, `Log`, `MethodFilter`, and the options to specify file names and ports for the dump handlers.
For example:
* `-H:Dump= -H:MethodFilter=ClassName.MethodName`: dump the compiler graphs of the `native-image` builder.
* `-XX:Dump= -XX:MethodFilter=ClassName.MethodName`: dump the compile graphs at runtime.

### Preserving Packages, Modules, or Classes

GraalVM 25 introduces the `-H:Preserve` option. This lets you instruct the `native-image` tool to keep entire packages, modules, or all classes on the classpath in the native executable, even when static analysis cannot discover them.

You can use `-H:Preserve` in the following ways:

* `-H:Preserve=all`: preserves all elements from the entire JDK and classpath. This creates larger images but ensures all code is included, which can help resolve missing metadata issues.
* `-H:Preserve=module=<module>`: preserves all elements from a given module.
* `-H:Preserve=module=ALL-UNNAMED`: preserves all elements from the classpath (provided with `-cp`).
* `-H:Preserve=package=<package>`: preserves all elements from a given package. You can use `*` to include all subpackages, for example: `-H:Preserve=package=com.my.pkg.*,package=com.another.pkg.*`. Note that only the `*` wildcard is supported; other regex patterns are not allowed.
* `-H:Preserve=path=<cp-entry>`: preserves all elements from a given class-path entry
* You can combine any of the previous uses by separating them with a comma (`,`). For example: `-H:Preserve=path=<cp-entry>,module=<module>,module=<module2>,package=<package>`

You must explicitly configure multi-interface proxy classes, arrays of dimension 3 and higher, and _.class_ files as resources in the native image. Tooling-related Java modules are not included by default with `-H:Preserve=all` and must be added with `-H:Preserve=module=<module>` if needed.

If you get errors related to `--initialize-at-build-time`, follow the suggestions in the error messages.

> **Note:** Using `-H:Preserve=all` requires significant memory and will result in much larger native images. Use the `-Os` flag to reduce image size. For more information, see [Optimizations and Performance](OptimizationsAndPerformance.md).

For a practical demonstration, see the [preserve-package demo](https://github.com/graalvm/graalvm-demos/tree/master/native-image/preserve-package).

You can combine `-H:Preserve` with [metadata tracing from a native image](AutomaticMetadataCollection.md#dynamic-metadata-collection-from-a-native-image) to collect reachability metadata from a representative run:
```shell
native-image -H:+UnlockExperimentalVMOptions -H:+MetadataTracingSupport -H:-UnlockExperimentalVMOptions -H:Preserve=package=com.example.library.* ...
./application -XX:TraceMetadata=path=metadata-output -XX:TraceMetadataConditionPackages=com.example.application
```

#### Memory Requirements

Native Image compilation is memory-intensive, particularly when building large projects or when using `-H:Preserve=all` or `--pgo-instrument`.

If you encounter `OutOfMemoryError: Java heap space` you can:

* use the `-Os` flag to reduce image size. For more information, see [Optimizations and Performance](OptimizationsAndPerformance.md)
* use more specific preservation options like `-H:Preserve=package=<package>` instead of `-H:Preserve=all`
* use more RAM by increasing the heap size with `-J-Xmx<n>g` where `<n>` varies based on your machine's available memory and build requirements

## System Properties

You can define system properties at image build time using the `-D<system.property>=<value>` option syntax.
It sets a system property for the `native-image` tool, but the property will not be included in the generated executable.
However, JDK system properties are included in generated executables and are visible at runtime.

For example:
* `-D<system.property>=<value>` will only be visible at build time. If this system property is accessed in the native executable, it will return `null`.
* `-Djava.version=25` will be visible at both build time and in the native executable because the value is copied into the binary by default.

The following system properties are automatically copied into the generated executable:

| Name                          | Description                                                       |
|-------------------------------|-------------------------------------------------------------------|
| file.separator                | File separator                                                    |
| file.encoding                 | Character encoding for the default locale                         |
| java.version                  | Java Runtime Environment version                                  |
| java.version.date             | General-availability (GA) date of the release                     |
| java.class.version            | Java class format version number                                  |
| java.runtime.version          | Java Runtime Environment version                                  |
| java.specification.name       | Java Runtime Environment specification name                       |
| java.specification.vendor     | Java Runtime Environment specification vendor                     |
| java.specification.version    | Java Virtual Machine specification version                        |
| java.vm.specification.name    | Java Virtual Machine specification name                           |
| java.vm.specification.vendor  | Java Virtual Machine implementation vendor                        |
| java.vm.specification.version | Java Virtual Machine specification version                        |
| line.separator                | Line separator                                                    |
| native.encoding               | Specifies the host environment's character encoding               |
| org.graalvm.nativeimage.kind  | Specifies if the image is built as a shared library or executable |
| path.separator                | Path separator                                                    |
| stdin.encoding                | Specifies the encoding for `System.in`                            |
| stdout.encoding               | Specifies the encoding for `System.out` and `System.err`          |
| sun.jnu.encoding              | Specifies encoding when parsing values passed via the commandline |

## Related Documentation

* [Build Configuration](BuildConfiguration.md#order-of-arguments-evaluation)
* [Build Overview](BuildOverview.md)
