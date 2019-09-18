# Graal Tools

Graal Tools suite provides tooling support for guest languages running on the graal
platform. It depends on [Truffle](https://github.com/graalvm/graal/tree/master/truffle)
and is language-agnostic. The Tools are available to be used with any language
written on Truffle and passing [TCK](https://github.com/graalvm/graal/blob/master/truffle/docs/TCK.md)
tests.

## Using Tools

The tools provided in this suite have options, that can be specified to command-line
language launchers. Some tools have their [APIs](http://www.graalvm.org/tools/javadoc/).

## Setup and Build

Graal uses the [MX build tool](https://github.com/graalvm/mx/), which must be downloaded
and put onto your PATH. Also, point `JAVA_HOME` to a
[JVMCI-enabled JDK 8](https://github.com/graalvm/openjdk8-jvmci-builder/releases).

```bash
git clone https://github.com/graalvm/mx.git
export PATH=$PWD/mx:$PATH
git clone https://github.com/graalvm/graal.git
cd graal/tools
mx build
mx unittest
```

## IDE Configuration

You can generate IDE project configurations by running:

```
mx ideinit
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

The Tools are is licensed under the GPL 2 with Classpath Exception.
