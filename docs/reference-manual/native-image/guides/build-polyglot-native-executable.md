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

> Note: JavaScript support by GraalVM Native Image is considered general availability.

This guide demonstrates how to build a polyglot native executable with Java as a host language and JavaScript as a guest language. 

For the demo part, you will use a simple JSON Pretty Printer Java application that prints the output in JSON format:
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

### Prerequisite 
Make sure you have installed a GraalVM JDK.
The easiest way to get started is with [SDKMAN!](https://sdkman.io/jdks#graal).
For other installation options, visit the [Downloads section](https://www.graalvm.org/downloads/).

1. Download or clone the demos repository and navigate to the directory _native-image/build-with-js-embedded/_:
    ```bash
    git clone https://github.com/graalvm/graalvm-demos
    ```
    ```bash
    cd graalvm-demos/native-image/build-with-js-embedded
    ```

2. Open the project configuration file (in this case, _pom.xml_) and examine the required dependencies to enable interoperability with JavaScript.
    - To enable the polyglot runtime:
      ```xml
      <dependency>
          <groupId>org.graalvm.polyglot</groupId>
          <artifactId>polyglot</artifactId> 
          <version>${graalvm.polyglot.version}</version>
      </dependency>
      ```
    - To enable Javascript:
      ```xml
      <dependency>
          <groupId>org.graalvm.polyglot</groupId>
          <artifactId>js</artifactId> 
          <version>${graalvm.polyglot.version}</version>
      </dependency>
      ```

3. Compile and package the project with Maven:
    ```bash
    mvn clean package
    ```
    
4. Build a native executable:
    ```shell
    mvn -Pnative package
    ```
    It takes several minutes as it does not just build the executable, but also pulls in the JavaScript engine. 
    The JavaScript context will be available in the generated image.

    > Note: Building a polyglot native executable requires more physical memory because the Truffle framework is included.

5. Run the resulting executable and perform some pretty-printing:
    ```shell
    ./target/PrettyPrintJSON <<EOF
    {"GraalVM":{"description":"Language Abstraction Platform","supports":["combining languages","embedding languages","creating native images"],"languages": ["Java", "JavaScript", "Python"]}}
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
          "Python"
        ]
      }
    }
    ```

### Related Documentation

* [Embedding Languages](../../embedding/embed-languages.md)
* [JavaScript and Java Interoperability](https://github.com/oracle/graaljs/blob/master/docs/user/JavaInteroperability.md)