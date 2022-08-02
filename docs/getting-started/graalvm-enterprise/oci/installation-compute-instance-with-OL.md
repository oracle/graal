---
layout: ohc
permalink: /getting-started/oci/compute-instance/
---

# GraalVM Enterprise on OCI Compute Instances with Oracle Linux

This guide focuses on installing GraalVM Enterprise on an Oracle Cloud Infrastructure (OCI) Compute instance with Oracle Linux 7,8.
For users who prefer a Bare Metal Machine instance, see [this documentation](https://cloud.oracle.com/iaas/whitepapers/deploying_custom_os_images.pdf).
For complete beginners, [start with this tutorial for creating and launching your first Linux instance](https://docs.cloud.oracle.com/iaas/Content/GSG/Reference/overviewworkflow.htm?tocpath=Getting%20Started%7CTutorial%20-%20Launching%20Your%20First%20Linux%20Instance%7C_____0).

> Note: [Oracle Cloud Infrastructure (OCI)](https://www.oracle.com/cloud) provides Oracle GraalVM Enterprise Edition to its customers for free.
GraalVM Enterprise support is included in a Oracle Cloud subscription.

### Prerequisites

To replicate the steps in this guide, [create a Compute instance and connect to it](https://docs.cloud.oracle.com/iaas/Content/GSG/Reference/overviewworkflow.htm?tocpath=Getting%20Started%7CTutorial%20-%20Launching%20Your%20First%20Linux%20Instance%7C_____0).

## Install GraalVM Enterprise

For convenience, GraalVM Enterprise RPMs are available in the Oracle YUM repository. 
Each RPM is self-contained and will automatically pull in all required dependencies.

That means that OCI customers can use the GraalVM Enterprise environment in their compute instances by installing it with `yum` - a package-management utility for the Linux operating system.

The following instructions have been tested on an OCI Compute Instance with **Oracle Linux 7.9** and **VM.Standard.E4.Flex** with 1 OCPU and 16 GB RAM.
Use the following command to connect to the OCI Compute Instance from a Unix-style system:

   ```shell
   ssh -i .ssh/id_rsa opc@INSTANCE_PUBLIC_IP
   ```

The `.ssh/id_rsa` part is the full path and name of the file containing the private SSH key, `opc` is the default name for the Oracle Linux image, and `INSTANCE_PUBLIC_IP` is the instance IP address provisioned from the console.
For more details, refer to the [Connecting to Your Linux Instance Using SSH](https://docs.cloud.oracle.com/iaas/Content/GSG/Tasks/testingconnection.htm) tutorial.

1. Having connected to the instance, verify which GraalVM Enterprise RPMs are available for the installation, narrowing down the search to the latest release, and Java 11.

   ```shell
   sudo yum provides graalvm22-ee-11-jdk
   ```
   The resulting list includes both current and previous versions of all of the core package and additional features.

2. Find the appropriate RPM package name, and install GraalVM Enterprise with `sudo yum install <package_name>`.
For example, to install the latest version of "Oracle GraalVM Enterprise Edition JDK11 Java Development Kit", run:

   ```shell
   sudo yum install graalvm22-ee-11-jdk
   ```
   Confirm if the installed package size is okay by typing `yes` at the prompt. 
   It will install the latest version of **graalvm22-ee-11-jdk** which includes the JVM runtime with the Graal compiler.

   After the installation, the GraalVM Enterprise binary is placed in _/usr/lib64/graalvm_. You can check this with:

   ```shell
   ls /usr/lib64/graalvm
   ```

3. Configure environment variables to point to the GraalVM Enterprise installation for this SSH session. After the installation, the package files are placed in the `/usr/lib64/graalvm` directory, and binaries in `bin` accordingly.

   - Set the `PATH` and `JAVA_HOME` environment variables in the bash configuration to point to GraalVM Enterprise with the following commands:

      ```shell
      echo "export JAVA_HOME=/usr/lib64/graalvm/graalvm22-ee-java11" >> ~/.bashrc
      ```
      
      ```shell
      echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
      ```
   - Activate this change:

      ```shell
      source ~/.bashrc
      ```

   - Verify the values of `PATH` and `JAVA_HOME`:

      ```shell
      echo $JAVA_HOME
      echo $PATH
      ```
   - Run the following command to confirm the version of GraalVM Enterprise JDK installed:

      ```shell
      java -version
      ```

Now you have a ready-to-go VM instance with GraalVM Enterprise installed.

## Install Additional Features

GraalVM Enterprise consists of different features and components - JDK, Native Image, Python runtime, Node.js runtime, LLVM toolchain, etc. - each of which can be installed separately or as an add-on to an existing component. 
See [Distribution Components List](https://docs.oracle.com/en/graalvm/enterprise/22/docs/overview/architecture/#distribution-components-list) for more information.

To add additional features to GraalVM Enterprise, use the `yum install <package_name>` command. 

1. Check what additional features are available for your current GraalVM Enterprise installation:

   ```shell
   sudo yum provides graalvm22*
   ```
   The printed list is enormous. If you are interested in a particular component, for example, the Python runtime, narrow down the search providing the exact package name:

   ```shell
   sudo yum provides graalvm22-ee-11-python*
   ```

2. Install the component to GraalVM Enterprise with the `yum install <package_name> command` command. To install the Python runtime, run:

   ```shell
   sudo yum install graalvm22-ee-11-python
   ```
   Confirm if the installed package size is okay by typing `yes` at the prompt.

### Install Native Image

[Native Image](../../../reference-manual/native-image/README.md) is a technology to turn your Java application into a standalone native executable and has to be installed to GraalVM Enterprise JDK installation.

1. Search for Native Image PRMs available for your installation: 

   ```shell
   sudo yum provides graalvm22-ee-11-native-image*
   ```
2. Install Native Image using the `yum install <package_name> command` command. All required dependencies will be automatically resolved.

   - On Oracle Linux 7.9, run:
      ```shell
      sudo yum install graalvm22-ee-11-native-image
      ```
      Confirm if the installed package size is okay by typing `yes` at the prompt.

   - On Oracle Linux 8, run these commands one by one:
      ```shell
      sudo yum update -y oraclelinux-release-el8
      sudo yum config-manager --set-enabled ol8_codeready_builder
      sudo yum install graalvm22-ee-11-native-image
      ```
      Confirm if the installed package size is okay by typing `yes` at the prompt.
      
   - On Oracle Linux 8 with `dnf` or `microdnf` default package managers, run these commands one by one:
      ```shell
      dnf update -y oraclelinux-release-el8
      dnf --enablerepo ol8_codeready_builder
      dnf install graalvm22-ee-11-native-image
      ```
      Confirm if the installed package size is okay by typing `yes` at the prompt.

## Update GraalVM Enterprise

The `yum` package manager for Oracle Linux can be used to update an existing GraalVM installation or replace it with another version. 

1. To update GraalVM, for example, from version 21.x to 22.x and install the distribution for Java 17 instead of Java 11, run:

   ```shell
   sudo yum install graalvm22-ee-17-jdk
   ```

2. Confirm if the installed package size is okay by typing `yes` at the prompt.
3. Check the Java version to see if the update was successful:

   ```shell
   java -version
   ```

The **graalvm22-ee-17-jdk** package is installed alongside **graalvm22-ee-11-jdk** in the _/usr/lib64/graalvm_ directory. Note that regardless the version printed to the console, the `PATH` and `JAVA_HOME` environment variables still point to the old version. Reset the variables as described in [Install GraalVM Enterprise](#install-graalvm-enterprise), step 3.

### Note on `yum upgrade`

The `yum upgrade` command can be used to update on the same year package line, for example, to upgrade from GraalVM Enterprise 22.1.0 to version 22.2.0 when this RPM package becomes available:

   ```shell
   sudo yum upgrade graalvm22-ee-11-jdk
   ```
   As there is no newer package available, you will see the `No packages marked for update` message.

It will update the whole system and remove any obsolete GraalVM Enterprise installation.

### Related Documentation

- [Get Started with GraalVM on Oracle Linux in OCI](https://luna.oracle.com/lab/3b0dcf97-22d0-489b-a049-5d269199fa00): Run the interactive workshop to install GraalVM Enterprise on Oracle Linux 8 (all the necessary cloud resources are provisioned).

- [Accelerate Applications in Oracle Cloud with GraalVM Enterprise](https://luna.oracle.com/lab/d502417b-df66-45be-9fed-a3ac8e3f09b1): Run the interactive workshop to see how GraalVM Enterprise accelerates Java applications in OCI.