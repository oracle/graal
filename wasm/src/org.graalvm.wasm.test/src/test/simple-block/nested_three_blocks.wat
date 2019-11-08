(module
    (func (export "_main") (result i32)
        block $B0 (result i32)
            i32.const 42
            block $B1 (result i32)
                i32.const 12
                i32.const 24
                i32.add
                block $B2 (result i32)
                    i32.const 36
                end
                i32.sub
            end
            i32.sub
        end
    )
)