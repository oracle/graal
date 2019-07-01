(module
    (func (result i32)
        block $B0 (result i32)
            i32.const 42
            block $B1 (result i32)
                i32.const 12
                br $B1
                i32.const 24
                i32.add
            end
            i32.sub
        end
    )
)