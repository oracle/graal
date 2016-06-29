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
* 	Doug Simon, Christian Wimmer, Bernhard Urban, Gilles Duboscq, Lukas Stadler, Thomas Würthinger:
  	_Snippets: Taking the High Road to a Low Level_.<br>
    In _Transactions on Architecture and Code Optimization (TACO)_, 2015

    [Download paper](http://dx.doi.org/10.1145/2764907)<br>
    Describes Graal Snippets.

*   Gilles Duboscq, Thomas Würthinger, Hanspeter Mössenböck: _Speculation Without Regret: Reducing Deoptimization Meta-data in the Graal compiler_.<br>
In _Proceedings of the Intl. Conf. on Principles and Practice of Programming in Java (PPPJ'14)_, 2014

    [Download paper](http://ssw.jku.at/General/Staff/GD/PPPJ-2014-duboscq-29.pdf)<br>
    Describes techniques used in Graal to reduce the memory footprint of speculative optimizations.

*   Lukas Stadler: _Partial Escape Analysis and Scalar Replacement for Java_. PhD thesis, Johannes Kepler University Linz, 2014

    [Download](http://ssw.jku.at/Research/Papers/Stadler14PhD/Thesis_Stadler_14.pdf)

*   Lukas Stadler, Thomas Würthinger, Hanspeter Mössenböck: _Partial Escape Analysis and Scalar Replacement for Java_.<br>
    In _Proceedings of the Symposium on Code Generation and Optimization (CGO)_, 2014.

    [Download paper](http://ssw.jku.at/Research/Papers/Stadler14/Stadler2014-CGO-PEA.pdf)<br>
    Describes Graal's advanced escape analysis algorithm.

*   Gilles Duboscq, Thomas Würthinger, Lukas Stadler, Christian Wimmer, Doug Simon, Hanspeter Mössenböck: _An Intermediate Representation for Speculative Optimizations in a Dynamic Compiler_.<br>
    In _Proceedings of the Workshop on Virtual Machines and Intermediate Languages_, 2013.

    [Download paper](http://lafo.ssw.uni-linz.ac.at/papers/2013_VMIL_GraalIR.pdf)<br>
    Describes the speculative optimizations that Graal's graph-base intermediate representation allows.

*   Lukas Stadler, Gilles Duboscq, Hanspeter Mössenböck, Thomas Würthinger, Doug Simon: _An Experimental Study of the Influence of Dynamic Compiler Optimizations on Scala Performance_.<br>
    In _Proceedings of the 4th Workshop on Scala_ (SCALA '13), 2013.

    [Download paper](http://lampwww.epfl.ch/~hmiller/scala2013/resources/pdfs/paper9.pdf)


*   Gilles Duboscq, Lukas Stadler, Thomas Würthinger, Doug Simon, Christian Wimmer, Hanspeter Mössenböck: _Graal IR: An Extensible Declarative Intermediate Representation_.<br>
    In _Proceedings of the Asia-Pacific Programming Languages and Compilers Workshop_, 2013.

    [Download paper](http://lafo.ssw.uni-linz.ac.at/papers/2013_APPLC_GraalIR.pdf)<br>
    Describes the basic architecture of Graal's graph-based intermediate representation.

*   Lukas Stadler, Gilles Duboscq, Hanspeter Mössenböck, Thomas Würthinger: _Compilation Queueing and Graph Caching for Dynamic Compilers_. In _Proceedings of the sixth ACM workshop on Virtual machines and intermediate languages_, 2012.

    [Download paper](http://lafo.ssw.uni-linz.ac.at/papers/2012_VMIL_Graal.pdf)

*   Thomas Würthinger, Christian Wimmer, Andreas Wöß, Lukas Stadler, Gilles Duboscq, Christian Humer, Gregor Richards, Doug Simon, Mario Wolczko: _One VM to Rule Them All_. In _Proceedings of Onward!_, ACM Press, 2013. [doi: 10.1145/2509578.2509581](http://dx.doi.org/10.1145/2509578.2509581)

    [Download paper](http://lafo.ssw.uni-linz.ac.at/papers/2013_Onward_OneVMToRuleThemAll.pdf)<br>
    Describes the vision of the Truffle approach, and the full system stack including the interpreter and dynamic compiler.

*   Thomas Würthinger, Andreas Wöß, Lukas Stadler, Gilles Duboscq, Doug Simon, Christian Wimmer: _Self-Optimizing AST Interpreters_. In _Proceedings of the Dynamic Languages Symposium_, pages 73–82\. ACM Press, 2012\. [doi:10.1145/2384577.2384587](http://dx.doi.org/10.1145/2384577.2384587)

    [Download paper](http://lafo.ssw.uni-linz.ac.at/papers/2012_DLS_SelfOptimizingASTInterpreters.pdf)<br>
    Describes the design of self-optimizing and self-specializing interpreter, and the application to JavaScript.
