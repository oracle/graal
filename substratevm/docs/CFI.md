# Control Flow Integrity

Control-flow integrity (CFI) ties the actual control flow of a program to the control flow intended by the programmer.
It enforces the control-flow graph when a code pointer is intentionally or unintentionally compromised.
CFI can harden trusted code against control-flow hijacking attacks or restrict the execution of untrusted code.

Forward-edge CFI applies to indirect calls and jumps.
Backward-edge CFI applies to function returns.
Forward-edge CFI can also apply to returns by converting them into indirect branches.

An equivalence class is the set of valid targets for an indirect control-flow transfer.
The size and number of equivalence classes determine CFI granularity.
Coarse-grained CFI has a small number of large equivalence classes, whereas fine-grained CFI has many classes with fewer entries.

## Software CFI

Native Image implements coarse-grained software CFI with one equivalence class that contains every indirect branch and return target.
The implementation is available on AMD64.
It marks targets with the `endbr64` instruction, which acts as a `nop` when hardware CFI is not enabled.

Before an indirect branch, `AMD64SoftwareCFISubstrateMacroAssembler.validateBranchTarget(Register, boolean)` compares the instruction at the target with `endbr64` and traps if they differ.
For returns, `SubstrateAMD64Backend` emits an `AMD64CFIReturnOp`.
This operation replaces `ret` with a `pop` and `jmp` sequence so the assembler can validate the target before branching.

The assembler emits `endbr64` at function entry points and at basic blocks marked as indirect branch targets.
It also wraps calls with a `PostCallAction` that inserts `endbr64` at each return target.

Native libraries are not necessarily built with software CFI and may not contain `endbr64` markers.
Use the `SW_NONATIVE` CFI mode to skip checks for transitions to and from native code.

## Hardware CFI

Native Image implements backward-edge CFI on AArch64 using pointer authentication codes (PAC).
The method prologue signs the return address in the link register with `paciasp`.
After restoring the original stack pointer, the method epilogue authenticates the return address with `autiasp`.
Authentication failure corrupts the address so that the subsequent return deliberately faults.
