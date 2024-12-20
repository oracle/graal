---
layout: ohc
permalink: /getting-started/oci/code-editor/
---

# Oracle GraalVM in OCI Code Editor

This guide shows you how to get started with Oracle GraalVM in Oracle Cloud Infrastructure (OCI) Code Editor. 

[OCI Code Editor](https://docs.oracle.com/iaas/Content/API/Concepts/code_editor_intro.htm) provides a rich, in-console editing environment that enables you to edit code without having to switch between the Oracle Cloud Console and your local development environment. 
The Code Editor enables you to edit and deploy code for OCI services directly from the OCI Console.

Oracle GraalVM for JDK 17 is preinstalled in Cloud Shell, so you do not have to install and configure a development machine. Code Editor's integration with Cloud Shell gives you direct access to it.

> Note: Oracle GraalVM license and support are included in the Oracle Cloud Infrastructure subscription at no additional cost.

## Create and Run a Java Application in OCI Code Editor

### Step 1: Open a Terminal in Code Editor

1. [Login to the Oracle Cloud Console and launch Code Editor](https://cloud.oracle.com/?bdcstate=maximized&codeeditor=true).
2. Open a Terminal in Code Editor, by clicking **New Terminal** from the **Terminal** menu.

### Step 2: Select GraalVM JDK as the Default JDK

1. List the installed JDKs using the `csruntimectl java list` command.
    ```bash
    csruntimectl java list
    ```
    The output lists the JDKs preinstalled in Cloud Shell: Oracle GraalVM for JDK 17, Oracle JDK 11, and Oracle JDK 8. The JDK marked with an asterisk is the current JDK.

2. Select Oracle GraalVM for JDK 17 as the current JDK:
    ```bash
    csruntimectl java set graalvmjdk-17
    ```
    You will see the confirmation message printed: "The current managed java version is set to graalvmjdk-17".

3. Now confirm the values of the environment variables `PATH` and `JAVA_HOME`, and the versions of `java` and the `native-image` tool:
    ```bash
    echo $JAVA_HOME
    ```
    ```bash
    echo $PATH
    ```
    ```bash
    java -version
    ```
    ```bash
    native-image --version
    ```

## Step 3: Setup a Java Project and Run

1. Clone a demo repository and open it in OCI Code Editor. To achieve this, run the following commands one by one:
    ```bash
    git init graalvmee-java-hello-world
    ```
    ```bash
    cd graalvmee-java-hello-world
    ```
    ```bash
    git remote add origin https://github.com/oracle-devrel/oci-code-editor-samples.git
    ```
    ```bash
    git config core.sparsecheckout true
    ```
    ```bash
    echo "java-samples/graalvmee-java-hello-world/*">>.git/info/sparse-checkout
    ```
    ```bash
    git pull --depth=1 origin main
    ```
    ```bash
    cd java-samples/graalvmee-java-hello-world/
    ```
    You can now view/edit the sample code in Code Editor.

2. Package the sample application into a runnable JAR file:
    ```bash
    mvn clean package
    ```

3. Run the JAR file:
    ```bash
    java -jar target/my-app-1.0-SNAPSHOT.jar
    ```
    It prints out “Hello World!”.

## Step 4: Build and Run a Native Executable

This Java application incorporates the [Maven plugin for GraalVM Native Image](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html) that adds support for building native executables using Apache Maven. For testing purposes, build a native executable with the quick build mode first enabled and then disabled.

### Quick Build Mode Enabled

1. To enable the quick build mode, uncomment this line in _pom.xml_, as follows:
    ```xml
    <quickBuild>true</quickBuild>
    ```
    
2. Build a native executable using the `native` Maven profile:
    ```bash
    mvn clean -Pnative -DskipTests package
    ```
    This will generate a native executable for Linux in the _target_ directory, named _my-app_.

3. Run the app native executable in the background:
    ```bash
    ./target/my-app
    ```

### Quick Build Mode Disabled

1. To disable the quick build mode, comment out this line in _pom.xml_, as follows:
    ```xml
    <!-- <quickBuild>true</quickBuild> -->
    ```
    
2. Build a native executable again:
    ```bash
    mvn clean -Pnative -DskipTests package
    ```
    This will generate a native executable, _my-app_, in the _target_ directory, replacing the previous one. You have probably noticed how the quick build mode reduced the time required to generate a native executable, making it easier to use Native Image in a typical development cycle (compile, test, and debug). However, the size of a generated executable is larger and peak performance is worse. The quick build mode is recommended for development purposes only. 

3. Run the native executable:
    ```bash
    ./target/my-app
    ```

Congratulations! You have successfully built and run a native executable using Oracle GraalVM in OCI Code Editor without the need to switch between the Oracle Cloud Console and your local development environments.
The Code Editor allows you to accomplish quick coding tasks and run applications directly from the OCI Console.

### Related Documentation

- [Java Hello World with Oracle GraalVM in OCI Code Editor](https://github.com/oracle-devrel/oci-code-editor-samples/tree/main/java-samples/graalvmee-java-hello-world)
- [Micronaut Hello World REST App with Oracle GraalVM in OCI Code Editor](https://github.com/oracle-devrel/oci-code-editor-samples/tree/main/java-samples/graalvmee-java-micronaut-hello-rest)
- [Working with Code Editor](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/code_editor_intro.htm)
- [Oracle GraalVM in OCI Cloud Shell](cloud-shell.md)
