---
layout: docs
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
| **jdk**          | Provides container images with Oracle GraalVM JDK which can be used to both compile and deploy Java applications. Image tags let you select the Java version and Oracle Linux version. |
| **native-image** | Provides Oracle GraalVM container images with the `native-image` utility along with all tools required to compile applications into native Linux executables. These images are commonly used in multi-stage builds to compile applications into executables that are then packaged in a lightweight container image. Image tags let you select the Java version and Oracle Linux version as well as variants that include the musl toolchain for the creation of fully statically linked executables. |

Both repositories provide container images for AMD64 and AArch64 processor architectures, with a choice of Oracle Linux versions 7, 8, or 9.

Oracle GraalVM is installed in `/usr/lib64/graalvm/graalvm-java<$FeatureVersion>` where `<$FeatureVersion>` is `17`, `20`, etc. 
For instance, Oracle GraalVM for JDK 17 is installed in `/usr/lib64/graalvm/graalvm-java17`. 
All binaries, including `java`, `javac`, `native-image`, and other binaries are available as global commands via the `alternatives` command.

## Tags

Each repository provides multiple tags that let you choose the level of stability you need including the Java version, build number, and the Oracle Linux version. 
Oracle GraalVM image tags use the following naming convention:

```bash
$version[-muslib(for native image only)][-$platform][-$buildnumber]
```

The following tags are listed from the most-specific tag (at the top) to the least-specific tag (at the bottom). 
The most-specific tag is unique and always points to the same image, while the less-specific tags point to newer image variants over time.

```
17.0.8-ol9-20230904
17.0.8-ol9
17.0.8
17-ol9
17
```

## Pulling Images

1. To pull the container image for Oracle GraalVM JDK for a specific JDK feature version, e.g., _17_, run:

    ```bash
    docker pull container-registry.oracle.com/graalvm/jdk:17
    ```
    
    Alternatively, to use the container image as the base image in your Dockerfile, use:
    
    ```bash
    FROM container-registry.oracle.com/graalvm/jdk:17
    ```

2.  To pull the container image for Oracle GraalVM `native-image` utility for a specific JDK feature version, e.g., _17_, run: 
    
    ```bash
    docker pull container-registry.oracle.com/graalvm/native-image:17
    ```

	Alternatively, to pull the container image for Oracle GraalVM `native-image` utility with the `musl libc` toolchain to create fully statically linked executables, run:
    
    ```bash
    docker pull container-registry.oracle.com/graalvm/native-image:17-muslib
    ```
    
    Alternatively, to use the container image as the base image in your Dockerfile, use:
    
    ```bash
    FROM container-registry.oracle.com/graalvm/native-image:17-muslib
    ```
    
3. To verify, start the container and enter the Bash session:

    ```bash
    docker run -it --rm --entrypoint /bin/bash container-registry.oracle.com/graalvm/native-image:17
    ```

	To check the version of Oracle GraalVM and its installed location, run the `env` command from the Bash prompt:

    ```bash
    env
    ```
    
    The output shows the environment variable `JAVA_HOME` pointing to the installed Oracle GraalVM version and location.

	To check the Java version, run the following command from the Bash prompt:
    
    ```bash
    java -version
    ```
    
    The output shows the installed Oracle GraalVM Java runtime environment and version information.
    
    To check the `native-image` version, run the following command from the Bash prompt:
    
    ```bash
    native-image --version
    ```
    
    The output shows the installed Oracle GraalVM `native-image` utility version information.
    
4. Calling `docker pull` without specifying a processor architecture pulls container images for the processor architecture that matches your Docker client. 
To pull container images for a different platform architecture, specify the desired platform architecture with the `--platform` option and either `linux/amd64` or `linux/aarch64` as follows:

    ```bash
    docker pull --platform linux/aarch64 container-registry.oracle.com/graalvm/native-image:17
    ```

### Learn More

- [GraalVM Native Image, Spring and Containerisation](https://luna.oracle.com/lab/fdfd090d-e52c-4481-a8de-dccecdca7d68): Learn how GraalVM Native Image can generate native executables ideal for containerization.
- [Announcement Blog: New Oracle GraalVM Container Images](https://blogs.oracle.com/java/post/new-oracle-graalvm-container-images)

