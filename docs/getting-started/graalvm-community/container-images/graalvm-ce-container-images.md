---
layout: docs
toc_group: container-images
link_title: Container Images
permalink: /docs/getting-started/container-images/
---

## GraalVM Community Edition Container Images

To support container-based development, GraalVM Community Edition container images are published in the [GitHub Container Registry](https://github.com/orgs/graalvm/packages).
Learn here how to start using GraalVM Community Edition images for Docker containers.

You can pull a package by name or by name and version tag. To install GraalVM JDK from the command line, use:
```shell
docker pull ghcr.io/graalvm/jdk-community:20.0.1-ol9
```

Alternatively, use GraalVM JDK as a base image in [Dockerfile](https://docs.docker.com/engine/reference/builder/):
```shell
FROM ghcr.io/graalvm/jdk-community:20.0.1-ol9
```

There are different GraalVM Community Edition container images provided depending on the architecture and the Java version.
The images are multi-arch (`aarch64` or `amd64` depending on the host architecture), and tagged with the format `ghcr.io/graalvm/$IMAGE_NAME[:][$java_version][-$os_version][-$date]`.
The version tag defines the level of specificity. It is recommended that the most specific tag be used, for example, `20.0.1` or `20.0.1-ol9[-$date]`, where the `-ol9-[-$date]` means the image required a patch and this specific build will never change.

See what types of container images are available [here](https://github.com/graalvm/container).

## Get Started

1. Start a container and enter the `bash` session with the following run command:
    ```shell
    docker run -it --rm ghcr.io/graalvm/jdk-community:20.0.1-ol9 bash
    ```
2. Check the `java` version:
    ```shell
    →docker run -it --rm ghcr.io/graalvm/jdk-community:20.0.1-ol9 bash
    bash-4.4# java -version
    ```

You have pulled a size compact GraalVM Community Edition container image with the GraalVM JDK pre-installed and the Graal compiler.

RPM-based GraalVM Community container images are based on GraalVM components RPMs that are available for Oracle Linux 7, Oracle Linux 8, and Oracle Linux 9. 
Similar to any other available packages, you can install these components using `yum` on Oracle Linux 7 or `microdnf` on the Oracle Linux 

To pull a GraalVM Community Edition container image containing the [`gu` utility](../../../reference-manual/graalvm-updater.md) for installing additional components, run this command:
```
docker pull ghcr.io/graalvm/graalvm-community:20.0.1-ol9
```

Here is a sample command that maps the `/absolute/path/to/directory/no/trailing/slash` directory from the host system to the `/path/inside/container` inside the container.

```shell
docker run -it --rm -v /absolute/path/to/directory/no/trailing/slash:/path/inside/container ghcr.io/graalvm/graalvm-community:20.0.1-ol9 bash
```

Using `ghcr.io/graalvm/native-image-community` you will always get the latest update available for GraalVM Community Native Image, the latest OS which is for now Oracle Linux 9 and Oracle Linux 9 slim, and the latest Java version.

Check what other configuration types of container images are available [here](https://github.com/orgs/graalvm/packages). 

If you are looking for Oracle GraalVM container images, they are published in the [Oracle Container Registry](https://container-registry.oracle.com/ords/f?p=113:10::::::).