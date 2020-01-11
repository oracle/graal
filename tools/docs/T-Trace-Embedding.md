# [T-Trace](T-Trace-Manual.md): Embedding

[T-Trace](T-Trace-Manual.md) is a multipurpose, flexible tool providing
enourmous possiblilities when it comes to dynamic understanding of user
application behavior. See its [manual](T-Trace-Manual.md) for more details.
Read on to learn how to use [T-Trace](T-Trace-Manual.md) in your own application.

### Enabling Embedded T-Trace

[GraalVM](http://graalvm.org) languages can be embedded into custom applications via polyglot
[Context](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html) API.
[T-Trace](T-Trace-Manual.md) isn't an exception and it can also be
controlled via the same API as well. See
[AgentScript class documentation](https://www.graalvm.org/tools/javadoc/com/oracle/truffle/tools/agentscript/AgentScript.html)
for more details.

### Ignoring Internal Scripts

Often one wants to treat certain code written in a dynamic language as a
priviledged one - imagine various bindings to OS concepts or other features
of one's application. Such scripts are better to remain blackboxed and hidden
from [T-Trace](T-Trace-Manual.md) instrumentation capabilities.

To hide priviledged scripts from [T-Trace](T-Trace-Manual.md) sight
[mark such scripts as internal](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Source.Builder.html#internal-boolean-)
- by default [T-Trace](T-Trace-Manual.md) ignores and doesn't process *internal* scripts.

## Where next?

Read about more T-Trace use-cases in its [hacker's manual](T-Trace-Manual.md).
