# Debugging on the GraalVM LLVM Runtime

The GraalVM LLVM runtime supports source-level (e.g., the C language) debugging with the [Chrome Developer Tools](https://developers.google.com/web/tools/chrome-devtools/) using GraalVM's Chrome Inspector implementation.
This includes support for single-stepping, breakpoints, and inspection of local and global variables.

To use this feature, make sure to compile your program with debug information enabled by specifying the `-g` argument when compiling with `clang` (the LLVM toolchain shipped with GraalVM will automatically enable debug information).
This gives you the ability to step through the program's source code and set breakpoints in it.

With GraalVM 20.0 and older, the option `--llvm.enableLVI=true` is needed for being able to inspect variables during debugging.
This option is not enabled by default as it decreases the program's run-time performance.
Starting with GraalVM 20.1, this option is not needed anymore and thus deprecated.

To start debugging, run `lli` with the `--inspect` option:
```shell
$GRAALVM_HOME/bin/lli --inspect <bitcode file>
```

When launched, the inspector will suspend execution at the first instruction of the program and print a link to the console.
Pasting this link into Chrome's address bar will open the developer tools for you.

## Breakpoints

Breakpoints can only be set in functions that have already been parsed.
GraalVM defaults to parsing functions in LLVM bitcode files only when they are first being executed.
To instead parse functions eagerly, and be able to set breakpoints also in functions not yet executed, you can use the option `lli --llvm.lazyParsing=false`.

### Program-defined Breakpoints Using `__builtin_debugtrap()`

Program-defined breakpoints using the `__builtin_debugtrap` function enables you to mark locations in the program at which you explicitly want GraalVM to halt the program and switch to the debugger.
The debugger automatically halts at each call to this function as if a breakpoint were set on the call.
You can use this feature to quickly reach the code you are actually trying to debug without having to first find and set a breakpoint on it after launching your application.
You can also instruct Chrome Inspector not to suspend your program at the first source-level statement being executed. When doing so, GraalVM will instead execute your program until it reaches a call to `__builtin_debugtrap()` before invoking the debugger.
To enable this behavior you need to pass the arguments `lli --inspect.Suspend=false --inspect.WaitAttached=true`.

## Locating Source Files

Debug information in LLVM bitcode files contains absolute search paths to identify the
location of source code. If the source files did not move, it should be found automatically.

If the source files moved, or were compiled on a different machine, a search path can be
specified using the `--inspect.SourcePath=<path>` option (multiple paths can be separated
by `:`).
