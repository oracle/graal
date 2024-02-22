---
layout: ohc
permalink: /getting-started/oci/compute-instance/
---

# Oracle GraalVM on OCI Compute Instances with Oracle Linux

This guide describes how to install Oracle GraalVM on an Oracle Cloud Infrastructure (OCI) Compute instance with Oracle Linux 7, 8, and 9.
For complete beginners, [start with this tutorial to create and launch your first Linux instance](https://docs.oracle.com/iaas/Content/GSG/Reference/overviewworkflow.htm).

> Note: Oracle GraalVM license and support are included in the Oracle Cloud Infrastructure subscription at no additional cost.

### Prerequisites

To replicate the steps in this guide, [create a Compute instance and connect to it](https://docs.oracle.com/iaas/Content/GSG/Reference/overviewworkflow.htm).

## Install Oracle GraalVM

For convenience, Oracle GraalVM RPMs are available in the Oracle YUM repository.
RPMs for Oracle GraalVM for JDK 17, JDK 21, and JDK 22 are available with the package names `graalvm-17-native-image`, `graalvm-21-native-image`, and `graalvm-22-native-image`, respectively.
These Oracle GraalVM distributions include a JDK and Natime Image.
Each Oracle GraalVM RPM is self-contained and all required dependencies will be automatically resolved during the installation.

That means that OCI customers can use Oracle GraalVM in their compute instances, just like any other Java Development Kit, by installing it with `yum`, `dnf`, or `microdnf` default package managers, depending on the Oracle Linux version.

Use the following command to connect to the OCI Compute Instance from a Unix-style system:
```shell
ssh -i .ssh/id_rsa opc@INSTANCE_PUBLIC_IP
```

Where `.ssh/id_rsa` is the full path and name of the file containing your private SSH key; `opc` is the default name for the Oracle Linux image; and `INSTANCE_PUBLIC_IP` is the instance IP address provisioned from the console.
For more details, refer to the [Connecting to Your Linux Instance Using SSH](https://docs.cloud.oracle.com/iaas/Content/GSG/Tasks/testingconnection.htm) tutorial.

The installation steps may differ per Oracle Linux version or package manager. 

### Oracle Linux 7

1. Install newer devtoolset with GCC version 10 (required by Oracle GraalVM Native Image):
   ```shell
   sudo yum -y install oracle-softwarecollection-release-el7
   ```
   ```shell
   sudo yum install devtoolset-10
   ```
2. Enable the newer devtoolset by default:
   ```shell
   echo 'source scl_source enable devtoolset-10' >> ~/.bashrc
   ```
3. Enter a new bash session with the newer devtoolset enabled:
   ```
   bash
   ```
4. Install the latest Oracle GraalVM:
   ```
   sudo yum install graalvm-22-native-image
   ```
   Confirm that the installed package size is correct by entering `yes` at the prompt.

### Oracle Linux 8

On Oracle Linux 8 with the `yum` package manager, run these commands one by one:
```shell
sudo yum update -y oraclelinux-release-el8
```
```shell
sudo yum config-manager --set-enabled ol8_codeready_builder
```
```shell
sudo yum install graalvm-22-native-image
```
Confirm that the installed package size is correct by entering `yes` at the prompt.

On Oracle Linux 8 with `dnf` or `microdnf` default package managers, run these commands one by one:
```shell
sudo dnf update -y oraclelinux-release-el8
```
```shell
sudo dnf config-manager --set-enabled ol8_codeready_builder
```
```shell
sudo dnf install graalvm-22-native-image
```

### Oracle Linux 9

On Oracle Linux 9 with the `yum` package manager, run these commands one by one:
```shell
sudo yum update -y oraclelinux-release-el9
```
```shell
sudo yum config-manager --set-enabled ol9_codeready_builder
```
```shell
sudo yum install graalvm-22-native-image
```
Confirm that the installed package size is correct by entering `yes` at the prompt.

On Oracle Linux 9 with `dnf` or `microdnf` default package managers, run these commands one by one:
```shell
sudo dnf update -y oraclelinux-release-el9
```
```shell
sudo dnf config-manager --set-enabled ol9_codeready_builder
```
```shell
sudo dnf install graalvm-22-native-image
```

### Configure Environment Variables

Configure environment variables to point to the Oracle GraalVM installation for this SSH session. 
After installation, the package files are placed in the _/usr/lib64/graalvm_ directory, and binaries in _bin_ accordingly.

1. Set the `PATH` and `JAVA_HOME` environment variables in the bash configuration to point to Oracle GraalVM with the following commands:
   ```shell
   echo "export JAVA_HOME=/usr/lib64/graalvm/graalvm-java22" >> ~/.bashrc
   ```
   ```shell
   echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
   ```
2. Activate this change:
   ```shell
   source ~/.bashrc
   ```
3. Check the values of `PATH` and `JAVA_HOME`, and the Java version to confirm the installation was successful:
   ```shell
   echo $JAVA_HOME
   ```
   ```shell
   echo $PATH
   ```
   ```shell
   java -version
   ```

Congratulations! You have installed Oracle GraalVM on the Compute instance with the Oracle Linux image, and can use it as any other Java Development Kit.

### Related Documentation

- [Oracle GraalVM in OCI DevOps Build Pipelines](installation-devops-build-pipeline.md)

- [Oracle GraalVM in OCI Cloud Shell](cloud-shell.md)

- [Get Started with GraalVM on Oracle Linux in OCI](https://luna.oracle.com/lab/3b0dcf97-22d0-489b-a049-5d269199fa00): Run the interactive workshop to install Oracle GraalVM on Oracle Linux 8 (all the necessary cloud resources are provisioned).
