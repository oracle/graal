---
layout: docs
toc_group: truffle
link_title: Truffle AOT Compilation
permalink: /graalvm-as-a-platform/language-implementation-framework/AOT/
---
# Truffle AOT Tutorial

Many statically compiled languages, like C are designed to be compilable without prior execution.
By default, Truffle first interprets code before it is compiled.
In order to improve warmup speed of static languages AOT compilation can be supported.
The following tutorial describes how to support Truffle AOT in your language, how to trigger and test it.

## Language Support

In order for languages to support AOT compilation the language needs to implement the [RootNode.prepareForAOT()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html#prepareForAOT--) method.
The language implementation can indicate support for AOT by returning a non `null` value in this method.
The goal for implementing a root node for AOT is to prepare all the AST nodes such that they no longer deoptimize when they are compiled without prior execution.

Typical actions performed in an implementation of this method are:

* Initialize local variable types in the FrameDescriptor of the root node. If a language uses local variables and their types are known, then this information must be provided to the FrameDescriptor. This step can often be done already during parsing.
* Compute the expected execution signature of a root node and return it. This step requires the parser to infer expected types
for arguments and return values.
* Prepare specializing nodes with profiles that do not invalidate on first execution. Truffle DSL supports preparation of specializing nodes for AOT. See the example AOT language for details.

### Trigger AOT compilation

AOT compilation can be triggered and tested by using the `--engine.CompileAOTOnCreate=true` option.
This will trigger AOT compilation for every created call target with a root node that supports AOT compilation.
A root node supports AOT compilation if it returns a non null value in [RootNode.prepareForAOT()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html#prepareForAOT--).
Note that enabling this flag will also disable background compilation which makes it not suitable for production usage.

### Example Usage

Use the following documented and executable Truffle language as inspiration for AOT support:
[AOT Tutorial](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.dsl.test/src/com/oracle/truffle/api/dsl/test/examples/AOTTutorial.java)

The example is executable as mx unittest using `mx unittest AOTTutorial`.
