(module
    (func (export "main") (result i32)
        block $B0 (result i32)
            block $B1 (result i32)
                i32.const 12
                br $B1
                i32.const 24
                i32.add
            end
            block $B2 (result i32)
                i32.const 2
                br $B2
                i32.const 1
                i32.add
            end
            i32.sub
        end
    )
)