(module
  (type (;0;) (func (result i32)))
  (type (;1;) (func (param i32) (result i32)))
  (func (;0;) (type 1) (param i32) (result i32)
    (local i32)
    i32.const 1
    local.set 1
    loop
      global.get 0
      local.get 1
      i32.mul
      global.set 0
      local.get 1
      i32.const 1
      i32.add
      local.set 1
      local.get 0
      local.get 1
      i32.ge_s
      br_if 0
    end
    global.get 0)
  (func (;1;) (type 0) (result i32)
    i32.const 10
    call 0)
  (global (;0;) (mut i32) (i32.const 1))
  (export "_main" (func 1)))
