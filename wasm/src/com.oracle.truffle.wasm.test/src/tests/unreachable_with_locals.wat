(module
    (func (result i32) (local $l0 i32)
        i32.const 42
        local.set $l0
        local.get $l0
        unreachable
    )
)