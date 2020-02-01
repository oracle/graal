# Truffle Tools Changelog

This changelog summarizes major changes between Truffle Tools versions.

## Version 20.0.0
* Access to source location (see `line`, `column`, etc.) and `sourceFilter` selector in [T-Trace agent object API](https://www.graalvm.org/tools/javadoc/com/oracle/truffle/tools/agentscript/AgentScript.html#VERSION)
* Embedding [T-Trace](docs/T-Trace-Embedding.md) into own application is now easily done via [Graal SDK](https://www.graalvm.org/tools/javadoc/com/oracle/truffle/tools/agentscript/AgentScript.html#ID)
* Apply [T-Trace scripts](docs/T-Trace-Manual.md) C/C++/Julia & co. code - e.g. *hack into C with JavaScript*!
* Better error handling - e.g. propagation of errors from [T-Trace scripts](docs/T-Trace-Manual.md) to application code

## Version 19.3.0
* Introducing [T-Trace](docs/T-Trace.md) - a  multipurpose, flexible tool for instrumenting and monitoring applications at full speed.
* Added a CLI code coverage tool for truffle languages. Enabled with `--coverage`. See `--help:tools` for more details.
