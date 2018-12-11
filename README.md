# :coffee: Espresso
A Java bytecode interpreter on top of [Truffle](https://github.com/oracle/graal/tree/master/truffle).

**Note:** Espresso is a work in progress.

### Features
  - Bytecode interpreter passing _100%_ of the JTT bytecode tests
  - Working* PE
  - Uses guest class loaders
  - `.class` file parser
  - JNI support
  - Standalone native-image via SVM

### Not supported/implemented (yet)
  - Threads
  - InvokeDynamic
  - Modules
  - Interop
  - Class/bytecode verification/access-checks/error-reporting
  - Full Java spec compliance (e.g. class loading and initialization, access checks)
  
Despite these limiations, Espresso can already run `javac` and compile itself.
It can run a toy web server, Tetris, a ray-tracer written in Scala and much more.

### _Espresso_ setup
Espresso needs some tweaks to run; checkout the `mokapot` branch on the graal repo:
```bash
cd ../graal
git checkout mokapot
```
Always use the `master` branch on Espresso, `mokapot` on graal (for Espresso ad-hoc patches). 

### Run _Espresso_
Use `mx espresso` which mimics the `java` (8) command.
```bash
mx espresso -help
mx espresso -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.HelloWorld
# or just
mx espresso-playground HelloWorld
```

### Run _Espresso_ + Truffle PE
```bash
# Runs a simple prime-sieve
mx --dy /compiler --jdk jvmci espresso-playground TestMain
```

### Terminal tetris
`mx espresso-playground` is a handy shortcut to run test programs bundled with Espresso.
```bash
mx espresso-playground Tetris
```

### Fast(er?) factorial
```bash
mx espresso-playground Fastorial 100
```

### Dumping IGV graphs
```bash
mx -v --dy /compiler --jdk jvmci -J"-Dgraal.Dump=:4 -Dgraal.TraceTruffleCompilation=true -Dgraal.TruffleBackgroundCompilation=false" espresso -cp  mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.TestMain
```

### Run _Espresso_ unit tests
Most unit tests are executed in the same context.
```bash
mx unittest --suite espresso
```

## _Espresso_ + SVM
```bash
# Build Espresso native image
mx --env espresso.svm build
export ESPRESSO_NATIVE=`mx --env espresso.svm graalvm-home`/bin/espresso
time $ESPRESSO_NATIVE -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.HelloWorld
```
The Espresso native image assumes it's part of the GraalVM to derive the boot classpath and other essential parameters needed to boot the VM.
