---
layout: ohc
permalink: /getting-started/oci/cloud-shell/
---

# GraalVM Enterprise in Cloud Shell

As of version 22.2.0, GraalVM Enterprise JDK 17 and Native Image are pre-installed in Oracle Cloud Infrastructre (OCI) Cloud Shell. 

Cloud Shell is a browser-based terminal accessible from the Oracle Cloud Console. It provides access to a Linux shell with pre-authenticated OCI CLI, pre-installed developer tools, and comes with 5GB of storage.

> Note: GraalVM Enterprise is available on Oracle Cloud Infrastructure (OCI) at no additional cost.

This guide shows you how to get started with GraalVM Enterprise Edition in Cloud Shell.

## Set `JAVA_HOME` to GraalVM Enterprise

Cloud Shell comes with several JDKs preinstalled, including GraalVM Enterprise JDK.

1. Login to your OCI account and open Cloud Shell (click the code icon in the top right bar).

2. Check the versions installed using the `csruntimectl java list` command. It will return the following output:

    ```shell
    csruntimectl java list

    graalvmeejdk-17.0.4                                    /usr/lib64/graalvm/graalvm22-ee-java17
    openjdk-11.0.15                   /usr/lib/jvm/java-11-openjdk-11.0.15.0.9-2.0.1.el7_9.x86_64
    openjdk-1.8.0.332                /usr/lib/jvm/java-1.8.0-openjdk-1.8.0.332.b09-1.el7_9.x86_64
    ```

3. Set `java` to GraalVM Enterprise:

    ```shell
    csruntimectl java set graalvmeejdk-17.0.4
    ```
    You will see the confirmation message printed:
    ```shell
    The current managed java version is set to graalvmeejdk-17.0.4.
    ```
4. Now check the `java` version, `PATH` and `JAVA_HOME` environment variables:

    ```shell
    java -version

    java version "17.0.4" 2022-07-19 LTS   
    Java(TM) SE Runtime Environment GraalVM EE 22.2.0 (build 17.0.4+11-LTS-jvmci-22.2-b05)   
    Java HotSpot(TM) 64-Bit Server VM GraalVM EE 22.2.0 (build 17.0.4+11-LTS-jvmci-22.2-b05, mixed mode, sharing)
    ```

    ```shell
    echo $JAVA_HOME

    /usr/lib64/graalvm/graalvm22-ee-java17
    ```

    ```shell
    echo $PATH

    /usr/lib64/graalvm/graalvm22-ee-java17/bin/:/ggs_client/usr/bin:/home/user_xyz/.yarn/bin:/...
    ```

GraalVM Enterprise Native Image is also pre-installed in Cloud Shell. Check its version:

```shell
native-image --version
GraalVM 22.2.0 Java 17 EE (Java Version 17.0.4+11-LTS-jvmci-22.2-b05)
```

You are all set to start running Java applications on GraalVM Enterprise JDK in Cloud Shell.

## Run a Java Application

For the demo part, you will run a minimal REST-based API application, built on top of Spring Boot with Maven. 
The _pom.xml_ file was generated using [Spring Initializr](https://start.spring.io/) with Spring Native Tools added as a feature. 
The [Spring AOT plugin](https://docs.spring.io/spring-native/docs/current/reference/htmlsingle/#spring-aot) performs ahead-of-time transformations of a Spring application into a native executable.

For OCI users, Apache Maven is pre-installed in Cloud Shell (check the version with `mvn --version`).

1.  Clone the demos repository and enter the application root directory:

    ```shell
    git clone https://github.com/graalvm/graalvm-demos.git && cd graalvm-demos/spring-native-image
    ```
2. Build the application:

    ```shell
    mvn clean package
    ```
    This will generate a runnable JAR file that contains all of the application’s dependencies and also a correctly configured `MANIFEST` file.

3. Test running this application from a JAR:

    ```shell
    java -jar ./target/benchmark-jibber-0.0.1-SNAPSHOT.jar &
    ```
    where `&` brings the application to the background. Call the endpoint:
    ```shell
    curl http://localhost:8080/jibber
    ```
    You should get some nonsense verse back.
    Bring the app back to the foreground and terminate:
    ```shell
    fg
    ctrl+C
    ```

4. Next build a native executable for this Spring Boot application using the Maven profile:

    ```shell
    mvn package -Dnative
    ```
    It will generate a native executable for Linux in the target directory, called `jibber`.

5. Run the application from a native executable. Execute the following command in your terminal and put it into the background, using `&`:
    ```shell
    ./target/jibber &
    ```
    Call the endpoint to test:
    ```shell
    curl http://localhost:8080/jibber
    ```
    
    You should get some nonsense verse back. To terminate it, bring the application to the foreground, `fg`, and kill the process `<ctrl+c>`.

Notice how fast this native executable starts in comparison to running from the JAR file. 
Thus, it uses fewer resources and occupies less memory in OCI (check the executable file size to see the footprint `ls -lh target/jibber`). 

Pre-installing GraalVM Enterprise JDK and Native Image in Cloud Shell enables OCI users to immediately start using GraalVM Enterprise as the default Java environment. 
Running Java applications on GraalVM Enterprise can accelerate application performance while consuming fewer resources.

### Related Documentation

- [GraalVM Enterprise in OCI DevOps Build Pipelines](installation-devops-build-pipeline.md)

- [Accelerate Applications in Oracle Cloud with GraalVM Enterprise](https://luna.oracle.com/lab/d502417b-df66-45be-9fed-a3ac8e3f09b1)