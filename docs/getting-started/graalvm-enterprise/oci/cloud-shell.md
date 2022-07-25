---
layout: ohc
permalink: /getting-started/oci/cloud-shell/
---

# GraalVM Enterprise in Cloud Shell

As of version 22.2.0, GraalVM Enterprise JDK 17 and Native Image are provided in Oracle Cloud Infrastructure (OCI) Cloud Shell. 

[Cloud Shell](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/cloudshellintro.htm) is a browser-based terminal accessible from the Oracle Cloud Console. It provides access to a Linux shell with a pre-authenticated OCI command-line interface (CLI), pre-installed developer tools, and comes with 5GB of storage.

> Note: GraalVM Enterprise is available on Oracle Cloud Infrastructure (OCI) at no additional cost.

This guide shows you how to get started with GraalVM Enterprise Edition in Cloud Shell.

## Steps to Use GraalVM Enterprise

Cloud Shell has several pre-installed JDKs, including GraalVM Enterprise JDK.

1. Login to your OCI account and click the code icon in the top right bar to open Cloud Shell.

2. List the installed JDKs using the `csruntimectl java list` command. You should see the following output:

    ```shell
    csruntimectl java list

      graalvmeejdk-17.0.4           /usr/lib64/graalvm/graalvm22-ee-java17
      openjdk-11.0.15               /usr/lib/jvm/java-11-openjdk-11.0.15.0.9-2.0.1.el7_9.x86_64
    * openjdk-1.8.0.332             /usr/lib/jvm/java-1.8.0-openjdk-1.8.0.332.b09-1.el7_9.x86_64
    ```
    The JDK marked with an asterisk is the current JDK.

3. Set the version of the JDK, and consequently the version of `java` on your path:

    ```shell
    csruntimectl java set graalvmeejdk-17.0.4
    ```
    You will see the confirmation message printed:
    ```shell
    The current managed java version is set to graalvmeejdk-17.0.4.
    ```
4. Now check your version of `java`, the `native-image` generator, as well as the values of your `PATH` and `JAVA_HOME` environment variables:

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

<<<<<<< HEAD
GraalVM Enterprise Native Image is also pre-installed in Cloud Shell. Check its version:

```shell
native-image --version
GraalVM 22.2.0 Java 17 EE (Java Version 17.0.4+11-LTS-jvmci-22.2-b05)
```

You are all set to run Java applications using GraalVM Enterprise JDK in Cloud Shell.
=======
You are all set to start running Java applications on GraalVM Enterprise JDK in Cloud Shell.
>>>>>>> 0ed3246b67e (Follow Sachin P. comments)

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
    Bring your application to the foreground and terminate it by pressing Ctrl+C:
    ```shell
    fg
    CTRL+C
    ```

4. Next build a native executable for this Spring Boot application using the  [`native` Maven profile](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html#quickstart).

    ```shell
    mvn package -Dnative
    ```
    If you are using GraalVM Enterprise version 22.2.0 (or later), you may see the following error:

    ```shell
    Fatal error: com.oracle.svm.core.util.VMError$HostedError: The classpath of com.oracle.svm.hosted.NativeImageGeneratorRunner must not contain ".". This can happen implicitly if the builder runs exclusively on the --module-path but specifies the com.oracle.svm.hosted.NativeImageGeneratorRunner main class without --module.
    ```
    Starting with release 22.2, the `native-image` tool (that the Spring AOT plugin invokes in the background) runs on the module path. As a quick workaround, disable the default module path by exporting the `USE_NATIVE_IMAGE_JAVA_PLATFORM_MODULE_SYSTEM` variable and re-run the Maven package command:

    ```shell
    export USE_NATIVE_IMAGE_JAVA_PLATFORM_MODULE_SYSTEM=false
    mvn package -Dnative
    ```
    It will generate a native executable for Linux in the _target_ directory, named _jibber_.

5. Run the native executable, using the following command:
    ```shell
    ./target/jibber &
    ```
    Call the endpoint to test:
    ```shell
    curl http://localhost:8080/jibber
    ```
    
    Again, you should see some nonsense verse printed. To terminate your application, bring it to the foreground (using `fg`), and press Ctrl+C.

Notice how fast this native executable starts compared to running the Java equivalent. 
Thus, it uses fewer resources and occupies less memory in OCI (check the executable file size to see the footprint `ls -lh target/jibber`). 

Providing GraalVM Enterprise JDK and Native Image in Cloud Shell enables OCI users to immediately use GraalVM Enterprise as the default Java environment. 
Running Java applications on GraalVM Enterprise can accelerate application performance while consuming fewer resources.

### Related Documentation

- [GraalVM Enterprise in OCI DevOps Build Pipelines](installation-devops-build-pipeline.md)

- [Accelerate Applications in Oracle Cloud with GraalVM Enterprise](https://luna.oracle.com/lab/d502417b-df66-45be-9fed-a3ac8e3f09b1)