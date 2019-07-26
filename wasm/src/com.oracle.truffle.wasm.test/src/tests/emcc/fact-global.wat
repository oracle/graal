(module
  (type (;0;) (func (result i64)))
  (type (;1;) (func (param i64) (result i64)))
  (type (;2;) (func (param i64)))
  (func (;0;) (type 2) (param i64)
    local.get 0
    i64.eqz
    if ;; label = @1
      nop
    else
      local.get 0
      global.get 1
      i64.mul
      global.set 1
      local.get 0
      i64.const -1
      i64.add
      call 0
    end)
  (func (;1;) (type 0) (result i64)
    i64.const 20
    call 0
    global.get 1)
  (func (;2;) (type 1) (param i64) (result i64)
    (local i64 i64)
    global.get 0
    local.set 2
    local.get 0
    global.get 0
    i64.add
    global.set 0
    global.get 0
    i64.const 15
    i64.add
    i64.const -16
    i64.and
    global.set 0
    local.get 2)
  (global (;0;) (mut i64) (i64.const 2768))
  (global (;1;) (mut i64) (i64.const 1))
  (export "_main" (func 1))
  (export "stackAlloc" (func 2)))
