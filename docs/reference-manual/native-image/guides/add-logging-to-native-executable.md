---
layout: ni-docs
toc_group: how-to-guides
link_title: Add Logging to a Native Executable
permalink: /reference-manual/native-image/guides/add-logging-to-native-executable/
redirect_from: /reference-manual/native-image/Logging/
---

# Add Logging to a Native Executable

By default, a native executable produced by Native Image supports logging via the `java.util.logging.*` API.

## Default Logging Configuration

The default logging configuration in a native executable is based on the _logging.properties_ file found in the JDK.
This file configures a `java.util.logging.ConsoleHandler` which will only show messages at the `INFO` level and above.
Custom logging configuration can be loaded either at build time or at run time as described below.

If you require additional logging handlers, you must register the corresponding classes for reflection.
For example, if you use `java.util.logging.FileHandler`, then provide the following reflection configuration in the _META-INF/native-image/reachability-metadata.json_ file:
```json
{
    "name" : "java.util.logging.FileHandler",
    "methods" : [
      { "name" : "<init>", "parameterTypes" : [] }
    ]
  }
```

For more details, see [Reflection Support](../ReachabilityMetadata.md#reflection).

The usage of the logger is shown in the following example:

1. Save the following Java code into a file named _LoggerRunTimeInit.java_, and compile it:

    ```java
    import java.io.IOException;
    import java.util.logging.Level;
    import java.util.logging.LogManager;
    import java.util.logging.Logger;
    
    public class LoggerRunTimeInit {
        public static void main(String[] args) throws IOException {
            LogManager.getLogManager().readConfiguration(LoggerRunTimeInit.class.getResourceAsStream("/logging.properties"));
            Logger logger = Logger.getLogger(LoggerRunTimeInit.class.getName());
            logger.log(Level.WARNING, "Danger, Will Robinson!");
        }
    }
    ```
    ```bash
    javac LoggerRunTimeInit.java
    ```

2. Download the [_logging.properties_](../assets/logging.properties) resource file and save it in the same directory as _LoggerRunTimeInit.java_.

3. Build and run the native executable
    ```shell
    native-image LoggerRunTimeInit -H:IncludeResources="logging.properties"
    ```
    ```shell
    ./loggerruntimeinit
    ```
    It should produce output that looks similar to:
    ```
    WARNING: Danger, Will Robinson! [Wed May 18 17:22:40 BST 2022]
    ```

    In this case, the _logging.properties_ file must be available for runtime processing and therefore needs to be registered in the _META-INF/native-image/reachability-metadata.json_ file.
    For more details on how to do this, see [Use of Resources in a Native Executable](../ReachabilityMetadata.md#resources).

### Related Documentation

* [Reachability Metadata: Reflection](../ReachabilityMetadata.md#reflection)
* [Native Image Build Configuration](../BuildConfiguration.md)