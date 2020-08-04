# Native Image Options

The `native-image` command line needs to provide the classpath for all classes
using the familiar option from the `java` launcher: `-cp` is followed by a list
of directories or .jar files, separated by `:`. The name of the class containing
the `main` method is the last argument, or you can use `-jar` and provide a .jar
file that specifies the main method in its manifest.

The syntax of the `native-image` command is:

- `native-image [options] class` to build an executable file for a class in the
current working directory. Invoking it executes the native-compiled code of that
class.

- `native-image [options] -jar jarfile` to build an image for a jar file.

Options to Native Image Builder fall into four categories:
image generation options, macro options, non-standard options and server options.
Non-standard and server options are subject to change through a deprecation cycle.

There is a command-line help available. Run `native-image --help` to get
commands overview and `native-image --help-extra` to print help on non-standard,
macro and server options. The `native-image --version` command prints product
version and exits.

### Options to Native Image Builder
The following options to the `native-image` generator are currently supported:

| Option                         | Description                                                                                                                                                                       |
|--------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| -cp and --class-path           | Search for class files through a separated list of directories, JAR and ZIP archives.                                                                                     |
| -D<name>=<value>               | Set a system property for the JVM running the image generator.                                                                                                                    |
| -J< flag >                       | Pass < flag > directly to the JVM running the image generator.                                                                                                                      |
| -O< level >                      | 0 – no optimizations, 1 – basic optimizations (default).                                                                                                                          |
| --verbose                      | Enable verbose output.                                                                                                                                                            |
| --version                      | Print component version.                                                                                                                                                            |
| --allow-incomplete-classpath   | Allow image building with an incomplete class path: report type, resolution errors at run time when they are accessed the first time, instead of during image building.           |
| --auto-fallback                | Build stand-alone image if possible.                                                                                                                                              |
| --enable-all-security-services | Add all security service classes to the generated image.                                                                                                                          |
| --enable-http                  | Enable http support in the generated image.                                                                                                                                       |
| --enable-https                 | Enable https support in the generated image.                                                                                                                                      |
| --enable-url-protocols         | List of comma separated URL protocols to enable.                                                                                                                                  |
| --features                     | A comma-separated list of fully qualified feature implementation classes.                                                                                                        |
| --force-fallback               | Force building of fallback image.                                                                                                                                                 |
| --initialize-at-build-time   | A comma-separated list of packages and classes and implicitly all of their superclasses that are initialized during image generation. An empty string designates all packages.           |
| --initialize-at-run-time   | A comma-separated list of packages and classes and implicitly all of their subclasses that must be initialized at runtime and not during image building. An empty string is not supported.           |
| --install-exit-handlers   | Provide java.lang.Terminator exit handlers for executable images.           |
| --native-compiler-options   | Provide custom C compiler option used for query code compilation.           |
| --native-compiler-path   | Provide custom path to C compiler used for query code compilation
                          and linking.           |
| --native-image-info   | Show native-toolchain information and image-build settings.           |                          
| --no-fallback                  | Build stand-alone image or report failure.                                                                                                                                        |
| --pgo                          | A comma-separated list of files from which to read the data, collected for profile-guided optimization of AOT compiled code (reads from _default.iprof_ if nothing is specified). |
| --pgo-instrument                         | Instrument AOT compiled code to collect data for profile-guided, optimization into _default.iprof_ file.                                         |
| --report-unsupported-elements-at-runtime | Report usage of unsupported methods and fields at run time when they are accessed the first time, instead of an error during image building. |
| --shared                                 | Build a shared library.                                                                                                                          |
| --static                                 | Build statically linked executable (requires static _libc_ and _zlib_).                                                                              |
| -da                                      | Disable assertions in the generated image.                                                                                                       |
| -ea                                      | Enable assertions in the generated image.                                                                                                        |
| -g                                       | Generate debugging information.                                                                                                                  |

### Macro Options

| Option                 	| Description                                               	|
|------------------------	|-----------------------------------------------------------	|
| --language:nfi      	|   Make Truffle Native Function Interface language available.    	|
| --language:regex     	|   Make Truffle Regular Expression engine available that exposes regular expression functionality in GraalVM supported languages.  	|
| --language:R          	| Make R available as a language for the image.    	|
| --language:python       | Make Python available as a language for the image.                	|
| --language:llvm        	| Make LLVM bitcode available for the image.                	|
| --language:js          	| Make JavaScript available as a language for the image.    	|
| --language:ruby         | Make Ruby available as a language for the image.    	|
| --tool:profiler        	| Add profiling support to a GraalVM supported language.  	|
| --tool:chromeinspector 	| Add debugging support to a GraalVM supported language.  	|

The `--language:python`, `--language:ruby` and `--language:R` polyglot macro options become available once the corresponding languages engines are added to the base GraalVM installation (see the [GraalVM Updater](https://www.graalvm.org/docs/reference-manual/gu/) guide).

### Non-standard Options

| Option                                                                  | Description                                                                          |
|-------------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| --expert-options                                                        | List image build options for experts.                                                |
| --expert-options-all                                                    | List all image build options for experts (use at your own risk).                     |
| --configurations-path <search path of option-configuration directories> | A : separated list of directories to be treated as option-configuration directories. |
| --debug-attach[=< port >]                                                 | Attach to debugger during image building (default port is 8000).                     |
| --dry-run                                                               | Output the command line that would be used for building.                             |
| -V<key>=<value>                                                         | Provide values for placeholders in _native-image.properties_ files.                  |

### Server Options

| Option                                 | Description                                                                           |
|----------------------------------------|---------------------------------------------------------------------------------------|
| --no-server                            | Do not use server-based image building.                                                        |
| --server-list                          | List current image-build servers.                                                     |
| --server-list-details                  | List current image-build servers with more details.                                   |
| --server-cleanup                       | Remove stale image-build servers entries.                                             |
| --server-shutdown                      | Shut down image-build servers under current session ID.                               |
| --server-shutdown-all                  | Shut down all image-build servers.                                                    |
| --server-session=<custom-session-name> | Use custom session name instead of system provided session ID of the calling process. |
| --verbose-server                       | Enable verbose output for image-build server handling.                                |

Options to Native Image are also distinguished as hosted and runtime options. Continue reading to the [ Native Image Hosted and Runtime Options](HostedvsRuntimeOptions.md) guide.
