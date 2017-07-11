# Architecture Haiku

An Architecture Haiku is ["a one-page, quick-to-build, uber-terse design description."](http://www.neverletdown.net/2015/03/architecture-haiku.html)

## Solution description

Sulong is a system to run C, Fortran, and other native languages on the JVM.
Sulong uses LLVM frontends to compile languages to LLVM IR.
It then executes this IR with a new Truffle LLVM IR interpreter.
Sulong uses the Graal compiler to compile frequently executed functions
to machine code.

## Technical Constraints

Sulong must run on 64-bit Mac and Linux systems supported by both LLVM and Graal.

## Functional Requirements

Sulong should be able to execute all LLVM languages on the JVM.
The current focus is on single-threaded C and Fortran programs.
Other languages are not fully supported, and, e.g., C++ exception handling
is not yet implemented.

## Tradeoffs

* Truffle interoperability over standalone Sulong:
Sulong's main use case is to be used by other Truffle languages and thus
needs to implement the Truffle interoperability API. Design decisions are
made in favor of the interoperability use case, rather than for standalone
Sulong.
* Peak performance over other types of performance: Sulong focuses on
long running server processes and thus aims to achieve the best possible
run-time peak performance. It does not focus on start-up time, warm-up time,
or memory consumption.
* Extensibility over complexity: Sulong's node factory for instantiating
Truffle nodes should be easy replaceable and composable with other node
factories. Abstractions in the parser support this use case, but make the
parser code more complex.
* Native code interoperability over security: Sulong allocates LLVM IR
objects using unmanaged memory to efficiently call native functions
without having to convert or marshal data structures. However, using
unmanaged memory means that the Sulong Truffle interpreter can crash or
corrupt the executing process when dereferencing or writing to invalid
addresses.

## Top Quality Attributes

Truffle and native code interoperability > Peak performance > Extensibility

## Design rationales

* Since Truffle interoperability is important, Sulong complies with the
Truffle API and provides interoperability intrinsics
(see `LLVMNativeIntrinsicsProvider` and `include/truffle.h`).
* Since peak performance is important, Truffle nodes performs profiling
(e.g, in `LLVMValueProfilingNode` and `LLVMBasicBlockNode`) in the
interpreter, and are otherwise kept simple to be compiled to efficient
machine code.
* Since extensibility is important, the parser (`com.oracle.truffle.llvm.parser`)
does not import the node implementations (`com.oracle.truffle.llvm.nodes`)
to directly instantiate the Truffle nodes. Instead, the `NodeFactory`
facade class provides an abstraction to transparently construct Truffle nodes
for LLVM IR constructs. Sulong loads different node factory facades with a
`ServiceLoader` in the `Sulong` class.
* Since native code interoperability is important, Sulong uses the
Truffle Native Function interface to call native functions. It allocates unmanaged
memory for the stack (`LLVMStack`) with `sun.misc.Unsafe` and unmanaged heap
memory by directly calling `malloc` and other standard library allocation
functions.

## Architectural styles and patterns

* Interpreter pattern implemented by the Truffle nodes in `com.oracle.truffle.llvm.nodes`
* Adapted facade pattern combined with factory pattern in `NodeFactory`
to simplify the instantiation of the nodes
* Adapted visitor pattern in `LLVMParserRuntime` and other classes to traverse
the AST produced from the LLVM IR file and construct Truffle nodes for it
