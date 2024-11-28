---
layout: docs
toc_group: debugging-and-diagnostics
link_title: Java Diagnostic Command
permalink: /reference-manual/native-image/debugging-and-diagnostics/jcmd/
---

# Java Diagnostic Command (jcmd) with Native Image

Native Image now supports the Java Diagnostic Command (`jcmd`), enabling users to interact with native executables using the same `jcmd` tool they use for Java applications.
This support complements existing Native Image monitoring features, including JDK Flight Recorder, heap dumps, and native memory tracking.

## Enabling `jcmd` Support

Support for `jcmd` is disabled by default and must be explicitly enabled at build time.
 
Use the `--enable-monitoring=jcmd` option to build a native executable with `jcmd` enabled.
```shell
native-image --enable-monitoring=jcmd YourApplication
```

When enabling support for `jcmd`, you may also want to include additional monitoring features, such as JDK Flight Recorder or heap dumps.
Including multiple monitoring features during the Native Image build process unlocks access to more diagnostic commands at runtime. 
For example:
```shell
native-image --enable-monitoring=jcmd,jfr,heapdump YourApplication
```

To use `jcmd` at runtime, start your native executable as usual and obtain its process ID (PID).
With the PID, you can use `jcmd` to connect to the running native application.
For example, to list the available commands for a specific executable, run: `jcmd <pid> help`.
```shell
jcmd 388454 help

388454:
The following commands are available:
GC.heap_dump
GC.run
JFR.start
JFR.stop
JFR.check
JFR.dump
Thread.dump_to_file
Thread.print
VM.command_line
VM.native_memory
VM.system_properties
VM.uptime
VM.version
help

For more information about a specific command use 'help <command>'.
```

You might find it useful to also enable the `jvmstat` monitoring feature so your native executable can be discovered and listed with `jcmd -l` or `jcmd` with no arguments provided.
```shell
native-image --enable-monitoring=jcmd,jvmstat YourApplication
```

```shell
jcmd -l
1455557 YourApplication
1455667 jdk.jcmd/sun.tools.jcmd.JCmd -l
```

## Supported Diagnostic Commands

The following key-value pairs are supported:

| Name                     | Included with `--enable-monitoring=`            | Description                                                                                        |
|--------------------------|-------------------------------------------------|----------------------------------------------------------------------------------------------------|
| Compiler.dump_code_cache | Only available with Truffle runtime compilation | Print information about all compiled methods in the code cache.                                    |
| GC.heap_dump             | heapdump                                        | Generate a HPROF format dump of the Java heap.                                                     |
| GC.run                   | Always available                                | Call `java.lang.System.gc()`.                                                                      |
| JFR.start                | jfr                                             | Starts a new JFR recording.                                                                        |
| JFR.stop                 | jfr                                             | Stops a JFR recording.                                                                             |
| JFR.check                | jfr                                             | Checks running JFR recording(s).                                                                   |
| JFR.dump                 | jfr                                             | Copies contents of a JFR recording to file. Either the name or the recording id must be specified. |
| Thread.dump_to_file      | Always available                                | Dump threads, with stack traces, to a file in plain text or JSON format.                           |
| Thread.print             | Always available                                | Print all threads with stacktraces.                                                                |
| VM.command_line          | Always available                                | Print the command line used to start this VM instance.                                             |
| VM.native_memory         | nmt                                             | Print native memory usage.                                                                         |
| VM.system_properties     | Always available                                | Print system properties.                                                                           |
| VM.uptime                | Always available                                | Print VM uptime.                                                                                   |
| VM.version               | Always available                                | Print JVM version information.                                                                     | 
| help                     | Always available                                | Display help information.                                                                          |

## Performance

Adding `jcmd` support to Native Image has minimal impact on performance when the application is idle.
However, the performance impact varies significantly depending on the diagnostic commands used and how frequently they are invoked.
For example, triggering multiple garbage collections will have a much greater overhead than dumping a single native memory tracking report.
You can use `jcmd <pid> help <command>` to print the help information for a specific command which also lists its expected performance impact.

## Limitations

Currently, this feature is not available on Windows.

### Further Reading

- [Debugging and Diagnostics](DebuggingAndDiagnostics.md)
- [Build and Run Native Executables with JFR](guides/build-and-run-native-executable-with-jfr.md)