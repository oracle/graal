---
layout: ni-docs
toc_group: how-to-guides
link_title: Build a Polyglot Native Executable
permalink: /reference-manual/native-image/guides/build-polyglot-native-executable/
---

# Build a Polyglot Native Executable (Java and JavaScript)

With [GraalVM Polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html) you can embed and run code from a guest language in a Java-based host application.
GraalVM makes it possible to ahead-of-time compile a Java application with embedded JavaScript too and create a polyglot native executable. 
[Embedding Reference](../../embedding/embed-languages.md) on how to interact with a guest language like JavaScript from a Java host application for more information.

This guide will show how to build a polyglot native executable with Java host language and JavaScript as a guest language. 

For a demo, you will use this JSON pretty-printer Java application that prints the output in the JSON format:

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

1. Save it in the _PrettyPrintJSON.java_ file and compile:

    ```shell
    javac PrettyPrintJSON.java
    ```
2. Build a native executable by enabling the JavaScript interoperability:

    ```shell
    native-image --language:js PrettyPrintJSON
    ```
    The `--language:js` argument ensures that JavaScript is available in the generated image.
    It will take several minutes as it does not just build the executable, but also pulls in the JavaScript engine.

    > Note: Building a polyglot native executable requires more physical memory because the Truffle framework is included.

3. Run the resulting executable and perform some pretty-printing:

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

The native executable version runs faster than running the same application on the JVM.

> Note: JavaScript support by GraalVM Native Image is considered general availability. The remaining languages support is experimental.

### Related Documentation

* [Embedding Languages](../../embedding/embed-languages.md)
* [JavaScript and Java Interoperability](../../js/JavaInteroperability.md)