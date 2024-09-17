---
layout: ni-docs
toc_group: how-to-guides
link_title: Build a Native Executable from a Spring Boot Application
permalink: /reference-manual/native-image/guides/build-spring-boot-app-into-native-executable/
---

# Build a Native Executable from a Spring Boot Application

GraalVM Native Image can significantly boost the performance of a Spring Boot application.
Spring Boot 3 has integrated support for GraalVM Native Image, making it easier to set up and configure your project.

This guide demonstrates how to build a native executable from a Spring Boot 3 application.

## Create an Application

For the demo part, you will create a simple REST server Java application.

1. Go to [Spring Initializr](https://start.spring.io/#!dependencies=native,web){:target="_blank"} and create a new Spring Boot project. 
Ensure to add the **GraalVM Native Support** and **Spring Web** dependencies.

2. Click GENERATE to create and download the project as a _.zip_ file. Unzip the file and open it in your favorite IDE. 
    
    The project configuration already contains all necessary dependencies and plugins, including [Native Build Tools](https://graalvm.github.io/native-build-tools/latest/index.html){:target="_blank"}. 
    For example, if you created a Maven project, these are the required plugins added in the _pom.xml_ file:
    ```xml
	<build>
		<plugins>
			<plugin>
				<groupId>org.graalvm.buildtools</groupId>
				<artifactId>native-maven-plugin</artifactId>
			</plugin>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>
		</plugins>
	</build>
    ```

3. The main application class was created by the initializer. In the same directory, create a REST controller in a file named _HelloController.java_ with the following contents:
    ```java
    package com.example.demo;

    import org.springframework.web.bind.annotation.GetMapping;
    import org.springframework.web.bind.annotation.RestController;

    @RestController
    public class HelloController {

        @GetMapping("/hello")
        public String hello() {
            return "Hello, GraalVM!";
        }
    }
    ```

4. (Optional) Package and run the application on a Java HotSpot Virtual Machine.  
    Maven:
    ```bash
    ./mvnw spring-boot:run
    ```
    Gradle: 
    ```bash
    ./gradlew bootRun
    ```
    It compiles the application, creates a JAR file, and runs the application. 

    The application starts in hundreds of milliseconds.
    Open a browser and navigate to [localhost:8080/hello](http://localhost:8080/hello){:target="_blank"} to see the application running. 
    You should see "Hello, GraalVM!". 

## Build a Native Executable Using Paketo Buildpacks

Spring Boot supports building container images containing native executables using the [Paketo Buildpack for Oracle](https://github.com/paketo-buildpacks/oracle) which provides GraalVM Native Image. 

### Prerequisite
Make sure you have a Docker-API compatible container runtime such as [Rancher Desktop](https://docs.rancherdesktop.io/getting-started/installation/){:target="_blank"} or [Docker](https://www.docker.com/gettingstarted/){:target="_blank"} installed and running.

1. First, enable the [Paketo Buildpack for Oracle](https://github.com/paketo-buildpacks/oracle){:target="_blank"} requesting the Native Image tool.

    - **Maven**. Open the _pom.xml_ file, find the `spring-boot-maven-plugin` declaration, and change it so that it looks like this: 
        ```xml
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <configuration>
                <image>
                    <builder>paketobuildpacks/builder-jammy-buildpackless-tiny</builder><!--required for AArch64/M1 support -->
                    <buildpacks>
                        <buildpack>paketobuildpacks/oracle</buildpack>
                        <buildpack>paketobuildpacks/java-native-image</buildpack>
                    </buildpacks>
                </image>
            </configuration>
        </plugin>
        ```

        You should also ensure that your _pom.xml_ file uses `spring-boot-starter-parent`. 
        The `<parent>` section should have been added by the initializer.

    - **Gradle**. Open the _build.gradle_ file, and add the following lines:
        ```
        bootBuildImage {
                builder = "paketobuildpacks/builder-jammy-buildpackless-tiny"
                buildpacks = ["paketobuildpacks/oracle", "paketobuildpacks/java-native-image"]
        }
        ```
    When `java-native-image` is requested, the buildpack downloads Oracle GraalVM, which includes Native Image.

2. Build a native executable for this Spring application using buildpacks:
    - Maven: 
        ```bash
        ./mvnw -Pnative spring-boot:build-image
        ```

    - Gradle:
        ```bash
        ./gradlew bootBuildImage
        ```

3. Once the build completes, a Docker image should be available. You can start your application using `docker run`. For example:
    ```bash
    docker run --rm -p 8080:8080 docker.io/library/demo:0.0.1-SNAPSHOT
    ```

The [Paketo documentation provides several examples](https://paketo.io/docs/howto/java/#build-an-app-as-a-graalvm-native-image-application){:target="_blank"} that show you how to build applications with GraalVM Native Image using buildpacks. 

## Build a Native Executable Using Native Build Tools

If you do not want to use Docker and create a native executable on a host machine, use [Native Build Tools](https://graalvm.github.io/native-build-tools/latest/index.html){:target="_blank"} which provide Maven and Gradle plugins for building native images. 

### Prerequisite
Make sure you have installed a GraalVM JDK.
The easiest way to get started is with [SDKMAN!](https://sdkman.io/jdks#graal){:target="_blank"}:
```bash
sdk install java 21.0.4-graal
```
Substitute `21.0.4` with a preferred GraalVM release or early access build.
For other installation options, visit the [Downloads section](https://www.graalvm.org/downloads/).

1. Build a native executable using Native Build Tools:
    - Maven: 
        ```bash
        ./mvnw -Pnative native:compile
        ```
        The command compiles the project and creates a native executable, `demo`, in the _target/_ directory.

    - Gradle:
        ```bash
        ./gradlew nativeCompile
        ```
        The command compiles the project and creates a native executable, `demo`, in the _build/native/nativeCompile/_ directory.

2. Run the application from the native executable:
    - Maven:
        ```bash
        ./target/demo
        ```
    - Gradle:
        ```bash
        ./build/native/nativeCompile/demo
        ```
        With Gradle, you can also execute the `nativeRun` task: `gradle nativeRun`.

        If you ran this application on HotSpot before, you would notice that startup time decreased significantly.

This guide demonstrated how you can create a native executable for a Spring Boot application. 
You can do that in a container environment using Paketo Buildpacks, or on a host machine using Native Build Tools.

A Spring Boot application when compiled ahead of time into a native executable is not only faster and lighter, but also more efficient, especially in environments with constrained resources such as cloud platforms or containers.

### Related Documentation

* [Developing Your First GraalVM Native Application](https://docs.spring.io/spring-boot/docs/3.0.0/reference/htmlsingle/#native-image.developing-your-first-application){:target="_blank"}
* [Paketo Buildpack for Oracle](https://github.com/paketo-buildpacks/oracle){:target="_blank"}
* [Native Build Tools](https://graalvm.github.io/native-build-tools/latest/index.html){:target="_blank"}