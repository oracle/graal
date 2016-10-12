This page describes various presentations and publications related to Graal.

### Graal Tutorial

This tutorial presents Graal, a high-performance dynamic compiler for Java written in Java. It covers the following topics:

*   Key distinguishing features of Graal,
*   Introduction to the Graal IR: basic properties, instructions, and optimization phases
*   Speculative optimizations: first-class support for optimistic optimizations and deoptimization
*   Graal API: separation of the compiler from the VM
*   Snippets: expressing high-level semantics in low-level Java code
*   Compiler intrinsics: use all your hardware instructions with Graal
*   Using Graal for static analysis
*   Custom compilations with Graal: integration of the compiler with an application or library
*   Graal as a compiler for dynamic programming languages in the Truffle framework

CGO 2015, February 7-11, San Francisco, CA<br>
Video recording: [Part 1](https://youtu.be/Af9T9kFk1lM), [Part 2](https://youtu.be/WyU7KctlhzE)<br>
[Download Slides](http://lafo.ssw.uni-linz.ac.at/papers/2015_CGO_Graal.pdf)

### Graal Papers
*   Josef Eisl, Matthias Grimmer, Doug Simon, Thomas Würthinger, Hanspeter Mössenböck:<br>
    __Trace-based Register Allocation in a JIT Compiler__<br>
    In _Proceedings of the 13th International Conference on Principles and Practices of Programming on the Java Platform: Virtual Machines, Languages, and Tools (PPPJ)_, 2016.<br>
    [Download Paper](http://dl.acm.org/citation.cfm?id=2972206.2972211)

*   Josef Eisl:<br>
    __Trace register allocation__<br>
    In _Companion Proceedings of the 2015 ACM SIGPLAN International Conference on Systems, Programming, Languages and Applications: Software for Humanity (SPLASH Companion)_, 2015.<br>
    [Download Paper](http://dl.acm.org/citation.cfm?doid=2814189.2814199)

*   Doug Simon, Christian Wimmer, Bernhard Urban, Gilles Duboscq, Lukas Stadler, Thomas Würthinger:<br>
    __Snippets: Taking the High Road to a Low Level__<br>
    In _Transactions on Architecture and Code Optimization (TACO)_, 2015<br>
    [Download paper](http://dx.doi.org/10.1145/2764907)<br>
    Describes Graal Snippets.

*   David Leopoldseder, Lukas Stadler, Christian Wimmer, Hanspeter Mössenböck:<br>
    __Java-to-JavaScript Translation via Structured Control Flow Reconstruction of Compiler IR__<br>
    In _Proceedings of the 11th Dynamic Language Symposium (DLS)_, 2015.<br>
    [Download Paper](http://dx.doi.org/10.1145/2816707.2816715)

*   Gilles Duboscq, Thomas Würthinger, Hanspeter Mössenböck:<br>
    __Speculation Without Regret: Reducing Deoptimization Meta-data in the Graal compiler__<br>
    In _Proceedings of the Intl. Conf. on Principles and Practice of Programming in Java (PPPJ'14)_, 2014<br>
    [Download paper](http://ssw.jku.at/General/Staff/GD/PPPJ-2014-duboscq-29.pdf)<br>
    Describes techniques used in Graal to reduce the memory footprint of speculative optimizations.

*   Lukas Stadler:<br>
    __Partial Escape Analysis and Scalar Replacement for Java__<br>
    PhD thesis, Johannes Kepler University Linz, 2014.<br>
    [Download](http://ssw.jku.at/Research/Papers/Stadler14PhD/Thesis_Stadler_14.pdf)

*   Lukas Stadler, Thomas Würthinger, Hanspeter Mössenböck:<br>
    __Partial Escape Analysis and Scalar Replacement for Java__<br>
    In _Proceedings of the Symposium on Code Generation and Optimization (CGO)_, 2014.<br>
    [Download paper](http://ssw.jku.at/Research/Papers/Stadler14/Stadler2014-CGO-PEA.pdf)<br>
    Describes Graal's advanced escape analysis algorithm.

*   Gilles Duboscq, Thomas Würthinger, Lukas Stadler, Christian Wimmer, Doug Simon, Hanspeter Mössenböck:<br>
    __An Intermediate Representation for Speculative Optimizations in a Dynamic Compiler__<br>
    In _Proceedings of the Workshop on Virtual Machines and Intermediate Languages_, 2013.<br>
    [Download paper](http://lafo.ssw.uni-linz.ac.at/papers/2013_VMIL_GraalIR.pdf)<br>
    Describes the speculative optimizations that Graal's graph-base intermediate representation allows.

*   Lukas Stadler, Gilles Duboscq, Hanspeter Mössenböck, Thomas Würthinger, Doug Simon:<br>
    __An Experimental Study of the Influence of Dynamic Compiler Optimizations on Scala Performance__<br>
    In _Proceedings of the 4th Workshop on Scala_ (SCALA '13), 2013.<br>
    [Download paper](http://lampwww.epfl.ch/~hmiller/scala2013/resources/pdfs/paper9.pdf)

*   Gilles Duboscq, Lukas Stadler, Thomas Würthinger, Doug Simon, Christian Wimmer, Hanspeter Mössenböck:<br>
    __Graal IR: An Extensible Declarative Intermediate Representation__<br>
    In _Proceedings of the Asia-Pacific Programming Languages and Compilers Workshop_, 2013.<br>
    [Download paper](http://lafo.ssw.uni-linz.ac.at/papers/2013_APPLC_GraalIR.pdf)<br>
    Describes the basic architecture of Graal's graph-based intermediate representation.

*   Lukas Stadler, Gilles Duboscq, Hanspeter Mössenböck, Thomas Würthinger:<br>
    __Compilation Queueing and Graph Caching for Dynamic Compilers__<br>
    In _Proceedings of the sixth ACM workshop on Virtual machines and intermediate languages_, 2012.<br>
    [Download paper](http://lafo.ssw.uni-linz.ac.at/papers/2012_VMIL_Graal.pdf)

*   Thomas Würthinger, Christian Wimmer, Andreas Wöß, Lukas Stadler, Gilles Duboscq, Christian Humer, Gregor Richards, Doug Simon, Mario Wolczko:<br>
    __One VM to Rule Them All__<br>
    In _Proceedings of Onward!_, 2013.<br>
    [Download paper](http://lafo.ssw.uni-linz.ac.at/papers/2013_Onward_OneVMToRuleThemAll.pdf)<br>
    Describes the vision of the Truffle approach, and the full system stack including the interpreter and dynamic compiler.

*   Thomas Würthinger, Andreas Wöß, Lukas Stadler, Gilles Duboscq, Doug Simon, Christian Wimmer:<br>
    __Self-Optimizing AST Interpreters__<br>
    In _Proceedings of the Dynamic Languages Symposium (DLS)_, 2012.<br>
    [Download paper](http://lafo.ssw.uni-linz.ac.at/papers/2012_DLS_SelfOptimizingASTInterpreters.pdf)<br>
    Describes the design of self-optimizing and self-specializing interpreter, and the application to JavaScript.
