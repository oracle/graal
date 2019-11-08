(module
  (type (;0;) (func (result i32)))
  (type (;1;) (func (param i32) (result i32)))
  (func (;0;) (type 1) (param i32) (result i32)
    (local i32 i32)
    loop
        local.get 1
        local.get 2
        i32.add
        local.set 1
        local.get 2
        i32.const 1
        i32.add
        local.set 2
        local.get 0
        local.get 2
        i32.ne
        br_if 0
    end
    local.get 1)
  (func (;1;) (type 0) (result i32)
    i32.const 1000
    call 0)
  (global (;0;) (mut i32) (i32.const 2768))
  (export "_main" (func 1)))
