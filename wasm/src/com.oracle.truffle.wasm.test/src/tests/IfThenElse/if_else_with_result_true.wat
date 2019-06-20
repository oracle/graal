(module
    (func (result i32) (local $l0 i32)
        i32.const 1
        if $l0 (result i32)
            i32.const 42
        else
            i32.const 55
        end
    )
)