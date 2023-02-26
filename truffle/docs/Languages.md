---
layout: docs
toc_group: truffle
link_title: Language Implementations
permalink: /graalvm-as-a-platform/language-implementation-framework/Languages/
---
# Language Implementations

This page is intended to keep track of the growing number of language implementations and experiments on top of Truffle.
The following language implementations exist already:

* [Espresso](https://github.com/oracle/graal/tree/master/espresso), a meta-circular Java bytecode interpreter. *
* [FastR](https://github.com/graalvm/fastr), an implementation of GNU R. *
* [Graal.js](https://github.com/graalvm/graaljs), an ECMAScript 2020 compliant JavaScript implementation. *
* [GraalPy](https://github.com/graalvm/graalpython), an early-stage implementation of Python. *
* [grCUDA](https://github.com/NVIDIA/grcuda), a polyglot CUDA integration.
* [SimpleLanguage](https://github.com/graalvm/simplelanguage), a toy language implementation to demonstrate Truffle features.
* [SOMns](https://github.com/smarr/SOMns), a Newspeak implementation for Concurrency Research.
* [Sulong](https://github.com/oracle/graal/tree/master/sulong), an LLVM bitcode interpreter. *
* [TRegex](https://github.com/oracle/graal/tree/master/regex), a generic regular expression engine (internal, for use by other languages only). *
* [TruffleRuby](https://github.com/graalvm/truffleruby), an implementation of Ruby. *
* [TruffleSOM](https://github.com/SOM-st/TruffleSOM), a SOM Smalltalk implementation.
* [TruffleSqueak](https://github.com/hpi-swa/trufflesqueak/), a Squeak/Smalltalk VM implementation and polyglot programming environment.
* [Yona](https://yona-lang.org/), the reference implementation of a minimalistic, strongly and dynamically-typed, parallel and non-blocking, polyglot, strict, functional programming language.
* [Enso](https://github.com/enso-org/enso), an open source, visual language for data science that lets you design, prototype and develop any application by connecting visual elements together.

\* Shipped as part of [GraalVM](https://www.oracle.com/technetwork/graalvm/downloads/index.html).

## Experiments

* [BACIL](https://github.com/jagotu/BACIL), .NET CIL interpreter.
* [bf](https://github.com/chumer/bf/), an experimental Brainfuck programming language implementation.
* [brainfuck-jvm](https://github.com/mthmulders/brainfuck-jvm), another Brainfuck language implementation.
* [Cover](https://github.com/gerard-/cover), a Safe Subset of C++.
* [DynSem](https://github.com/metaborg/dynsem), a DSL for declarative specification of dynamic semantics of languages.
* [Heap Language](https://github.com/jaroslavtulach/heapdump), a tutorial showing the embedding of Truffle languages via interoperability.
* [hextruffe](https://bitbucket.org/hexafraction/truffles), an implementation of Hex.
* [LuaTruffle](https://github.com/lucasallan/LuaTruffle), an implementation of the Lua language.
* [Mozart-Graal](https://github.com/eregon/mozart-graal), an implementation of the Oz programming language.
* [Mumbler](https://github.com/cesquivias/mumbler), an experimental Lisp programming language.
* [PorcE](https://github.com/orc-lang/orc/tree/master/PorcE), an Orc language implementation.
* [ProloGraal](https://gitlab.forge.hefr.ch/tony.licata/prolog-truffle) a Prolog language implementation supporting interoperability.
* [PureScript](https://github.com/slamdata/truffled-purescript), a small, strongly-typed programming language.
* [Reactive Ruby](https://github.com/guidosalva/ReactiveRubyTruffle), TruffleRuby meets Reactive Programming.
* [shen-truffle](https://github.com/ragnard/shen-truffle), a port of the Shen programming language.
* [TruffleMATE](https://github.com/charig/TruffleMATE), a Smalltalk with a completely reified runtime system.
* [TrufflePascal](https://github.com/Aspect26/TrufflePascal/), a Pascal interpreter.
* [ZipPy](https://github.com/securesystemslab/zippy), a Python implementation.


Submit a [pull request](https://help.github.com/articles/using-pull-requests/) to add/remove from this list.
