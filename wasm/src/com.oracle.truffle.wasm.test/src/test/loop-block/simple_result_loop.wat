(module
    (func (export "_main") (result i32) (local $l0 i32) (local $l1 i32)
        i32.const 0
        local.set $l0
        i32.const 10
        local.set $l1
        block $B0
            loop $B1 (result i32)
                local.get $l0
                i32.const 1
                i32.add
                local.set $l0
                local.get $l1
                i32.const 1
                i32.sub
                local.tee $l1
                if (result i32)
                  br $B1
                else
                  i32.const 47
                end
            end
            local.set $l0
        end
        local.get $l0
    )
)