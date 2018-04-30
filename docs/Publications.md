This page describes various presentations and publications related to Graal and Truffle that were published by Oracle Labs and its academic collaborators.

## Truffle Tutorial

Forget "this language is fast", "this language has the libraries I need", and "this language has the tool support I need".
The Truffle framework for implementing managed languages in Java gives you native performance, multi-language integration with all other Truffle languages, and tool support -- all of that by just implementing an abstract syntax tree (AST) interpreter in Java.
Truffle applies AST specialization during interpretation, which enables partial evaluation to create highly optimized native code without the need to write a compiler specifically for a language.
The Java VM contributes high-performance garbage collection, threads, and parallelism support.

This tutorial is both for newcomers who want to learn the basic principles of Truffle, and for people with Truffle experience who want to learn about recently added features.
It presents the basic principles of the partial evaluation used by Truffle and the Truffle DSL used for type specializations, as well as features that were added recently such as the language-agnostic object model, language integration, and debugging support.

Oracle Labs and external research groups have implemented a variety of programming languages on top of Truffle, including JavaScript, Ruby, R, Python, and Smalltalk. Several of them already exceed the best implementation of that language that existed before.

PLDI 2016, June 13, 2016, Santa Barbara, CA  
[Video recording](https://youtu.be/FJY96_6Y3a4)  
[Slides](https://lafo.ssw.uni-linz.ac.at/pub/papers/2016_PLDI_Truffle.pdf)

## Graal Tutorial

This tutorial presents Graal, a high-performance dynamic compiler written in Java. Because it is highly configurable and extensible, it delivers excellent peak performance for a diverse set of managed languages including Java (beating the Java HotSpot server compiler), JavaScript (beating the V8 Crankshaft compiler), Ruby, and R. This lifts compiler research using Graal to a new level: researchers can evaluate new compiler optimizations immediately on many languages. If you are a language implementer who is curious how modern VMs like the Java HotSpot VM or the V8 JavaScript VM optimize your code, you will get all your questions answered too.

In detail, the tutorial covers the following topics:

* Key distinguishing features of Graal,
* Introduction to the Graal IR: basic properties, instructions, and optimization phases
* Speculative optimizations: first-class support for optimistic optimizations and deoptimization
* Graal API: separation of the compiler from the VM
* Snippets: expressing high-level semantics in low-level Java code
* Compiler intrinsics: use all your hardware instructions with Graal
* Using Graal for static analysis
* Custom compilations with Graal: integration of the compiler with an application or library
* Graal as a compiler for dynamic programming languages

PLDI 2017, June 18-23, Barcelona, Spain  
[Video recording](https://www.youtube.com/watch?v=5_Y3kc--eTI)   
[Download Slides](http://lafo.ssw.uni-linz.ac.at/papers/2017_PLDI_GraalTutorial.pdf)

## Truffle Presentations

**Debugging at Full Speed: Instrumenting Truffle-implemented Programs**  
JVM Language Summit 2014, July 28-30, Santa Clara, CA  
[Video recording](http://medianetwork.oracle.com/video/player/3731019771001)  
[Download slides](http://www.oracle.com/technetwork/java/jvmls2014vandevanter-2265212.pdf)

**One VM to Rule Them All**  
JVM Language Summit 2013, July 29-31, Santa Clara, CA  
[Video recording](http://medianetwork.oracle.com/video/player/2623645003001)  
[Download slides](http://lafo.ssw.uni-linz.ac.at/papers/2013_JVMLanguageSummit_OneVMToRuleThemAll.pdf)

**Truffle: A Self-Optimizing Runtime System**  
SPLASH 2012, October 19-26, Tucson, AZ  
[Download slides](http://lafo.ssw.uni-linz.ac.at/papers/2012_SPLASH_Truffle_Slides.pdf)  
[Download absract](http://lafo.ssw.uni-linz.ac.at/papers/2012_SPLASH_Truffle.pdf)

## Truffle Papers

### 2018

- Michael Van De Vanter, Chris Seaton, Michael Haupt, Christian Humer, and Thomas Würthinger  
[**Fast, Flexible, Polyglot Instrumentation Support for Debuggers and other Tools**](https://arxiv.org/pdf/1803.10201v1.pdf)  
In _The Art, Science, and Engineering of Programming, vol. 2, no. 3, 2018, article 14 (<Programming 2018>, Nice, France, April 12, 2018)_  
[DOI](https://doi.org/10.22152/programming-journal.org/2018/2/14)  


### 2017

- T. Würthinger, C. Wimmer, C. Humer, A. Wöss, L. Stadler, C. Seaton, G. Duboscq, D. Simon, M. Grimmer  
[**Practical Partial Evaluation for High-Performance Dynamic Language Runtimes**](http://chrisseaton.com/rubytruffle/pldi17-truffle/pldi17-truffle.pdf)  
In _Proceedings of the Conference on Programming Language Design and Implementation (PLDI)_  
[Video recording](https://www.youtube.com/watch?v=8eff207KPkA&list=PLMTm6Ln7vQZZv6sQ0I4R7iaIjvSVhHXod&index=42)  
[DOI: 10.1145/3062341.3062381](https://doi.org/10.1145/3062341.3062381)

- Michael Van De Vanter  
[**Building Flexible, Low-Overhead Tooling Support into a High-Performance Polyglot VM (Extended Abstract)**](http://vandevanter.net/mlvdv/publications/mlvdv-morevms-2017.pdf)  
_MoreVMs Workshop on Modern Language Runtimes, Ecosystems, and VMs_.

### 2016

- Benoit Daloze, Stefan Marr, Daniele Bonetta, Hanspeter Mössenböck  
[**Efficient and Thread-Safe Objects for Dynamically-Typed Languages**](http://ssw.jku.at/General/Staff/Daloze/thread-safe-objects.pdf)  
In _Proceedings of the Conference on Object-Oriented Programming, Systems, Languages, and Applications (OOPSLA)_.

- Manuel Rigger, Matthias Grimmer, Christian Wimmer, Thomas Würthinger, Hanspeter Mössenböck  
[**Bringing Low-Level Languages to the JVM: Efficient Execution of LLVM IR on Truffle**](https://doi.org/10.1145/2998415.2998416)  
In _Proceedings of the Workshop on Virtual Machines and Intermediate Languages (VMIL)_.

- Manuel Rigger, Matthias Grimmer, Hanspeter Mössenböck  
[**Sulong -- Execution of LLVM-Based Languages on the JVM**](http://2016.ecoop.org/event/icooolps-2016-sulong-execution-of-llvm-based-languages-on-the-jvm)  
In _Proceedings of International Workshop on Implementation, Compilation, Optimization of Object-Oriented Languages, Programs and Systems (ICOOOLPS)_.

- Manuel Rigger  
[**Sulong: Memory Safe and Efficient Execution of LLVM-Based   Languages**](http://ssw.jku.at/General/Staff/ManuelRigger/ECOOP16-DS.pdf)  
In _Proceedings of the ECOOP 2016 Doctoral Symposium_.

### 2015

- Benoit Daloze, Chris Seaton, Daniele Bonetta, Hanspeter Mössenböck  
[**Techniques and Applications for Guest-Language Safepoints**](http://ssw.jku.at/Research/Papers/Daloze15.pdf)  
In _Proceedings of the International Workshop on Implementation, Compilation, Optimization of Object-Oriented Languages, Programs and Systems (ICOOOLPS)_.

- Matthias Grimmer, Chris Seaton, Roland Schatz, Würthinger, Hanspeter Mössenböck  
[**High-Performance Cross-Language Interoperability in a Multi-Language Runtime**](http://dx.doi.org/10.1145/2816707.2816714)  
In _Proceedings of the 11th Dynamic Language Symposium (DLS)_.

- Matthias Grimmer, Chris Seaton, Thomas Würthinger, Hanspeter Mössenböck  
[**Dynamically Composing Languages in a Modular Way: Supporting C Extensions for Dynamic Languages.**](http://chrisseaton.com/rubytruffle/modularity15/rubyextensions.pdf)  
In _Proceedings of the 14th International Conference on Modularity_.

- Gülfem Savrun-Yeniçeri, Michael Van De Vanter, Per Larsen, Stefan Brunthaler, and Michael Franz  
[**An Efficient and Generic Event-based Profiler Framework for Dynamic Languages**](http://dl.acm.org/citation.cfm?id=2807435)  
In _Proceedings of the International Conference on Principles and Practices of Programming on The Java Platform: virtual machines, languages, and tools (PPPJ)_.

- Michael Van De Vanter  
[**Building Debuggers and Other Tools: We Can "Have it All" (Position Paper)**](http://vandevanter.net/mlvdv/publications/2015-icooolps.pdf)  
In _Proceedings of the 10th Implementation, Compilation, Optimization of Object-Oriented Languages, Programs and Systems Workshop (ICOOOLPS)_.

### 2014

- Matthias Grimmer  
[**High-performance language interoperability in multi-language runtimes**](http://dl.acm.org/citation.cfm?doid=2660252.2660256)  
In _Proceedings of the companion publication of the 2014 ACM SIGPLAN conference on Systems, Programming, and Applications: Software for Humanity (SPLASH Companion)_.

- Matthias Grimmer, Manuel Rigger, Roland Schatz, Lukas Stadler, Hanspeter Mössenböck  
[**Truffle C: Dynamic Execution of C on the Java Virtual Machine**](http://dl.acm.org/citation.cfm?id=2647528)  
In _Proceedings of the International Conference on Principles and Practice of Programming in Java (PPPJ)_.

-  Christian Humer, Christian Wimmer, Christian Wirth, Andreas Wöß, Thomas Würthinger  
[**A Domain-Specific Language for Building Self-Optimizing AST Interpreters**](http://lafo.ssw.uni-linz.ac.at/papers/2014_GPCE_TruffleDSL.pdf)  
In _Proceedings of the International Conference on Generative Programming: Concepts and Experiences (GPCE)_.

- Andreas Wöß, Christian Wirth, Daniele Bonetta, Chris Seaton, Christian Humer, Hanspeter Mössenböck  
[**An Object Storage Model for the Truffle Language Implementation Framework**](http://dl.acm.org/citation.cfm?id=2647517)  
In _Proceedings of International Conference on Principles and Practice of Programming in Java (PPPJ)_.

- Matthias Grimmer, Thomas Würthinger, Andreas Wöß, Hanspeter Mössenböck  
[**An Efficient Approach to Access Native Binary Data from JavaScript**](http://dl.acm.org/citation.cfm?id=2633302)  
In _Proceedings of the 9th Workshop on Implementation, Compilation, Optimization of Object-Oriented Languages, Programs and Systems (ICOOOLPS)_.

- Chris Seaton, Michael Van De Vanter, and Michael Haupt  
[**Debugging at full speed**](http://www.lifl.fr/dyla14/papers/dyla14-3-Debugging_at_Full_Speed.pdf)  
In _Proceedings of the 8th Workshop on Dynamic Languages and Applications (DYLA)_.

### 2013

- Thomas Würthinger, Christian Wimmer, Andreas Wöß, Lukas Stadler, Gilles Duboscq, Christian Humer, Gregor Richards, Doug Simon, Mario Wolczko  
[**One VM to Rule Them All**](http://lafo.ssw.uni-linz.ac.at/papers/2013_Onward_OneVMToRuleThemAll.pdf)  
In _Proceedings of Onward!_.  
Describes the vision of the Truffle approach, and the full system stack including the interpreter and dynamic compiler.

- Matthias Grimmer, Manuel Rigger, Lukas Stadler, Roland Schatz, Hanspeter Mössenböck  
[**An efficient native function interface for Java**](http://dx.doi.org/10.1145/2500828.2500832)  
In _Proceedings of the International Conference on Principles and Practices of Programming on the Java Platform: Virtual Machines, Languages, and Tools. (PPPJ)_.

- Matthias Grimmer  
[**Runtime Environment for the Truffle/C VM**](http://ssw.jku.at/Research/Papers/Grimmer13Master/)  
Master's thesis, Johannes Kepler University Linz, November 2013.

### 2012

- Thomas Würthinger, Andreas Wöß, Lukas Stadler, Gilles Duboscq, Doug Simon, Christian Wimmer  
[**Self-Optimizing AST Interpreters**](http://lafo.ssw.uni-linz.ac.at/papers/2012_DLS_SelfOptimizingASTInterpreters.pdf)  
In _Proceedings of the Dynamic Languages Symposium (DLS)_.  
Describes the design of self-optimizing and self-specializing interpreter, and the application to JavaScript.

## Graal Papers

### 2016

- Josef Eisl, Matthias Grimmer, Doug Simon, Thomas Würthinger, Hanspeter Mössenböck  
[**Trace-based Register Allocation in a JIT Compiler**](http://dx.doi.org/10.1145/2972206.2972211)  
In Proceedings of the 13th International Conference on Principles and Practices of Programming on the Java Platform: Virtual Machines, Languages, and Tools (PPPJ '16)

- Stefan Marr, Benoit Daloze, Hanspeter Mössenböck  
[**Cross-language compiler benchmarking: are we fast yet?**](https://doi.org/10.1145/2989225.2989232)  
In Proceedings of the 12th Symposium on Dynamic Languages (DLS 2016)

- Manuel Rigger, Matthias Grimmer, Christian Wimmer, Thomas Würthinger, Hanspeter Mössenböck  
[**Bringing low-level languages to the JVM: efficient execution of LLVM IR on Truffle**](https://doi.org/10.1145/2998415.2998416)  
In Proceedings of the 8th International Workshop on Virtual Machines and Intermediate Languages (VMIL 2016)

- Manuel Rigger  
[**Sulong: Memory Safe and Efficient Execution of LLVM-Based Languages**](http://ssw.jku.at/General/Staff/ManuelRigger/ECOOP16-DS.pdf)  
ECOOP 2016 Doctoral Symposium

- Manuel Rigger, Matthias Grimmer, Hanspeter Mössenböck  
[**Sulong - Execution of LLVM-Based Languages on the JVM**](http://2016.ecoop.org/event/icooolps-2016-sulong-execution-of-llvm-based-languages-on-the-jvm)  
Int. Workshop on Implementation, Compilation, Optimization of Object-Oriented Languages, Programs and Systems (ICOOOLPS'16)

- Luca Salucci, Daniele Bonetta, Walter Binder  
[**Efficient Embedding of Dynamic Languages in Big-Data Analytics**](http://ieeexplore.ieee.org/document/7756203/)  
International Conference on Distributed Computing Systems Workshops (ICDCSW 2016)

- Lukas Stadler, Adam Welc, Christian Humer, Mick Jordan  
[**Optimizing R language execution via aggressive speculation**](https://doi.org/10.1145/2989225.2989236)  
In Proceedings of the 12th Symposium on Dynamic Languages (DLS 2016)

- Daniele Bonetta, Luca Salucci, Stefan Marr, Walter Binder  
[**GEMs: shared-memory parallel programming for Node.js**](https://doi.org/10.1145/2983990.2984039)  
In Proceedings of the 2016 ACM SIGPLAN International Conference on Object-Oriented Programming, Systems, Languages, and Applications (OOPSLA 2016)

- Benoit Daloze, Stefan Marr, Daniele Bonetta, Hanspeter Mössenböck  
[**Efficient and thread-safe objects for dynamically-typed languages**](https://doi.org/10.1145/3022671.2984001)  
In Proceedings of the 2016 ACM SIGPLAN International Conference on Object-Oriented Programming, Systems, Languages, and Applications (OOPSLA 2016)

- Luca Salucci, Daniele Bonetta, Walter Binder  
[**Lightweight Multi-language Bindings for Apache Spark**](http://link.springer.com/chapter/10.1007/978-3-319-43659-3_21)  
European Conference on Parallel Processing (Euro-Par 2016)

- Luca Salucci, Daniele Bonetta, Stefan Marr, Walter Binder  
[**Generic messages: capability-based shared memory parallelism for event-loop systems**](https://doi.org/10.1145/3016078.2851184)  
Proceedings of the 21st ACM SIGPLAN Symposium on Principles and Practice of Parallel Programming (PPoPP 2016)

- Stefan Marr, Chris Seaton, Stéphane Ducasse  
[**Zero-overhead metaprogramming: reflection and metaobject protocols fast and without compromises**](http://dx.doi.org/10.1145/2813885.2737963)  
In Proceedings of the 36th ACM SIGPLAN Conference on Programming Language Design and Implementation (PLDI 2016)

### 2015

- Josef Eisl  
[**Trace register allocation**](https://doi.org/10.1145/2814189.2814199)    
In Companion Proceedings of the 2015 ACM SIGPLAN International Conference on Systems, Programming, Languages and Applications: Software for Humanity (SPLASH Companion 2015)

- Matthias Grimmer, Chris Seaton, Roland Schatz, Thomas Würthinger, Hanspeter Mössenböck  
[**High-performance cross-language interoperability in a multi-language runtime**](http://dx.doi.org/10.1145/2936313.2816714)  
In Proceedings of the 11th Symposium on Dynamic Languages (DLS 2015)

- Matthias Grimmer, Roland Schatz, Chris Seaton, Thomas Würthinger, Hanspeter Mössenböck  
[**Memory-safe Execution of C on a Java VM**](http://dx.doi.org/10.1145/2786558.2786565)  
In Proceedings of the 10th ACM Workshop on Programming Languages and Analysis for Security (PLAS'15)

- Matthias Grimmer, Chris Seaton, Thomas Würthinger, Hanspeter Mössenböck  
[**Dynamically composing languages in a modular way: supporting C extensions for dynamic languages**](http://dx.doi.org/10.1145/2724525.2728790)  
In Proceedings of the 14th International Conference on Modularity (MODULARITY 2015)

- Doug Simon, Christian Wimmer, Bernhard Urban, Gilles Duboscq, Lukas Stadler, Thomas Würthinger  
[**Snippets: Taking the High Road to a Low Level**](http://dx.doi.org/10.1145/2764907)  
ACM Transactions on Architecture and Code Optimization (TACO)

- David Leopoldseder, Lukas Stadler, Christian Wimmer, Hanspeter Mössenböck  
[**Java-to-JavaScript translation via structured control flow reconstruction of compiler IR**](http://dx.doi.org/10.1145/2816707.2816715)  
In Proceedings of the 11th Symposium on Dynamic Languages (DLS 2015)

- Codruţ Stancu, Christian Wimmer, Stefan Brunthaler, Per Larsen, Michael Franz  
[**Safe and efficient hybrid memory management for Java**](http://dx.doi.org/10.1145/2887746.2754185)  
Proceedings of the 2015 International Symposium on Memory Management (ISMM '15)

- Gülfem Savrun-Yeniçeri, Michael L. Van de Vanter, Per Larsen, Stefan Brunthaler, Michael Franz  
[**An Efficient and Generic Event-based Profiler Framework for Dynamic Languages**](http://dx.doi.org/10.1145/2807426.2807435)  
In Proceedings of the Principles and Practices of Programming on The Java Platform (PPPJ '15)

- Michael L. Van De Vanter  
[**Building debuggers and other tools: we can "have it all"**](http://dx.doi.org/10.1145/2843915.2843917)  
In Proceedings of the 10th Workshop on Implementation, Compilation, Optimization of Object-Oriented Languages, Programs and Systems (ICOOOLPS '15)

- Benoit Daloze, Chris Seaton, Daniele Bonetta, Hanspeter Mössenböck  
[**Techniques and applications for guest-language safepoints**](http://dx.doi.org/10.1145/2843915.2843921)  
In Proceedings of the 10th Workshop on Implementation, Compilation, Optimization of Object-Oriented Languages, Programs and Systems (ICOOOLPS '15)

### 2014

- Wei Zhang, Per Larsen, Stefan Brunthaler, Michael Franz  
[**Accelerating iterators in optimizing AST interpreters**](https://doi.org/10.1145/2660193.2660223)  
 In Proceedings of the 2014 ACM International Conference on Object Oriented Programming Systems Languages & Applications (OOPSLA '14)

- Matthias Grimmer  
[**High-performance language interoperability in multi-language runtimes**](http://dx.doi.org/10.1145/2660252.2660256)  
 In Proceedings of the companion publication of the 2014 ACM SIGPLAN conference on Systems, Programming, and Applications: Software for Humanity (SPLASH '14)

- Matthias Grimmer, Manuel Rigger, Roland Schatz, Lukas Stadler, Hanspeter Mössenböck  
 [**TruffleC: dynamic execution of C on a Java virtual machine**](http://dx.doi.org/10.1145/2647508.2647528)  
 In Proceedings of the 2014 International Conference on Principles and Practices of Programming on the Java platform: Virtual machines, Languages, and Tools (PPPJ '14)

- Matthias Grimmer, Thomas Würthinger, Andreas Wöß, Hanspeter Mössenböck  
[**An efficient approach for accessing C data structures from JavaScript**](http://dx.doi.org/10.1145/2633301.2633302)  
In Proceedings of the 9th International Workshop on Implementation, Compilation, Optimization of Object-Oriented Languages, Programs and Systems PLE (ICOOOLPS '14)

- Christian Humer, Christian Wimmer, Christian Wirth, Andreas Wöß, Thomas Würthinger  
[**A domain-specific language for building self-optimizing AST interpreters**](http://dx.doi.org/10.1145/2658761.2658776)  
In Proceedings of the 2014 International Conference on Generative Programming: Concepts and Experiences (GPCE 2014)

- Gilles Duboscq, Thomas Würthinger, Hanspeter Mössenböck  
[**Speculation without regret: reducing deoptimization meta-data in the Graal compiler**](http://dx.doi.org/10.1145/2647508.2647521)  
In Proceedings of the 2014 International Conference on Principles and Practices of Programming on the Java platform: Virtual machines, Languages, and Tools (PPPJ '14)

- Thomas Würthinger  
[**Graal and truffle: modularity and separation of concerns as cornerstones for building a multipurpose runtime**](http://dx.doi.org/10.1145/2584469.2584663)  
In Proceedings of the companion publication of the 13th international conference on Modularity (MODULARITY '14)

- Lukas Stadler, Thomas Würthinger, Hanspeter Mössenböck  
[**Partial Escape Analysis and Scalar Replacement for Java**](http://dx.doi.org/10.1145/2544137.2544157)  
In Proceedings of Annual IEEE/ACM International Symposium on Code Generation and Optimization (CGO '14)

- Christian Häubl, Christian Wimmer, Hanspeter Mössenböck  
[**Trace transitioning and exception handling in a trace-based JIT compiler for java**](http://dx.doi.org/10.1145/2579673)  
ACM Transactions on Architecture and Code Optimization (TACO)

- Chris Seaton, Michael L. Van De Vanter, Michael Haupt  
[**Debugging at Full Speed**](http://dx.doi.org/10.1145/2617548.2617550)  
In Proceedings of the Workshop on Dynamic Languages and Applications (Dyla'14)

- Andreas Wöß, Christian Wirth, Daniele Bonetta, Chris Seaton, Christian Humer, Hanspeter Mössenböck  
[**An object storage model for the truffle language implementation framework**](http://dx.doi.org/10.1145/2647508.2647517)  
In Proceedings of the 2014 International Conference on Principles and Practices of Programming on the Java platform: Virtual machines, Languages, and Tools (PPPJ '14)

- Codruţ Stancu, Christian Wimmer, Stefan Brunthaler, Per Larsen, Michael Franz  
[**Comparing points-to static analysis with runtime recorded profiling data**](http://dx.doi.org/10.1145/2647508.2647524)  
In Proceedings of the 2014 International Conference on Principles and Practices of Programming on the Java platform: Virtual machines, Languages, and Tools (PPPJ '14)

### 2013

- Matthias Grimmer, Manuel Rigger, Lukas Stadler, Roland Schatz, Hanspeter Mössenböck  
[**An efficient native function interface for Java**](http://dx.doi.org/10.1145/2500828.2500832)  
In Proceedings of the 2013 International Conference on Principles and Practices of Programming on the Java Platform: Virtual Machines, Languages, and Tools (PPPJ '13)

- Thomas Würthinger, Christian Wimmer, Andreas Wöß, Lukas Stadler, Gilles Duboscq, Christian Humer, Gregor Richards, Doug Simon, Mario Wolczko  
[**One VM to rule them all**](http://dx.doi.org/10.1145/2509578.2509581)  
In Proceedings of the 2013 ACM international symposium on New ideas, new paradigms, and reflections on programming & software (Onward! 2013)

- Gilles Duboscq, Thomas Würthinger, Lukas Stadler, Christian Wimmer, Doug Simon, Hanspeter Mössenböck  
[**An intermediate representation for speculative optimizations in a dynamic compiler**](http://dx.doi.org/10.1145/2542142.2542143)  
In Proceedings of the 7th ACM workshop on Virtual machines and intermediate languages (VMIL '13)

- Lukas Stadler, Gilles Duboscq, Hanspeter Mössenböck, Thomas Würthinger, Doug Simon  
[**An experimental study of the influence of dynamic compiler optimizations on Scala performance**](http://dx.doi.org/10.1145/2489837.2489846)  
In Proceedings of the 4th Workshop on Scala (SCALA '13)

- Gilles Duboscq, Lukas Stadler, Thomas Würthinger, Doug Simon, Christian Wimmer, Hanspeter Mössenböck  
[**Graal IR: An Extensible Declarative Intermediate Representation**](http://ssw.jku.at/General/Staff/GD/APPLC-2013-paper_12.pdf)  
In Proceedings of the Asia-Pacific Programming Languages and Compilers Workshop, 2013.

- Christian Häubl, Christian Wimmer, Hanspeter Mössenböck  
[**Context-sensitive trace inlining for Java**](http://dx.doi.org/10.1016/j.cl.2013.04.002)  
Special issue on the Programming Languages track at the 27th ACM Symposium on Applied Computing  
Computer Languages, Systems & Structures

- Christian Wimmer, Stefan Brunthaler  
[**ZipPy on truffle: a fast and simple implementation of python**](http://dx.doi.org/10.1145/2508075.2514572)  
In Proceedings of the 2013 companion publication for conference on Systems, programming, & applications: software for humanity (SPLASH '13)

- Christian Häubl, Christian Wimmer, Hanspeter Mössenböck  
[**Deriving code coverage information from profiling data recorded for a trace-based just-in-time compiler**](http://dx.doi.org/10.1145/2500828.2500829)  
In Proceedings of the 2013 International Conference on Principles and Practices of Programming on the Java Platform: Virtual Machines, Languages, and Tools (PPPJ '13)

### 2012

- Thomas Würthinger, Andreas Wöß, Lukas Stadler, Gilles Duboscq, Doug Simon, Christian Wimmer  
[**Self-optimizing AST interpreters**](http://dl.acm.org/citation.cfm?doid=2384577.2384587)  
Proceedings of the 8th symposium on Dynamic languages (DLS '12)

- Christian Wimmer, Thomas Würthinger  
[**Truffle: a self-optimizing runtime system**](http://dx.doi.org/10.1145/2384716.2384723)  
In Proceedings of the 3rd annual conference on Systems, programming, and applications: software for humanity (SPLASH '12)

- Christian Häubl, Christian Wimmer, Hanspeter Mössenböck  
[**Evaluation of trace inlining heuristics for Java**](http://dx.doi.org/10.1145/2245276.2232084)  
In Proceedings of the 27th Annual ACM Symposium on Applied Computing (SAC '12)
