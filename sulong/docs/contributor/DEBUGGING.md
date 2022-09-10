# Debugging the GraalVM LLVM Runtime

## Using the Chrome Inspector

The Chrome Developer Tools can be used to debug programs running on the GraalVM
LLVM runtime.

See [the user documentation on debugging](../../../docs/reference-manual/llvm/Debugging.md) for details.

If running from the source directory using `mx`, note that the `tools` suite
needs to be imported for debugging to work:

```
mx --dynamicimport /tools lli --inspect ...
```

## Debugging on the LLVM bitcode level

To diagnose problems in the LLVM runtime itself, it it sometimes useful to debug
programs running on GraalVM on the level of the LLVM bitcode directly, instead
of showing the original high-level (e.g. C language) source code.

This debugging mode can be enabled using the `--llvm.llDebug` option:

```
lli --inspect --experimental-options --llvm.llDebug ...
```

To debug on the LLVM-IR level, you need to provide disassembled bitcode files
next to the binary files that are loaded by GraalVM. These can be produced with
the `llvm-dis` tool. Use the `--log.llvm.LLDebug.level=FINER` option to get
diagnostic messages about missing disassembled bitcode files.

There is a helper script in `mx` to produce the disassembled bitcode files and
put them in the correct place:

```
mx llvm-dis <path-to-file>
```

You can pass any file to `mx llvm-dis` that the GraalVM LLVM runtime can open,
e.g. bitcode files, ELF files with embedded bitcode and so on. The script will
extract the bitcode from the file, disassemble it, and put the result in a file
next to it with the correct name for the `--llvm.llDebug` code to find it.

### Tracing the execution of LLVM bitcode

GraalVM can produce an LLVM IR-level trace of its program execution. You ca
enable this feature by passing the `--log.llvm.TraceIR.level=FINER` option to
`lli`. This requires `--llvm.llDebug` to be enabled and the disassembled bitcode
to be available.

```
$ mx lli --experimental-options --log.llvm.TraceIR.level=FINER --llvm.llDebug hello.bc
[llvm::TraceIR] FINER: >> Entering function @main at hello.ll:9:1 with arguments:[StackPointer 0x7fd46bfff010 (Bounds: 0x7fd466fff010 - 0x7fd46bfff010), 1, 0x7fd4ec261978, 0x7fd4ec261988]
[llvm::TraceIR] FINER: >> hello.ll:10:1 ->   %1 = alloca i32, align 4
[llvm::TraceIR] FINER: >> hello.ll:11:1 ->   store i32 0, i32* %1, align 4
[llvm::TraceIR] FINER: >> hello.ll:12:1 ->   %2 = call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([15 x i8], [15 x i8]* @.str, i32 0, i32 0)), !dbg !13
Hello, World!
[llvm::TraceIR] FINER: >> hello.ll:13:1 ->   ret i32 0, !dbg !14
[llvm::TraceIR] FINER: >> Leaving @main
```

## Debugging the runtime

For debugging the internals of the runtime, it is recommended to run the GraalVM
LLVM runtime in JVM mode (`lli --jvm`). Then a regular Java debugger can be
attached.

If running with `mx`, the `-d` option can be used to enable debugging:

```
$ mx -d lli hello
Listening for transport dt_socket at address: 8000
```

Or for running unit tests (see [TESTS](TESTS.md)):

```
$ mx -d unittest SulongSuite
Listening for transport dt_socket at address: 8000
```

Alternatively, from a built GraalVM, debugging can be enabled using the standard
Java debugging flags, for example:

```
lli --jvm --vm.Xdebug --vm.Xrunjdwp:transport=dt_socket,server=y,address=8000,suspend=y ...
```

## Mixed debugging

Sometimes it is useful to debug in both modes simultaneously. For that you can
just pass both options, and attach both a Java IDE and the Chrome Inspector to
the VM:

```
mx --dynamicimport /tools -d lli --inspect ...
```

That way, it is possible to for example step through a C program to the
interesting point, and then enable a breakpoint in the Java debugger, so it will
suspend on the next "step" command in the Chrome Inspector, or vice versa.

## Detail Formatters

Editors such as Eclipse support the specification of custom code formatters. The
`LLVMNodeUtils` class provides methods for printing the stack trace and AST of
any `LLVMNode`. The follow steps configure this in Eclipse:

1. Open Window -> Preferences
2. Go to Java -> Debug -> Detail Formatters
3. Add a new entry
   1. Set the qualified type name to `com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode`
   2. Set the snippet to
      `com.oracle.truffle.llvm.runtime.LLVMNodeUtils.stackTraceAndAST(this)`

## Debugging Utilities

The class `LLVMDebugPointer` contains functions that may be useful while
debugging an application. For example, given the pointer `char ** myPtr`, the
code snippet `LLVMDebugPointer.of(myPtr).deref().readHex(18)` can be used to
return the bytes `myPtr[0][0]` to `myPtr[0][17]`, such as:

```
00000000  4e 75 6c 6c 21 00 00 00 01 00 00 00 00 00 00 00  Null!...........
00000010  00 00                                            ..
```

The class contains the following functions:

* `readI8/.../readI64` to read the integer value at the pointer address
* `readHex(n)` returns the hex output of the `n` bytes at the pointer address
* `readAsciiString` returns the null-terminated string at the pointer address
* `deref` debug the pointer at the pointer address
* `asHex` format the pointer address as a hex value
* `increment(n)` shift the pointer by `n` bytes
