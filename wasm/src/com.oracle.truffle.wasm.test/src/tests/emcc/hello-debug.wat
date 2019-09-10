(module
  (type (;0;) (func (param i32 i32 i32) (result i32)))
  (type (;1;) (func (param i32) (result i32)))
  (type (;2;) (func (param i32 i64 i32) (result i64)))
  (type (;3;) (func (param i32)))
  (type (;4;) (func (param i32 i32) (result i32)))
  (type (;5;) (func))
  (type (;6;) (func (result i32)))
  (type (;7;) (func (param i32 i32)))
  (type (;8;) (func (param i32 i32 i32 i32) (result i32)))
  (type (;9;) (func (param i32 i32 i32 i32 i32) (result i32)))
  (import "env" "abort" (func $abort (type 3)))
  (import "env" "___setErrNo" (func $___setErrNo (type 3)))
  (import "env" "___syscall140" (func $___syscall140 (type 4)))
  (import "env" "___syscall146" (func $___syscall146 (type 4)))
  (import "env" "___syscall54" (func $___syscall54 (type 4)))
  (import "env" "___syscall6" (func $___syscall6 (type 4)))
  (import "env" "_abort" (func $_abort (type 5)))
  (import "env" "_emscripten_get_heap_size" (func $_emscripten_get_heap_size (type 6)))
  (import "env" "_emscripten_memcpy_big" (func $_emscripten_memcpy_big (type 0)))
  (import "env" "_emscripten_resize_heap" (func $_emscripten_resize_heap (type 1)))
  (import "env" "abortOnCannotGrowMemory" (func $abortOnCannotGrowMemory (type 1)))
  (import "env" "setTempRet0" (func $setTempRet0 (type 3)))
  (import "env" "__table_base" (global (;0;) i32))
  (import "env" "DYNAMICTOP_PTR" (global (;1;) i32))
  (import "env" "memory" (memory (;0;) 256 256))
  (import "env" "table" (table (;0;) 8 8 funcref))
  (func $stackAlloc (type 1) (param i32) (result i32)
    (local i32)
    global.get 2
    local.set 1
    local.get 0
    global.get 2
    i32.add
    global.set 2
    global.get 2
    i32.const 15
    i32.add
    i32.const -16
    i32.and
    global.set 2
    local.get 1)
  (func $stackSave (type 6) (result i32)
    global.get 2)
  (func $stackRestore (type 3) (param i32)
    local.get 0
    global.set 2)
  (func $establishStackSpace (type 7) (param i32 i32)
    local.get 0
    global.set 2
    local.get 1
    global.set 3)
  (func $_main (type 6) (result i32)
    call $_puts
    i32.const 0)
  (func $___stdio_close (type 1) (param i32) (result i32)
    (local i32)
    global.get 2
    local.set 1
    global.get 2
    i32.const 16
    i32.add
    global.set 2
    local.get 1
    local.get 0
    i32.load offset=60
    i32.store
    i32.const 6
    local.get 1
    call $___syscall6
    call $___syscall_ret
    local.set 0
    local.get 1
    global.set 2
    local.get 0)
  (func $___stdout_write (type 0) (param i32 i32 i32) (result i32)
    (local i32 i32 i32)
    global.get 2
    local.set 4
    global.get 2
    i32.const 32
    i32.add
    global.set 2
    local.get 4
    local.tee 3
    i32.const 16
    i32.add
    local.set 5
    local.get 0
    i32.const 2
    i32.store offset=36
    local.get 0
    i32.load
    i32.const 64
    i32.and
    i32.eqz
    if  ;; label = @1
      local.get 3
      local.get 0
      i32.load offset=60
      i32.store
      local.get 3
      i32.const 21523
      i32.store offset=4
      local.get 3
      local.get 5
      i32.store offset=8
      i32.const 54
      local.get 3
      call $___syscall54
      if  ;; label = @2
        local.get 0
        i32.const -1
        i32.store8 offset=75
      end
    end
    local.get 0
    local.get 1
    local.get 2
    call $___stdio_write
    local.set 0
    local.get 4
    global.set 2
    local.get 0)
  (func $___stdio_seek (type 2) (param i32 i64 i32) (result i64)
    (local i32 i32)
    global.get 2
    local.set 4
    global.get 2
    i32.const 32
    i32.add
    global.set 2
    local.get 4
    i32.const 8
    i32.add
    local.tee 3
    local.get 0
    i32.load offset=60
    i32.store
    local.get 3
    local.get 1
    i64.const 32
    i64.shr_u
    i64.store32 offset=4
    local.get 3
    local.get 1
    i64.store32 offset=8
    local.get 3
    local.get 4
    local.tee 0
    i32.store offset=12
    local.get 3
    local.get 2
    i32.store offset=16
    i32.const 140
    local.get 3
    call $___syscall140
    call $___syscall_ret
    i32.const 0
    i32.lt_s
    if (result i64)  ;; label = @1
      local.get 0
      i64.const -1
      i64.store
      i64.const -1
    else
      local.get 0
      i64.load
    end
    local.set 1
    local.get 4
    global.set 2
    local.get 1)
  (func $___syscall_ret (type 1) (param i32) (result i32)
    local.get 0
    i32.const -4096
    i32.gt_u
    if (result i32)  ;; label = @1
      i32.const 2240
      i32.const 0
      local.get 0
      i32.sub
      i32.store
      i32.const -1
    else
      local.get 0
    end)
  (func $___errno_location (type 6) (result i32)
    i32.const 2240)
  (func $___stdio_write (type 0) (param i32 i32 i32) (result i32)
    (local i32 i32 i32 i32 i32 i32 i32)
    global.get 2
    local.set 6
    global.get 2
    i32.const 48
    i32.add
    global.set 2
    local.get 6
    i32.const 32
    i32.add
    local.set 5
    local.get 6
    local.tee 3
    local.get 0
    i32.load offset=28
    local.tee 4
    i32.store
    local.get 3
    local.get 0
    i32.load offset=20
    local.get 4
    i32.sub
    local.tee 4
    i32.store offset=4
    local.get 3
    local.get 1
    i32.store offset=8
    local.get 3
    local.get 2
    i32.store offset=12
    local.get 3
    i32.const 16
    i32.add
    local.tee 1
    local.get 0
    i32.load offset=60
    i32.store
    local.get 1
    local.get 3
    i32.store offset=4
    local.get 1
    i32.const 2
    i32.store offset=8
    block  ;; label = @1
      block  ;; label = @2
        local.get 2
        local.get 4
        i32.add
        local.tee 4
        i32.const 146
        local.get 1
        call $___syscall146
        call $___syscall_ret
        local.tee 1
        i32.eq
        br_if 0 (;@2;)
        i32.const 2
        local.set 7
        loop  ;; label = @3
          local.get 1
          i32.const 0
          i32.ge_s
          if  ;; label = @4
            local.get 4
            local.get 1
            i32.sub
            local.set 4
            local.get 3
            i32.const 8
            i32.add
            local.get 3
            local.get 1
            local.get 3
            i32.load offset=4
            local.tee 9
            i32.gt_u
            local.tee 8
            select
            local.tee 3
            local.get 1
            local.get 9
            i32.const 0
            local.get 8
            select
            i32.sub
            local.tee 1
            local.get 3
            i32.load
            i32.add
            i32.store
            local.get 3
            local.get 3
            i32.load offset=4
            local.get 1
            i32.sub
            i32.store offset=4
            local.get 5
            local.get 0
            i32.load offset=60
            i32.store
            local.get 5
            local.get 3
            i32.store offset=4
            local.get 5
            local.get 8
            i32.const 31
            i32.shl
            i32.const 31
            i32.shr_s
            local.get 7
            i32.add
            local.tee 7
            i32.store offset=8
            i32.const 146
            local.get 5
            call $___syscall146
            call $___syscall_ret
            local.tee 1
            local.get 4
            i32.eq
            br_if 2 (;@2;)
            br 1 (;@3;)
          end
        end
        local.get 0
        i32.const 0
        i32.store offset=16
        local.get 0
        i32.const 0
        i32.store offset=28
        local.get 0
        i32.const 0
        i32.store offset=20
        local.get 0
        local.get 0
        i32.load
        i32.const 32
        i32.or
        i32.store
        local.get 7
        i32.const 2
        i32.eq
        if (result i32)  ;; label = @3
          i32.const 0
        else
          local.get 2
          local.get 3
          i32.load offset=4
          i32.sub
        end
        local.set 2
        br 1 (;@1;)
      end
      local.get 0
      local.get 0
      i32.load offset=44
      local.tee 1
      local.get 0
      i32.load offset=48
      i32.add
      i32.store offset=16
      local.get 0
      local.get 1
      i32.store offset=28
      local.get 0
      local.get 1
      i32.store offset=20
    end
    local.get 6
    global.set 2
    local.get 2)
  (func $_strlen (type 6) (result i32)
    (local i32 i32 i32)
    i32.const 1172
    local.set 0
    block  ;; label = @1
      loop  ;; label = @2
        local.get 0
        i32.const 4
        i32.add
        local.set 1
        local.get 0
        i32.load
        local.tee 2
        i32.const -2139062144
        i32.and
        i32.const -2139062144
        i32.xor
        local.get 2
        i32.const -16843009
        i32.add
        i32.and
        i32.eqz
        if  ;; label = @3
          local.get 1
          local.set 0
          br 1 (;@2;)
        end
      end
      local.get 2
      i32.const 255
      i32.and
      if  ;; label = @2
        loop  ;; label = @3
          local.get 0
          i32.const 1
          i32.add
          local.tee 0
          i32.load8_s
          br_if 0 (;@3;)
        end
      end
    end
    local.get 0
    i32.const 1172
    i32.sub)
  (func $___fwritex (type 4) (param i32 i32) (result i32)
    (local i32 i32 i32 i32)
    i32.const 1172
    local.set 4
    block  ;; label = @1
      block  ;; label = @2
        local.get 1
        i32.load offset=16
        local.tee 2
        br_if 0 (;@2;)
        local.get 1
        call $___towrite
        if (result i32)  ;; label = @3
          i32.const 0
        else
          local.get 1
          i32.load offset=16
          local.set 2
          br 1 (;@2;)
        end
        local.set 3
        br 1 (;@1;)
      end
      local.get 2
      local.get 1
      i32.load offset=20
      local.tee 3
      i32.sub
      local.get 0
      i32.lt_u
      if  ;; label = @2
        local.get 1
        i32.const 1172
        local.get 0
        local.get 1
        i32.load offset=36
        i32.const 3
        i32.and
        i32.const 2
        i32.add
        call_indirect (type 0)
        local.set 3
        br 1 (;@1;)
      end
      local.get 0
      i32.eqz
      local.get 1
      i32.load8_s offset=75
      i32.const 0
      i32.lt_s
      i32.or
      if (result i32)  ;; label = @2
        i32.const 0
      else
        block (result i32)  ;; label = @3
          local.get 0
          local.set 2
          loop  ;; label = @4
            local.get 2
            i32.const -1
            i32.add
            local.tee 5
            i32.const 1172
            i32.add
            i32.load8_s
            i32.const 10
            i32.ne
            if  ;; label = @5
              local.get 5
              if  ;; label = @6
                local.get 5
                local.set 2
                br 2 (;@4;)
              else
                i32.const 0
                br 3 (;@3;)
              end
              unreachable
            end
          end
          local.get 1
          i32.const 1172
          local.get 2
          local.get 1
          i32.load offset=36
          i32.const 3
          i32.and
          i32.const 2
          i32.add
          call_indirect (type 0)
          local.tee 3
          local.get 2
          i32.lt_u
          br_if 2 (;@1;)
          local.get 1
          i32.load offset=20
          local.set 3
          local.get 0
          local.get 2
          i32.sub
          local.set 0
          local.get 2
          i32.const 1172
          i32.add
          local.set 4
          local.get 2
        end
      end
      local.set 2
      local.get 3
      local.get 4
      local.get 0
      call $_memcpy
      drop
      local.get 1
      local.get 1
      i32.load offset=20
      local.get 0
      i32.add
      i32.store offset=20
      local.get 0
      local.get 2
      i32.add
      local.set 3
    end
    local.get 3)
  (func $___towrite (type 1) (param i32) (result i32)
    (local i32)
    local.get 0
    local.get 0
    i32.load8_s offset=74
    local.tee 1
    local.get 1
    i32.const 255
    i32.add
    i32.or
    i32.store8 offset=74
    local.get 0
    i32.load
    local.tee 1
    i32.const 8
    i32.and
    if (result i32)  ;; label = @1
      local.get 0
      local.get 1
      i32.const 32
      i32.or
      i32.store
      i32.const -1
    else
      local.get 0
      i32.const 0
      i32.store offset=8
      local.get 0
      i32.const 0
      i32.store offset=4
      local.get 0
      local.get 0
      i32.load offset=44
      local.tee 1
      i32.store offset=28
      local.get 0
      local.get 1
      i32.store offset=20
      local.get 0
      local.get 1
      local.get 0
      i32.load offset=48
      i32.add
      i32.store offset=16
      i32.const 0
    end)
  (func $_fwrite (type 4) (param i32 i32) (result i32)
    (local i32)
    local.get 0
    local.set 2
    block (result i32)  ;; label = @1
      local.get 1
      i32.load offset=76
      drop
      local.get 2
      local.get 1
      call $___fwritex
      local.tee 1
    end
    local.get 0
    local.get 1
    local.get 2
    i32.ne
    select)
  (func $___overflow (type 1) (param i32) (result i32)
    (local i32 i32 i32 i32)
    global.get 2
    local.set 2
    global.get 2
    i32.const 16
    i32.add
    global.set 2
    local.get 2
    local.tee 3
    i32.const 10
    i32.store8
    block  ;; label = @1
      block  ;; label = @2
        local.get 0
        i32.load offset=16
        local.tee 1
        br_if 0 (;@2;)
        local.get 0
        call $___towrite
        if (result i32)  ;; label = @3
          i32.const -1
        else
          local.get 0
          i32.load offset=16
          local.set 1
          br 1 (;@2;)
        end
        local.set 1
        br 1 (;@1;)
      end
      local.get 0
      i32.load offset=20
      local.tee 4
      local.get 1
      i32.lt_u
      if  ;; label = @2
        i32.const 10
        local.tee 1
        local.get 0
        i32.load8_s offset=75
        i32.ne
        if  ;; label = @3
          local.get 0
          local.get 4
          i32.const 1
          i32.add
          i32.store offset=20
          local.get 4
          i32.const 10
          i32.store8
          br 2 (;@1;)
        end
      end
      local.get 0
      local.get 3
      i32.const 1
      local.get 0
      i32.load offset=36
      i32.const 3
      i32.and
      i32.const 2
      i32.add
      call_indirect (type 0)
      i32.const 1
      i32.eq
      if (result i32)  ;; label = @2
        local.get 3
        i32.load8_u
      else
        i32.const -1
      end
      local.set 1
    end
    local.get 2
    global.set 2
    local.get 1)
  (func $_puts (type 5)
    (local i32 i32)
    i32.const 1168
    i32.load
    local.tee 0
    i32.load offset=76
    i32.const -1
    i32.gt_s
    if (result i32)  ;; label = @1
      i32.const 1
    else
      i32.const 0
    end
    drop
    call $_strlen
    local.tee 1
    local.get 1
    local.get 0
    call $_fwrite
    i32.ne
    i32.const 31
    i32.shl
    i32.const 31
    i32.shr_s
    i32.const 0
    i32.lt_s
    if (result i32)  ;; label = @1
      i32.const -1
    else
      block (result i32)  ;; label = @2
        local.get 0
        i32.load8_s offset=75
        i32.const 10
        i32.ne
        if  ;; label = @3
          local.get 0
          i32.load offset=20
          local.tee 1
          local.get 0
          i32.load offset=16
          i32.lt_u
          if  ;; label = @4
            local.get 0
            local.get 1
            i32.const 1
            i32.add
            i32.store offset=20
            local.get 1
            i32.const 10
            i32.store8
            i32.const 0
            br 2 (;@2;)
          end
        end
        local.get 0
        call $___overflow
      end
    end
    drop)
  (func $_malloc (type 1) (param i32) (result i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32)
    global.get 2
    local.set 17
    global.get 2
    i32.const 16
    i32.add
    global.set 2
    local.get 0
    i32.const 245
    i32.lt_u
    if (result i32)  ;; label = @1
      i32.const 2244
      i32.load
      local.tee 4
      i32.const 16
      local.get 0
      i32.const 11
      i32.add
      i32.const -8
      i32.and
      local.get 0
      i32.const 11
      i32.lt_u
      select
      local.tee 14
      i32.const 3
      i32.shr_u
      local.tee 7
      i32.shr_u
      local.tee 1
      i32.const 3
      i32.and
      if  ;; label = @2
        local.get 1
        i32.const 1
        i32.and
        i32.const 1
        i32.xor
        local.get 7
        i32.add
        local.tee 0
        i32.const 3
        i32.shl
        i32.const 2284
        i32.add
        local.tee 3
        i32.load offset=8
        local.tee 2
        i32.const 8
        i32.add
        local.tee 1
        i32.load
        local.tee 5
        local.get 3
        i32.eq
        if  ;; label = @3
          i32.const 2244
          local.get 4
          i32.const 1
          local.get 0
          i32.shl
          i32.const -1
          i32.xor
          i32.and
          i32.store
        else
          i32.const 2260
          i32.load
          local.get 5
          i32.gt_u
          if  ;; label = @4
            call $_abort
          end
          local.get 2
          local.get 5
          i32.load offset=12
          i32.eq
          if  ;; label = @4
            local.get 5
            local.get 3
            i32.store offset=12
            local.get 3
            local.get 5
            i32.store offset=8
          else
            call $_abort
          end
        end
        local.get 2
        local.get 0
        i32.const 3
        i32.shl
        local.tee 0
        i32.const 3
        i32.or
        i32.store offset=4
        local.get 0
        local.get 2
        i32.add
        local.tee 0
        local.get 0
        i32.load offset=4
        i32.const 1
        i32.or
        i32.store offset=4
        local.get 17
        global.set 2
        local.get 1
        return
      end
      local.get 14
      i32.const 2252
      i32.load
      local.tee 12
      i32.gt_u
      if (result i32)  ;; label = @2
        local.get 1
        if  ;; label = @3
          i32.const 2
          local.get 7
          i32.shl
          local.tee 0
          i32.const 0
          local.get 0
          i32.sub
          i32.or
          local.get 1
          local.get 7
          i32.shl
          i32.and
          local.tee 0
          i32.const 0
          local.get 0
          i32.sub
          i32.and
          i32.const -1
          i32.add
          local.tee 1
          i32.const 12
          i32.shr_u
          i32.const 16
          i32.and
          local.tee 0
          local.get 1
          local.get 0
          i32.shr_u
          local.tee 1
          i32.const 5
          i32.shr_u
          i32.const 8
          i32.and
          local.tee 0
          i32.or
          local.get 1
          local.get 0
          i32.shr_u
          local.tee 1
          i32.const 2
          i32.shr_u
          i32.const 4
          i32.and
          local.tee 0
          i32.or
          local.get 1
          local.get 0
          i32.shr_u
          local.tee 1
          i32.const 1
          i32.shr_u
          i32.const 2
          i32.and
          local.tee 0
          i32.or
          local.get 1
          local.get 0
          i32.shr_u
          local.tee 1
          i32.const 1
          i32.shr_u
          i32.const 1
          i32.and
          local.tee 0
          i32.or
          local.get 1
          local.get 0
          i32.shr_u
          i32.add
          local.tee 0
          i32.const 3
          i32.shl
          i32.const 2284
          i32.add
          local.tee 1
          i32.load offset=8
          local.tee 8
          i32.const 8
          i32.add
          local.tee 5
          i32.load
          local.tee 2
          local.get 1
          i32.eq
          if  ;; label = @4
            i32.const 2244
            local.get 4
            i32.const 1
            local.get 0
            i32.shl
            i32.const -1
            i32.xor
            i32.and
            local.tee 11
            i32.store
          else
            i32.const 2260
            i32.load
            local.get 2
            i32.gt_u
            if  ;; label = @5
              call $_abort
            end
            local.get 2
            i32.load offset=12
            local.get 8
            i32.eq
            if  ;; label = @5
              local.get 2
              local.get 1
              i32.store offset=12
              local.get 1
              local.get 2
              i32.store offset=8
              local.get 4
              local.set 11
            else
              call $_abort
            end
          end
          local.get 8
          local.get 14
          i32.const 3
          i32.or
          i32.store offset=4
          local.get 8
          local.get 14
          i32.add
          local.tee 3
          local.get 0
          i32.const 3
          i32.shl
          local.tee 0
          local.get 14
          i32.sub
          local.tee 4
          i32.const 1
          i32.or
          i32.store offset=4
          local.get 0
          local.get 8
          i32.add
          local.get 4
          i32.store
          local.get 12
          if  ;; label = @4
            i32.const 2264
            i32.load
            local.set 8
            local.get 12
            i32.const 3
            i32.shr_u
            local.tee 0
            i32.const 3
            i32.shl
            i32.const 2284
            i32.add
            local.set 2
            local.get 11
            i32.const 1
            local.get 0
            i32.shl
            local.tee 0
            i32.and
            if  ;; label = @5
              i32.const 2260
              i32.load
              local.get 2
              i32.const 8
              i32.add
              local.tee 1
              i32.load
              local.tee 0
              i32.gt_u
              if  ;; label = @6
                call $_abort
              else
                local.get 1
                local.set 13
                local.get 0
                local.set 9
              end
            else
              i32.const 2244
              local.get 0
              local.get 11
              i32.or
              i32.store
              local.get 2
              i32.const 8
              i32.add
              local.set 13
              local.get 2
              local.set 9
            end
            local.get 13
            local.get 8
            i32.store
            local.get 9
            local.get 8
            i32.store offset=12
            local.get 8
            local.get 9
            i32.store offset=8
            local.get 8
            local.get 2
            i32.store offset=12
          end
          i32.const 2252
          local.get 4
          i32.store
          i32.const 2264
          local.get 3
          i32.store
          local.get 17
          global.set 2
          local.get 5
          return
        end
        i32.const 2248
        i32.load
        local.tee 7
        if (result i32)  ;; label = @3
          local.get 7
          i32.const 0
          local.get 7
          i32.sub
          i32.and
          i32.const -1
          i32.add
          local.tee 1
          i32.const 12
          i32.shr_u
          i32.const 16
          i32.and
          local.tee 0
          local.get 1
          local.get 0
          i32.shr_u
          local.tee 1
          i32.const 5
          i32.shr_u
          i32.const 8
          i32.and
          local.tee 0
          i32.or
          local.get 1
          local.get 0
          i32.shr_u
          local.tee 1
          i32.const 2
          i32.shr_u
          i32.const 4
          i32.and
          local.tee 0
          i32.or
          local.get 1
          local.get 0
          i32.shr_u
          local.tee 1
          i32.const 1
          i32.shr_u
          i32.const 2
          i32.and
          local.tee 0
          i32.or
          local.get 1
          local.get 0
          i32.shr_u
          local.tee 1
          i32.const 1
          i32.shr_u
          i32.const 1
          i32.and
          local.tee 0
          i32.or
          local.get 1
          local.get 0
          i32.shr_u
          i32.add
          i32.const 2
          i32.shl
          i32.const 2548
          i32.add
          i32.load
          local.tee 0
          i32.load offset=4
          i32.const -8
          i32.and
          local.get 14
          i32.sub
          local.set 8
          local.get 0
          local.set 5
          loop  ;; label = @4
            block  ;; label = @5
              local.get 0
              i32.load offset=16
              local.tee 1
              if  ;; label = @6
                local.get 1
                local.set 0
              else
                local.get 0
                i32.load offset=20
                local.tee 0
                i32.eqz
                br_if 1 (;@5;)
              end
              local.get 0
              i32.load offset=4
              i32.const -8
              i32.and
              local.get 14
              i32.sub
              local.tee 1
              local.get 8
              i32.lt_u
              local.set 9
              local.get 1
              local.get 8
              local.get 9
              select
              local.set 8
              local.get 0
              local.get 5
              local.get 9
              select
              local.set 5
              br 1 (;@4;)
            end
          end
          i32.const 2260
          i32.load
          local.tee 13
          local.get 5
          i32.gt_u
          if  ;; label = @4
            call $_abort
          end
          local.get 5
          local.get 14
          i32.add
          local.tee 10
          local.get 5
          i32.le_u
          if  ;; label = @4
            call $_abort
          end
          local.get 5
          i32.load offset=24
          local.set 18
          local.get 5
          i32.load offset=12
          local.tee 0
          local.get 5
          i32.eq
          if  ;; label = @4
            block  ;; label = @5
              local.get 5
              i32.const 20
              i32.add
              local.tee 1
              i32.load
              local.tee 0
              i32.eqz
              if  ;; label = @6
                local.get 5
                i32.const 16
                i32.add
                local.tee 1
                i32.load
                local.tee 0
                i32.eqz
                br_if 1 (;@5;)
              end
              loop  ;; label = @6
                block  ;; label = @7
                  local.get 0
                  i32.const 20
                  i32.add
                  local.tee 11
                  i32.load
                  local.tee 9
                  i32.eqz
                  if  ;; label = @8
                    local.get 0
                    i32.const 16
                    i32.add
                    local.tee 11
                    i32.load
                    local.tee 9
                    i32.eqz
                    br_if 1 (;@7;)
                  end
                  local.get 11
                  local.set 1
                  local.get 9
                  local.set 0
                  br 1 (;@6;)
                end
              end
              local.get 13
              local.get 1
              i32.gt_u
              if  ;; label = @6
                call $_abort
              else
                local.get 1
                i32.const 0
                i32.store
                local.get 0
                local.set 2
              end
            end
          else
            local.get 13
            local.get 5
            i32.load offset=8
            local.tee 1
            i32.gt_u
            if  ;; label = @5
              call $_abort
            end
            local.get 5
            local.get 1
            i32.load offset=12
            i32.ne
            if  ;; label = @5
              call $_abort
            end
            local.get 0
            i32.load offset=8
            local.get 5
            i32.eq
            if  ;; label = @5
              local.get 1
              local.get 0
              i32.store offset=12
              local.get 0
              local.get 1
              i32.store offset=8
              local.get 0
              local.set 2
            else
              call $_abort
            end
          end
          local.get 18
          if  ;; label = @4
            block  ;; label = @5
              local.get 5
              i32.load offset=28
              local.tee 1
              i32.const 2
              i32.shl
              i32.const 2548
              i32.add
              local.tee 0
              i32.load
              local.get 5
              i32.eq
              if  ;; label = @6
                local.get 0
                local.get 2
                i32.store
                local.get 2
                i32.eqz
                if  ;; label = @7
                  i32.const 2248
                  local.get 7
                  i32.const 1
                  local.get 1
                  i32.shl
                  i32.const -1
                  i32.xor
                  i32.and
                  i32.store
                  br 2 (;@5;)
                end
              else
                i32.const 2260
                i32.load
                local.get 18
                i32.gt_u
                if  ;; label = @7
                  call $_abort
                else
                  local.get 18
                  i32.const 16
                  i32.add
                  local.get 18
                  i32.const 20
                  i32.add
                  local.get 18
                  i32.load offset=16
                  local.get 5
                  i32.eq
                  select
                  local.get 2
                  i32.store
                  local.get 2
                  i32.eqz
                  br_if 2 (;@5;)
                end
              end
              i32.const 2260
              i32.load
              local.tee 0
              local.get 2
              i32.gt_u
              if  ;; label = @6
                call $_abort
              end
              local.get 2
              local.get 18
              i32.store offset=24
              local.get 5
              i32.load offset=16
              local.tee 1
              if  ;; label = @6
                local.get 0
                local.get 1
                i32.gt_u
                if  ;; label = @7
                  call $_abort
                else
                  local.get 2
                  local.get 1
                  i32.store offset=16
                  local.get 1
                  local.get 2
                  i32.store offset=24
                end
              end
              local.get 5
              i32.load offset=20
              local.tee 0
              if  ;; label = @6
                i32.const 2260
                i32.load
                local.get 0
                i32.gt_u
                if  ;; label = @7
                  call $_abort
                else
                  local.get 2
                  local.get 0
                  i32.store offset=20
                  local.get 0
                  local.get 2
                  i32.store offset=24
                end
              end
            end
          end
          local.get 8
          i32.const 16
          i32.lt_u
          if  ;; label = @4
            local.get 5
            local.get 8
            local.get 14
            i32.add
            local.tee 0
            i32.const 3
            i32.or
            i32.store offset=4
            local.get 0
            local.get 5
            i32.add
            local.tee 0
            local.get 0
            i32.load offset=4
            i32.const 1
            i32.or
            i32.store offset=4
          else
            local.get 5
            local.get 14
            i32.const 3
            i32.or
            i32.store offset=4
            local.get 10
            local.get 8
            i32.const 1
            i32.or
            i32.store offset=4
            local.get 8
            local.get 10
            i32.add
            local.get 8
            i32.store
            local.get 12
            if  ;; label = @5
              i32.const 2264
              i32.load
              local.set 9
              local.get 12
              i32.const 3
              i32.shr_u
              local.tee 0
              i32.const 3
              i32.shl
              i32.const 2284
              i32.add
              local.set 2
              local.get 4
              i32.const 1
              local.get 0
              i32.shl
              local.tee 0
              i32.and
              if  ;; label = @6
                i32.const 2260
                i32.load
                local.get 2
                i32.const 8
                i32.add
                local.tee 1
                i32.load
                local.tee 0
                i32.gt_u
                if  ;; label = @7
                  call $_abort
                else
                  local.get 1
                  local.set 6
                  local.get 0
                  local.set 3
                end
              else
                i32.const 2244
                local.get 0
                local.get 4
                i32.or
                i32.store
                local.get 2
                i32.const 8
                i32.add
                local.set 6
                local.get 2
                local.set 3
              end
              local.get 6
              local.get 9
              i32.store
              local.get 3
              local.get 9
              i32.store offset=12
              local.get 9
              local.get 3
              i32.store offset=8
              local.get 9
              local.get 2
              i32.store offset=12
            end
            i32.const 2252
            local.get 8
            i32.store
            i32.const 2264
            local.get 10
            i32.store
          end
          local.get 17
          global.set 2
          local.get 5
          i32.const 8
          i32.add
          return
        else
          local.get 14
        end
      else
        local.get 14
      end
    else
      local.get 0
      i32.const -65
      i32.gt_u
      if (result i32)  ;; label = @2
        i32.const -1
      else
        block (result i32)  ;; label = @3
          local.get 0
          i32.const 11
          i32.add
          local.tee 0
          i32.const -8
          i32.and
          local.set 15
          i32.const 2248
          i32.load
          local.tee 9
          if (result i32)  ;; label = @4
            i32.const 0
            local.get 15
            i32.sub
            local.set 2
            block  ;; label = @5
              block  ;; label = @6
                local.get 0
                i32.const 8
                i32.shr_u
                local.tee 0
                if (result i32)  ;; label = @7
                  local.get 15
                  i32.const 16777215
                  i32.gt_u
                  if (result i32)  ;; label = @8
                    i32.const 31
                  else
                    local.get 0
                    local.get 0
                    i32.const 1048320
                    i32.add
                    i32.const 16
                    i32.shr_u
                    i32.const 8
                    i32.and
                    local.tee 6
                    i32.shl
                    local.tee 0
                    i32.const 520192
                    i32.add
                    i32.const 16
                    i32.shr_u
                    i32.const 4
                    i32.and
                    local.set 11
                    local.get 15
                    i32.const 14
                    local.get 0
                    local.get 11
                    i32.shl
                    local.tee 3
                    i32.const 245760
                    i32.add
                    i32.const 16
                    i32.shr_u
                    i32.const 2
                    i32.and
                    local.tee 0
                    local.get 6
                    local.get 11
                    i32.or
                    i32.or
                    i32.sub
                    local.get 3
                    local.get 0
                    i32.shl
                    i32.const 15
                    i32.shr_u
                    i32.add
                    local.tee 0
                    i32.const 7
                    i32.add
                    i32.shr_u
                    i32.const 1
                    i32.and
                    local.get 0
                    i32.const 1
                    i32.shl
                    i32.or
                  end
                else
                  i32.const 0
                end
                local.tee 19
                i32.const 2
                i32.shl
                i32.const 2548
                i32.add
                i32.load
                local.tee 0
                if  ;; label = @7
                  local.get 15
                  i32.const 0
                  i32.const 25
                  local.get 19
                  i32.const 1
                  i32.shr_u
                  i32.sub
                  local.get 19
                  i32.const 31
                  i32.eq
                  select
                  i32.shl
                  local.set 3
                  i32.const 0
                  local.set 6
                  loop  ;; label = @8
                    local.get 0
                    i32.load offset=4
                    i32.const -8
                    i32.and
                    local.get 15
                    i32.sub
                    local.tee 11
                    local.get 2
                    i32.lt_u
                    if  ;; label = @9
                      local.get 11
                      if (result i32)  ;; label = @10
                        local.get 0
                        local.set 6
                        local.get 11
                      else
                        i32.const 0
                        local.set 6
                        local.get 0
                        local.set 2
                        br 4 (;@6;)
                      end
                      local.set 2
                    end
                    local.get 13
                    local.get 0
                    i32.load offset=20
                    local.tee 13
                    local.get 13
                    i32.eqz
                    local.get 13
                    local.get 0
                    i32.const 16
                    i32.add
                    local.get 3
                    i32.const 31
                    i32.shr_u
                    i32.const 2
                    i32.shl
                    i32.add
                    i32.load
                    local.tee 11
                    i32.eq
                    i32.or
                    select
                    local.set 0
                    local.get 3
                    i32.const 1
                    i32.shl
                    local.set 3
                    local.get 11
                    if  ;; label = @9
                      local.get 0
                      local.set 13
                      local.get 11
                      local.set 0
                      br 1 (;@8;)
                    end
                  end
                else
                  i32.const 0
                  local.set 0
                  i32.const 0
                  local.set 6
                end
                local.get 0
                local.get 6
                i32.or
                if (result i32)  ;; label = @7
                  local.get 0
                  local.set 3
                  local.get 6
                else
                  local.get 15
                  local.get 9
                  i32.const 2
                  local.get 19
                  i32.shl
                  local.tee 0
                  i32.const 0
                  local.get 0
                  i32.sub
                  i32.or
                  i32.and
                  local.tee 0
                  i32.eqz
                  br_if 4 (;@3;)
                  drop
                  local.get 0
                  i32.const 0
                  local.get 0
                  i32.sub
                  i32.and
                  i32.const -1
                  i32.add
                  local.tee 3
                  i32.const 12
                  i32.shr_u
                  i32.const 16
                  i32.and
                  local.tee 0
                  local.get 3
                  local.get 0
                  i32.shr_u
                  local.tee 3
                  i32.const 5
                  i32.shr_u
                  i32.const 8
                  i32.and
                  local.tee 0
                  i32.or
                  local.get 3
                  local.get 0
                  i32.shr_u
                  local.tee 3
                  i32.const 2
                  i32.shr_u
                  i32.const 4
                  i32.and
                  local.tee 0
                  i32.or
                  local.get 3
                  local.get 0
                  i32.shr_u
                  local.tee 3
                  i32.const 1
                  i32.shr_u
                  i32.const 2
                  i32.and
                  local.tee 0
                  i32.or
                  local.get 3
                  local.get 0
                  i32.shr_u
                  local.tee 3
                  i32.const 1
                  i32.shr_u
                  i32.const 1
                  i32.and
                  local.tee 0
                  i32.or
                  local.get 3
                  local.get 0
                  i32.shr_u
                  i32.add
                  i32.const 2
                  i32.shl
                  i32.const 2548
                  i32.add
                  i32.load
                  local.set 3
                  i32.const 0
                end
                local.set 0
                local.get 3
                if (result i32)  ;; label = @7
                  local.get 2
                  local.set 6
                  local.get 3
                  local.set 2
                  br 1 (;@6;)
                else
                  local.get 0
                  local.set 3
                  local.get 2
                end
                local.set 6
                br 1 (;@5;)
              end
              local.get 0
              local.set 3
              loop  ;; label = @6
                local.get 2
                i32.load offset=4
                i32.const -8
                i32.and
                local.get 15
                i32.sub
                local.tee 13
                local.get 6
                i32.lt_u
                local.set 11
                local.get 13
                local.get 6
                local.get 11
                select
                local.set 6
                local.get 2
                local.get 3
                local.get 11
                select
                local.set 3
                block (result i32)  ;; label = @7
                  local.get 2
                  i32.load offset=16
                  local.tee 0
                  i32.eqz
                  if  ;; label = @8
                    local.get 2
                    i32.load offset=20
                    local.set 0
                  end
                  local.get 0
                end
                if  ;; label = @7
                  local.get 0
                  local.set 2
                  br 1 (;@6;)
                end
              end
            end
            local.get 3
            if (result i32)  ;; label = @5
              local.get 6
              i32.const 2252
              i32.load
              local.get 15
              i32.sub
              i32.lt_u
              if (result i32)  ;; label = @6
                i32.const 2260
                i32.load
                local.tee 13
                local.get 3
                i32.gt_u
                if  ;; label = @7
                  call $_abort
                end
                local.get 3
                local.get 15
                i32.add
                local.tee 10
                local.get 3
                i32.le_u
                if  ;; label = @7
                  call $_abort
                end
                local.get 3
                i32.load offset=24
                local.set 11
                local.get 3
                i32.load offset=12
                local.tee 0
                local.get 3
                i32.eq
                if  ;; label = @7
                  block  ;; label = @8
                    local.get 3
                    i32.const 20
                    i32.add
                    local.tee 2
                    i32.load
                    local.tee 0
                    i32.eqz
                    if  ;; label = @9
                      local.get 3
                      i32.const 16
                      i32.add
                      local.tee 2
                      i32.load
                      local.tee 0
                      i32.eqz
                      br_if 1 (;@8;)
                    end
                    loop  ;; label = @9
                      block  ;; label = @10
                        local.get 0
                        i32.const 20
                        i32.add
                        local.tee 5
                        i32.load
                        local.tee 8
                        i32.eqz
                        if  ;; label = @11
                          local.get 0
                          i32.const 16
                          i32.add
                          local.tee 5
                          i32.load
                          local.tee 8
                          i32.eqz
                          br_if 1 (;@10;)
                        end
                        local.get 5
                        local.set 2
                        local.get 8
                        local.set 0
                        br 1 (;@9;)
                      end
                    end
                    local.get 13
                    local.get 2
                    i32.gt_u
                    if  ;; label = @9
                      call $_abort
                    else
                      local.get 2
                      i32.const 0
                      i32.store
                      local.get 0
                      local.set 12
                    end
                  end
                else
                  local.get 13
                  local.get 3
                  i32.load offset=8
                  local.tee 2
                  i32.gt_u
                  if  ;; label = @8
                    call $_abort
                  end
                  local.get 3
                  local.get 2
                  i32.load offset=12
                  i32.ne
                  if  ;; label = @8
                    call $_abort
                  end
                  local.get 0
                  i32.load offset=8
                  local.get 3
                  i32.eq
                  if  ;; label = @8
                    local.get 2
                    local.get 0
                    i32.store offset=12
                    local.get 0
                    local.get 2
                    i32.store offset=8
                    local.get 0
                    local.set 12
                  else
                    call $_abort
                  end
                end
                local.get 11
                if  ;; label = @7
                  block  ;; label = @8
                    local.get 3
                    i32.load offset=28
                    local.tee 2
                    i32.const 2
                    i32.shl
                    i32.const 2548
                    i32.add
                    local.tee 0
                    i32.load
                    local.get 3
                    i32.eq
                    if  ;; label = @9
                      local.get 0
                      local.get 12
                      i32.store
                      local.get 12
                      i32.eqz
                      if  ;; label = @10
                        i32.const 2248
                        local.get 9
                        i32.const 1
                        local.get 2
                        i32.shl
                        i32.const -1
                        i32.xor
                        i32.and
                        local.tee 1
                        i32.store
                        br 2 (;@8;)
                      end
                    else
                      i32.const 2260
                      i32.load
                      local.get 11
                      i32.gt_u
                      if  ;; label = @10
                        call $_abort
                      else
                        local.get 11
                        i32.const 16
                        i32.add
                        local.get 11
                        i32.const 20
                        i32.add
                        local.get 11
                        i32.load offset=16
                        local.get 3
                        i32.eq
                        select
                        local.get 12
                        i32.store
                        local.get 12
                        i32.eqz
                        if  ;; label = @11
                          local.get 9
                          local.set 1
                          br 3 (;@8;)
                        end
                      end
                    end
                    i32.const 2260
                    i32.load
                    local.tee 0
                    local.get 12
                    i32.gt_u
                    if  ;; label = @9
                      call $_abort
                    end
                    local.get 12
                    local.get 11
                    i32.store offset=24
                    local.get 3
                    i32.load offset=16
                    local.tee 2
                    if  ;; label = @9
                      local.get 0
                      local.get 2
                      i32.gt_u
                      if  ;; label = @10
                        call $_abort
                      else
                        local.get 12
                        local.get 2
                        i32.store offset=16
                        local.get 2
                        local.get 12
                        i32.store offset=24
                      end
                    end
                    local.get 3
                    i32.load offset=20
                    local.tee 0
                    if  ;; label = @9
                      i32.const 2260
                      i32.load
                      local.get 0
                      i32.gt_u
                      if  ;; label = @10
                        call $_abort
                      else
                        local.get 12
                        local.get 0
                        i32.store offset=20
                        local.get 0
                        local.get 12
                        i32.store offset=24
                        local.get 9
                        local.set 1
                      end
                    else
                      local.get 9
                      local.set 1
                    end
                  end
                else
                  local.get 9
                  local.set 1
                end
                local.get 6
                i32.const 16
                i32.lt_u
                if  ;; label = @7
                  local.get 3
                  local.get 6
                  local.get 15
                  i32.add
                  local.tee 0
                  i32.const 3
                  i32.or
                  i32.store offset=4
                  local.get 0
                  local.get 3
                  i32.add
                  local.tee 0
                  local.get 0
                  i32.load offset=4
                  i32.const 1
                  i32.or
                  i32.store offset=4
                else
                  block  ;; label = @8
                    local.get 3
                    local.get 15
                    i32.const 3
                    i32.or
                    i32.store offset=4
                    local.get 10
                    local.get 6
                    i32.const 1
                    i32.or
                    i32.store offset=4
                    local.get 6
                    local.get 10
                    i32.add
                    local.get 6
                    i32.store
                    local.get 6
                    i32.const 3
                    i32.shr_u
                    local.set 0
                    local.get 6
                    i32.const 256
                    i32.lt_u
                    if  ;; label = @9
                      local.get 0
                      i32.const 3
                      i32.shl
                      i32.const 2284
                      i32.add
                      local.set 2
                      i32.const 2244
                      i32.load
                      local.tee 1
                      i32.const 1
                      local.get 0
                      i32.shl
                      local.tee 0
                      i32.and
                      if  ;; label = @10
                        i32.const 2260
                        i32.load
                        local.get 2
                        i32.const 8
                        i32.add
                        local.tee 1
                        i32.load
                        local.tee 0
                        i32.gt_u
                        if  ;; label = @11
                          call $_abort
                        else
                          local.get 1
                          local.set 14
                          local.get 0
                          local.set 7
                        end
                      else
                        i32.const 2244
                        local.get 0
                        local.get 1
                        i32.or
                        i32.store
                        local.get 2
                        i32.const 8
                        i32.add
                        local.set 14
                        local.get 2
                        local.set 7
                      end
                      local.get 14
                      local.get 10
                      i32.store
                      local.get 7
                      local.get 10
                      i32.store offset=12
                      local.get 10
                      local.get 7
                      i32.store offset=8
                      local.get 10
                      local.get 2
                      i32.store offset=12
                      br 1 (;@8;)
                    end
                    local.get 6
                    i32.const 8
                    i32.shr_u
                    local.tee 0
                    if (result i32)  ;; label = @9
                      local.get 6
                      i32.const 16777215
                      i32.gt_u
                      if (result i32)  ;; label = @10
                        i32.const 31
                      else
                        local.get 0
                        local.get 0
                        i32.const 1048320
                        i32.add
                        i32.const 16
                        i32.shr_u
                        i32.const 8
                        i32.and
                        local.tee 5
                        i32.shl
                        local.tee 0
                        i32.const 520192
                        i32.add
                        i32.const 16
                        i32.shr_u
                        i32.const 4
                        i32.and
                        local.set 9
                        local.get 6
                        i32.const 14
                        local.get 0
                        local.get 9
                        i32.shl
                        local.tee 2
                        i32.const 245760
                        i32.add
                        i32.const 16
                        i32.shr_u
                        i32.const 2
                        i32.and
                        local.tee 0
                        local.get 5
                        local.get 9
                        i32.or
                        i32.or
                        i32.sub
                        local.get 2
                        local.get 0
                        i32.shl
                        i32.const 15
                        i32.shr_u
                        i32.add
                        local.tee 0
                        i32.const 7
                        i32.add
                        i32.shr_u
                        i32.const 1
                        i32.and
                        local.get 0
                        i32.const 1
                        i32.shl
                        i32.or
                      end
                    else
                      i32.const 0
                    end
                    local.tee 5
                    i32.const 2
                    i32.shl
                    i32.const 2548
                    i32.add
                    local.set 2
                    local.get 10
                    local.get 5
                    i32.store offset=28
                    local.get 10
                    i32.const 0
                    i32.store offset=20
                    local.get 10
                    i32.const 0
                    i32.store offset=16
                    local.get 1
                    i32.const 1
                    local.get 5
                    i32.shl
                    local.tee 0
                    i32.and
                    i32.eqz
                    if  ;; label = @9
                      i32.const 2248
                      local.get 0
                      local.get 1
                      i32.or
                      i32.store
                      local.get 2
                      local.get 10
                      i32.store
                      local.get 10
                      local.get 2
                      i32.store offset=24
                      local.get 10
                      local.get 10
                      i32.store offset=12
                      local.get 10
                      local.get 10
                      i32.store offset=8
                      br 1 (;@8;)
                    end
                    local.get 2
                    i32.load
                    local.tee 0
                    i32.load offset=4
                    i32.const -8
                    i32.and
                    local.get 6
                    i32.eq
                    if  ;; label = @9
                      local.get 0
                      local.set 4
                    else
                      block  ;; label = @10
                        local.get 6
                        i32.const 0
                        i32.const 25
                        local.get 5
                        i32.const 1
                        i32.shr_u
                        i32.sub
                        local.get 5
                        i32.const 31
                        i32.eq
                        select
                        i32.shl
                        local.set 2
                        loop  ;; label = @11
                          local.get 0
                          i32.const 16
                          i32.add
                          local.get 2
                          i32.const 31
                          i32.shr_u
                          i32.const 2
                          i32.shl
                          i32.add
                          local.tee 5
                          i32.load
                          local.tee 1
                          if  ;; label = @12
                            local.get 2
                            i32.const 1
                            i32.shl
                            local.set 2
                            local.get 1
                            i32.load offset=4
                            i32.const -8
                            i32.and
                            local.get 6
                            i32.eq
                            if  ;; label = @13
                              local.get 1
                              local.set 4
                              br 3 (;@10;)
                            else
                              local.get 1
                              local.set 0
                              br 2 (;@11;)
                            end
                            unreachable
                          end
                        end
                        i32.const 2260
                        i32.load
                        local.get 5
                        i32.gt_u
                        if  ;; label = @11
                          call $_abort
                        else
                          local.get 5
                          local.get 10
                          i32.store
                          local.get 10
                          local.get 0
                          i32.store offset=24
                          local.get 10
                          local.get 10
                          i32.store offset=12
                          local.get 10
                          local.get 10
                          i32.store offset=8
                          br 3 (;@8;)
                        end
                      end
                    end
                    i32.const 2260
                    i32.load
                    local.tee 0
                    local.get 4
                    i32.le_u
                    local.get 0
                    local.get 4
                    local.tee 0
                    i32.load offset=8
                    local.tee 1
                    i32.le_u
                    i32.and
                    if  ;; label = @9
                      local.get 1
                      local.get 10
                      i32.store offset=12
                      local.get 0
                      local.get 10
                      i32.store offset=8
                      local.get 10
                      local.get 1
                      i32.store offset=8
                      local.get 10
                      local.get 4
                      i32.store offset=12
                      local.get 10
                      i32.const 0
                      i32.store offset=24
                    else
                      call $_abort
                    end
                  end
                end
                local.get 17
                global.set 2
                local.get 3
                i32.const 8
                i32.add
                return
              else
                local.get 15
              end
            else
              local.get 15
            end
          else
            local.get 15
          end
        end
      end
    end
    local.set 12
    block  ;; label = @1
      block  ;; label = @2
        i32.const 2252
        i32.load
        local.tee 3
        local.get 12
        i32.ge_u
        if  ;; label = @3
          i32.const 2264
          i32.load
          local.set 0
          local.get 3
          local.get 12
          i32.sub
          local.tee 2
          i32.const 15
          i32.gt_u
          if  ;; label = @4
            i32.const 2264
            local.get 0
            local.get 12
            i32.add
            local.tee 1
            i32.store
            i32.const 2252
            local.get 2
            i32.store
            local.get 1
            local.get 2
            i32.const 1
            i32.or
            i32.store offset=4
            local.get 0
            local.get 3
            i32.add
            local.get 2
            i32.store
            local.get 0
            local.get 12
            i32.const 3
            i32.or
            i32.store offset=4
          else
            i32.const 2252
            i32.const 0
            i32.store
            i32.const 2264
            i32.const 0
            i32.store
            local.get 0
            local.get 3
            i32.const 3
            i32.or
            i32.store offset=4
            local.get 0
            local.get 3
            i32.add
            local.tee 1
            local.get 1
            i32.load offset=4
            i32.const 1
            i32.or
            i32.store offset=4
          end
          br 1 (;@2;)
        end
        block  ;; label = @3
          i32.const 2256
          i32.load
          local.tee 11
          local.get 12
          i32.gt_u
          if  ;; label = @4
            i32.const 2256
            local.get 11
            local.get 12
            i32.sub
            local.tee 2
            i32.store
            br 1 (;@3;)
          end
          local.get 17
          local.set 0
          i32.const 2716
          i32.load
          if (result i32)  ;; label = @4
            i32.const 2724
            i32.load
          else
            i32.const 2724
            i32.const 4096
            i32.store
            i32.const 2720
            i32.const 4096
            i32.store
            i32.const 2728
            i32.const -1
            i32.store
            i32.const 2732
            i32.const -1
            i32.store
            i32.const 2736
            i32.const 0
            i32.store
            i32.const 2688
            i32.const 0
            i32.store
            i32.const 2716
            local.get 0
            i32.const -16
            i32.and
            i32.const 1431655768
            i32.xor
            i32.store
            i32.const 4096
          end
          local.tee 0
          local.get 12
          i32.const 47
          i32.add
          local.tee 13
          i32.add
          local.tee 4
          i32.const 0
          local.get 0
          i32.sub
          local.tee 3
          i32.and
          local.tee 7
          local.get 12
          i32.le_u
          if  ;; label = @4
            br 3 (;@1;)
          end
          i32.const 2684
          i32.load
          local.tee 2
          if  ;; label = @4
            i32.const 2676
            i32.load
            local.tee 1
            local.get 7
            i32.add
            local.tee 0
            local.get 1
            i32.le_u
            local.get 0
            local.get 2
            i32.gt_u
            i32.or
            if  ;; label = @5
              br 4 (;@1;)
            end
          end
          local.get 12
          i32.const 48
          i32.add
          local.set 9
          block  ;; label = @4
            block  ;; label = @5
              i32.const 2688
              i32.load
              i32.const 4
              i32.and
              if  ;; label = @6
                i32.const 0
                local.set 2
              else
                block  ;; label = @7
                  block  ;; label = @8
                    block  ;; label = @9
                      i32.const 2268
                      i32.load
                      local.tee 1
                      i32.eqz
                      br_if 0 (;@9;)
                      i32.const 2692
                      local.set 6
                      loop  ;; label = @10
                        block  ;; label = @11
                          local.get 6
                          i32.load
                          local.tee 0
                          local.get 1
                          i32.le_u
                          if  ;; label = @12
                            local.get 0
                            local.get 6
                            i32.load offset=4
                            i32.add
                            local.get 1
                            i32.gt_u
                            br_if 1 (;@11;)
                          end
                          local.get 6
                          i32.load offset=8
                          local.tee 6
                          br_if 1 (;@10;)
                          br 2 (;@9;)
                        end
                      end
                      local.get 4
                      local.get 11
                      i32.sub
                      local.get 3
                      i32.and
                      local.tee 2
                      i32.const 2147483647
                      i32.lt_u
                      if  ;; label = @10
                        local.get 2
                        call $_sbrk
                        local.set 0
                        local.get 0
                        local.get 6
                        i32.load
                        local.get 6
                        i32.load offset=4
                        i32.add
                        i32.ne
                        br_if 2 (;@8;)
                        local.get 0
                        i32.const -1
                        i32.ne
                        br_if 5 (;@5;)
                      else
                        i32.const 0
                        local.set 2
                      end
                      br 2 (;@7;)
                    end
                    i32.const 0
                    call $_sbrk
                    local.tee 0
                    i32.const -1
                    i32.eq
                    if (result i32)  ;; label = @9
                      i32.const 0
                    else
                      i32.const 2676
                      i32.load
                      local.tee 3
                      local.get 0
                      i32.const 2720
                      i32.load
                      local.tee 2
                      i32.const -1
                      i32.add
                      local.tee 1
                      i32.add
                      i32.const 0
                      local.get 2
                      i32.sub
                      i32.and
                      local.get 0
                      i32.sub
                      i32.const 0
                      local.get 0
                      local.get 1
                      i32.and
                      select
                      local.get 7
                      i32.add
                      local.tee 2
                      i32.add
                      local.set 4
                      local.get 2
                      i32.const 2147483647
                      i32.lt_u
                      local.get 2
                      local.get 12
                      i32.gt_u
                      i32.and
                      if (result i32)  ;; label = @10
                        i32.const 2684
                        i32.load
                        local.tee 1
                        if  ;; label = @11
                          local.get 4
                          local.get 3
                          i32.le_u
                          local.get 4
                          local.get 1
                          i32.gt_u
                          i32.or
                          if  ;; label = @12
                            i32.const 0
                            local.set 2
                            br 5 (;@7;)
                          end
                        end
                        local.get 0
                        local.get 2
                        call $_sbrk
                        local.tee 1
                        i32.eq
                        br_if 5 (;@5;)
                        local.get 1
                        local.set 0
                        br 2 (;@8;)
                      else
                        i32.const 0
                      end
                    end
                    local.set 2
                    br 1 (;@7;)
                  end
                  local.get 0
                  i32.const -1
                  i32.ne
                  local.get 2
                  i32.const 2147483647
                  i32.lt_u
                  i32.and
                  local.get 9
                  local.get 2
                  i32.gt_u
                  i32.and
                  i32.eqz
                  if  ;; label = @8
                    local.get 0
                    i32.const -1
                    i32.eq
                    if  ;; label = @9
                      i32.const 0
                      local.set 2
                      br 2 (;@7;)
                    else
                      br 4 (;@5;)
                    end
                    unreachable
                  end
                  i32.const 2724
                  i32.load
                  local.tee 1
                  local.get 13
                  local.get 2
                  i32.sub
                  i32.add
                  i32.const 0
                  local.get 1
                  i32.sub
                  i32.and
                  local.tee 3
                  i32.const 2147483647
                  i32.ge_u
                  br_if 2 (;@5;)
                  i32.const 0
                  local.get 2
                  i32.sub
                  local.set 1
                  local.get 3
                  call $_sbrk
                  i32.const -1
                  i32.eq
                  if (result i32)  ;; label = @8
                    local.get 1
                    call $_sbrk
                    drop
                    i32.const 0
                  else
                    local.get 2
                    local.get 3
                    i32.add
                    local.set 2
                    br 3 (;@5;)
                  end
                  local.set 2
                end
                i32.const 2688
                i32.const 2688
                i32.load
                i32.const 4
                i32.or
                i32.store
              end
              local.get 7
              i32.const 2147483647
              i32.lt_u
              if  ;; label = @6
                local.get 7
                call $_sbrk
                local.set 0
                i32.const 0
                call $_sbrk
                local.tee 9
                local.get 0
                i32.sub
                local.tee 1
                local.get 12
                i32.const 40
                i32.add
                i32.gt_u
                local.set 3
                local.get 1
                local.get 2
                local.get 3
                select
                local.set 2
                local.get 3
                i32.const 1
                i32.xor
                local.get 0
                i32.const -1
                i32.eq
                i32.or
                local.get 0
                i32.const -1
                i32.ne
                local.get 9
                i32.const -1
                i32.ne
                i32.and
                local.get 0
                local.get 9
                i32.lt_u
                i32.and
                i32.const 1
                i32.xor
                i32.or
                i32.eqz
                br_if 1 (;@5;)
              end
              br 1 (;@4;)
            end
            i32.const 2676
            i32.const 2676
            i32.load
            local.get 2
            i32.add
            local.tee 1
            i32.store
            local.get 1
            i32.const 2680
            i32.load
            i32.gt_u
            if  ;; label = @5
              i32.const 2680
              local.get 1
              i32.store
            end
            i32.const 2268
            i32.load
            local.tee 4
            if  ;; label = @5
              block  ;; label = @6
                i32.const 2692
                local.set 6
                block  ;; label = @7
                  block  ;; label = @8
                    loop  ;; label = @9
                      local.get 6
                      i32.load
                      local.tee 9
                      local.get 6
                      i32.load offset=4
                      local.tee 3
                      i32.add
                      local.get 0
                      i32.eq
                      br_if 1 (;@8;)
                      local.get 6
                      i32.load offset=8
                      local.tee 6
                      br_if 0 (;@9;)
                    end
                    br 1 (;@7;)
                  end
                  local.get 6
                  local.tee 1
                  i32.load offset=12
                  i32.const 8
                  i32.and
                  i32.eqz
                  if  ;; label = @8
                    local.get 9
                    local.get 4
                    i32.le_u
                    local.get 0
                    local.get 4
                    i32.gt_u
                    i32.and
                    if  ;; label = @9
                      local.get 1
                      local.get 2
                      local.get 3
                      i32.add
                      i32.store offset=4
                      local.get 4
                      i32.const 0
                      local.get 4
                      i32.const 8
                      i32.add
                      local.tee 0
                      i32.sub
                      i32.const 7
                      i32.and
                      i32.const 0
                      local.get 0
                      i32.const 7
                      i32.and
                      select
                      local.tee 1
                      i32.add
                      local.set 3
                      i32.const 2256
                      i32.load
                      local.get 2
                      i32.add
                      local.tee 0
                      local.get 1
                      i32.sub
                      local.set 1
                      i32.const 2268
                      local.get 3
                      i32.store
                      i32.const 2256
                      local.get 1
                      i32.store
                      local.get 3
                      local.get 1
                      i32.const 1
                      i32.or
                      i32.store offset=4
                      local.get 0
                      local.get 4
                      i32.add
                      i32.const 40
                      i32.store offset=4
                      i32.const 2272
                      i32.const 2732
                      i32.load
                      i32.store
                      br 3 (;@6;)
                    end
                  end
                end
                local.get 0
                i32.const 2260
                i32.load
                local.tee 6
                i32.lt_u
                if  ;; label = @7
                  i32.const 2260
                  local.get 0
                  i32.store
                  local.get 0
                  local.set 6
                end
                local.get 0
                local.get 2
                i32.add
                local.set 1
                i32.const 2692
                local.set 11
                block  ;; label = @7
                  block  ;; label = @8
                    loop  ;; label = @9
                      local.get 11
                      i32.load
                      local.get 1
                      i32.eq
                      br_if 1 (;@8;)
                      local.get 11
                      i32.load offset=8
                      local.tee 11
                      br_if 0 (;@9;)
                    end
                    br 1 (;@7;)
                  end
                  local.get 11
                  i32.load offset=12
                  i32.const 8
                  i32.and
                  i32.eqz
                  if  ;; label = @8
                    local.get 11
                    local.get 0
                    i32.store
                    local.get 11
                    local.get 11
                    i32.load offset=4
                    local.get 2
                    i32.add
                    i32.store offset=4
                    local.get 0
                    i32.const 0
                    local.get 0
                    i32.const 8
                    i32.add
                    local.tee 0
                    i32.sub
                    i32.const 7
                    i32.and
                    i32.const 0
                    local.get 0
                    i32.const 7
                    i32.and
                    select
                    i32.add
                    local.tee 11
                    local.get 12
                    i32.add
                    local.set 7
                    local.get 1
                    i32.const 0
                    local.get 1
                    i32.const 8
                    i32.add
                    local.tee 0
                    i32.sub
                    i32.const 7
                    i32.and
                    i32.const 0
                    local.get 0
                    i32.const 7
                    i32.and
                    select
                    i32.add
                    local.tee 2
                    local.get 11
                    i32.sub
                    local.get 12
                    i32.sub
                    local.set 8
                    local.get 11
                    local.get 12
                    i32.const 3
                    i32.or
                    i32.store offset=4
                    local.get 2
                    local.get 4
                    i32.eq
                    if  ;; label = @9
                      i32.const 2256
                      i32.const 2256
                      i32.load
                      local.get 8
                      i32.add
                      local.tee 0
                      i32.store
                      i32.const 2268
                      local.get 7
                      i32.store
                      local.get 7
                      local.get 0
                      i32.const 1
                      i32.or
                      i32.store offset=4
                    else
                      block  ;; label = @10
                        i32.const 2264
                        i32.load
                        local.get 2
                        i32.eq
                        if  ;; label = @11
                          i32.const 2252
                          i32.const 2252
                          i32.load
                          local.get 8
                          i32.add
                          local.tee 0
                          i32.store
                          i32.const 2264
                          local.get 7
                          i32.store
                          local.get 7
                          local.get 0
                          i32.const 1
                          i32.or
                          i32.store offset=4
                          local.get 0
                          local.get 7
                          i32.add
                          local.get 0
                          i32.store
                          br 1 (;@10;)
                        end
                        local.get 2
                        i32.load offset=4
                        local.tee 0
                        i32.const 3
                        i32.and
                        i32.const 1
                        i32.eq
                        if  ;; label = @11
                          local.get 0
                          i32.const -8
                          i32.and
                          local.set 13
                          local.get 0
                          i32.const 3
                          i32.shr_u
                          local.set 1
                          block  ;; label = @12
                            local.get 0
                            i32.const 256
                            i32.lt_u
                            if  ;; label = @13
                              local.get 2
                              i32.load offset=12
                              local.set 9
                              local.get 2
                              i32.load offset=8
                              local.tee 3
                              local.get 1
                              i32.const 3
                              i32.shl
                              i32.const 2284
                              i32.add
                              local.tee 0
                              i32.ne
                              if  ;; label = @14
                                block  ;; label = @15
                                  local.get 6
                                  local.get 3
                                  i32.gt_u
                                  if  ;; label = @16
                                    call $_abort
                                  end
                                  local.get 3
                                  i32.load offset=12
                                  local.get 2
                                  i32.eq
                                  br_if 0 (;@15;)
                                  call $_abort
                                end
                              end
                              local.get 3
                              local.get 9
                              i32.eq
                              if  ;; label = @14
                                i32.const 2244
                                i32.const 2244
                                i32.load
                                i32.const 1
                                local.get 1
                                i32.shl
                                i32.const -1
                                i32.xor
                                i32.and
                                i32.store
                                br 2 (;@12;)
                              end
                              local.get 0
                              local.get 9
                              i32.eq
                              if  ;; label = @14
                                local.get 9
                                i32.const 8
                                i32.add
                                local.set 20
                              else
                                block  ;; label = @15
                                  local.get 6
                                  local.get 9
                                  i32.gt_u
                                  if  ;; label = @16
                                    call $_abort
                                  end
                                  local.get 9
                                  i32.const 8
                                  i32.add
                                  local.tee 0
                                  i32.load
                                  local.get 2
                                  i32.eq
                                  if  ;; label = @16
                                    local.get 0
                                    local.set 20
                                    br 1 (;@15;)
                                  end
                                  call $_abort
                                end
                              end
                              local.get 3
                              local.get 9
                              i32.store offset=12
                              local.get 20
                              local.get 3
                              i32.store
                            else
                              local.get 2
                              i32.load offset=24
                              local.set 10
                              local.get 2
                              i32.load offset=12
                              local.tee 0
                              local.get 2
                              i32.eq
                              if  ;; label = @14
                                block  ;; label = @15
                                  local.get 2
                                  i32.const 16
                                  i32.add
                                  local.tee 1
                                  i32.const 4
                                  i32.add
                                  local.tee 3
                                  i32.load
                                  local.tee 0
                                  if  ;; label = @16
                                    local.get 3
                                    local.set 1
                                  else
                                    local.get 1
                                    i32.load
                                    local.tee 0
                                    i32.eqz
                                    br_if 1 (;@15;)
                                  end
                                  loop  ;; label = @16
                                    block  ;; label = @17
                                      local.get 0
                                      i32.const 20
                                      i32.add
                                      local.tee 9
                                      i32.load
                                      local.tee 4
                                      i32.eqz
                                      if  ;; label = @18
                                        local.get 0
                                        i32.const 16
                                        i32.add
                                        local.tee 9
                                        i32.load
                                        local.tee 4
                                        i32.eqz
                                        br_if 1 (;@17;)
                                      end
                                      local.get 9
                                      local.set 1
                                      local.get 4
                                      local.set 0
                                      br 1 (;@16;)
                                    end
                                  end
                                  local.get 6
                                  local.get 1
                                  i32.gt_u
                                  if  ;; label = @16
                                    call $_abort
                                  else
                                    local.get 1
                                    i32.const 0
                                    i32.store
                                    local.get 0
                                    local.set 16
                                  end
                                end
                              else
                                local.get 6
                                local.get 2
                                i32.load offset=8
                                local.tee 1
                                i32.gt_u
                                if  ;; label = @15
                                  call $_abort
                                end
                                local.get 2
                                local.get 1
                                i32.load offset=12
                                i32.ne
                                if  ;; label = @15
                                  call $_abort
                                end
                                local.get 0
                                i32.load offset=8
                                local.get 2
                                i32.eq
                                if  ;; label = @15
                                  local.get 1
                                  local.get 0
                                  i32.store offset=12
                                  local.get 0
                                  local.get 1
                                  i32.store offset=8
                                  local.get 0
                                  local.set 16
                                else
                                  call $_abort
                                end
                              end
                              local.get 10
                              i32.eqz
                              br_if 1 (;@12;)
                              local.get 2
                              i32.load offset=28
                              local.tee 1
                              i32.const 2
                              i32.shl
                              i32.const 2548
                              i32.add
                              local.tee 0
                              i32.load
                              local.get 2
                              i32.eq
                              if  ;; label = @14
                                block  ;; label = @15
                                  local.get 0
                                  local.get 16
                                  i32.store
                                  local.get 16
                                  br_if 0 (;@15;)
                                  i32.const 2248
                                  i32.const 2248
                                  i32.load
                                  i32.const 1
                                  local.get 1
                                  i32.shl
                                  i32.const -1
                                  i32.xor
                                  i32.and
                                  i32.store
                                  br 3 (;@12;)
                                end
                              else
                                i32.const 2260
                                i32.load
                                local.get 10
                                i32.gt_u
                                if  ;; label = @15
                                  call $_abort
                                else
                                  local.get 10
                                  i32.const 16
                                  i32.add
                                  local.get 10
                                  i32.const 20
                                  i32.add
                                  local.get 10
                                  i32.load offset=16
                                  local.get 2
                                  i32.eq
                                  select
                                  local.get 16
                                  i32.store
                                  local.get 16
                                  i32.eqz
                                  br_if 3 (;@12;)
                                end
                              end
                              i32.const 2260
                              i32.load
                              local.tee 0
                              local.get 16
                              i32.gt_u
                              if  ;; label = @14
                                call $_abort
                              end
                              local.get 16
                              local.get 10
                              i32.store offset=24
                              local.get 2
                              i32.load offset=16
                              local.tee 1
                              if  ;; label = @14
                                local.get 0
                                local.get 1
                                i32.gt_u
                                if  ;; label = @15
                                  call $_abort
                                else
                                  local.get 16
                                  local.get 1
                                  i32.store offset=16
                                  local.get 1
                                  local.get 16
                                  i32.store offset=24
                                end
                              end
                              local.get 2
                              i32.load offset=20
                              local.tee 0
                              i32.eqz
                              br_if 1 (;@12;)
                              i32.const 2260
                              i32.load
                              local.get 0
                              i32.gt_u
                              if  ;; label = @14
                                call $_abort
                              else
                                local.get 16
                                local.get 0
                                i32.store offset=20
                                local.get 0
                                local.get 16
                                i32.store offset=24
                              end
                            end
                          end
                          local.get 2
                          local.get 13
                          i32.add
                          local.set 2
                          local.get 8
                          local.get 13
                          i32.add
                          local.set 8
                        end
                        local.get 2
                        local.get 2
                        i32.load offset=4
                        i32.const -2
                        i32.and
                        i32.store offset=4
                        local.get 7
                        local.get 8
                        i32.const 1
                        i32.or
                        i32.store offset=4
                        local.get 7
                        local.get 8
                        i32.add
                        local.get 8
                        i32.store
                        local.get 8
                        i32.const 3
                        i32.shr_u
                        local.set 0
                        local.get 8
                        i32.const 256
                        i32.lt_u
                        if  ;; label = @11
                          local.get 0
                          i32.const 3
                          i32.shl
                          i32.const 2284
                          i32.add
                          local.set 2
                          i32.const 2244
                          i32.load
                          local.tee 1
                          i32.const 1
                          local.get 0
                          i32.shl
                          local.tee 0
                          i32.and
                          if  ;; label = @12
                            block  ;; label = @13
                              i32.const 2260
                              i32.load
                              local.get 2
                              i32.const 8
                              i32.add
                              local.tee 1
                              i32.load
                              local.tee 0
                              i32.le_u
                              if  ;; label = @14
                                local.get 1
                                local.set 21
                                local.get 0
                                local.set 18
                                br 1 (;@13;)
                              end
                              call $_abort
                            end
                          else
                            i32.const 2244
                            local.get 0
                            local.get 1
                            i32.or
                            i32.store
                            local.get 2
                            i32.const 8
                            i32.add
                            local.set 21
                            local.get 2
                            local.set 18
                          end
                          local.get 21
                          local.get 7
                          i32.store
                          local.get 18
                          local.get 7
                          i32.store offset=12
                          local.get 7
                          local.get 18
                          i32.store offset=8
                          local.get 7
                          local.get 2
                          i32.store offset=12
                          br 1 (;@10;)
                        end
                        local.get 8
                        i32.const 8
                        i32.shr_u
                        local.tee 0
                        if (result i32)  ;; label = @11
                          local.get 8
                          i32.const 16777215
                          i32.gt_u
                          if (result i32)  ;; label = @12
                            i32.const 31
                          else
                            local.get 0
                            local.get 0
                            i32.const 1048320
                            i32.add
                            i32.const 16
                            i32.shr_u
                            i32.const 8
                            i32.and
                            local.tee 2
                            i32.shl
                            local.tee 0
                            i32.const 520192
                            i32.add
                            i32.const 16
                            i32.shr_u
                            i32.const 4
                            i32.and
                            local.set 3
                            local.get 8
                            i32.const 14
                            local.get 0
                            local.get 3
                            i32.shl
                            local.tee 1
                            i32.const 245760
                            i32.add
                            i32.const 16
                            i32.shr_u
                            i32.const 2
                            i32.and
                            local.tee 0
                            local.get 2
                            local.get 3
                            i32.or
                            i32.or
                            i32.sub
                            local.get 1
                            local.get 0
                            i32.shl
                            i32.const 15
                            i32.shr_u
                            i32.add
                            local.tee 0
                            i32.const 7
                            i32.add
                            i32.shr_u
                            i32.const 1
                            i32.and
                            local.get 0
                            i32.const 1
                            i32.shl
                            i32.or
                          end
                        else
                          i32.const 0
                        end
                        local.tee 3
                        i32.const 2
                        i32.shl
                        i32.const 2548
                        i32.add
                        local.set 2
                        local.get 7
                        local.get 3
                        i32.store offset=28
                        local.get 7
                        i32.const 0
                        i32.store offset=20
                        local.get 7
                        i32.const 0
                        i32.store offset=16
                        i32.const 2248
                        i32.load
                        local.tee 1
                        i32.const 1
                        local.get 3
                        i32.shl
                        local.tee 0
                        i32.and
                        i32.eqz
                        if  ;; label = @11
                          i32.const 2248
                          local.get 0
                          local.get 1
                          i32.or
                          i32.store
                          local.get 2
                          local.get 7
                          i32.store
                          local.get 7
                          local.get 2
                          i32.store offset=24
                          local.get 7
                          local.get 7
                          i32.store offset=12
                          local.get 7
                          local.get 7
                          i32.store offset=8
                          br 1 (;@10;)
                        end
                        local.get 2
                        i32.load
                        local.tee 0
                        i32.load offset=4
                        i32.const -8
                        i32.and
                        local.get 8
                        i32.eq
                        if  ;; label = @11
                          local.get 0
                          local.set 5
                        else
                          block  ;; label = @12
                            local.get 8
                            i32.const 0
                            i32.const 25
                            local.get 3
                            i32.const 1
                            i32.shr_u
                            i32.sub
                            local.get 3
                            i32.const 31
                            i32.eq
                            select
                            i32.shl
                            local.set 2
                            loop  ;; label = @13
                              local.get 0
                              i32.const 16
                              i32.add
                              local.get 2
                              i32.const 31
                              i32.shr_u
                              i32.const 2
                              i32.shl
                              i32.add
                              local.tee 3
                              i32.load
                              local.tee 1
                              if  ;; label = @14
                                local.get 2
                                i32.const 1
                                i32.shl
                                local.set 2
                                local.get 1
                                i32.load offset=4
                                i32.const -8
                                i32.and
                                local.get 8
                                i32.eq
                                if  ;; label = @15
                                  local.get 1
                                  local.set 5
                                  br 3 (;@12;)
                                else
                                  local.get 1
                                  local.set 0
                                  br 2 (;@13;)
                                end
                                unreachable
                              end
                            end
                            i32.const 2260
                            i32.load
                            local.get 3
                            i32.gt_u
                            if  ;; label = @13
                              call $_abort
                            else
                              local.get 3
                              local.get 7
                              i32.store
                              local.get 7
                              local.get 0
                              i32.store offset=24
                              local.get 7
                              local.get 7
                              i32.store offset=12
                              local.get 7
                              local.get 7
                              i32.store offset=8
                              br 3 (;@10;)
                            end
                          end
                        end
                        i32.const 2260
                        i32.load
                        local.tee 0
                        local.get 5
                        i32.le_u
                        local.get 0
                        local.get 5
                        local.tee 0
                        i32.load offset=8
                        local.tee 1
                        i32.le_u
                        i32.and
                        if  ;; label = @11
                          local.get 1
                          local.get 7
                          i32.store offset=12
                          local.get 0
                          local.get 7
                          i32.store offset=8
                          local.get 7
                          local.get 1
                          i32.store offset=8
                          local.get 7
                          local.get 5
                          i32.store offset=12
                          local.get 7
                          i32.const 0
                          i32.store offset=24
                        else
                          call $_abort
                        end
                      end
                    end
                    local.get 17
                    global.set 2
                    local.get 11
                    i32.const 8
                    i32.add
                    return
                  end
                end
                i32.const 2692
                local.set 6
                loop  ;; label = @7
                  block  ;; label = @8
                    local.get 6
                    i32.load
                    local.tee 1
                    local.get 4
                    i32.le_u
                    if  ;; label = @9
                      local.get 1
                      local.get 6
                      i32.load offset=4
                      i32.add
                      local.tee 9
                      local.get 4
                      i32.gt_u
                      br_if 1 (;@8;)
                    end
                    local.get 6
                    i32.load offset=8
                    local.set 6
                    br 1 (;@7;)
                  end
                end
                i32.const 2268
                i32.const 0
                local.get 0
                i32.const 8
                i32.add
                local.tee 1
                i32.sub
                i32.const 7
                i32.and
                i32.const 0
                local.get 1
                i32.const 7
                i32.and
                select
                local.tee 1
                local.get 0
                i32.add
                local.tee 5
                i32.store
                i32.const 2256
                local.get 2
                i32.const -40
                i32.add
                local.tee 3
                local.get 1
                i32.sub
                local.tee 1
                i32.store
                local.get 5
                local.get 1
                i32.const 1
                i32.or
                i32.store offset=4
                local.get 0
                local.get 3
                i32.add
                i32.const 40
                i32.store offset=4
                i32.const 2272
                i32.const 2732
                i32.load
                i32.store
                local.get 4
                i32.const 0
                local.get 9
                i32.const -47
                i32.add
                local.tee 3
                i32.const 8
                i32.add
                local.tee 1
                i32.sub
                i32.const 7
                i32.and
                i32.const 0
                local.get 1
                i32.const 7
                i32.and
                select
                local.get 3
                i32.add
                local.tee 1
                local.get 1
                local.get 4
                i32.const 16
                i32.add
                i32.lt_u
                select
                local.tee 3
                i32.const 27
                i32.store offset=4
                local.get 3
                i32.const 2692
                i64.load align=4
                i64.store offset=8 align=4
                local.get 3
                i32.const 2700
                i64.load align=4
                i64.store offset=16 align=4
                i32.const 2692
                local.get 0
                i32.store
                i32.const 2696
                local.get 2
                i32.store
                i32.const 2704
                i32.const 0
                i32.store
                i32.const 2700
                local.get 3
                i32.const 8
                i32.add
                i32.store
                local.get 3
                i32.const 24
                i32.add
                local.set 0
                loop  ;; label = @7
                  local.get 0
                  i32.const 4
                  i32.add
                  local.tee 1
                  i32.const 7
                  i32.store
                  local.get 0
                  i32.const 8
                  i32.add
                  local.get 9
                  i32.lt_u
                  if  ;; label = @8
                    local.get 1
                    local.set 0
                    br 1 (;@7;)
                  end
                end
                local.get 3
                local.get 4
                i32.ne
                if  ;; label = @7
                  local.get 3
                  local.get 3
                  i32.load offset=4
                  i32.const -2
                  i32.and
                  i32.store offset=4
                  local.get 4
                  local.get 3
                  local.get 4
                  i32.sub
                  local.tee 5
                  i32.const 1
                  i32.or
                  i32.store offset=4
                  local.get 3
                  local.get 5
                  i32.store
                  local.get 5
                  i32.const 3
                  i32.shr_u
                  local.set 0
                  local.get 5
                  i32.const 256
                  i32.lt_u
                  if  ;; label = @8
                    local.get 0
                    i32.const 3
                    i32.shl
                    i32.const 2284
                    i32.add
                    local.set 2
                    i32.const 2244
                    i32.load
                    local.tee 1
                    i32.const 1
                    local.get 0
                    i32.shl
                    local.tee 0
                    i32.and
                    if  ;; label = @9
                      i32.const 2260
                      i32.load
                      local.get 2
                      i32.const 8
                      i32.add
                      local.tee 1
                      i32.load
                      local.tee 0
                      i32.gt_u
                      if  ;; label = @10
                        call $_abort
                      else
                        local.get 1
                        local.set 22
                        local.get 0
                        local.set 10
                      end
                    else
                      i32.const 2244
                      local.get 0
                      local.get 1
                      i32.or
                      i32.store
                      local.get 2
                      i32.const 8
                      i32.add
                      local.set 22
                      local.get 2
                      local.set 10
                    end
                    local.get 22
                    local.get 4
                    i32.store
                    local.get 10
                    local.get 4
                    i32.store offset=12
                    local.get 4
                    local.get 10
                    i32.store offset=8
                    local.get 4
                    local.get 2
                    i32.store offset=12
                    br 2 (;@6;)
                  end
                  local.get 5
                  i32.const 8
                  i32.shr_u
                  local.tee 0
                  if (result i32)  ;; label = @8
                    local.get 5
                    i32.const 16777215
                    i32.gt_u
                    if (result i32)  ;; label = @9
                      i32.const 31
                    else
                      local.get 0
                      local.get 0
                      i32.const 1048320
                      i32.add
                      i32.const 16
                      i32.shr_u
                      i32.const 8
                      i32.and
                      local.tee 2
                      i32.shl
                      local.tee 0
                      i32.const 520192
                      i32.add
                      i32.const 16
                      i32.shr_u
                      i32.const 4
                      i32.and
                      local.set 3
                      local.get 5
                      i32.const 14
                      local.get 0
                      local.get 3
                      i32.shl
                      local.tee 1
                      i32.const 245760
                      i32.add
                      i32.const 16
                      i32.shr_u
                      i32.const 2
                      i32.and
                      local.tee 0
                      local.get 2
                      local.get 3
                      i32.or
                      i32.or
                      i32.sub
                      local.get 1
                      local.get 0
                      i32.shl
                      i32.const 15
                      i32.shr_u
                      i32.add
                      local.tee 0
                      i32.const 7
                      i32.add
                      i32.shr_u
                      i32.const 1
                      i32.and
                      local.get 0
                      i32.const 1
                      i32.shl
                      i32.or
                    end
                  else
                    i32.const 0
                  end
                  local.tee 3
                  i32.const 2
                  i32.shl
                  i32.const 2548
                  i32.add
                  local.set 2
                  local.get 4
                  local.get 3
                  i32.store offset=28
                  local.get 4
                  i32.const 0
                  i32.store offset=20
                  local.get 4
                  i32.const 0
                  i32.store offset=16
                  i32.const 2248
                  i32.load
                  local.tee 1
                  i32.const 1
                  local.get 3
                  i32.shl
                  local.tee 0
                  i32.and
                  i32.eqz
                  if  ;; label = @8
                    i32.const 2248
                    local.get 0
                    local.get 1
                    i32.or
                    i32.store
                    local.get 2
                    local.get 4
                    i32.store
                    local.get 4
                    local.get 2
                    i32.store offset=24
                    local.get 4
                    local.get 4
                    i32.store offset=12
                    local.get 4
                    local.get 4
                    i32.store offset=8
                    br 2 (;@6;)
                  end
                  local.get 2
                  i32.load
                  local.tee 0
                  i32.load offset=4
                  i32.const -8
                  i32.and
                  local.get 5
                  i32.eq
                  if  ;; label = @8
                    local.get 0
                    local.set 8
                  else
                    block  ;; label = @9
                      local.get 5
                      i32.const 0
                      i32.const 25
                      local.get 3
                      i32.const 1
                      i32.shr_u
                      i32.sub
                      local.get 3
                      i32.const 31
                      i32.eq
                      select
                      i32.shl
                      local.set 6
                      loop  ;; label = @10
                        local.get 0
                        i32.const 16
                        i32.add
                        local.get 6
                        i32.const 31
                        i32.shr_u
                        i32.const 2
                        i32.shl
                        i32.add
                        local.tee 2
                        i32.load
                        local.tee 1
                        if  ;; label = @11
                          local.get 6
                          i32.const 1
                          i32.shl
                          local.set 6
                          local.get 1
                          i32.load offset=4
                          i32.const -8
                          i32.and
                          local.get 5
                          i32.eq
                          if  ;; label = @12
                            local.get 1
                            local.set 8
                            br 3 (;@9;)
                          else
                            local.get 1
                            local.set 0
                            br 2 (;@10;)
                          end
                          unreachable
                        end
                      end
                      i32.const 2260
                      i32.load
                      local.get 2
                      i32.gt_u
                      if  ;; label = @10
                        call $_abort
                      else
                        local.get 2
                        local.get 4
                        i32.store
                        local.get 4
                        local.get 0
                        i32.store offset=24
                        local.get 4
                        local.get 4
                        i32.store offset=12
                        local.get 4
                        local.get 4
                        i32.store offset=8
                        br 4 (;@6;)
                      end
                    end
                  end
                  i32.const 2260
                  i32.load
                  local.tee 0
                  local.get 8
                  i32.le_u
                  local.get 0
                  local.get 8
                  local.tee 0
                  i32.load offset=8
                  local.tee 1
                  i32.le_u
                  i32.and
                  if  ;; label = @8
                    local.get 1
                    local.get 4
                    i32.store offset=12
                    local.get 0
                    local.get 4
                    i32.store offset=8
                    local.get 4
                    local.get 1
                    i32.store offset=8
                    local.get 4
                    local.get 8
                    i32.store offset=12
                    local.get 4
                    i32.const 0
                    i32.store offset=24
                  else
                    call $_abort
                  end
                end
              end
            else
              i32.const 2260
              i32.load
              local.tee 1
              i32.eqz
              local.get 0
              local.get 1
              i32.lt_u
              i32.or
              if  ;; label = @6
                i32.const 2260
                local.get 0
                i32.store
              end
              i32.const 2692
              local.get 0
              i32.store
              i32.const 2696
              local.get 2
              i32.store
              i32.const 2704
              i32.const 0
              i32.store
              i32.const 2280
              i32.const 2716
              i32.load
              i32.store
              i32.const 2276
              i32.const -1
              i32.store
              i32.const 2296
              i32.const 2284
              i32.store
              i32.const 2292
              i32.const 2284
              i32.store
              i32.const 2304
              i32.const 2292
              i32.store
              i32.const 2300
              i32.const 2292
              i32.store
              i32.const 2312
              i32.const 2300
              i32.store
              i32.const 2308
              i32.const 2300
              i32.store
              i32.const 2320
              i32.const 2308
              i32.store
              i32.const 2316
              i32.const 2308
              i32.store
              i32.const 2328
              i32.const 2316
              i32.store
              i32.const 2324
              i32.const 2316
              i32.store
              i32.const 2336
              i32.const 2324
              i32.store
              i32.const 2332
              i32.const 2324
              i32.store
              i32.const 2344
              i32.const 2332
              i32.store
              i32.const 2340
              i32.const 2332
              i32.store
              i32.const 2352
              i32.const 2340
              i32.store
              i32.const 2348
              i32.const 2340
              i32.store
              i32.const 2360
              i32.const 2348
              i32.store
              i32.const 2356
              i32.const 2348
              i32.store
              i32.const 2368
              i32.const 2356
              i32.store
              i32.const 2364
              i32.const 2356
              i32.store
              i32.const 2376
              i32.const 2364
              i32.store
              i32.const 2372
              i32.const 2364
              i32.store
              i32.const 2384
              i32.const 2372
              i32.store
              i32.const 2380
              i32.const 2372
              i32.store
              i32.const 2392
              i32.const 2380
              i32.store
              i32.const 2388
              i32.const 2380
              i32.store
              i32.const 2400
              i32.const 2388
              i32.store
              i32.const 2396
              i32.const 2388
              i32.store
              i32.const 2408
              i32.const 2396
              i32.store
              i32.const 2404
              i32.const 2396
              i32.store
              i32.const 2416
              i32.const 2404
              i32.store
              i32.const 2412
              i32.const 2404
              i32.store
              i32.const 2424
              i32.const 2412
              i32.store
              i32.const 2420
              i32.const 2412
              i32.store
              i32.const 2432
              i32.const 2420
              i32.store
              i32.const 2428
              i32.const 2420
              i32.store
              i32.const 2440
              i32.const 2428
              i32.store
              i32.const 2436
              i32.const 2428
              i32.store
              i32.const 2448
              i32.const 2436
              i32.store
              i32.const 2444
              i32.const 2436
              i32.store
              i32.const 2456
              i32.const 2444
              i32.store
              i32.const 2452
              i32.const 2444
              i32.store
              i32.const 2464
              i32.const 2452
              i32.store
              i32.const 2460
              i32.const 2452
              i32.store
              i32.const 2472
              i32.const 2460
              i32.store
              i32.const 2468
              i32.const 2460
              i32.store
              i32.const 2480
              i32.const 2468
              i32.store
              i32.const 2476
              i32.const 2468
              i32.store
              i32.const 2488
              i32.const 2476
              i32.store
              i32.const 2484
              i32.const 2476
              i32.store
              i32.const 2496
              i32.const 2484
              i32.store
              i32.const 2492
              i32.const 2484
              i32.store
              i32.const 2504
              i32.const 2492
              i32.store
              i32.const 2500
              i32.const 2492
              i32.store
              i32.const 2512
              i32.const 2500
              i32.store
              i32.const 2508
              i32.const 2500
              i32.store
              i32.const 2520
              i32.const 2508
              i32.store
              i32.const 2516
              i32.const 2508
              i32.store
              i32.const 2528
              i32.const 2516
              i32.store
              i32.const 2524
              i32.const 2516
              i32.store
              i32.const 2536
              i32.const 2524
              i32.store
              i32.const 2532
              i32.const 2524
              i32.store
              i32.const 2544
              i32.const 2532
              i32.store
              i32.const 2540
              i32.const 2532
              i32.store
              i32.const 2268
              i32.const 0
              local.get 0
              i32.const 8
              i32.add
              local.tee 1
              i32.sub
              i32.const 7
              i32.and
              i32.const 0
              local.get 1
              i32.const 7
              i32.and
              select
              local.tee 1
              local.get 0
              i32.add
              local.tee 3
              i32.store
              i32.const 2256
              local.get 2
              i32.const -40
              i32.add
              local.tee 2
              local.get 1
              i32.sub
              local.tee 1
              i32.store
              local.get 3
              local.get 1
              i32.const 1
              i32.or
              i32.store offset=4
              local.get 0
              local.get 2
              i32.add
              i32.const 40
              i32.store offset=4
              i32.const 2272
              i32.const 2732
              i32.load
              i32.store
            end
            i32.const 2256
            i32.load
            local.tee 0
            local.get 12
            i32.gt_u
            if  ;; label = @5
              i32.const 2256
              local.get 0
              local.get 12
              i32.sub
              local.tee 2
              i32.store
              br 2 (;@3;)
            end
          end
          i32.const 2240
          i32.const 12
          i32.store
          br 2 (;@1;)
        end
        i32.const 2268
        i32.const 2268
        i32.load
        local.tee 0
        local.get 12
        i32.add
        local.tee 1
        i32.store
        local.get 1
        local.get 2
        i32.const 1
        i32.or
        i32.store offset=4
        local.get 0
        local.get 12
        i32.const 3
        i32.or
        i32.store offset=4
      end
      local.get 17
      global.set 2
      local.get 0
      i32.const 8
      i32.add
      return
    end
    local.get 17
    global.set 2
    i32.const 0)
  (func $_free (type 3) (param i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32)
    local.get 0
    i32.eqz
    if  ;; label = @1
      return
    end
    local.get 0
    i32.const -8
    i32.add
    local.tee 5
    i32.const 2260
    i32.load
    local.tee 11
    i32.lt_u
    if  ;; label = @1
      call $_abort
    end
    local.get 0
    i32.const -4
    i32.add
    i32.load
    local.tee 0
    i32.const 3
    i32.and
    local.tee 12
    i32.const 1
    i32.eq
    if  ;; label = @1
      call $_abort
    end
    local.get 5
    local.get 0
    i32.const -8
    i32.and
    local.tee 2
    i32.add
    local.set 7
    local.get 0
    i32.const 1
    i32.and
    if  ;; label = @1
      local.get 5
      local.tee 4
      local.set 3
      local.get 2
      local.set 1
    else
      block  ;; label = @2
        local.get 5
        i32.load
        local.set 10
        local.get 12
        i32.eqz
        if  ;; label = @3
          return
        end
        local.get 5
        local.get 10
        i32.sub
        local.tee 0
        local.get 11
        i32.lt_u
        if  ;; label = @3
          call $_abort
        end
        local.get 2
        local.get 10
        i32.add
        local.set 5
        i32.const 2264
        i32.load
        local.get 0
        i32.eq
        if  ;; label = @3
          local.get 7
          i32.load offset=4
          local.tee 1
          i32.const 3
          i32.and
          i32.const 3
          i32.ne
          if  ;; label = @4
            local.get 0
            local.set 4
            local.get 0
            local.set 3
            local.get 5
            local.set 1
            br 2 (;@2;)
          end
          i32.const 2252
          local.get 5
          i32.store
          local.get 7
          local.get 1
          i32.const -2
          i32.and
          i32.store offset=4
          local.get 0
          local.get 5
          i32.const 1
          i32.or
          i32.store offset=4
          local.get 0
          local.get 5
          i32.add
          local.get 5
          i32.store
          return
        end
        local.get 10
        i32.const 3
        i32.shr_u
        local.set 2
        local.get 10
        i32.const 256
        i32.lt_u
        if  ;; label = @3
          local.get 0
          i32.load offset=12
          local.set 4
          local.get 0
          i32.load offset=8
          local.tee 3
          local.get 2
          i32.const 3
          i32.shl
          i32.const 2284
          i32.add
          local.tee 1
          i32.ne
          if  ;; label = @4
            local.get 11
            local.get 3
            i32.gt_u
            if  ;; label = @5
              call $_abort
            end
            local.get 0
            local.get 3
            i32.load offset=12
            i32.ne
            if  ;; label = @5
              call $_abort
            end
          end
          local.get 3
          local.get 4
          i32.eq
          if  ;; label = @4
            i32.const 2244
            i32.const 2244
            i32.load
            i32.const 1
            local.get 2
            i32.shl
            i32.const -1
            i32.xor
            i32.and
            i32.store
            local.get 0
            local.set 4
            local.get 0
            local.set 3
            local.get 5
            local.set 1
            br 2 (;@2;)
          end
          local.get 1
          local.get 4
          i32.eq
          if  ;; label = @4
            local.get 4
            i32.const 8
            i32.add
            local.set 6
          else
            local.get 11
            local.get 4
            i32.gt_u
            if  ;; label = @5
              call $_abort
            end
            local.get 4
            i32.const 8
            i32.add
            local.tee 1
            i32.load
            local.get 0
            i32.eq
            if  ;; label = @5
              local.get 1
              local.set 6
            else
              call $_abort
            end
          end
          local.get 3
          local.get 4
          i32.store offset=12
          local.get 6
          local.get 3
          i32.store
          local.get 0
          local.set 4
          local.get 0
          local.set 3
          local.get 5
          local.set 1
          br 1 (;@2;)
        end
        local.get 0
        i32.load offset=24
        local.set 13
        local.get 0
        i32.load offset=12
        local.tee 2
        local.get 0
        i32.eq
        if  ;; label = @3
          block  ;; label = @4
            local.get 0
            i32.const 16
            i32.add
            local.tee 6
            i32.const 4
            i32.add
            local.tee 10
            i32.load
            local.tee 2
            if  ;; label = @5
              local.get 10
              local.set 6
            else
              local.get 6
              i32.load
              local.tee 2
              i32.eqz
              br_if 1 (;@4;)
            end
            loop  ;; label = @5
              block  ;; label = @6
                local.get 2
                i32.const 20
                i32.add
                local.tee 10
                i32.load
                local.tee 12
                i32.eqz
                if  ;; label = @7
                  local.get 2
                  i32.const 16
                  i32.add
                  local.tee 10
                  i32.load
                  local.tee 12
                  i32.eqz
                  br_if 1 (;@6;)
                end
                local.get 10
                local.set 6
                local.get 12
                local.set 2
                br 1 (;@5;)
              end
            end
            local.get 11
            local.get 6
            i32.gt_u
            if  ;; label = @5
              call $_abort
            else
              local.get 6
              i32.const 0
              i32.store
              local.get 2
              local.set 8
            end
          end
        else
          local.get 11
          local.get 0
          i32.load offset=8
          local.tee 6
          i32.gt_u
          if  ;; label = @4
            call $_abort
          end
          local.get 0
          local.get 6
          i32.load offset=12
          i32.ne
          if  ;; label = @4
            call $_abort
          end
          local.get 2
          i32.load offset=8
          local.get 0
          i32.eq
          if  ;; label = @4
            local.get 6
            local.get 2
            i32.store offset=12
            local.get 2
            local.get 6
            i32.store offset=8
            local.get 2
            local.set 8
          else
            call $_abort
          end
        end
        local.get 13
        if  ;; label = @3
          local.get 0
          i32.load offset=28
          local.tee 2
          i32.const 2
          i32.shl
          i32.const 2548
          i32.add
          local.tee 6
          i32.load
          local.get 0
          i32.eq
          if  ;; label = @4
            local.get 6
            local.get 8
            i32.store
            local.get 8
            i32.eqz
            if  ;; label = @5
              i32.const 2248
              i32.const 2248
              i32.load
              i32.const 1
              local.get 2
              i32.shl
              i32.const -1
              i32.xor
              i32.and
              i32.store
              local.get 0
              local.set 4
              local.get 0
              local.set 3
              local.get 5
              local.set 1
              br 3 (;@2;)
            end
          else
            i32.const 2260
            i32.load
            local.get 13
            i32.gt_u
            if  ;; label = @5
              call $_abort
            else
              local.get 13
              i32.const 16
              i32.add
              local.tee 2
              local.get 13
              i32.const 20
              i32.add
              local.get 2
              i32.load
              local.get 0
              i32.eq
              select
              local.get 8
              i32.store
              local.get 8
              i32.eqz
              if  ;; label = @6
                local.get 0
                local.set 4
                local.get 0
                local.set 3
                local.get 5
                local.set 1
                br 4 (;@2;)
              end
            end
          end
          i32.const 2260
          i32.load
          local.tee 6
          local.get 8
          i32.gt_u
          if  ;; label = @4
            call $_abort
          end
          local.get 8
          local.get 13
          i32.store offset=24
          local.get 0
          i32.load offset=16
          local.tee 2
          if  ;; label = @4
            local.get 6
            local.get 2
            i32.gt_u
            if  ;; label = @5
              call $_abort
            else
              local.get 8
              local.get 2
              i32.store offset=16
              local.get 2
              local.get 8
              i32.store offset=24
            end
          end
          local.get 0
          i32.load offset=20
          local.tee 2
          if  ;; label = @4
            i32.const 2260
            i32.load
            local.get 2
            i32.gt_u
            if  ;; label = @5
              call $_abort
            else
              local.get 8
              local.get 2
              i32.store offset=20
              local.get 2
              local.get 8
              i32.store offset=24
              local.get 0
              local.set 4
              local.get 0
              local.set 3
              local.get 5
              local.set 1
            end
          else
            local.get 0
            local.set 4
            local.get 0
            local.set 3
            local.get 5
            local.set 1
          end
        else
          local.get 0
          local.set 4
          local.get 0
          local.set 3
          local.get 5
          local.set 1
        end
      end
    end
    local.get 4
    local.get 7
    i32.ge_u
    if  ;; label = @1
      call $_abort
    end
    local.get 7
    i32.load offset=4
    local.tee 0
    i32.const 1
    i32.and
    i32.eqz
    if  ;; label = @1
      call $_abort
    end
    local.get 0
    i32.const 2
    i32.and
    if (result i32)  ;; label = @1
      local.get 7
      local.get 0
      i32.const -2
      i32.and
      i32.store offset=4
      local.get 3
      local.get 1
      i32.const 1
      i32.or
      i32.store offset=4
      local.get 1
      local.get 4
      i32.add
      local.get 1
      i32.store
      local.get 1
    else
      i32.const 2268
      i32.load
      local.get 7
      i32.eq
      if  ;; label = @2
        i32.const 2256
        i32.const 2256
        i32.load
        local.get 1
        i32.add
        local.tee 0
        i32.store
        i32.const 2268
        local.get 3
        i32.store
        local.get 3
        local.get 0
        i32.const 1
        i32.or
        i32.store offset=4
        local.get 3
        i32.const 2264
        i32.load
        i32.ne
        if  ;; label = @3
          return
        end
        i32.const 2264
        i32.const 0
        i32.store
        i32.const 2252
        i32.const 0
        i32.store
        return
      end
      i32.const 2264
      i32.load
      local.get 7
      i32.eq
      if  ;; label = @2
        i32.const 2252
        i32.const 2252
        i32.load
        local.get 1
        i32.add
        local.tee 0
        i32.store
        i32.const 2264
        local.get 4
        i32.store
        local.get 3
        local.get 0
        i32.const 1
        i32.or
        i32.store offset=4
        local.get 0
        local.get 4
        i32.add
        local.get 0
        i32.store
        return
      end
      local.get 0
      i32.const -8
      i32.and
      local.get 1
      i32.add
      local.set 5
      local.get 0
      i32.const 3
      i32.shr_u
      local.set 6
      block  ;; label = @2
        local.get 0
        i32.const 256
        i32.lt_u
        if  ;; label = @3
          local.get 7
          i32.load offset=12
          local.set 1
          local.get 7
          i32.load offset=8
          local.tee 2
          local.get 6
          i32.const 3
          i32.shl
          i32.const 2284
          i32.add
          local.tee 0
          i32.ne
          if  ;; label = @4
            i32.const 2260
            i32.load
            local.get 2
            i32.gt_u
            if  ;; label = @5
              call $_abort
            end
            local.get 7
            local.get 2
            i32.load offset=12
            i32.ne
            if  ;; label = @5
              call $_abort
            end
          end
          local.get 1
          local.get 2
          i32.eq
          if  ;; label = @4
            i32.const 2244
            i32.const 2244
            i32.load
            i32.const 1
            local.get 6
            i32.shl
            i32.const -1
            i32.xor
            i32.and
            i32.store
            br 2 (;@2;)
          end
          local.get 0
          local.get 1
          i32.eq
          if  ;; label = @4
            local.get 1
            i32.const 8
            i32.add
            local.set 16
          else
            i32.const 2260
            i32.load
            local.get 1
            i32.gt_u
            if  ;; label = @5
              call $_abort
            end
            local.get 1
            i32.const 8
            i32.add
            local.tee 0
            i32.load
            local.get 7
            i32.eq
            if  ;; label = @5
              local.get 0
              local.set 16
            else
              call $_abort
            end
          end
          local.get 2
          local.get 1
          i32.store offset=12
          local.get 16
          local.get 2
          i32.store
        else
          local.get 7
          i32.load offset=24
          local.set 8
          local.get 7
          i32.load offset=12
          local.tee 0
          local.get 7
          i32.eq
          if  ;; label = @4
            block  ;; label = @5
              local.get 7
              i32.const 16
              i32.add
              local.tee 1
              i32.const 4
              i32.add
              local.tee 2
              i32.load
              local.tee 0
              if  ;; label = @6
                local.get 2
                local.set 1
              else
                local.get 1
                i32.load
                local.tee 0
                i32.eqz
                br_if 1 (;@5;)
              end
              loop  ;; label = @6
                block  ;; label = @7
                  local.get 0
                  i32.const 20
                  i32.add
                  local.tee 2
                  i32.load
                  local.tee 6
                  i32.eqz
                  if  ;; label = @8
                    local.get 0
                    i32.const 16
                    i32.add
                    local.tee 2
                    i32.load
                    local.tee 6
                    i32.eqz
                    br_if 1 (;@7;)
                  end
                  local.get 2
                  local.set 1
                  local.get 6
                  local.set 0
                  br 1 (;@6;)
                end
              end
              i32.const 2260
              i32.load
              local.get 1
              i32.gt_u
              if  ;; label = @6
                call $_abort
              else
                local.get 1
                i32.const 0
                i32.store
                local.get 0
                local.set 9
              end
            end
          else
            i32.const 2260
            i32.load
            local.get 7
            i32.load offset=8
            local.tee 1
            i32.gt_u
            if  ;; label = @5
              call $_abort
            end
            local.get 7
            local.get 1
            i32.load offset=12
            i32.ne
            if  ;; label = @5
              call $_abort
            end
            local.get 0
            i32.load offset=8
            local.get 7
            i32.eq
            if  ;; label = @5
              local.get 1
              local.get 0
              i32.store offset=12
              local.get 0
              local.get 1
              i32.store offset=8
              local.get 0
              local.set 9
            else
              call $_abort
            end
          end
          local.get 8
          if  ;; label = @4
            local.get 7
            i32.load offset=28
            local.tee 0
            i32.const 2
            i32.shl
            i32.const 2548
            i32.add
            local.tee 1
            i32.load
            local.get 7
            i32.eq
            if  ;; label = @5
              local.get 1
              local.get 9
              i32.store
              local.get 9
              i32.eqz
              if  ;; label = @6
                i32.const 2248
                i32.const 2248
                i32.load
                i32.const 1
                local.get 0
                i32.shl
                i32.const -1
                i32.xor
                i32.and
                i32.store
                br 4 (;@2;)
              end
            else
              i32.const 2260
              i32.load
              local.get 8
              i32.gt_u
              if  ;; label = @6
                call $_abort
              else
                local.get 8
                i32.const 16
                i32.add
                local.tee 0
                local.get 8
                i32.const 20
                i32.add
                local.get 0
                i32.load
                local.get 7
                i32.eq
                select
                local.get 9
                i32.store
                local.get 9
                i32.eqz
                br_if 4 (;@2;)
              end
            end
            i32.const 2260
            i32.load
            local.tee 1
            local.get 9
            i32.gt_u
            if  ;; label = @5
              call $_abort
            end
            local.get 9
            local.get 8
            i32.store offset=24
            local.get 7
            i32.load offset=16
            local.tee 0
            if  ;; label = @5
              local.get 1
              local.get 0
              i32.gt_u
              if  ;; label = @6
                call $_abort
              else
                local.get 9
                local.get 0
                i32.store offset=16
                local.get 0
                local.get 9
                i32.store offset=24
              end
            end
            local.get 7
            i32.load offset=20
            local.tee 0
            if  ;; label = @5
              i32.const 2260
              i32.load
              local.get 0
              i32.gt_u
              if  ;; label = @6
                call $_abort
              else
                local.get 9
                local.get 0
                i32.store offset=20
                local.get 0
                local.get 9
                i32.store offset=24
              end
            end
          end
        end
      end
      local.get 3
      local.get 5
      i32.const 1
      i32.or
      i32.store offset=4
      local.get 4
      local.get 5
      i32.add
      local.get 5
      i32.store
      i32.const 2264
      i32.load
      local.get 3
      i32.eq
      if (result i32)  ;; label = @2
        i32.const 2252
        local.get 5
        i32.store
        return
      else
        local.get 5
      end
    end
    local.tee 4
    i32.const 3
    i32.shr_u
    local.set 1
    local.get 4
    i32.const 256
    i32.lt_u
    if  ;; label = @1
      local.get 1
      i32.const 3
      i32.shl
      i32.const 2284
      i32.add
      local.set 0
      i32.const 2244
      i32.load
      local.tee 4
      i32.const 1
      local.get 1
      i32.shl
      local.tee 1
      i32.and
      if  ;; label = @2
        i32.const 2260
        i32.load
        local.get 0
        i32.const 8
        i32.add
        local.tee 1
        i32.load
        local.tee 4
        i32.gt_u
        if  ;; label = @3
          call $_abort
        else
          local.get 1
          local.set 17
          local.get 4
          local.set 15
        end
      else
        i32.const 2244
        local.get 1
        local.get 4
        i32.or
        i32.store
        local.get 0
        i32.const 8
        i32.add
        local.set 17
        local.get 0
        local.set 15
      end
      local.get 17
      local.get 3
      i32.store
      local.get 15
      local.get 3
      i32.store offset=12
      local.get 3
      local.get 15
      i32.store offset=8
      local.get 3
      local.get 0
      i32.store offset=12
      return
    end
    local.get 4
    i32.const 8
    i32.shr_u
    local.tee 0
    if (result i32)  ;; label = @1
      local.get 4
      i32.const 16777215
      i32.gt_u
      if (result i32)  ;; label = @2
        i32.const 31
      else
        local.get 0
        local.get 0
        i32.const 1048320
        i32.add
        i32.const 16
        i32.shr_u
        i32.const 8
        i32.and
        local.tee 5
        i32.shl
        local.tee 1
        i32.const 520192
        i32.add
        i32.const 16
        i32.shr_u
        i32.const 4
        i32.and
        local.set 0
        local.get 1
        local.get 0
        i32.shl
        local.tee 2
        i32.const 245760
        i32.add
        i32.const 16
        i32.shr_u
        i32.const 2
        i32.and
        local.set 1
        local.get 4
        i32.const 14
        local.get 0
        local.get 5
        i32.or
        local.get 1
        i32.or
        i32.sub
        local.get 2
        local.get 1
        i32.shl
        i32.const 15
        i32.shr_u
        i32.add
        local.tee 0
        i32.const 7
        i32.add
        i32.shr_u
        i32.const 1
        i32.and
        local.get 0
        i32.const 1
        i32.shl
        i32.or
      end
    else
      i32.const 0
    end
    local.tee 1
    i32.const 2
    i32.shl
    i32.const 2548
    i32.add
    local.set 0
    local.get 3
    local.get 1
    i32.store offset=28
    local.get 3
    i32.const 0
    i32.store offset=20
    local.get 3
    i32.const 0
    i32.store offset=16
    i32.const 2248
    i32.load
    local.tee 5
    i32.const 1
    local.get 1
    i32.shl
    local.tee 2
    i32.and
    if  ;; label = @1
      block  ;; label = @2
        local.get 0
        i32.load
        local.tee 0
        i32.load offset=4
        i32.const -8
        i32.and
        local.get 4
        i32.eq
        if  ;; label = @3
          local.get 0
          local.set 14
        else
          block  ;; label = @4
            local.get 4
            i32.const 0
            i32.const 25
            local.get 1
            i32.const 1
            i32.shr_u
            i32.sub
            local.get 1
            i32.const 31
            i32.eq
            select
            i32.shl
            local.set 5
            loop  ;; label = @5
              local.get 0
              i32.const 16
              i32.add
              local.get 5
              i32.const 31
              i32.shr_u
              i32.const 2
              i32.shl
              i32.add
              local.tee 2
              i32.load
              local.tee 1
              if  ;; label = @6
                local.get 5
                i32.const 1
                i32.shl
                local.set 5
                local.get 1
                i32.load offset=4
                i32.const -8
                i32.and
                local.get 4
                i32.eq
                if  ;; label = @7
                  local.get 1
                  local.set 14
                  br 3 (;@4;)
                else
                  local.get 1
                  local.set 0
                  br 2 (;@5;)
                end
                unreachable
              end
            end
            i32.const 2260
            i32.load
            local.get 2
            i32.gt_u
            if  ;; label = @5
              call $_abort
            else
              local.get 2
              local.get 3
              i32.store
              local.get 3
              local.get 0
              i32.store offset=24
              local.get 3
              local.get 3
              i32.store offset=12
              local.get 3
              local.get 3
              i32.store offset=8
              br 3 (;@2;)
            end
          end
        end
        i32.const 2260
        i32.load
        local.tee 0
        local.get 14
        i32.le_u
        local.get 0
        local.get 14
        local.tee 0
        i32.load offset=8
        local.tee 1
        i32.le_u
        i32.and
        if  ;; label = @3
          local.get 1
          local.get 3
          i32.store offset=12
          local.get 0
          local.get 3
          i32.store offset=8
          local.get 3
          local.get 1
          i32.store offset=8
          local.get 3
          local.get 14
          i32.store offset=12
          local.get 3
          i32.const 0
          i32.store offset=24
        else
          call $_abort
        end
      end
    else
      i32.const 2248
      local.get 2
      local.get 5
      i32.or
      i32.store
      local.get 0
      local.get 3
      i32.store
      local.get 3
      local.get 0
      i32.store offset=24
      local.get 3
      local.get 3
      i32.store offset=12
      local.get 3
      local.get 3
      i32.store offset=8
    end
    i32.const 2276
    i32.const 2276
    i32.load
    i32.const -1
    i32.add
    local.tee 0
    i32.store
    local.get 0
    if  ;; label = @1
      return
    end
    i32.const 2700
    local.set 0
    loop  ;; label = @1
      local.get 0
      i32.load
      local.tee 1
      i32.const 8
      i32.add
      local.set 0
      local.get 1
      br_if 0 (;@1;)
    end
    i32.const 2276
    i32.const -1
    i32.store)
  (func $_memcpy (type 0) (param i32 i32 i32) (result i32)
    (local i32 i32 i32)
    local.get 2
    i32.const 8192
    i32.ge_s
    if  ;; label = @1
      local.get 0
      local.get 1
      local.get 2
      call $_emscripten_memcpy_big
      drop
      local.get 0
      return
    end
    local.get 0
    local.set 4
    local.get 0
    local.get 2
    i32.add
    local.set 3
    local.get 0
    i32.const 3
    i32.and
    local.get 1
    i32.const 3
    i32.and
    i32.eq
    if  ;; label = @1
      loop  ;; label = @2
        local.get 0
        i32.const 3
        i32.and
        if  ;; label = @3
          local.get 2
          i32.eqz
          if  ;; label = @4
            local.get 4
            return
          end
          local.get 0
          local.get 1
          i32.load8_s
          i32.store8
          local.get 0
          i32.const 1
          i32.add
          local.set 0
          local.get 1
          i32.const 1
          i32.add
          local.set 1
          local.get 2
          i32.const 1
          i32.sub
          local.set 2
          br 1 (;@2;)
        end
      end
      local.get 3
      i32.const -4
      i32.and
      local.tee 2
      i32.const -64
      i32.add
      local.set 5
      loop  ;; label = @2
        local.get 0
        local.get 5
        i32.le_s
        if  ;; label = @3
          local.get 0
          local.get 1
          i32.load
          i32.store
          local.get 0
          local.get 1
          i32.load offset=4
          i32.store offset=4
          local.get 0
          local.get 1
          i32.load offset=8
          i32.store offset=8
          local.get 0
          local.get 1
          i32.load offset=12
          i32.store offset=12
          local.get 0
          local.get 1
          i32.load offset=16
          i32.store offset=16
          local.get 0
          local.get 1
          i32.load offset=20
          i32.store offset=20
          local.get 0
          local.get 1
          i32.load offset=24
          i32.store offset=24
          local.get 0
          local.get 1
          i32.load offset=28
          i32.store offset=28
          local.get 0
          local.get 1
          i32.load offset=32
          i32.store offset=32
          local.get 0
          local.get 1
          i32.load offset=36
          i32.store offset=36
          local.get 0
          local.get 1
          i32.load offset=40
          i32.store offset=40
          local.get 0
          local.get 1
          i32.load offset=44
          i32.store offset=44
          local.get 0
          local.get 1
          i32.load offset=48
          i32.store offset=48
          local.get 0
          local.get 1
          i32.load offset=52
          i32.store offset=52
          local.get 0
          local.get 1
          i32.load offset=56
          i32.store offset=56
          local.get 0
          local.get 1
          i32.load offset=60
          i32.store offset=60
          local.get 0
          i32.const -64
          i32.sub
          local.set 0
          local.get 1
          i32.const -64
          i32.sub
          local.set 1
          br 1 (;@2;)
        end
      end
      loop  ;; label = @2
        local.get 0
        local.get 2
        i32.lt_s
        if  ;; label = @3
          local.get 0
          local.get 1
          i32.load
          i32.store
          local.get 0
          i32.const 4
          i32.add
          local.set 0
          local.get 1
          i32.const 4
          i32.add
          local.set 1
          br 1 (;@2;)
        end
      end
    else
      local.get 3
      i32.const 4
      i32.sub
      local.set 2
      loop  ;; label = @2
        local.get 0
        local.get 2
        i32.lt_s
        if  ;; label = @3
          local.get 0
          local.get 1
          i32.load8_s
          i32.store8
          local.get 0
          local.get 1
          i32.load8_s offset=1
          i32.store8 offset=1
          local.get 0
          local.get 1
          i32.load8_s offset=2
          i32.store8 offset=2
          local.get 0
          local.get 1
          i32.load8_s offset=3
          i32.store8 offset=3
          local.get 0
          i32.const 4
          i32.add
          local.set 0
          local.get 1
          i32.const 4
          i32.add
          local.set 1
          br 1 (;@2;)
        end
      end
    end
    loop  ;; label = @1
      local.get 0
      local.get 3
      i32.lt_s
      if  ;; label = @2
        local.get 0
        local.get 1
        i32.load8_s
        i32.store8
        local.get 0
        i32.const 1
        i32.add
        local.set 0
        local.get 1
        i32.const 1
        i32.add
        local.set 1
        br 1 (;@1;)
      end
    end
    local.get 4)
  (func $_memset (type 0) (param i32 i32 i32) (result i32)
    (local i32 i32 i32 i32)
    local.get 0
    local.get 2
    i32.add
    local.set 4
    local.get 1
    i32.const 255
    i32.and
    local.set 3
    local.get 2
    i32.const 67
    i32.ge_s
    if  ;; label = @1
      loop  ;; label = @2
        local.get 0
        i32.const 3
        i32.and
        if  ;; label = @3
          local.get 0
          local.get 3
          i32.store8
          local.get 0
          i32.const 1
          i32.add
          local.set 0
          br 1 (;@2;)
        end
      end
      local.get 3
      i32.const 8
      i32.shl
      local.get 3
      i32.or
      local.get 3
      i32.const 16
      i32.shl
      i32.or
      local.get 3
      i32.const 24
      i32.shl
      i32.or
      local.set 1
      local.get 4
      i32.const -4
      i32.and
      local.tee 5
      i32.const -64
      i32.add
      local.set 6
      loop  ;; label = @2
        local.get 0
        local.get 6
        i32.le_s
        if  ;; label = @3
          local.get 0
          local.get 1
          i32.store
          local.get 0
          local.get 1
          i32.store offset=4
          local.get 0
          local.get 1
          i32.store offset=8
          local.get 0
          local.get 1
          i32.store offset=12
          local.get 0
          local.get 1
          i32.store offset=16
          local.get 0
          local.get 1
          i32.store offset=20
          local.get 0
          local.get 1
          i32.store offset=24
          local.get 0
          local.get 1
          i32.store offset=28
          local.get 0
          local.get 1
          i32.store offset=32
          local.get 0
          local.get 1
          i32.store offset=36
          local.get 0
          local.get 1
          i32.store offset=40
          local.get 0
          local.get 1
          i32.store offset=44
          local.get 0
          local.get 1
          i32.store offset=48
          local.get 0
          local.get 1
          i32.store offset=52
          local.get 0
          local.get 1
          i32.store offset=56
          local.get 0
          local.get 1
          i32.store offset=60
          local.get 0
          i32.const -64
          i32.sub
          local.set 0
          br 1 (;@2;)
        end
      end
      loop  ;; label = @2
        local.get 0
        local.get 5
        i32.lt_s
        if  ;; label = @3
          local.get 0
          local.get 1
          i32.store
          local.get 0
          i32.const 4
          i32.add
          local.set 0
          br 1 (;@2;)
        end
      end
    end
    loop  ;; label = @1
      local.get 0
      local.get 4
      i32.lt_s
      if  ;; label = @2
        local.get 0
        local.get 3
        i32.store8
        local.get 0
        i32.const 1
        i32.add
        local.set 0
        br 1 (;@1;)
      end
    end
    local.get 4
    local.get 2
    i32.sub)
  (func $_sbrk (type 1) (param i32) (result i32)
    (local i32 i32 i32)
    call $_emscripten_get_heap_size
    local.set 3
    local.get 0
    global.get 1
    i32.load
    local.tee 2
    i32.add
    local.tee 1
    local.get 2
    i32.lt_s
    local.get 0
    i32.const 0
    i32.gt_s
    i32.and
    local.get 1
    i32.const 0
    i32.lt_s
    i32.or
    if  ;; label = @1
      local.get 1
      call $abortOnCannotGrowMemory
      drop
      i32.const 12
      call $___setErrNo
      i32.const -1
      return
    end
    local.get 1
    local.get 3
    i32.gt_s
    if  ;; label = @1
      local.get 1
      call $_emscripten_resize_heap
      i32.eqz
      if  ;; label = @2
        i32.const 12
        call $___setErrNo
        i32.const -1
        return
      end
    end
    global.get 1
    local.get 1
    i32.store
    local.get 2)
  (func $dynCall_ii (type 4) (param i32 i32) (result i32)
    local.get 1
    local.get 0
    i32.const 1
    i32.and
    call_indirect (type 1))
  (func $dynCall_iiii (type 8) (param i32 i32 i32 i32) (result i32)
    local.get 1
    local.get 2
    local.get 3
    local.get 0
    i32.const 3
    i32.and
    i32.const 2
    i32.add
    call_indirect (type 0))
  (func $b0 (type 1) (param i32) (result i32)
    i32.const 0
    call $abort
    i32.const 0)
  (func $b1 (type 0) (param i32 i32 i32) (result i32)
    i32.const 1
    call $abort
    i32.const 0)
  (func $b2 (type 2) (param i32 i64 i32) (result i64)
    i32.const 2
    call $abort
    i64.const 0)
  (func $legalstub$dynCall_jiji (type 9) (param i32 i32 i32 i32 i32) (result i32)
    (local i64)
    local.get 1
    local.get 2
    i64.extend_i32_u
    local.get 3
    i64.extend_i32_u
    i64.const 32
    i64.shl
    i64.or
    local.get 4
    local.get 0
    i32.const 1
    i32.and
    i32.const 6
    i32.add
    call_indirect (type 2)
    local.tee 5
    i64.const 32
    i64.shr_u
    i32.wrap_i64
    call $setTempRet0
    local.get 5
    i32.wrap_i64)
  (global (;2;) (mut i32) (i32.const 3984))
  (global (;3;) (mut i32) (i32.const 5246864))
  (export "___errno_location" (func $___errno_location))
  (export "_free" (func $_free))
  (export "_main" (func $_main))
  (export "_malloc" (func $_malloc))
  (export "_memcpy" (func $_memcpy))
  (export "_memset" (func $_memset))
  (export "_sbrk" (func $_sbrk))
  (export "dynCall_ii" (func $dynCall_ii))
  (export "dynCall_iiii" (func $dynCall_iiii))
  (export "dynCall_jiji" (func $legalstub$dynCall_jiji))
  (export "establishStackSpace" (func $establishStackSpace))
  (export "stackAlloc" (func $stackAlloc))
  (export "stackRestore" (func $stackRestore))
  (export "stackSave" (func $stackSave))
  (elem (;0;) (global.get 0) $b0 $___stdio_close $b1 $___stdout_write $___stdio_write $b1 $b2 $___stdio_seek)
  (data (;0;) (i32.const 1024) "\05")
  (data (;1;) (i32.const 1036) "\01")
  (data (;2;) (i32.const 1060) "\01\00\00\00\01\00\00\00\b8\04\00\00\00\04")
  (data (;3;) (i32.const 1084) "\01")
  (data (;4;) (i32.const 1099) "\0a\ff\ff\ff\ff")
  (data (;5;) (i32.const 1169) "\04\00\00Hello world!"))
