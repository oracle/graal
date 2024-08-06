---
layout: ni-docs
toc_group: how-to-guides
link_title: Build a Polyglot Native Executable
permalink: /reference-manual/native-image/guides/build-polyglot-native-executable/
---

# Build a Polyglot Native Executable (Java and JavaScript)

With the [GraalVM Polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html) you can embed and run code from a guest language in a Java-based host application.
GraalVM makes it possible to compile a Java application ahead-of-time with embedded JavaScript and to create a polyglot native executable. 
See the [Embedding Languages documentation](../../embedding/embed-languages.md) for more information about how a Java host application can interact with a guest language like JavaScript.

This guide demonstrates how to build a polyglot native executable with Java as a host language and JavaScript as a guest language. 

For the demo part, you will use a simple JSON Pretty Printer Java application that prints the output in JSON format.

### Prerequisite 
Make sure you have installed a GraalVM JDK.
The easiest way to get started is with [SDKMAN!](https://sdkman.io/jdks#graal).
For other installation options, visit the [Downloads section](https://www.graalvm.org/downloads/).

1. Create a Maven project and replace the default _Application.java_ file with a file named _PrettyPrintJSON.java_. Copy and paste the following contents into the file:

    ```java
    import java.io.*;
    import java.util.stream.*;
    import org.graalvm.polyglot.*;

    public class PrettyPrintJSON {
      public static void main(String[] args) throws java.io.IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String input = reader.lines()
        .collect(Collectors.joining(System.lineSeparator()));
        try (Context context = Context.create("js")) {
          Value parse = context.eval("js", "JSON.parse");
          Value stringify = context.eval("js", "JSON.stringify");
          Value result = stringify.execute(parse.execute(input), null, 2);
          System.out.println(result.asString());
        }
      }
    } 
    ```

2. Open your project configuration file (in this case, _pom.xml_) and add the following dependencies to enable interoperability with JavaScript.

    - To enable the polyglot runtime:
      ```xml
      <dependency>
          <groupId>org.graalvm.polyglot</groupId>
          <artifactId>polyglot</artifactId> 
          <version>${graalvm.version}</version>
      </dependency>
      ```
    - To enable Javascript:
      ```xml
      <dependency>
          <groupId>org.graalvm.polyglot</groupId>
          <artifactId>js</artifactId> 
          <version>${graalvm.version}</version>
      </dependency>
      ```

3. Compile the project using the GraalVM JDK:
    ```shell 
    ./mvnw clean package
    ```

4. Build a native executable:
    ```shell
    native-image PrettyPrintJSON
    ```
  
    It takes several minutes as it does not just build the executable, but also pulls in the JavaScript engine. 
    The JavaScript context will be available in the generated image.

    > Note: Building a polyglot native executable requires more physical memory because the Truffle framework is included.

5. Run the resulting executable and perform some pretty-printing:
    ```shell
    ./prettyprintjson <<EOF
    {"GraalVM":{"description":"Language Abstraction Platform","supports":["combining languages","embedding languages","creating native images"],"languages": ["Java","JavaScript","Node.js", "Python", "Ruby","R","LLVM"]}}
    EOF
    ```
    The expected output is:

    ```JSON
    {
    "GraalVM": {
        "description": "Language Abstraction Platform",
        "supports": [
        "combining languages",
        "embedding languages",
        "creating native images"
        ],
        "languages": [
        "Java",
        "JavaScript",
        "Node.js",
        "Python",
        "Ruby",
        "R",
        "LLVM"
        ]
    }
    }
    ```

The native executable version runs faster than running the same application on the GraalVM JDK.

> Note: JavaScript support by GraalVM Native Image is considered general availability. The remaining languages support is experimental.

### Related Documentation

* [Embedding Languages](../../embedding/embed-languages.md)
* [JavaScript and Java Interoperability](../../js/JavaInteroperability.md)