---
layout: ni-docs
toc_group: how-to-guides
link_title: Add Logging to a Native Executable
permalink: /reference-manual/native-image/guides/add-logging-to-native-executable/
---

# Add Logging to a Native Executable

By default, a native executable produced by Native Image supports logging via the `java.util.logging.*` API.

## Default Logging Configuration

The default logging configuration in a native executable is based on the `logging.properties` file found in the JDK.
This file configures a `java.util.logging.ConsoleHandler` which will only show messages at the `INFO` level and above.
Custom logging configuration can be loaded either at executable build time or at runtime as described below.

If you require additional logging handlers, you must register the corresponding classes for reflection.
For example, if you use `java.util.logging.FileHandler` then provide the following reflection configuration:
```json
{
    "name" : "java.util.logging.FileHandler",
    "methods" : [
      { "name" : "<init>", "parameterTypes" : [] },
    ]
  }
```
For more details, see [Reflection Support](../Reflection.md).

## Build-Time Logger Initialization

The logger can be initialized at executable build time with a custom _logging.properties_ configuration file, as illustrated in following example.

1. Save the following Java code into a file named _BuildTimeLoggerInit.java_, then compile it using `javac`:
    ```java
    import java.io.IOException;
    import java.util.logging.Level;
    import java.util.logging.LogManager;
    import java.util.logging.Logger;

    public class BuildTimeLoggerInit {
        private static final Logger LOGGER;
        static {
            try {
                LogManager.getLogManager().readConfiguration(BuildTimeLoggerInit.class.getResourceAsStream("/logging.properties"));
            } catch (IOException | SecurityException | ExceptionInInitializerError ex) {
                Logger.getLogger(BuildTimeLoggerInit.class.getName()).log(Level.SEVERE, "Failed to read logging.properties file", ex);
            }
            LOGGER = Logger.getLogger(BuildTimeLoggerInit.class.getName());
        }

        public static void main(String[] args) throws IOException {
            LOGGER.log(Level.WARNING, "Danger, Will Robinson!");
        }
    } 
    ```
2. Download the [_logging.properties_](../assets/logging.properties) resource file and save it in the same directory as _BuildTimeLoggerInit.java_.

3. Build and run the native executable

    ```shell
    native-image BuildTimeLoggerInit --initialize-at-build-time=BuildTimeLoggerInit
    ./buildtimeloggerinit
    ```
  
    It should produce output that looks similar to
    ```
    WARNING: Danger, Will Robinson! [Wed May 18 17:20:39 BST 2022]
    ```

    This demonstrates that the _logging.properties_ file is processed at when the executable is built.
    The file does not need to be included in the native executable and reduces the size of the resulting executable file.

   `LoggerHolder.LOGGER` is also initialized at build time and is readily available at runtime, therefore improving the startup time. 
   Unless your application needs to process a custom _logging.properties_ configuration file at runtime, this approach is recommended.

## Runtime Logger Initialization

The logger can also be initialized at runtime, as shown in the following example.

1. Save the following Java code into a file named _RuntimeLoggerInit.java_, then compile it using `javac`:

    ```java
    import java.io.IOException;
    import java.util.logging.Level;
    import java.util.logging.LogManager;
    import java.util.logging.Logger;
    
    public class RuntimeLoggerInit {
        public static void main(String[] args) throws IOException {
            LogManager.getLogManager().readConfiguration(RuntimeLoggerInit.class.getResourceAsStream("/logging.properties"));
            Logger logger = Logger.getLogger(RuntimeLoggerInit.class.getName());
            logger.log(Level.WARNING, "Danger, Will Robinson!");
        }
    }
    ```

2. Download the [_logging.properties_](../assets/logging.properties) resource file and save it in the same directory as _RuntimeLoggerInit.java_.

3. Build and run the native executable

    ```shell
    native-image RuntimeLoggerInit -H:IncludeResources="logging.properties"
    ./runtimeloggerinit
    ```

    It should produce output that looks similar to

    ```
    WARNING: Danger, Will Robinson! [Wed May 18 17:22:40 BST 2022]
    ```


    In this case, the _logging.properties_ file needs to be available for runtime processing and it must be included in the executable via the `-H:IncludeResources=logging.properties` option. For more details on this option, see [Use of Resources in a Native Executable](../Resources.md).

### Related Documentation

* [Reachability Metadata: Reflection](../ReachabilityMetadata.md#reflection)
* [Native Image Build Configuration](../BuildConfiguration.md)
