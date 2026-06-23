(;
  Wasm text file to verify the wasm-tools assembler works correctly.

  Uses WasmGC features (struct types, ref types).
;)
(module
  (type $point (struct (field $x i32) (field $y i32)))
  (func $main (export "main") (result i32)
    (struct.get $point $x
      (struct.new $point (i32.const 42) (i32.const 7)))
  )
)
