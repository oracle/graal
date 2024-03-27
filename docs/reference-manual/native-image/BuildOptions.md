---
layout: docs
toc_group: build-overview
link_title: Command-line Options
permalink: /reference-manual/native-image/overview/Options/
redirect_from:
  - /reference-manual/native-image/overview/BuildOptions/
  - /reference-manual/native-image/Options/
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

* `-cp, -classpath, --class-path <class search path of directories and zip/jar files>`: a `:` (`;` on Windows) separated list of directories, JAR archives, and ZIP archives to search for class files
* `-p <module path>, --module-path <module path>`: a `:` (`;` on Windows) separated list of directories. Each directory is a directory of modules.
* `--add-modules <module name>[,<module name>...]`: add root modules to resolve in addition to the initial module. `<module name>` can also be `ALL-DEFAULT`, `ALL-SYSTEM`, `ALL-MODULE-PATH`.
* `-D<name>=<value>`: set a system property 
* `-J<flag>`: pass an option directly to the JVM running the `native-image` builder
* `--diagnostics-mode`: enable diagnostics output: class initialization, substitutions, etc.
* `--enable-preview`: allow classes to depend on preview features of this release
* `--enable-native-access <module name>[,<module name>...]`: enable modules that are permitted to perform restricted native operations. `<module name>` can also be `ALL-UNNAMED`
* `--verbose`: enable verbose output
* `--version`: print the product version and exit
* `--help`: print this help message
* `--help-extra`: print help on non-standard options
* `--auto-fallback`: build a standalone executable if possible
* `--color`: color build output (`always`, `never`, or `auto`)
* `--configure-reflection-metadata`: enable runtime instantiation of reflection objects for non-invoked methods
* `--enable-all-security-services`: add all security service classes to the generated native executable
* `--enable-http`: enable HTTP support in a native executable
* `--enable-https`: enable HTTPS support in a native executable
* `--enable-monitoring`: enable monitoring features that allow the VM to be inspected at run time. A comma-separated list can contain `heapdump`, `jfr`, `jvmstat`, `jmxserver` (experimental), `jmxclient` (experimental), `threaddump`, or `all` (deprecated behavior: defaults to `all` if no argument is provided). For example: `--enable-monitoring=heapdump,jfr`.
* `--enable-sbom`: embed a Software Bill of Materials (SBOM) in the executable or shared library for passive inspection. A comma-separated list can contain `cyclonedx`, `strict` (defaults to `cyclonedx` if no argument is provided), or `export` to save the SBOM to the native executable's output directory. The optional `strict` flag aborts the build if any class cannot be matched to a library in the SBOM. For example: `--enable-sbom=cyclonedx,strict`. (Not available in GraalVM Community Edition.)
* `--enable-url-protocols`: list comma-separated URL protocols to enable
* `--features`: a comma-separated list of fully qualified [Feature implementation classes](https://www.graalvm.org/sdk/javadoc/index.html?org/graalvm/nativeimage/hosted/Feature.html)
* `--force-fallback`: force building of a fallback native executable
* `--gc=<value>`: select a Native Image garbage collector implementation. Allowed options for `<value>` are: `G1` for G1 garbage collector (not available in GraalVM Community Edition); `epsilon` for Epsilon garbage collector; `serial` for Serial garbage collector (default).
* `--initialize-at-build-time`: a comma-separated list of packages and classes (and implicitly all of their superclasses) that are initialized during generation of a native executable. An empty string designates all packages.
* `--initialize-at-run-time`: a comma-separated list of packages and classes (and implicitly all of their subclasses) that must be initialized at run time and not during generation. An empty string is currently not supported.
* `--install-exit-handlers`: provide `java.lang.Terminator` exit handlers
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
* `--report-unsupported-elements-at-runtime`: report the usage of unsupported methods and fields at run time when they are accessed the first time, instead of an error during executable's building
* `--shared`: build a shared library
* `--silent`: silence build output
* `--static`: build a statically-linked executable (requires `libc` and `zlib` static libraries)
* `--static-nolibc`: build statically linked executable with libc dynamically linked
* `--target`: select the compilation target for `native-image` (in the `<OS>-<architecture>` format). It defaults to host's OS-architecture pair.
* `--trace-class-initialization`: provide a comma-separated list of fully-qualified class names that a class initialization is traced for
* `--trace-object-instantiation`: provide a comma-separated list of fully-qualified class names that an object instantiation is traced for
* `-O<level>`: control code optimizations where available variants are: `b` - optimize for fastest build time, `0` - no optimizations, `1` - basic optimizations, `2` - aggressive optimizations, `3` - all optimizations for best performance (enabled automatically with the Profile-guided optimization) 
* `-da`, `-da[:[packagename]|:[classname]`, `disableassertions[:[packagename]|:[classname]`: disable assertions with specified granularity at run time
* `-dsa`, `-disablesystemassertions`: disable assertions in all system classes at run time
* `-ea`, `-ea[:[packagename]|:[classname]`, `enableassertions[:[packagename]|:[classname]`: enable assertions with specified granularity at run time
* `-esa`, `-enablesystemassertions`: enable assertions in all system classes at run time
* `-g`: generate debugging information
* `-march`: generate instructions for a specific machine type. Defaults to `x86-64-v3` on AMD64 and `armv8-a` on AArch64. Use `-march=compatibility` for best compatibility, or `-march=native` for best performance if a native executable is deployed on the same machine or on a machine with the same CPU features. To list all available machine types, use `-march=list`.
* `-o`: name of the output file to be generated

## Extra Build Options

Run `native-image --help-extra` for help on additional options.

* `--exclude-config`: exclude configuration for a comma-separated pair of classpath/modulepath pattern and resource pattern. For example: `--exclude-config foo.jar,META-INF\/native-image\/.*.properties` ignores all properties files in _META-INF/native-image_ in all JARs named _foo.jar_.
* `--expert-options`: list image build options for experts
* `--expert-options-all`: list all image build options for experts (use at your own risk). Options marked with _Extra help available_ contain help that can be shown with `--expert-options-detail`
* `--expert-options-detail`: display all available help for a comma-separated list of option names. Pass `*` to show extra help for all options that contain it.
* `--configurations-path <search path of option-configuration directories>`: a separated list of directories to be treated as option-configuration directories.
* `--debug-attach[=<port or host:port (* can be used as host meaning bind to all interfaces)>]`: attach to a debugger during native executable generation (default port is 8000)
* `--diagnostics-mode`: Enables logging of image-build information to a diagnostics folder.
* `--dry-run`: output the command line that would be used for building
* `--bundle-create[=new-bundle.nib]`: in addition to image building, create a native image bundle file _(*.nibfile)_ that allows rebuilding of that image again at a later point. If a bundle file gets passed, the bundle will be created with the given name. Otherwise, the bundle file name is derived from the image name. Note both bundle options can be extended with `dry-run` and `container`.
    - `dry-run`: only perform the bundle operations without any actual native executable building
    - `container`: set up a container image and perform a native executable generation from inside that container. Requires Podman or rootless Docker to be installed. If available, Podman is preferred and rootless Docker is the fallback. Specifying one or the other as `=<container-tool>` forces the use of a specific tool.
    - `dockerfile=<Dockerfile>`: use a user provided `Dockerfile` instead of the default based on [Oracle Linux 8 base images for GraalVM](https://github.com/graalvm/container)
* `--bundle-apply=some-bundle.nib[,dry-run][,container[=<container-tool>][,dockerfile=<Dockerfile>]]`: an image will be built from the given bundle file with the exact same arguments and files that have been passed to Native Image originally to create the bundle. Note that if an extra `--bundle-create` gets passed after `--bundle-apply`, a new bundle will be written based on the given bundle args plus any additional arguments that haven been passed afterwards. For example: `native-image --bundle-apply=app.nib --bundle-create=app_dbg.nib -g` creates a new bundle _app_dbg.nib_ based on the given _app.nib_ bundle. Both bundles are the same except the new one also uses the `-g` option.
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
The JSON files validate against the JSON schema defined in [build-output-schema-v0.9.3.json](https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/assets/build-output-schema-v0.9.3.json).
A comprehensive report with additional information can be requested using the `-H:+BuildReport` option.

> Note: The `-H:+BuildReport` option is not available in GraalVM Community Edition.

### Graph Dumping

Native Image re-used the options for graph dumping, logging, counters, and everything else from the GraalVM debug environment.
These GraalVM options can be used both as **hosted options** (if you want to dump graphs of the `native-image` builder), and as **runtime** options (if you want to dump graphs during dynamic compilation at runtime).

The Graal compiler options that work as expected include `Dump`, `DumpOnError`, `Log`, `MethodFilter`, and the options to specify file names and ports for the dump handlers.
For example:
* `-H:Dump= -H:MethodFilter=ClassName.MethodName`: dump the compiler graphs of the `native-image` builder.
* `-XX:Dump= -XX:MethodFilter=ClassName.MethodName`: dump the compile graphs at runtime.

## Related Documentation

* [Build Configuration](BuildConfiguration.md#order-of-arguments-evaluation)
* [Build Overview](BuildOverview.md)