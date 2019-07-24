(module
  (type (;0;) (func (result i32)))
  (type (;1;) (func (param i32) (result i32)))
  (func (;0;) (type 1) (param i32) (result i32)
    local.get 0
    i32.const 2
    i32.lt_s
    if (result i32)  ;; label = @1
      local.get 0
    else
      local.get 0
      i32.const -1
      i32.add
      call 0
      local.get 0
      i32.const -2
      i32.add
      call 0
      i32.add
    end)
  (func (;1;) (type 0) (result i32)
    i32.const 34
    call 0)
  (func (;2;) (type 1) (param i32) (result i32)
    (local i32 i32)
    global.get 0
    local.set 2
    local.get 0
    global.get 0
    i32.add
    global.set 0
    global.get 0
    i32.const 15
    i32.add
    i32.const -16
    i32.and
    global.set 0
    local.get 2)
  (global (;0;) (mut i32) (i32.const 2768))
  (export "_main" (func 1))
  (export "stackAlloc" (func 2)))
