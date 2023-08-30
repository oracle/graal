---
layout: ni-docs
toc_group: how-to-guides
link_title: Containerise a Native Executable and Run in a Docker Container
permalink: /reference-manual/native-image/guides/containerise-native-executable-and-run-in-docker-container/
---

# Containerise a Native Executable and Run in a Docker Container

Docker containers provide the flexibility of development environments to match a production environment, to help isolate your application, and to minimize overhead. 
For self-contained executables, generated with GraalVM Native Image, containers are an obvious deployment scenario.

To support container-based development, there are several GraalVM container images available, depending on the platform, the architecture, the Java version, and the edition:

- Oracle GraalVM container images, available in [Oracle Container Registry (OCR)](https://container-registry.oracle.com) under the [GraalVM Free Terms and Conditions (GFTC) license](https://www.oracle.com/downloads/licenses/graal-free-license.html).
- GraalVM Community Edition container images published in the [GitHub Container Registry](https://github.com/orgs/graalvm/packages).

This guide shows how to containerise a Java application with Docker.
You will use a size compact GraalVM Community container image containing the Native Image and Oracle Linux 8 environment to easily compile a Java application ahead-of-time.
The Dockerfile will be provided.

## Note on a Sample Application

For the demo you will use the [Spring Boot 3 Native Image Microservice example](https://github.com/graalvm/graalvm-demos/blob/master/spring-native-image/README.md). 

1. Download and install the latest Oracle GraalVM.
The easiest way to get started is with [SDKMAN!](https://sdkman.io/jdks#graal):
    ```bash
    sdk install java 17.0.8-graal 
    ```
    For other installation options, go to [Downloads](https://www.graalvm.org/downloads/).

2. Install and run Docker. See [Get Docker](https://docs.docker.com/get-docker/#installation) for more details. Configure it to [allow non-root user](https://docs.docker.com/engine/install/linux-postinstall/#manage-docker-as-a-non-root-user) if you are on Linux.

3. Clone the [GraalVM Demos repository](https://github.com/graalvm/graalvm-demos) and enter the application directory:

    ```shell
    git clone https://github.com/graalvm/graalvm-demos
    ```
    ```shell
    cd spring-native-image
    ```

3. Build a native executable and run the application:

    ```shell
    ./mvnw native:compile -Pnative
    ```
    The `-Pnative` profile is used to turn on building a native executable with Maven.

    This will create a binary executable `target/benchmark-jibber`. Start it to see the application running:

    ```shell
    ./target/benchmark-jibber &
    curl http://localhost:8080/jibber
    fg
    ```
        
    Alternatively, to build using BuildPacks, run the `./mvnw spring-boot:build-image -Pnative` command to generate a native executable. 
    For more information about using BuildPacks to create a native executable, see [Building a Native Image Using Buildpacks](https://docs.spring.io/spring-boot/docs/3.0.0/reference/html/native-image.html#native-image.developing-your-first-application.buildpacks).

Now that you have a native executable version of the sample application (`target/jibber`) and seen it working, you can proceed to the next steps.

## Containerise a Native Executable

The output of a native executable is platform-dependent.
If you use a Mac or Windows, to build a Docker image containing your native executable, you build a native executable **within** a Docker container - so you need a container with a JDK distribution.
If you are a Linux user, you can just pass a native executable to Docker and use the simplest slim or distroless container, depending on static libraries your application is linked against. 
For example:

```
FROM gcr.io/distroless/base
ARG APP_FILE
EXPOSE 8080
COPY target/${APP_FILE} app 
ENTRYPOINT ["/jibber"]
```

1. Build a Docker image of your application. For convenience, Dockerfiles are provided for Linux-only and Multistage Docker builds with the sample application. 
    - On Linux, containerise the native executable using the following command:
        ```shell
        docker build -f Dockerfiles/Dockerfile.native --build-arg APP_FILE=benchmark-jibber -t jibber-benchmark:native.0.0.1-SNAPSHOT .
        ```

    - On MacOS and Windows, you need to build the native executable inside a Docker container. 
    From application root folder, run this command to create a native executable and then build a Docker image containing that native executable:
        ```shell
        docker build -f Dockerfiles/Dockerfile -t jibber-benchmark:native.0.0.1-SNAPSHOT .
        ```
        It will take several minutes to set up Maven in the container and do rest of the job.

2. Query Docker to look at your newly built image:
    ```shell
    docker images | head -n2
    ```
    You should see a new image listed.

3. Run the application as follows:
    ```shell
    docker run --rm --name native -p 8080:8080 jibber-benchmark:native.0.0.1-SNAPSHOT
    ```
    
4. Open the application in a browser, or call the endpoint using the `curl` command from a new terminal window:
    ```shell
    curl http://localhost:8080/jibber
    ```
    You should receive a nonsense verse in the style of the poem Jabberwocky.

5. To stop the application, first get the container id using `docker ps`, and then run:
```bash
docker rm -f <container_id>
```   
    
You can take a look at how long the application took to startup by looking at the logs:

```shell
docker logs <CONTAINER ID>
```
You can also query Docker to get the size of the produced container:
```
docker images jibber-benchmark:native.0.0.1-SNAPSHOT
```

The difference will be more visible if you build a Docker image of the same Spring Boot application containing a JAR file instead of a native executable, and compare images startup times and file sizes. 
    
On Linux, you can shrink your container size even more.
With GraalVM Native Image you have the ability to build a statically linked native executable by packaging the native executable directly into an empty Docker image, also known as a scratch container. 
Continue to [Build a Static or Mostly-Static Native Executable guide](build-static-and-mostly-static-executable.md) to learn more.

### Related Documentation

* [GraalVM Native Image, Spring and Containerisation](https://luna.oracle.com/lab/fdfd090d-e52c-4481-a8de-dccecdca7d68)
* [Build a Static or Mostly-Static Native Executable](build-static-and-mostly-static-executable.md)
* [Oracle GraalVM Container Images](https://container-registry.oracle.com/)