---
layout: ni-docs
toc_group: how-to-guides
link_title: Access Environment Variables
permalink: /reference-manual/native-image/guides/access-environment-variables/
redirect_from: /reference-manual/native-image/Properties/
---

# Access Environment Variables in a Native Executable at Runtime

A native executable accesses environment variables in the same way as a regular Java application.

## Run a Demo

For example, run a Java application that iterates over your environment variables and prints out the ones that contain the String of characters passed as a command-line argument.

### Prerequisite 
Make sure you have installed a GraalVM JDK.
The easiest way to get started is with [SDKMAN!](https://sdkman.io/jdks#graal).
For other installation options, visit the [Downloads section](https://www.graalvm.org/downloads/).

1. Save the following source code in a file named _EnvMap.java_:
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

2. Compile the file and build a native executable, as follows:
    ```shell
    javac EnvMap.java
    ```
    ```shell
    native-image EnvMap
    ```

3. Run the native application and pass a command-line argument, such as "HELLO". There should be no output, because there is no environment variable with a matching name. 
    ```shell
    ./envmap HELLO
    <no output>
    ```

4. Create a new environment variable named "HELLOWORLD" and give it the value "Hello World!". (If you are using a `bash` shell, follow the example below.) Now, run the native executable again&mdash;it will correctly print out the name and value of the matching environment variable(s).
    ```shell
    export HELLOWORLD='Hello World!'
    ```
    ```shell
    ./envmap HELLO
    ```
    You should receive the expected output:
    ```
    HELLOWORLD=Hello World!
    ```

### Related Documentation

* [Native Image Build Configuration](../BuildConfiguration.md)

