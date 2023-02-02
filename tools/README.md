# GraalVM Tools

This tools suite provides tooling support for guest languages running on
GraalVM. It depends on [Truffle](https://github.com/graalvm/graal/tree/master/truffle)
and is language-agnostic. The tools are available to be used with any language
built with Truffle and passing [TCK](https://github.com/graalvm/graal/blob/master/truffle/docs/TCK.md)
tests.

## Using Tools

Please refer to https://www.graalvm.org/tools/ to learn how to use each tool.
The tools in this suite have options that can be specified to command-line
language launchers, which can be listed via `--help:tools` (e.g.,
`$GRAALVM_HOME/bin/polyglot --help:tools`). Some tools provide additional
[APIs](http://www.graalvm.org/tools/javadoc/).

## Setup and Build

Tools can be built with the [MX build tool](https://github.com/graalvm/mx/),
which must be downloaded and put onto your `PATH`. Make sure that your
`JAVA_HOME` points to a JVMCI-enabled JDK such as
[labs-openjdk-11](https://github.com/graalvm/labs-openjdk-11/releases).

```bash
$ git clone https://github.com/graalvm/mx.git
$ export PATH=$PWD/mx:$PATH
$ git clone https://github.com/graalvm/graal.git
$ cd graal/tools
$ mx build
$ mx unittest
```

## IDE Configuration

You can generate IDE project configurations by running:

```bash
$ mx ideinit
```

This will generate Eclipse, IntelliJ, and NetBeans project configurations.

## Contributing

You can contact the Truffle developers at graal-dev@openjdk.java.net mailing
list. To contribute a change, verify it using
```bash
$ mx gate
```
and start a [pull request](https://help.github.com/articles/using-pull-requests/).

## License

The tools are is licensed under the GPL 2 with Classpath Exception.
