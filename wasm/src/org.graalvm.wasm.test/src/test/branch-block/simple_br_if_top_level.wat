(module
    (func (export "_main") (result i32)
        block $B0 (result i32)
            i32.const 42
            i32.const 1
            br_if $B0
            i32.const 12
            i32.sub
        end
    )
)