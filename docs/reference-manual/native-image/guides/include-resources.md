---
layout: ni-docs
toc_group: how-to-guides
link_title: Include Resources in a Native Executable
permalink: /reference-manual/native-image/guides/include-resources/
---

# Include Resources in a Native Executable

The following steps illustrate how to include a resource in a native executable. The application `fortune` simulates the traditional `fortune` Unix program (for more information, see [fortune](https://en.wikipedia.org/wiki/Fortune_(Unix))).

1. Make sure you have installed a GraalVM JDK.
The easiest way to get started is with [SDKMAN!](https://sdkman.io/jdks#graal).
For other installation options, visit the [Downloads section](https://www.graalvm.org/downloads/).

2. Save the following Java source code as a file named _Fortune.java_:

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

3. Download the [_fortunes.u8_](https://github.com/oracle/graal/blob/3ed4a7ebc5004c51ae310e48be3828cd7c7802c2/docs/reference-manual/native-image/assets/fortunes.u8) resource file and save it in the same directory as _Fortune.java_.

4. Compile the Java source code:
    ```shell
    $JAVA_HOME/bin/javac Fortune.java
    ```

5. Build a native executable by specifying the resource path:
    ```shell
    $JAVA_HOME/bin/native-image Fortune -H:IncludeResources=".*u8$"
    ```

6. Run the executable image: 
    ```shell
    ./fortune
    ```

### Related Documentation

* [Accessing Resources in Native Image](../Resources.md)
