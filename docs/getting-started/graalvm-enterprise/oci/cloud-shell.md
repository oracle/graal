---
layout: ohc
permalink: /getting-started/oci/cloud-shell/
---

# GraalVM Enterprise in OCI Cloud Shell

This guide shows you how to get started with GraalVM Enterprise Edition in Oracle Cloud Infrastructure (OCI) Cloud Shell. 

[OCI Cloud Shell](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/cloudshellintro.htm) is a browser-based terminal accessible from the Oracle Cloud Console. It provides access to a Linux shell with a pre-authenticated OCI Command Line Interface (CLI), pre-installed developer tools, and comes with 5GB of storage.

GraalVM Enterprise JDK 17 and Native Image are pre-installed in Cloud Shell. 

> Note: GraalVM Enterprise is available on Oracle Cloud Infrastructure at no additional cost.

## Steps to Use GraalVM Enterprise in Cloud Shell

Cloud Shell has several pre-installed JDKs, including GraalVM Enterprise JDK.

1. [Login to OCI Console and launch Cloud Shell](https://cloud.oracle.com/?bdcstate=maximized&cloudshell=true).

2. List the installed JDKs using the `csruntimectl java list` command. You should see the following output:

    ```shell
    csruntimectl java list
    ```
    The output lists the JDKs preinstalled in Cloud Shell - GraalVM JDK for Java 17, Oracle JDK for Java 11, and Oracle JDK for Java 8. The JDK marked with an asterisk is the current JDK.

3. Select GraalVM JDK for Java 17 as the current JDK:

    ```shell
    csruntimectl java set graalvmeejdk-17
    ```
    You will see the confirmation message printed `The current managed java version is set to graalvmeejdk-17`.

4. Now confirm the values of the environment variables `PATH` and `JAVA_HOME`, and the version of `java`, the `native-image` generator:

    ```shell
    echo $JAVA_HOME
    ```
    ```shell
    echo $PATH
    ```
    ```shell
    java -version
    ```
    ```shell
    native-image --version
    ```

You are all set to run Java applications using GraalVM Enterprise JDK in Cloud Shell.

## Run a Java Application

The example that you will run is a minimal REST-based application, built on top of Spring Boot using Maven. 
The _pom.xml_ file was generated using [Spring Initializr](https://start.spring.io/) with Spring Native Tools added as a feature. 
The [Spring AOT plugin](https://docs.spring.io/spring-native/docs/current/reference/htmlsingle/#spring-aot) performs ahead-of-time transformations of a Spring application into a native executable.

1.  Clone the demos repository and change to the application root directory:

    ```shell
    git clone https://github.com/graalvm/graalvm-demos.git
    ```
    ```shell
    cd graalvm-demos/spring-native-image
    ```
2. Build the application with Maven (Apache Maven is also pre-installed in Cloud Shell):

    ```shell
    mvn clean package
    ```
    This will generate a runnable JAR file that contains all of the application’s dependencies as well as a correctly configured `MANIFEST` file.

3. Run the Java application:

    ```shell
    java -jar ./target/benchmark-jibber-0.0.1-SNAPSHOT.jar &
    ```
	
    Call the REST endpoint:
    ```shell
    curl http://localhost:8080/jibber
    ```
    You should see some nonsense verse printed.
    
    Bring the application to the foreground:
    ```shell
    fg
    ```
    
    Terminate the application by pressing Ctrl+C.

4. Next build a native executable for this Spring Boot application using the [`native` Maven profile](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html#quickstart).

    ```shell
    mvn -Pnative native:compile
    ```
    This will generate a native executable for Linux in the _target_ directory, named _benchmark-jibber_.

5. Run the native executable, using the following command:

    ```shell
   ./target/benchmark-jibber &
    ```
    
    Call the endpoint to test:
    
    ```shell
    curl http://localhost:8080/jibber
    ```
    Again, you should see some nonsense verse printed. 
    
    Bring the application to the foreground:
    
    ```shell
    fg
    ```
    Terminate the application by pressing Ctrl+C.

Congratulations! You have successfully used GraalVM Enterprise JDK and Native Image to build and test a Spring Boot REST application in Cloud Shell. 

Thus, you can easily use GraalVM Enterprise in OCI Cloud Shell to build and test simple Java applications with Micronaut, Spring, and other microservice frameworks.

### Related Documentation

- [Java Hello World with GraalVM Enterprise in OCI Cloud Shell](https://github.com/graalvm/graalvm-demos/blob/master/java-hello-world-maven/README-Cloud-Shell.md)
- [Micronaut Hello World REST App with GraalVM Enterprise in OCI Cloud Shell](https://github.com/graalvm/graalvm-demos/blob/master/micronaut-hello-rest-maven/README-Cloud-Shell.md)
- [Spring Boot Microservice with GraalVM Enterprise in OCI Cloud Shell](https://github.com/graalvm/graalvm-demos/blob/master/spring-native-image/README-Cloud-Shell.md)
- [GraalVM Enterprise in OCI Code Editor](code-editor.md)

