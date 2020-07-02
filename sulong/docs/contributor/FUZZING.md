#### Building

Tested with llvm 9 toolchain.

`mx build`

#### Usage Example

    mx fuzz testfuzz
    mx ll-reduce --clang-input='<path:com.oracle.truffle.llvm.tools.fuzzing.native>/src/fuzzmain.c' testfuzz/<<timestamp>>/autogen.ll
    bugpoint -mlimit=0 -keep-main -opt-command=<<path to opt tool>> -compile-custom -compile-command='mx check-interesting' autogen.ll

If bugpoint erroneously passes duplicated parameters to check-interesting, extract the compile command into a shell script.
