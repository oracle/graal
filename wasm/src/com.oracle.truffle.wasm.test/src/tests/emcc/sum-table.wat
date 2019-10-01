(module
  (type (;0;) (func (result i32)))
  (type (;1;) (func (param i32) (result i32)))
  (table 2 2 funcref)
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
    i32.const 100000
    i32.const 0
    call_indirect (type 1)
    drop
    i32.const 1000
    i32.const 1
    call_indirect (type 1))
  (global (;0;) (mut i32) (i32.const 2768))
  (elem (i32.const 0) 0 0)
  (export "_main" (func 1)))
