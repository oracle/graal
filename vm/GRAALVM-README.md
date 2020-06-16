# GraalVM

GraalVM is an ecosystem for compiling and running applications written in most modern programming languages and offers the following benefits:

* **Performance**: GraalVM leverages years of research into compiler technology to give you better
peak performance on average than any other JVM.
* **Interoperability**: Combining programming languages in the same runtime maximizes your resources and
increases code efficiency. Use whichever programming language is best fit for
purpose, in any combination. Match the correct code to the use case you need.
* **Embeddable**: The Graal Polyglot SDK removes isolation between programming languages and gives
you a next-generation runtime environment where you no longer need to write
separate applications to use different languages.
* **Ahead-of-time compilation**: Ahead-of-time compiled native images improve application start-up time and
reduce memory footprint.
* **Tooling**: GraalVM takes advantage of JVM-based tooling and provides a common set of tools,
such as debugging and profiling, that you can use for all your code.

## Using GraalVM Components

Some components are not part of the GraalVM core distribution and must be
downloaded and installed separately. These are [GraalVM Native Image](https://www.graalvm.org/docs/reference-manual/native-image/), [LLVM toolchain](https://www.graalvm.org/docs/reference-manual/languages/llvm/#llvm-toolchain), Python, R and Ruby language engines. Installable component packages
are provided separately for individual operating systems and architectures.

GraalVM provides a simple installation utility, [GraalVM Updater](https://www.graalvm.org/docs/reference-manual/install-components/), which allows to **download** package files, **install** them to their appropriate locations, and **manage**.
Use
>`bin/gu --help`

for brief help.

Alternatively, you can download a component package from the [component catalog](https://www.graalvm.org/component-catalog/graal-updater-component-catalog.properties).
With the already downloaded component JAR file, the component can be installed as

>`gu -L install component.jar`

where `-L` option, equivalent to `--local-file`, tells to install from a downloaded component archive.

## Using GraalVM
You can use GraalVM like a Java Development Kit (JDK) in your IDE.
