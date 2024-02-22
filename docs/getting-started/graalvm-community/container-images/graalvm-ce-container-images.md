---
layout: docs
toc_group: container-images
link_title: Container Images
permalink: /docs/getting-started/container-images/
---

## GraalVM Community Edition Container Images

To support container-based development, GraalVM Community Edition container images are published in the [GitHub Container Registry](https://github.com/orgs/graalvm/packages).

## Repositories

There are different GraalVM Community Edition container images provided depending on the architecture and the Java version, and have `-community` as part of their names.
These are: **native-image-community**, **jdk-community**, **truffleruby-community**, **nodejs-community**, and **graalpy-community**.
The container images are multi-arch, for AMD64 and AArch64 processor architectures, with a choice of Oracle Linux versions 7, 8, or 9.

GraalVM is installed in `/usr/lib64/graalvm/graalvm-java<$FeatureVersion>` where `<$FeatureVersion>` is `17`, `21`, `22`, etc.
For instance, GraalVM for JDK 22 is installed in `/usr/lib64/graalvm/graalvm-java22`. 
All binaries, including `java`, `javac`, `native-image`, and other binaries are available as global commands via the `alternatives` command.

See a full list of GraalVM Community Edition container images [here](https://github.com/graalvm/container).

## Tags

Each repository provides multiple tags that let you choose the level of stability you need including the Java version, build number, and the Oracle Linux version. 
Image tags use the following naming convention:

```bash
$version[-muslib(for native image only)][-$platform][-$buildnumber]
```

The following tags are listed from the most-specific tag (at the top) to the least-specific tag (at the bottom). 
The most-specific tag is unique and always points to the same image, while the less-specific tags point to newer image variants over time.
For example:
```
21.0.0-ol9-20230919
21.0.0-ol9
21.0.0
21-ol9
21
```

## Pulling Images

1. To pull the container image for GraalVM JDK for a specific JDK feature version, e.g, _22_, run:
    ```bash
    docker pull ghcr.io/graalvm/jdk-community:22
    ```
    
    Alternatively, to use the container image as the base image in your Dockerfile, use:
    ```bash
    FROM ghcr.io/graalvm/jdk-community:22
    ```

    You have pulled a size compact GraalVM Community Edition container image with the GraalVM JDK and the Graal compiler pre-installed.

2. To pull the container image with the `native-image` utility for a specific JDK feature version, e.g, _22_, run: 
    ```bash
    docker pull ghcr.io/graalvm/native-image-community:22
    ```

	Alternatively, to pull the container image with the `native-image` utility with the `musl libc` toolchain to create fully statically linked executables, use:
    ```bash
    docker pull ghcr.io/graalvm/native-image-community:22-muslib
    ```
    
    Alternatively, to use the container image as the base image in your Dockerfile, use:
    ```bash
    FROM ghcr.io/graalvm/native-image-community:22-muslib
    ```

3. To verify, start the container and enter the Bash session:
    ```bash
    docker run -it --rm --entrypoint /bin/bash ghcr.io/graalvm/native-image-community:22
    ```

	To check the version of GraalVM and its installed location, run the `env` command from the Bash prompt:
    ```bash
    env
    ```

    The output shows the environment variable `JAVA_HOME` pointing to the installed GraalVM version and location.

	To check the Java version, run:
    ```bash
    java -version
    ```
    
    To check the `native-image` version, run:
    ```bash
    native-image --version
    ```

4. Calling `docker pull` without specifying a processor architecture pulls container images for the processor architecture that matches your Docker client. To pull container images for a different platform architecture, specify the desired platform architecture with the `--platform` option and either `linux/amd64` or `linux/aarch64` as follows:
    ```bash
    docker pull --platform linux/aarch64 ghcr.io/graalvm/native-image-community:22
    ```

## Oracle GraalVM Container Images 

Oracle GraalVM container images are published in the [Oracle Container Registry](https://container-registry.oracle.com/ords/ocr/ba/graalvm) under the [GraalVM Free Terms and Conditions (GFTC) license](https://www.oracle.com/downloads/licenses/graal-free-license.html). 
Learn more at [Oracle Help Center](https://docs.oracle.com/en/graalvm/jdk/21/docs/getting-started/container-images/#oracle-graalvm-container-images).

### Learn More

- [GraalVM Native Image, Spring and Containerisation](https://luna.oracle.com/lab/fdfd090d-e52c-4481-a8de-dccecdca7d68): Learn how GraalVM Native Image can generate native executables ideal for containerization.
