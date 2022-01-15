---
layout: docs
toc_group: container-images
link_title: Container Images
permalink: /docs/getting-started/container-images/
---

## GraalVM Community Images

Containers can simplify application deployment and development.
To support container-based development, GraalVM Community container images for each release are published in the [GitHub Container Registry](https://github.com/orgs/graalvm/packages/container/package/graalvm-ce).
Learn here how to start using GraalVM Community images for Docker containers.

You can pull a package by name or by name and version tag. To install GraalVM JDK from the command line, use:
```shell
docker pull ghcr.io/graalvm/jdk:java17-<version>
```

Alternatively, use GraalVM JDK as base image in [Dockerfile](https://docs.docker.com/engine/reference/builder/):
```shell
FROM ghcr.io/graalvm/jdk:java17-<version>
```

There are different GraalVM Community container images provided depending on the architecture and the Java version.
GraalVM binaries are built for Linux, macOS, and Windows platforms on x86 64-bit systems, and for Linux on ARM 64-bit systems.
The images are multi-arch (`aarch64` or `amd64` will be pulled depending on Docker host architecture), and tagged with the format `ghcr.io/graalvm/IMAGE_NAME:version`.
The version tag defines the level of specificity.
It is recommended that the most specific tag be used, e.g., `java17-21.3.0` or `java17-21.3.0-b1`, where the `-b1` means the image required a patch and this specific build will never change.
See what types of container images are available [here](https://github.com/graalvm/container).

The images are based on Oracle Linux and has GraalVM Community downloaded, unzipped and made available.
It means that Java, JavaScript, and the LLVM runtime are available out of the box.

You can start a container and enter the `bash` session with the following run command:
```shell
docker run -it --rm ghcr.io/graalvm/jdk:java17-22.0.0 bash
```

Check that `java`, `js` and other commands work as expected.
```shell
→ docker run -it --rm ghcr.io/graalvm/jdk:java17-22.0.0 bash
bash-4.4# java -version
openjdk 17.0.2 2022-01-18
OpenJDK Runtime Environment GraalVM CE 22.0.0 (build 17.0.2+5-jvmci-22.0-b02)
OpenJDK 64-Bit Server VM GraalVM CE 22.0.0 (build 17.0.2+5-jvmci-22.0-b02, mixed mode, sharing)

bash-4.4# js -version
GraalVM JavaScript (GraalVM CE Native 22.0.0)
> 1 + 1
2
> quit()
>
bash-4.4# lli --version
LLVM 12.0.1 (GraalVM CE Native 22.0.0)
```

You have pulled a size compact GraalVM Community container image with the GraalVM JDK pre-installed.
JavaScript and `lli` are the components immediately available at the GraalVM Community image core.
However, the [GraalVM Updater, `gu`, utility](../../../reference-manual/graalvm-updater.md) is also included and may be used to install additional languages and runtimes like Node.js, Ruby, R, Python or WebAssembly.
Check what other configuration types of container images are available [here](https://github.com/graalvm/container).

To add the Ruby support, run the following command (the output below is truncated for brevity):

```shell
docker run -it --rm ghcr.io/graalvm/jdk:java17-22.0.0 bash
bash-4.4# gu install ruby
Downloading: Component catalog
Processing component archive: Component ruby
Downloading: Component ruby
[######              ]
...
```

Here is a sample command that maps the `/absolute/path/to/directory/no/trailing/slash` directory from the host system to the `/path/inside/container` inside the container.

```shell
docker run -it --rm -v /absolute/path/to/directory/no/trailing/slash:/path/inside/container ghcr.io/graalvm/jdk:java17-22.0.0 bash
```

If you want to create Docker images that contain GraalVM with Ruby, R, or Python, you can use a Dockerfile like the example below, which uses `ghcr.io/graalvm/jdk:java17-22.0.0` as the base image, installs the Ruby support using the `gu` utility, then creates and runs a sample Ruby program.

```shell
FROM ghcr.io/graalvm/jdk:java17-22.0.0
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
Version: truffleruby 22.0.0, like ruby 3.0.2, GraalVM CE Native [x86_64-darwin]
```
