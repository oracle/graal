LLVM Upstream Patches
---------------------

This directory contains the following patches to LLVM,
which are required by the LLVM backend for Native Image.
All patches are either upstream, under review for upstream merge,
or in preparation for such review:

| Patch | Ticket(s) |
|:------|:----------|
| [AArch64] Introduce option to force placement of the frame record on top of the stack frame | [D150938](https://reviews.llvm.org/D150938) |
| [RISCV][NFC] Add generateMCInstSeq in RISCVMatInt | backported from [main](https://github.com/llvm/llvm-project/commit/d2f8ba7d6dc7251815f1431cf8715053576615f4) |
| [RISCV] Make EmitToStreamer return whether Inst is compressed | backported from [main](https://github.com/llvm/llvm-project/commit/8dc006ea4008c1af298e56c4db6fffe2a40a2ba9) |
| [RISCV] Implement Statepoint and Patchpoint lowering to call instructions | backported from [main](https://github.com/llvm/llvm-project/commit/53003e36e9f4574d06c22611f61f68de32c89c6b) |
