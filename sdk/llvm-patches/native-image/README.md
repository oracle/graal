LLVM Upstream Patches
---------------------

This directory contains the following patches to LLVM,
which are required by the LLVM backend for Native Image.
All patches are either upstream, under review for upstream merge,
or in preparation for such review:

* [Stackpoints] Enable emission of stackmaps for
 non-MachO binaries on ARM _(merged to upstream, will be included in LLVM 10.0.0: https://reviews.llvm.org/D70069)_
* [Statepoints] Implemented statepoints for ARM _(under review for upstream merge: https://reviews.llvm.org/D66012)_
* [Statepoints] Mark statepoint instructions as clobbering LR on AArch64 _(under review for upstream merge: https://reviews.llvm.org/D74902)_
* [Statepoints] Support for compressed pointers
 in the statepoint emission pass _(review in preparation)_
