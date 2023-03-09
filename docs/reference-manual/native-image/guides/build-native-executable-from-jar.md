---
layout: ni-docs
toc_group: how-to-guides
link_title: Build a Native Executable from a JAR File
permalink: /reference-manual/native-image/guides/build-native-executable-from-jar/
---

# Build a Native Executable from a JAR File

You can build a native executable from a class file, from a JAR file, or from a module. This guide demonstrates how to build a native executable from a JAR file. 

To build a native executable from a JAR file in the current working directory, use the following command:
```shell
native-image [options] -jar jarfile [executable name]
```
1. Download and install the latest GraalVM JDK with Native Image using the [GraalVM JDK Downloader](https://github.com/graalvm/graalvm-jdk-downloader):
    ```bash
    bash <(curl -sL https://get.graalvm.org/jdk) 
    ```

2. Prepare the application.

    - Create a new Java project named "App", for example in your favorite IDE or from your terminal, with the following structure:

        ```shell
        | src
        |   --com/
        |      -- example
        |          -- App.java
        ```

    - Add the following Java code to the _src/com/example/App.java_ file:

        ```java
        package com.example;

        public class App {

            public static void main(String[] args) {
                String str = "Native Image is awesome";
                String reversed = reverseString(str);
                System.out.println("The reversed string is: " + reversed);
            }

            public static String reverseString(String str) {
                if (str.isEmpty())
                    return str;
                return reverseString(str.substring(1)) + str.charAt(0);
            }
        }
        ```

        This is a small Java application that reverses a String using recursion.

3. Compile the application:
    ```shell
    javac -d build src/com/example/App.java
    ```

    This produces the file _App.class_ in the _build/com/example_ directory.

4. Create a runnable JAR file:
    ```shell
    jar --create --file App.jar --main-class com.example.App -C build .
    ```

    It will generate a runnable JAR file, named _App.jar_, in the project root directory: 
    To view its contents, run the command `jar tf App.jar`.

5. Create a native executable:
    ```shell
    native-image -jar App.jar
    ```

    It will produce a native executable in the project root directory.

6. Run the native executable:
    ```shell
    ./App
    ```

The default behavior of `native-image` is aligned with the `java` command which means you can pass the `-jar`, `-cp`, `-m`  options to build with Native Image as you would normally do with `java`. For example, `java -jar App.jar someArgument` becomes `native-image -jar App.jar` and `./App someArgument`.

### Related Documentation

* [GraalVM Native Image Quick Start](https://luna.oracle.com/lab/47dafec8-4095-4fba-8313-dad43a64dee4)
* [Build Java Modules into a Native Executable](build-java-module-app-aot.md)
