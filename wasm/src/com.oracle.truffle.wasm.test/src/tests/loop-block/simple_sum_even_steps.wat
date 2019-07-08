(module
    (func (export "main") (result i32) (local $l0 i32) (local $l1 i32)
        i32.const 0
        local.set $l0
        i32.const 10
        local.set $l1
        loop $B0
            local.get $l1
            i32.const 2
            i32.rem_s
            if
                local.get $l0
                i32.const 1
                i32.add
                local.set $l0
            end
            local.get $l1
            i32.const 1
            i32.sub
            local.tee $l1
            br_if $B0
        end
        local.get $l0
    )
)
