---
layout: ni-docs
toc_group: build-overview
link_title: Build Options
permalink: /reference-manual/native-image/overview/BuildOptions/
redirect_from: /$version/reference-manual/native-image/Options/
---

#  Native Image Build Options

Depending on the GraalVM edition, the options to the `native-image` builder may differ.
The following options are supported across GraalVM Community Edition and Oracle GraalVM:

* `-cp, -classpath, --class-path <class search path of directories and zip/jar files>`: a `:` (`;` on Windows) separated list of directories, JAR archives, and ZIP archives to search for class files
* `-p <module path>, --module-path <module path>`: a `:` (`;` on Windows) separated list of directories; each directory is a directory of modules
* `--add-modules <module name>[,<module name>...]`: add root modules to resolve in addition to the initial module. `<module name>` can also be `ALL-DEFAULT`, `ALL-SYSTEM`, `ALL-MODULE-PATH`
* `-D<name>=<value>`: set a system property 
* `-J<flag>`: pass `<flag>` directly to the JVM running the image generator
* `--diagnostics-mode`: enable diagnostics output: class initialization, substitutions, etc.
* `--enable-preview`: allow classes to depend on preview features of this release
* `--verbose`: enable verbose output
* `--version`: print the product version and exit
* `--help`: print this help message
* `--help-extra`: print help on non-standard options
* `--auto-fallback`: build stand-alone image if possible
* `--configure-reflection-metadata`: enable runtime instantiation of reflection objects for non-invoked methods.
* `--enable-all-security-services`: add all security service classes to the generated image
* `--enable-http`: enable http support in the generated image
* `--enable-https`: enable https support in the generated image
* `--enable-monitoring`: enable monitoring features that allow the VM to be inspected at run time. Comma-separated list can contain `heapdump`, `jfr`, `jvmstat`, `jmxserver` (experimental), `jmxclient` (experimental), or `all` (deprecated behavior: defaults to `all` if no argument is provided). For example:`--enable-monitoring=heapdump,jfr`.
* `--enable-sbom`:  embed a Software Bill of Materials (SBOM) in the executable or shared library for passive inspection. A comma-separated list can contain `cyclonedx`, `strict` (defaults to `cyclonedx` if no argument is provided), or `export` to save the SBOM to the native executable's output directory. The optional `strict` flag aborts the build if any class cannot be matched to a library in the SBOM. For example: `--enable-sbom=cyclonedx,strict`. (Not available in GraalVM Community Edition.)
* `--enable-url-protocols`: list comma-separated URL protocols to enable.
* `--features`: a comma-separated list of fully qualified [Feature implementation classes](https://www.graalvm.org/sdk/javadoc/index.html?org/graalvm/nativeimage/hosted/Feature.html).
* `--force-fallback`: force building of fallback image
* `--gc=<value>`: select Native Image garbage collector implementation. Allowed options for `<value>` are: `G1` for G1 garbage collector (not available in GraalVM Community Edition); `epsilon` for Epsilon garbage collector; `serial` for Serial garbage collector (default).
* `--initialize-at-build-time`: a comma-separated list of packages and classes (and implicitly all of their superclasses) that are initialized during image generation. An empty string designates all packages.
* `--initialize-at-run-time`: a comma-separated list of packages and classes (and implicitly all of their subclasses) that must be initialized at run time and not during image building. An empty string is currently not supported.
* `--install-exit-handlers`: provide `java.lang.Terminator` exit handlers
* `--libc`: select the `libc` implementation to use. Available implementations: `glibc`, `musl`, `bionic`
* `--link-at-build-time`: require types to be fully defined at image build-time. If used without args, all classes in scope of the option are required to be fully defined.
* `--link-at-build-time-paths`: require all types in given class or module-path entries to befully defined at image build-time.
* `--list-cpu-features`: show CPU features specific to the target platform and exit.
* `--native-compiler-options`: provide a custom C compiler option used to query code compilation.
* `--list-modules`: list observable modules and exit.
* `--native-compiler-options`: provide custom C compiler option used for query code compilation.
* `--native-compiler-path`: provide a custom path to the C compiler used to query code compilation and linking.
* `--native-image-info`: show native toolchain information and native executable's build settings
* `--no-fallback`: build a standalone native executable or report a failure
* `--pgo`: a comma-separated list of files from which to read the data collected for profile-guided optimization of AOT compiled code (reads from  _default.iprof_ if nothing is specified). (Not available in GraalVM Community Edition.)
* `--pgo-instrument`: instrument AOT compiled code to collect data for profile-guided optimization into the _default.iprof_ file. (Not available in GraalVM Community Edition.)
* `--report-unsupported-elements-at-runtime`: report the usage of unsupported methods and fields at run time when they are accessed the first time, instead of an error during image building
* `--shared`: build a shared library
* `--silent`: silence build output
* `--static`: build a statically linked executable (requires `libc` and `zlib` static libraries)
* `--target`: select the native image compilation target (in <OS>-<architecture> format). Defaults to host's OS-architecture pair.
* `--trace-class-initialization`: provide a comma-separated list of fully-qualified class names that a class initialization is traced for.
* `--trace-object-instantiation`: provide a comma-separated list of fully-qualified class names that an object instantiation is traced for.
* `-O<level>`: control code optimizations: b - quick build mode for development,0 - no optimizations, 1 - basic optimizations, 2 - aggressive optimizations (default).
* `-da`: also `-da[:[packagename]|:[classname]` or -`disableassertions[:[packagename]|:[classname]`. Disable assertions with specified granularity at run time.
* `-dsa`: also `-disablesystemassertions`. Disables assertions in all system classes at run time.
* `-ea`: also `-ea[:[packagename]|:[classname]` or -`enableassertions[:[packagename]|:[classname]`. Enable assertions with specified granularity at run time.
* `-esa`: also `-enablesystemassertions`. Enables assertions in all system classes at run time.
* `-g`: generate debugging information
* `-march`: generate instructions for a specific machine type. Defaults to 'x86-64-v3' on AMD64 and 'armv8-a' on AArch64. Use `-march=compatibility` for best compatibility, or `-march=native` for best performance if the native executable is deployed on the same machine or on a machine with the same CPU features. To list all available machine types, use `-march=list`.
* `-o`: name of the output file to be generated

### Macro Options
* `--language:nfi`: make the Truffle Native Function Interface language available
* `--tool:coverage`: add source code coverage support to the GraalVM supported language
* `--tool:insight`: add support for detailed access to program's runtime behavior, allowing users to inspect values and types at invocation or allocation sites
* `--tool:dap`: allow image to open a debugger port serving the Debug Adapter Protocol in IDEs like VS Code
* `--tool:chromeinspector`: add debugging support to a GraalVM supported language
* `--tool:insightheap`: snapshot a region of image heap during the execution
* `--tool:lsp`: add the Language Server Protocol support to later attach compatible debuggers to GraalVM in IDEs like VS Code
* `--tool:sandbox`: enables the Truffle sandbox resource limits. For more information, check the [dedicated documentation](../embedding/sandbox-options.md)
* `--tool:profiler`: add profiling support to a GraalVM supported language

### Non-standard Options

Run `native-image --help-extra` for non-standard options help.

* `--expert-options`: list image build options for experts
* `--expert-options-all `: list all image build options for experts (use at your own risk). Options marked with _Extra help available_ contain help that can be shown with `--expert-options-detail`
* `--expert-options-detail`: display all available help for a comma-separated list of option names. Pass `*` to show extra help for all options that contain it.
* `--configurations-path <search path of option-configuration directories>`: a separated list of directories to be treated as option-configuration directories.
* `--debug-attach[=< port (* can be used as host meaning bind to all interfaces)>]`: attach to debugger during image building (default port is 8000)
* `--diagnostics-mode`: Enables logging of image-build information to a diagnostics folder.
* `--dry-run`: output the command line that would be used for building
* `--bundle-create[=new-bundle.nib]`: in addition to image building, create a native image bundle file _(*.nibfile)_ that allows rebuilding of that image again at a later point. If a bundle-file gets passed the bundle will be created with the given name. Otherwise, the bundle-file name is derived from the image name. Note both bundle options can be combined with `--dry-run` to only perform the bundle operations without any actual image building.
* `--bundle-apply=some-bundle.nib`: an image will be built from the given bundle file with the exact same arguments and files that have been passed to Native Image originally to create the bundle. Note that if an extra `--bundle-create` gets passed after `--bundle-apply`, a new bundle will be written based on the given bundle args plus any additional arguments that haven been passed afterwards. For example: `native-image --bundle-apply=app.nib --bundle-create=app_dbg.nib -g` creates a new bundle <app_dbg.nib>  based on the given _app.nib_ bundle. Both bundles are the same except the new one also uses the -g option.
* `-E<env-var-key>[=<env-var-value>]`: allow Native Image to access the given environment variable during image build. If the optional <env-var-value> is not given, the value of the environment variable will be taken from the environment Native Image was invoked from.
* `-V<key>=<value>`:  provide values for placeholders in `native-image.properties` files
* `--add-exports`: value `<module>/<package>=<target-module>(,<target-module>)*` updates `<module>` to export `<package>` to `<target-module>`, regardless of module declaration. `<target-module>` can be `ALL-UNNAMED` to export to all unnamed modules
* `--add-opens`: value `<module>/<package>=<target-module>(,<target-module>)*` updates `<module>` to open `<package>` to `<target-module>`, regardless of module declaration. 
* `--add-reads`: value `<module>=<target-module>(,<target-module>)*` updates `<module>` to read `<target-module>`, regardless of module declaration. `<target-module>` can be `ALL-UNNAMED` to read all unnamed modules.

Available macro options are:
* `--macro:gu-launcher`
* `--macro:native-image-agent-library`
* `--macro:native-image-configure-launcher`
* `--macro:native-image-diagnostics-agent-library`
* `--macro:native-image-launcher`
* `--macro:polyglot-launcher`
* `--macro:polyglot-library`
* `--macro:truffle`
* `--macro:truffle-language-library`


Native Image options are also distinguished as hosted and runtime options.

### Further Reading

* [Native Image Hosted and Runtime Options](HostedvsRuntimeOptions.md) guide.
* [Build Configuration](BuildConfiguration.md#order-of-arguments-evaluation)