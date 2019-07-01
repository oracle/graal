(module
    (func (result i32)
        block $B0 (result i32)
            i32.const 42
            block $B1 (result i32)
                i32.const 12
                block $B2 (result i32)
                    i32.const 8
                    br $B0
                end
                i32.add
            end
            i32.add
        end
    )
)