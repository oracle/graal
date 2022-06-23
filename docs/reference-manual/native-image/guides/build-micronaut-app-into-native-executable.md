---
layout: ni-docs
toc_group: how-to-guides
link_title: Build a Micronaut Application into a Native Executable
permalink: /reference-manual/native-image/guides/build-micronaut-app-into-native-executable/
---

# Build a Micronaut Application into a Native Executable

This guide shows how to create a simple [Micronaut](https://micronaut.io/) application, and compile it into a native executable with GraalVM Native Image.

1. Create a sample Micronaut REST application. Go to [Micronaut Launch](https://micronaut.io/launch/). Select **Micronaut Application** as application type. Click **FEATURES**, search and add **graalvm** packaging feature. You can keep rest values as default and click **Generate Project**. Then **Download ZIP**.

2. Unzip the downloaded package and enter application directory:

    ```shell
    unzip demo.zip && cd demo
    ```
    
    Assume you selected Maven as the build tool, Micronaut Launch would create a folder with a complete Micronaut application skeleton:
    ```
    ├── README.md
    ├── micronaut-cli.yml
    ├── mvnw
    ├── mvnw.bat
    ├── pom.xml
    └── src
        ├── main
        │   ├── java
        │   │   └── com
        │   │       └── example
        │   │           └── Application.java
        │   └── resources
        │       ├── application.yml
        │       └── logback.xml
        └── test
            └── java
                └── com
                    └── example
                        └── DemoTest.java
    ```      
    Now you will modify this template application and add a rest endpoint that will return a simple message.

3. Under _src/main/java/com/example_ create a POJO in a file named _Conference.java_ with the following content:
    ```java
    package com.example;

    import io.micronaut.core.annotation.Introspected;

    @Introspected 
    public class Conference {

        private final String name;

        public Conference(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
    ```
    Notice the `@Introspected` annotation before the class name to generate `BeanIntrospection` metadata at compilation time. 
    This information can be used, for example, to the render the POJO as JSON using Jackson without using reflection.

4. Next create a _ConferenceService.java_ service in the same location:

    ```java
    package com.example;

    import jakarta.inject.Singleton;
    import java.util.Arrays;
    import java.util.List;
    import java.util.Random;

    @Singleton 
    public class ConferenceService {

        private static final List<Conference> CONFERENCES = Arrays.asList(
                new Conference("Greach"),
                new Conference("GR8Conf EU"),
                new Conference("Micronaut Summit"),
                new Conference("Devoxx Belgium"),
                new Conference("Oracle Code One"),
                new Conference("CommitConf"),
                new Conference("Codemotion Madrid")
        );

        public Conference randomConf() { 
            return CONFERENCES.get(new Random().nextInt(CONFERENCES.size()));
        }
    }
    ```
    The service returns a random conference name. 
    Notice `jakarta.inject.Singleton` to designate a class as a singleton.

5. Finally, create a Micronaut controller as a REST endpoint that returns a `Conference` in a file named _ConferenceController.java_. The Micronaut framework will convert it automatically to JSON in the response: 

    ```java
    package com.example;

    import io.micronaut.http.annotation.Controller;
    import io.micronaut.http.annotation.Get;

    @Controller("/conferences") 
    public class ConferenceController {

        private final ConferenceService conferenceService;

        public ConferenceController(ConferenceService conferenceService) { 
            this.conferenceService = conferenceService;
        }

        @Get("/random") 
        public Conference randomConf() { 
            return conferenceService.randomConf();
        }
    }
    ```
    The class is defined as a controller with the `@Controller` annotation mapped to the path `/conferences`. The `@Get` annotation maps the index method to an HTTP GET request on `/random`.

6. Now since the application is ready, generate a native executable using GraalVM Native Image:

    - If the application was built with Maven, specify the `native-image` packaging format:
        ```shell
        ./mvnw package -Dpackaging=native-image
        ```

    - If you used using Gradle, execute the `nativeImage` task:
        ```shell
        ./gradlew nativeCompile
        ```

    After some time a native executable called `demo` will be built into the `/target` directory. If you use Gradle, the executable called `demo` will be written to the `/build/native/nativeCompile/` folder.

7. Execute the application by running the executable:
    Maven:
    ```shell
    ./target/demo
    ```

    Gradle:
    ```
    ./build/native/nativeCompile/demo
    ```
    Send a request to test it:
    ```shell
    time curl localhost:8080/conferences/random
    ```

8. Run this application regularly, from a JAR on a JVM, to compare execution time:

    ```shell
    ./mvnw mn:run
    ```
    
    Notice the startup time. Deploying a Micronaut application as a native executable helps to achieve instantaneous startup, lower CPU and memory consumption, making the application cloud native and ready for cloud or on-premises deployments.

As a nice extra, you can also create a native executable inside Docker. You do not need to install any additional dependencies.

```shell
./mvnw package -Dpackaging=docker-native
```

The output is a platform-dependent Docker image containing a native executable of your Micronaut applocation.

### Related Documentation

* [Creating your first Micronaut GraalVM application](https://guides.micronaut.io/latest/micronaut-creating-first-graal-app.html)
* [Use Maven to Build Java Applications into Native Executables](use-native-image-maven-plugin.md)