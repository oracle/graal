# Source-Level Debugging

Sulong supports source-level debugging on the Netbeans IDE via the
[Truffle Debugging Support plugin](http://plugins.netbeans.org/plugin/68647/truffle-debugging-support)
and on the [Chrome Developer Tools](https://developers.google.com/web/tools/chrome-devtools/) using
GraalVM's Chrome Inspector. This includes support for single-stepping, breakpoints and inspection of
local and global variables.

## Chrome Inspector

Sulong supports source-level debugging with the Chrome Developer Tools.

### Setup

The Chrome Inspector can be used with both the precompiled GraalVM and Sulong built from source.

#### GraalVM

The easiest way to use Sulong's source-level debugging capabilities is to run it from GraalVM.
To start debugging simply run the following in GraalVM's `bin` directory:

    lli --inspect --llvm.enableLVI=true <bitcode file>

#### Built from source

The source code of Chrome Inspector is available as part of the
[Tools suite in the Graal repository](https://github.com/oracle/graal/tree/master/tools).
If you followed the instructions in [Sulong's README](../README.md) and already built Sulong the Tools suite
should be available at `sulong-dev/graal/tools`. Please follow the
[build instructions in Tools's README](https://github.com/oracle/graal/blob/master/tools/README.md)
to compile the code. If you have not yet built Sulong you can download its dependencies, including the
Graal repository, by running `mx sforceimports` from `sulong-dev/sulong`. If you were able to build the
tools correctly you should find `chromeinspector.jar` and `truffle-profiler.jar` in the directory
`sulong-dev/graal/tools/mxbuild/dists`.

To debug a program you can then use the following command in `sulong-dev/sulong`:

    mx --cp-sfx <path to truffle-profiler.jar>:<path to chromeinspector.jar> lli -Dpolyglot.llvm.enableLVI=true -Dpolyglot.inspect=true <bitcode file>

This can also be used in conjunction with the `-d` option to `mx`, allowing you to attach a Java
Debugger for debugging Sulong itself in addition to the source-level debugger.

### Usage

When launched, the inspector will suspend execution at the first instruction of the program and print
a link to the console. Pasting this link into Chrome's address bar will open the developer tools for you.

#### Breakpoints

Breakpoints can only be set in functions that have already been parsed. Sulong defaults to parsing
functions in LLVM bitcode files only when they are first being executed. To instead parse functions
eagerly and be able to set breakpoints also in functions not yet executed you can launch Sulong
with the option `--llvm.lazyParsing=false`.

#### Program-defined breakpoints using `__builtin_debugtrap()`

The `__builtin_debugtrap` function enables you to mark locations in your program at which you explicitly
want Sulong to halt the program and switch to the debugger. The debugger automatically halts at each call
to this function as if a breakpoint were set on the call. You can use this feature to quickly reach the
code you are actually trying to debug without having to first find and set a breakpoint on it after
launching your application. You can also instruct Chrome Inspector not to suspend your program at the first
source-level statement being executed. When doing so, Sulong will instead execute your program until it
reaches a call to `__builtin_debugtrap()` before invoking the debugger. To enable this behaviour you need
pass the arguments `--inspect.Suspend=false` and `--inspect.WaitAttached=true` (or
`-Dpolyglot.inspect.Suspend=false` and `-Dpolyglot.inspect.WaitAttached=true` if you compiled Sulong
from source).

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

In Netbeans it is currently not possible to set breakpoints in non-Java source files.
This is a limitation of the Netbeans IDE and will likely never be fixed.

## FAQ

### I am compiling my bitcode files on another system. Can the source-level debugger find the sources on my system?

In general, debug information in LLVM bitcode files uses absolute paths to identify the
location of source code. Usually this requires you to imitate the compiling system's
directory structure in which your sources reside so that the debugger can find them.
To work around this limitation, in C/C++ code you can use the `#line` preprocessor
directive to manually define a path for the source file.

    #line <current line number> <filename/path you want to be used by the debugger for the current file>

You can also use a Sulong-specific format as filename to make the paths pseudo-relative.

    "truffle-relpath://<property>//<relative path/filename>"

Sulong will use the system property `property`, which you can set dynamically, to resolve
`relative path/filename` against.

### Can I also debug my program on LLVM-IR level?

Sulong also contains preliminary support for debugging program on the level of LLVM IR.
This feature is only in the early stages and may contain bugs. To use it, you need to
replace the option `-Dpolyglot.llvm.enableLVI=true` with `-Dpolyglot.llvm.llDebug=true`.
Please note that both `enableLVI` and `llDebug` cannot be used together. Also, to
debug on LLVM-IR level you need to use `llvm-dis` to disassemble the `*.bc` files
which you want to execute. Sulong expects an equally named `*.ll` file in the same
directory as the `*.bc` files it executes. To disassemble all files in a directory
you can use this command:

    for f in $(find . -type f -name *.bc) ; do llvm-dis -o ${f::-3}.ll $f ; done

If you run this command on Sulong's `mxbuild` directory you may notice several
warnings about files with incompatible debug information. These are bitcode files
that were compiled using DragonEgg, which emits a version of LLVM debug information
that is incompatible with modern LLVM releases. You can safely ignore those warnings.
Sulong does not require any debug information to be present in either `*.bc` or `*.ll`
files to debug on the level of LLVM-IR.
