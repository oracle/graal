---
layout: docs
toc_group: metadata
link_title: Experimental Agent Options
permalink: /reference-manual/native-image/metadata/ExperimentalAgentOptions/
redirect_from: /reference-manual/native-image/ExperimentalAgentOptions/
---

# Experimental Agent Options

The `native-image-agent` tool has options which are currently experimental and might be enabled in future releases, but can also be changed or removed entirely.
These options are described here.

## Support For Predefined Classes

Native-image needs all classes to be known at image build time (a "closed-world assumption").
However, Java has support for loading new classes at runtime.
To emulate class loading, the agent can be told to trace dynamically loaded classes and save their bytecode for later use by the image builder.
This functionality can be enabled by adding `experimental-class-define-support` to the agent option string, e.g.: `-agentlib:native-image-agent=config-output-dir=config,experimental-class-define-support`
Apart from the standard configuration files, the agent will create an `agent-extracted-predefined-classes` directory in the configuration output directory and write bytecode of newly loaded classes on the go.
The configuration directory can then be used by image builder without additional tweaks,.
The classes will be loaded during the image build, but will not be initialized or made available to the application.
At runtime, if there is an attempt to load a class with the same name and bytecodes as one of the classes encountered during tracing, the predefined class will be supplied to the application.

### Known Limitations

 - Native images support "loading" a predefined class only once per execution, by just a single class loader.
 - Predefined classes are initialized when they are "loaded" at runtime and cannot be initialized at build time.
 - The agent collects all classes which are not loaded by one of the Java VM's built-in class loaders (with some exceptions), that is, from the class path or module path. This includes classes loaded by any custom class loaders.
 - Classes that are generated with varying data in their name or bytecodes, such as sequential or random numbers or timestamps, can generally not be matched to predefined classes at runtime. In these cases, the way such classes are generated needs to be adjusted.

## Printing Configuration With Origins

For debugging, it may be useful to know the origin of certain configuration entries.
By supplying `experimental-configuration-with-origins` to the agent option string, the agent will output configuration files with configuration entries broken down to the calling context (stack trace) they originate from in tree form.
This option should be used in conjunction with `config-output-dir=<path>` to tell the agent where to output the configuration files.
An example agent option string: `-agentlib:native-image-agent=config-output-dir=config-with-origins/,experimental-configuration-with-origins`

## Omitting Configuration From The Agent's Output

The agent can omit traced configuration entries present in existing configuration files.
There are two ways to specify these existing configuration files:
 - By using configuration files from the class path or module path. When `experimental-omit-config-from-classpath` is added to the agent option string, the class path and module path of the running application are scanned for `META-INF/native-image/**/*.json` configuration files.
 - By explicitly pointing the agent to an existing configuration file directory using `config-to-omit=<path>`.

## Generating Conditional Configuration Using the Agent

The agent can, using a heuristic, generate configuration with reachability conditions on user specified classes.
The agent will track configuration origins and try to deduce the conditions automatically.
User classes are specified via an agent filter file (for more information on the format, see [more about the agent](AutomaticMetadataCollection.md#caller-based-filters)).
Additionally, the resulting configuration can further be filtered using another filter file.

Currently, this feature supports two modes:
 1. Generating conditional configuration in a single run with the agent.
 2. Generating conditional configuration from multiple runs with the agent and finally merging the collected data.

### Generating Conditional Configuration During an Agent Run

To enable this mode, add `experimental-conditional-config-filter-file=<path>` to the agent's command line, where `<path>` points to an agent filter file.
Classes that are considered included by this filter will be designated as user code classes.
To further filter the generated configuration, you can use `conditional-config-class-filter-file=<path>`, where `<path>` is a path to an agent filter file.

### Generating Conditional Configuration From Multiple Agent Runs

Conditional configuration can be generated from multiple agent runs that reach different code paths in the application.
Each agent run produces configuration with metadata. `native-image-configure` is then used to merge the collected data and produce a conditional configuration.
To run the agent in this mode, add `experimental-conditional-config-part` to the agent's command line.
Once all the agent runs have finished, you can generate a conditional configuration by invoking:
```shell
native-image-configure generate-conditional --user-code-filter=<path-to-filter-file> --class-name-filter=<path-to-filter-file> --input-dir=<path-to-agent-run-output-1> --input-dir=<path-to-agent-run-ouput-2> ... --output-dir=<path-to-resulting-conditional-config>
```
where:
 - `--user-code-filter=<path-to-filter-file>`: path to an agent filter file that specifies user classes
 - (optional) `--class-name-filter=<path-to-filter-file>`: path to an agent filter file that further filters the generated config

### The Underlying Heuristics

Conditions are generated using the call tree of the application. The heuristics work as follows:
 1. For each unique method, create a list of all nodes in the call tree that correspond to the method
 2. For each unique method, if the method has more than one call node in the tree:
  - Find common configuration across all call nodes of that method
  - For each call node of the method, propagate configuration that isn't common across these calls to the caller node
 3. Repeat 2. until an iteration produced no changes in the call tree.
 4. For each node that contains configuration, generate a conditional configuration entry with the method's class as the condition.

The primary goal of this heuristic is to attempt to find where a method creates different configuration entries depending on the caller (for example, a method that wraps `Class.forName` calls.)
This implies that the heuristic will not work well for code that generates configuration through a different dependency (for example, same method returns calls `Class.forName` with different class parameters depending on a system property).

### Further Reading

* [Reachability Metadata](ReachabilityMetadata.md)
* [Metadata Collection with the Tracing Agent](AutomaticMetadataCollection.md)