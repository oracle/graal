(module
    (func (export "_main") (result i32) (local $l0 i32) (local $l1 i32)
        i32.const 0
        local.set $l0
        i32.const 10
        local.set $l1
        loop $B0
            local.get $l0
            i32.const 1
            i32.add
            local.set $l0
            local.get $l1
            i32.const 1
            i32.sub
            local.tee $l1
            br_if $B0
        end
        i32.const 10
        local.set $l1
        loop $B1
            local.get $l0
            i32.const 1
            i32.sub
            local.set $l0
            local.get $l1
            i32.const 1
            i32.sub
            local.tee $l1
            br_if $B1
        end
        local.get $l0
    )
)
