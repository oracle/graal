This page describes various presentations and publications related to Truffle.

### Truffle Tutorial

Forget "this language is fast", "this language has the libraries I need", and "this language has the tool support I need".
The Truffle framework for implementing managed languages in Java gives you native performance, multi-language integration with all other Truffle languages, and tool support -- all of that by just implementing an abstract syntax tree (AST) interpreter in Java.
Truffle applies AST specialization during interpretation, which enables partial evaluation to create highly optimized native code without the need to write a compiler specifically for a language.
The Java VM contributes high-performance garbage collection, threads, and parallelism support.

This tutorial is both for newcomers who want to learn the basic principles of Truffle, and for people with Truffle experience who want to learn about recently added features.
It presents the basic principles of the partial evaluation used by Truffle and the Truffle DSL used for type specializations, as well as features that were added recently such as the language-agnostic object model, language integration, and debugging support.

Oracle Labs and external research groups have implemented a variety of programming languages on top of Truffle, including JavaScript, Ruby, R, Python, and Smalltalk. Several of them already exceed the best implementation of that language that existed before.

PLDI 2016, June 13, 2016, Santa Barbara, CA<br>
[Video recording](https://youtu.be/FJY96_6Y3a4)<br>
[Slides](https://lafo.ssw.uni-linz.ac.at/pub/papers/2016_PLDI_Truffle.pdf)

### Truffle Presentations
*   __Debugging at Full Speed: Instrumenting Truffle-implemented Programs__<br>
    JVM Language Summit 2014, July 28-30, Santa Clara, CA<br>
    [Video recording](http://medianetwork.oracle.com/video/player/3731019771001)<br>
    [Download slides](http://www.oracle.com/technetwork/java/jvmls2014vandevanter-2265212.pdf)

*   __One VM to Rule Them All__<br>
    JVM Language Summit 2013, July 29-31, Santa Clara, CA<br>
    [Video recording](http://medianetwork.oracle.com/video/player/2623645003001)<br>
    [Download slides](http://lafo.ssw.uni-linz.ac.at/papers/2013_JVMLanguageSummit_OneVMToRuleThemAll.pdf)

*   __Truffle: A Self-Optimizing Runtime System__<br>
    SPLASH 2012, October 19-26, Tucson, AZ<br>
    [Download slides](http://lafo.ssw.uni-linz.ac.at/papers/2012_SPLASH_Truffle_Slides.pdf)<br>
    [Download absract](http://lafo.ssw.uni-linz.ac.at/papers/2012_SPLASH_Truffle.pdf)


### Truffle Papers
*   Benoit Daloze, Stefan Marr, Daniele Bonetta, Hanspeter Mössenböck:<br>
    __Efficient and Thread-Safe Objects for Dynamically-Typed Languages__<br>
    Accepted for the _Annual ACM SIGPLAN Conference on Object-Oriented Programming, Systems, Languages, and Applications 2016 (OOPSLA)_, 2016.<br>
    [Download Paper](http://ssw.jku.at/General/Staff/Daloze/thread-safe-objects.pdf)

*   Manuel Rigger, Matthias Grimmer, Christian Wimmer, Thomas Würthinger, Hanspeter Mössenböck:<br>
    __Bringing Low-Level Languages to the JVM: Efficient Execution of LLVM IR on Truffle__<br>
    Accepted for the _Workshop on Virtual Machines and Intermediate Languages (VMIL)_, 2016.

*   Manuel Rigger, Matthias Grimmer, Hanspeter Mössenböck:<br>
    __Sulong -- Execution of LLVM-Based Languages on the JVM__<br>
    In _Proceedings of International Workshop on Implementation, Compilation, Optimization of Object-Oriented Languages, Programs and Systems (ICOOOLPS)_, 2016.<br>
    [Download Paper](http://2016.ecoop.org/event/icooolps-2016-sulong-execution-of-llvm-based-languages-on-the-jvm)

*   Manuel Rigger:<br>
    __Sulong: Memory Safe and Efficient Execution of LLVM-Based Languages__<br>
    In _Proceedings of the ECOOP 2016 Doctoral Symposium_, 2016<br>
    [Download Paper](http://ssw.jku.at/General/Staff/ManuelRigger/ECOOP16-DS.pdf)

*   Benoit Daloze, Chris Seaton, Daniele Bonetta, Hanspeter Mössenböck:<br>
    __Techniques and Applications for Guest-Language Safepoints__<br>
    In _Proceedings of the International Workshop on Implementation, Compilation, Optimization of Object-Oriented Languages, Programs and Systems (ICOOOLPS)_, 2015.<br>
    [Download Paper](http://ssw.jku.at/Research/Papers/Daloze15.pdf)

*   Matthias Grimmer, Chris Seaton, Roland Schatz, Würthinger, Hanspeter Mössenböck:<br>
    __High-Performance Cross-Language Interoperability in a Multi-Language Runtime__<br>
    In _Proceedings of the 11th Dynamic Language Symposium (DLS)_, 2015.<br>
    [Donwload Paper](http://dx.doi.org/10.1145/2816707.2816714)

*   Matthias Grimmer, Chris Seaton, Thomas Würthinger, Hanspeter Mössenböck:<br>
    __Dynamically Composing Languages in a Modular Way: Supporting C Extensions for Dynamic Languages.__<br>
    In _Proceedings of the 14th International Conference on Modularity_, 2015.<br>
    [Download Paper](http://chrisseaton.com/rubytruffle/modularity15/rubyextensions.pdf)

*   Gülfem Savrun-Yeniçeri, Michael Van De Vanter, Per Larsen, Stefan Brunthaler, and Michael Franz:<br>
    __An Efficient and Generic Event-based Profiler Framework for Dynamic Languages__<br>
    In _Proceedings of the International Conference on Principles and Practices of Programming on The Java Platform: virtual machines, languages, and tools (PPPJ)_, 2015.<br>
    [Download Paper](http://dl.acm.org/citation.cfm?id=2807435)

*   Michael Van De Vanter:<br>
    __Building Debuggers and Other Tools: We Can "Have it All"__ (Position Paper)<br>
    In _Proceedings of the 10th Implementation, Compilation, Optimization of Object-Oriented Languages, Programs and Systems Workshop (ICOOOLPS)_, 2015.<br>
    [Download paper](http://vandevanter.net/mlvdv/publications/2015-icooolps.pdf)

*   Matthias Grimmer:<br>
    __High-performance language interoperability in multi-language runtimes__<br>
    In _Proceedings of the companion publication of the 2014 ACM SIGPLAN conference on Systems, Programming, and Applications: Software for Humanity (SPLASH Companion)_, 2014.<br>
    [Download paper](http://dl.acm.org/citation.cfm?doid=2660252.2660256)

*   Matthias Grimmer, Manuel Rigger, Roland Schatz, Lukas Stadler, Hanspeter Mössenböck:<br>
    __Truffle C: Dynamic Execution of C on the Java Virtual Machine__<br>
    In _Proceedings of the International Conference on Principles and Practice of Programming in Java (PPPJ)_, 2014.<br>
    [Download paper](http://dl.acm.org/citation.cfm?id=2647528)

*    Christian Humer, Christian Wimmer, Christian Wirth, Andreas Wöß, Thomas Würthinger:<br>
    __A Domain-Specific Language for Building Self-Optimizing AST Interpreters__<br>
    In _Proceedings of the International Conference on Generative Programming: Concepts and Experiences (GPCE)_, 2014.<br>
    [Download paper](http://lafo.ssw.uni-linz.ac.at/papers/2014_GPCE_TruffleDSL.pdf)

*   Andreas Wöß, Christian Wirth, Daniele Bonetta, Chris Seaton, Christian Humer, Hanspeter Mössenböck:<br>
    __An Object Storage Model for the Truffle Language Implementation Framework__<br>
    In _Proceedings of International Conference on Principles and Practice of Programming in Java (PPPJ)_, 2014.<br>
    [Download paper](http://dl.acm.org/citation.cfm?id=2647517)

*   Matthias Grimmer, Thomas Würthinger, Andreas Wöß, Hanspeter Mössenböck:<br>
    __An Efficient Approach to Access Native Binary Data from JavaScript__<br>
    In _Proceedings of the 9th Workshop on Implementation, Compilation, Optimization of Object-Oriented Languages, Programs and Systems (ICOOOLPS)_, 2014.<br>
    [Download paper](http://dl.acm.org/citation.cfm?id=2633302)

*   Thomas Würthinger, Christian Wimmer, Andreas Wöß, Lukas Stadler, Gilles Duboscq, Christian Humer, Gregor Richards, Doug Simon, Mario Wolczko:<br>
    __One VM to Rule Them All__<br>
    In _Proceedings of Onward!_, 2013.<br>
    [Download paper](http://lafo.ssw.uni-linz.ac.at/papers/2013_Onward_OneVMToRuleThemAll.pdf)<br>
    Describes the vision of the Truffle approach, and the full system stack including the interpreter and dynamic compiler.

*   Chris Seaton, Michael Van De Vanter, and Michael Haupt:<br>
    __Debugging at full speed__<br>
    In _Proceedings of the 8th Workshop on Dynamic Languages and Applications (DYLA)_, 2014.<br>
    [Download paper](http://www.lifl.fr/dyla14/papers/dyla14-3-Debugging_at_Full_Speed.pdf)

*   Matthias Grimmer, Manuel Rigger, Lukas Stadler, Roland Schatz, Hanspeter Mössenböck:<br>
    __An efficient native function interface for Java__<br>
    In _Proceedings of the International Conference on Principles and Practices of Programming on the Java Platform: Virtual Machines, Languages, and Tools. (PPPJ)_, 2013.<br>
    [Download paper](http://dx.doi.org/10.1145/2500828.2500832)

*   Matthias Grimmer:<br>
    __Runtime Environment for the Truffle/C VM__<br>
    Master's thesis, Johannes Kepler University Linz, November 2013.<br>
    [Download](http://ssw.jku.at/Research/Papers/Grimmer13Master/)

*   Thomas Würthinger, Andreas Wöß, Lukas Stadler, Gilles Duboscq, Doug Simon, Christian Wimmer:<br>
    __Self-Optimizing AST Interpreters__<br>
    In _Proceedings of the Dynamic Languages Symposium (DLS)_, 2012.<br>
    [Download paper](http://lafo.ssw.uni-linz.ac.at/papers/2012_DLS_SelfOptimizingASTInterpreters.pdf)<br>
    Describes the design of self-optimizing and self-specializing interpreter, and the application to JavaScript.

### Graal
Truffle uses the Graal compiler, for publications about Graal, check the corresponding file in the [graal repository](https://github.com/graalvm/graal-core/blob/master/docs/Publications.md).
