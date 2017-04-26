The Graal project is a framework for developing high performance implementations of languages spanning from
low level languages such as LLVM bitcode up to highly dynamic languages such as R and JavaScript. Its core components are
Truffle and the Graal compiler. Truffle is a stable API for expressing language semantics in a simple abstract syntax
tree (AST) form. Truffle ASTs are executable on any standard Java runtime. However, for optimal peak
performance, the Graal compiler can be used to partially evaluate Truffle ASTs and compile them to efficient machine code.
In addition, the Graal compiler can be deployed as a standard dynamic compiler for the HotSpot JVM, taking the place of the
server (i.e., "C2") compiler.

For further details on Truffle, please go [here](truffle/README.md).

For further details on the Graal compiler, please go [here](compiler/README.md).
