---
layout: ni-docs
toc_group: how-to-guides
link_title: Use System Properties
permalink: /reference-manual/native-image/guides/use-system-properties/
redirect_to: /reference-manual/native-image/overview/Options/
---

# Use System Properties in a Native Executable

Assume you have compiled the following Java application using `javac`:
```java
public class App {
    public static void main(String[] args) {
        System.getProperties().list(System.out);
    }
}
```

If you build a native executable using `native-image -Dfoo=bar App`, the system property `foo` will **only** be available at build time.
This means it is available to the [code in your application that is run at build time](http://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/ImageInfo.html#inImageBuildtimeCode--) (usually static field initializations and static initializers).
But if you run the resulting executable, it will not contain `foo` in the printed list of properties.

If, on the other hand, you run the executable with `app -Dfoo=bar`, it will display `foo` in the list of properties because you specified this property.

## Read System Properties at Build Time

You can read system properties at build time and incorporate them into the native executable, as shown in the following example.

### Prerequisite 
Make sure you have installed a GraalVM JDK.
The easiest way to get started is with [SDKMAN!](https://sdkman.io/jdks#graal).
For other installation options, visit the [Downloads section](https://www.graalvm.org/downloads/).

1. Save the following Java code into a file named _ReadProperties.java_:
    ```java
    public class ReadProperties {
        private static final String STATIC_PROPERTY_KEY = "static_key";
        private static final String INSTANCE_PROPERTY_KEY = "instance_key";
        private static final String STATIC_PROPERTY;
        private final String instanceProperty;
        static {
            System.out.println("Getting value of static property with key: " + STATIC_PROPERTY_KEY);
            STATIC_PROPERTY = System.getProperty(STATIC_PROPERTY_KEY);
        }
    
        public ReadProperties() {
            System.out.println("Getting value of instance property with key: " + INSTANCE_PROPERTY_KEY);
            instanceProperty = System.getProperty(INSTANCE_PROPERTY_KEY);
        }
        
        public void print() {
            System.out.println("Value of instance property: " + instanceProperty);
        } 
        
        public static void main(String[] args) {
            System.out.println("Value of static property: " + STATIC_PROPERTY);
            ReadProperties rp = new ReadProperties();
            rp.print();
        } 
    }
    ```

2. Compile the application:
    ```shell
    javac ReadProperties.java
    ```

3. Build the native executable, passing a system property as a command-line option. Then run the native executable, passing a different system property on the command line.
    ```shell
    native-image -Dstatic_key=STATIC_VALUE ReadProperties
    ```
    ```shell
    ./readproperties -Dinstance_key=INSTANCE_VALUE
    ```

    You should see the following output:
    ```shell
    Getting value of static property with key: static_key
    Value of static property: null
    Getting value of instance property with key: instance_key
    Value of instance property: INSTANCE_VALUE
    ```

    This indicates that the class static initializer was not run at build time, but at **run time**.

4. To force the class static initializer to run at build time, use the `--initialize-at-build-time` option, as follows:
    ```shell
    native-image --initialize-at-build-time=ReadProperties -Dstatic_key=STATIC_VALUE ReadProperties
    ```
    In the output from the `native-image` tool you should see the message like this:
    ```
    GraalVM Native Image: Generating 'readproperties' (executable)...
    ==========================================================================
    Getting value of static property with key: static_key
    [1/8] Initializing...                                      (4.0s @ 0.13GB)
    ...
    ```

5. Run the executable again, as follows:
    ```shell
    ./readproperties -Dinstance_key=INSTANCE_VALUE
    ```

    This time you should see the following output, confirming that the static initializer was run at **build time**, not at run time.

    ```shell
    Value of static property: STATIC_VALUE
    Getting value for instance property key: instance_key
    Value of instance property: INSTANCE_VALUE
    ```

### Related Documentation

* [Command-line Options: System Properties](../BuildOptions.md#system-properties)
* [Specify Class Initialization Explicitly](specify-class-initialization.md)