---
layout: docs
toc_group: truffle
link_title: Implementing a New Language with Truffle
permalink: /graalvm-as-a-platform/language-implementation-framework/LanguageTutorial/
---
# Implementing a New Language with Truffle

The Truffle framework enables you to implement a programming language and run it efficiently on GraalVM.
We provide extensive [Truffle API documentation](http://graalvm.org/truffle/javadoc/).

A good way to start implementing your language is to:
* Look at the [TruffleLanguage](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html) class, and subclass it for your own language implementation. 
* Fork the [SimpleLanguage](https://github.com/graalvm/simplelanguage) project and start hacking. SimpleLanguage is a relatively small language implementation, well-documented, and designed to demonstrate most of the Truffle features.
* Examine the [GraalVM Polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html) that enables you to embed your Truffle language in Java.

We also recommend to watch this online seminar on [Dynamic Metacompilation with Truffle](https://www.youtube.com/watch?v=pksRrON5XfU) by Christian Humer from Oracle, to better understand Truffle concepts such as dynamic metacompilation, partial evaluation, polymorphic inlining, and so on.