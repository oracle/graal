---
layout: ni-docs
toc_group: metadata
link_title: Collect Metadata with the Tracing Agent
permalink: /reference-manual/native-image/metadata/AutomaticMetadataCollection/
redirect_from: /$version/reference-manual/native-image/Agent/
---

# Collect Metadata with the Tracing Agent

The Native Image tool relies on the static analysis of an application's reachable code at runtime. 
However, the analysis cannot always completely predict all usages of the Java Native Interface (JNI), Java Reflection, Dynamic Proxy objects, or class path resources. 
Undetected usages of these dynamic features must be provided to the `native-image` tool in the form of [metadata](ReachabilityMetadata.md) (precomputed in code or as JSON configuration files).

Here you will find information how to automatically collect metadata for an application and write JSON configuration files.
To learn how to compute dynamic feature calls in code, see [Reachability Metadata](ReachabilityMetadata.md#computing-metadata-in-code).

### Table of Contents

* [Tracing Agent](#tracing-agent)
* [Conditional Metadata Collection](#conditional-metadata-collection)
* [Agent Advanced Usage](#agent-advanced-usage)
* [Native Image Configure Tool](#native-image-configure-tool)

## Tracing Agent

GraalVM provides a **Tracing Agent** to easily gather metadata and prepare configuration files. 
The agent tracks all usages of dynamic features during application execution on a regular Java VM.

Enable the agent on the command line with the `java` command from the GraalVM JDK:
```shell
$JAVA_HOME/bin/java -agentlib:native-image-agent=config-output-dir=/path/to/config-dir/ ...
```

>Note: `-agentlib` must be specified _before_ a `-jar` option or a class name or any application parameters as part of the `java` command.

When run, the agent looks up classes, methods, fields, resources for which the `native-image` tool needs additional information.
When the application completes and the JVM exits, the agent writes metadata to JSON files in the specified output directory (`/path/to/config-dir/`).

It may be necessary to run the application more than once (with different execution paths) for improved coverage of dynamic features.
The `config-merge-dir` option adds to an existing set of configuration files, as follows:
```shell
$JAVA_HOME/bin/java -agentlib:native-image-agent=config-merge-dir=/path/to/config-dir/ ...                                                              ^^^^^
```

The agent also provides the following options to write metadata on a periodic basis:
- `config-write-period-secs=n`: writes metadata files every `n` seconds; `n` must be greater than 0.
- `config-write-initial-delay-secs=n`: waits `n` seconds before first writing metadata; defaults to `1`.

For example:
```shell
$JAVA_HOME/bin/java -agentlib:native-image-agent=config-output-dir=/path/to/config-dir/,config-write-period-secs=300,config-write-initial-delay-secs=5 ...
```

The above command will write metadata files to `/path/to/config-dir/` every 300 seconds after an initial delay of 5 seconds.

It is advisable to manually review the generated configuration files.
Because the agent observes only executed code, the application input should cover as many code paths as possible.

The generated configuration files can be supplied to the `native-image` tool by placing them in a `META-INF/native-image/` directory on the class path. 
This directory (or any of its subdirectories) is searched for files with the names `jni-config.json`, `reflect-config.json`, `proxy-config.json`, `resource-config.json`, `predefined-classes-config.json`, `serialization-config.json` which are then automatically included in the build process. 
Not all of those files must be present. 
When multiple files with the same name are found, all of them are considered.

To test the agent collecting metadata on an example application, go to the [Build a Native Executable with Reflection](guides/build-with-reflection.md) guide.

## Conditional Metadata Collection

The agent can deduce metadata conditions based on their usage in executed code.
Conditional metadata is mainly aimed towards library maintainers with the goal of reducing overall footprint.

To collect conditional metadata with the agent, see [Conditional Metadata Collection](ExperimentalAgentOptions.md#generating-conditional-configuration-using-the-agent).

## Agent Advanced Usage

### Caller-based Filters

By default, the agent filters dynamic accesses which Native Image supports without configuration.
The filter mechanism works by identifying the Java method performing the access, also referred to as _caller_ method, and matching its declaring class against a sequence of filter rules.
The built-in filter rules exclude dynamic accesses which originate in the JVM, or in parts of a Java class library directly supported by Native Image (such as `java.nio`) from the generated configuration files.
Which item (class, method, field, resource, etc.) is being accessed is not relevant for filtering.

In addition to the built-in filter, custom filter files with additional rules can be specified using the `caller-filter-file` option.
For example: `-agentlib:caller-filter-file=/path/to/filter-file,config-output-dir=...`

Filter files have the following structure:
```json
{ "rules": [
    {"excludeClasses": "com.oracle.svm.**"},
    {"includeClasses": "com.oracle.svm.tutorial.*"},
    {"excludeClasses": "com.oracle.svm.tutorial.HostedHelper"}
  ],
  "regexRules": [
    {"includeClasses": ".*"},
    {"excludeClasses": ".*\\$\\$Generated[0-9]+"}
  ]
}
```

The `rules` section contains a sequence of rules.
Each rule specifies either `includeClasses`, which means that lookups originating in matching classes will be included in the resulting configuration, or `excludeClasses`, which excludes lookups originating in matching classes from the configuration.
Each rule defines a pattern to match classes. The pattern can end in `.*` or `.**`, interpreted as follows:
    - `.*` matches all classes in the package and only that package;
    - `.**` matches all classes in the package as well as in all subpackages at any depth. 
Without `.*` or `.**`, the rule applies only to a single class with the qualified name that matches the pattern.
All rules are processed in the sequence in which they are specified, so later rules can partially or entirely override earlier ones.
When multiple filter files are provided (by specifying multiple `caller-filter-file` options), their rules are chained together in the order in which the files are specified.
The rules of the built-in caller filter are always processed first, so they can be overridden in custom filter files.

In the example above, the first rule excludes lookups originating in all classes from package `com.oracle.svm` and from all of its subpackages (and their subpackages, etc.) from the generated metadata.
In the next rule however, lookups from those classes that are directly in package `com.oracle.svm.tutorial` are included again.
Finally, lookups from the `HostedHelper` class is excluded again. Each of these rules partially overrides the previous ones.
For example, if the rules were in the reverse order, the exclusion of `com.oracle.svm.**` would be the last rule and would override all other rules.

The `regexRules` section also contains a sequence of rules.
Its structure is the same as that of the `rules` section, but rules are specified as regular expression patterns which are matched against the entire fully qualified class identifier.
The `regexRules` section is optional.
If a `regexRules` section is specified, a class will be considered included if (and only if) both `rules` and `regexRules` include the class and neither of them exclude it.
With no `regexRules` section, only the `rules` section determines whether a class is included or excluded.

For testing purposes, the built-in filter for Java class library lookups can be disabled by adding the `no-builtin-caller-filter` option, but the resulting metadata files are generally unsuitable for the build.
Similarly, the built-in filter for Java VM-internal accesses based on heuristics can be disabled with `no-builtin-heuristic-filter` and will also generally lead to less usable metadata files.
For example: `-agentlib:native-image-agent=no-builtin-caller-filter,no-builtin-heuristic-filter,config-output-dir=...`

### Access Filters

Unlike the caller-based filters described above, which filter dynamic accesses based on where they originate, _access filters_ apply to the _target_ of the access.
Therefore, access filters enable directly excluding packages and classes (and their members) from the generated configuration.

By default, all accessed classes (which also pass the caller-based filters and the built-in filters) are included in the generated configuration.
Using the `access-filter-file` option, a custom filter file that follows the file structure described above can be added.
The option can be specified more than once to add multiple filter files and can be combined with the other filter options, for example, `-agentlib:access-filter-file=/path/to/access-filter-file,caller-filter-file=/path/to/caller-filter-file,config-output-dir=...`.

### Specify Configuration Files as Arguments

A directory containing configuration files that is not part of the class path can be specified to `native-image` via `-H:ConfigurationFileDirectories=/path/to/config-dir/`.
This directory must directly contain all files: `jni-config.json`, `reflect-config.json`, `proxy-config.json` and `resource-config.json`.
A directory with the same metadata files that is on the class path, but not in `META-INF/native-image/`, can be provided via `-H:ConfigurationResourceRoots=path/to/resources/`.
Both `-H:ConfigurationFileDirectories` and `-H:ConfigurationResourceRoots` can also take a comma-separated list of directories.

### Injecting the Agent via the Process Environment

Altering the `java` command line to inject the agent can prove to be difficult if the Java process is launched by an application or script file, or if Java is even embedded in an existing process.
In that case, it is also possible to inject the agent via the `JAVA_TOOL_OPTIONS` environment variable.
This environment variable can be picked up by multiple Java processes which run at the same time, in which case each agent must write to a separate output directory with `config-output-dir`.
(The next section describes how to merge sets of configuration files.)
In order to use separate paths with a single global `JAVA_TOOL_OPTIONS` variable, the agent's output path options support placeholders:
```shell
export JAVA_TOOL_OPTIONS="-agentlib:native-image-agent=config-output-dir=/path/to/config-output-dir-{pid}-{datetime}/"
```

The `{pid}` placeholder is replaced with the process identifier, while `{datetime}` is replaced with the system date and time in UTC, formatted according to ISO 8601. 
For the above example, the resulting path could be: `/path/to/config-output-dir-31415-20181231T235950Z/`.

### Trace Files

In the examples above, `native-image-agent` has been used to both keep track of the dynamic accesses on a JVM and then to generate a set of configuration files from them.
However, for a better understanding of the execution, the agent can also write a _trace file_ in JSON format that contains each individual access:
```shell
$JAVA_HOME/bin/java -agentlib:native-image-agent=trace-output=/path/to/trace-file.json ...
```

The `native-image-configure` tool can transform trace files to configuration files.
The following command reads and processes `trace-file.json` and generates a set of configuration files in the directory `/path/to/config-dir/`:
```shell
native-image-configure generate --trace-input=/path/to/trace-file.json --output-dir=/path/to/config-dir/
```

### Interoperability

The agent uses the JVM Tool Interface (JVMTI) and can potentially be used with other JVMs that support JVMTI.
In this case, it is necessary to provide the absolute path of the agent:
```shell
/path/to/some/java -agentpath:/path/to/graalvm/jre/lib/amd64/libnative-image-agent.so=<options> ...
```

### Experimental Options

The agent has options which are currently experimental and might be enabled in future releases, but can also be changed or removed entirely.
See the [ExperimentalAgentOptions.md](ExperimentalAgentOptions.md) guide.

## Native Image Configure Tool

When using the agent in multiple processes at the same time as described in the previous section, `config-output-dir` is a safe option, but it results in multiple sets of configuration files.
The `native-image-configure` tool can be used to merge these configuration files.
This tool must first be built with:
```shell
native-image --macro:native-image-configure-launcher
```

Then, the tool can be used to merge sets of configuration files as follows:
```shell
native-image-configure generate --input-dir=/path/to/config-dir-0/ --input-dir=/path/to/config-dir-1/ --output-dir=/path/to/merged-config-dir/
```

This command reads one set of configuration files from `/path/to/config-dir-0/` and another from `/path/to/config-dir-1/` and then writes a set of configuration files that contains both of their information to `/path/to/merged-config-dir/`.
An arbitrary number of `--input-dir` arguments with sets of configuration files can be specified. See `native-image-configure help` for all options.

### Further Reading

* [Build a Native Executable with Reflection](guides/build-with-reflection.md)
* [Reachability Metadata](ReachabilityMetadata.md)
* [Experimental Agent Options](ExperimentalAgentOptions.md)