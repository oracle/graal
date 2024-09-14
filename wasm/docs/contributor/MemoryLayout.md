# Extracting the Internal GraalWasm Memory Layout Based on a Given WebAssembly Program

GraalWasm contains a tool for extracting the internal memory layout for a given WebAssembly application.
This is useful for detecting the causes of memory overhead.

To execute the memory layout extractor, run:

```bash
$ mx --dy /compiler wasm-memory-layout -Djol.magicFieldOffset=true -- [wasm-file]
```

This prints the memory layout tree of the given file to the console. The application provides additional options:

* --warmup-iterations: to set the number of warmup iterations.
* --entry-point: to set the entry point of the application. This is used to perform linking
* --output: to extract the memory layout into a file instead of the console.

You can also pass all other options available in GraalWasm such as `--wasm.Builtins=wasi_snapshot_preview1`.

The resulting tree represents a recursive representation of the Objects alive in GraalWasm starting from
the `WasmContext`. The output looks similar to this:

```
-context: 6598280 Byte [100%]
  -equivalenceClasses: 1320 Byte [0%]
    -table: 80 Byte [0%]
    -table[0]: 384 Byte [0%]
      -key: 72 Byte [0%]
        -paramTypes: 24 Byte [0%]
        -resultTypes: 24 Byte [0%]
      -next: 280 Byte [0%]
        -key: 64 Byte [0%]
          -paramTypes: 24 Byte [0%]
          -resultTypes: 16 Byte [0%]
        -next: 184 Byte [0%]
          -key: 56 Byte [0%]
            -paramTypes: 16 Byte [0%]
            -resultTypes: 16 Byte [0%]
          -next: 96 Byte [0%]
            -key: 64 Byte [0%]
              -paramTypes: 24 Byte [0%]
              -resultTypes: 16 Byte [0%]
    -table[2]: 208 Byte [0%]
      -key: 72 Byte [0%]
        -paramTypes: 24 Byte [0%]
        -resultTypes: 24 Byte [0%]
      -next: 104 Byte [0%]
...
```

The **names** represent the names of fields in classes. For example `equivalenceClasses` is a field in `WasmContext`.
The **values** next to the names represent the absolute amount of memory in bytes while the number in brackets represent
the relative contribution to the overall memory overhead.
**Names** with indices represent array entries such as `table[0]`.
