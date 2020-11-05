(module
  (type (;0;) (func))
  (type (;1;) (func (result i32)))
  (type (;2;) (func (param i32)))
  (type (;3;) (func (param i32 i32) (result i32)))
  (import "wasi_snapshot_preview1" "proc_exit" (func $__wasi_proc_exit (type 2)))
  (func $run (type 1) (result i32)
    (local i32 i32 i32 i32 i32)
    global.get 0
    i32.const 2400016
    i32.sub
    local.tee 1
    global.set 0
    i32.const 2
    local.set 2
    i32.const 2
    local.set 0
    loop  ;; label = @1
      local.get 1
      local.get 0
      i32.const 2
      i32.shl
      i32.add
      local.get 0
      i32.store
      local.get 0
      i32.const 1
      i32.add
      local.tee 0
      i32.const 600001
      i32.ne
      br_if 0 (;@1;)
    end
    loop  ;; label = @1
      block  ;; label = @2
        local.get 1
        local.get 2
        i32.const 2
        i32.shl
        i32.add
        local.tee 4
        i32.load
        local.tee 0
        i32.eqz
        br_if 0 (;@2;)
        local.get 0
        i32.const 300000
        i32.gt_s
        br_if 0 (;@2;)
        local.get 0
        i32.const 1
        i32.shl
        local.set 3
        i32.const 2
        local.set 0
        loop  ;; label = @3
          local.get 1
          local.get 3
          i32.const 2
          i32.shl
          i32.add
          i32.const 0
          i32.store
          local.get 0
          i32.const 1
          i32.add
          local.tee 0
          i32.const 600000
          i32.eq
          br_if 1 (;@2;)
          local.get 4
          i32.load
          local.get 0
          i32.mul
          local.tee 3
          i32.const 600001
          i32.lt_s
          br_if 0 (;@3;)
        end
      end
      local.get 2
      i32.const 1
      i32.add
      local.tee 2
      i32.const 775
      i32.ne
      br_if 0 (;@1;)
    end
    local.get 1
    i32.load offset=2400000
    local.set 0
    local.get 1
    i32.const 2400016
    i32.add
    global.set 0
    local.get 0)
  (func $_start (type 0)
    (local i32)
    call $__original_main
    local.tee 0
    if  ;; label = @1
      local.get 0
      call $__wasi_proc_exit
      unreachable
    end)
  (func $__original_main (type 1) (result i32)
    (local i32 i32 i32 i32 i32)
    global.get 0
    i32.const 2400016
    i32.sub
    local.tee 1
    global.set 0
    i32.const 2
    local.set 2
    i32.const 2
    local.set 0
    loop  ;; label = @1
      local.get 1
      local.get 0
      i32.const 2
      i32.shl
      i32.add
      local.get 0
      i32.store
      local.get 0
      i32.const 1
      i32.add
      local.tee 0
      i32.const 600001
      i32.ne
      br_if 0 (;@1;)
    end
    loop  ;; label = @1
      block  ;; label = @2
        local.get 1
        local.get 2
        i32.const 2
        i32.shl
        i32.add
        local.tee 4
        i32.load
        local.tee 0
        i32.eqz
        br_if 0 (;@2;)
        local.get 0
        i32.const 300000
        i32.gt_s
        br_if 0 (;@2;)
        local.get 0
        i32.const 1
        i32.shl
        local.set 3
        i32.const 2
        local.set 0
        loop  ;; label = @3
          local.get 1
          local.get 3
          i32.const 2
          i32.shl
          i32.add
          i32.const 0
          i32.store
          local.get 0
          i32.const 1
          i32.add
          local.tee 0
          i32.const 600000
          i32.eq
          br_if 1 (;@2;)
          local.get 0
          local.get 4
          i32.load
          i32.mul
          local.tee 3
          i32.const 600001
          i32.lt_s
          br_if 0 (;@3;)
        end
      end
      local.get 2
      i32.const 1
      i32.add
      local.tee 2
      i32.const 775
      i32.ne
      br_if 0 (;@1;)
    end
    local.get 1
    i32.load offset=2400000
    local.set 0
    local.get 1
    i32.const 2400016
    i32.add
    global.set 0
    local.get 0)
  (func $main (type 3) (param i32 i32) (result i32)
    (local i32 i32 i32)
    global.get 0
    i32.const 2400016
    i32.sub
    local.tee 1
    global.set 0
    i32.const 2
    local.set 2
    i32.const 2
    local.set 0
    loop  ;; label = @1
      local.get 1
      local.get 0
      i32.const 2
      i32.shl
      i32.add
      local.get 0
      i32.store
      local.get 0
      i32.const 1
      i32.add
      local.tee 0
      i32.const 600001
      i32.ne
      br_if 0 (;@1;)
    end
    loop  ;; label = @1
      block  ;; label = @2
        local.get 1
        local.get 2
        i32.const 2
        i32.shl
        i32.add
        local.tee 4
        i32.load
        local.tee 0
        i32.eqz
        br_if 0 (;@2;)
        local.get 0
        i32.const 300000
        i32.gt_s
        br_if 0 (;@2;)
        local.get 0
        i32.const 1
        i32.shl
        local.set 3
        i32.const 2
        local.set 0
        loop  ;; label = @3
          local.get 1
          local.get 3
          i32.const 2
          i32.shl
          i32.add
          i32.const 0
          i32.store
          local.get 0
          i32.const 1
          i32.add
          local.tee 0
          i32.const 600000
          i32.eq
          br_if 1 (;@2;)
          local.get 0
          local.get 4
          i32.load
          i32.mul
          local.tee 3
          i32.const 600001
          i32.lt_s
          br_if 0 (;@3;)
        end
      end
      local.get 2
      i32.const 1
      i32.add
      local.tee 2
      i32.const 775
      i32.ne
      br_if 0 (;@1;)
    end
    local.get 1
    i32.load offset=2400000
    local.set 0
    local.get 1
    i32.const 2400016
    i32.add
    global.set 0
    local.get 0)
  (func $__wasm_call_ctors (type 0)
    nop)
  (memory (;0;) 256 256)
  (global (;0;) (mut i32) (i32.const 5244432))
  (export "memory" (memory 0))
  (export "run" (func $run))
  (export "_start" (func $_start))
  (data (;0;) (i32.const 1552) "\b0\06P"))
