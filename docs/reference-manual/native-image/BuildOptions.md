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

Depending on the GraalVM version, the options to the `native-image` builder may differ.

Native Image options can also be categorized as **hosted** or **runtime** options.

* **Hosted options**: to configure the build process&mdash;for example, influence what is included in the native binary and how it is built.
These options use the prefix `-H:`.
* **Runtime options**: to provide the initial value(s) when building the native binary, using the prefix `-R:`. At runtime, the default prefix is `-XX:` (this is application-specific and not mandated by Native Image).

For more information describing how to define and use these options, read the [`com.oracle.svm.core.option`](https://github.com/oracle/graal/tree/master/substratevm/src/com.oracle.svm.core/src/com/oracle/svm/core/option) package documentation.

## Build Options

Run `native-image --help` for help on build options.

* `-cp, -classpath, --class-path <class search path of directories and ZIP/JAR files>`: a `:` (`;` on Windows) separated list of directories, JAR archives, and ZIP archives to search for class files
* `-p <module path>, --module-path <module path>`: a `:` (`;` on Windows) separated list of directories. Each directory is a directory of modules.
* `--add-modules <module name>[,<module name>...]`: add root modules to resolve in addition to the initial module. `<module name>` can also be `ALL-DEFAULT`, `ALL-SYSTEM`, `ALL-MODULE-PATH`.
* `-D<name>=<value>`: set a system property for image build time only
* `-J<flag>`: pass an option directly to the JVM running the `native-image` builder
* `--diagnostics-mode`: enable diagnostics output: class initialization, substitutions, etc.
* `--enable-preview`: allow classes to depend on preview features of this release
* `--verbose`: enable verbose output
* `--version`: print the product version and exit
* `--help`: print this help message
* `--help-extra`: print help on non-standard options
* `--auto-fallback`: build a standalone executable if possible
* `--color`: color build output (`always`, `never`, or `auto`)
* `--configure-reflection-metadata`: enable runtime instantiation of reflection objects for non-invoked methods
* `--emit`: emit additional data as a result of the build. Use `build-report` to emit a detailed Build Report, for example: `--emit build-report` or `--emit build-report=report.html`
* `--enable-all-security-services`: add all security service classes to the generated native executable
* `--enable-http`: enable HTTP support in a native executable
* `--enable-https`: enable HTTPS support in a native executable
* `--enable-monitoring`: enable monitoring features that allow the VM to be inspected at run time. A comma-separated list can contain `heapdump`, `jfr`, `jvmstat`, `jmxserver` (experimental), `jmxclient` (experimental), `threaddump`, or `all` (deprecated behavior: defaults to `all` if no argument is provided). For example: `--enable-monitoring=heapdump,jfr`.
* `--enable-native-access <module name>[,<module name>...]`: enable modules that are permitted to perform restricted native operations. `<module name>` can also be `ALL-UNNAMED`
* `--enable-sbom`: assemble a Software Bill of Materials (SBOM) for the executable or shared library based on the results from the static analysis. Comma-separated list can contain `embed` to store the SBOM in data sections of the binary, `export` to save the SBOM in the output directory, `classpath` to include the SBOM as a Java resource on the classpath at _META-INF/native-image/sbom.json_, `strict` to abort the build if any type (such as a class, interface, or annotation) cannot be matched to an SBOM component, `cyclonedx` (the only format currently supported), and `class-level` to include class-level metadata. Defaults to embedding an SBOM: `--enable-sbom=embed`. To disable the SBOM feature, use `--enable-sbom=false` on the command line.
* `--enable-url-protocols`: list comma-separated URL protocols to enable
* `--exact-reachability-metadata`: enables exact and user-friendly handling of reflection, resources, JNI, and serialization
* `--exact-reachability-metadata-path`: trigger exact handling of reflection, resources, JNI, and serialization from all types in the given class-path or module-path entries
* `--features`: a comma-separated list of fully qualified [Feature implementation classes](https://www.graalvm.org/sdk/javadoc/index.html?org/graalvm/nativeimage/hosted/Feature.html)
* `--force-fallback`: force building of a fallback native executable
* `--future-defaults`: enable options that are planned to become defaults in future releases. A comma-separated list can contain `all`, `run-time-initialized-jdk`, `none`.
* `--gc=<value>`: select a Native Image garbage collector implementation. Allowed options for `<value>` are: `G1` for G1 garbage collector (not available in GraalVM Community Edition); `epsilon` for Epsilon garbage collector; `serial` for Serial garbage collector (default).
* `--initialize-at-build-time`: a comma-separated list of packages and classes (and implicitly all of their superclasses) that are initialized during generation of a native executable. An empty string designates all packages.
* `--initialize-at-run-time`: a comma-separated list of packages and classes (and implicitly all of their subclasses) that must be initialized at run time and not during generation. An empty string is currently not supported.
* `--libc`: select the `libc` implementation to use. Available implementations are `glibc`, `musl`, `bionic`.
* `--link-at-build-time`: require types to be fully defined at native executable build time. If used without arguments, all classes in scope of the option are required to be fully defined.
* `--link-at-build-time-paths`: require all types in given class or module path entries to be fully defined at native executable build time
* `--list-cpu-features`: show CPU features specific to the target platform and exit
* `--list-modules`: list observable modules and exit
* `--native-compiler-options`: provide a custom C compiler option used for query code compilation
* `--native-compiler-path`: provide a custom path to the C compiler used to query code compilation and linking
* `--native-image-info`: show the native toolchain information and executable's build settings
* `--no-fallback`: build a standalone native executable or report a failure
* `--parallelism`: specify the maximum number of threads to use concurrently during native executable generation
* `--pgo`: provide a comma-separated list of files from which to read the data collected for Profile-guided optimization of AOT-compiled code (reads from  _default.iprof_ if nothing is specified). Each file must contain a single `PGOProfiles` object, serialized in JSON format, optionally compressed by gzip. (Not available in GraalVM Community Edition.)
* `--pgo-instrument`: instrument AOT-compiled code to collect data for Profile-guided optimization into the _default.iprof_ file. (Not available in GraalVM Community Edition.)
* `--pgo-sampling`: perform profiling by sampling the AOT compiled code to collect data for Profile-guided optimization. (Not available in GraalVM Community Edition.)
* `--shared`: build a shared library
* `--silent`: silence build output
* `--static`: build a statically-linked executable (requires `libc` and `zlib` static libraries)
* `--static-nolibc`: build statically linked executable with `libc` dynamically linked
* `--target`: select the compilation target for `native-image` (in the `<OS>-<architecture>` format). It defaults to host's OS-architecture pair.
* `--trace-object-instantiation`: provide a comma-separated list of fully-qualified class names that an object instantiation is traced for
* `-O<level>`: control code optimizations where available variants are: `b` - optimize for fastest build time, `s` - optimize for size, `0` - no optimizations, `1` - basic optimizations, `2` - aggressive optimizations, `3` - all optimizations for best performance (enabled automatically with Profile-Guided Optimization (PGO))
* `-da`, `-da[:[packagename]|:[classname]`, `disableassertions[:[packagename]|:[classname]`: disable assertions with specified granularity at run time
* `-dsa`, `-disablesystemassertions`: disable assertions in all system classes at run time
* `-ea`, `-ea[:[packagename]|:[classname]`, `enableassertions[:[packagename]|:[classname]`: enable assertions with specified granularity at run time
* `-esa`, `-enablesystemassertions`: enable assertions in all system classes at run time
* `-g`: generate debugging information
* `-march`: generate instructions for a specific machine type. Defaults to `x86-64-v3` on AMD64 and `armv8-a` on AArch64. Use `-march=compatibility` for best compatibility, or `-march=native` for best performance if a native executable is deployed on the same machine or on a machine with the same CPU features. To list all available machine types, use `-march=list`.
* `-o`: name of the output file to be generated

## Extra Build Options

Run `native-image --help-extra` for help on additional options.

* `--exclude-config`: exclude configuration for a space-separated pair of class path/module path pattern and resource pattern. For example: `--exclude-config foo.jar META-INF\/native-image\/.*.properties` ignores all properties files in _META-INF/native-image_ in all JAR files named _foo.jar_.
* `--expert-options`: list image build options for experts
* `--expert-options-all`: list all image build options for experts (use at your own risk). Options marked with _Extra help available_ contain help that can be shown with `--expert-options-detail`
* `--expert-options-detail`: display all available help for a comma-separated list of option names. Pass `*` to show extra help for all options that contain it.
* `--configurations-path <search path of option-configuration directories>`: a separated list of directories to be treated as option-configuration directories.
* `--debug-attach[=<port or host:port (* can be used as host meaning bind to all interfaces)>]`: attach to a debugger during native executable generation (default port is 8000)
* `--diagnostics-mode`: Enables logging of image-build information to a diagnostics directory.
* `--dry-run`: output the command line that would be used for building
* `--bundle-create[=new-bundle.nib]`: in addition to image building, create a native image bundle file _(*.nibfile)_ that allows rebuilding of that image again at a later point. If a bundle file gets passed, the bundle will be created with the given name. Otherwise, the bundle file name is derived from the image name. Note both bundle options can be extended with `dry-run` and `container`.
    - `dry-run`: only perform the bundle operations without any actual native executable building
    - `container`: set up a container image and perform a native executable generation from inside that container. Requires Podman or rootless Docker to be installed. If available, Podman is preferred and rootless Docker is the fallback. Specifying one or the other as `=<container-tool>` forces the use of a specific tool.
    - `dockerfile=<Dockerfile>`: use a user provided `Dockerfile` instead of the default based on [Oracle Linux 8 base images for GraalVM](https://github.com/graalvm/container)
* `--bundle-apply=some-bundle.nib[,dry-run][,container[=<container-tool>][,dockerfile=<Dockerfile>]]`: an image will be built from the given bundle file with the exact same arguments and files that have been passed to Native Image originally to create the bundle. Note that if an extra `--bundle-create` gets passed after `--bundle-apply`, a new bundle will be written based on the given bundle arguments plus any additional arguments that haven been passed afterwards. For example: `native-image --bundle-apply=app.nib --bundle-create=app_dbg.nib -g` creates a new bundle _app_dbg.nib_ based on the given _app.nib_ bundle. Both bundles are the same except the new one also uses the `-g` option.
* `-E<env-var-key>[=<env-var-value>]`: allow Native Image to access the given environment variable during native executable generation. If the optional `<env-var-value>` is not given, the value of the environment variable will be taken from the environment Native Image was invoked from.
* `-V<key>=<value>`:  provide values for placeholders in the _native-image.properties_ files
* `--add-exports`: value `<module>/<package>=<target-module>(,<target-module>)` updates `<module>` to export `<package>` to `<target-module>`, regardless of module declaration. `<target-module>` can be `ALL-UNNAMED` to export to all unnamed modules
* `--add-opens`: value `<module>/<package>=<target-module>(,<target-module>)` updates `<module>` to open `<package>` to `<target-module>`, regardless of module declaration
* `--add-reads`: value `<module>=<target-module>(,<target-module>)` updates `<module>` to read `<target-module>`, regardless of module declaration. `<target-module>` can be `ALL-UNNAMED` to read all unnamed modules

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

GraalVM for JDK 25 introduces the `-H:Preserve` option. This lets you instruct the `native-image` tool to keep entire packages, modules, or all classes on the classpath in the native executable, even when static analysis cannot discover them.

You can use `-H:Preserve` in the following ways:

* `-H:Preserve=all`: preserves all elements from the JDK and from the classpath
* `-H:Preserve=module=<module>`: preserves all elements from a given module
* `-H:Preserve=module=ALL-UNNAMED`: preserves all elements from the classpath (provided with `-cp`).
* `-H:Preserve=package=<package>`: preserves all elements from a given package
* `-H:Preserve=path=<cp-entry>`: preserves all elements from a given class-path entry
* You can combine any of the previous uses by separating them with a comma (`,`). For example: `-H:Preserve=path=<cp-entry>,module=<module>,module=<module2>,package=<package>`

You must explicitly configure multi-interface proxy classes, arrays of dimension 3 and higher, and _.class_ files as resources in the native image. Tooling-related Java modules are not included by default with `-H:Preserve=all` and must be added with `-H:Preserve=module=<module>` if needed.

If you get errors related to `--initialize-at-build-time`, follow the suggestions in the error messages.

For a practical demonstration, see the [preserve-package demo](https://github.com/graalvm/graalvm-demos/tree/master/native-image/preserve-package).

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