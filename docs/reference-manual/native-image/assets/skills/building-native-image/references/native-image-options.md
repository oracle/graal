# GraalVM Native Image CLI Options

## Classpath and modules

**If native-image can't find your classes:**
```bash
native-image -cp <path1>:<path2> <class>
```

**If using modules:**
```bash
native-image -p <module-path> --add-modules <module-name> <class>
```

## Build failures

**If a class fails because it initializes at build time but must not:**
```bash
native-image --initialize-at-run-time=com.example.LazyClass <class>
```

**If a class must be initialized at build time:**
```bash
native-image --initialize-at-build-time=com.example.EagerClass <class>
```

**If a type must be fully defined at build time:**
```bash
native-image --link-at-build-time <class>
```

**If the build runs out of memory:**
```bash
native-image -J-Xmx8g <class>
```

**If you need to set a system property at build time:**
```bash
native-image -Dkey=value <class>
```

**If you need to pass a flag to the JVM running the builder:**
```bash
native-image -J<flag> <class>
```

## Missing reachability metadata

**If you want exact error reporting for missing reflection/JNI/proxy/resource/serialization registrations (GraalVM JDK 23+):**
```bash
native-image --exact-reachability-metadata <class>
```

**If you want to scope exact metadata handling to specific classpath entries:**
```bash
native-image --exact-reachability-metadata-path=<path> <class>
```

## Output and binary type

**If you want to rename the output binary:**
```bash
native-image -o myapp <class>
```

**If you want to build a shared library:**
```bash
native-image --shared <class>
```

**If you want a fully statically linked binary:**
```bash
native-image --static --libc=musl <class>
```

**If you want static linking but keep libc dynamic:**
```bash
native-image --static-nolibc <class>
```

## Performance and optimization

**If you want fastest build time (dev iteration):**
```bash
native-image -Ob <class>
```

**If you want best runtime performance:**
```bash
native-image -O3 <class>   # or combine with --pgo
```

**If you want to optimize for binary size:**
```bash
native-image -Os <class>
```

**If you want to collect PGO profile data:**
```bash
native-image --pgo-instrument <class>
./myapp                          # run to collect profile
native-image --pgo=default.iprof <class>
```

**If you want to change the garbage collector:**
```bash
native-image --gc=G1 <class>       # G1 (GraalVM EE only)
native-image --gc=epsilon <class>  # no GC (throughput)
native-image --gc=serial <class>   # default
```

**If you want to target the current machine's CPU features:**
```bash
native-image -march=native <class>
```

**If you need maximum compatibility across machines:**
```bash
native-image -march=compatibility <class>
```

**If you want to limit build parallelism:**
```bash
native-image --parallelism=4 <class>
```

## Debugging and diagnostics

**If you want debug symbols in the binary:**
```bash
native-image -g <class>
```

**If you want verbose build output:**
```bash
native-image --verbose <class>
```

**If you want to inspect class initialization and substitutions:**
```bash
native-image --diagnostics-mode <class>
```

**If you want a detailed HTML build report:**
```bash
native-image --emit build-report <class>
# or: --emit build-report=report.html
```

**If you want to trace instantiation of a specific class:**
```bash
native-image --trace-object-instantiation=com.example.MyClass <class>
```

## Network support

**If the binary needs HTTP/HTTPS:**
```bash
native-image --enable-http --enable-https <class>
```

**If the binary needs specific URL protocols:**
```bash
native-image --enable-url-protocols=http,https <class>
```

## Monitoring and observability

**If you need runtime monitoring (heap dumps, JFR, thread dumps):**
```bash
native-image --enable-monitoring=heapdump,jfr,threaddump <class>
```

## Security and compliance

**If you need all security services (e.g. TLS/SSL):**
```bash
native-image --enable-all-security-services <class>
```

**If you need a Software Bill of Materials (SBOM):**
```bash
native-image --enable-sbom=embed,export <class>
```

## Cross-compilation and platform

**If you need to cross-compile for a different OS/arch:**
```bash
native-image --target=linux-aarch64 <class>
```

**If you need a custom C compiler:**
```bash
native-image --native-compiler-path=/usr/bin/gcc <class>
```

## Info and discovery

**If you want to list available CPU features:**
```bash
native-image --list-cpu-features
```

**If you want to list observable modules:**
```bash
native-image --list-modules
```

**If you want to see the native toolchain and build settings:**
```bash
native-image --native-image-info
```
