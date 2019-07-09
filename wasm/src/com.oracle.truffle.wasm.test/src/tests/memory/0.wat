(module
    (memory 1 1)
    (func (export "main") (result i32)
        i32.const 0
        i32.const 155
        i32.store
        i32.const 0
        i32.load
    )
)