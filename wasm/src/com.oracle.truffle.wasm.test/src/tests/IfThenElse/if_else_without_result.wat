(module
    (func (result i64) (local $l0 i32)
        i32.const 1
        if $l0
            i32.const 42
            drop
        else
            i32.const 55
            drop
        end
        i64.const 100
    )
)