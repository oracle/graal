---
layout: ni-docs
toc_group: how-to-guides
link_title: Build a Native Executable from a JAR
permalink: /reference-manual/native-image/guides/build-native-executable-from-jar/
---

# Build a Native Executable from a JAR

You can build a native executable from a class file, from a JAR file, or from a module (with Java 9 and higher). This guide demonstrates how to build a  native executable from a JAR file. 

To build a native executable from a JAR file in the current working directory, use the following command:
```shell
native-image [options] -jar jarfile [imagename]
```

1. Prepare the application.

    - Create a new Java project named "App", for example in your favorite IDE or from your terminal, with the following structure:

        ```shell
        | src
        |   --com/
        |      -- example
        |          -- App.java
        ```

    - Add the following Java code into the _src/com/example/App.java_ file:

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

2. Compile the application:

    ```shell
    javac -d build src/com/example/App.java
    ```
    This produces the file _App.class_ in the _build/com/example_ directory.

3. Create a runnable JAR file:

    ```shell
    jar --create --file App.jar --main-class com.example.App -C build .
    ```
    It will generate a runnable JAR file, named `App.jar`, in the root directory: 
    To view its contents, type `jar tf App.jar`.

4. Create a native executable:

    ```
    native-image -jar App.jar
    ```
    It will produce a native executable in the project root directory.
5. Run the native executable:

    ```shell
    ./App
    ```

The `native-image` tool can provide the class path for all classes using the familiar option from the java launcher: `-cp`, followed by a list of directories or JAR files, separated by `:` on Linux and macOS platforms, or `;` on Windows. The name of the class containing the `main` method is the last argument, or you can use the `-jar` option and provide a JAR file that specifies the `main` method in its manifest.

### Related Documentation

* [GraalVM Native Image Quick Start](https://luna.oracle.com/lab/47dafec8-4095-4fba-8313-dad43a64dee4)
* [Build Java Modules into a Native Executable](build-java-module-app-aot.md)
