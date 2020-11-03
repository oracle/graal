# Native Image Build Configuration

* [Embedding a Configuration File](#embedding-a-configuration-file)
* [Configuration File Format](#configuration-file-format)
* [Memory Configuration for Native Image Build](#memory-configuration-for-native-image-build)
* [Runtime vs Build-Time Initialization](#runtime-vs-build-time-initialization)
* [Assisted Configuration of Native Image Builds](#assisted-configuration-of-native-image-builds)
* [Building Native Image with Java Reflection Example](#building-native-image-with-java-reflection-example)
* [Agent Advanced Usage](#agent-advanced-usage)

Native Image supports a wide range of options to configure a native image build process.

## Embedding a Configuration File

A recommended way to provide configuration is to embed a
**native-image.properties** file into a project JAR file. The Native Image builder
will automatically pick up all configuration options provided anywhere below the
resource location `META-INF/native-image/` and use it to construct
`native-image` command line arguments.

To avoid a situation when constituent parts of a project are built
with overlapping configurations, it is recommended to use "subdirectories" within
`META-INF/native-image`. That way a JAR file built from multiple maven projects
cannot suffer from overlapping `native-image` configurations. For example:
* _foo.jar_ has its configurations in `META-INF/native-image/foo_groupID/foo_artifactID`
* _bar.jar_ has its configurations in `META-INF/native-image/bar_groupID/bar_artifactID`

The JAR file that contains `foo` and `bar` will then contain both configurations
without conflicting with one another. Therefore the recommended layout for
storing native image configuration data in JAR files is the following:
```
META-INF/
└── native-image
    └── groupID
        └── artifactID
            └── native-image.properties
```

Note that the use of `${.}` in a _native-image.properties_ file expands to the
resource location that contains that exact configuration file. This can be
useful if the _native-image.properties_ file wants to refer to resources within
its "subfolder", for example, `-H:SubstitutionResources=${.}/substitutions.json`.
Always make sure to use the option variants that take resources, i.e., use
`-H:ResourceConfigurationResources` instead of `-H:ResourceConfigurationFiles`.
Other options that are known to work in this context are:
* `-H:DynamicProxyConfigurationResources`
* `-H:JNIConfigurationResources`
* `-H:ReflectionConfigurationResources`
* `-H:ResourceConfigurationResources`
* `-H:SubstitutionResources`

By having such a composable _native-image.properties_ file, building an image
does not require any additional arguments specified on command line. It is
sufficient to just run the following command:
```shell
$JAVA_HOME/bin/native-image -jar target/<name>.jar
```

To debug which configuration data gets applied for the image building, use `native-image --verbose`. This will show from where `native-image` picks up the
configurations to construct the final composite configuration command line
options for the native image builder.
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

Use this property if your project requires custom `native-image` command line options to build correctly. For example, the `native-image-configure-examples/configure-at-runtime-example` has `Args = --initialize-at-build-time=com.fasterxml.jackson.annotation.JsonProperty$Access`  in its `native-image.properties` file to ensure the class `com.fasterxml.jackson.annotation.JsonProperty$Access` gets initialized at image build time.

**JavaArgs**

Sometimes it can be necessary to provide custom options to the JVM that runs the
native image builder. The `JavaArgs` property can be used in this case.

**ImageName**

This property can be used to specify a user-defined name for the image. If
`ImageName` is not used, a name gets automatically chosen:
* `native-image -jar <name.jar>` has a default image name `<name>`
* `native-image -cp ... fully.qualified.MainClass` has a default image name `fully.qualified.mainclass`

Note that using `ImageName` does not prevent the user to override the name later via command line. For example, if `foo.bar` contains `ImageName=foo_app`:
* `native-image -jar foo.bar` generates the image `foo_app` but
* `native-image -jar foo.bar application` generates the image `application`

### Order of Arguments Evaluation
The arguments passed to `native-image` are evaluated left-to-right. This also
extends to arguments that get passed indirectly via `META-INF/native-image`
based native image configuration. Suppose you have a JAR file that contains
_native-image.properties_ with `Args = -H:Optimize=0`. Then by using the
`-H:Optimize=2` option after `-cp <jar-file>` you can override the setting that
comes from the JAR file.

### Specifying Default Options for Native Image
If there is a need to pass some options for every image build unconditionally, for
example, to always generate an image in verbose mode (`--verbose`), you can
make use of the `NATIVE_IMAGE_CONFIG_FILE` environment variable.
If it is set to a Java properties file, the Native Image builder will use the
default setting defined in there on each invocation. Write a
configuration file and export
`NATIVE_IMAGE_CONFIG_FILE=$HOME/.native-image/default.properties` in
`~/.bash_profile`. Every time `native-image` gets used, it will implicitly use
the arguments specified as `NativeImageArgs`, plus the arguments specified on the
command line. Here is an example of a configuration file, saved as
`~/.native-image/default.properties`:

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

During the native image build, the representation of a whole program is created to
figure out which classes and methods will be used at run time. It is a
computationally intensive process. The default values for memory usage at image build time are:
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

Building your application into a native image allows you to decide which parts
of your application should be run at image build time and which parts have to
run at image run time.

All class-initialization code (static initializers and static
field initialization) of the application you build an image for is executed
at image run time by default. Sometimes it is beneficial to allow class
initialization code to get executed at image build time for faster startup (e.g.,
if some static fields get initialized to run-time independent data). This can be
controlled with the following `native-image` options:

* `--initialize-at-build-time=<comma-separated list of packages and classes>`
* `--initialize-at-run-time=<comma-separated list of packages and classes>`

In addition to that, arbitrary computations are allowed at build time that can be put into `ImageSingletons` that are
accessible at image run time. For more information please have a look at [Native Image configuration examples](https://github.com/graalvm/graalvm-demos/tree/master/native-image-configure-examples).

For more information, continue reading to the [Class Initialization in Native Image](ClassInitialization.md) guide.

## Assisted Configuration of Native Image Builds

Native images are built ahead of runtime and their build relies on a static analysis of which code will be reachable. However, this analysis cannot always completely predict all usages of the Java Native Interface (JNI), Java Reflection, Dynamic Proxy objects (`java.lang.reflect.Proxy`), or class path resources (`Class.getResource`). Undetected usages of these dynamic features need to be provided to the `native-image` tool in the form of configuration files.

In order to make preparing these configuration files easier and more convenient, GraalVM provides an _agent_ that tracks all usages of dynamic features of an execution on a regular Java VM. It can be enabled on the command line of the GraalVM `java` command:
```shell
$JAVA_HOME/bin/java -agentlib:native-image-agent=config-output-dir=/path/to/config-dir/ ...
```

Note that `-agentlib` must be specified _before_ a `-jar` option or a class name or any application parameters in the `java` command line.

During execution, the agent interfaces with the Java VM to intercept all calls that look up classes, methods, fields, resources, or request proxy accesses. The agent then generates the files `jni-config.json`, `reflect-config.json`, `proxy-config.json` and `resource-config.json` in the specified output directory, which is `/path/to/config-dir/` in the example above. The generated files are standalone configuration files in _JSON_ format which contain all intercepted dynamic accesses.

It can be necessary to run the target application more than once with different inputs to trigger separate execution paths for a better coverage of dynamic accesses. The agent supports this with the `config-merge-dir` option which adds the intercepted accesses to an existing set of configuration files:
```shell
$JAVA_HOME/bin/java -agentlib:native-image-agent=config-merge-dir=/path/to/config-dir/ ...
                                                              ^^^^^
```

If the specified target directory or configuration files in it are missing when using `config-merge-dir`, the agent creates them and prints a warning.

By default the agent will write the configuration files after the JVM process terminates. In addition, the agent provides the following flags to write configuration files on a periodic basis:
- `config-write-period-secs`: executes a periodic write every number of seconds as specified in this configuration. Supports only integer values greater than zero.
- `config-write-initial-delay-secs`: the number of seconds before the first write is schedule for execution. Supports only integer values greater or equal to zero. Enabled only if `config-write-period-secs` is greater than zero.

For example:
```shell
$JAVA_HOME/bin/java -agentlib:native-image-agent=config-output-dir=/path/to/config-dir/,config-write-period-secs=300,config-write-initial-delay-secs=5 ...
```

It is advisable to manually review the generated configuration files. Because the agent observes only code that was executed, the resulting configurations can be missing elements that are used in other code paths. It could also make sense to simplify the generated configurations to make any future manual maintenance easier.

The generated configuration files can be supplied to the `native-image` tool by placing them in a `META-INF/native-image/` directory on the class path, for example, in a JAR file used in the image build. This directory (or any of its subdirectories) is searched for files with the names `jni-config.json`, `reflect-config.json`, `proxy-config.json` and `resource-config.json`, which are then automatically included in the build. Not all of those files must be present. When multiple files with the same name are found, all of them are included.

## Building Native Image with Java Reflection Example

For demonstration purposes, save the following code as _ReflectionExample.java_ file:

```java
import java.lang.reflect.Method;

class StringReverser {
    static String reverse(String input) {
        return new StringBuilder(input).reverse().toString();
    }
}

class StringCapitalizer {
    static String capitalize(String input) {
        return input.toUpperCase();
    }
}

public class ReflectionExample {
    public static void main(String[] args) throws ReflectiveOperationException {
        String className = args[0];
        String methodName = args[1];
        String input = args[2];

        Class<?> clazz = Class.forName(className);
        Method method = clazz.getDeclaredMethod(methodName, String.class);
        Object result = method.invoke(null, input);
        System.out.println(result);
    }
}
```

This is a simple Java program where non-constant strings for accessing program
elements by name must come as external inputs. The main method invokes a method
of a particular class (`Class.forName`) whose names are passed as command line
arguments. Providing any other class or method name on the command line leads to
an exception.

Having compiled the example, invoke each method:
```shell
$JAVA_HOME/bin/javac ReflectionExample.java
$JAVA_HOME/bin/java ReflectionExample StringReverser reverse "hello"
olleh
$JAVA_HOME/bin/java ReflectionExample StringCapitalizer capitalize "hello"
HELLO
```

Build a native image normally, without a reflection configuration file, and run a resulting image:
```shell
$JAVA_HOME/bin/native-image ReflectionExample
[reflectionexample:59625]    classlist:     467.66 ms
...
./reflectionexample
```
The `reflectionexample` binary is just a launcher for the JVM. To
build a native image with reflective lookup operations, apply the tracing
agent to write a configuration file to be later fed into the native image
build together.

1. Create a directory `META-INF/native-image` in the working directory:
```shell
mkdir -p META-INF/native-image
```

2. Enable the agent and pass necessary command line arguments:
```shell
$JAVA_HOME/bin/java -agentlib:native-image-agent=config-output-dir=META-INF/native-image ReflectionExample StringReverser reverse "hello"
```
This command creates a _reflection-config.json_ file which makes the `StringReverser` class and the `reverse()` method accessible via reflection. The _jni-config.json_, _proxy-config.json_ , and _resource-config.json_ configuration files are written in that directory too.

3. Build a native image:
```shell
$JAVA_HOME/bin/native-image --no-fallback ReflectionExample
```
The native image builder automatically picks up configuration files in the
_META-INF/native-image_ directory or subdirectories. However, it is recommended
to have _META-INF/native-image_ location on the class path, either via a JAR
file or via the `-cp` flag. It will help to avoid confusion for IDE users where a
directory structure is defined by the tool.

4. Test the methods, but remember that you have not run the tracing agent twice to create a configuration that supports both:
```shell
./reflectionexample StringReverser reverse "hello"
olleh
./reflectionexample  StringCapitalizer capitalize "hello"
Exception in thread "main" java.lang.ClassNotFoundException: StringCapitalizer
	at com.oracle.svm.core.hub.ClassForNameSupport.forName(ClassForNameSupport.java:60)
	at java.lang.Class.forName(DynamicHub.java:1161)
	at ReflectionExample.main(ReflectionExample.java:21)
```

Neither the tracing agent nor native images generator can automatically check
if the provided configuration files are complete. The agent only observes and
records which values are accessed through reflection so that the same accesses
are possible in a native image. You can either manually edit the
_reflection-config.json_ file, or re-run the tracing agent to transform the
existing configuration file, or extend it by using `config-merge-dir` option:

```shell
$JAVA_HOME/bin/java -agentlib:native-image-agent=config-merge-dir=META-INF/native-image ReflectionExample StringCapitalizer capitalize "hello"
```
Note, the different `config-merge-dir` option instructs the agent to extend the
existing configuration files instead of overwriting them. After re-building the
native image, the `StringCapitalizer` class and the `capitalize` method will be
accessible too.

![](https://www.graalvm.org/docs/reference-manual/native-image/reflect_config_file_merged.png)

## Agent Advanced Usage

### Caller-based Filters

By default, the agent filters dynamic accesses which Native Image supports without configuration. The filter mechanism works by identifying the Java method performing the access, also referred to as _caller_ method, and matching its declaring class against a sequence of filter rules. The built-in filter rules exclude dynamic accesses which originate in the JVM, or in parts of a Java class library directly supported by Native Image (such as `java.nio`) from the generated configuration files. Which item (class, method, field, resource, etc.) is being accessed is not relevant for filtering.

In addition to the built-in filter, custom filter files with additional rules can be specified using the `caller-filter-file` option. For example: `-agentlib:caller-filter-file=/path/to/filter-file,config-output-dir=...`

Filter files have the following structure:
```json
{ "rules": [
    {"excludeClasses": "com.oracle.svm.**"},
    {"includeClasses": "com.oracle.svm.tutorial.*"},
    {"excludeClasses": "com.oracle.svm.tutorial.HostedHelper"}
  ]
}
```

The `rules` section contains a sequence of rules. Each rule specifies either `includeClasses`, which means that lookups originating in matching classes will be included in the resulting configuration, or `excludeClasses`, which excludes lookups originating in matching classes from the configuration. Each rule defines a pattern for the set of matching classes, which can end in `.*` or `.**`: a `.*` ending matches all classes in a package and that package only, while a `.**` ending matches all classes in the package as well as in all subpackages at any depth. Without `.*` or `.**`, the rule applies only to a single class with the qualified name that matches the pattern. All rules are processed in the sequence in which they are specified, so later rules can partially or entirely override earlier ones. When multiple filter files are provided (by specifying multiple `caller-filter-file` options), their rules are chained together in the order in which the files are specified. The rules of the built-in caller filter are always processed first, so they can be overridden in custom filter files.

In the example above, the first rule excludes lookups originating in all classes from package `com.oracle.svm` and from all of its subpackages (and their subpackages, etc.) from the generated configuration. In the next rule however, lookups from those classes that are directly in package `com.oracle.svm.tutorial` are included again. Finally, lookups from the `HostedHelper` class is excluded again. Each of these rules partially overrides the previous ones. For example, if the rules were in the reverse order, the exclusion of `com.oracle.svm.**` would be the last rule and would override all other rules.

For testing purposes, the built-in filter for Java class library lookups can be disabled by adding the `no-builtin-caller-filter` option, but the resulting configuration files are generally unsuitable for a native image build. Similarly, the built-in filter for Java VM-internal accesses based on heuristics can be disabled with `no-builtin-heuristic-filter` and will also generally lead to less usable configuration files. For example: `-agentlib:native-image-agent=no-builtin-caller-filter,no-builtin-heuristic-filter,config-output-dir=...`

### Access Filters

Unlike the caller-based filters described above, which filter dynamic accesses based on where they originate from, _access filters_ apply to the _target_ of the access. Therefore, access filters enable directly excluding packages and classes (and their members) from the generated configuration.

By default, all accessed classes (which also pass the caller-based filters and the built-in filters) are included in the generated configuration. Using the `access-filter-file` option, a custom filter file that follows the file structure described above can be added. The option can be specified more than once to add multiple filter files and can be combined with the other filter options. For example: `-agentlib:access-filter-file=/path/to/access-filter-file,caller-filter-file=/path/to/caller-filter-file,config-output-dir=...`

### Specifying Configuration Files as Native Image Arguments

A directory containing configuration files that is not part of the class path can be specified to `native-image` via `-H:ConfigurationFileDirectories=/path/to/config-dir/`. This directory must directly contain all four files: `jni-config.json`, `reflect-config.json`, `proxy-config.json` and `resource-config.json`. A directory with the same four configuration files that is on the class path, but not in `META-INF/native-image/`, can be provided via `-H:ConfigurationResourceRoots=path/to/resources/`. Both `-H:ConfigurationFileDirectories` and `-H:ConfigurationResourceRoots` can also take a comma-separated list of directories.

### Injecting the Agent via the Process Environment

Altering the `java` command line to inject the agent can prove to be difficult if the Java process is launched by an application or script file, or if Java is even embedded in an existing process. In that case, it is also possible to inject the agent via the `JAVA_TOOL_OPTIONS` environment variable. This environment variable can be picked up by multiple Java processes which run at the same time, in which case each agent must write to a separate output directory with `config-output-dir`. (The next section describes how to merge sets of configuration files.) In order to use separate paths with a single global `JAVA_TOOL_OPTIONS` variable, the agent's output path options support placeholders:
```shell
export JAVA_TOOL_OPTIONS="java -agentlib:native-image-agent=config-output-dir=/path/to/config-output-dir-{pid}-{datetime}/"
```

The `{pid}` placeholder is replaced with the process identifier, while `{datetime}` is replaced with the system date and time in UTC, formatted according to ISO 8601. For the above example, the resulting path could be: `/path/to/config-output-dir-31415-20181231T235950Z/`.

### The Configuration Tool

When using the agent in multiple processes at the same time as described in the previous section, `config-output-dir` is a safe option, but results in multiple sets of configuration files. The `native-image-configure` tool can be used to merge these configuration files. This tool must first be built with:
```shell
native-image --tool:native-image-configure
```

Then, the tool can be used to merge sets of configuration files as follows:
```shell
native-image-configure generate --input-dir=/path/to/config-dir-0/ --input-dir=/path/to/config-dir-1/ --output-dir=/path/to/merged-config-dir/
```

This command reads one set of configuration files from `/path/to/config-dir-0/` and another from `/path/to/config-dir-1/` and then writes a set of configuration files that contains both of their information to `/path/to/merged-config-dir/`.

An arbitrary number of `--input-dir` arguments with sets of configuration files can be specified. See `native-image-configure help` for all options.

### Trace Files

In the examples above, `native-image-agent` has been used to both keep track of the dynamic accesses in a Java VM and then to generate a set of configuration files from them. However, for a better understanding of the execution, the agent can also write a _trace file_ in JSON format that contains each individual access:
```shell
$JAVA_HOME/bin/java -agentlib:native-image-agent=trace-output=/path/to/trace-file.json ...
```

The `native-image-configure` tool can transform trace files to configuration files that can be used in native image builds. The following command reads and processes `trace-file.json` and generates a set of configuration files in directory `/path/to/config-dir/`:
```shell
native-image-configure generate --trace-input=/path/to/trace-file.json --output-dir=/path/to/config-dir/
```

### Interoperability

Although the agent is distributed with GraalVM, it uses the JVM Tool Interface (JVMTI) and can potentially be used with other JVMs that support JVMTI. In this case, it is necessary to provide the absolute path of the agent:
```shell
/path/to/some/java -agentpath:/path/to/graalvm/jre/lib/amd64/libnative-image-agent.so=<options> ...
```
