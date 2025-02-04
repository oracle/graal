---
layout: ohc
toc_group: container-images
link_title: Oracle GraalVM Container Images
permalink: /getting-started/container-images/
---

## Oracle GraalVM Container Images

Oracle GraalVM container images are available in [Oracle Container Registry (OCR)](https://container-registry.oracle.com/ords/ocr/ba/graalvm) under the [GraalVM Free Terms and Conditions (GFTC) license](https://www.oracle.com/downloads/licenses/graal-free-license.html).

## Repositories

Oracle GraalVM container images are published in two OCR repositories: **jdk** and **native-image**. 

| Repository       | Description |
|------------------|-------------|
| **jdk**          | Provides container images with Oracle GraalVM JDK (without the `native-image` utility) which can be used to both compile and deploy a Java application. Use the container image tags to select the appropriate Java version and Oracle Linux version. |
| **native-image** | Provides Oracle GraalVM container images with the `native-image` utility along with all tools required to compile an application into a native Linux executable. These images are commonly used in multistage builds to compile an application into an executable that is then packaged in a lightweight container image. Use the container image tags to select the Java version and Oracle Linux version as well as variants that include the `musl` toolchain for the creation of a fully statically linked executable. |

Both repositories provide container images for x64 and AArch64 processor architectures, with a choice of Oracle Linux versions 8 or 9.

Oracle GraalVM is installed in _/usr/lib64/graalvm/graalvm-java&lt;$FeatureVersion&gt;/_ where `<$FeatureVersion>` is `17`, `21`, `23`, and so on.

For example, Oracle GraalVM for JDK 23 is installed in _/usr/lib64/graalvm/graalvm-java23/_.
All binaries, including `java`, `javac`, `native-image`, and other binaries are available as global commands via the `alternatives` command.

## Tags

Each repository provides multiple tags that let you choose the level of stability you need including the Java version, build number, and the Oracle Linux version. 
Oracle GraalVM container image tags use the following naming convention:
```bash
$version[-muslib(for native image only)][-$platform][-$buildnumber]
```

The following tags are listed from the most-specific tag (at the top) to the least-specific tag (at the bottom). 
The most-specific tag is unique and always points to the same container image, while the less-specific tags point to newer container image variants over time.
```
23.0.1-ol9-20240504
23.0.1-ol9
23.0.1
23-ol9
23
```

## Pulling Images

1. To pull the container image for Oracle GraalVM JDK for a specific JDK feature version, such as _23_, run:
    ```bash
    docker pull container-registry.oracle.com/graalvm/jdk:23
    ```
    
    Alternatively, to use the container image as the base image in your Dockerfile, use:
    ```bash
    FROM container-registry.oracle.com/graalvm/jdk:23
    ```

2.  To pull the container image for Oracle GraalVM `native-image` utility for a specific JDK feature version, such as _23_, run: 
    ```bash
    docker pull container-registry.oracle.com/graalvm/native-image:23
    ```
    
    Alternatively, to pull the container image for Oracle GraalVM `native-image` utility with the `musl libc` toolchain to create fully statically linked executables, run:
    ```bash
    docker pull container-registry.oracle.com/graalvm/native-image:23-muslib
    ```
    
    Alternatively, to use the container image as the base image in your Dockerfile, use:
    ```bash
    FROM container-registry.oracle.com/graalvm/native-image:23-muslib
    ```
    
3. To verify, start the container and enter a Bash session:
    ```bash
    docker run -it --rm --entrypoint /bin/bash container-registry.oracle.com/graalvm/native-image:23
    ```

    To check the version of Oracle GraalVM and its installed location, run the `env` command from the `bash` prompt:
    ```bash
    env
    ```
    The output includes the environment variable `JAVA_HOME` with its value corresponding to the installed GraalVM version and location.

	To check the version of GraalVM and its installed location, run the `env` command from the Bash prompt:
    ```bash
    java -version
    ```
    The output shows the installed Oracle GraalVM Java runtime environment and version information.
    
    To check the `native-image` version, run the following command from the Bash prompt:
    ```bash
    native-image --version
    ```
    The output shows the installed Oracle GraalVM `native-image` utility version information.
    
4. A `docker pull` command that omits a processor architecture pulls a container image for the processor architecture that matches your Docker client. To pull a container image for a different platform architecture, specify the desired platform architecture with the `--platform` option and either `linux/amd64` or `linux/aarch64` as follows:
    ```bash
    docker pull --platform linux/aarch64 container-registry.oracle.com/graalvm/native-image:23
    ```

### Related Documentation

- [Tiny Java Containers](https://github.com/graalvm/graalvm-demos/tree/master/native-image/containerize/tiny-java-containers): Learn how GraalVM Native Image can generate native executables ideal for containerization.