# T-Trace: The Ultimate Tracing and Insights Gathering Platform

**T-Trace** is multipurpose, flexible tool that greatly reduces 
the effort of writing reliable microservices solutions. The dynamic nature
of **T-Trace** helps everyone to selectively apply complex tracing
pointcuts on already deployed applications running at full speed. 
**T-Trace** further blurs the difference between various DevOps tasks -
code once, apply your insights anytime, anywhere!

## Tracing in any Language and any Framework

The traditional tracing solution requires every developer to modify their own code 
with manually added traces. **T-Trace** brings such experience to another 
level by using the powerful instrumentation capabilities built 
into any GraalVM language (JavaScript, Python, Ruby, R)
and letting you dynamically apply the tracing when needed, without altering the
original code of the application at all. All GraalVM languages and technologies 
are designed with support for tracing in mind. 
Apply the **T-Trace** insights to scripts running in *node.js* or
*Ruby on Rails* or your *Python* big data computation pipeline. All of that
is possible and ready to be explored.

Every user can easily create own
**T-Trace** insights in a language of one's choice. The insights are well
crafted code that, when enabled, gets automatically spread around the codebase 
of one's application and is applied at critical tracing pointcuts.
The code is smoothly blended into bussiness code of the application 
enriching the core functionality with additional cross cutting concerns
(for example security).

## Excellent for Research

While common GraalVM **T-Trace** sample scripts primarily targeted
ease of use in the microservices area, the functionality of **T-Trace**
pointcuts isn't limited to such area at all!

**T-Trace** is an ideal tool for practicing *aspects oriented programming*
in a completely language agnostic way. **T-Trace** insights allow detailed
access to runtime behavior of a program at all possible pointcuts allowing one to
inspect values, types at invocation or allocation sites, gathering useful information
and collecting and presenting it in unrestricted ways. The **T-Trace** insights
allow one to modify computed values, interrupt execution and 
quickly experiment with behavioral changes without modifying the
application code.

The applicability of **T-Trace** isn't limited only to scripting
languages. Any language written using Truffle API can be a target of
**T-Trace** insights including static languages handled by Sulong
(e.g. C, C++, Rust, Fortran, etc.). Enrich your static code behavior 
by attaching your insights written in dynamic languages.

**T-Trace** framework brings powerful cross-language yet language agnostic
metaprogramming features into hands of every researcher and practitioner.

## Running at Full Speed

GraalVM languages are well known for running with excellent performance and **T-Trace** 
makes no compromises to that! Your applications are inherently ready for
tracing without giving up any speed. Launch your application 
as you are used to. Let it run at full speed. When needed, connect to its GraalVM
and enable requested **T-Trace** insights. Their code gets automatically
blended into the code of your application, making them a natural part
of surrounding code. There is no loss of performance compared to code that
would be manually tweaked to contain the insights at appropriate places, but
such modification doesn't have to be done in advance - it can be fully applied
only when needed.

The flexibility and the power of standard as well as hand written
**T-Trace** insights makes them an excellent choice for vendors of cloud
based offerings. There is no other system that could compete with the 
multi-language offerings of GraalVM. The ability to create custom **T-Trace** 
based insights in any language brings the combined offering to yet another level.
GraalVM with **T-Trace** is the dream come true for anyone seeking security,
[embeddablity](T-Trace-Embedding.md), configurability, robustness and performance at the cloud scale.

## Hacker's Handle to the Ultimate Tracing Framework

Any moderately skilled hacker can easily create own 
so called **T-Trace** snippets and dynamically apply them to 
the actual programs. That provides ultimate insights into
execution and behavior of one's application without compromising the speed
of the execution.

Please consult the [the Hacker's manual](T-Trace-Manual.md) to
get started with an obligatory Hello World example and other more and more
challenging tasks.

## Embeddeding **T-Trace** into own Applications

[GraalVM](http://graalvm.org) languages can be embedded into custom applications via polyglot
[Context](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html) API.
**T-Trace** isn't an exception - it can also be
controlled via the same API as well.

Read [embedding documentation](T-Trace-Embedding.md) to learn how to integrate
**T-Trace** capabilities into own applications in a secure way.

## Tracing with **T-Trace**

**T-Trace** is an excellent tool for dynamically adding tracing capabilities
into existing code. Write your application as normally and apply
[Open Telemetry](https://opentelemetry.io/) traces dynamically when needed.

Read more about [T-Trace & Jaeger integration](T-Trace-Tracing.md) in a
dedicated [document](T-Trace-Tracing.md).

