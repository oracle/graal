---
layout: docs
toc_group: native-image
link_title: Build Configuration
permalink: /reference-manual/native-image/BuildConfiguration/
---
# Native Image Build Configuration

* [Embedding a Configuration File](#embedding-a-configuration-file)
* [Configuration File Format](#configuration-file-format)
* [Memory Configuration for Native Image Build](#memory-configuration-for-native-image-build)
* [Runtime vs Build-Time Initialization](#runtime-vs-build-time-initialization)
* [Assisted Configuration of Native Image Builds](Agent.md#assisted-configuration-of-native-image-builds)
* [Building Native Image with Java Reflection Example](Agent.md#building-native-image-with-java-reflection-example)
* [Agent Advanced Usage](Agent.md#agent-advanced-usage)

Native Image supports a wide range of options to configure a native image build process.

## Embedding a Configuration File

A recommended way to provide configuration is to embed a **native-image.properties** file into a project JAR file.
The Native Image builder will automatically pick up all configuration options provided anywhere below the resource location `META-INF/native-image/` and use it to construct `native-image` command line arguments.

To avoid a situation when constituent parts of a project are built with overlapping configurations, it is recommended to use "subdirectories" within `META-INF/native-image`.
That way a JAR file built from multiple maven projects cannot suffer from overlapping `native-image` configurations.
For example:
* _foo.jar_ has its configurations in `META-INF/native-image/foo_groupID/foo_artifactID`
* _bar.jar_ has its configurations in `META-INF/native-image/bar_groupID/bar_artifactID`

The JAR file that contains `foo` and `bar` will then contain both configurations without conflicting with one another.
Therefore the recommended layout for storing native image configuration data in JAR files is the following:
```
META-INF/
└── native-image
    └── groupID
        └── artifactID
            └── native-image.properties
```

Note that the use of `${.}` in a _native-image.properties_ file expands to the resource location that contains that exact configuration file.
This can be useful if the _native-image.properties_ file wants to refer to resources within its "subfolder", for example, `-H:SubstitutionResources=${.}/substitutions.json`.
Always make sure to use the option variants that take resources, i.e., use `-H:ResourceConfigurationResources` instead of `-H:ResourceConfigurationFiles`.
Other options that are known to work in this context are:
* `-H:DynamicProxyConfigurationResources`
* `-H:JNIConfigurationResources`
* `-H:ReflectionConfigurationResources`
* `-H:ResourceConfigurationResources`
* `-H:SubstitutionResources`
* `-H:SerializationConfigurationResources`

By having such a composable _native-image.properties_ file, building an image does not require any additional arguments specified on command line.
It is sufficient to just run the following command:
```shell
$JAVA_HOME/bin/native-image -jar target/<name>.jar
```

To debug which configuration data gets applied for the image building, use `native-image --verbose`.
This will show from where `native-image` picks up the configurations to construct the final composite configuration command line options for the native image builder.
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

Typical examples of `META-INF/native-image` based native image configuration can be found in [Native Image configuration examples](https://github.com/graalvm/graalvm-demos/tree/master/native-image-configure-examples).

## Configuration File Format

A `native-image.properties` file is a regular Java properties file that can be
used to specify native image configurations. The following properties are
supported.

**Args**

Use this property if your project requires custom `native-image` command line options to build correctly.
For example, the `native-image-configure-examples/configure-at-runtime-example` has `Args = --initialize-at-build-time=com.fasterxml.jackson.annotation.JsonProperty$Access` in its `native-image.properties` file to ensure the class `com.fasterxml.jackson.annotation.JsonProperty$Access` gets initialized at image build time.

**JavaArgs**

Sometimes it can be necessary to provide custom options to the JVM that runs the native image builder.
The `JavaArgs` property can be used in this case.

**ImageName**

This property can be used to specify a user-defined name for the image.
If `ImageName` is not used, a name gets automatically chosen:
* `native-image -jar <name.jar>` has a default image name `<name>`
* `native-image -cp ... fully.qualified.MainClass` has a default image name `fully.qualified.mainclass`

Note that using `ImageName` does not prevent the user to override the name later via command line.
For example, if `foo.bar` contains `ImageName=foo_app`:
* `native-image -jar foo.bar` generates the image `foo_app` but
* `native-image -jar foo.bar application` generates the image `application`

### Order of Arguments Evaluation
The arguments passed to `native-image` are evaluated left-to-right.
This also extends to arguments that get passed indirectly via `META-INF/native-image` based native image configuration.
Suppose you have a JAR file that contains _native-image.properties_ with `Args = -H:Optimize=0`.
Then by using the `-H:Optimize=2` option after `-cp <jar-file>` you can override the setting that comes from the JAR file.

### Specifying Default Options for Native Image
If there is a need to pass some options for every image build unconditionally, for example, to always generate an image in verbose mode (`--verbose`), you can make use of the `NATIVE_IMAGE_CONFIG_FILE` environment variable.
If it is set to a Java properties file, the Native Image builder will use the default setting defined in there on each invocation.
Write a configuration file and export `NATIVE_IMAGE_CONFIG_FILE=$HOME/.native-image/default.properties` in `~/.bash_profile`.
Every time `native-image` gets used, it will implicitly use the arguments specified as `NativeImageArgs`, plus the arguments specified on the command line.
Here is an example of a configuration file, saved as `~/.native-image/default.properties`:

```
NativeImageArgs = --configurations-path /home/user/custom-image-configs \
                  -O1
```

### Changing the Configuration Directory

Native Image by default stores the configuration information in user's home directory -- `$HOME/.native-image/`.
In order to change the output directory, set the environment variable `NATIVE_IMAGE_USER_HOME` to a different location. For example:
```shell
export NATIVE_IMAGE_USER_HOME= $HOME/.local/share/native-image
```

## Memory Configuration for Native Image Build

The native image build runs on the Java HotSpot VM and uses the memory management of the underlying platform.
The usual Java HotSpot command-line options for garbage collection apply to the native image builder.

During the native image build, the representation of a whole program is created to figure out which classes and methods will be used at run time.
It is a computationally intensive process.
The default values for memory usage at image build time are:
```
-Xss10M \
-Xms1G \
```
These defaults can be changed by passing `-J + <jvm option for memory>` to the native image builder.

The `-Xmx` value is computed by using 80% of the physical memory size, but no more than 14G per server.
Providing a larger value for `-Xmx` on command line is possible, e.g., `-J-Xmx26G`.

By default, image building uses of up to 32 threads (but not more than the number of processors available). For custom values `-H:NumberOfThreads=...` can be used.

Check other related options to the native image builder from the `native-image --expert-options-all` list.

## Runtime vs Build-Time Initialization

Building your application into a native image allows you to decide which parts of your application should be run at image build time and which parts have to run at image run time.

All class-initialization code (static initializers and static field initialization) of the application you build an image for is executed at image run time by default.
Sometimes it is beneficial to allow class initialization code to get executed at image build time for faster startup (e.g., if some static fields get initialized to run-time independent data).
This can be controlled with the following `native-image` options:

* `--initialize-at-build-time=<comma-separated list of packages and classes>`
* `--initialize-at-run-time=<comma-separated list of packages and classes>`

In addition to that, arbitrary computations are allowed at build time that can be put into `ImageSingletons` that are accessible at image run time.
For more information please have a look at [Native Image configuration examples](https://github.com/graalvm/graalvm-demos/tree/master/native-image-configure-examples).

For more information, continue reading to the [Class Initialization in Native Image](ClassInitialization.md) guide.
