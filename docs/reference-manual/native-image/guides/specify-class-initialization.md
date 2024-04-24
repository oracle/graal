---
layout: ni-docs
toc_group: how-to-guides
link_title: Specify Class Initialization
permalink: /reference-manual/native-image/guides/specify-class-initialization/
---

# Specify Class Initialization Explicitly

By default, Native Image initializes as many classes as possible at build time. 
This includes most of the JDK classes, the garbage collector, and the application classes that Native Image estimates as "safe" for initialization.

However, you can influence the default behavior by specifying the classes to be build-time or run-time initialized explicitly.
For that, there are two command-line options: `--initialize-at-build-time` and `--initialize-at-run-time`.
You can use these options to specify whole packages or individual classes.
For example, if you have the classes `p.C1`, `p.C2`, … ,`p.Cn`, you can specify that all the classes in the package `p` are to be initialized at build time by passing the following option to `native-image` on the command line:
```shell
--initialize-at-build-time=p
```
If you want only class `C1` in package `p` to be initialized at runtime, use:
```shell
--initialize-at-run-time=p.C1
```

The whole class hierarchy can be initialized at build time by passing `--initialize-at-build-time` on the command line.

You can also programmatically specify Class initialization using the [`RuntimeClassInitialization` class ](https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/hosted/RuntimeClassInitialization.java) from the [Native Image Feature interface](https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/hosted/Feature.java).

This guide demonstrates how to build a native executable by explicitly initializing classes at build time, and then at runtime, and compares the two approaches.

## Run a Demo

For the demo part, you will run a simple XML SAX parser Java application, listing top five most viewed Java talks in 2023. 
The Java SAX parser is event-based, and does not load a complete XML document into memory, but reads it sequentially, unlike a DOM parser.

### Prerequisite 
Make sure you have installed a GraalVM JDK.
The easiest way to get started is with [SDKMAN!](https://sdkman.io/jdks#graal).
For other installation options, visit the [Downloads section](https://www.graalvm.org/downloads/).

1. Save the following Java source code in a file named _SaxXMLParser.java_:
    ```java
    import java.io.IOException;
    import java.io.StringReader;

    import javax.xml.parsers.DocumentBuilder;
    import javax.xml.parsers.DocumentBuilderFactory;
    import javax.xml.parsers.ParserConfigurationException;

    import org.w3c.dom.Document;
    import org.xml.sax.InputSource;
    import org.xml.sax.SAXException;

    public class SaxXMLParser {

        private static final Document configRoot = parseXML("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <talks>
                            <talk name="Asynchronous Programming in Java: Options to Choose from by Venkat Subramaniam"></talk>
                            <talk name="Anatomy of a Spring Boot App with Clean Architecture by Steve Pember"></talk>
                            <talk name="Java in the Cloud with GraalVM by Alina Yurenko"></talk>
                            <talk name="Bootiful Spring Boot 3 by Josh Long"></talk>
                            <talk name="Spring I/O 2023 - Keynote"></talk>
                        </talks>
                        """);

        private static Document parseXML(String xmlContents) {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                return builder.parse(new InputSource(new StringReader(xmlContents)));
            } catch (ParserConfigurationException | IOException | SAXException e) {
                throw new RuntimeException(e);
            }
        }

        public static void main(String[] args) {
            System.out.println("Talks loaded via XML:");
            var nodeList = configRoot.getElementsByTagName("talk");
            for (int i = 0; i < nodeList.getLength(); i++) {
                System.out.println("- " + nodeList.item(i).getAttributes().getNamedItem("name").getTextContent());
            }
        }
    }
    ```

2. Compile the application:
    ```bash
    javac SaxXMLParser.java
    ```

3. Build a native executable, initializing the application classes at build time:
    ```bash
    native-image --initialize-at-build-time=SaxXMLParser -o buildtime-parser SaxXMLParser
    ```
    The `-o` option specifies the name for the output file. 
    If your Java project structure is different, make sure to pass necessary JAR file(s) on the classpath using `-cp <ClasspathForYourProject>`.

4. Run the application from a native executable and `time` the results:
    ```bash
    time ./buildtime-parser
    ```
    On a machine with 16 GB of memory and 8 cores, you should see a result similar to:
    ```
    Talks loaded via XML:
    - Asynchronous Programming in Java: Options to Choose from by Venkat Subramaniam
    - Anatomy of a Spring Boot App with Clean Architecture by Steve Pember
    - Java in the Cloud with GraalVM by Alina Yurenko
    - Bootiful Spring Boot 3 by Josh Long
    - Spring I/O 2023 - Keynote
    ./buildtime-parser  0.00s user 0.00s system 46% cpu 0.017 total
    ```
    Check the file size, which should be 13M:
    ```
    du -sh buildtime-parser
    ```

5. Next, build a new native executable that excludes the static initializer from being initialized at build time, and provides a different name to differentiate from the previous build:
    ```bash
    native-image --initialize-at-run-time=SaxXMLParser -o runtime-parser SaxXMLParser
    ```

6. Run the second executable and `time` the results for comparison:
    ```bash
    time ./runtime-parser
    ```
    This time you should see something similar to this:
    ```
    Talks loaded via XML:
    - Asynchronous Programming in Java: Options to Choose from by Venkat Subramaniam
    - Anatomy of a Spring Boot App with Clean Architecture by Steve Pember
    - Java in the Cloud with GraalVM by Alina Yurenko
    - Bootiful Spring Boot 3 by Josh Long
    - Spring I/O 2023 - Keynote
    ./runtime-parser  0.00s user 0.00s system 59% cpu 0.012 total
    ```
    The static initializer is now run at runtime. 
    Notice the increase in the CPU time.
    
    Check also the file size, which should have increased to 19M:
    ```
    du -sh runtime-parser
    ```

    The benefits of the build-time versus run-time initialization are described in [Class Initialization in Native Image](../ClassInitialization.md), but, in short, it makes the run-time initialization and "class safely" checks unnecessary, improving the application efficiency.

This guide demonstrated how you can influence the default `native-image` build-time initialization policy, and configure it to initialize a specific class at runtime, depending on the use case. 

### Related Documentation

* [Class Initialization](../ClassInitialization.md)
* [Native Image Build Configuration](../BuildConfiguration.md)