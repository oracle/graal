(module
    (func (export "main") (result i32)
        block $B0 (result i32)
            i32.const 42
            block $B1 (result i32)
                i32.const 12
                i32.const 24
                block $B2
                    i32.const 0
                    i32.const 1
                    i32.const 2
                    drop
                    drop
                    drop
                end
                i32.add
                block $B3 (result i32)
                    i32.const 36
                end
                i32.sub
            end
            i32.sub
        end
    )
)