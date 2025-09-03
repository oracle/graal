(;
  Wasm text file to verify the wasm-as (binaryen) wasm assembler works correctly.

  Uses features from the GC and exception handling proposal
;)
(module
  (rec
    (; Base type ;)
    (type $A (sub (struct)))

    (type $B (sub $A (struct
      (field $f1 (mut (ref null $A)))
      (field $f2 (mut (ref null $A)))
    )))

    (type $C (sub $A (struct
      (field $f3 (mut (ref null $A)))
      (field $f4 (mut i32))
    )))
  )

  (tag $tag0 (param (ref $A)))

  (start $main)
  (func $main
    (try
      (do (call $throwB))
      (catch $tag0
        (drop (call $checkException))
      )
    )

    (try
      (do (call $throwC))
      (catch $tag0
        (drop (call $checkException))
      )
    )
  )

  (func $checkException (param $p0 (ref $A)) (result (ref null $A))
    (block $b1 (result (ref $B))
      (block $b2 (result (ref $A))
        (br_on_cast $b1 (ref $A) (ref $B) (local.get $p0))
      )
      (return
        (struct.get $C $f3 (ref.cast (ref $C)))
      )
    )
    (return
      (struct.get $B $f2)
    )
  )

  (func $throwB
    (throw $tag0 (struct.new_default $B))
  )

  (func $throwC
    (throw $tag0 (struct.new_default $C))
  )
)
