# GraalVM Insight: The Ultimate Insights Gathering Platform

GraalVM Insight (hereafter "Insight") is a multipurpose, flexible tool that can reduce the effort of writing reliable microservices solutions.
The dynamic nature of the tool helps to selectively apply complex insights by gathering hooks on already deployed applications running at full speed.

Find out more about Insight from its [formal API specification](https://www.graalvm.org/tools/javadoc/org/graalvm/tools/insight/Insight.html) and the [Insight Manual](../../docs/tools/insight/Insight-Manual.md).

## Motivation Behind Insight

The traditional tracing solution requires every developer to modify their own code with manually added traces.
Insight brings such experience to another level.
The powerful instrumentation capabilities built into GraalVM languages (languages implemented with the Truffle framework, i.e., JavaScript, Python, Ruby, R) let you dynamically apply the tracing when needed, without altering the original code of the application.
All GraalVM languages and technologies are designed with support for tracing in mind.
Apply the insights to scripts running in Node.js, or Ruby on Rails, or your Python big data computation pipeline.

Every user can easily create own insights in a language of one's choice.
The insights are well crafted code that, when enabled, gets automatically spread around the codebase of one's application and is applied at critical tracing pointcuts.
The code is smoothly blended into the application code enriching the core functionality with additional cross cutting concerns (for example security).

## Excellent for Research

While common GraalVM Insight sample scripts primarily targeted ease of use in the microservices area, the functionality of Insight pointcuts is not limited to such area at all.

Insight is an ideal tool for practicing *aspects oriented programming* in a completely language agnostic way.
Insight hooks allow detailed access to run-time behavior of a program, enabling one to inspect values, types at invocation or allocation sites, gathering useful information, collecting and presenting it in unrestricted ways.
The insights allow one to modify computed values, interrupt execution and quickly experiment with behavioral changes without modifying the application code.

The applicability of GraalVM Insight is not limited only to scripting languages.
Any language written using Truffle API can be a target of Insight hooks including static native languages like C, C++, Rust, etc.
Enrich the static code by attaching insights written in dynamic languages.

GraalVM Insight brings powerful cross-language and language agnostic metaprogramming features into hands of every researcher and practitioner.

## Running at Full Speed

GraalVM languages (JavaScript, Python, Ruby, R) are well known for running with high performance and Insight makes no compromises to that.
Your applications are inherently ready for tracing without giving up any speed.
Launch your application as you are used to.
When needed, connect to GraalVM and enable requested insights.
The insights get automatically blended into the code of your application, making them a natural part of surrounding code.
There is no loss of performance compared to code that would be manually tweaked to contain the insights at appropriate places, but such modification does not have to be done in advance - it can be fully applied only when needed.

The flexibility and the power of standard as well as hand written insights makes them an excellent choice for vendors of cloud based offerings.
There is no other system that could compete with the multi-language offerings of GraalVM.
The ability to create custom Insight hooks in any language brings the combined offering to yet another level.
GraalVM Insight is the dream come true for anyone seeking security, [embeddablity](../../docs/tools/insight/Insight-Embedding.md), configurability, robustness and performance at the cloud scale.

## "Hacker's Manual" to the Ultimate Tracing Framework

Any moderately skilled developer can easily create own so called "hooks" and dynamically apply them to the actual programs.
That provides ultimate insights into execution and behavior of one's application without compromising the execution speed.

Consult the [Insight Manual](../../docs/tools/insight/Insight-Manual.md) to get started with an obligatory HelloWorld example and other challenging tasks.

## Embeddeding Insight into Applications

GraalVM languages (languages implemented with the Truffle framework) can be embedded into custom applications via [Polyglot Context API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html). GraalVM Insight can also be controlled via the same API.

Read the [embedding documentation](../../docs/tools/insight/Insight-Embedding.md) to learn how to integrate GraalVM Insight capabilities into applications in a secure way.

## Tracing with Insight

GraalVM Insight dynamically adds tracing capabilities into existing code. Write your application as normally and apply [Open Telemetry](https://opentelemetry.io/) traces dynamically when needed.
Read more about [Insight and Jaeger integration](../../docs/tools/insight/Insight-Tracing.md) in a dedicated [guide](../../docs/tools/insight/Insight-Tracing.md).
