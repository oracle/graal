---
layout: docs
toc_group: llvm
link_title: Limitations and Differences to Native Execution
permalink: /reference-manual/llvm/NativeExecution/
---
# Limitations and Differences to Native Execution

LLVM code interpreted or compiled with the default configuration of GraalVM Community or Enterprise editions will not have the same characteristics as the same code interpreted or compiled in a managed environment, enabled with the `--llvm.managed` option on top of GraalVM Enterprise.
The behaviour of the `lli` interpreter used to directly execute programs in LLVM bitcode format differs between native and managed modes.
The difference lies in safety guarantees and cross-language interoperability.

Note: Managed execution mode for LLVM bitcode is possible with GraalVM Enterprise only.

In the default configuration, cross-language interoperability requires bitcode to be compiled with the debug information enabled (`-g`), and the `-mem2reg` optimization performed on LLVM bitcode (compiled with at least `-O1`, or explicitly using the `opt` tool).
These requirements can be overcome in a managed environment of GraalVM Enterprise that allows native code to participate in the polyglot programs, passing and receiving the data from any other supported language.
In terms of security, the execution of native code in a managed environment passes with additional safety features: catching illegal pointer accesses, accessing arrays outside of the bounds, etc.

There are certain limitations and differences to the native execution depending on the GraalVM edition.
Consider them respectively.

### Limitations and Differences to Native Execution on Top of GraalVM Community
The LLVM interpreter in GraalVM Community Edition environment allows executing LLVM bitcode within a multilingual context.
Even though it aspires to be a generic LLVM runtime, there are certain fundamental and/or implementational limitations that users need to be aware of.

The following restrictions and differences to native execution (i.e., bitcode compiled down to native code) exist when LLVM bitcode is executed with the LLVM interpreter on top of GraalVM Community:

* The GraalVM LLVM interpreter assumes that bitcode was generated to target the x86_64 architecture.
* Bitcode should be the result of compiling C/C++ code using clang version 7, other compilers/languages, e.g., Rust, might have specific requirements that are not supported.
* Unsupported functionality -- it is not possible to call any of the following functions:
  * `clone()`
  * `fork()`
  * `vfork()`
  * `setjmp()`, `sigsetjmp()`, `longjmp()`, `siglongjmp()`
  * Functions of the `exec()` function family
  * Pthread functions
  * Code running in the LLVM interpreter needs to be aware that a JVM is running in the same process, so many syscalls such as fork, brk, sbrk, futex, mmap, rt_sigaction, rt_sigprocmask, etc. might not work as expected or cause the JVM to crash.
  * Calling unsupported syscalls or unsupported functionality (listed above) via native code libraries can cause unexpected side effects and crashes.
* Thread local variables
  * Thread local variables from bitcode are not compatible with thread local variables from native code.
* Cannot rely on memory layout
  * Pointers to thread local variables are not stored in specific locations, e.g., the FS segment.
  * The order of globals in memory might be different, consequently no assumptions about their relative locations can be made.
  * Stack frames cannot be inspected or modified using pointer arithmetic (overwrite return address, etc.).
  * Walking the stack is only possible using the Truffle APIs.
  * There is a strict separation between code and data, so that reads, writes and pointer arithmetic on function pointers or pointers to code will lead to undefined behavior.
* Signal handlers
  * Installing signal handlers is not supported.
* The stack
  * The default stack size is not set by the operating system but by the option `--llvm.stackSize`.
* Dynamic linking
  * Interacting with the LLVM bitcode dynamic linker is not supported, e.g., dlsym/dlopen can only be used for native libraries.
  * The dynamic linking order is undefined if native libraries and LLVM bitcode libraries are mixed.
  * Native libraries cannot import symbols from bitcode libraries.
* x86_64 inline assembly is not supported.
* Undefined behavior according to C spec
  * While most C compilers map undefined behavior to CPU semantics, the GraalVM LLVM interpreter might map some of this undefined behavior to Java or other semantics. Examples include: signed integer overflow (mapped to the Java semantics of an arithmetic overflow), integer division by zero (will throw an ArithmeticException), oversized shift amounts (mapped to the Java behavior).
* Floating point arithmetics
  * Some floating point operations and math functions will use more precise operations and cast the result to a lower precision (instead of performing the operation at a lower precision).
  * Only the rounding mode FE_TONEAREST is supported.
  * Floating point exceptions are not supported.
* NFI limitations (calling real native functions)
  * Structs, complex numbers, or fp80 values are not supported as by-value arguments or by-value return values.
  * The same limitation applies to calls back from native code into interpreted LLVM bitcode.
* Limitations of polyglot interoperability (working with values from other GraalVM languages)
  * Foreign objects cannot be stored in native memory locations. Native memory locations include:
    - globals (except the specific case of a global holding exactly one pointer value);
    - malloc'ed memory (including c++ new, etc.);
    - stack (e.g., escaping automatic variables).
* LLVM instruction set support (based on LLVM 7.0.1)
  * A set of rarely-used bitcode instructions are not available (va_arg, catchpad, cleanuppad, catchswitch, catchret, cleanupret, fneg, callbr).
  * The instructions with limited support:
    - atomicrmw (only supports sub, add, and, nand, or, xor, xchg);
    - extract value and insert value (only supports a single indexing operand);
    - cast (missing support for certain rarely-used kinds);
    - atomic ordering and address space attributes of load and store instructions are ignored.
  * Values -- assembly constants are not supported (module-level assembly and any assembly strings).
  * Types:
    - There is no support for 128-bit floating point types (fp128 and ppc_fp128), x86_mmx, half-precision floats (fp16) and any vectors of unsupported primitive types.
    - The support for fp80 is limited (not all intrinsics are supported for fp80, some intrinsics or instructions might silently fall back to fp64).
* A number of rarely-used or experimental intrinsics based on LLVM 7.0.1 are not supported because of implementational limitations or because they are out of scope:
  * experimental intrinsics: `llvm.experimental.*`, `llvm.launder.invariant.group`, `llvm.strip.invariant.group`;
  * trampoline intrinsics: `llvm.init.trampoline`, `llvm.adjust.trampoline`;
  * general intrinsics: `llvm.var.annotation`, `llvm.ptr.annotation`, `llvm.annotation`, `llvm.codeview.annotation`, `llvm.trap`, `llvm.debugtrap`, `llvm.stackprotector`, `llvm.stackguard`, `llvm.ssa_copy`, `llvm.type.test`, `llvm.type.checked.load`, `llvm.load.relative`, `llvm.sideeffect`;
  * specialised arithmetic intrinsics: `llvm.canonicalize`, `llvm.fmuladd`;
  * standard c library intrinsics: `llvm.fma`, `llvm.trunc`, `llvm.nearbyint`, `llvm.round`;
  * code generator intrinsics: `llvm.returnaddress`, `llvm.addressofreturnaddress`, `llvm.frameaddress`, `llvm.localescape`, `llvm.localrecover`, `llvm.read_register`, `llvm.write_register`, `llvm.stacksave`, `llvm.stackrestore`, `llvm.get.dynamic.area.offset`, `llvm.prefetch`, `llvm.pcmarker`, `llvm.readcyclecounter`, `llvm.clear_cache`, `llvm.instrprof*`, `llvm.thread.pointer`;
  * exact gc intrinsics: `llvm.gcroot`, `llvm.gcread`, `llvm.gcwrite`;
  * element wise atomic memory intrinsics: `llvm.*.element.unordered.atomic`;
  * masked vector intrinsics: `llvm.masked.*`;
  * bit manipulation intrinsics: `llvm.bitreverse`, `llvm.fshl`, `llvm.fshr`.

### Limitations and Differences to Managed Execution on Top of GraalVM Enterprise

The managed execution for LLVM bitcode is a GraalVM Enterprise Edition feature and can be enabled with the `--llvm.managed` command line option.
In managed mode, the GraalVM LLVM runtime prevents access to unmanaged memory and uncontrolled calls to native code and operating system functionality.
The allocations are performed in the managed Java heap, and accesses to the surrounding system are routed through proper Language API and Java API calls.

All the restrictions from the default native LLVM execution on GraalVM apply to the managed execution, but with the following differences/changes:

* Platform independent
   * Bitcode must be compiled for the generic `linux_x86_64` target using the provided musl libc library on all platforms, regardless of the actual underlying operating system.
 * C++
   * C++ on managed mode requires GraalVM 20.1 or newer.
 * Native memory and code
   * Calls to native functions are not possible. Thus only the functionality provided in the supplied musl libc and by the GraalVM LLVM interface is available.
   * Loading native libraries is not possible.
   * Native memory access is not possible.
 * System calls
   * System calls with only limited support are read, readv, write, writev, open, close, dup, dup2, lseek, stat, fstat, lstat, chmod, fchmod, ioctl, fcntl, unlink, rmdir, utimensat, uname, set_tid_address, gettid, getppid, getpid, getcwd, exit, exit_group, clock_gettime, and arch_prctl.
   * The functionality is limited to common terminal IO, process control, and file system operations.
   * Some syscalls are implemented as a noop and/or return error warning that they are not available, e.g., chown, lchown, fchown, brk, rt_sigaction, sigprocmask, and futex.
 * Musl libc
   * The musl libc library behaves differently than the more common glibc [in some cases](https://wiki.musl-libc.org/functional-differences-from-glibc.html).
 * The stack
   * Accessing the stack pointer directly is not possible.
   * The stack is not contiguous, and accessing memory that is out of the bounds of a stack allocation (e.g., accessing neighboring stack value using pointer arithmetics) is not possible.
 * Pointers into the managed heap
   * Reading parts of a managed pointer is not possible.
   * Overwriting parts of a managed pointer (e.g., using bits for pointer tagging) and subsequently dereferencing the destroyed managed pointer is not possible.
   * Undefined behavior in C pointer arithmetics applies.
   * Complex pointer arithmetics (e.g., multiplying pointers) can convert a managed pointer to an i64 value. The i64 value can be used in pointer comparisons but cannot be dereferenced.
 * Floating point arithmetics
   * 80-bit floating points only use 64-bit floating point precision.
 * Dynamic linking
   * The interaction with the LLVM bitcode dynamic linker is not supported, e.g., dlsym/dlopen cannot be used. This does not allow to load native code.
