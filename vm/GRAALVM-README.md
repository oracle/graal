# GraalVM

GraalVM is an ecosystem for compiling and running applications written in most modern programming languages and offers the following benefits:

* **Performance**: GraalVM leverages years of research into compiler technology to give you better peak performance on average than any other JVM.
* **Polyglot Interoperability**: Combining programming languages in the same runtime maximizes your resources and increases code efficiency. Use whichever programming language is best fit for purpose, in any combination. Match the correct code to the use case you need.
* **Embeddable**: The Graal Polyglot SDK removes isolation between programming languages and gives you a next-generation runtime environment where you no longer need to write separate applications to use different languages.
* **Native**: Ahead-of-time (AOT) compiled native images improve application start-up time and reduce memory footprint.
* **Tooling**: GraalVM takes advantage of JVM-based tooling and provides a common set of tools, such as debugging and profiling, that you can use for all your code.

## Using GraalVM Components
Some components (languages) of GraalVM are distributed separately: R, Ruby, and Python. If you need these languages, you have to download their distribution packages from
https://www.graalvm.org/1.0.0-rc1/component-catalog/graal-updater-component-catalog.properties

and install them yourself. Installable component packages are provided separately for individual OS and architectures.

GraalVM provides a simple installation utility, `bin/gu`, which allows to **download** package files, **install** them to their appropriate locations, and **uninstall** them.
Use
>`bin/gu --help`

for brief help.

## Using GraalVM
You can use GraalVM like a Java Development Kit (JDK) in your IDE.
