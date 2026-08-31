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

Run `native-image --print-options` to generate a table of the available options like this one below.

The `--enable-http`, `--enable-https`, and `--enable-url-protocols` options are deprecated.
Use reachability metadata instead.
These deprecated URL protocol options are omitted from the generated table; see [URL Protocols in Native Image](URLProtocols.md).

<!-- BEGIN: build-options-table -->

<table>
  <thead>
    <tr>
      <th>Command</th>
      <th>Type</th>
      <th>Description</th>
      <th>Default</th>
      <th>Usage</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>--add-exports</code></td>
      <td>String</td>
      <td>value &lt;module&gt;/&lt;package&gt;=&lt;target-module&gt;(,&lt;target-module&gt;)* updates &lt;module&gt; to export &lt;package&gt; to &lt;target-module&gt;, regardless of module declaration. &lt;target-module&gt; can be ALL-UNNAMED to export to all unnamed modules.</td>
      <td>None</td>
      <td><code>--add-exports=add-exports</code></td>
    </tr>
    <tr>
      <td><code>--add-opens</code></td>
      <td>String</td>
      <td>value &lt;module&gt;/&lt;package&gt;=&lt;target-module&gt;(,&lt;target-module&gt;)* updates &lt;module&gt; to open &lt;package&gt; to &lt;target-module&gt;, regardless of module declaration.</td>
      <td>None</td>
      <td><code>--add-opens=add-opens</code></td>
    </tr>
    <tr>
      <td><code>--add-reads</code></td>
      <td>String</td>
      <td>value &lt;module&gt;=&lt;target-module&gt;(,&lt;target-module&gt;)* updates &lt;module&gt; to read &lt;target-module&gt;, regardless of module declaration. &lt;target-module&gt; can be ALL-UNNAMED to read all unnamed modules.</td>
      <td>None</td>
      <td><code>--add-reads=add-reads</code></td>
    </tr>
    <tr>
      <td><code>--color</code></td>
      <td>String</td>
      <td>color build output ('always', 'never', or 'auto')</td>
      <td>None</td>
      <td><code>--color=color</code></td>
    </tr>
    <tr>
      <td><code>--emit</code></td>
      <td>String</td>
      <td>emit additional data as a result of the build. Use 'build-report' to emit a detailed Build Report, for example: '--emit build-report' or '--emit build-report=/tmp/report.html'</td>
      <td>None</td>
      <td><code>--emit=emit</code></td>
    </tr>
    <tr>
      <td><code>--enable-all-security-services</code></td>
      <td>String</td>
      <td>add all security service classes to the generated image.</td>
      <td>None</td>
      <td><code>--enable-all-security-services=enable-all-security-services</code></td>
    </tr>
    <tr>
      <td><code>--enable-monitoring</code></td>
      <td>String</td>
      <td>enable monitoring features that allow the VM to be inspected at run time. Comma-separated list can contain 'heapdump', 'jfr', 'jvmstat', 'jmxserver' (experimental), 'jmxclient' (experimental), 'threaddump', 'nmt' (experimental), 'jcmd' (experimental), or 'all' (deprecated behavior: defaults to 'all' if no argument is provided). For example: '--enable-monitoring=heapdump,jfr'.</td>
      <td>&lt;deprecated-default&gt;</td>
      <td><code>--enable-monitoring=enable-monitoring</code></td>
    </tr>
    <tr>
      <td><code>--enable-native-access</code></td>
      <td>String</td>
      <td>a comma-separated list of modules that are permitted to perform restricted native operations. The module name can also be ALL-UNNAMED.</td>
      <td>None</td>
      <td><code>--enable-native-access=enable-native-access</code></td>
    </tr>
    <tr>
      <td><code>--enable-sbom</code></td>
      <td>String</td>
      <td>assemble a Software Bill of Materials (SBOM) for the executable or shared library based on the results from the static analysis. Comma-separated list can contain 'embed' to store the SBOM in data sections of the binary, 'export' to save the SBOM in the output directory, 'classpath' to include the SBOM as a Java resource on the classpath at 'META-INF/native-image/sbom.json', 'hashes' to include component hashes, 'strict' to abort the build if any type (such as a class, interface, or annotation) cannot be matched to an SBOM component or if a component hash could not be created, 'cyclonedx' (the only format currently supported), and 'class-level' to include class-level metadata. Defaults to embedding an SBOM: '--enable-sbom=embed'. To disable the SBOM feature, use '--enable-sbom=false' on the command line.</td>
      <td>embed</td>
      <td><code>--enable-sbom=--enable-sbom</code></td>
    </tr>
    <tr>
      <td><code>--exact-reachability-metadata</code></td>
      <td>String</td>
      <td>enables exact and user-friendly handling of reflection, resources, JNI, and serialization.</td>
      <td></td>
      <td><code>--exact-reachability-metadata=exact-reachability-metadata</code></td>
    </tr>
    <tr>
      <td><code>--exact-reachability-metadata-path</code></td>
      <td>String</td>
      <td>trigger exact handling of reflection, resources, JNI, and serialization from all types in the given class-path or module-path entries.</td>
      <td>None</td>
      <td><code>--exact-reachability-metadata-path=exact-reachability-metadata-path</code></td>
    </tr>
    <tr>
      <td><code>--features</code></td>
      <td>String</td>
      <td>a comma-separated list of fully qualified Feature implementation classes</td>
      <td>None</td>
      <td><code>--features=features</code></td>
    </tr>
    <tr>
      <td><code>--future-defaults</code></td>
      <td>String</td>
      <td>enable options that are planned to become defaults in future releases. Comma-separated list can contain 'all', 'none', 'run-time-initialize-jdk', 'class-for-name-respects-class-loader', 'run-time-initialize-file-system-providers', 'run-time-initialize-security-providers', 'run-time-initialize-resource-bundles', 'explicit-feature-singleton-registration'. The preferred usage is '--future-defaults=all'.</td>
      <td>&lt;default-value&gt;</td>
      <td><code>--future-defaults=future-defaults</code></td>
    </tr>
    <tr>
      <td><code>--initialize-at-build-time</code></td>
      <td>String</td>
      <td>a comma-separated list of packages and classes (and implicitly all of their superclasses) that are initialized during image generation. An empty string designates all packages.</td>
      <td></td>
      <td><code>--initialize-at-build-time=initialize-at-build-time</code></td>
    </tr>
    <tr>
      <td><code>--initialize-at-run-time</code></td>
      <td>String</td>
      <td>a comma-separated list of packages and classes (and implicitly all of their subclasses) that must be initialized at runtime and not during image building. An empty string is currently not supported.</td>
      <td></td>
      <td><code>--initialize-at-run-time=initialize-at-run-time</code></td>
    </tr>
    <tr>
      <td><code>--libc</code></td>
      <td>String</td>
      <td>selects the libc implementation to use. Available implementations: glibc, musl, bionic</td>
      <td>None</td>
      <td><code>--libc=libc</code></td>
    </tr>
    <tr>
      <td><code>--link-at-build-time</code></td>
      <td>String</td>
      <td>require types to be fully defined at image build-time. If used without args, all classes in scope of the option are required to be fully defined.</td>
      <td></td>
      <td><code>--link-at-build-time=link-at-build-time</code></td>
    </tr>
    <tr>
      <td><code>--link-at-build-time-paths</code></td>
      <td>String</td>
      <td>require all types in given class or module-path entries to be fully defined at image build-time.</td>
      <td>None</td>
      <td><code>--link-at-build-time-paths=link-at-build-time-paths</code></td>
    </tr>
    <tr>
      <td><code>--list-cpu-features</code></td>
      <td>String</td>
      <td>show CPU features specific to the target platform and exit.</td>
      <td>None</td>
      <td><code>--list-cpu-features=list-cpu-features</code></td>
    </tr>
    <tr>
      <td><code>--list-modules</code></td>
      <td>String</td>
      <td>list observable modules and exit.</td>
      <td>None</td>
      <td><code>--list-modules=list-modules</code></td>
    </tr>
    <tr>
      <td><code>--native-compiler-options</code></td>
      <td>String</td>
      <td>provide custom C compiler option used for query code compilation.</td>
      <td>None</td>
      <td><code>--native-compiler-options=native-compiler-options</code></td>
    </tr>
    <tr>
      <td><code>--native-compiler-path</code></td>
      <td>String</td>
      <td>provide custom path to C compiler used for query code compilation and linking.</td>
      <td>None</td>
      <td><code>--native-compiler-path=native-compiler-path</code></td>
    </tr>
    <tr>
      <td><code>--native-image-info</code></td>
      <td>String</td>
      <td>show native-toolchain information and image-build settings</td>
      <td>None</td>
      <td><code>--native-image-info=native-image-info</code></td>
    </tr>
    <tr>
      <td><code>--parallelism</code></td>
      <td>String</td>
      <td>the maximum number of threads the build process is allowed to use.</td>
      <td>None</td>
      <td><code>--parallelism=parallelism</code></td>
    </tr>
    <tr>
      <td><code>--pgo</code></td>
      <td>String</td>
      <td>a comma-separated list of files from which to read the data collected for profile-guided optimization of AOT compiled code (reads from default.iprof if nothing is specified). Each file must contain a single PGOProfiles object, serialized in JSON format, optionally compressed by gzip.</td>
      <td>default.iprof</td>
      <td><code>--pgo=pgo</code></td>
    </tr>
    <tr>
      <td><code>--pgo-instrument</code></td>
      <td>String</td>
      <td>instrument AOT compiled code to collect data for profile-guided optimization into default.iprof file</td>
      <td>None</td>
      <td><code>--pgo-instrument=pgo-instrument</code></td>
    </tr>
    <tr>
      <td><code>--pgo-sampling</code></td>
      <td>String</td>
      <td>perform profiling by sampling the AOT compiled code to collect data for profile-guided optimization.</td>
      <td>None</td>
      <td><code>--pgo-sampling=pgo-sampling</code></td>
    </tr>
    <tr>
      <td><code>--shared</code></td>
      <td>String</td>
      <td>build shared library</td>
      <td>None</td>
      <td><code>--shared=shared</code></td>
    </tr>
    <tr>
      <td><code>--silent</code></td>
      <td>String</td>
      <td>silence build output</td>
      <td>None</td>
      <td><code>--silent=silent</code></td>
    </tr>
    <tr>
      <td><code>--static</code></td>
      <td>String</td>
      <td>build statically linked executable (requires static libc and zlib)</td>
      <td>None</td>
      <td><code>--static=static</code></td>
    </tr>
    <tr>
      <td><code>--static-nolibc</code></td>
      <td>String</td>
      <td>build statically linked executable with libc dynamically linked</td>
      <td>None</td>
      <td><code>--static-nolibc=static-nolibc</code></td>
    </tr>
    <tr>
      <td><code>--target</code></td>
      <td>String</td>
      <td>selects native-image compilation target (in &lt;OS&gt;-&lt;architecture&gt; format). Defaults to host's OS-architecture pair.</td>
      <td>None</td>
      <td><code>--target=target</code></td>
    </tr>
    <tr>
      <td><code>--trace-object-instantiation</code></td>
      <td>String</td>
      <td>comma-separated list of fully-qualified class names that object instantiation is traced for.</td>
      <td>None</td>
      <td><code>--trace-object-instantiation=trace-object-instantiation</code></td>
    </tr>
    <tr>
      <td><code>-O</code></td>
      <td>String</td>
      <td>control code optimizations: b - optimize for fastest build time, s - optimize for size, 0 - no optimizations, 1 - basic optimizations, 2 - advanced optimizations, 3 - all optimizations for best performance.</td>
      <td>None</td>
      <td><code>-O=-O</code></td>
    </tr>
    <tr>
      <td><code>-Werror</code></td>
      <td>String</td>
      <td>treat warnings as errors and terminate build.</td>
      <td>all</td>
      <td><code>-Werror=-Werror</code></td>
    </tr>
    <tr>
      <td><code>-da</code></td>
      <td>String</td>
      <td>also -da[:[packagename]...|:classname] or -disableassertions[:[packagename]...|:classname]. Disable assertions with specified granularity at run time.</td>
      <td></td>
      <td><code>-da=-da</code></td>
    </tr>
    <tr>
      <td><code>-dsa</code></td>
      <td>String</td>
      <td>also -disablesystemassertions. Disables assertions in all system classes at run time.</td>
      <td>None</td>
      <td><code>-dsa=-dsa</code></td>
    </tr>
    <tr>
      <td><code>-ea</code></td>
      <td>String</td>
      <td>also -ea[:[packagename]...|:classname] or -enableassertions[:[packagename]...|:classname]. Enable assertions with specified granularity at run time.</td>
      <td></td>
      <td><code>-ea=-ea</code></td>
    </tr>
    <tr>
      <td><code>-esa</code></td>
      <td>String</td>
      <td>also -enablesystemassertions. Enables assertions in all system classes at run time.</td>
      <td>None</td>
      <td><code>-esa=-esa</code></td>
    </tr>
    <tr>
      <td><code>-g</code></td>
      <td>String</td>
      <td>generate debugging information</td>
      <td>2</td>
      <td><code>-g=-g</code></td>
    </tr>
    <tr>
      <td><code>-march</code></td>
      <td>String</td>
      <td>generate instructions for a specific machine type. Defaults to 'x86-64-v3' on AMD64 and 'armv8.1-a' on AArch64. Use -march=compatibility for best compatibility, or -march=native for best performance if the native executable is deployed on the same machine or on a machine with the same CPU features. To list all available machine types, use -march=list.</td>
      <td>None</td>
      <td><code>-march=-march</code></td>
    </tr>
    <tr>
      <td><code>-o</code></td>
      <td>String</td>
      <td>name of the output file to be generated</td>
      <td>None</td>
      <td><code>-o=-o</code></td>
    </tr>
    <tr>
      <td><code>--gc</code></td>
      <td>Enum</td>
      <td>select native-image garbage collector implementation. Allowed values: 'epsilon', 'serial', 'G1'.</td>
      <td>serial</td>
      <td><code>--gc=&lt;value&gt;</code></td>
    </tr>
    <tr>
      <td><code>--add-modules</code></td>
      <td>String</td>
      <td>root modules to resolve in addition to the initial module. &lt;module name&gt; can also be ALL-DEFAULT, ALL-SYSTEM, ALL-MODULE-PATH.</td>
      <td></td>
      <td><code>--add-modules &lt;module name&gt;[,&lt;module name&gt;...]</code></td>
    </tr>
    <tr>
      <td><code>--bundle-apply</code></td>
      <td>String</td>
      <td>build an image from the given bundle file using the original arguments and files. If --bundle-create is passed after --bundle-apply, a new bundle is written with the applied plus additional arguments.</td>
      <td></td>
      <td><code>--bundle-apply=some-bundle.nib[,dry-run][,container[=&lt;container-tool&gt;][,dockerfile=&lt;Dockerfile&gt;]]</code></td>
    </tr>
    <tr>
      <td><code>--bundle-create</code></td>
      <td>String</td>
      <td>in addition to image building, create a Native Image bundle file (*.nib file) that allows rebuilding of that image again at a later point. If a bundle-file gets passed, the bundle will be created with the given name; otherwise, the bundle-file name is derived from the image name. Bundle options can be extended with ',dry-run' and ',container'; 'dockerfile=&lt;Dockerfile&gt;' uses a user-provided Dockerfile.</td>
      <td></td>
      <td><code>--bundle-create[=new-bundle.nib][,dry-run][,container[=&lt;container-tool&gt;][,dockerfile=&lt;Dockerfile&gt;]]</code></td>
    </tr>
    <tr>
      <td><code>--class-path</code></td>
      <td>Path</td>
      <td>A : separated list of directories, JAR archives, and ZIP archives to search for class files.</td>
      <td></td>
      <td><code>--class-path &lt;class search path of directories and zip/jar files&gt;</code></td>
    </tr>
    <tr>
      <td><code>--configurations-path</code></td>
      <td>Path</td>
      <td>A : separated list of directories to be treated as option-configuration directories.</td>
      <td></td>
      <td><code>--configurations-path &lt;search path of option-configuration directories&gt;</code></td>
    </tr>
    <tr>
      <td><code>--debug-attach</code></td>
      <td>String</td>
      <td>attach to debugger during image building (default port is 8000)</td>
      <td></td>
      <td><code>--debug-attach[=&lt;port or host:port (* can be used as host meaning bind to all interfaces)&gt;]</code></td>
    </tr>
    <tr>
      <td><code>--diagnostics-mode</code></td>
      <td>Boolean</td>
      <td>Enables logging of image-build information to a diagnostics folder.</td>
      <td></td>
      <td><code>--diagnostics-mode</code></td>
    </tr>
    <tr>
      <td><code>--dry-run</code></td>
      <td>Boolean</td>
      <td>output the command line that would be used for building</td>
      <td></td>
      <td><code>--dry-run</code></td>
    </tr>
    <tr>
      <td><code>--enable-preview</code></td>
      <td>Boolean</td>
      <td>allow classes to depend on preview features of this release</td>
      <td></td>
      <td><code>--enable-preview</code></td>
    </tr>
    <tr>
      <td><code>--exclude-config</code></td>
      <td>String</td>
      <td>exclude configuration for a space-separated pair of classpath/modulepath pattern and resource pattern. For example: '--exclude-config foo.jar META-INF\\/native-image\\/.*.properties' ignores all .properties files in 'META-INF/native-image' in all JARs named 'foo.jar'.</td>
      <td></td>
      <td><code>--exclude-config</code></td>
    </tr>
    <tr>
      <td><code>--expert-options</code></td>
      <td>Boolean</td>
      <td>lists image build options for experts</td>
      <td></td>
      <td><code>--expert-options</code></td>
    </tr>
    <tr>
      <td><code>--expert-options-all</code></td>
      <td>Boolean</td>
      <td>lists all image build options for experts (use at your own risk). Options marked with [Extra help available] contain help that can be shown with --expert-options-detail.</td>
      <td></td>
      <td><code>--expert-options-all</code></td>
    </tr>
    <tr>
      <td><code>--expert-options-detail</code></td>
      <td>String</td>
      <td>displays all available help for a comma-separated list of option names. Pass * to show extra help for all options that contain it.</td>
      <td></td>
      <td><code>--expert-options-detail</code></td>
    </tr>
    <tr>
      <td><code>--help</code></td>
      <td>Boolean</td>
      <td>print this help message</td>
      <td></td>
      <td><code>--help</code></td>
    </tr>
    <tr>
      <td><code>--help-extra</code></td>
      <td>Boolean</td>
      <td>print help on non-standard options</td>
      <td></td>
      <td><code>--help-extra</code></td>
    </tr>
    <tr>
      <td><code>--module-path</code></td>
      <td>Path</td>
      <td>A : separated list of directories, each directory is a directory of modules.</td>
      <td></td>
      <td><code>--module-path &lt;module path&gt;...</code></td>
    </tr>
    <tr>
      <td><code>--print-options</code></td>
      <td>String</td>
      <td>print comprehensive options table. Available formats: 'table' (default), 'markdown' or 'md', and 'json'. This eliminates duplication with manual documentation tables.</td>
      <td></td>
      <td><code>--print-options[=&lt;format&gt;]</code></td>
    </tr>
    <tr>
      <td><code>--verbose</code></td>
      <td>Boolean</td>
      <td>enable verbose output</td>
      <td></td>
      <td><code>--verbose</code></td>
    </tr>
    <tr>
      <td><code>--version</code></td>
      <td>Boolean</td>
      <td>print product version and exit</td>
      <td></td>
      <td><code>--version</code></td>
    </tr>
    <tr>
      <td><code>-D</code></td>
      <td>String</td>
      <td>set a system property for image build time only</td>
      <td></td>
      <td><code>-D&lt;name&gt;=&lt;value&gt;</code></td>
    </tr>
    <tr>
      <td><code>-E</code></td>
      <td>String</td>
      <td>allow native-image to access the given environment variable during image build. If &lt;env-var-value&gt; is omitted, the value is taken from the environment native-image was invoked from.</td>
      <td></td>
      <td><code>-E&lt;env-var-key&gt;[=&lt;env-var-value&gt;]</code></td>
    </tr>
    <tr>
      <td><code>-J</code></td>
      <td>String</td>
      <td>pass &lt;flag&gt; directly to the JVM running the image generator</td>
      <td></td>
      <td><code>-J&lt;flag&gt;</code></td>
    </tr>
    <tr>
      <td><code>-V</code></td>
      <td>String</td>
      <td>provide values for placeholders in native-image.properties files</td>
      <td></td>
      <td><code>-V&lt;key&gt;=&lt;value&gt;</code></td>
    </tr>
    <tr>
      <td><code>-classpath</code></td>
      <td>Path</td>
      <td>class search path of directories and zip/jar files</td>
      <td></td>
      <td><code>-classpath &lt;class search path of directories and zip/jar files&gt;</code></td>
    </tr>
    <tr>
      <td><code>-cp</code></td>
      <td>Path</td>
      <td>class search path of directories and zip/jar files</td>
      <td></td>
      <td><code>-cp &lt;class search path of directories and zip/jar files&gt;</code></td>
    </tr>
    <tr>
      <td><code>-p</code></td>
      <td>Path</td>
      <td>module path</td>
      <td></td>
      <td><code>-p &lt;module path&gt;</code></td>
    </tr>
    <tr>
      <td><code>@argument</code></td>
      <td>String</td>
      <td>one or more argument files containing options</td>
      <td></td>
      <td><code>@argument files</code></td>
    </tr>
  </tbody>
</table>
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

Native Image also preserves the corresponding lambda proxy class when a lambda proxy class generated for a preserved capturing class is reached.
It registers that proxy class for reflection and JNI access, like other preserved classes, and it registers serializable lambdas for Java serialization.

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
