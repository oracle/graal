# Logging on Substrate VM

Substrate VM supports logging out-of-the box using the `java.util.logging.*` API.

## Default logging configuration

The logging configuration built in the image by default is based on the `logging.properties` file found in the JDK.
This configures a `java.util.logging.ConsoleHandler` which will only shows messages at the `INFO` and above levels.
Custom logging configuration can be loaded either at image build time or at run time as described below.
An important detail is that if additional logging handlers are used the corresponding classes need to be registerd for reflection.
For example if `java.util.logging.FileHandler` is used then the following reflection config is necessary:
```
{
    "name" : "java.util.logging.FileHandler",
    "methods" : [
      { "name" : "<init>", "parameterTypes" : [] },
    ]
  }
```
(See the [reflection support documentation](REFLECTION.md) for more details.)


## Build time logger initialization

The logger can be initialized at image build time with a custom `logging.properties` config, as in the code below:
```
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

The `logging.properties` file is processed at image build time and it doesn't need to be included in the native image, therefore reducing the image size.
The `LoggerHolder.LOGGER` is also initialized at image build time and is readily available at run time, therefore improving the startup.
Unless the application needs to process a custom `logging.properties` configuration at run time this approach is recommended.


## Run time logger initialization

The logger can also be initialized at run time, as in the code below:

```
public class RuntimeLoggerInit {
    public static void main(String[] args) throws IOException {
        LogManager.getLogManager().readConfiguration(RuntimeLoggerInit.class.getResourceAsStream("logging.properties"));
        Logger logger = Logger.getLogger(RuntimeLoggerInit.class.getName());

        // Use the logger here
    }
}
```

In this case the `logging.properties` file needs to be available for run time processing and it must be included in the image via the `-H:IncludeResources=logging.properties` option.
(See the documentation on [accessing resources at run time](RESOURCES.md) for more details on this option.)
