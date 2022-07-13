---
layout: ni-docs
toc_group: how-to-guides
link_title: Build a Spring Boot Application into a Native Executable
permalink: /reference-manual/native-image/guides/build-spring-boot-app-into-native-executable/
---

# Build a Spring Boot Application into a Native Executable

To package a Spring Boot application into a native executable, you need to use **Spring Native**, and add **Maven/Gradle plugin for GraalVM Native Image (Native Build Tools)** to automate the process.

- [Spring Native](https://docs.spring.io/spring-native/docs/current/reference/htmlsingle/#overview) project provides support for compiling Spring applications ahead-of-time using GraalVM Native Image and eventually packaging them into lightweight containers. The target is to support any Spring applications, almost unmodified.

- [Native Build Tools](https://graalvm.github.io/native-build-tools/) provide Maven and Gradle plugins to add support for building Java applications into native executables and testing them using [Apache Maven™](https://maven.apache.org/). These plugins are maintained by the GraalVM team.

In this guide you will learn how to use Spring Native and the [Maven plugin for GraalVM Native Image](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html) to add basic support and build a native executable for a Spring Boot application.

### Note on a Sample Application

For the demo part, we will use a minimal REST-based API application, built on top of Spring Boot:

- `com.example.demo.DemoApplication`: the main Spring Boot class that defines the HTTP endpoint.
- `com.example.demo.Jabberwocky`: a utility class that implements the logic of the application.

If we call the HTTP endpoint, `/jibber`, it will return some nonsense verse generated in the style of the Jabberwocky poem, by Lewis Carroll. 
The program achieves this by using a Markov Chain to model the original poem (this is essentially a statistical model). 
This model generates a new text.
The example application provides the text of the poem, then generates a model of the text, which the application then uses to generate a new text that is similar to the original text. 
The application uses the `RiTa` library as an external dependency to build and use Markov Chains.

The `pom.xml` file was generated using [Spring Initializr](https://start.spring.io/) with Spring Native Tools added as a feature.

Now we will go step-by-step explaining what necessary dependencies should be added to successfully convert a Spring Boot application into a native executable. 

1. Clone the demo:

    ```shell
    git clone https://github.com/graalvm/graalvm-demos.git
    ```
    Find **spring-native-image** and open the demo folder in your favourite code editor or IDE.
    Open the Maven configuration file, `pom.xml`, to review what necessary dependencies were added.

2. Find the [Spring AOT plugin](https://docs.spring.io/spring-native/docs/current/reference/htmlsingle/#spring-aot) added to the default build configuration in plugins section. It performs ahead-of-time transformations of a Spring application into a native executable.

    ```xml
    <plugin>
        <groupId>org.springframework.experimental</groupId>
        <artifactId>spring-aot-maven-plugin</artifactId>
        <version>${spring-native.version}</version>
        <executions>
            <execution>
                <id>test-generate</id>
                <goals>
                    <goal>test-generate</goal>
                </goals>
            </execution>
            <execution>
                <id>generate</id>
                <goals>
                    <goal>generate</goal>
                </goals>
            </execution>
        </executions>
    </plugin>
    ```
    This is the first dependency that you have add to your Spring Boot projects if you plan to target GraalVM Native Image.        

3. Notice the Spring Native dependency in  `<dependencies>` section, which provides native configuration APIs and other mandatory classes required to run a Spring application as a native executable. You need to specify it explicitly only with Maven.
    ```xml
    <dependency>
        <groupId>org.springframework.experimental</groupId>
        <artifactId>spring-native</artifactId>
        <version>${spring-native.version}</version>
    </dependency>
    ```
    This is another required dependency.

4. See the required repositories added for Maven:

     - The repository for the `spring-native` dependency:

        ```xml
        <repositories>
            <repository>
                <id>spring-release</id>
                <name>Spring release</name>
                <url>https://repo.spring.io/release</url>
                <snapshots>
				    <enabled>false</enabled>
			    </snapshots>
            </repository>
        </repositories>
        ```
    -  The plugin's repository for the Spring AOT plugin:

        ```xml
        <pluginRepositories>
            <pluginRepository>
                <id>spring-release</id>
                <name>Spring release</name>
                <url>https://repo.spring.io/release</url>
                <snapshots>
				    <enabled>false</enabled>
			    </snapshots>
            </pluginRepository>
        </pluginRepositories>
        ```
5. So far you learned how to configure build of Spring applications specifically. The next step is adding the Maven plugin for GraalVM Native Image that is a common requirement for any Java application. Find the `org.graalvm.buildtools:native-maven-plugin` plugin configuration in `pom.xml`:

    ```xml
    <plugin>
        <groupId>org.graalvm.buildtools</groupId>
        <artifactId>native-maven-plugin</artifactId>
        <version>${native-buildtools.version}</version>
        <executions>
            <execution>
                <id>build-native</id>
                <phase>package</phase>
                <goals>
                    <goal>build</goal>
                </goals>
            </execution>
        </executions>
        <configuration>
            <imageName>${binary-name}</imageName>
            <skip>${skip-native-build}</skip>
            <buildArgs>
                <buildArg>-H:+ReportExceptionStackTraces ${native-image-extra-flags}</buildArg>
            </buildArgs>
        </configuration>
    </plugin>
    ```

    Notice how we pass the configuration arguments to the underlying `native-image` tool using the `<buildArgs>` section. In individual `buildArg` tags, you can pass all Native Image parameters as you would pass them to the `native-image` tool on the command line.

6. To avoid classes clash between Spring Boot packaging and the `native-maven-plugin` build, we customized a Spring Boot classifier:

    - Included `<repackage.classifier/>` into project's general properties:
        ```xml
        <properties>
            <java.version>17</java.version>
            <repackage.classifier/>
            <spring-native.version>0.11.4</spring-native.version>
        </properties>
        ```

    - Modified `native` profile so to include `repackage.classifier`:
        ```xml
        <profiles>
            <profile>
                <id>native</id>
                <properties>
                    <repackage.classifier>exec</repackage.classifier>
                </properties>
                ...
            </profile>
        </profiles>
        ```

    - Modified the `spring-boot-maven-plugin` configuration to include the classifier:
        ```xml
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <configuration>
                <classifier>${repackage.classifier}</classifier>
                <image>
                    <builder>paketobuildpacks/builder:tiny</builder>
                    <env>
                        <BP_NATIVE_IMAGE>true</BP_NATIVE_IMAGE>
                    </env>
                </image>
            </configuration>
        </plugin>
        ```

    This Spring Boot application is now ready to be built and compiled ahead-of-time into a native executable.

7. From a console window, enter the application root directory and build the application:

    ```shell
    cd graalvm-demos/spring-native-image && mvn clean package
    ```
    This will generate a runnable JAR file that contains all of the application's dependencies and also a correctly configured `MANIFEST` file. 

    As a nice extra, there is also a [Dockerfile](https://github.com/graalvm/graalvm-demos/blob/master/spring-native-image/Dockerfiles/Dockerfile) provided with this demo. 
    So, besides building the application JAR, you see a Docker image being built at `mvn clean package` step, pulling the GraalVM container image, `ghcr.io/graalvm/jdk:ol8-java17`, as the JVM.

    You can also test running this application from a JAR:
    ```shell
    java -jar ./target/benchmark-jibber-0.0.1-SNAPSHOT.jar &
    ```
    where `&` brings the application to the background.
    Call the endpoint:
    ```shell
    curl http://localhost:8080/jibber
    ```
    Bring the app back to the foreground and terminate:
    ```shell
    fg
    ctrl+C
    ```

8. Next build a native executable for this Spring Boot application using the Maven profile:

    ```shell
    mvn package -Dnative
    ```
    It will generate a native executable for the platform in the target directory, called `jibber`.

9. Run the application from a native executable. Execute the following command in your terminal and put it into the background, using `&`:

    ```shell
    ./target/jibber &
    ```
    Call the endpoint to test it using the `curl` command:

    ```shell
    curl http://localhost:8080/jibber
    ```
    Notice how fast this native version of your Spring Boot application starts. It also uses fewer resources than running from JAR.

    You should get some nonsense verse back. To terminate it, first bring the application to the foreground, `fg` and kill `<ctrl-c>`.

    Last, check the executable file size to compare the footprint:
    ```shell
    ls -lh target/jibber
    ```

You can further "shrink" this native executable by [containerising and running it from a Docker container](containerise-native-executable-with-docker.md).

### Related Documentation

- Run an interactive lab: [GraalVM Native Image, Spring and Containerisation](https://luna.oracle.com/lab/fdfd090d-e52c-4481-a8de-dccecdca7d68). This lab will also show how to create small Distroless containers to package your GraalVM Native Image native executables in, allowing you to shrink your Docker Images even further.
- Package a Spring application to lightweight Docker container containing a native executable with [Spring Boot Buildpacks](https://docs.spring.io/spring-native/docs/current/reference/htmlsingle/#getting-started-buildpacks).
- Learn more about the [Spring Native project](https://docs.spring.io/spring-native/docs/current/reference/htmlsingle/#getting-started).
- Read more about [Native Build Tools](https://graalvm.github.io/native-build-tools/).