# Logging in Native Image

Out of the box, Native Image supports logging using the `java.util.logging.*` API.

## Default Logging Configuration

The logging configuration built in a native image by default is based on the `logging.properties` file found in the JDK.
This configures a `java.util.logging.ConsoleHandler` which will only show messages at the `INFO` and above levels.
Custom logging configuration can be loaded either at image build time or at run time as described below.

Note that if additional logging handlers are used, the corresponding classes need to be registered for reflection.
For example, if `java.util.logging.FileHandler` is used then the following reflection configuration is necessary:
```json
{
    "name" : "java.util.logging.FileHandler",
    "methods" : [
      { "name" : "<init>", "parameterTypes" : [] },
    ]
  }
```
See the [Reflection Support](Reflection.md) page for more details.

## Build-Time Logger Initialization

The logger can be initialized at image build time with a custom `logging.properties` config, as in the code below:
```java
public class BuildTimeLoggerInit {
  private static final Logger LOGGER;
  static {
    LogManager.getLogManager().readConfiguration(BuildTimeLoggerInit.class.getResourceAsStream("logging.properties"));
    LOGGER = Logger.getLogger(BuildTimeLoggerInit.class.getName());
  }

  public static void main(String[] args) throws IOException {
    // Use the LOGGER here
  }
}
```

The `logging.properties` file is processed at image build time.
It does not need to be included in the native image, therefore reducing the image size.

`LoggerHolder.LOGGER` is also initialized at image build time and is readily available at run time, therefore improving the startup.
Unless the application needs to process a custom `logging.properties` configuration at run time, this approach is recommended.

## Runtime Logger Initialization

The logger can also be initialized at run time, as in the code below:
```java
public class RuntimeLoggerInit {
    public static void main(String[] args) throws IOException {
        LogManager.getLogManager().readConfiguration(RuntimeLoggerInit.class.getResourceAsStream("logging.properties"));
        Logger logger = Logger.getLogger(RuntimeLoggerInit.class.getName());
        // Use the logger here
    }
}
```

In this case, the `logging.properties` file needs to be available for runtime processing and it must be included in the image via the `-H:IncludeResources=logging.properties` option.

See the information about [accessing resources at runtime](Resources.md) for more details on this option.
