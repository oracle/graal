---
layout: ni-docs
toc_group: how-to-guides
link_title: Containerize a Native Executable and Run in a Container
permalink: /reference-manual/native-image/guides/containerise-native-executable-and-run-in-docker-container/
---

# Containerize a Native Executable and Run in a Container

Containers provide the flexibility of development environments to match a production environment, to help isolate your application, and to minimize overhead. 
For self-contained executables, generated with GraalVM Native Image, containers are an obvious deployment choice.

To support container-based development, there are several GraalVM container images available, depending on the platform, the architecture, the Java version, and the edition:

- Oracle GraalVM container images, available in [Oracle Container Registry (OCR)](https://container-registry.oracle.com/ords/ocr/ba/graalvm) under the [GraalVM Free Terms and Conditions (GFTC) license](https://www.oracle.com/downloads/licenses/graal-free-license.html).
- GraalVM Community Edition container images published in the [GitHub Container Registry](https://github.com/orgs/graalvm/packages).

This guide shows how to containerize a native executable for your Java application.
You will use a GraalVM container image with Native Image to compile a Java application ahead-of-time into a native executable.

## Download a Sample Application

This guide uses the [Spring Boot 3 Native Image Microservice example](https://github.com/graalvm/graalvm-demos/blob/master/spring-native-image/README.md). 
The example is a minimal REST-based API application, built on top of Spring Boot 3.
If you call the HTTP endpoint `/jibber`, it will return some nonsense verse generated in the style of the Jabberwocky poem, by Lewis Carroll. 

### Prerequisite 
Make sure you have installed a GraalVM JDK.
The easiest way to get started is with [SDKMAN!](https://sdkman.io/jdks#graal).
For other installation options, visit the [Downloads section](https://www.graalvm.org/downloads/).

1. Install and run a Docker-API compatible container runtime such as [Rancher Desktop](https://docs.rancherdesktop.io/getting-started/installation/), [Docker](https://www.docker.io/gettingstarted/), or [Podman](https://podman.io/docs/installation). 

2. Clone the GraalVM Demos repository:
    ```shell
    git clone https://github.com/graalvm/graalvm-demos
    ```
    
3. Change directory to _spring-native-image/_:
    ```shell
    cd spring-native-image
    ```

## Build and Run as a Native Executable

With the built-in support for GraalVM Native Image in Spring Boot 3, it has become much easier to compile a Spring Boot 3 application into a native executable.

1. Build a native executable:
    ```shell
    ./mvnw native:compile -Pnative
    ```

    The `-Pnative` profile is used to generate a native executable for your platform.
    This will generate a native executable called _benchmark-jibber_ in the _target/_ directory.

2. Run the native executable and put it into the background by appending `&`:
    ```shell
    ./target/benchmark-jibber &
    ```

3. Call the endpoint using `curl`:
    ```shell
    curl http://localhost:8080/jibber
    ```

    You should get a random nonsense verse. 

4. Bring the application to the foreground using `fg`, and then enter `<CTRL-c>` to stop the application.
        
## Containerize the Native Executable

The generated native executable is platform-dependent.

1. Containerize the native executable using the following command:

    - On Linux, containerize the native executable generated in the previous step using the following command:
        ```shell
        docker build -f Dockerfiles/Dockerfile.native --build-arg APP_FILE=benchmark-jibber -t jibber-benchmark:native.0.0.1-SNAPSHOT .
        ```

    - On MacOS, Windows, or Linux, use multistage Docker builds to build a native executable inside a container, and package the native executable in a lightweight container image:
        ```shell
        docker build -f Dockerfiles/Dockerfile -t jibber-benchmark:native.0.0.1-SNAPSHOT .
        ```  

2. Run the application:
    ```shell
    docker run --rm --name native -p 8080:8080 jibber-benchmark:native.0.0.1-SNAPSHOT
    ```

3. From a new terminal window, call the endpoint using `curl`:
    ```shell
    curl http://localhost:8080/jibber
    ```

    It should generate a random nonsense verse.

4. To stop the application, first get the container id using `docker ps`, and then run:
    ```shell
    docker rm -f <container_id>
    ```

5. To delete the container images, first get the image id using `docker images`, and then run:
    ```shell
    docker rmi -f <image_1_id> <image_n_id>
    ```

### Summary

In this guide, you saw how to use GraalVM container images to containerize a native executable for your Java application.

With GraalVM Native Image you can build a statically linked native executable by packaging the native executable directly into tiny containers such as scratch or distroless images. 

### Related Documentation

* [Build a Static or Mostly-Static Native Executable](build-static-and-mostly-static-executable.md)
* <a href="https://docs.oracle.com/en/graalvm/jdk/17/docs/getting-started/container-images/" target="_blank">Oracle GraalVM Container Images</a>
* <a href="https://luna.oracle.com/lab/fdfd090d-e52c-4481-a8de-dccecdca7d68" target="_blank">Hands-on Lab: GraalVM Native Image, Spring and Containerisation</a>
