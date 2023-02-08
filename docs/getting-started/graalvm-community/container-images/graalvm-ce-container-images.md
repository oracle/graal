---
layout: docs
toc_group: container-images
link_title: Container Images
permalink: /docs/getting-started/container-images/
---

## GraalVM Community Images

To support container-based development, GraalVM Community container images are published in the [GitHub Container Registry](https://github.com/orgs/graalvm/packages).
Learn here how to start using GraalVM Community images for Docker containers.

You can pull a package by name or by name and version tag. To install GraalVM JDK from the command line, use:
```shell
docker pull ghcr.io/graalvm/jdk:ol8-java17-<version>
```

Alternatively, use GraalVM JDK as base image in [Dockerfile](https://docs.docker.com/engine/reference/builder/):
```shell
FROM ghcr.io/graalvm/jdk:ol8-java17-<version>
```

There are different images provided depending on the  platforms, the architecture and the Java version.
GraalVM binaries are built for Linux, macOS, and Windows platforms on x86 64-bit systems, and for Linux on AArch64 architecture.
The images are multi-arch (`aarch64` or `amd64` will be pulled depending on Docker host architecture), and tagged with the format `ghcr.io/graalvm/$IMAGE_NAME[:][$os_version][-$java_version][-$version][-$build_number]`.
The version tag defines the level of specificity.
It is recommended that the most specific tag be used, e.g., `java17-22.3.1` or `java17-22.3.1-b1`, where the `-b1` means the image required a patch and this specific build will never change.
See what types of container images are available [here](https://github.com/graalvm/container).

## Get Started

1. Start a container and enter the `bash` session with the following run command:
    ```shell
    docker run -it --rm ghcr.io/graalvm/jdk:ol8-java17-22.3.1 bash
    ```
2. Check the `java` version:
    ```shell
    →docker run -it --rm ghcr.io/graalvm/jdk:ol8-java17-22.3.1 bash
    bash-4.4# java -version
    ```

You have pulled a size compact GraalVM Community container image with the GraalVM JDK pre-installed and the Graal compiler.

Size compact images are based on GraalVM components RPMs that are available for Oracle Linux 7, Oracle Linux 8, and Oracle Linux 9. Similar to any other available packages, you can install these components using `yum` on Oracle Linux 7 or `microdnf` on the Oracle Linux 8 and Oracle Linux 9 based images.

To pull a GraalVM Community Edition container image containing the [`gu` utility](../../../reference-manual/graalvm-updater.md) for installing additional components, run this command:
```
docker pull ghcr.io/graalvm/graalvm-ce:22.3.1 
```

GraalVM Updater, `gu`, can be used to install additional GraalVM language runtimes like JavaScript, Node.js, LLVM, Ruby, R, Python, and WebAssembly. For example, tp add the Ruby support, run the following command (the output below is truncated for brevity):

```shell
docker run -it --rm ghcr.io/graalvm/graalvm-ce:22.3.1  bash
bash-4.4# gu install ruby
Downloading: Component catalog
Processing component archive: Component ruby
Downloading: Component ruby
[######              ]
...
```
Here is a sample command that maps the `/absolute/path/to/directory/no/trailing/slash` directory from the host system to the `/path/inside/container` inside the container.

```shell
docker run -it --rm -v /absolute/path/to/directory/no/trailing/slash:/path/inside/container ghcr.io/graalvm/graalvm-ce:22.3.1 bash
```

If you want to create Docker images that contain GraalVM with Ruby, R, or Python, you can use a Dockerfile like the example below, which uses `ghcr.io/graalvm/graalvm-ce:22.3.1` as the base image, installs the Ruby support using the `gu` utility, then creates and runs a sample Ruby program.

```shell
FROM ghcr.io/graalvm/graalvm-ce:22.3.1
RUN gu install ruby
WORKDIR /workdir
RUN echo 'puts "Hello from Ruby!\nVersion: #{RUBY_DESCRIPTION}"' > app.rb
CMD ruby app.rb
```

If you put the above snippet in a Dockerfile in the current directory, you can build and run it with the following commands:

```shell
docker build -t ruby-demo .
...
docker run -it --rm ruby-demo
Hello from Ruby!
Version: truffleruby 22.3.1, like ruby 3.0.3, GraalVM CE Native [x86_64-darwin]
```

Check what other configuration types of container images are available [here](https://github.com/graalvm/container). 

If you look for Oracle GraalVM Enterprise container images, they are published in the [Oracle Container Registry](https://container-registry.oracle.com/ords/f?p=113:10::::::). See [here](https://docs.oracle.com/en/graalvm/enterprise/22/docs/getting-started/container-images/) to learn more.