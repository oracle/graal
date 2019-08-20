# Assisted Configuration of Native Image Builds

Native images are built ahead of runtime and their build relies on a static analysis of which code will be reachable. However, this analysis cannot always completely predict all usages of the Java Native Interface (JNI), Java reflection, dynamic proxy objects (`java.lang.reflect.Proxy`) or class path resources (`Class.getResource`). Undetected usages of these dynamic features need to be provided to the `native-image` tool in the form of configuration files.

In order to make preparing these configuration files easier and more convenient, GraalVM provides an _agent_ that tracks all usages of dynamic features of an execution on a regular Java VM. It can be enabled on the command line of the GraalVM `java` command:
```
/path/to/graalvm/bin/java -agentlib:native-image-agent=config-output-dir=/path/to/config-dir/ ...
```

During execution, the agent interfaces with the Java VM to intercept all calls that look up classes, methods, fields, resources or request proxy accesses. The agent then generates the files `jni-config.json`, `reflect-config.json`, `proxy-config.json` and `resource-config.json` in the specified output directory, which is `/path/to/config-dir/` in the example above. The generated files are stand-alone configuration files in _JSON_ format which contain all intercepted dynamic accesses.

It can be necessary to run the target application more than once with different inputs to trigger separate execution paths for a better coverage of dynamic accesses. The agent supports this with the `config-merge-dir` option which adds the intercepted accesses to an existing set of configuration files:
```
/path/to/graalvm/bin/java -agentlib:native-image-agent=config-merge-dir=/path/to/config-dir/ ...
                                                              ^^^^^
```

If the specified target directory or configuration files in it are missing when using `config-merge-dir`, the agent creates them and prints a warning.

It is advisable to manually review the generated configuration files. Because the agent observes only code that was executed, the resulting configurations can be missing elements that are used in other code paths. It could also make sense to simplify the generated configurations to make any future manual maintenance easier.

The generated configuration files can be supplied to the `native-image` tool by placing them in a `META-INF/native-image/` directory on the class path, for example, in a JAR file used in the image build. This directory (or any of its subdirectories) is searched for files with the names `jni-config.json`, `reflect-config.json`, `proxy-config.json` and `resource-config.json`, which are then automatically included in the build. Not all of those files must be present. When multiple files with the same name are found, all of them are included.

## Advanced Usage

A directory containing configuration files that is not part of the class path can be specified to `native-image` via `-H:ConfigurationFileDirectories=/path/to/config-dir/`. This directory must directly contain all four files `jni-config.json`, `reflect-config.json`, `proxy-config.json` and `resource-config.json`. A directory with the same four configuration files that is on the class path, but not in `META-INF/native-image/`, can be provided via `-H:ConfigurationResourceRoots=path/to/resources/`. Both `-H:ConfigurationFileDirectories` and `-H:ConfigurationResourceRoots` can also take a comma-separated list of directories.

Altering the `java` command line to inject the agent can prove to be difficult if the Java process is launched by an application or script file or if Java is even embedded in an existing process. In that case, it is also possible to inject the agent via the `JAVA_TOOL_OPTIONS` environment variable. This environment variable can be picked up by multiple Java processes which run at the same time, in which case each agent must write to a separate output directory with `config-output-dir`. (The next section describes how to merge sets of configuration files.) In order to use separate paths with a single global `JAVA_TOOL_OPTIONS` variable, the agent's output path options support placeholders:
```
export JAVA_TOOL_OPTIONS="java -agentlib:native-image-agent=config-output-dir=/path/to/config-output-dir-{pid}-{datetime}/"
```

The `{pid}` placeholder is replaced with the process identifier, while `{datetime}` is replaced with the system date and time in UTC, formatted according to ISO 8601. For the above example, the resulting path could be: `/path/to/config-output-dir-31415-20181231T235950Z/`.

The agent also tracks failed lookups of classes, methods, fields or resources, but by default, it does not include them in the generated configuration files. Likewise, it filters accesses from inside the Java class library or the Java VM (such as in `java.nio`) by default. For testing purposes, filtering can be disabled by adding the `no-filter` option, but the resulting configuration files are generally unsuitable for a native image build. For example: `-agentlib:native-image-agent=no-filter,config-output-dir=...`

### The Configuration Tool

When using the agent in multiple processes at the same time as described in the previous section, `config-output-dir` is a safe option, but results in multiple sets of configuration files. The `native-image-configure` tool can be used to merge these configuration files. This tool must first be built with:
```
native-image --tool:native-image-configure
```

Then, the tool can be used to merge sets of configuration files as follows:
```
native-image-configure generate --input-dir=/path/to/config-dir-0/ --input-dir=/path/to/config-dir-1/ --output-dir=/path/to/merged-config-dir/
```

This command reads one set of configuration files from `/path/to/config-dir-0/` and another from `/path/to/config-dir-1/` and then writes a set of configuration files that contains both of their information to `/path/to/merged-config-dir/`.

An arbitrary number of `--input-dir` arguments with sets of configuration files can be specified. See `native-image-configure help` for all options.

### Trace Files

In the examples above, `native-image-agent` has been used to both keep track of the dynamic accesses in a Java VM and then to generate a set of configuration files from them. However, for a better understanding of the execution, the agent can also write a _trace file_ in JSON format that contains each individual access:
```
/path/to/graalvm/bin/java -agentlib:native-image-agent=trace-output=/path/to/trace-file.json ...
```

The `native-image-configure` tool can transform trace files to configuration files that can be used in native image builds. The following command reads and processes `trace-file.json` and generates a set of configuration files in directory `/path/to/config-dir/`:
```
native-image-configure generate --trace-input=/path/to/trace-file.json --output-dir=/path/to/config-dir/
```

### Interoperability

Although the agent is distributed with Graal VM, it uses the Java VM Tool Interface (JVMTI) and can potentially be used with other Java VMs that support JVMTI. In this case, it is necessary to provide the absolute path of the agent:
```
/path/to/some/java -agentpath:/path/to/graalvm/jre/lib/amd64/libnative-image-agent.so=<options> ...
```
