# Debugging on the GraalVM LLVM Runtime

The GraalVM LLVM runtime supports source-level (e.g. C language) debugging with the
[Chrome Developer Tools](https://developers.google.com/web/tools/chrome-devtools/) using GraalVM's
Chrome Inspector. This includes support for single-stepping, breakpoints and inspection of local
and global variables.

To start debugging simply run the following in GraalVM's `bin` directory:

```
$GRAALVM/bin/lli --inspect <bitcode file>
```

When launched, the inspector will suspend execution at the first instruction of the program and print
a link to the console. Pasting this link into Chrome's address bar will open the developer tools for you.

## Breakpoints

Breakpoints can only be set in functions that have already been parsed. GraalVM defaults to parsing
functions in LLVM bitcode files only when they are first being executed. To instead parse functions
eagerly and be able to set breakpoints also in functions not yet executed you can use the option
`lli --llvm.lazyParsing=false`.

## Program-defined breakpoints using `__builtin_debugtrap()`

The `__builtin_debugtrap` function enables you to mark locations in your program at which you explicitly
want GraalVM to halt the program and switch to the debugger. The debugger automatically halts at each call
to this function as if a breakpoint were set on the call. You can use this feature to quickly reach the
code you are actually trying to debug without having to first find and set a breakpoint on it after
launching your application. You can also instruct Chrome Inspector not to suspend your program at the first
source-level statement being executed. When doing so, GraalVM will instead execute your program until it
reaches a call to `__builtin_debugtrap()` before invoking the debugger. To enable this behaviour you need
pass the arguments `lli --inspect.Suspend=false --inspect.WaitAttached=true`.

## Locating source files

Debug information in LLVM bitcode files contains absolute search paths to identify the
location of source code. If the source didn't move, it should be found automatically.

If the source files moved, or were compiled on a different machine, a search path can be
specified using the `--inspect.SourcePath=<path>` option (multiple paths can be separated
by `:`).
