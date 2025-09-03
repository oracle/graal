(;
  Wasm text file to verify the wat2wasm assembler works correctly.

  Uses features from the exception handling proposal
;)
(module
  (tag $tag0 (param i32))
  (start $main)
  (func $main
    (try
      (do (call $throw))
      (catch $tag0
        (drop)
      )
    )
  )
  (func $throw
    (throw $tag0 (i32.const 0x1234))
  )
)
