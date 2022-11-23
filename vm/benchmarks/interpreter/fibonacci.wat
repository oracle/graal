(module
  (type (;0;) (func (param i32)))
  (type (;1;) (func))
  (type (;2;) (func (param i32) (result i32)))
  (type (;3;) (func (result i32)))
  (import "wasi_snapshot_preview1" "proc_exit" (func $__imported_wasi_snapshot_preview1_proc_exit (type 0)))
  (func $_start (type 1)
    (local i32)
    block  ;; label = @1
      call $__original_main
      local.tee 0
      i32.eqz
      br_if 0 (;@1;)
      local.get 0
      call $exit
      unreachable
    end)
  (func $fibonacci (type 2) (param i32) (result i32)
    (local i32)
    i32.const 1
    local.set 1
    block  ;; label = @1
      local.get 0
      i32.const 1
      i32.ge_s
      br_if 0 (;@1;)
      i32.const 0
      return
    end
    block  ;; label = @1
      local.get 0
      i32.const 3
      i32.lt_u
      br_if 0 (;@1;)
      i32.const 0
      local.set 1
      loop  ;; label = @2
        local.get 0
        i32.const -1
        i32.add
        call $fibonacci
        local.get 1
        i32.add
        local.set 1
        local.get 0
        i32.const -2
        i32.add
        local.tee 0
        i32.const 2
        i32.gt_u
        br_if 0 (;@2;)
      end
      local.get 1
      i32.const 1
      i32.add
      local.set 1
    end
    local.get 1)
  (func $run (type 3) (result i32)
    i32.const 31
    call $fibonacci
    i32.const 1346269
    i32.ne)
  (func $__original_main (type 3) (result i32)
    i32.const 31
    call $fibonacci
    i32.const 1346269
    i32.ne)
  (func $__wasi_proc_exit (type 0) (param i32)
    local.get 0
    call $__imported_wasi_snapshot_preview1_proc_exit
    unreachable)
  (func $_Exit (type 0) (param i32)
    local.get 0
    call $__wasi_proc_exit
    unreachable)
  (func $dummy (type 1))
  (func $__wasm_call_dtors (type 1)
    call $dummy
    call $dummy)
  (func $exit (type 0) (param i32)
    call $dummy
    call $dummy
    local.get 0
    call $_Exit
    unreachable)
  (func $_start.command_export (type 1)
    call $_start
    call $__wasm_call_dtors)
  (func $run.command_export (type 3) (result i32)
    call $run
    call $__wasm_call_dtors)
  (table (;0;) 1 1 funcref)
  (memory (;0;) 2)
  (global $__stack_pointer (mut i32) (i32.const 66560))
  (export "memory" (memory 0))
  (export "_start" (func $_start.command_export))
  (export "run" (func $run.command_export)))
