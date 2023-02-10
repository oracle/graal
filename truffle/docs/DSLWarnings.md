---
layout: docs
toc_group: truffle
link_title: Truffle DSL Warnings
permalink: /graalvm-as-a-platform/language-implementation-framework/DSLWarnings/
---

# Truffle DSL Warnings 

Since version 23.0, Truffle DSL now produces significantly more warnings. 
These warnings are intended to guide the user to better DSL usage.
The following sections describe how to handle and eventually suppress warnings.

All warnings of Truffle DSL can be suppressed using the `-Atruffle.dsl.SuppressAllWarnings=true` option. 
If a language uses strict checks where warnings are treated as errors in their CI, it is recommended to add this option to the Java compilation command line. This can be useful to avoid CI failures when Truffle DSL adds new warning messages. Adding new warning messages in Truffle DSL is considered a compatible change.

Truffle DSL warnings can be suppressed just like Java warnings using the `@SuppressWarnings` annotation or with`@SuppressPackageWarnings` for entire packages.
The following warning keys are supported:

* `all` all warnings emitted by the Java compiler or Truffle DSL
* `truffle` all warnings emitted by Truffle DSL
* `truffle-sharing` warnings when the DSL recommends sharing between cached values
* `truffle-inlining` warnings when the DSL recommends using node object inlining.
* `truffle-neverdefault` warnings for when cached initializers should be marked as never having a default value.
* `truffle-limit` warnings when a specialization limit is recommended, but not specified.
* `truffle-static-method` warnings when the DSL recommends to use the `static` modifier.
* `truffle-unused` warnings if a DSL attribute or annotation has no effect and is recommended to be removed. 
* `truffle-abstract-export` warnings if an abstract message of a Truffle library is not exported.
* `truffle-assumption` if the assumptions feature is used with a specialization that reaches a `@Fallback` specialization.
* `truffle-guard` if a guard uses methods where a `@Idempotent` or `@NonIdempotent` method may be beneficial for the generated code.  

Specific warnings can also be suppressed globally using the `-Atruffle.dsl.SuppressWarnings=truffle-inlining,truffle-neverdefault` Java compiler processor option. 
Note that also Java system properties can be used to configure the annotation processor. (e.g. by passing `-J-Dtruffle.dsl.SuppressWarnings=truffle-inlining,truffle-neverdefault` to javac)

Suppressing a specific warning should be preferred over suppressing all warnings.
Find the latest list of warnings in the [source code](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.dsl.processor/src/com/oracle/truffle/dsl/processor/TruffleSuppressedWarnings.java)
