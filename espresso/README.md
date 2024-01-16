# Java On Truffle :coffee:

Espresso is a full JVM capable of dynamic bytecode loading, written in Java using the Truffle framework. It can provide competitive performance when run on [GraalVM](https://github.com/oracle/graal).

It has some unique features:

- Run Java code inside a sandboxed, isolated context using [GraalVM's sandboxing features](https://www.graalvm.org/latest/security-guide/polyglot-sandbox/).
- More comprehensive hotswap support than HotSpot, enabling you to easily patch code whilst the app is running by just recompiling your project in your IDE whilst attached with the debugger. Your app can react to HotSwap events in order to fix up internal state when needed.
- Can be used to dynamically load and run Java from inside an ahead of time compiled _native image_.
- Can be used to isolate and call code that requires an older Java version on a newer host JVM. 

Why implement a JVM in Java?

- It's easy to understand and work on. Adding features is easy. See [How Espresso Works](docs/how-espresso-works.md).
- Full self-hosting (meta-circularity) is a common goal for programming languages, as it proves they are fully general enough to implement themselves.
- It highlights the sublime potential of the GraalVM as a platform for implementing high-performance languages and runtimes.

## Status

Espresso is a complete Java implementation which is kept up to date with the latest LTS JDK versions. When the VM is compiled to a native image it runs on Windows, macOS and Linux.

It features complete meta-circularity: it can run itself any amount of layers deep, preserving all the capabilities (Unsafe, JNI, Reflection...) of the base layer. Running HelloWorld on three nested layers of Espresso takes **~15 minutes**.  

The development of Espresso happens mostly on HotSpot, but this configuration (Espresso on HotSpot) is only supported on Linux, see [Limitations](docs/hacking.md#limitations).

## Working on Espresso

See the [hacking notes](docs/hacking.md).
