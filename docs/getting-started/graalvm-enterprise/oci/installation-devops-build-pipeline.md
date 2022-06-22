---
layout: ohc
permalink: /getting-started/oci/devops-build-pipeline/
---

# GraalVM Enterprise in an OCI DevOps Build Pipeline

This guide describes how to use GraalVM Enterprise in the Oracle Cloud Infrastructure (OCI) DevOps service. (The DevOps service provides a continuous integration and continuous delivery (CI/CD) platform for developers.)

> Note: [Oracle Cloud Infrastructure (OCI)](https://www.oracle.com/cloud) provides Oracle GraalVM Enterprise Edition to its customers for free.
GraalVM Enterprise support is included in a Oracle Cloud subscription.

[OCI DevOps service](https://docs.oracle.com/en-us/iaas/Content/devops/using/devops_overview.htm) provides Oracle Linux 7 as the base container image as well as other [runtimes and tools](https://docs.oracle.com/en-us/iaas/Content/devops/using/runtime_details.htm). GraalVM Enterprise Edition is supported. 

GraalVM Enterprise RPMs are available in the Oracle YUM repository. 
Each RPM is self-contained and will automatically pull in all its required dependencies.
You can install and use GraalVM Enterprise in DevOps Build Pipelines using the YUM package manager.

### Prerequisites

- [DevOps project](https://docs.oracle.com/en-us/iaas/Content/devops/using/create_project.htm#create_a_project)
- [OCI Notification Topic](https://docs.oracle.com/en-us/iaas/Content/Notification/Tasks/managingtopicsandsubscriptions.htm#createTopic)
- [OCI DevOps Build Pipeline](https://docs.oracle.com/en-us/iaas/Content/devops/using/create_buildpipeline.htm)

The way to work with a build pipeline is to add statements to a build specification file (`build-spec.yml`), then the DevOps CI/CD platform reads the file and runs the commands one by one. You do not run a YUM package manager command manually. 

To use GraalVM Enterprise in the DevOps build pipeline, insert installation commands at the beginning of the build specification file, then add more commands in later sections of the file. 

1. Add the command to install GraalVM Enterprise with Native Image and Java Development Kit (JDK):
    ```yml
    steps:
    - type: Command
        name: "Install GraalVM 22.x Native Image for Java17"
        command: |
        yum -y install graalvm22-ee-17-native-image
    ```

2. Add the command to set the `JAVA_HOME` environment variable:

    ```yml
    env:
    variables:
        "JAVA_HOME" : "/usr/lib64/graalvm/graalvm22-ee-java17"
    ```

3. Add the command to set the `PATH` environment variable:

    ```yml
    env:
    variables:
        # PATH is a reserved variable and cannot be defined as a variable.
        # PATH can be changed in a build step and the change is visible in subsequent steps.

    steps:
    - type: Command
        name: "Set PATH Variable"
        command: |
        export PATH=$JAVA_HOME/bin:$PATH
    ```

Here is an example of a complete [build specification file](https://github.com/oracle-devrel/oci-devops-examples/blob/main/oci-build-examples/oci_devops_build_with_graalenterprise/build_spec.yaml).

For more information, see  [Using GraalVM Enterprise in OCI DevOps Build Pipelines](https://github.com/oracle-devrel/oci-devops-examples/tree/main/oci-build-examples/oci_devops_build_with_graalenterprise). It describes how to set up GraalVM Enterprise in OCI DevOps service, create a build pipeline, add build stages, and so on.

### Related Documentation

* [Using GraalVM Enterprise in OCI DevOps Build Pipelines](https://github.com/oracle-devrel/oci-devops-examples/tree/main/oci-build-examples/oci_devops_build_with_graalenterprise)
* [OCI DevOps service](https://docs.oracle.com/en-us/iaas/Content/devops/using/devops_overview.htm)