# Assisted Configuration of Native Image Builds

_NOTE: the described agent is currently only supported on Linux._

Native images are built ahead of runtime and their build relies on a static analysis of which code will be reachable. However, this analysis cannot always completely predict all usages of the Java Native Interface (JNI), Java reflection, dynamic proxy objects (`java.lang.reflect.Proxy`) or class-path resources (`Class.getResource`). Undetected usages of these dynamic features need to be provided to the `native-image` tool in the form of configuration files.

In order to make preparing these configuration files easier and more convenient, GraalVM provides an _agent_ that traces all usages of dynamic features of an execution on a regular Java VM. It can be enabled on the command line of the GraalVM `java` command:

```
/path/to/graalvm/bin/java -agentlib:native-image-agent=output=/path/to/trace-file.json ...
```

During execution, the agent interfaces with the Java VM to intercept all calls that look up classes, methods, fields, resources or request proxy accesses and writes a trace file with the specified path.

Next, the `native-image-configure` tool can transform the trace file to configuration files for native image builds. This tool itself must first be built with the command `native-image --tool:native-image-configure`. Then, the tool can be used as follows:

```
native-image-configure process-trace                          \
                       --jni-output jni-config.json           \
                       --reflect-output reflect-config.json   \
                       --proxy-output proxy-config.json       \
                       --resources-output resource-args.txt   \
                       /path/to/trace-file.json
```

This invocation reads and processes `trace-file.json` and generates the files `jni-config.json`, `reflect-config.json`, `proxy-config.json` and `resource-params.txt` (but not all these arguments must always be specified, see `native-image-configure help`). The `.json` files are stand-alone configuration files, while `resource-args.txt` contains command-line arguments to `native-image`.

It is advisable to manually review the generated configuration files. Because the agent observes only a single execution, the resulting configurations can be missing elements that are used in code paths that were not executed. It could also make sense to simplify the generated configurations to make any future manual maintenance easier.

Failed lookups of classes, methods, fields or resources are recorded in the trace, but are not included in the generated configurations by default. Likewise, accesses from inside the Java class library or the Java VM (such as in `java.nio`) are filtered by default. For testing purposes, filtering can be disabled by passing `--no-filter` to `native-image-configure`, but the generated configuration files are likely unsuitable for a build.

Finally, the generated configuration files can be used in a `native-image` build as follows:

```
native-image -H:JNIConfigurationFiles=jni-config.json                  \
             -H:ReflectionConfigurationFiles=reflect-config.json       \
             -H:DynamicProxyConfigurationFiles=proxy-config.json       \
             -H:IncludeResources=some/resource/on/classpath.txt        \
             -H:IncludeResources=some/other/resource/on/classpath.txt  \
             ...
```