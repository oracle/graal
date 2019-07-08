(module
    (func (export "main") (result f32)
        block $B0 (result f32)
            f32.const 3.99
            block $B1 (result f32)
                f32.const 0.1
                f32.const 0.9
                f32.add
            end
            f32.sub
        end
    )
)