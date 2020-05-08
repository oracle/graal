#### Building

Tested with llvm 8 toolchain. 

Build `llvm-stress` and `llvm-reduce`:

    clang++-8 -I/usr/include/llvm-8 -I/usr/include/llvm-c-8  /usr/lib/llvm-8/lib/libLLVM-8.so llvm-stress.cpp
    clang++-8 -I/usr/include/llvm-8 -I/usr/include/llvm-c-8  /usr/lib/llvm-8/lib/libLLVM-8.so llvm-reduce.cpp

Copy `llvm-stress` and `llvm-reduce` to `graal/sdk/mxbuild/linux-amd64/LLVM_TOOLCHAIN`.
Copy `clang` from `sulong/mxbuild/SULONG_BOOTSTRAP_TOOLCHAIN/bin` to `graal/sdk/mxbuild/linux-amd64/LLVM_TOOLCHAIN` and rename it to `clang-sulong-bootstrap`.

#### Usage Example

    mx fuzz testfuzz
    mx ll-reduce --clang-input='<sulong_home>'/fuzzmain.c testfuzz/<<timestamp>>/autogen.ll
