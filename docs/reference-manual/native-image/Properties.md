---
layout: docs
toc_group: native-image
link_title: Using System Properties in Native Image
permalink: /reference-manual/native-image/Properties/
---

# Using System Properties in Native Image

Assume you have the following Java Program:
```java
public class App {
    public static void main(String[] args) {
        System.getProperties().list(System.out);
    }
}
```
If you compile that with, e.g., `native-image -Dfoo=bar App` the system property `foo` will be available at *executable build time*.
For example, whenever you are in the [code that is part of your application but executed at image build time](http://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/ImageInfo.html#inImageBuildtimeCode--) (usually static field initializations and static initializers).
Thus if you execute the image above it will not contain `foo` in the list of properties.

If, on the other hand, you execute the image with `app -Dfoo=bar`, it will show `foo` in the list of properties because you specified it for *executable runtime*.

In other words:
* Passing `-D<key>=<value>` as an argument to `native-image` affects properties seen at executable build time.
* Passing `-D<key>=<value>` as an argument to a native executable affects properties seen at executable runtime.

### Using System Properties at Build Time
System Properties can be read at build time and incorporated into the executable file, as shown in the following example.

1. Save the following Java code into a file named _ReadProperties.java_, then compile it using `javac`:

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

2. Build and run the native executable

    ```shell
    native-image -Dstatic_key=STATIC_VALUE ReadProperties
    ./readproperties -Dinstance_key=INSTANCE_VALUE
    ```

    You should see output similar to

    ```
    Getting value of static property with key: static_key
    Value of static property: null
    Getting value of instance property with key: instance_key
    Value of instance property: INSTANCE_VALUE
    ```

    This indicates that the class static initializer was run at **runtime**, not at build time.

    To force the class static initializer to run at build time, use the `--initialize-at-build-time` flag, as follows:

    ```shell
    native-image --initialize-at-build-time=ReadProperties -Dstatic_key=STATIC_VALUE ReadProperties
    ```

    In the output from the `native-image` tool you should see output similar to the following

    ```
    ...
    [1/7] Initializing...                                            (7.7s @ 0.07GB)
    Getting value of static property with key: static_key
    ...
    ```
    Run the executable again, as follows

    ```shell
    ./readproperties -Dinstance_key=INSTANCE_VALUE
    ```

    This time, you should see output similar to 

    ```
    Value of static property: STATIC_VALUE
    Getting value for instance property key: instance_key
    Value of instance property: INSTANCE_VALUE
    ```

    This confirms that the static initializer was run at **build time**, not at runtime.


## Access Environment Variables at Run Time

Native image can also access environment variables at runtime.
Consider the following example.

1. Save this Java code into the _EnvMap.java_ file:

  ```java
  import java.util.Map;
  public class EnvMap {
      public static void main (String[] args) {
          var filter = args.length > 0 ? args[0] : "";
          Map<String, String> env = System.getenv();
          for (String envName : env.keySet()) {
              if(envName.contains(filter)) {
                  System.out.format("%s=%s%n",
                                    envName,
                                    env.get(envName));
              }
          }
      }
  }
  ```

  This code iterates over the environment variables and prints out the ones passing through the filter, passed as the command line argument.

2. Compile and build a native image:

  ```shell
  javac EnvMap.java
  native-image EnvMap
  ```

3. Run the resulting native image and pass some argument. It will correctly print out the values of the environment variables. For example:

  ```shell
  ./envmap HELLO
  HELLOWORLD=hello world
  export HELLOWORLD="world"
  ./envmap HELLO
  HELLOWORLD=world
  ```
## Related Documentation
* [Class Initialization in Native Image](ClassInitialization.md)