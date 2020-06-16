---
name: "\U0001F409 LLVM Runtime Issue Report"
about: Report an issue with the GraalVM LLVM Runtime. To report a security vulnerability, please see below or the SECURITY.md file at the root of the repository. Do not open a GitHub issue.
title: '[LLVM] '
labels: llvm
assignees: ''

---
**Describe GraalVM and your environment :**
 - GraalVM version or commit id if built from source: **[e.g. 19.3]**
 - CE or EE: **[e.g.: CE]**
 - JDK version: **[e.g.: JDK8]**
 - OS and OS Version: **[e.g.: macOS Catalina]**
 - Architecture: **[e.g.: amd64]**
 - The output of `java -Xinternalversion`:
```
 **PASTE OUTPUT HERE**
```

**Have you verified this issue still happens when using the latest snapshot?**
You can find snapshot builds here: https://github.com/graalvm/graalvm-ce-dev-builds/releases

**Describe the issue**
A clear and concise description of the issue.

**How to compile the LLVM bitcode that causes the problem**
 - Did you use the bundled [llvm toolchain](https://www.graalvm.org/docs/reference-manual/languages/llvm/#compiling-to-llvm-bitcode) for compiling to LLVM bicode (see `lli --print-toolchain-path`)?
 - If not, does problem still occur when using the toolchain?
 - If the toolchain cannot be used, which compilers/tools were use and what versions (e.g., `clang --version`)?

**Code snippet or code repository that reproduces the problem**

**Steps to reproduce the problem**
Please include both build steps as well as run steps
1. Step one [e.g.: `$LLVM_TOOLCHAIN/clang mytest.c -o mytest`]
2. Step two [e.g.: `$GRAAALVM_HOME/bin/lli mytest`]

**Expected behavior**
A clear and concise description of what you expected to happen.

**Additional context**
Add any other context about the problem here.
