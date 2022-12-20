---
layout: ni-docs
toc_group: build-overview
link_title: Build Options
permalink: /reference-manual/native-image/overview/BuildOptions/
redirect_from: /$version/reference-manual/native-image/Options/
---

#  Native Image Build Options

Depending on the GraalVM edition, the options to the `native-image` builder may differ.
The following options are supported across both GraalVM Community and Enterprise editions:

* `-cp, -classpath, --class-path <class search path of directories and zip/jar files>`: a `:` (`;` on Windows) separated list of directories, JAR archives, and ZIP archives to search for class files
* `-p <module path>, --module-path <module path>`: a `:` (`;` on Windows) separated list of directories; each directory is a directory of modules
* `--add-modules <module name>[,<module name>...]`: add root modules to resolve in addition to the initial module. `<module name>` can also be `ALL-DEFAULT`, `ALL-SYSTEM`, `ALL-MODULE-PATH`
* `-D<name>=<value>`: set a system property for the image builder
* `-J<flag>`: pass `<flag>` directly to the JVM running the `native-image` builder
* `-O<level>`: 0 for no optimizations, or 1 for basic optimizations (default)
* `--verbose`: enable verbose output
* `--version`: print the product version and exit
* `--help`: print this help message
* `--help-extra`: print help on non-standard options
* `--auto-fallback`: build a standalone native executable if possible
* `--configure-reflection-metadata`: enable runtime instantiation of reflection objects for non-invoked methods
* `--enable-all-security-services`: add all security service classes to a native executable
* `--enable-http`: enable HTTP support in a native executable
* `--enable-https`: enable HTTPS support in a native executable
* `--enable-monitoring`: enable monitoring features that allow the VM to be inspected at run time. Comma-separated list can contain `heapdump`, `jfr`, `jvmstat`, or `all` (defaults to `all` if no argument is provided). For example: `--enable-monitoring=heapdump,jvmstat`.
* `--enable-sbom`:  embed a Software Bill of Materials (SBOM) in the executable or shared library for passive inspection. Comma-separated list can contain `cyclonedx` or `strict` (defaults to `cyclonedx` if no argument is provided).  The optional `strict` flag aborts the build if any class cannot be matched to a library in the SBOM. For example: `--enable-sbom=cyclonedx,strict`. **GraalVM Enterprise only**
* `--enable-preview`: allow classes to depend on preview features of this release
* `--enable-url-protocols`: list comma-separated URL protocols to enable
* `--features`: a comma-separated list of fully qualified [Feature implementation classes](https://www.graalvm.org/sdk/javadoc/index.html?org/graalvm/nativeimage/hosted/Feature.html)
* `--force-fallback`: force building of a fallback native executable
* `--gc=<value>`: select Native Image garbage collector implementation. Allowed options for `<value>` are: `G1` for G1 garbage collector (**GraalVM Enterprise only**); `epsilon` for Epsilon garbage collector; `serial` for Serial garbage collector (default).
* `--initialize-at-build-time`: a comma-separated list of packages and classes (and implicitly all of their superclasses) that are initialized during image generation. An empty string designates all packages.
* `--initialize-at-run-time`: a comma-separated list of packages and classes (and implicitly all of their subclasses) that must be initialized at run time and not during image building. An empty string is currently not supported.
* `--install-exit-handlers`: provide `java.lang.Terminator` exit handlers
* `--libc`: select the `libc` native library implementation to use. Available implementations are `glibc` and `musl`.
* `--link-at-build-time`: require types to be fully defined at native executable build time. If used without arguments, all classes in scope of the option are required to be fully defined.
* `--link-at-build-time-paths`: require all types in given class or module-path entries to be fully defined at native executable build time
* `--list-cpu-features`: show CPU features specific to the target platform and exit
* `--native-compiler-options`: provide a custom C compiler option used to query code compilation
* `--native-compiler-path`: provide a custom path to the C compiler used to query code compilation and linking
* `--native-image-info`: show native toolchain information and native executable's build settings
* `--no-fallback`: build a standalone native executable or report a failure
* `--pgo`: a comma-separated list of files from which to read the data collected for profile-guided optimization of AOT compiled code (reads from  _default.iprof_ if nothing is specified). **GraalVM Enterprise only**
* `--pgo-instrument`: instrument AOT compiled code to collect data for profile-guided optimization into the _default.iprof_ file. **GraalVM Enterprise only**
* `--report-unsupported-elements-at-runtime`: report the usage of unsupported methods and fields at run time when they are accessed the first time, instead of an error during building a native executable
* `--shared`: build a shared library
* `--static`: build a statically-linked native executable (requires `libc` and `zlib` static libraries)
* `--target`: select the native image compilation target (in <OS>-<architecture> format). It defaults to the host's OS-architecture pair
* `--trace-class-initialization`: provide a comma-separated list of fully-qualified class names that a class initialization is traced for
* `--trace-object-instantiation`: provide a comma-separated list of fully-qualified class names that an object instantiation is traced for
* `-da`: disable assertions with specified granularity in the generated native executable. The  `-da[:[packagename]|:[classname]` or -`disableassertions[:[packagename]|:[classname]` variants are also supported.
* `-dsa`: disable assertions in all system classes
* `-ea`: enable assertions with specified granularity in a generated native executable. The  `-ea[:[packagename]|:[classname]` or -`enableassertions[:[packagename]|:[classname]` variants are also supported.
* `-esa`: enable assertions in all system classes
* `-g`: generate debugging information. The debug information produced on GraalVM Community will differ from that generated on GraalVM Enterprise.

### Macro Options
* `--language:nfi`: make the Truffle Native Function Interface language available
* `--language:python`: make Python available as a language for the executable
* `--language:R`: make R available as a language for the executable
* `--language:regex`: make the Truffle Regular Expression engine available
* `--language:wasm`: make WebAssembly available as a language for the executable
* `--language:java`: make Java available as a language for the executable
* `--language:llvm`: make LLVM bitcode available as a language for the executable
* `--language:js`: make JavaScript available as a language for the executable
* `--language:nodejs`: make Node.js available for the executable
* `--language:ruby`: make Ruby available as a language for the executable
* `--tool:coverage`: add source code coverage support to the GraalVM supported language
* `--tool:insight`: add support for detailed access to program's runtime behavior, allowing users to inspect values and types at invocation or allocation sites
* `--tool:dap`: allow image to open a debugger port serving the Debug Adapter Protocol in IDEs like VS Code
* `--tool:chromeinspector`: add debugging support to a GraalVM supported language
* `--tool:insightheap`: snapshot a region of image heap during the execution
* `--tool:lsp`: add the Language Server Protocol support to later attach compatible debuggers to GraalVM in IDEs like VS Code
* `--tool:sandbox`: enables the Truffle sandbox resource limits. For more information, check the [dedicated documentation](../embedding/sandbox-options.md)
* `--tool:profiler`: add profiling support to a GraalVM supported language

The `--language:nodejs`, `--language:python`, `--language:ruby`, `--language:R`, `--language:wasm`, and `--language:llvm` polyglot macro options become available once the corresponding languages are added to the base GraalVM installation (see the [GraalVM Updater](../graalvm-updater.md) guide).

### Non-standard Options

Run `native-image --help-extra` for non-standard options help.

* `--expert-options`: list image build options for experts
* `--expert-options-all `: list all image build options for experts (use at your own risk). Options marked with _Extra help available_ contain help that can be shown with `--expert-options-detail`
* `--expert-options-detail`: display all available help for a comma-separated list of option names. Pass `*` to show extra help for all options that contain it
* `--configurations-path <search path of option-configuration directories>`: a separated list of directories to be treated as option-configuration directories
* `--debug-attach[=< port >]`: attach to debugger during image building (default port is 8000)
* `--diagnostics-mode`: enable logging of the build information to a diagnostics folder
* `--dry-run`: output the command line that would be used for building a native executable
* `-V<key>=<value>`:  provide values for placeholders in `native-image.properties` files
* `--add-exports`: value `<module>/<package>=<target-module>(,<target-module>)*` updates `<module>` to export `<package>` to `<target-module>`, regardless of module declaration. `<target-module>` can be `ALL-UNNAMED` to export to all unnamed modules
* `--add-opens`: value `<module>/<package>=<target-module>(,<target-module>)*` updates `<module>` to open `<package>` to `<target-module>`, regardless of module declaration
* `--add-reads`: value `<module>=<target-module>(,<target-module>)*` updates `<module>` to read `<target-module>`, regardless of module declaration. `<target-module>` can be `ALL-UNNAMED` to read all unnamed modules

Native Image options are also distinguished as hosted and runtime options.

### Further Reading

* [Native Image Hosted and Runtime Options](HostedvsRuntimeOptions.md) guide.
* [Build Configuration](BuildConfiguration.md#order-of-arguments-evaluation)