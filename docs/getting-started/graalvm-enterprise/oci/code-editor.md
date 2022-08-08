---
layout: ohc
permalink: /getting-started/oci/code-editor/
---

# GraalVM Enterprise in OCI Code Editor

This guide shows how you can get started quickly with GraalVM Enterprise Edition in Oracle Cloud Infrastructre (OCI) Code Editor. 

[OCI Code Editor](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/code_editor_intro.htm) provides a rich, in-console editing environment that enables you to edit code and update service workflows and scripts without having to switch between the Console and your local development environment. The Code Editor enables you to edit and deploy code for various OCI services directly from the OCI Console.

Code Editor's direct integration with Cloud Shell allows you access to the GraalVM Enterprise Native Image and JDK 17 (Java Development Kit) provided in Cloud Shell.

> Note: GraalVM Enterprise is available on Oracle Cloud Infrastructure at no additional cost.

## Create and Run a Java Application in OCI Code Editor

### Step 1: Open Terminal in Code Editor

1. [Login to OCI Console](https://www.oracle.com/cloud/sign-in.html) and launch Code Editor.
2. Open a New Terminal in Code Editor. Use this Terminal window to run the following steps. 

    ![OCI Code Editor](../img/oci-code-editor.png)

### Step 2: Select GraalVM JDK as the current JDK

1. List the installed JDKs using the `csruntimectl java list` command. You should see the following output:

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

2. Select GraalVM JDK as the current JDK:

    ```shell
    csruntimectl java set graalvmeejdk-17.0.4
    ```
    You will see the confirmation message printed:
    ```shell
    The current managed java version is set to graalvmeejdk-17.0.4.
    ```

3. (Optional) Check software version and environment variables:
    ```shell
    echo $JAVA_HOME
    echo $PATH
    java -version
    native-image --version
    ```

## Step 3: Setup a Project and Run

1. Clone a demo repository and open it in OCI Code Editor. To achieve this, run the following commands one by one:  

    ```shell
    git init graalvmee-java-hello-world

    cd graalvmee-java-hello-world

    git remote add origin https://github.com/oracle-devrel/oci-code-editor-samples.git

    git config core.sparsecheckout true

    echo "java-samples/graalvmee-java-hello-world/*">>.git/info/sparse-checkout

    git pull --depth=1 origin main

    cd java-samples/graalvmee-java-hello-world/
    ```
    You can now view/change the sample code in code editor.

    ![Java project opened in OCI Code Editor](../img/oci-ce-java-app.png)

2. Build a JAR for the sample application:

    ```shell
    mvn clean package
    ```
3. Run the JAR:

    ```shell
    java -jar target/my-app-1.0-SNAPSHOT.jar
    ```
    It prints out "Hello World!".

4. Use GraalVM Native Image to produce a native executable.


### Related Documentation

- [Working with Code Editor](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/code_editor_intro.htm).