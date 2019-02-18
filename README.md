# :coffee: Espresso²
A Java bytecode meta-interpreter on top of [Truffle](https://github.com/oracle/graal/tree/master/truffle).

Espresso can run way past HelloWorld; it can run a toy web server, terminal Tetris, a ray-tracer written in Scala and much more.  
**Espresso is written entirely in Java, it can run `javac` and compile itself.**  
**It can interpret itself, on top of itself... any number of layers deep.**


### Features
  - Meta-interpreter, fully self-hosted, able to compile itself and run on top of itself
  - PE-friendly bytecode interpreter
  - Single-pass `.class` file parser
  - JNI-in-Java implementation (libnespresso)
  - libjvm-in-Java implementation (libmokapot)
  - Standalone native-image via SubstrateVM

### Not supported (yet)
  - Threads
  - InvokeDynamic
  - Modules
  - Interop
  - Class/bytecode verification/access-checks/error-reporting
  - Full Java spec compliance (e.g. class loading and initialization, access checks)

## _Espresso_ setup
Espresso needs some patches to run; checkout the `mokapot` branch on the graal repo (internal branch not available on GitHub):
```bash
cd ../graal
git checkout mokapot
```
Always use `master` for Espresso and the `mokapot` branch on the graal repo. 

## _Espresso_ interpreter
Use `mx espresso` which mimics the `java` (8) command.
```bash
mx espresso -help
mx espresso -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.HelloWorld
# or just
mx espresso-playground HelloWorld
```

## _Espresso_ + compilation enabled
```bash
# Executes a prime-sieve
mx --dy /compiler --jdk jvmci espresso-playground TestMain
mx --dy /compiler --jdk jvmci espresso -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.Tetris
```

## Terminal tetris
`mx espresso-playground` is a handy shortcut to run test programs bundled with Espresso.
```bash
mx espresso-playground Tetris
```

## Dumping IGV graphs
```bash
mx -v --dy /compiler --jdk jvmci -J"-Dgraal.Dump=:4 -Dgraal.TraceTruffleCompilation=true -Dgraal.TruffleBackgroundCompilation=false" espresso -cp  mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.TestMain
```

## _Espresso_ unit tests
Most unit tests are executed in the same context.
```bash
mx unittest --suite espresso
```

## _Espresso_ + SubstrateVM
```bash
# Build Espresso native image
mx --env espresso.svm build
export ESPRESSO_NATIVE=`mx --env espresso.svm graalvm-home`/bin/espresso
# Run HelloWorld
time $ESPRESSO_NATIVE -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.HelloWorld
```
The Espresso native image assumes it's part of the GraalVM to derive the boot classpath and other essential parameters needed to boot the VM.

## Espressoⁿ Java-ception
**Self-hosting requires a Linux distro with an up-to-date libc. Works on HotSpot (no SVM support yet)**  
Use `mx espresso-meta` to run programs on Espresso². Be sure to preprend `LD_DEBUG=unused` to overcome a known libc bug.  
To run Tetris on Espresso² execute the following:
```bash
LD_DEBUG=unused mx --dy /compiler --jdk jvmci espresso-meta -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.Tetris
```
It will take quite some time, only the bottom layer gets compilation. Once Tetris starts it will be very laggy, it will take ~10 pieces (for compilation to catch-up) to be relatively fluid. Enjoy!
