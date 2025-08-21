---
layout: ohc
permalink: /getting-started/oci/compute-instance/
---

# Oracle GraalVM on an OCI Compute Instance with Oracle Linux

This guide describes how to install Oracle GraalVM on an Oracle Cloud Infrastructure (OCI) Compute instance with Oracle Linux 7, 8, 9, and 10.
For complete beginners, [start with this tutorial to create and launch your first Linux instance](https://docs.oracle.com/iaas/Content/GSG/Reference/overviewworkflow.htm).

> Note: Oracle GraalVM license and support are included in the Oracle Cloud Infrastructure subscription at no additional cost.

### Prerequisites

To replicate the steps in this guide, [create a Compute instance and connect to it](https://docs.oracle.com/iaas/Content/GSG/Reference/overviewworkflow.htm).

## Install Oracle GraalVM

For convenience, the RPM package for Oracle GraalVM for JDK 25 is available in the Oracle YUM repository with the package name `graalvm-25-native-image`.
This distribution includes a JDK and Native Image.
It is self-contained and all the required dependencies will be automatically resolved during the installation.

That means that OCI customers can use Oracle GraalVM in their compute instances, just like any other Java Development Kit, by installing it with `yum`, `dnf`, or `microdnf` default package managers, depending on the Oracle Linux version.

Use the following command to connect to the OCI Compute Instance from a Unix-style system:
```bash
ssh -i .ssh/id_rsa opc@INSTANCE_PUBLIC_IP
```

Where `.ssh/id_rsa` is the full path and name of the file containing your private SSH key; `opc` is the default name for the Oracle Linux image; and `INSTANCE_PUBLIC_IP` is the instance IP address provisioned from the console.
For more details, refer to the [Connecting to Your Linux Instance Using SSH](https://docs.cloud.oracle.com/iaas/Content/GSG/Tasks/testingconnection.htm) tutorial.

The installation steps may differ per Oracle Linux version or package manager.

### Oracle Linux 10

On Oracle Linux 10 with the `yum` package manager, run these commands one by one:
```bash
sudo yum update -y oraclelinux-release-el10
```
```bash
sudo yum config-manager --set-enabled el10_codeready_builder
```
```bash
sudo yum install graalvm-25-native-image
```
Confirm that the installed package size is correct by entering `yes` at the prompt.

On Oracle Linux 10 with `dnf` or `microdnf` default package managers, run these commands one by one:
```bash
sudo dnf update -y oraclelinux-release-el10
```
```bash
sudo dnf config-manager --set-enabled el10_codeready_builder
```
```bash
sudo dnf install graalvm-25-native-image
```

### Oracle Linux 9

On Oracle Linux 9 with the `yum` package manager, run these commands one by one:
```bash
sudo yum update -y oraclelinux-release-el9
```
```bash
sudo yum config-manager --set-enabled ol9_codeready_builder
```
```bash
sudo yum install graalvm-25-native-image
```
Confirm that the installed package size is correct by entering `yes` at the prompt.

On Oracle Linux 9 with `dnf` or `microdnf` default package managers, run these commands one by one:
```bash
sudo dnf update -y oraclelinux-release-el9
```
```bash
sudo dnf config-manager --set-enabled ol9_codeready_builder
```
```bash
sudo dnf install graalvm-25-native-image
```

### Oracle Linux 8

On Oracle Linux 8 with the `yum` package manager, run these commands one by one:
```bash
sudo yum update -y oraclelinux-release-el8
```
```bash
sudo yum config-manager --set-enabled ol8_codeready_builder
```
```bash
sudo yum install graalvm-25-native-image
```
Confirm that the installed package size is correct by entering `yes` at the prompt.

On Oracle Linux 8 with `dnf` or `microdnf` default package managers, run these commands one by one:
```bash
sudo dnf update -y oraclelinux-release-el8
```
```bash
sudo dnf config-manager --set-enabled ol8_codeready_builder
```
```bash
sudo dnf install graalvm-25-native-image
```

### Oracle Linux 7

1. Install a newer devtoolset with GCC version 10 (required by Oracle GraalVM Native Image):
   ```bash
   sudo yum -y install oracle-softwarecollection-release-el7
   ```
   ```bash
   sudo yum install devtoolset-10
   ```
2. Enable the newer devtoolset by default:
   ```bash
   echo 'source scl_source enable devtoolset-10' >> ~/.bashrc
   ```
3. Enter a new bash session with the newer devtoolset enabled:
   ```
   bash
   ```
4. Install the latest Oracle GraalVM:
   ```
   sudo yum install graalvm-25-native-image
   ```
   Confirm that the installed package size is correct by entering `yes` at the prompt.


## Configure Environment Variables

Configure environment variables to point to the Oracle GraalVM installation for this SSH session.
After installation, the package files are placed in the _/usr/lib64/graalvm_ directory, and binaries in _bin_ accordingly.

1. Set the values of the `PATH` and `JAVA_HOME` environment variables in the bash configuration to point to the location of the Oracle GraalVM installation with the following commands:
   ```bash
   echo "export JAVA_HOME=/usr/lib64/graalvm/graalvm-java25" >> ~/.bashrc
   ```
   ```bash
   echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
   ```
2. Activate this change:
   ```bash
   source ~/.bashrc
   ```
3. Check the values of `PATH` and `JAVA_HOME`, and the Java version to confirm the installation was successful:
   ```bash
   echo $JAVA_HOME
   ```
   ```bash
   echo $PATH
   ```
   ```bash
   java -version
   ```

Congratulations! You have installed Oracle GraalVM on the Compute instance with the Oracle Linux image, and can use it as any other Java Development Kit.

### Related Documentation

- [Oracle GraalVM in OCI DevOps Build Pipelines](installation-devops-build-pipeline.md)
- [Oracle GraalVM in OCI Cloud Shell](cloud-shell.md)