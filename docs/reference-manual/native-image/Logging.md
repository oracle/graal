---
layout: docs
toc_group: native-image
link_title: Logging on Native Image
permalink: /reference-manual/native-image/Logging/
---
# Logging in Native Image

By default, Native Image supports logging via the `java.util.logging.*` API.

## Default Logging Configuration

The default logging configuration in a native executable is based on the `logging.properties` file found in the JDK.
This configures a `java.util.logging.ConsoleHandler` which will only show messages at the `INFO` level and above.
Custom logging configuration can be loaded either at executable build time or at runtime as described below.

Note that if additional logging handlers are used, the corresponding classes must be registered for reflection.
For example, if `java.util.logging.FileHandler` is used then the following reflection configuration is necessary:
```json
{
    "name" : "java.util.logging.FileHandler",
    "methods" : [
      { "name" : "<init>", "parameterTypes" : [] },
    ]
  }
```
For more details, see [Reflection Support](Reflection.md).

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
2. Download the [_logging.properties_](assets/logging.properties) resource file and save it in the same directory as _BuildTimeLoggerInit.java_.

3. Build and run the native executable

    ```shell
    native-image BuildTimeLoggerInit --initialize-at-build-time=BuildTimeLoggerInit
    ./buildtimeloggerinit
    ```
The _logging.properties_ file is processed at build time.
It does not need to be included in the native executable, therefore reducing the size of the executable file.

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

2. Download the [_logging.properties_](assets/logging.properties) resource file and save it in the same directory as _RuntimeLoggerInit.java_.

3. Build and run the native executable

    ```shell
    native-image RuntimeLoggerInit -H:IncludeResources="logging.properties"
    ./runtimeloggerinit
    ```

In this case, the _logging.properties_ file needs to be available for runtime processing and it must be included in the executable via the `-H:IncludeResources=logging.properties` option. For more details on this option, see  [accessing resources at runtime](Resources.md).

## Related Documentation
* [Accessing Resources in Native Images](Resources.md)
* [Class Initialization in Native Image](ClassInitialization.md)
* [Native Image Build Configuration](BuildConfiguration.md)
* [Reflection Use in Native Images](Reflection.md)
