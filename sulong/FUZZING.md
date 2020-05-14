#### Building

Tested with llvm 9 toolchain.

`mx build`

#### Usage Example

    mx fuzz testfuzz
    mx ll-reduce --clang-input='<path:com.oracle.truffle.llvm.tools.fuzzing.native>/src/fuzzmain.c' testfuzz/<<timestamp>>/autogen.ll
