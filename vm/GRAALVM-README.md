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

GraalVM provides a simple installation utility, GraalVM Updater, which allows users to **download** and **install** additional GraalVM components that are not part of the core distribution:
- Native Image
- LLVM Runtime
- LLVM Toolchain
- JavaScript
- Node.js
- Python
- R
- Ruby
- Java on Truffle
- VisualVM

For more information, visit https://www.graalvm.org/docs/getting-started/ and run:
>`bin/gu --help`

## Using GraalVM
You can use GraalVM like a Java Development Kit (JDK) in your IDE.
