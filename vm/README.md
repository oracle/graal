# Graal VM

Graal VM is an ecosystem for compiling and running applications written in most modern programming languages and offers the following benefits:

* **Performance**: Graal VM leverages years of research into compiler technology to give you better peak performance on average than any other JVM.
* **Polyglot Interoperability**: Combining programming languages in the same runtime maximizes your resources and increases code efficiency. Use whichever programming language is best fit for purpose, in any combination. Match the correct code to the use case you need.
* **Embeddable**: The Graal Polyglot SDK removes isolation between programming languages and gives you a next-generation runtime environment where you no longer need to write separate applications to use different languages.
* **Native**: Ahead-of-time (AOT) compiled native images improve application start-up time and reduce memory footprint.
* **Tooling**: Graal VM takes advantage of JVM-based tooling and provides a common set of tools, such as debugging and profiling, that you can use for all your code.

## Using Graal VM Components
Some components (languages) of Graal VM are distributed separately: R and Ruby. If you need these languages, you have to download their distribution packages from
> **TBD**

and install them yourself. Installable component packages are provided separately for individual OS and architectures.

Graal VM provides a simple installation utility, `bin/installer`, which allows to **install** package files to their appropriate locations and
can **uninstall** them.
Use
>`bin/installer --help`

for brief help.

## Using Graal VM
You can use Graal VM like a Java Development Kit (JDK) in your IDE.
