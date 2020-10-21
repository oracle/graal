# Native Image Options

The `native-image` builder needs to provide the classpath for all classes
using the familiar option from the `java` launcher: `-cp` is followed by a list
of directories or JAR files, separated by `:`. The name of the class containing
the `main` method is the last argument, or you can use `-jar` and provide a JAR
file that specifies the main method in its manifest.

The syntax of the `native-image` command is:

- `native-image [options] class` to build an executable file for a class in the
current working directory. Invoking it executes the native-compiled code of that
class.

- `native-image [options] -jar jarfile` to build an image for a JAR file.

The options passed to `native-image` are evaluated left-to-right. For more information, see [Native Image Build Configuration](BuildConfiguration.md#order-of-arguments-evaluation).

The options fall into four categories:
image generation options, macro options, non-standard options, and server options.
Non-standard and server options are subject to change through a deprecation cycle.

Command-line help is available. Run `native-image --help` to get
the commands overview, and `native-image --help-extra` to print help on non-standard,
macro, and server options.

### Options to Native Image Builder
The following options are currently supported:

* `-cp, -classpath, --class-path <class search path of directories and zip/jar files>`: a separated list of directories, JAR archives, and ZIP archives to search for class files
* `-D<name>=<value>`: set a system property
* `-J<flag>`: pass `<flag>` directly to the JVM running the image generator
* `-O<level>`: 0 for no optimizations, or 1 for basic optimizations (default)
* `--verbose`: enable verbose output
* `--version`: print product version and exit
* `--help`: print this help message
* `--help-extra`: print help on non-standard options
* `--allow-incomplete-classpath`: allow image building with an incomplete class path; report type resolution errors at runtime when they are accessed the first time, instead of during image building
* `--auto-fallback`: build stand-alone image if possible
* `--enable-all-security-services`: add all security service classes to the generated image
* `--enable-http`: enable http support in the generated image
* `--enable-https`: enable https support in the generated image
* `--enable-url-protocols`: list of comma-separated URL protocols to enable <!---please check this one--->
* `--features`: a comma-separated list of fully qualified feature implementation classes
* `--force-fallback`: force building of fallback image
* `--initialize-at-build-time`: a comma-separated list of packages and classes (and implicitly all of their superclasses) that are initialized during image generation. An empty string designates all packages
* `--initialize-at-run-time`: a comma-separated list of packages and classes (and implicitly all of their subclasses) that must be initialized at runtime and not during image building. An empty string is currently not supported
* `--install-exit-handlers`: provide java.lang.Terminator exit handlers for executable images
* `--libc`: select the libc implementation to use (available implementations are glibc and musl)
* `--native-compiler-options`: provide custom C compiler option used for query code compilation
* `--native-compiler-path`: provide custom path to C compiler used for query code compilation
and linking
* `--native-image-info`: show native toolchain information and image-build settings
* `--no-fallback`: build stand-alone image or report failure
* `--pgo`: a comma-separated list of files from which to read the data collected for profile-guided optimization of AOT compiled code (reads from default.iprof if nothing is specified)
* `--pgo-instrument`: instrument AOT compiled code to collect data for profile-guided optimization into default.iprof file
* `--report-unsupported-elements-at-runtime`: report usage of unsupported methods and fields at runtime when they are accessed the first time, instead of as an error during image building
* `--shared`: build shared library
* `--static`: build statically-linked executable (requires static libc and zlib)
* `-da`: disable assertions in the generated image
* `-ea`: enable assertions in the generated image
* `-g`: generate debugging information

### Macro Options
* `--language:nfi`: make Truffle Native Function Interface language available
* `--language:regex`: make Truffle Regular Expression engine available that exposes regular expression functionality in GraalVM supported languages
* `--language:R`: make R available as a language for the image
* `--language:python`: make Python available as a language for the image
* `--language:llvm`: make LLVM bitcode available for the image
* `--language:js`: make JavaScript available as a language for the image
* `--language:ruby`: make Ruby available as a language for the image
* `--tool:profiler`: add profiling support to a GraalVM supported language
* `--tool:chromeinspector`: add debugging support to a GraalVM supported language

The `--language:python`, `--language:ruby` and `--language:R` polyglot macro options become available once the corresponding languages are added to the base GraalVM installation (see the [GraalVM Updater](https://www.graalvm.org/docs/reference-manual/gu/) guide).

### Non-standard Options
* `--expert-options`: list image build options for experts
* `--expert-options-all `: list all image build options for experts (use at your own risk)
* `--configurations-path <search path of option-configuration directories>`: a separated list of directories to be treated as option-configuration directories
* `--debug-attach[=< port >]`: attach to debugger during image building (default port is 8000)
* `--dry-run`: output the command line that would be used for building
* `-V<key>=<value>`:  provide values for placeholders in _native-image.properties_ files

Native Image options are also distinguished as hosted and runtime options. Continue reading to the [Native Image Hosted and Runtime Options](HostedvsRuntimeOptions.md) guide.
