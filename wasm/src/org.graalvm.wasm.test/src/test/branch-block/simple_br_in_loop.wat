(module
    (func (export "_main") (result i32)
        (local i32)
        i32.const 0
        local.set 0
        loop $B0 (result i32)
            local.get 0
            i32.const 1
            i32.add
            local.tee 0
            i32.const 4
            i32.ne
            if
                br $B0
            end
            i32.const 14
        end
    )
)