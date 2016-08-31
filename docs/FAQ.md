## General

### How can I get started understanding Sulong?

A good starting point to understand any Truffle language is to read the papers
[Self-optimizing AST interpreters](http://dl.acm.org/citation.cfm?id=2384587) and
[One VM to rule them all](http://dl.acm.org/citation.cfm?id=2509581).
A heavily documented Truffle language sample implementation is
[SimpleLanguage](https://github.com/graalvm/simplelanguage). To
understand how Sulong calls native functions you can read the paper
[An efficient native function interface for Java](http://dl.acm.org/citation.cfm?id=2500832).

To start working with the code you can try to tackle one of the
[open issues](https://github.com/graalvm/sulong/issues?utf8=%E2%9C%93&q=is%3Aissue+is%3Aopen+label%3Abeginner+).
Do not hesitate to ask questions!

### How does Sulong call native functions?

Sulong uses the Graal Native Function Interface to call native
functions. You can read the paper [An efficient native function interface for Java
](http://dl.acm.org/citation.cfm?id=2500832) to learn more about how
it works.

### How can I get a list of options supported by Sulong?

Invoke `mx su-options` in the Sulong directory.

## Mx

### How does mx execute a specific command?

To get a command line command you can use the `-v` mx option. For
example, `mx -v su-tests` prints the command line when executing the
Sulong test cases.

### What can I do when Java files got corrupted?

You can clean your workspace with `mx clean`. If you only want to clean
Java or native projects you can use `mx clean` with the options
`--no-native` or `--no-java`.

### Why does mx download DragonEgg?

[DragonEgg](http://dragonegg.llvm.org/) is a GCC plugin with which we
can use GCC to compile a source language to LLVM IR. Sulong uses
DragonEgg in its test cases to compile Fortran files to LLVM IR.
Sulong also uses DragonEgg for the C/C++ test cases besides Clang to get
additional "free" test cases for a given C/C++ file.

## Why does Sulong rely on GCC 4.6?

Sulong uses the GCC plugin [DragonEgg](http://dragonegg.llvm.org/) which
works best with GCC versions 4.5, 4.6, and 4.7.
Per default, our mx commands try to use GCC version 4.6. To use another
version, you can edit `sulong/mx.sulong/env` and, e.g., set the
following environment variables:

```
SULONG_GCC=gcc47
SULONG_GPP=g++47
```

### How can I debug a failing test case?

You can use the Sulong `-Dsulong.Debug=true` option to print the test
case that is currently executed, e.g. with
`mx su-tests-sulong -Dsulong.Debug=true`.
After you identified the failing test case you can run the bitcode file
with `mx su-run <file name>.ll`. To see other useful debug
options you can invoke `mx su-options` and look at the `DEBUG`
category.

### Why are some test cases launched in a remote JVM?

We use the Graal Native Function Interface to call native functions such
as `printf`. Thus, we cannot intercept printing to the console such as
we could do in Java. Instead, we create a remote JVM for some test case
suites for which we then read the process output. We use this process
output for validating the test case output. You can disable the remote
launching (for debugging purposes) with the
`sulong.LaunchRemoteTestCasesLocally` option.

## Eclipse

### How can I use Sulong with Eclipse?

In order to work with Eclipse, use `mx eclipseinit` to generate the
Eclipse project files. Import not only the Sulong project, but also the
Graal, Truffle, and JVMCI projects. You have the choice to either use
remote debugging and launch Sulong in mx, or launch Sulong within
Eclipse.

If you use Eclipse to launch Sulong, you have to ensure that all needed
packages are on the classpath and all necessary options set. You can
determine them by using `-v` in the mx Sulong command to make mx
output information on how it executed the command.

### How can I debug Sulong with Eclipse?

To debug the execution of a bitcode file in Eclipse, first start an mx
command with the `-d` flag, e.g.:

    $ mx -d su-run test.ll
    Listening for transport dt_socket at address: 8000

In Eclipse, set a breakpoint, navigate to
`Run->Debug->Remote Java Application` and select one
of the debug configurations, e.g.,`truffle-attach-localhost-8000`.
After clicking `Debug`, execution starts and the program should stop at
the specified breakpoint.

## Errors

### `UnsatisfiedLinkError` in `HotSpotNativeFunctionInterface`

With new installations of Sulong it can happen, that a shared
library needed by the test cases (or other parts of Sulong) cannot be found.

For example, the following error could appear:

    Exception in thread "main" java.lang.UnsatisfiedLinkError:
    /usr/lib/libxml2.so: cannot open shared object file: No such file or directory
        at com.oracle.graal.truffle.hotspot.nfi.HotSpotNativeFunctionInterface.getLibraryHandle(HotSpotNativeFunctionInterface.java:68)
        at com.oracle.graal.truffle.hotspot.nfi.HotSpotNativeFunctionInterface.getLibraryHandle(HotSpotNativeFunctionInterface.java:1)
        at com.oracle.truffle.llvm.nativeint.NativeLookup.getNativeFunctionHandles(NativeLookup.java:99)
        at com.oracle.truffle.llvm.nativeint.NativeLookup.getLibraryHandles(NativeLookup.java:84)
        at com.oracle.truffle.llvm.nativeint.NativeLookup.lookupSymbol(NativeLookup.java:120)
        at com.oracle.truffle.llvm.nativeint.NativeLookup.getNativeHandle(NativeLookup.java:153)

In case of an `UnsatisfiedLinkError`, first check whether the external
dependencies are installed. You can check the package names of the
external dependencies for Ubuntu in the `.travis.yml` file.
There you can find the command `sudo apt-get install -y libxml2-dev`
to install the library on Ubuntu. For other distributions, you usually
can find packages with the same content under a similar name.

If installing the library does not solve the problem, then probably
because the shared library is expected to be at a certain location,
such as in `/usr/lib` in the example above.
In such a case, first identify the location of the library (e.g.,
`locate libxml2.so`) and then create a link to the library in the
expected location (e.g., `ln -s /usr/lib/x86_64-linux-gnu/libxml2.so /usr/lib/libxml2.so`).
