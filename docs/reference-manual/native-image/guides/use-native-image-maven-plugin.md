---
layout: ni-docs
toc_group: how-to-guides
link_title: Use Native Image Maven Plugin
permalink: /reference-manual/native-image/guides/use-native-image-maven-plugin/
---

# Use Maven to Build a Native Executable from a Java Application

You can use the Maven plugin for GraalVM Native Image to build a native executable from a Java application in one step, in addition to a runnable JAR. 
The plugin is provided as part of the [Native Build Tools project](https://graalvm.github.io/native-build-tools/latest/index.html) and uses Apache Maven™. 
The plugin makes use of Maven profiles to build and test native executables.

This guide shows you how to use the Native Image Maven plugin to build a native executable from a Java application, run JUnit tests, and add support for Java dynamic features.

You will use a **Fortune demo** application that simulates the traditional [fortune Unix program](https://en.wikipedia.org/wiki/Fortune_(Unix)). The data for the fortune phrases is provided by [YourFortune](https://github.com/your-fortune).

We recommend that you follow the instructions and create the application step-by-step. Alternatively, you can use an existing project: clone the [GraalVM demos repository](https://github.com/graalvm/graalvm-demos) and navigate into the `fortune-demo/fortune` directory:
```shell
git clone https://github.com/graalvm/graalvm-demos && cd graalvm-demos/fortune-demo/fortune
```

> You must have [GraalVM installed with Native Image support](../README.md#install-native-image).

## Prepare a Demo Application

1. Create a new Java project with **Maven** in your favorite IDE, called "Fortune", in the `demo` package. The application should contain a sole Java file with the following content:

    ```java
    package demo;
    
    import com.fasterxml.jackson.core.JsonProcessingException;
    import com.fasterxml.jackson.databind.JsonNode;
    import com.fasterxml.jackson.databind.ObjectMapper;
    import java.io.BufferedReader;
    import java.io.IOException;
    import java.io.InputStream;
    import java.io.InputStreamReader;
    import java.nio.charset.StandardCharsets;
    import java.util.ArrayList;
    import java.util.Iterator;
    import java.util.Random;
    import java.util.logging.Level;
    import java.util.logging.Logger;

    public class Fortune {

        private static final Random RANDOM = new Random();
        private final ArrayList<String> fortunes = new ArrayList<>();

        public Fortune() throws JsonProcessingException {
            // Scan the file into the array of fortunes
            String json = readInputStream(ClassLoader.getSystemResourceAsStream("fortunes.json"));
            ObjectMapper omap = new ObjectMapper();
            JsonNode root = omap.readTree(json);
            JsonNode data = root.get("data");
            Iterator<JsonNode> elements = data.elements();
            while (elements.hasNext()) {
                JsonNode quote = elements.next().get("quote");
                fortunes.add(quote.asText());
            }      
        }
        
        private String readInputStream(InputStream is) {
            StringBuilder out = new StringBuilder();
            try (InputStreamReader streamReader = new InputStreamReader(is, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(streamReader)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line);
                }

            } catch (IOException e) {
                Logger.getLogger(Fortune.class.getName()).log(Level.SEVERE, null, e);
            }
            return out.toString();
        }
        
        private void printRandomFortune() throws InterruptedException {
            //Pick a random number
            int r = RANDOM.nextInt(fortunes.size());
            //Use the random number to pick a random fortune
            String f = fortunes.get(r);
            // Print out the fortune s.l.o.w.l.y
            for (char c: f.toCharArray()) {
                System.out.print(c);
                Thread.sleep(100);   
            }
            System.out.println();
        }
    
        /**
        * @param args the command line arguments
        * @throws java.lang.InterruptedException
        * @throws com.fasterxml.jackson.core.JsonProcessingException
        */
        public static void main(String[] args) throws InterruptedException, JsonProcessingException {
            Fortune fortune = new Fortune();
            fortune.printRandomFortune();
        }
    }
    ```

2. Copy and paste the following file, [fortunes.json](https://github.com/graalvm/graalvm-demos/blob/master/fortune-demo/fortune/src/main/resources/fortunes.json) under `resources/`. Your project tree should be:

    ```shell
    .
    ├── pom.xml
    └── src
        └── main
            ├── java
            │   └── demo
            │       └── Fortune.java
            └── resources
                └── fortunes.json
    ```

3. Add explicit FasterXML Jackson dependencies that provide functionality to read and write JSON, data-binding (used in the demo application). Open the _pom.xml_ file (a Maven configuration file), and insert the following in the `<dependencies>` section:

    ```xml
    <dependencies>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.12.6.1</version>
        </dependency>
    </dependencies>
    ```

4. Add regular Maven plugins for building and assembling a Maven project into an executable JAR. Insert the following into the `build` section in the  _pom.xml_ file:
    ```xml
    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.0.0</version>
                <executions>
                    <execution>
                        <id>java</id>
                        <goals>
                            <goal>java</goal>
                        </goals>
                        <configuration>
                            <mainClass>${mainClass}</mainClass>
                        </configuration>
                    </execution>
                </executions>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.8.1</version>
                <configuration>
                    <source>${maven.compiler.source}</source>
                    <target>${maven.compiler.source}</target>
                </configuration>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.2.2</version>
                <configuration>
                    <archive>
                        <manifest>
                            <addClasspath>true</addClasspath>
                            <mainClass>${mainClass}</mainClass>
                        </manifest>
                    </archive>
                </configuration>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-assembly-plugin</artifactId>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>single</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <archive>
                        <manifest>
                            <addClasspath>true</addClasspath>
                            <mainClass>${mainClass}</mainClass>
                        </manifest>
                    </archive>
                    <descriptorRefs>
                        <descriptorRef>jar-with-dependencies</descriptorRef>
                    </descriptorRefs>
                </configuration>
            </plugin>

        </plugins>
    </build>
    ```

5. Replace the default `<properties>` section in the _pom.xml_ file with this content:

    ```xml
    <properties>
        <native.maven.plugin.version>0.9.12</native.maven.plugin.version>
        <junit.jupiter.version>5.8.1</junit.jupiter.version>
        <maven.compiler.source>${java.specification.version}</maven.compiler.source>
        <maven.compiler.target>${java.specification.version}</maven.compiler.target>
        <imageName>fortune</imageName>
        <mainClass>demo.Fortune</mainClass>
    </properties>
    ```
    The statements "hardcoded" plugin versions and the entry point class to your application.
    The next steps will show you how enable the Maven plugin for GraalVM Native Image.

6. Register the Maven plugin for GraalVM Native Image, `native-maven-plugin`, in the profile called `native` by adding the following to the  _pom.xml_ file:
    ```xml
    <profiles>
        <profile>
            <id>native</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.graalvm.buildtools</groupId>
                        <artifactId>native-maven-plugin</artifactId>
                        <version>${native.maven.plugin.version}</version>
                        <extensions>true</extensions>
                        <executions>
                            <execution>
                                <id>build-native</id>
                                <goals>
                                    <goal>build</goal>
                                </goals>
                                <phase>package</phase>
                            </execution>
                            <execution>
                                <id>test-native</id>
                                <goals>
                                    <goal>test</goal>
                                </goals>
                                <phase>test</phase>
                            </execution>
                        </executions>
                        <configuration>
                            <fallback>false</fallback>
                            <buildArgs>
                                <arg>-H:DashboardDump=fortune -H:+DashboardAll</arg>
                            </buildArgs>
                            <agent>
                                <enabled>true</enabled>
                                <options>
                                    <option>experimental-class-loader-support</option>
                                </options>
                            </agent>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
    ```
    The plugin discovers which JAR files it needs to pass to the `native-image` builder and what the executable main class should be. With this plugin you can already build a native executable directly with Maven by running `mvn -Pnative package` (if your application does not call any methods reflectively at run time).
    
    This demo application is a little more complicated than `HelloWorld`, and and [requires metadata](../ReachabilityMetadata.md) before building a native executable. You do not have to configure anything manually: the Native Image Maven plugin can generate the required metadata for you by injecting the [Java agent](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html#agent-support) at package time. The agent is disabled by default, and can be enabled in project's _pom.xml_ file or via the command line.
    - To enable the agent via the _pom.xml_ file, specify `<enabled>true</enabled>` in the `native-maven-plugin` plugin configuration:

        ```xml
        <configuration>
        <agent>
            <enabled>true</enabled>
        </agent>
        </configuration>
        ```

    - To enable the agent via the command line, pass the `-Dagent=true` option when running Maven.
    So your next step is to run with the agent.

7. Before running with the agent, register a separate Mojo execution in the `native` profile which allows forking the Java process. It is required to run your application with the agent.
    ```xml
    <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.0.0</version>
        <executions>
            <execution>
                <id>java-agent</id>
                <goals>
                    <goal>exec</goal>
                </goals>
                <configuration>
                    <executable>java</executable>
                    <workingDirectory>${project.build.directory}</workingDirectory>
                    <arguments>
                        <argument>-classpath</argument>
                        <classpath/>
                        <argument>${mainClass}</argument>
                    </arguments>
                </configuration>
            </execution>
            <execution>
                <id>native</id>
                <goals>
                    <goal>exec</goal>
                </goals>
                <configuration>
                    <executable>${project.build.directory}/${imageName}</executable>
                    <workingDirectory>${project.build.directory}</workingDirectory>
                </configuration>
            </execution>
        </executions>
    </plugin>
    ``` 
    Now you are all set to to build a native executable from a Java application using the Native Image Maven plugin.

## Build a Native Executable with Maven

1. Compile the project on the Java VM to create a runnable JAR with all dependencies. Open a terminal window and, from the root application directory, run:

    ```shell
    mvn clean package
    ```

2. Run your application with the agent enabled:

    ```shell
    mvn -Pnative -Dagent exec:exec@java-agent
    ```
    The agent generates the configuration files in a subdirectory of `target/native/agent-output`. Those files will be automatically used by the `native-image` tool if you pass the appropriate options. 

3. Now build a native executable directly with Maven:

    ```shell
    mvn -Pnative -Dagent package
    ```
    When the command completes a native executable, _fortune_, is created in the _/target_ directory of the project and ready for use.

    The executable's name is derived from the artifact ID, but you can specify any custom name in the `native-maven-plugin` plugin within a <configuration> node:

    ```xml
    <configuration>
        <imageName>fortuneteller</imageName>
    </configuration>
    ```

4. Run the demo directly or with the Maven profile:

    ```shell
    ./target/fortune
    ```

    ```shell
    mvn -Pnative exec:exec@native
    ```

To see the benefits of running your application as a native executable, `time` how long it takes and compare the results with running on the JVM.

## Add JUnit Testing

The Maven plugin for GraalVM Native Image can run [JUnit Platform](https://junit.org/junit5/docs/current/user-guide/) tests on your native executable. This means that tests will be compiled and executed as native code.

This plugin requires JUnit Platform 1.8 or higher and Maven Surefire 2.22.0 or higher to run tests on a native executable.

1. Enable extensions in the plugin's configuration, `<extensions>true</extensions>`:

    ```xml
    <plugin>
        <groupId>org.graalvm.buildtools</groupId>
        <artifactId>native-maven-plugin</artifactId>
        <version>${native.maven.plugin.version}</version>
        <extensions>true</extensions>
    ```

2. Add an explicit dependency on the `junit-platform-launcher` artifact to the dependencies section of your native profile configuration as in the following example:

    ```xml
    <dependencies>
        <dependency>
            <groupId>org.junit.platform</groupId>
            <artifactId>junit-platform-launcher</artifactId>
            <version>1.8.2</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    ```

3. Run native tests:

    ```shell
    mvn -Pnative test
    ```
    Run `-Pnative` profile will then build and run native tests.

### Summary

The Native Image Maven plugin has many more configuration options. For more information, see the [plugin documentation](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html).

Note that if your application does not call dynamically any classes at run time, the execution with the agent is needless. 
Your workflow in that case you just be:

```shell
mvn clean compile
mvn -Pnative package
```

Another advantage of the plugin is that if you use GraalVM Enterprise as your `JAVA_HOME` environment, the plugin builds a native executable with enterprise features enabled.

### Related Documentation

- [Collect Metadata with the Tracing Agent](../AutomaticMetadataCollection.md#tracing-agent)
- [Maven plugin for GraalVM Native Image building](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html)