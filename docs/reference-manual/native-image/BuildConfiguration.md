---
layout: docs
toc_group: build-overview
link_title: Build Configuration
permalink: /reference-manual/native-image/overview/BuildConfiguration/
redirect_from: /reference-manual/native-image/BuildConfiguration/
---

# Native Image Build Configuration

Native Image supports a wide range of options to configure the `native-image` builder.

### Table of Contents

* [Embed a Configuration File](#embed-a-configuration-file)
* [Configuration File Format](#configuration-file-format)
* [Order of Arguments Evaluation](#order-of-arguments-evaluation)
* [Memory Configuration for Native Image Build](#memory-configuration-for-native-image-build)
* [Specify Types Required to Be Defined at Build Time](#specify-types-required-to-be-defined-at-build-time)
 
## Embed a Configuration File

We recommend that you provide the configuration for the `native-image` builder by embedding a _native-image.properties_ file into a project JAR file.
The `native-image` builder will also automatically pick up all configuration options provided in the _META-INF/native-image/_ directory (or any of its subdirectories) and use it to construct `native-image` command-line options.

To avoid a situation when constituent parts of a project are built with overlapping configurations, we recommended you use subdirectories within _META-INF/native-image_: a JAR file built from multiple maven projects cannot suffer from overlapping `native-image` configurations.
For example:
* _foo.jar_ has its configurations in _META-INF/native-image/foo_groupID/foo_artifactID_
* _bar.jar_ has its configurations in _META-INF/native-image/bar_groupID/bar_artifactID_

The JAR file that contains `foo` and `bar` will then contain both configurations without conflict.
Therefore the recommended layout to store configuration data in JAR files is as follows:
```
META-INF/
└── native-image
    └── groupID
        └── artifactID
            └── native-image.properties
```

Note that the use of `${.}` in a _native-image.properties_ file expands to the resource location that contains that exact configuration file.
This can be useful if the _native-image.properties_ file refers to resources within its subdirectory, for example, `-H:ResourceConfigurationResources=${.}/custom_resources.json`.
Always make sure you use the option variants that take resources, that is, use `-H:ResourceConfigurationResources` instead of `-H:ResourceConfigurationFiles`.
Other options that work in this context are:
* `-H:DynamicProxyConfigurationResources`
* `-H:JNIConfigurationResources`
* `-H:ReflectionConfigurationResources`
* `-H:ResourceConfigurationResources`
* `-H:SerializationConfigurationResources`

By having such a composable _native-image.properties_ file, building a native executable does not require any additional option on the command line.
It is sufficient to run the following command:
```shell
$JAVA_HOME/bin/native-image -jar target/<name>.jar
```

To identify which configuration is applied when building a native executable, use `native-image --verbose`.
This shows from where `native-image` picks up the configurations to construct the final composite configuration command-line options for the native image builder.
```shell
native-image --verbose -jar build/basic-app-0.1-all.jar
Apply jar:file://~/build/basic-app-0.1-all.jar!/META-INF/native-image/io.netty/common/native-image.properties
Apply jar:file://~/build/basic-app-0.1-all.jar!/META-INF/native-image/io.netty/buffer/native-image.properties
Apply jar:file://~/build/basic-app-0.1-all.jar!/META-INF/native-image/io.netty/transport/native-image.properties
Apply jar:file://~/build/basic-app-0.1-all.jar!/META-INF/native-image/io.netty/handler/native-image.properties
Apply jar:file://~/build/basic-app-0.1-all.jar!/META-INF/native-image/io.netty/codec-http/native-image.properties
...
Executing [
    <composite configuration command line options for the image builder>
]
```

Typical examples of configurations that use a configuration from _META-INF/native-image_ can be found in [Native Image configuration examples](https://github.com/graalvm/graalvm-demos/tree/master/native-image-configure-examples).

## Configuration File Format

A _native-image.properties_ file is a Java properties file that specifies configurations for `native-image`. 
The following properties are supported.

**Args**

Use this property if your project requires custom `native-image` command-line options to build correctly.
For example, the `native-image-configure-examples/configure-at-runtime-example` contains `Args = --initialize-at-build-time=com.fasterxml.jackson.annotation.JsonProperty$Access` in its _native-image.properties_ file to ensure the class `com.fasterxml.jackson.annotation.JsonProperty$Access` is initialized at executable build time.

**JavaArgs**

Sometimes it can be necessary to provide custom options to the JVM that runs the `native-image` builder.
Use the `JavaArgs` property in this case.

**ImageName**

This property specifies a user-defined name for the executable.
If `ImageName` is not used, a name is automatically chosen:
    * `native-image -jar <name.jar>` has a default executable name `<name>`
    * `native-image -cp ... fully.qualified.MainClass` has a default executable name `fully.qualified.mainclass`

Note that using `ImageName` does not prevent you from overriding the name via the command line.
For example, if `foo.bar` contains `ImageName=foo_app`:
    * `native-image -jar foo.bar` generates the executable `foo_app` but
    * `native-image -jar foo.bar application` generates the executable `application`

### Changing the Default Configuration Directory

Native Image by default stores configuration information in the user's home directory: _$HOME/.native-image/_.
To change this default, set the environment variable `NATIVE_IMAGE_USER_HOME` to a different location. For example:
```shell
export NATIVE_IMAGE_USER_HOME= $HOME/.local/share/native-image
```

## Order of Arguments Evaluation
The options passed to `native-image` are evaluated from left to right.
This also extends to options that are passed indirectly via configuration files in the _META-INF/native-image_ directory.
Consider the example where there is a JAR file that includes _native-image.properties_ containing `Args = -H:Optimize=0`.
You can override the setting that is contained in the JAR file by using the `-H:Optimize=2` option after `-cp <jar-file>`.

## Memory Configuration for Native Image Build

The `native-image` builder runs on a JVM and uses the memory management of the underlying platform.
The usual Java command-line options for garbage collection apply to the `native-image` builder.

During the creation of a native executable, the representation of the whole application is created to determine which classes and methods will be used at runtime.
It is a computationally intensive process that uses the following default values for memory usage:
```
-Xss10M \
-XX:MaxRAMPercentage=<percentage based on available memory> \
-XX:GCTimeRatio=19 \
-XX:+ExitOnOutOfMemoryError \
```
These defaults can be changed by passing `-J + <jvm option for memory>` to the `native-image` tool.

The `-XX:MaxRAMPercentage` value determines the maximum heap size of the builder and is computed based on available memory of the system.
It maxes out at 32GB by default and can be overwritten with, for example, `-J-XX:MaxRAMPercentage=90.0` for 90% of physical memory or `-Xmx4g` for 4GB.
`-XX:GCTimeRatio=19` increases the goal of the total time for garbage collection to 5%, which is more throughput-oriented and reduces peak RSS.
The build process also exits on the first `OutOfMemoryError` (`-XX:+ExitOnOutOfMemoryError`) to provide faster feedback in environments under a lot of memory pressure.

By default, the `native-image` tool uses up to 32 threads (but not more than the number of processors available). For custom values, use the `--parallelism=...` option.

For other related options available to the `native-image` tool, see the output from the command `native-image --expert-options-all`.

## Specify Types Required to Be Defined at Build Time

A well-structured library or application should handle linking of Java types (ensuring all reachable Java types are fully defined at build time) when building a native binary by itself.
The default behavior is to throw linking errors, if they occur, at runtime. 
However, you can prevent unwanted linking errors by specifying which classes are required to be fully linked at build time.
For that, use the `--link-at-build-time` option. 
If the option is used in the right context (see below), you can specify required classes to link at build time without explicitly listing classes and packages.
It is designed in a way that libraries can only configure their own classes, to avoid any side effects on other libraries.
You can pass the option to the `native-image` tool on the command line, embed it in a `native-image.properties` file on the module-path or the classpath.

Depending on how and where the option is used it behaves differently:

* If you use `--link-at-build-time` without arguments, all classes in the scope are required to be fully defined. If used without arguments on command line, all classes will be treated as "link-at-build-time" classes. If used without arguments embedded in a `native-image.properties` file on the module-path, all classes of the module will be treated as "link-at-build-time" classes. If you use `--link-at-build-time` embedded in a `native-image.properties` file on the classpath, the following error will be thrown:
    ```shell
    Error: Using '--link-at-build-time' without args only allowed on module-path. 'META-INF/native-image/org.mylibrary/native-image.properties' in 'file:///home/test/myapp/MyLibrary.jar' not part of module-path.
    ```
* If you use the  `--link-at-build-time` option with arguments, for example, `--link-at-build-time=foo.bar.Foobar,demo.myLibrary.Name,...`, the arguments should be fully qualified class names or package names. When used on the module-path or classpath (embedded in `native-image.properties` files), only classes and packages defined in the same JAR file can be specified. Packages for libraries used on the classpath need to be listed explicitly. To make this process easy, use the `@<prop-values-file>` syntax to generate a package list (or a class list) in a separate file automatically.

Another handy option is `--link-at-build-time-paths` which allows to specify which classes are required to be fully defined at build time by other means.
This variant requires arguments that are of the same type as the arguments passed via `-p` (`--module-path`) or `-cp` (`--class-path`):

```shell
--link-at-build-time-paths <class search path of directories and zip/jar files>
```

The given entries are searched and all classes inside are registered as `--link-at-build-time` classes.
This option is only allowed to be used on command line.

### Related Documentation

- [Class Initialization in Native Image](ClassInitialization.md)
- [Native Image Basics](NativeImageBasics.md)
- [Native Image Build Options](BuildOptions.md)
- [Native Image Build Overview](BuildOverview.md)
- [Reachability Metadata](ReachabilityMetadata.md)
