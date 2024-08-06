---
layout: ni-docs
toc_group: how-to-guides
link_title: Include Resources in a Native Executable
permalink: /reference-manual/native-image/guides/include-resources/
---

# Include Resources in a Native Executable

By default, the `native-image` tool does not integrate any Java resource files into a native executable.
You must specify resources that should be accessible by your application at runtime.

This guide demonstrates how to register resources to be included in a native executable by providing a resource configuration file.
See [Accessing Resources in Native Image](../Resources.md) for more ways to include resources.

### Prerequisite

Make sure you have installed a GraalVM JDK.
The easiest way to get started is with [SDKMAN!](https://sdkman.io/jdks#graal).
For other installation options, visit the [Downloads section](https://www.graalvm.org/downloads/).

## Run a Demo

In the following example, you run a "fortune teller" application that simulates the traditional `fortune` Unix program (for more information, see [fortune](https://en.wikipedia.org/wiki/Fortune_(Unix))).

1. Save the following Java source code as a file named _Fortune.java_:
    ```java
    import java.io.BufferedReader;
    import java.io.InputStreamReader;
    import java.util.ArrayList;
    import java.util.Random;
    import java.util.Scanner;

    public class Fortune {

        private static final String SEPARATOR = "%";
        private static final Random RANDOM = new Random();
        private ArrayList<String> fortunes = new ArrayList<>();

        public Fortune(String path) {
            // Scan the file into the array of fortunes
            Scanner s = new Scanner(new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream(path))));
            s.useDelimiter(SEPARATOR);
            while (s.hasNext()) {
                fortunes.add(s.next());
            }
        }
        
        private void printRandomFortune() throws InterruptedException {
            int r = RANDOM.nextInt(fortunes.size()); //Pick a random number
            String f = fortunes.get(r);  //Use the random number to pick a random fortune
            for (char c: f.toCharArray()) {  // Print out the fortune
              System.out.print(c);
                Thread.sleep(100); 
            }
        }
      
        public static void main(String[] args) throws InterruptedException {
            Fortune fortune = new Fortune("/fortunes.u8");
            fortune.printRandomFortune();
        }
    }
    ```

2. Download the [_fortunes.u8_](https://github.com/oracle/graal/blob/3ed4a7ebc5004c51ae310e48be3828cd7c7802c2/docs/reference-manual/native-image/assets/fortunes.u8) resource file and save it in the same directory as _Fortune.java_.

3. Create a configuration file, named _resource-config.json_, and save it in the _META-INF/native-image/_ subdirectory. Register the resource using a [glob pattern](../Resources.md#resource-configuration-file):
    ```json
    {
    "globs": [
        {
        "glob": "fortunes.u8"
        }
      ]
    }
    ```
    The `native-image` tool picks up all configuration files that it finds in the _META-INF/native-image/_ directory automatically.

4. Compile the application:
    ```shell
    javac Fortune.java
    ```

5. Build a native executable:
    ```shell
    native-image Fortune
    ```

6. Run the fortune teller application to test: 
    ```shell
    ./fortune
    ```

To see which resources were included in your native executable, pass the option `--emit build-report` to the `native-image` tool at build time.
It generates an HTML file that can be examined with a regular web browser.
The information about all included resources will be under the `Resources` tab.

In this demo the path to the resource file is straightforward, but it may be more complex in a real-world use case.
Resources are specified via globs. For more advanced use-cases, you can register resources using the API methods (see [class RuntimeResourceAccess](https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/hosted/RuntimeResourceAccess.html)). 
Learn more about specifying a resource path using a glob and some syntax rules to be observed from [Accessing Resources in Native Image](../Resources.md).

### Related Documentation

* [Accessing Resources in Native Image](../Resources.md)
* [Resource Metadata in JSON](../ReachabilityMetadata.md#resource-metadata-in-json)
