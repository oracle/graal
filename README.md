# :coffee: Espresso
A Java bytecode interpreter on top of [Truffle](https://github.com/oracle/graal/tree/master/truffle).

**Note:** Work in progress, early PoC.

### Features
  - Bytecode interpreter passing _100%_ of the JTT bytecode tests
  - OotB partial evaluation yields _20X_ speedup for a simple prime sieve
  - Uses guest class loaders
  - `.class` file parser

### Not supported/implemented (yet)
  - Threads
  - InvokeDynamic
  - Modules
  - Interop
  - Class/bytecode verification/access-checks/error-reporting
  - Full Java spec compliance (e.g. class loading and initialization, access checks)  

### Run _Espresso_ as a pure interpreter _~60s_
```bash
mx espresso -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.TestMain
```

### Java equivalent _~1s_
```bash
java -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.TestMain
```

### Run _Espresso_ + Truffle PE _~3s_
```bash
mx --dy /compiler --jdk jvmci espresso -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.TestMain
```

### Terminal tetris
```bash
mx espresso-playground Tetris
```

### Fast(er) factorial
mx espresso-playground Fastorial 100

## Dumping IGV graphs
```bash
mx -v --dy /compiler --jdk jvmci -J"-Dgraal.Dump=:4 -Dgraal.TraceTruffleCompilation=true -Dgraal.TruffleBackgroundCompilation=false" espresso -cp  mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.TestMain
```

### Run _Espresso_ tests _~100s_
Spawn a fresh Context per test class
```bash
mx unittest --suite espresso
```

### Run _Espresso_ tests _~10s_
Run all tests within a single context (faster) 
```bash
mx unittest --suite espresso -Despresso.test.SingletonContext=true
```
