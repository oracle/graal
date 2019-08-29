# How to add a target architecture to GraalVM using the LLVM backend

## Target-specific LLVM settings

There are a few instances where the Graal code has to go deeper than the target-independent nature of LLVM.
These are most notably inline assembly snippets to implement direct register accesses and direct register jumps (for trampolines), as well as precisions about the structure of the stack frames of the code emitted by LLVM.
All in all, this represents less than a dozen simple values to be set for each new target, and it is our goal that in the future this will be the only addition needed to support a new target.

_([Complete set of values for AArch64](https://github.com/oracle/graal/commit/80cceec6f6299181d94e844eb22dffbef3ecc9e4))_

## LLVM statepoint support

While the LLVM backend uses mostly common, well-supported features of LLVM, garbage collection support implies the use of statepoint intrinsics, an experimental feature of LLVM.
Currently this feature is only supported for x86_64, and we are currently pushing for the inclusion our implementation for AArch64 in the code base.
This means that, unless a significant effort is put together by the LLVM community, supporting a new architecture will require the implementation of statepoints in LLVM for the requested target.
As most of the statepoint logic is handled at the bitcode level, i.e. at a target-independent stage, this is mostly a matter of emitting the right type of calls to lower the statepoint intrinsics.
Our AArch64 implementation of statepoints consists of less than 100 lines of code.

_([Implementation of statepoints for AArch64](https://reviews.llvm.org/D66012))_

## Object file support

The data section for programs created with the LLVM backend of the Graal compiler is currently emitted independently from the code, which is handled by LLVM.
This means that Graal needs an understanding of object file relocations for the target architecture to be able to link the LLVM-compiled code with the Graal-generated data section.
Emitting the data section with the code as LLVM bitcode is our next priority for the LLVM backend, so this should not be an issue for future targets.

_(see `ELFMachine$ELFAArch64Relocations` for an example)_
