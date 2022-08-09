---
layout: ohc
permalink: /getting-started/oci/cloud-shell/
---

# GraalVM Enterprise in OCI Cloud Shell

This guide shows you how to get started with GraalVM Enterprise Edition in Oracle Cloud Infrastructure (OCI) Cloud Shell. 

[OCI Cloud Shell](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/cloudshellintro.htm) is a browser-based terminal accessible from the Oracle Cloud Console. It provides access to a Linux shell with a pre-authenticated OCI command-line interface (CLI), pre-installed developer tools, and comes with 5GB of storage.

As of version 22.2.0, GraalVM Enterprise JDK 17 and Native Image are pre-installed in Cloud Shell. 

> Note: GraalVM Enterprise is available on Oracle Cloud Infrastructure at no additional cost.

## Steps to Use GraalVM Enterprise in Cloud Shell

Cloud Shell has several pre-installed JDKs, including GraalVM Enterprise JDK.

1. [Login to OCI Console and launch Cloud Shell](https://cloud.oracle.com/?bdcstate=maximized&cloudshell=true).

2. List the installed JDKs using the `csruntimectl java list` command. You should see the following output:

    ```shell
    csruntimectl java list
    ```
    
    The output is similar to:
    
    ```shell
      graalvmeejdk-17.0.4           /usr/lib64/graalvm/graalvm22-ee-java17
    * openjdk-11.0.15               /usr/lib/jvm/java-11-openjdk-11.0.15.0.9-2.0.1.el7_9.x86_64
      openjdk-1.8.0.332             /usr/lib/jvm/java-1.8.0-openjdk-1.8.0.332.b09-1.el7_9.x86_64
    ```
    The JDK marked with an asterisk is the current JDK.

3. Select GraalVM JDK as the current JDK:

    ```shell
    csruntimectl java set graalvmeejdk-17.0.4
    ```
    You will see the confirmation message printed:
    ```shell
    The current managed java version is set to graalvmeejdk-17.0.4.
    ```
4. Now check the version of `java`, the `native-image` generator, as well as the values of the environment variables `PATH` and `JAVA_HOME`:

    ```shell
    java -version

    java version "17.0.4" 2022-07-19 LTS   
    Java(TM) SE Runtime Environment GraalVM EE 22.2.0 (build 17.0.4+11-LTS-jvmci-22.2-b05)   
    Java HotSpot(TM) 64-Bit Server VM GraalVM EE 22.2.0 (build 17.0.4+11-LTS-jvmci-22.2-b05, mixed mode, sharing)
    ```
    ```shell
    native-image --version
    
    GraalVM 22.2.0 Java 17 EE (Java Version 17.0.4+11-LTS-jvmci-22.2-b05)
    ```

    ```shell
    echo $JAVA_HOME
    
    /usr/lib64/graalvm/graalvm22-ee-java17
    ```

    ```shell
    echo $PATH
    
    /usr/lib64/graalvm/graalvm22-ee-java17/bin/:/ggs_client/usr/bin:/home/user_xyz/.yarn/bin:/...
    ```

You are all set to run Java applications using GraalVM Enterprise JDK in Cloud Shell.

## Run a Java Application

The example that you will run is a minimal REST-based application, built on top of Spring Boot using Maven. 
The _pom.xml_ file was generated using [Spring Initializr](https://start.spring.io/) with Spring Native Tools added as a feature. 
The [Spring AOT plugin](https://docs.spring.io/spring-native/docs/current/reference/htmlsingle/#spring-aot) performs ahead-of-time transformations of a Spring application into a native executable.

1.  Clone the demos repository and change to the application root directory:

    ```shell
    git clone https://github.com/graalvm/graalvm-demos.git
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
    export USE_NATIVE_IMAGE_JAVA_PLATFORM_MODULE_SYSTEM=false
    
    mvn package -Dnative
    ```
    This will generate a native executable for Linux in the _target_ directory, named _jibber_.

5. Run the native executable, using the following command:

    ```shell
    ./target/jibber &
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

Congraulations! You've successfully used GraalVM Enterprise JDK and Native Image to build and test a Spring Boot REST application in Cloud Shell.

Thus, you can easily use GraalVM Enterprise in OCI Cloud Shell to build and test simple Java applications with Micronaut, Spring, and other microservices frameworks.

### Related Documentation

- [Java Hello World with GraalVM Enterprise in OCI Cloud Shell](https://github.com/graalvm/graalvm-demos/blob/master/java-hello-world-maven/README-CS.md)
- [Micronaut Hello World REST App with GraalVM Enterprise in OCI Cloud Shell](https://github.com/graalvm/graalvm-demos/blob/master/micronaut-hello-rest-maven/README-CS.md)
- [GraalVM Enterprise in OCI Code Editor](code-editor.md)

