---
layout: ni-docs
toc_group: how-to-guides
link_title: Access Environment Variables
permalink: /reference-manual/native-image/guides/access-environment-variables/
---

# Access Environment Variables in a Native Executable at Run Time

A native executable accesses your environment variables in the same way as a regular Java application.
For example, assume you have the following source code:

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

This code iterates over your environment variables and prints out the ones that contain the String of characters passed as the command-line argument.

1. Compile the file and build a native executable, as follows:

    ```shell
    javac EnvMap.java
    native-image EnvMap
    ```

2. Run the resulting native executable and pass a command-line argument, such as "HELLO". There should be no output, because there is no environment variable with a matching name. 
    ```shell
    ./envmap HELLO
    <no output>
    ```

3. Create a new environment variable named "HELLOWORLD" and give it the value "Hello World!". (If you are using a `bash` shell, follow the example below.) Now, run the native executable again--it will correctly print out the name and value of the matching environment variable(s).

    ```shell
    export HELLOWORLD='Hello World!'
    ./envmap HELLO
    ```
    You should receive the expected output:
    ```
    HELLOWORLD=Hello World!
    ```

### Related Documentation

* [Native Image Build Configuration](../BuildConfiguration.md)

