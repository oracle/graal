---
layout: ni-docs
toc_group: how-to-guides
link_title: Build with Reflection
permalink: /reference-manual/native-image/guides/build-with-reflection/
---

# Build a Native Executable with Reflection 

Reflection is a feature of the Java programming language that enables a running Java program to examine and modify attributes of its classes, interfaces, fields, and methods.

The `native-image` utility provides partial support for reflection. It uses static analysis to detect the elements of your application that are accessed using the Java Reflection API. However, because the analysis is static, it cannot always completely predict all usages of the API when the program runs. In these cases, you must provide a configuration file to the native-image utility to specify the program elements that use the API.

## Example with No Configuration
The following application demonstrates the use of Java reflection.

1. Save the following source code in a file named _ReflectionExample.java_:
    ```java
    import java.lang.reflect.Method;
    
    class StringReverser {
        static String reverse(String input) {
            return new StringBuilder(input).reverse().toString();
        }
    }
    
    class StringCapitalizer {
        static String capitalize(String input) {
            return input.toUpperCase();
        }
    }
    
    public class ReflectionExample {
        public static void main(String[] args) throws ReflectiveOperationException {
            if (args.length == 0) {
                System.err.println("You must provide the name of a class, the name of its method and input for the method");
                return;
            }
            String className = args[0];
            String methodName = args[1];
            String input = args[2];
    
            Class<?> clazz = Class.forName(className);
            Method method = clazz.getDeclaredMethod(methodName, String.class);
            Object result = method.invoke(null, input);
            System.out.println(result);
        }
    }
    ```
    This Java application uses command-line arguments to determine the operation to be performed.

2. Compile the example and then run each command below.
    ```shell
    $JAVA_HOME/bin/javac ReflectionExample.java
    $JAVA_HOME/bin/java ReflectionExample StringReverser reverse "hello"
    $JAVA_HOME/bin/java ReflectionExample StringCapitalizer capitalize "hello"
    ```
    The output of each command should be `"olleh"` and `"HELLO"`, respectively. (An exception is thrown if you provide any other string to identify the class or method.)

3. Use the `native-image` utility to create a native executable, as follows:
    ```shell
    $JAVA_HOME/bin/native-image --no-fallback ReflectionExample
    ```
    > **NOTE:** The `--no-fallback` option to `native-image` causes the utility to fail if it can not create an executable file.

4. Run the resulting native executable, using the following command:
    ```bash
    ./reflectionexample StringReverser reverse "hello"
    ```
    You'll see an exception, similar to 
    ```shell
    Exception in thread "main" java.lang.ClassNotFoundException: StringReverser
        at java.lang.Class.forName(DynamicHub.java:1338)
        at java.lang.Class.forName(DynamicHub.java:1313)
        at ReflectionExample.main(ReflectionExample.java:25)
    ```
    This shows that, from its static analysis, the `native-image` tool was unable to determine that class `StringReverser`
    is used by the application and therefore did not include it in the native executable. 

## Example with Configuration
To build a native executable containing references to the classes and methods that are accessed via reflection, provide the `native-image` utility with a configuration file that specifies the classes and corresponding methods. (For more information about configuration files, see [Reflection Use in Native Images](../Reflection.md).) You can create this file by hand, but a more convenient approach is to generate the configuration using the tracing agent. The agent generates the configuration for you automatically when you run your application (for more information, see [Assisted Configuration with Tracing Agent](../AutomaticMetadataCollection.md#tracing-agent). 

The following steps demonstrate how to use the javaagent tool, and its output, to create a native executable that relies on reflection.

1. Create a directory `META-INF/native-image` in the working directory:
    ```shell
    mkdir -p META-INF/native-image
    ```

2. Run the application with the tracing agent enabled, as follows:
    ```shell
    $JAVA_HOME/bin/java -agentlib:native-image-agent=config-output-dir=META-INF/native-image ReflectionExample StringReverser reverse "hello"
    ```
    This command creates a file named _reflection-config.json_ containing the name of the class `StringReverser` and its `reverse()` method.
    ```json
    [
        {
        "name":"StringReverser",
        "methods":[{"name":"reverse","parameterTypes":["java.lang.String"] }]
        }
    ]
    ```

3. Build a native executable:
    ```shell
    $JAVA_HOME/bin/native-image ReflectionExample
    ```
    The `native-image` tool automatically uses configuration files in the _META-INF/native-image_ directory.
    However, we recommend that the _META-INF/native-image_ directory is on the class path, either via a JAR file or using the `-cp` flag. (This avoids confusion for IDE users where a directory structure is defined by the IDE itself.)

4. Test your executable.
    ```shell
    ./reflectionexample StringReverser reverse "hello"
    olleh
    ./reflectionexample StringCapitalizer capitalize "hello"
    Exception in thread "main" java.lang.ClassNotFoundException: StringCapitalizer
        at java.lang.Class.forName(DynamicHub.java:1338)
	    at java.lang.Class.forName(DynamicHub.java:1313)
	    at ReflectionExample.main(ReflectionExample.java:25)
    ```
    Neither the tracing agent nor the `native-image` tool can ensure that the configuration file is complete.
    The agent observes and records which program elements are accessed using reflection when you run the program. In this case, the `native-image` tool has not been configured to include references to class `StringCapitalizer`.

5. Update the configuration to include class `StringCapitalizer`.
    You can manually edit the _reflection-config.json_ file or re-run the tracing agent to update the existing configuration file using the `config-merge-dir` option, as follows:
    ```shell
    $JAVA_HOME/bin/java -agentlib:native-image-agent=config-merge-dir=META-INF/native-image ReflectionExample StringCapitalizer capitalize "hello"
    ```
    This command updates the _reflection-config.json_ file to include the name of the class `StringCapitalizer` and its `capitalize()` method.
    ```json
    [
        {
        "name":"StringCapitalizer",
        "methods":[{"name":"capitalize","parameterTypes":["java.lang.String"] }]
        },
        {
        "name":"StringReverser",
        "methods":[{"name":"reverse","parameterTypes":["java.lang.String"] }]
        }
    ]
    ```

6. Rebuild a native executable and run the resulting file.
    ```shell
    $JAVA_HOME/bin/native-image ReflectionExample
    ./reflectionexample StringCapitalizer capitalize "hello"
    ```
   
   The application should now work as intended.

### Related Documentation

* [Reachability Metadata: Reflection](../ReachabilityMetadata.md#reflection)
* [Assisted Configuration with Tracing Agent](../AutomaticMetadataCollection.md#tracing-agent) 
* [java.lang.reflect Javadoc](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/reflect/package-summary.html)