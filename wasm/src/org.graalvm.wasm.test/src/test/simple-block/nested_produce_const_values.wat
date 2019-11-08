(module
    (func (export "_main") (result i32)
        block $B0 (result i32)
            i32.const 42
            block $B1 (result i32)
                i32.const 12
            end
            i32.add
        end
    )
)