(module
    (func $main (export "_main") (result i32)
        block $B0 (result i32)
            i32.const 11
        end
        i32.const 21
        i32.add
    )
    (func (result i32)
        block $B0 (result i32)
            i32.const 11
        end
        i32.const 22
        i32.add
    )
    (func $weird (result f32)
        f32.const nan:0x1
    )
    (func $entry call $main drop)
    (start $entry)
)
