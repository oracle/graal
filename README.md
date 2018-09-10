# Espresso ☕
A Java bytecode interpreter on top of Truffle.

**Note:** Work in progress, early PoC.

### Run _Espresso_ as a pure interpreter ~60s
```bash
mx espresso -cp mxbuild/dists/espresso.jar com.oracle.truffle.espresso.TestMain
```

### Java equivalent
```bash
java -cp mxbuild/dists/espresso.jar com.oracle.truffle.espresso.TestMain
```

### Run _Espresso_ + Truffle PE ~6s
```bash
mx --dy /compiler --jdk jvmci espresso -cp mxbuild/dists/espresso.jar com.oracle.truffle.espresso.TestMain
```

### Terminal Tetris
```bash
mx espresso -cp mxbuild/dists/espresso.jar com.oracle.truffle.espresso.Tetris
```

## Dumping IGV graphs
```bash
mx -v --dy /compiler --jdk jvmci -J"-Dgraal.Dump=:4 -Dgraal.TraceTruffleCompilation=true -Dgraal.TruffleBackgroundCompilation=false" espresso -cp  mxbuild/dists/espresso.jar com.oracle.truffle.espresso.TestMain
```
