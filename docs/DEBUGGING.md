# Source-Level Debugging

Sulong supports source-level debugging on the Netbeans IDE via the
[Truffle Debugging Support plugin](http://plugins.netbeans.org/plugin/68647/truffle-debugging-support).
This includes support for single-stepping and inspection of local and global variables.

This feature is still a work in progress and not yet fully available in GraalVM.

## Netbeans

### Setup

#### Downloading the latest version

While the Truffle Debugging Support plugin is available for Netbeans 8.2 the version
offered for it is quite old and incompatible with the current Truffle releases. To
get the newest version of the plugin you need to install the
[latest Netbeans Development Snapshot](http://bits.netbeans.org/download/trunk/nightly/latest/).
You may select any version that supports Java SE. Please note that parallel
installations with the latest Netbeans release version are not supported.

#### Configuring the IDE

In order to use the Truffle debugging support, Netbeans needs to be executed on
GraalVM or on a JVMCI enabled JDK with Truffle and the Graal SDK on the classpath.

You can set the jdk or GraalVM to use when you run the installer.

    <path to netbeans installer> --javahome <path to jvmci-enabled jdk>

If you are not running Netbeans on GraalVM you need to add the required Truffle
libraries to the classpath when starting the IDE.

    export NETBEANS=<path to netbeans>
    export GRAAL_SDK=<path to graal-sdk.jar>
    export TRUFFLE_API=<path to truffle-api.jar>
    export TRUFFLE_DEBUG=<path to truffle-debug.jar>
    $NETBEANS --cp:a $GRAAL_SDK:$TRUFFLE_API:$TRUFFLE_DEBUG

You can also persist this setting by appending `-cp:a <the paths as before>` to the
`netbeans_default_options` entry in `<netbeans install dir>/etc/netbeans.conf`.

#### Installing the Truffle Debugging Support plugin

After you have successfully installed and configured Netbeans you can install the
Truffle Debugging Support plugin from the plugin manager.

    Tools -> Plugins -> Available Plugins

### Debugging

There are multiple ways to start debugging Truffle languages in Netbeans. For
Sulong they have 2 common requirements:

* All sourcefiles Netbeans would need to display need to be part of a project
opened in the same IDE as the debugger. This includes the C/C++/other files
from which the bitcode files running on Sulong were compiled as well as
Sulong's sources if you wish to display them.

* If you want to inspect source-level variables, Sulong needs to be launched
with `-Dpolyglot.llvm.enableLVI=true`. Since local variable inspection adds
a slight execution overhead this is not enabled by default.

#### Dedicated Launcher

The simplest way to start debugging is to create a small Java application to
start the debugger. An example can be found [here](Main.java).

To debug the program, simply set a breakpoint at any location where a Truffle
function is being called and start debugging. Once the breakpoint is hit you
can step into the function.

If the launcher is not executed on GraalVM further setup is necessary to ensure
Sulong is on the classpath. You can see how `mx` executes Sulong by running

    mx -v lli file.bc

Please add the same JVM arguments to the Launcher project's run configuration:

    <project menu> -> Properties -> Run -> VM Options

#### Attach Debugger to process

This option requires you to have Sulong's sources imported into Netbeans.
Similar to the previous option you can set a breakpoint at the line

    Value result = context.eval(source);

in the `executeMain(File, String[])` method in the
`com.oracle.truffle.llvm.Sulong` class. You can then run Sulong in debug
mode with

    mx -d lli -Dpolyglot.llvm.enableLVI=true file.bc

and attach the Netbeans debugger to this process.

    Debug -> Attach Debugger

`mx` will print the appropriate port number.

### Limitations

It is currently not possible to set breakpoints in C/C++ source files.

## Chrome Inspector

As an alternative to Netbeans, GraalVM also supports debugging using the
Chrome developer tools. This option is much easier to setup. To start
debugging simply run the following in GraalVM's `bin` directory:

    lli --inspect --llvm.enableLVI=true file.bc

While this is in some instances superior to debugging with Netbeans, e.g.
it is possible to set breakpoints, this feature is currently only
available in GraalVM.
