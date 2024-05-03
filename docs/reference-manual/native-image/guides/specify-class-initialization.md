---
layout: ni-docs
toc_group: how-to-guides
link_title: Specify Class Initialization
permalink: /reference-manual/native-image/guides/specify-class-initialization/
---

# Specify Class Initialization Explicitly

By default, Native Image initializes application classes at run time, except for the classes that Native Image proves "safe" for initialization at build time. 
However, you can influence the default behavior by specifying the classes to be build-time or run-time initialized explicitly.
For that, there are two command-line options: `--initialize-at-build-time` and `--initialize-at-run-time`.
You can use these options to specify whole packages or individual classes.
For example, if you have the classes `p.C1`, `p.C2`, … ,`p.Cn`, you can specify that all the classes in the package `p` are to be initialized at build time by passing the following option to `native-image`:
```shell
--initialize-at-build-time=p
```
If you want only class `C1` in package `p` to be initialized at runtime, use:
```shell
--initialize-at-run-time=p.C1
```

You can also programmatically specify Class initialization using the [`RuntimeClassInitialization` class](https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/hosted/RuntimeClassInitialization.java) from the [Native Image Feature interface](https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/hosted/Feature.java).

This guide demonstrates how to build a native executable by running the class initializer at runtime, and then at build time, and compares the two approaches. 

## Run a Demo

For the demo part, you will run a simple Java application that uses an XML SAX parser to list some Java talks from 2023. 
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

        private static final Document document = parseXML("""
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
            var nodeList = document.getElementsByTagName("talk");
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
  
3. Build a native executable, explicitly running the class initializer at runtime:
    ```bash
    native-image --initialize-at-run-time=SaxXMLParser -o runtime-parser SaxXMLParser
    ```
    You can leave out the `--initialize-at-run-time=SaxXMLParser` option in this example because the class initializer of `SaxXMLParser` parses XML to initialize the `document` field, which means it is not safe to initialize the class at build time automatically.
    The `-o` option specifies the name of the output file. 

4. Run and `time` the native application:
    ```bash
    time ./runtime-parser
    ```
    On a machine with 16 GB of memory and 8 cores, you should see a result similar to:
    ```
    Talks loaded via XML:
    - Asynchronous Programming in Java: Options to Choose from by Venkat Subramaniam
    - Anatomy of a Spring Boot App with Clean Architecture by Steve Pember
    - Java in the Cloud with GraalVM by Alina Yurenko
    - Bootiful Spring Boot 3 by Josh Long
    - Spring I/O 2023 - Keynote
    ./runtime-parser  0.00s user 0.01s system 44% cpu 0.021 total
    ```
    The application parses the XML data at runtime.

    Check the file size which should be around 19M:
    ```
    du -sh runtime-parser
    ```

5.  Next, build a native executable initializing `SaxXMLParser` at build time, and providing a different name for the output file to differentiate it from the previous build:
    ```bash
    native-image --initialize-at-build-time=SaxXMLParser -o buildtime-parser SaxXMLParser
    ```

6. Run and `time` the second executable for comparison:
    ```bash
    time ./buildtime-parser
    ```
    This time you should see something similar to this:
    ```
    Talks loaded via XML:
    - Asynchronous Programming in Java: Options to Choose from by Venkat Subramaniam
    (...)
    ./buildtime-parser  0.00s user 0.00s system 43% cpu 0.019 total
    ```
    Check the file size which should decrease to around 13M:
    ```
    du -sh buildtime-parser
    ```
    The file size change is because Native Image runs the static initializer at build time, parsing the XML data, and persisting only the `Document` object in the executable.
    As a result, the majority of the XML parsing infrastructure does not become reachable when Native Image statically analyzes the application and is, therefore, not included in the executable.

Another valuable criterion for profiling applications more accurately is the number of instructions, which can be obtained using the [Linux `perf` profiler](../PerfProfiling.md).

For example, for this demo application, the number of instructions decreased by almost 30% (from 11.8M to 8.6M) in the case of build-time class initialization:
```bash
perf stat ./runtime-parser 
Talks loaded via XML:
- Asynchronous Programming in Java: Options to Choose from by Venkat Subramaniam
(...)
 Performance counter stats for './runtime-parser':
(...)                   
        11,323,415      cycles                           #    3.252 GHz                       
        11,781,338      instructions                     #    1.04  insn per cycle            
         2,264,670      branches                         #  650.307 M/sec                     
            28,583      branch-misses                    #    1.26% of all branches           
(...)   
       0.003817438 seconds time elapsed
       0.000000000 seconds user
       0.003878000 seconds sys 
```
```bash
perf stat ./buildtime-parser 
Talks loaded via XML:
- Asynchronous Programming in Java: Options to Choose from by Venkat Subramaniam
(...)
 Performance counter stats for './buildtime-parser':
(...)                    
         9,534,318      cycles                           #    3.870 GHz                       
         8,609,249      instructions                     #    0.90  insn per cycle            
         1,640,540      branches                         #  665.818 M/sec                     
            23,490      branch-misses                    #    1.43% of all branches           
(...)
       0.003119519 seconds time elapsed
       0.001113000 seconds user
       0.002226000 seconds sys 
```

This demonstrates how Native Image can shift work from runtime to build time: when the class is initialized at build time, the XML data is parsed when the executable is being built and only the parsed `Document` object is included.
This not only makes the executable smaller in file size, but also faster to run: when the executable runs, the `Document` object already exists and only needs to be printed.

To ensure native executables built with Native Image are as compatible as possible with the HotSpot behavior, application classes that cannot be safely initialized at build time, are initialized at runtime.
You as a user, or a framework that you use, must explicitly request build-time initialization for certain classes to benefit from smaller file sizes and faster times to run.
Include the right data structures to avoid the image size blowing up instead.
We also recommend to use `--initialize-at-build-time` with single classes only. 
It may be that you need to add a lot of `--initialize-at-build-time` entries. 
Note that incorrect build-time initialization can lead from dysfunctional behavior to including sensitive data such as passwords or encryption keys, problems that are to be avoided in production settings.

### Conclusion

This guide demonstrated how you can influence the default `native-image` class initialization policy, and configure it to initialize a specific class at build time, depending on the use case. 
The benefits of the build-time versus run-time initialization are described in [Class Initialization in Native Image](../ClassInitialization.md), but, in short, build-time initialization can significantly decrease the overall file size and improve the runtime of your application when used correctly.

### Related Documentation

* [Class Initialization](../ClassInitialization.md)
* [Native Image Build Configuration](../BuildConfiguration.md)