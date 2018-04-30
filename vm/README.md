# VM suite

The vm suite is used to build the GraalVM in a modular way.
It can create the GraalVM distribution as well as installable components.

This suite defines a bare-bones GraalVM that contains the JVMCI-enabled JDK, the Graal SDK and Truffle.
More components are added by importing extra suites.
This can be done using `mx`'s `--dynamicimports`/`--dy` flag.

## Base GraalVM
For example building the typical GraalVM base one can do:
```
$ mx --dy /substratevm,/tools,sulong,/graal-nodejs build
```

This will include:
- SubstrateVM (with the `native-image` tool)
- Graal compiler & the Truffle-Graal accelerator (imported as a dependency of `substratevm`)
- The inspector and profiler tools
- Sulong
- Graal.nodejs
- Graal.js (imported as a dependency of `graal-nodejs`)
- libpolyglot

## Installable components
Installable components can be installed in a "base" GraalVM with the `gu` command.
They get created alongside the GraalVM for languages other than JS.
For example, it is also possible to run:
```
$ mx --dy fastr,truffleruby,graalpython,/substratevm build
```

To create installable components for FastR, TruffleRuby and Graal.python.

## Native images
Note that if `substratevm` is included, native launchers will be created otherwise, bash launchers will be used.
If `substratevm` is included, the `polyglot` launcher will also be built as well as `libpolyglot`.

# Providing components

Suites can provide components using `mx_sdk.register_graalvm_component`.