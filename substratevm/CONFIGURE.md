# Assisted Configuration of Native Image Builds

_NOTE: the described agent is currently only supported on Linux._

Native images are built ahead of runtime and their build relies on a static analysis of which code will be reachable. However, this analysis cannot always completely predict all usages of the Java Native Interface (JNI), Java reflection, dynamic proxy objects (`java.lang.reflect.Proxy`) or class-path resources (`Class.getResource`). Undetected usages of these dynamic features need to be provided to the `native-image` tool in the form of configuration files.

In order to make preparing these configuration files easier and more convenient, GraalVM provides an _agent_ that traces all usages of dynamic features of an execution on a regular Java VM. It can be enabled on the command line of the GraalVM `java` command:

```
/path/to/graalvm/bin/java -agentlib:native-image-agent=trace-output=/path/to/trace-file.json ...
```

During execution, the agent interfaces with the Java VM to intercept all calls that look up classes, methods, fields, resources or request proxy accesses and writes a trace file with the specified path.

Next, the `native-image-configure` tool can transform the trace file to configuration files for native image builds. This tool itself must first be built with:

```
native-image --tool:native-image-configure
```

Then, the tool generates configuration files from the trace file with the following command:

```
native-image-configure process-trace --output-dir=/path/to/config-dir/ /path/to/trace-file.json
```

This invocation reads and processes `trace-file.json` and, in the specified directory `/path/to/config-dir/`, generates the files `jni-config.json`, `reflect-config.json`, `proxy-config.json` and `resource-config.json`. These are stand-alone configuration files in _JSON_ format which contain all dynamic accesses from the trace file.

It is possible to specify multiple trace files to `native-image-configure`, which will then generate configuration files that include the dynamic accesses from all trace files. This can be used to improve the overall coverage of the configuration files by running the target application several times with multiple inputs that trigger different execution paths.

Failed lookups of classes, methods, fields or resources are recorded in the trace, but are not included in the generated configurations by default. Likewise, accesses from inside the Java class library or the Java VM (such as in `java.nio`) are filtered by default. For testing purposes, filtering can be disabled by passing `--no-filter` to `native-image-configure`, but the generated configuration files are generally unsuitable for a native image build.

It is advisable to manually review the generated configuration files. Because the agent observes only code that was executed, the resulting configurations can be missing elements that are used in other code paths. It could also make sense to simplify the generated configurations to make any future manual maintenance easier.

Finally, the generated configuration files can be used in a `native-image` build as follows:

```
native-image -H:ConfigurationFileDirectories=/path/to/config-dir/ ...
```

Providing `ConfigurationFileDirectories` will search the specified directory (or multiple directories) for the files `jni-config.json`, `reflect-config.json`, `proxy-config.json` and `resource-config.json` and include these configuration files in the build.

## Individual Configuration Files

The configuration files to be generated can also be specified individually, and not all types of configuration files must be generated (see `native-image-configure help`). The command `native-image-configure process-trace --output-dir=/path/to/config-dir/` is equivalent to the following:

```
native-image-configure process-trace                                               \
                       --jni-output=/path/to/config-dir/jni-config.json            \
                       --reflect-output=/path/to/config-dir/reflect-config.json    \
                       --proxy-output=/path/to/config-dir/proxy-config.json        \
                       --resource-output=/path/to/config-dir/resource-config.json  \
                       /path/to/trace-file.json
```

Similarly, configuration files can be specified individually instead of providing an entire directory:
```
native-image -H:JNIConfigurationFiles=/path/to/config-dir/jni-config.json             \
             -H:ReflectionConfigurationFiles=/path/to/config-dir/reflect-config.json  \
             -H:DynamicProxyConfigurationFiles=/path/to/config-dir/proxy-config.json  \
             -H:ResourceConfigurationFiles=/path/to/config-dir/resource-config.json   \
             ...
```

Moreover, the above options (as well as `ConfigurationFileDirectories`) can take multiple, comma-separated paths.

Instead of file paths, configuration files can also be provided via Java resources on the classpath, for example from within a _JAR_ file. This is done via the options `-H:ConfigurationResourceRoots=path/to/resources/` and/or individually with `-H:JNIConfigurationResources=path/to/resources/jni-config.json`, `-H:ReflectionConfigurationResources=...`, `-H:DynamicProxyConfigurationResources=...` and `-H:ResourceConfigurationResources=...`.

## Advanced Agent Usage

Altering the `java` command line to inject the agent can prove difficult if the Java process is launched by an application or script file or if Java is even embedded in an existing process. In that case, it is also possible to inject the agent via the `JAVA_TOOL_OPTIONS` environment variable:

```
export JAVA_TOOL_OPTIONS="-agentlib:native-image-agent=trace-output=/path/to/trace-file.json"
```

When running multiple Java instances with the agent at the same time, each of them must write to a different trace file. Placeholders in the agent's `trace-output` option can be used to generate unique file names, which is particularly useful in combination with `JAVA_TOOL_OPTIONS`:

```
java -agentlib:native-image-agent=trace-output=/path/to/trace-{pid}-{datetime}.json ...
```

The `{pid}` placeholder is replaced with the process identifier, while `{datetime}` is replaced with the system date and time in UTC, formatted according to ISO 8601. For the above example, the resulting path could be: `/path/to/trace-31415-20181231T235950Z.json`.

While the agent is distributed with Graal VM, it uses the Java VM Tool Interface (JVMTI) and can therefore be used with other Java VMs that support this interface. In this case, it is generally necessary to provide the absolute path of the agent:
```
/path/to/some/java -agentpath:/path/to/graalvm/jre/lib/amd64/libnative-image-agent.so=<options> ...`
```
