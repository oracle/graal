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

1. Having the Docker daemon running, pull the image from GitHub with `docker pull`:
```shell
docker pull ghcr.io/graalvm/graalvm-ce:latest
```
2. Alternatively, use as the base image in [Dockerfile](https://docs.docker.com/engine/reference/builder/):
```shell
FROM ghcr.io/graalvm/graalvm-ce:latest
```

There are different GraalVM Community container images provided depending on the architecture and the Java version.
GraalVM binaries are built for Linux, macOS, and Windows platforms on x86 64-bit systems, and for Linux on ARM 64-bit systems.
The images are multi-arch (`aarch64` or `amd64` will be pulled depending on Docker host architecture), and named per a _platform-jdk-version_ naming scheme, for example, `ghcr.io/graalvm/graalvm-ce:latest:ol8-java11-21.2.0`.
A complete list can be found on the [All versions](https://github.com/orgs/graalvm/packages/container/graalvm-ce/versions) page.

The images are based on Oracle Linux and has GraalVM Community downloaded, unzipped and made available.
It means that Java, JavaScript, Node.js and the LLVM runtime are available out of the box.

You can start a container and enter the `bash` session with the following run command:
```shell
docker run -it --rm ghcr.io/graalvm/graalvm-ce:21.2.0 bash
```
Check that `java`, `js` and other commands work as expected.
```shell
→ docker run -it --rm ghcr.io/graalvm/graalvm-ce:21.2.0 bash
bash-4.4# java -version
openjdk version "11.0.12" 2021-07-20
OpenJDK Runtime Environment GraalVM CE 21.2.0 (build 11.0.12+6-jvmci-21.2-b06)
OpenJDK 64-Bit Server VM GraalVM CE 21.2.0 (build 11.0.12+6-jvmci-21.2-b06, mixed mode, sharing)
bash-4.4# js -version
GraalVM JavaScript (GraalVM CE Native 21.2.0)
> 1 + 1
2
> quit()
bash-4.4# lli --version
LLVM 10.0.0 (GraalVM CE Native 21.2.0)
bash-4.4#
```

Please note that the image contains only the components immediately available in the GraalVM Community core download.
However, the [GraalVM Updater](/reference-manual/graalvm-updater/) utility is on the `PATH` and you can install the support for additional languages and runtimes like Node.js, Ruby, R, Python or WebAssembly at will.

However, the [GraalVM Updater, `gu`](/reference-manual/graalvm-updater/), utility is included in the container image and may be used to install additional languages and runtimes like Node.js, Ruby, R, Python or WebAssembly.
For example, the following command installs the Ruby support (the output below is truncated for brevity):

```shell
docker run -it --rm ghcr.io/graalvm/graalvm-ce:21.2.0 bash
bash-4.4# gu install ruby
Downloading: Component catalog
Processing component archive: Component ruby
Downloading: Component ruby
[######              ]
...
```

Here is a sample command that maps the `/absolute/path/to/directory/no/trailing/slash` directory from the host system to the `/path/inside/container` inside the container.

```shell
docker run -it --rm -v /absolute/path/to/directory/no/trailing/slash:/path/inside/container ghcr.io/graalvm/graalvm-ce:21.2.0 bash
```

If you want to create Docker images that contain GraalVM with Ruby, R, or Python, you can use a Dockerfile like the example below, which uses `ghcr.io/graalvm/graalvm-ce:21.2.0` as the base image, installs the Ruby support using the `gu` utility, then creates and runs a sample Ruby program.

```shell
FROM ghcr.io/graalvm/graalvm-ce:21.2.0
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
Version: truffleruby 21.2.0, like ruby 2.7.3, GraalVM CE Native [x86_64-darwin]
```
