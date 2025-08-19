---
layout: docs
toc_group: container-images
link_title: Container Images
permalink: /getting-started/container-images/
redirect_from: /docs/getting-started/container-images/
---

## GraalVM Community Edition Container Images

To support container-based development, GraalVM Community Edition container images are published in the [GitHub Container Registry](https://github.com/orgs/graalvm/packages).

## Repositories

There are different GraalVM Community Edition container images provided depending on the architecture and the Java version, and have `-community` as part of their names.
These are: **native-image-community**, **jdk-community**, **truffleruby-community**, **nodejs-community**, and **graalpy-community**.
The container images are multi-arch, for x64 and AArch64 processor architectures, with a choice of Oracle Linux versions 8, 9, and 10.

GraalVM is installed in _/usr/lib64/graalvm/graalvm-community-java&lt;$FeatureVersion&gt;/_ where `<$FeatureVersion>` is `17`, `21`, `25`, and so on.
For instance, GraalVM for JDK 25 is installed in _/usr/lib64/graalvm/graalvm-community-java25/_.
All binaries, including `java`, `javac`, `native-image`, and other binaries are available as global commands via the `alternatives` command.

> Note: For GraalVM non-RPM based images (**graalvm-community**, **python-community**, **truffleruby-community**), the installation location is under _/opt/_ (_/opt/graalvm-community-java&lt;$FeatureVersion&gt;/_, _/opt/truffleruby-&lt;$GRAALVM_VERSION&gt;/_, and _/opt/graalpy-&lt;$GRAALVM_VERSION&gt;/_ respectively).

> Note: GraalVM Community Edition container images are based on Oracle Linux slim images, and the default package manager is `microdnf`.

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
```bash
25.0.0-ol9
25.0.0
25-ol9
25
```

## Pulling Images

1. To pull the container image for GraalVM JDK for a specific JDK feature version, such as _25_, run:
    ```bash
    docker pull ghcr.io/graalvm/jdk-community:25
    ```

    Alternatively, to use the container image as the base image in your Dockerfile, use:
    ```bash
    FROM ghcr.io/graalvm/jdk-community:25
    ```
    You have pulled a size compact GraalVM Community Edition container image with the GraalVM JDK and the Graal compiler preinstalled.

2. To pull the container image with the `native-image` utility for a specific JDK feature version, such as _25_, run:
    ```bash
    docker pull ghcr.io/graalvm/native-image-community:25
    ```

    Alternatively, to pull the container image with the `native-image` utility with the `musl libc` toolchain to create fully statically linked executables, use:
    ```bash
    docker pull ghcr.io/graalvm/native-image-community:25-muslib
    ```

    Alternatively, to use the container image as the base image in your Dockerfile, use:
    ```bash
    FROM ghcr.io/graalvm/native-image-community:25-muslib
    ```

3. To verify, start the container and enter a Bash session:
    ```bash
    docker run -it --rm --entrypoint /bin/bash ghcr.io/graalvm/native-image-community:25
    ```

	To check the version of GraalVM and its installed location, run the `env` command from the Bash prompt:
    ```bash
    env
    ```
    The output includes the environment variable `JAVA_HOME` with its value corresponding to the installed GraalVM version and location.

	To check the Java version, run:
    ```bash
    java -version
    ```

    To check the `native-image` version, run:
    ```bash
    native-image --version
    ```

4. Calling `docker pull` without specifying a processor architecture pulls container images for the processor architecture that matches your Docker client. To pull a container image for a different platform architecture, specify the desired platform architecture with the `--platform` option and either `linux/amd64` or `linux/aarch64` as follows:
    ```bash
    docker pull --platform linux/aarch64 ghcr.io/graalvm/native-image-community:25
    ```

## Oracle GraalVM Container Images

Oracle GraalVM container images are published in the [Oracle Container Registry (OCR)](https://container-registry.oracle.com/ords/ocr/ba/graalvm) and include [GFTC-licensed](https://www.oracle.com/downloads/licenses/graal-free-license.html) Oracle GraalVM.
Learn more in the [Oracle GraalVM Container Images documentation](https://docs.oracle.com/en/graalvm/jdk/25/docs/getting-started/container-images/).

### Related Documentation

- [Tiny Java Containers](https://github.com/graalvm/graalvm-demos/tree/master/native-image/tiny-java-containers): Learn how GraalVM Native Image can generate native executables ideal for containerization.