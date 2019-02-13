## General

### How can I get started understanding Sulong?

A good starting point is to read and look through the
[papers and presentations about Sulong](PUBLICATIONS.md).
The paper [Sulong - Execution of LLVM-Based Languages on the JVM](http://2016.ecoop.org/event/icooolps-2016-sulong-execution-of-llvm-based-languages-on-the-jvm)
provides a minimal description of Sulong and describes its main goals.

To understand more about Truffle, on which Sulong is implemented, is to
read the papers [Self-optimizing AST interpreters](http://dl.acm.org/citation.cfm?id=2384587)
and [One VM to rule them all](http://dl.acm.org/citation.cfm?id=2509581).
A heavily documented Truffle language sample implementation is
[SimpleLanguage](https://github.com/graalvm/simplelanguage).

## Mx

### How does mx execute a specific command?

To get a command line command you can use the `-v` mx option. For
example, `mx -v lli test.bc` prints the command line mx uses to run
the file `test.bc` with Sulong.

### What can I do when Java files got corrupted?

You can clean your workspace with `mx clean`. If you only want to clean
Java or native projects you can use `mx clean` with the options
`--no-native` or `--no-java`.

## Eclipse

### How can I use Sulong with Eclipse?

In order to work with Eclipse, use `mx eclipseinit` to generate the
Eclipse project files. Import not only the Sulong project, but also the
Truffle project from `sulong-dev/graal/truffle`. You have the choice to
either use remote debugging and launch Sulong in mx, or launch Sulong
within Eclipse.

If you use Eclipse to launch Sulong, you have to ensure that all needed
packages are on the classpath and all necessary options set. You can
determine them by using `-v` in the mx Sulong command to make mx
output information on how it executed the command.

### How can I debug Sulong with Eclipse?

To debug the execution of a bitcode file in Eclipse, first start an mx
command with the `-d` flag, e.g.:

    $ mx -d lli test.ll
    Listening for transport dt_socket at address: 8000

In Eclipse, set a breakpoint, navigate to
`Run->Debug->Remote Java Application` and select one
of the debug configurations, e.g.,`truffle-attach-localhost-8000`.
After clicking `Debug`, execution starts and the program should stop at
the specified breakpoint.
