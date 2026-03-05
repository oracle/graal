# GraalVM Native Image — Build Options

## Build Options

- `-cp, -classpath, --class-path <class search path of directories and ZIP/JAR files>`  
  A `:` (`;` on Windows) separated list of directories, JAR archives, and ZIP archives to search for class files.

- `-p <module path>, --module-path <module path>`  
  A `:` (`;` on Windows) separated list of directories. Each directory is a directory of modules.

- `--add-modules <module name>[,<module name>...]`  
  Add root modules to resolve in addition to the initial module. `<module name>` can also be `ALL-DEFAULT`, `ALL-SYSTEM`, `ALL-MODULE-PATH`.

- `-D<name>=<value>`  
  Set a system property for image build time only.

- `-J<flag>`  
  Pass an option directly to the JVM running the `native-image` builder.

- `--diagnostics-mode`  
  Enable diagnostics output: class initialization, substitutions, etc.

- `--enable-preview`  
  Allow classes to depend on preview features of this release.

- `--verbose`  
  Enable verbose output.

- `--version`  
  Print the product version and exit.

- `--help`  
  Print this help message.

- `--help-extra`  
  Print help on non-standard options.

- `--color`  
  Color build output (`always`, `never`, or `auto`).

- `--configure-reflection-metadata`  
  Enable runtime instantiation of reflection objects for non-invoked methods.

- `--emit`  
  Emit additional data as a result of the build. Use `build-report` to emit a detailed Build Report, for example:  
  `--emit build-report` or `--emit build-report=report.html`

- `--enable-all-security-services`  
  Add all security service classes to the generated native executable.

- `--enable-http`  
  Enable HTTP support in a native executable.

- `--enable-https`  
  Enable HTTPS support in a native executable.

- `--enable-monitoring`  
  Enable monitoring features that allow the VM to be inspected at run time. A comma-separated list can contain:  
  `heapdump`, `jfr`, `jvmstat`, `jmxserver` (experimental), `jmxclient` (experimental), `threaddump`, or `all` (deprecated behavior: defaults to `all` if no argument is provided).  
  Example: `--enable-monitoring=heapdump,jfr`.

- `--enable-native-access <module name>[,<module name>...]`  
  Enable modules that are permitted to perform restricted native operations. `<module name>` can also be `ALL-UNNAMED`.

- `--enable-sbom`  
  Assemble a Software Bill of Materials (SBOM) for the executable or shared library based on the results from the static analysis.  
  Comma-separated list can contain:
  - `embed` — store the SBOM in data sections of the binary
  - `export` — save the SBOM in the output directory
  - `classpath` — include the SBOM as a Java resource on the classpath at `META-INF/native-image/sbom.json`
  - `strict` — abort the build if any type (such as a class, interface, or annotation) cannot be matched to an SBOM component
  - `cyclonedx` — the only format currently supported
  - `class-level` — include class-level metadata  
  Defaults to embedding an SBOM: `--enable-sbom=embed`.  
  To disable the SBOM feature, use `--enable-sbom=false`.

- `--enable-url-protocols`  
  List comma-separated URL protocols to enable.

- `--exact-reachability-metadata`  
  Enables exact and user-friendly handling of reflection, resources, JNI, and serialization.

- `--exact-reachability-metadata-path`  
  Trigger exact handling of reflection, resources, JNI, and serialization from all types in the given class-path or module-path entries.

- `--features`  
  A comma-separated list of fully qualified Feature implementation classes.

- `--future-defaults`  
  Enable options that are planned to become defaults in future releases. A comma-separated list can contain:  
  `all`, `run-time-initialized-jdk`, `none`.

- `--gc=<value>`  
  Select a Native Image garbage collector implementation. Allowed options for `<value>` are:
  - `G1` — G1 garbage collector (not available in GraalVM Community Edition)
  - `epsilon` — Epsilon garbage collector
  - `serial` — Serial garbage collector (default)

- `--initialize-at-build-time`  
  A comma-separated list of packages and classes (and implicitly all of their superclasses) that are initialized during generation of a native executable.  
  An empty string designates all packages.

- `--initialize-at-run-time`  
  A comma-separated list of packages and classes (and implicitly all of their subclasses) that must be initialized at run time and not during generation.  
  An empty string is currently not supported.

- `--libc`  
  Select the `libc` implementation to use. Available implementations are: `glibc`, `musl`, `bionic`.

- `--link-at-build-time`  
  Require types to be fully defined at native executable build time. If used without arguments, all classes in scope of the option are required to be fully defined.

- `--link-at-build-time-paths`  
  Require all types in given class or module path entries to be fully defined at native executable build time.

- `--list-cpu-features`  
  Show CPU features specific to the target platform and exit.

- `--list-modules`  
  List observable modules and exit.

- `--native-compiler-options`  
  Provide a custom C compiler option used for query code compilation.

- `--native-compiler-path`  
  Provide a custom path to the C compiler used to query code compilation and linking.

- `--native-image-info`  
  Show the native toolchain information and executable’s build settings.

- `--parallelism`  
  Specify the maximum number of threads to use concurrently during native executable generation.

- `--pgo`  
  Provide a comma-separated list of files from which to read the data collected for Profile-guided optimization of AOT-compiled code (reads from `default.iprof` if nothing is specified).  
  Each file must contain a single `PGOProfiles` object, serialized in JSON format, optionally compressed by gzip.  
  (Not available in GraalVM Community Edition.)

- `--pgo-instrument`  
  Instrument AOT-compiled code to collect data for Profile-guided optimization into the `default.iprof` file.  
  (Not available in GraalVM Community Edition.)

- `--pgo-sampling`  
  Perform profiling by sampling the AOT compiled code to collect data for Profile-guided optimization.  
  (Not available in GraalVM Community Edition.)

- `--shared`  
  Build a shared library.

- `--silent`  
  Silence build output.

- `--static`  
  Build a statically-linked executable (requires `libc` and `zlib` static libraries).

- `--static-nolibc`  
  Build statically linked executable with `libc` dynamically linked.

- `--target`  
  Select the compilation target for `native-image` (in the `<OS>-<architecture>` format).  
  It defaults to host’s OS-architecture pair.

- `--trace-object-instantiation`  
  Provide a comma-separated list of fully-qualified class names that an object instantiation is traced for.

- `-O<level>`  
  Control code optimizations where available variants are:
  - `b` — optimize for fastest build time
  - `s` — optimize for size
  - `0` — no optimizations
  - `1` — basic optimizations
  - `2` — aggressive optimizations
  - `3` — all optimizations for best performance (enabled automatically with Profile-Guided Optimization (PGO))

- `-da`, `-da[:[packagename]|:[classname]`, `disableassertions[:[packagename]|:[classname]`  
  Disable assertions with specified granularity at run time.

- `-dsa`, `-disablesystemassertions`  
  Disable assertions in all system classes at run time.

- `-ea`, `-ea[:[packagename]|:[classname]`, `enableassertions[:[packagename]|:[classname]`  
  Enable assertions with specified granularity at run time.

- `-esa`, `-enablesystemassertions`  
  Enable assertions in all system classes at run time.

- `-g`  
  Generate debugging information.

- `-march`  
  Generate instructions for a specific machine type. Defaults to `x86-64-v3` on AMD64 and `armv8-a` on AArch64.  
  Use `-march=compatibility` for best compatibility, or `-march=native` for best performance if a native executable is deployed on the same machine or on a machine with the same CPU features.  
  To list all available machine types, use `-march=list`.

- `-o`  
  Name of the output file to be generated.
