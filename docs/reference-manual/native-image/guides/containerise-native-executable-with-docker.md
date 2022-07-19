---
layout: ni-docs
toc_group: how-to-guides
link_title: Containerise a Native Executable and Run in a Docker Container
permalink: /reference-manual/native-image/guides/containerise-native-executable-and-run-in-docker-container/
---

# Containerise a Native Executable and Run in a Docker Container

Docker containers provide the flexibility of development environments to match a production environment, to help isolate your application, and to minimize overhead. For self-contained executables, generated with GraalVM Native Image, containers are an obvious deployment scenario.

To support container-based development, there are several GraalVM images available, depending on the platform, the architecture, the Java version, and the edition:

- GraalVM Enterprise container images are in [Oracle Container Registry](https://container-registry.oracle.com/ords/f?p=113:10::::::)
- GraalVM Community container images in [GitHub Container Registry](https://github.com/orgs/graalvm/packages)

## Note on a Sample Application

For the demo you will use the same [Spring Boot "Jibber" application"](https://github.com/graalvm/graalvm-demos/tree/master/spring-native-image) as in the [Build a Spring Boot Application into a Native Executable](build-spring-app-into-native-executable.md) guide. 
You are expected to pass all steps in that tutorial, have a native executable version of the sample application (`target/jibber`) and seen it working.

### Prerequisites

- Docker 

The output of a native executable is platform-dependent.
If you use a Mac or Windows, to build a Docker image containing your native executable you need to build a native executable **within** a Docker container -- so you need a container with a JDK distribution.
If you are a Linux user, you can just pass a native executable to Docker and use the simplest slim or distroless container, depending on static libraries your application is linked against. 
For example:

    ```
    FROM gcr.io/distroless/base
    ARG APP_FILE
    EXPOSE 8080
    COPY target/${APP_FILE} app 
    ENTRYPOINT ["/jibber"]
    ```
For user's convenience, Dockerfiles are provided with the demo. 

We will show how to containerise a Java application with Docker on macOS. 
For that, we will use `ghcr.io/graalvm/jdk:ol8-java17` which is a size compact GraalVM Community container image with the GraalVM JDK pre-installed. 
[This Dockerfile](https://github.com/graalvm/graalvm-demos/blob/master/spring-native-image/Dockerfiles/Dockerfile) will be applied.

1. From application root folder, run this command to create a native executable within a container and then build a Docker image containing that native executable:

    ```shell
    docker build -f Dockerfiles/Dockerfile \
                --build-arg APP_FILE=./target/jibber \
                -t localhost/jibber:native.01 .
    ```
    It will take several minutes to set up Maven in the container and do rest of the job.

2. Query Docker to look at your newly built image:
    ```shell
    docker images | head -n2
    ```
    You should see a new image listed.

3. Run the image as follows:

    ```shell
    docker run --rm --name native -d -p 8080:8080 localhost/jibber:native.01 
    ```
    
4. Then call the endpoint using the `curl` command in the same console window:

    ```shell
    curl http://localhost:8080/jibber
    ```
    You should receive a nonsense verse in the style of the poem Jabberwocky. You can take a look at how long the application took to startup by looking at the logs:

    ```shell
    docker logs <CONTAINER ID>
    ```
    You can also query Docker to get the size of the produced container:
    ```
    docker images localhost/jibber:native.01
    ```
    The difference will be more visible if you build a Docker image of the same Spring Boot application containing a JAR file instead of a native executable, and compare images startup times and file sizes. 
    
On Linux, you can shrink your container size even more.
With GraalVM Native Image you have the ability to build a statically linked native executable by packaging the native executable directly into an empty Docker image, also known as a scratch container. Continue to [Build a Static or Mostly-Static Native Executable guide](build-static-and-mostly-static-executable.md) to learn more.

### Related Documentation

* [GraalVM Native Image, Spring and Containerisation](https://luna.oracle.com/lab/fdfd090d-e52c-4481-a8de-dccecdca7d68)
* [GraalVM Community Images](../../../getting-started/graalvm-community/container-images/graalvm-ce-container-images.md)
* [Build a Static or Mostly-Static Native Executable](build-static-and-mostly-static-executable.md)