---
layout: ni-docs
toc_group: how-to-guides
link_title: Use System Properties
permalink: /reference-manual/native-image/guides/use-system-properties/
redirect_from: /reference-manual/native-image/Properties/
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
If you build a native executable using `native-image -Dfoo=bar App`, the system property `foo` will be available at *executable build time*. This means it is available to the [code in your application that is run at build time](http://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/ImageInfo.html#inImageBuildtimeCode--) (usually static field initializations and static initializers).
Thus, if you run the resulting executable, it will not contain `foo` in the printed list of properties.

If, on the other hand, you run the executable with `app -Dfoo=bar`, it will display `foo` in the list of properties because you specified property at *executable runtime*.

In other words:
* Pass `-D<key>=<value>` as an argument to `native-image` to control the properties seen at executable build time.
* Pass `-D<key>=<value>` as an argument to a native executable to control the properties seen at executable runtime.

## Reading System Properties at Build Time
You can read system properties at build time and incorporate them into the resulting executable file, as shown in the following example.

1. Make sure you have installed a GraalVM JDK.
The easiest way to get started is with [SDKMAN!](https://sdkman.io/jdks#graal).
For other installation options, visit the [Downloads section](https://www.graalvm.org/downloads/).

2. Save the following Java code into a file named _ReadProperties.java_, then compile it using `javac`:

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

3. Build the native executable, passing a system property as a command-line argument. Then run the native executable, passing a different system property on the command line.
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

    This indicates that the class static initializer was not run at **build time**, but at **runtime**.

4. To force the class static initializer to run at build time, use the `--initialize-at-build-time` flag, as follows:

    ```shell
    native-image --initialize-at-build-time=ReadProperties -Dstatic_key=STATIC_VALUE ReadProperties
    ```
    In the output from the `native-image` tool you should see output similar to the following:
   ```shell
    ...
    [1/7] Initializing...                                            (7.7s @ 0.07GB)
    Getting value of static property with key: static_key
    ...
    ```

    Run the executable again, as follows:
    ```shell
    ./readproperties -Dinstance_key=INSTANCE_VALUE
    ```

    This time you should see the following output, confirming that the static initializer was run at **build time**, not at runtime.

    ```shell
    Value of static property: STATIC_VALUE
    Getting value for instance property key: instance_key
    Value of instance property: INSTANCE_VALUE
    ```

### Related Documentation

* [Class Initialization in Native Image](../ClassInitialization.md)
* [Native Image Build Configuration](../BuildConfiguration.md)