# Truffle Tools Changelog

This changelog summarizes major changes between Truffle Tools versions.

## Version 20.1.0

* [GraalVM Insight](docs/Insight.md) is the new name for the former *T*-*Trace* technology

## Version 20.0.0
* Access to source location (see `line`, `column`, etc.) and `sourceFilter` selector in [Insight agent object API](https://www.graalvm.org/tools/javadoc/com/oracle/truffle/tools/agentscript/AgentScript.html#VERSION)
* Embedding [Insight](docs/Insight-Embedding.md) into own application is now easily done via [Graal SDK](https://www.graalvm.org/tools/javadoc/com/oracle/truffle/tools/agentscript/AgentScript.html#ID)
* Apply [Insight scripts](docs/Insight-Manual.md) C/C++/Julia & co. code - e.g. *hack into C with JavaScript*!
* Better error handling - e.g. propagation of errors from [Insight scripts](docs/Insight-Manual.md) to application code
* Hack your [Insight scripts in R](docs/Insight-Manual.md)!

## Version 19.3.0
* Introducing [GraalVM Insight](docs/Insight.md) - a  multipurpose, flexible tool for instrumenting and monitoring applications at full speed.
* Added a CLI code coverage tool for truffle languages. Enabled with `--coverage`. See `--help:tools` for more details.
