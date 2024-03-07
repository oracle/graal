---
layout: ohc
permalink: /getting-started/oci/devops-build-pipeline/
---

# Oracle GraalVM in OCI DevOps Build Pipelines

This guide describes how to use Oracle GraalVM in the Oracle Cloud Infrastructure (OCI) DevOps service. 
[OCI DevOps](https://www.oracle.com/in/devops/devops-service/) is a continuous integration/continuous delivery (CI/CD) service that enables developers to automate the delivery and deployment of software to OCI compute platforms.

> Note: Oracle GraalVM license and support are included in the Oracle Cloud Infrastructure subscription at no additional cost.

OCI DevOps service provides build runners with Oracle Linux 7 as the base container image along with a number of [runtimes and tools](https://docs.oracle.com/en-us/iaas/Content/devops/using/runtime_details.htm). 

Oracle GraalVM RPMs are available in the Oracle YUM repository. 
Each RPM is self-contained and will automatically pull in all its required dependencies.
You can install and use Oracle GraalVM in DevOps Build Pipelines using the YUM package manager.

### Prerequisites

- [DevOps project](https://docs.oracle.com/en-us/iaas/Content/devops/using/create_project.htm#create_a_project)
- [OCI Notification Topic](https://docs.oracle.com/en-us/iaas/Content/Notification/Tasks/managingtopicsandsubscriptions.htm#createTopic)
- [OCI DevOps Build Pipeline](https://docs.oracle.com/en-us/iaas/Content/devops/using/create_buildpipeline.htm)

To work with a Build Pipeline, add statements to a [build specification file](https://docs.oracle.com/en-us/iaas/Content/devops/using/build_specs.htm), _build-spec.yml_. 
The DevOps CI/CD platform reads the file and runs the commands one by one. 
You do not need to run a YUM package manager command manually.

RPMs for Oracle GraalVM are available with the package names `graalvm-17-native-image`, `graalvm-21-native-image`, and `graalvm-22-native-image`. 
Each package includes the JDK and Native Image.

To install and use Oracle GraalVM in your DevOps Build Pipeline, update your build specification file as shown in the following example.

1. Add the command to install Oracle GraalVM for JDK 22 with Native Image and Java Development Kit (JDK):

    ```yml
    steps:
    - type: Command
        name: "Install Oracle GraalVM for JDK 22"
        command: |
        yum -y install graalvm-22-native-image
    ```

2. Add the command to set the `JAVA_HOME` environment variable for Oracle GraalVM for JDK 22:

    ```yml
    env:
    variables:
        "JAVA_HOME" : "/usr/lib64/graalvm/graalvm-java22"
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

Here is an example of a complete [build specification file](https://github.com/oracle-devrel/oci-devops-examples/blob/main/oci-build-examples/oci_devops_build_with_graalenterprise/build_spec_oracle_graalvm_jdk20.yaml).

Oracle GraalVM provides more features, each of which can be installed as an add-on.
Use the `yum list` command to get a list of the available RPMs for your installation.
For instance, for Oracle GraalVM for JDK 22, run:

```shell
yum list graalvm-22*
...
```

To try this feature out, use the sample project: [Using Oracle GraalVM in OCI DevOps Build Pipelines](https://github.com/oracle-devrel/oci-devops-examples/tree/main/oci-build-examples/oci_devops_build_with_graalenterprise). 
It describes how to set up Oracle GraalVM in OCI DevOps service, create a Build Pipeline, add build stages, and so on.

### Related Documentation

* [OCI DevOps: Using Oracle GraalVM in DevOps Build Pipelines](https://docs.oracle.com/en-us/iaas/Content/devops/using/graalvm.htm)
* [OCI Build Examples: Using Oracle GraalVM in OCI DevOps Build Pipelines](https://github.com/oracle-devrel/oci-devops-examples/tree/main/oci-build-examples/oci_devops_build_with_graalenterprise)
* [OCI Build Examples: Using Oracle GraalVM in OCI DevOps to build a Micronaut REST App](https://github.com/oracle-devrel/oci-devops-examples/tree/main/oci-build-examples/oci_devops_graalee_micronaut)
