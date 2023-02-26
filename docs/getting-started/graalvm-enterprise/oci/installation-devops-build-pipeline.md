---
layout: ohc
permalink: /getting-started/oci/devops-build-pipeline/
---

# GraalVM Enterprise in OCI DevOps Build Pipelines

This guide describes how to use GraalVM Enterprise in the Oracle Cloud Infrastructure (OCI) DevOps service. [OCI DevOps](https://www.oracle.com/in/devops/devops-service/) is a continuous integration/continuous delivery (CI/CD) service that enables developers to automate the delivery and deployment of software to OCI compute platforms.

> Note: GraalVM Enterprise is available on Oracle Cloud Infrastructure (OCI) at no additional cost.

OCI DevOps service provides build runners with Oracle Linux 7 as the base container image along with a number of [runtimes and tools](https://docs.oracle.com/en-us/iaas/Content/devops/using/runtime_details.htm). 
GraalVM Enterprise Edition is supported.

GraalVM Enterprise RPMs are available in the Oracle YUM repository. 
Each RPM is self-contained and will automatically pull in all its required dependencies.
You can install and use GraalVM Enterprise in DevOps Build Pipelines using the YUM package manager.

### Prerequisites

- [DevOps project](https://docs.oracle.com/en-us/iaas/Content/devops/using/create_project.htm#create_a_project)
- [OCI Notification Topic](https://docs.oracle.com/en-us/iaas/Content/Notification/Tasks/managingtopicsandsubscriptions.htm#createTopic)
- [OCI DevOps Build Pipeline](https://docs.oracle.com/en-us/iaas/Content/devops/using/create_buildpipeline.htm)

The way to work with a build pipeline is to add statements to a [build specification file](https://docs.oracle.com/en-us/iaas/Content/devops/using/build_specs.htm), `build-spec.yml`, then the DevOps CI/CD platform reads the file and runs the commands one by one. You do not run a YUM package manager command manually.

To install and use GraalVM Enterprise in the DevOps build pipeline, update your build specification file as follows:

1. Add the command to install GraalVM Enterprise with Native Image and Java Development Kit (JDK):

    ```yml
    steps:
    - type: Command
        name: "Install GraalVM Enterprise 22.x Native Image for Java17"
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

Use  the `yum list` command to get a list of all the GraalVM Enterprise RPMs available. For instance, use the following command to list all the available GraalVM Enterprise 22.x JDK17 components:

```shell
yum list graalvm22-ee-17*

graalvm22-ee-17-native-image.x86_64        22.2.0-1.el7       ol7_oci_included
graalvm22-ee-17-espresso.x86_64            22.2.0-1.el7       ol7_oci_included
graalvm22-ee-17-javascript.x86_64          22.2.0-1.el7       ol7_oci_included
graalvm22-ee-17-jdk.x86_64                 22.2.0-1.el7       ol7_oci_included
graalvm22-ee-17-libpolyglot.x86_64         22.2.0-1.el7       ol7_oci_included
graalvm22-ee-17-llvm.x86_64                22.2.0-1.el7       ol7_oci_included
graalvm22-ee-17-llvm-toolchain.x86_64      22.2.0-1.el7       ol7_oci_included
graalvm22-ee-17-nodejs.x86_64              22.2.0-1.el7       ol7_oci_included
graalvm22-ee-17-polyglot.x86_64            22.2.0-1.el7       ol7_oci_included
graalvm22-ee-17-python.x86_64              22.2.0-1.el7       ol7_oci_included
graalvm22-ee-17-ruby.x86_64                22.2.0-1.el7       ol7_oci_included
graalvm22-ee-17-tools.x86_64               22.2.0-1.el7       ol7_oci_included
graalvm22-ee-17-wasm.x86_64                22.2.0-1.el7       ol7_oci_included
...
```

To try this feature out, use the sample project: [Using GraalVM Enterprise in OCI DevOps Build Pipelines](https://github.com/oracle-devrel/oci-devops-examples/tree/main/oci-build-examples/oci_devops_build_with_graalenterprise). It describes how to set up GraalVM Enterprise in OCI DevOps service, create a build pipeline, add build stages, and so on.

### Related Documentation

* [OCI DevOps: Using GraalVM Enterprise in DevOps Build Pipelines](https://docs.oracle.com/en-us/iaas/Content/devops/using/graalvm.htm)
* [OCI Build Examples: Using GraalVM Enterprise in OCI DevOps Build Pipelines](https://github.com/oracle-devrel/oci-devops-examples/tree/main/oci-build-examples/oci_devops_build_with_graalenterprise)
* [OCI Build Examples: Using GraalVM Enterprise in OCI DevOps to build a Micronaut REST App](https://github.com/oracle-devrel/oci-devops-examples/tree/main/oci-build-examples/oci_devops_graalee_micronaut)