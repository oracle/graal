(module
  (type (;0;) (func (param i32 i32 i32) (result i32)))
  (type (;1;) (func (param i32 f64 i32 i32 i32 i32) (result i32)))
  (type (;2;) (func (param i32 i32)))
  (type (;3;) (func (param i32 i64 i32) (result i64)))
  (type (;4;) (func (param i32) (result i32)))
  (type (;5;) (func (param i32)))
  (type (;6;) (func (param i32 i32 i32 i32) (result i32)))
  (type (;7;) (func (result i32)))
  (type (;8;) (func (param i32 i32) (result i32)))
  (type (;9;) (func (param f64) (result f64)))
  (type (;10;) (func))
  (type (;11;) (func (param i32 i32 i32)))
  (type (;12;) (func (param i32 i32 i32 i32 i32) (result i32)))
  (type (;13;) (func (param i64 i32 i32) (result i32)))
  (type (;14;) (func (param i64 i32) (result i32)))
  (type (;15;) (func (param i32 i32 i32 i32 i32)))
  (type (;16;) (func (param f64 i32) (result f64)))
  (import "env" "abort" (func (;0;) (type 5)))
  (import "env" "_gettimeofday" (func (;1;) (type 8)))
  (import "env" "___setErrNo" (func (;2;) (type 5)))
  (import "env" "_llvm_exp2_f64" (func (;3;) (type 9)))
  (import "env" "_emscripten_resize_heap" (func (;4;) (type 4)))
  (import "env" "_emscripten_memcpy_big" (func (;5;) (type 0)))
  (import "env" "_emscripten_get_heap_size" (func (;6;) (type 7)))
  (import "env" "___wasi_fd_write" (func (;7;) (type 6)))
  (import "env" "___unlock" (func (;8;) (type 5)))
  (import "env" "abortOnCannotGrowMemory" (func (;9;) (type 4)))
  (import "env" "___lock" (func (;10;) (type 5)))
  (import "env" "memory" (memory (;0;) 4096 4096))
  (import "env" "table" (table (;0;) 10 10 funcref))
  (func (;11;) (type 11) (param i32 i32 i32)
    local.get 0
    i32.load
    i32.const 32
    i32.and
    i32.eqz
    if  ;; label = @1
      local.get 1
      local.get 2
      local.get 0
      call 43
    end)
  (func (;12;) (type 15) (param i32 i32 i32 i32 i32)
    (local i32 i32 i32)
    global.get 2
    local.set 6
    global.get 2
    i32.const 256
    i32.add
    global.set 2
    local.get 6
    local.set 5
    local.get 4
    i32.const 73728
    i32.and
    i32.eqz
    local.get 2
    local.get 3
    i32.gt_s
    i32.and
    if  ;; label = @1
      local.get 5
      local.get 1
      i32.const 24
      i32.shl
      i32.const 24
      i32.shr_s
      local.get 2
      local.get 3
      i32.sub
      local.tee 1
      i32.const 256
      local.get 1
      i32.const 256
      i32.lt_u
      select
      call 19
      drop
      local.get 1
      i32.const 255
      i32.gt_u
      if  ;; label = @2
        block (result i32)  ;; label = @3
          local.get 2
          local.get 3
          i32.sub
          local.set 7
          loop  ;; label = @4
            local.get 0
            local.get 5
            i32.const 256
            call 11
            local.get 1
            i32.const -256
            i32.add
            local.tee 1
            i32.const 255
            i32.gt_u
            br_if 0 (;@4;)
          end
          local.get 7
        end
        i32.const 255
        i32.and
        local.set 1
      end
      local.get 0
      local.get 5
      local.get 1
      call 11
    end
    local.get 6
    global.set 2)
  (func (;13;) (type 2) (param i32 i32)
    (local i32)
    global.get 2
    local.set 2
    global.get 2
    i32.const 16
    i32.add
    global.set 2
    local.get 2
    local.get 1
    i32.store
    i32.const 1648
    i32.load
    local.get 0
    local.get 2
    call 48
    local.get 2
    global.set 2)
  (func (;14;) (type 4) (param i32) (result i32)
    (local i32 i32 i32)
    call 6
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
      call 9
      drop
      i32.const 12
      call 2
      i32.const -1
      return
    end
    local.get 1
    local.get 3
    i32.gt_s
    if  ;; label = @1
      local.get 1
      call 4
      i32.eqz
      if  ;; label = @2
        i32.const 12
        call 2
        i32.const -1
        return
      end
    end
    global.get 1
    local.get 1
    i32.store
    local.get 2)
  (func (;15;) (type 8) (param i32 i32) (result i32)
    (local i32)
    local.get 1
    if (result i32)  ;; label = @1
      local.get 0
      i32.const 1
      i32.shl
      local.tee 2
      i32.const -1
      i32.add
      local.get 1
      i32.const -1
      i32.add
      local.tee 1
      call 15
      local.get 2
      local.get 1
      call 15
      local.get 0
      call 24
    else
      i32.const 0
      i32.const 0
      local.get 0
      call 24
    end)
  (func (;16;) (type 14) (param i64 i32) (result i32)
    (local i32 i32 i64)
    local.get 0
    i32.wrap_i64
    local.set 2
    local.get 0
    i64.const 4294967295
    i64.gt_u
    if  ;; label = @1
      loop  ;; label = @2
        local.get 1
        i32.const -1
        i32.add
        local.tee 1
        local.get 0
        local.get 0
        i64.const 10
        i64.div_u
        local.tee 4
        i64.const 10
        i64.mul
        i64.sub
        i32.wrap_i64
        i32.const 255
        i32.and
        i32.const 48
        i32.or
        i32.store8
        local.get 0
        i64.const 42949672959
        i64.gt_u
        if  ;; label = @3
          local.get 4
          local.set 0
          br 1 (;@2;)
        end
      end
      local.get 4
      i32.wrap_i64
      local.set 2
    end
    local.get 2
    if  ;; label = @1
      loop  ;; label = @2
        local.get 1
        i32.const -1
        i32.add
        local.tee 1
        local.get 2
        local.get 2
        i32.const 10
        i32.div_u
        local.tee 3
        i32.const 10
        i32.mul
        i32.sub
        i32.const 48
        i32.or
        i32.store8
        local.get 2
        i32.const 10
        i32.ge_u
        if  ;; label = @3
          local.get 3
          local.set 2
          br 1 (;@2;)
        end
      end
    end
    local.get 1)
  (func (;17;) (type 5) (param i32)
    (local i32)
    local.get 0
    i32.load
    local.tee 1
    if  ;; label = @1
      local.get 1
      call 17
      local.get 0
      i32.load offset=4
      call 17
    end
    local.get 0
    call 20)
  (func (;18;) (type 4) (param i32) (result i32)
    local.get 0
    i32.const -48
    i32.add
    i32.const 10
    i32.lt_u)
  (func (;19;) (type 0) (param i32 i32 i32) (result i32)
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
  (func (;20;) (type 5) (param i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32 i32)
    local.get 0
    i32.eqz
    if  ;; label = @1
      return
    end
    i32.const 3216
    i32.load
    local.set 4
    local.get 0
    i32.const -8
    i32.add
    local.tee 1
    local.get 0
    i32.const -4
    i32.add
    i32.load
    local.tee 0
    i32.const -8
    i32.and
    local.tee 3
    i32.add
    local.set 5
    local.get 0
    i32.const 1
    i32.and
    if (result i32)  ;; label = @1
      local.get 1
      local.set 2
      local.get 3
    else
      block (result i32)  ;; label = @2
        local.get 1
        i32.load
        local.set 2
        local.get 0
        i32.const 3
        i32.and
        i32.eqz
        if  ;; label = @3
          return
        end
        local.get 1
        local.get 2
        i32.sub
        local.tee 0
        local.get 4
        i32.lt_u
        if  ;; label = @3
          return
        end
        local.get 2
        local.get 3
        i32.add
        local.set 3
        i32.const 3220
        i32.load
        local.get 0
        i32.eq
        if  ;; label = @3
          local.get 5
          i32.load offset=4
          local.tee 1
          i32.const 3
          i32.and
          i32.const 3
          i32.ne
          if  ;; label = @4
            local.get 0
            local.set 1
            local.get 0
            local.set 2
            local.get 3
            br 2 (;@2;)
          end
          i32.const 3208
          local.get 3
          i32.store
          local.get 5
          local.get 1
          i32.const -2
          i32.and
          i32.store offset=4
          local.get 0
          local.get 3
          i32.const 1
          i32.or
          i32.store offset=4
          local.get 0
          local.get 3
          i32.add
          local.get 3
          i32.store
          return
        end
        local.get 2
        i32.const 3
        i32.shr_u
        local.set 4
        local.get 2
        i32.const 256
        i32.lt_u
        if  ;; label = @3
          local.get 0
          i32.load offset=8
          local.tee 1
          local.get 0
          i32.load offset=12
          local.tee 2
          i32.eq
          if  ;; label = @4
            i32.const 3200
            i32.const 3200
            i32.load
            i32.const 1
            local.get 4
            i32.shl
            i32.const -1
            i32.xor
            i32.and
            i32.store
          else
            local.get 1
            local.get 2
            i32.store offset=12
            local.get 2
            local.get 1
            i32.store offset=8
          end
          local.get 0
          local.set 1
          local.get 0
          local.set 2
          local.get 3
          br 1 (;@2;)
        end
        local.get 0
        i32.load offset=24
        local.set 7
        local.get 0
        i32.load offset=12
        local.tee 1
        local.get 0
        i32.eq
        if  ;; label = @3
          block  ;; label = @4
            local.get 0
            i32.const 16
            i32.add
            local.tee 2
            i32.const 4
            i32.add
            local.tee 4
            i32.load
            local.tee 1
            if  ;; label = @5
              local.get 4
              local.set 2
            else
              local.get 2
              i32.load
              local.tee 1
              i32.eqz
              if  ;; label = @6
                i32.const 0
                local.set 1
                br 2 (;@4;)
              end
            end
            loop  ;; label = @5
              block  ;; label = @6
                local.get 1
                i32.const 20
                i32.add
                local.tee 4
                i32.load
                local.tee 6
                i32.eqz
                if  ;; label = @7
                  local.get 1
                  i32.const 16
                  i32.add
                  local.tee 4
                  i32.load
                  local.tee 6
                  i32.eqz
                  br_if 1 (;@6;)
                end
                local.get 4
                local.set 2
                local.get 6
                local.set 1
                br 1 (;@5;)
              end
            end
            local.get 2
            i32.const 0
            i32.store
          end
        else
          local.get 0
          i32.load offset=8
          local.tee 2
          local.get 1
          i32.store offset=12
          local.get 1
          local.get 2
          i32.store offset=8
        end
        local.get 7
        if (result i32)  ;; label = @3
          local.get 0
          i32.load offset=28
          local.tee 2
          i32.const 2
          i32.shl
          i32.const 3504
          i32.add
          local.tee 4
          i32.load
          local.get 0
          i32.eq
          if  ;; label = @4
            local.get 4
            local.get 1
            i32.store
            local.get 1
            i32.eqz
            if  ;; label = @5
              i32.const 3204
              i32.const 3204
              i32.load
              i32.const 1
              local.get 2
              i32.shl
              i32.const -1
              i32.xor
              i32.and
              i32.store
              local.get 0
              local.set 1
              local.get 0
              local.set 2
              local.get 3
              br 3 (;@2;)
            end
          else
            local.get 7
            i32.const 16
            i32.add
            local.tee 2
            local.get 7
            i32.const 20
            i32.add
            local.get 2
            i32.load
            local.get 0
            i32.eq
            select
            local.get 1
            i32.store
            local.get 1
            i32.eqz
            if  ;; label = @5
              local.get 0
              local.set 1
              local.get 0
              local.set 2
              local.get 3
              br 3 (;@2;)
            end
          end
          local.get 1
          local.get 7
          i32.store offset=24
          local.get 0
          i32.load offset=16
          local.tee 2
          if  ;; label = @4
            local.get 1
            local.get 2
            i32.store offset=16
            local.get 2
            local.get 1
            i32.store offset=24
          end
          local.get 0
          i32.load offset=20
          local.tee 2
          if  ;; label = @4
            local.get 1
            local.get 2
            i32.store offset=20
            local.get 2
            local.get 1
            i32.store offset=24
          end
          local.get 0
          local.set 1
          local.get 0
          local.set 2
          local.get 3
        else
          local.get 0
          local.set 1
          local.get 0
          local.set 2
          local.get 3
        end
      end
    end
    local.set 0
    local.get 1
    local.get 5
    i32.ge_u
    if  ;; label = @1
      return
    end
    local.get 5
    i32.load offset=4
    local.tee 8
    i32.const 1
    i32.and
    i32.eqz
    if  ;; label = @1
      return
    end
    local.get 8
    i32.const 2
    i32.and
    if  ;; label = @1
      local.get 5
      local.get 8
      i32.const -2
      i32.and
      i32.store offset=4
      local.get 2
      local.get 0
      i32.const 1
      i32.or
      i32.store offset=4
      local.get 0
      local.get 1
      i32.add
      local.get 0
      i32.store
      local.get 0
      local.set 3
    else
      i32.const 3224
      i32.load
      local.get 5
      i32.eq
      if  ;; label = @2
        i32.const 3212
        i32.const 3212
        i32.load
        local.get 0
        i32.add
        local.tee 0
        i32.store
        i32.const 3224
        local.get 2
        i32.store
        local.get 2
        local.get 0
        i32.const 1
        i32.or
        i32.store offset=4
        local.get 2
        i32.const 3220
        i32.load
        i32.ne
        if  ;; label = @3
          return
        end
        i32.const 3220
        i32.const 0
        i32.store
        i32.const 3208
        i32.const 0
        i32.store
        return
      end
      i32.const 3220
      i32.load
      local.get 5
      i32.eq
      if  ;; label = @2
        i32.const 3208
        i32.const 3208
        i32.load
        local.get 0
        i32.add
        local.tee 0
        i32.store
        i32.const 3220
        local.get 1
        i32.store
        local.get 2
        local.get 0
        i32.const 1
        i32.or
        i32.store offset=4
        local.get 0
        local.get 1
        i32.add
        local.get 0
        i32.store
        return
      end
      local.get 8
      i32.const 3
      i32.shr_u
      local.set 6
      local.get 8
      i32.const 256
      i32.lt_u
      if  ;; label = @2
        local.get 5
        i32.load offset=8
        local.tee 3
        local.get 5
        i32.load offset=12
        local.tee 4
        i32.eq
        if  ;; label = @3
          i32.const 3200
          i32.const 3200
          i32.load
          i32.const 1
          local.get 6
          i32.shl
          i32.const -1
          i32.xor
          i32.and
          i32.store
        else
          local.get 3
          local.get 4
          i32.store offset=12
          local.get 4
          local.get 3
          i32.store offset=8
        end
      else
        block  ;; label = @3
          local.get 5
          i32.load offset=24
          local.set 9
          local.get 5
          i32.load offset=12
          local.tee 3
          local.get 5
          i32.eq
          if  ;; label = @4
            block  ;; label = @5
              local.get 5
              i32.const 16
              i32.add
              local.tee 4
              i32.const 4
              i32.add
              local.tee 6
              i32.load
              local.tee 3
              if  ;; label = @6
                local.get 6
                local.set 4
              else
                local.get 4
                i32.load
                local.tee 3
                i32.eqz
                if  ;; label = @7
                  i32.const 0
                  local.set 3
                  br 2 (;@5;)
                end
              end
              loop  ;; label = @6
                block  ;; label = @7
                  local.get 3
                  i32.const 20
                  i32.add
                  local.tee 6
                  i32.load
                  local.tee 7
                  i32.eqz
                  if  ;; label = @8
                    local.get 3
                    i32.const 16
                    i32.add
                    local.tee 6
                    i32.load
                    local.tee 7
                    i32.eqz
                    br_if 1 (;@7;)
                  end
                  local.get 6
                  local.set 4
                  local.get 7
                  local.set 3
                  br 1 (;@6;)
                end
              end
              local.get 4
              i32.const 0
              i32.store
            end
          else
            local.get 5
            i32.load offset=8
            local.tee 4
            local.get 3
            i32.store offset=12
            local.get 3
            local.get 4
            i32.store offset=8
          end
          local.get 9
          if  ;; label = @4
            local.get 5
            i32.load offset=28
            local.tee 4
            i32.const 2
            i32.shl
            i32.const 3504
            i32.add
            local.tee 6
            i32.load
            local.get 5
            i32.eq
            if  ;; label = @5
              local.get 6
              local.get 3
              i32.store
              local.get 3
              i32.eqz
              if  ;; label = @6
                i32.const 3204
                i32.const 3204
                i32.load
                i32.const 1
                local.get 4
                i32.shl
                i32.const -1
                i32.xor
                i32.and
                i32.store
                br 3 (;@3;)
              end
            else
              local.get 9
              i32.const 16
              i32.add
              local.tee 4
              local.get 9
              i32.const 20
              i32.add
              local.get 4
              i32.load
              local.get 5
              i32.eq
              select
              local.get 3
              i32.store
              local.get 3
              i32.eqz
              br_if 2 (;@3;)
            end
            local.get 3
            local.get 9
            i32.store offset=24
            local.get 5
            i32.load offset=16
            local.tee 4
            if  ;; label = @5
              local.get 3
              local.get 4
              i32.store offset=16
              local.get 4
              local.get 3
              i32.store offset=24
            end
            local.get 5
            i32.load offset=20
            local.tee 4
            if  ;; label = @5
              local.get 3
              local.get 4
              i32.store offset=20
              local.get 4
              local.get 3
              i32.store offset=24
            end
          end
        end
      end
      local.get 2
      local.get 8
      i32.const -8
      i32.and
      local.get 0
      i32.add
      local.tee 3
      i32.const 1
      i32.or
      i32.store offset=4
      local.get 1
      local.get 3
      i32.add
      local.get 3
      i32.store
      i32.const 3220
      i32.load
      local.get 2
      i32.eq
      if  ;; label = @2
        i32.const 3208
        local.get 3
        i32.store
        return
      end
    end
    local.get 3
    i32.const 3
    i32.shr_u
    local.set 1
    local.get 3
    i32.const 256
    i32.lt_u
    if  ;; label = @1
      local.get 1
      i32.const 3
      i32.shl
      i32.const 3240
      i32.add
      local.set 0
      i32.const 3200
      i32.load
      local.tee 3
      i32.const 1
      local.get 1
      i32.shl
      local.tee 1
      i32.and
      if (result i32)  ;; label = @2
        local.get 0
        i32.const 8
        i32.add
        local.tee 1
        local.set 3
        local.get 1
        i32.load
      else
        i32.const 3200
        local.get 1
        local.get 3
        i32.or
        i32.store
        local.get 0
        i32.const 8
        i32.add
        local.set 3
        local.get 0
      end
      local.set 1
      local.get 3
      local.get 2
      i32.store
      local.get 1
      local.get 2
      i32.store offset=12
      local.get 2
      local.get 1
      i32.store offset=8
      local.get 2
      local.get 0
      i32.store offset=12
      return
    end
    local.get 3
    i32.const 8
    i32.shr_u
    local.tee 0
    if (result i32)  ;; label = @1
      local.get 3
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
        local.tee 4
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
        local.tee 6
        i32.const 245760
        i32.add
        i32.const 16
        i32.shr_u
        i32.const 2
        i32.and
        local.set 1
        local.get 3
        i32.const 14
        local.get 0
        local.get 4
        i32.or
        local.get 1
        i32.or
        i32.sub
        local.get 6
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
    i32.const 3504
    i32.add
    local.set 0
    local.get 2
    local.get 1
    i32.store offset=28
    local.get 2
    i32.const 0
    i32.store offset=20
    local.get 2
    i32.const 0
    i32.store offset=16
    i32.const 3204
    i32.load
    local.tee 4
    i32.const 1
    local.get 1
    i32.shl
    local.tee 6
    i32.and
    if  ;; label = @1
      block  ;; label = @2
        local.get 0
        i32.load
        local.tee 0
        i32.load offset=4
        i32.const -8
        i32.and
        local.get 3
        i32.eq
        if  ;; label = @3
          local.get 0
          local.set 1
        else
          block  ;; label = @4
            local.get 3
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
            local.set 4
            loop  ;; label = @5
              local.get 0
              i32.const 16
              i32.add
              local.get 4
              i32.const 31
              i32.shr_u
              i32.const 2
              i32.shl
              i32.add
              local.tee 6
              i32.load
              local.tee 1
              if  ;; label = @6
                local.get 4
                i32.const 1
                i32.shl
                local.set 4
                local.get 1
                i32.load offset=4
                i32.const -8
                i32.and
                local.get 3
                i32.eq
                br_if 2 (;@4;)
                local.get 1
                local.set 0
                br 1 (;@5;)
              end
            end
            local.get 6
            local.get 2
            i32.store
            local.get 2
            local.get 0
            i32.store offset=24
            local.get 2
            local.get 2
            i32.store offset=12
            local.get 2
            local.get 2
            i32.store offset=8
            br 2 (;@2;)
          end
        end
        local.get 1
        i32.load offset=8
        local.tee 0
        local.get 2
        i32.store offset=12
        local.get 1
        local.get 2
        i32.store offset=8
        local.get 2
        local.get 0
        i32.store offset=8
        local.get 2
        local.get 1
        i32.store offset=12
        local.get 2
        i32.const 0
        i32.store offset=24
      end
    else
      i32.const 3204
      local.get 4
      local.get 6
      i32.or
      i32.store
      local.get 0
      local.get 2
      i32.store
      local.get 2
      local.get 0
      i32.store offset=24
      local.get 2
      local.get 2
      i32.store offset=12
      local.get 2
      local.get 2
      i32.store offset=8
    end
    i32.const 3232
    i32.const 3232
    i32.load
    i32.const -1
    i32.add
    local.tee 0
    i32.store
    local.get 0
    if  ;; label = @1
      return
    end
    i32.const 3656
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
    i32.const 3232
    i32.const -1
    i32.store)
  (func (;21;) (type 4) (param i32) (result i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32)
    global.get 2
    local.set 10
    global.get 2
    i32.const 16
    i32.add
    global.set 2
    local.get 0
    i32.const 245
    i32.lt_u
    if (result i32)  ;; label = @1
      i32.const 3200
      i32.load
      local.tee 2
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
      local.tee 3
      i32.const 3
      i32.shr_u
      local.tee 0
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
        local.get 0
        i32.add
        local.tee 1
        i32.const 3
        i32.shl
        i32.const 3240
        i32.add
        local.tee 0
        i32.load offset=8
        local.tee 4
        i32.const 8
        i32.add
        local.tee 3
        i32.load
        local.tee 5
        local.get 0
        i32.eq
        if  ;; label = @3
          i32.const 3200
          local.get 2
          i32.const 1
          local.get 1
          i32.shl
          i32.const -1
          i32.xor
          i32.and
          i32.store
        else
          local.get 5
          local.get 0
          i32.store offset=12
          local.get 0
          local.get 5
          i32.store offset=8
        end
        local.get 4
        local.get 1
        i32.const 3
        i32.shl
        local.tee 0
        i32.const 3
        i32.or
        i32.store offset=4
        local.get 0
        local.get 4
        i32.add
        local.tee 0
        local.get 0
        i32.load offset=4
        i32.const 1
        i32.or
        i32.store offset=4
        local.get 10
        global.set 2
        local.get 3
        return
      end
      local.get 3
      i32.const 3208
      i32.load
      local.tee 9
      i32.gt_u
      if (result i32)  ;; label = @2
        local.get 1
        if  ;; label = @3
          i32.const 2
          local.get 0
          i32.shl
          local.tee 4
          i32.const 0
          local.get 4
          i32.sub
          i32.or
          local.get 1
          local.get 0
          i32.shl
          i32.and
          local.tee 0
          i32.const 0
          local.get 0
          i32.sub
          i32.and
          i32.const -1
          i32.add
          local.tee 0
          i32.const 12
          i32.shr_u
          i32.const 16
          i32.and
          local.tee 1
          local.get 0
          local.get 1
          i32.shr_u
          local.tee 0
          i32.const 5
          i32.shr_u
          i32.const 8
          i32.and
          local.tee 1
          i32.or
          local.get 0
          local.get 1
          i32.shr_u
          local.tee 0
          i32.const 2
          i32.shr_u
          i32.const 4
          i32.and
          local.tee 1
          i32.or
          local.get 0
          local.get 1
          i32.shr_u
          local.tee 0
          i32.const 1
          i32.shr_u
          i32.const 2
          i32.and
          local.tee 1
          i32.or
          local.get 0
          local.get 1
          i32.shr_u
          local.tee 0
          i32.const 1
          i32.shr_u
          i32.const 1
          i32.and
          local.tee 1
          i32.or
          local.get 0
          local.get 1
          i32.shr_u
          i32.add
          local.tee 4
          i32.const 3
          i32.shl
          i32.const 3240
          i32.add
          local.tee 0
          i32.load offset=8
          local.tee 1
          i32.const 8
          i32.add
          local.tee 6
          i32.load
          local.tee 5
          local.get 0
          i32.eq
          if  ;; label = @4
            i32.const 3200
            local.get 2
            i32.const 1
            local.get 4
            i32.shl
            i32.const -1
            i32.xor
            i32.and
            local.tee 0
            i32.store
          else
            local.get 5
            local.get 0
            i32.store offset=12
            local.get 0
            local.get 5
            i32.store offset=8
            local.get 2
            local.set 0
          end
          local.get 1
          local.get 3
          i32.const 3
          i32.or
          i32.store offset=4
          local.get 1
          local.get 3
          i32.add
          local.tee 5
          local.get 4
          i32.const 3
          i32.shl
          local.tee 2
          local.get 3
          i32.sub
          local.tee 4
          i32.const 1
          i32.or
          i32.store offset=4
          local.get 1
          local.get 2
          i32.add
          local.get 4
          i32.store
          local.get 9
          if  ;; label = @4
            i32.const 3220
            i32.load
            local.set 2
            local.get 9
            i32.const 3
            i32.shr_u
            local.tee 3
            i32.const 3
            i32.shl
            i32.const 3240
            i32.add
            local.set 1
            local.get 0
            i32.const 1
            local.get 3
            i32.shl
            local.tee 3
            i32.and
            if (result i32)  ;; label = @5
              local.get 1
              i32.const 8
              i32.add
              local.set 7
              local.get 1
              i32.load offset=8
            else
              i32.const 3200
              local.get 0
              local.get 3
              i32.or
              i32.store
              local.get 1
              i32.const 8
              i32.add
              local.set 7
              local.get 1
            end
            local.set 0
            local.get 7
            local.get 2
            i32.store
            local.get 0
            local.get 2
            i32.store offset=12
            local.get 2
            local.get 0
            i32.store offset=8
            local.get 2
            local.get 1
            i32.store offset=12
          end
          i32.const 3208
          local.get 4
          i32.store
          i32.const 3220
          local.get 5
          i32.store
          local.get 10
          global.set 2
          local.get 6
          return
        end
        i32.const 3204
        i32.load
        local.tee 11
        if (result i32)  ;; label = @3
          local.get 11
          i32.const 0
          local.get 11
          i32.sub
          i32.and
          i32.const -1
          i32.add
          local.tee 0
          i32.const 12
          i32.shr_u
          i32.const 16
          i32.and
          local.tee 1
          local.get 0
          local.get 1
          i32.shr_u
          local.tee 0
          i32.const 5
          i32.shr_u
          i32.const 8
          i32.and
          local.tee 1
          i32.or
          local.get 0
          local.get 1
          i32.shr_u
          local.tee 0
          i32.const 2
          i32.shr_u
          i32.const 4
          i32.and
          local.tee 1
          i32.or
          local.get 0
          local.get 1
          i32.shr_u
          local.tee 0
          i32.const 1
          i32.shr_u
          i32.const 2
          i32.and
          local.tee 1
          i32.or
          local.get 0
          local.get 1
          i32.shr_u
          local.tee 0
          i32.const 1
          i32.shr_u
          i32.const 1
          i32.and
          local.tee 1
          i32.or
          local.get 0
          local.get 1
          i32.shr_u
          i32.add
          i32.const 2
          i32.shl
          i32.const 3504
          i32.add
          i32.load
          local.tee 0
          i32.load offset=4
          i32.const -8
          i32.and
          local.get 3
          i32.sub
          local.set 6
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
              local.get 3
              i32.sub
              local.tee 4
              local.get 6
              i32.lt_u
              local.set 1
              local.get 4
              local.get 6
              local.get 1
              select
              local.set 6
              local.get 0
              local.get 5
              local.get 1
              select
              local.set 5
              br 1 (;@4;)
            end
          end
          local.get 3
          local.get 5
          i32.add
          local.tee 12
          local.get 5
          i32.gt_u
          if (result i32)  ;; label = @4
            local.get 5
            i32.load offset=24
            local.set 8
            local.get 5
            i32.load offset=12
            local.tee 0
            local.get 5
            i32.eq
            if  ;; label = @5
              block  ;; label = @6
                local.get 5
                i32.const 20
                i32.add
                local.tee 1
                i32.load
                local.tee 0
                i32.eqz
                if  ;; label = @7
                  local.get 5
                  i32.const 16
                  i32.add
                  local.tee 1
                  i32.load
                  local.tee 0
                  i32.eqz
                  if  ;; label = @8
                    i32.const 0
                    local.set 0
                    br 2 (;@6;)
                  end
                end
                loop  ;; label = @7
                  block  ;; label = @8
                    local.get 0
                    i32.const 20
                    i32.add
                    local.tee 7
                    i32.load
                    local.tee 4
                    i32.eqz
                    if  ;; label = @9
                      local.get 0
                      i32.const 16
                      i32.add
                      local.tee 7
                      i32.load
                      local.tee 4
                      i32.eqz
                      br_if 1 (;@8;)
                    end
                    local.get 7
                    local.set 1
                    local.get 4
                    local.set 0
                    br 1 (;@7;)
                  end
                end
                local.get 1
                i32.const 0
                i32.store
              end
            else
              local.get 5
              i32.load offset=8
              local.tee 1
              local.get 0
              i32.store offset=12
              local.get 0
              local.get 1
              i32.store offset=8
            end
            local.get 8
            if  ;; label = @5
              block  ;; label = @6
                local.get 5
                i32.load offset=28
                local.tee 1
                i32.const 2
                i32.shl
                i32.const 3504
                i32.add
                local.tee 4
                i32.load
                local.get 5
                i32.eq
                if  ;; label = @7
                  local.get 4
                  local.get 0
                  i32.store
                  local.get 0
                  i32.eqz
                  if  ;; label = @8
                    i32.const 3204
                    local.get 11
                    i32.const 1
                    local.get 1
                    i32.shl
                    i32.const -1
                    i32.xor
                    i32.and
                    i32.store
                    br 2 (;@6;)
                  end
                else
                  local.get 8
                  i32.const 16
                  i32.add
                  local.get 8
                  i32.const 20
                  i32.add
                  local.get 8
                  i32.load offset=16
                  local.get 5
                  i32.eq
                  select
                  local.get 0
                  i32.store
                  local.get 0
                  i32.eqz
                  br_if 1 (;@6;)
                end
                local.get 0
                local.get 8
                i32.store offset=24
                local.get 5
                i32.load offset=16
                local.tee 1
                if  ;; label = @7
                  local.get 0
                  local.get 1
                  i32.store offset=16
                  local.get 1
                  local.get 0
                  i32.store offset=24
                end
                local.get 5
                i32.load offset=20
                local.tee 1
                if  ;; label = @7
                  local.get 0
                  local.get 1
                  i32.store offset=20
                  local.get 1
                  local.get 0
                  i32.store offset=24
                end
              end
            end
            local.get 6
            i32.const 16
            i32.lt_u
            if  ;; label = @5
              local.get 5
              local.get 3
              local.get 6
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
              local.get 3
              i32.const 3
              i32.or
              i32.store offset=4
              local.get 12
              local.get 6
              i32.const 1
              i32.or
              i32.store offset=4
              local.get 6
              local.get 12
              i32.add
              local.get 6
              i32.store
              local.get 9
              if  ;; label = @6
                i32.const 3220
                i32.load
                local.set 1
                local.get 9
                i32.const 3
                i32.shr_u
                local.tee 4
                i32.const 3
                i32.shl
                i32.const 3240
                i32.add
                local.set 0
                local.get 2
                i32.const 1
                local.get 4
                i32.shl
                local.tee 4
                i32.and
                if (result i32)  ;; label = @7
                  local.get 0
                  i32.const 8
                  i32.add
                  local.set 3
                  local.get 0
                  i32.load offset=8
                else
                  i32.const 3200
                  local.get 2
                  local.get 4
                  i32.or
                  i32.store
                  local.get 0
                  i32.const 8
                  i32.add
                  local.set 3
                  local.get 0
                end
                local.set 2
                local.get 3
                local.get 1
                i32.store
                local.get 2
                local.get 1
                i32.store offset=12
                local.get 1
                local.get 2
                i32.store offset=8
                local.get 1
                local.get 0
                i32.store offset=12
              end
              i32.const 3208
              local.get 6
              i32.store
              i32.const 3220
              local.get 12
              i32.store
            end
            local.get 10
            global.set 2
            local.get 5
            i32.const 8
            i32.add
            return
          else
            local.get 3
          end
        else
          local.get 3
        end
      else
        local.get 3
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
          local.set 8
          i32.const 3204
          i32.load
          local.tee 1
          if (result i32)  ;; label = @4
            i32.const 0
            local.get 8
            i32.sub
            local.set 2
            block  ;; label = @5
              block  ;; label = @6
                local.get 0
                i32.const 8
                i32.shr_u
                local.tee 0
                if (result i32)  ;; label = @7
                  local.get 8
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
                    local.tee 4
                    i32.shl
                    local.tee 3
                    i32.const 520192
                    i32.add
                    i32.const 16
                    i32.shr_u
                    i32.const 4
                    i32.and
                    local.set 0
                    local.get 8
                    i32.const 14
                    local.get 3
                    local.get 0
                    i32.shl
                    local.tee 3
                    i32.const 245760
                    i32.add
                    i32.const 16
                    i32.shr_u
                    i32.const 2
                    i32.and
                    local.tee 7
                    local.get 0
                    local.get 4
                    i32.or
                    i32.or
                    i32.sub
                    local.get 3
                    local.get 7
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
                local.tee 6
                i32.const 2
                i32.shl
                i32.const 3504
                i32.add
                i32.load
                local.tee 0
                if  ;; label = @7
                  local.get 8
                  i32.const 0
                  i32.const 25
                  local.get 6
                  i32.const 1
                  i32.shr_u
                  i32.sub
                  local.get 6
                  i32.const 31
                  i32.eq
                  select
                  i32.shl
                  local.set 4
                  i32.const 0
                  local.set 3
                  loop  ;; label = @8
                    local.get 0
                    i32.load offset=4
                    i32.const -8
                    i32.and
                    local.get 8
                    i32.sub
                    local.tee 7
                    local.get 2
                    i32.lt_u
                    if  ;; label = @9
                      local.get 7
                      if (result i32)  ;; label = @10
                        local.get 0
                        local.set 3
                        local.get 7
                      else
                        i32.const 0
                        local.set 3
                        local.get 0
                        local.set 2
                        br 4 (;@6;)
                      end
                      local.set 2
                    end
                    local.get 5
                    local.get 0
                    i32.load offset=20
                    local.tee 5
                    local.get 5
                    i32.eqz
                    local.get 5
                    local.get 0
                    i32.const 16
                    i32.add
                    local.get 4
                    i32.const 31
                    i32.shr_u
                    i32.const 2
                    i32.shl
                    i32.add
                    i32.load
                    local.tee 7
                    i32.eq
                    i32.or
                    select
                    local.set 0
                    local.get 4
                    i32.const 1
                    i32.shl
                    local.set 4
                    local.get 7
                    if  ;; label = @9
                      local.get 0
                      local.set 5
                      local.get 7
                      local.set 0
                      br 1 (;@8;)
                    end
                  end
                else
                  i32.const 0
                  local.set 0
                  i32.const 0
                  local.set 3
                end
                local.get 0
                local.get 3
                i32.or
                if (result i32)  ;; label = @7
                  local.get 0
                  local.set 4
                  local.get 3
                else
                  local.get 8
                  local.get 1
                  i32.const 2
                  local.get 6
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
                  local.tee 0
                  i32.const 12
                  i32.shr_u
                  i32.const 16
                  i32.and
                  local.tee 4
                  local.get 0
                  local.get 4
                  i32.shr_u
                  local.tee 0
                  i32.const 5
                  i32.shr_u
                  i32.const 8
                  i32.and
                  local.tee 4
                  i32.or
                  local.get 0
                  local.get 4
                  i32.shr_u
                  local.tee 0
                  i32.const 2
                  i32.shr_u
                  i32.const 4
                  i32.and
                  local.tee 4
                  i32.or
                  local.get 0
                  local.get 4
                  i32.shr_u
                  local.tee 0
                  i32.const 1
                  i32.shr_u
                  i32.const 2
                  i32.and
                  local.tee 4
                  i32.or
                  local.get 0
                  local.get 4
                  i32.shr_u
                  local.tee 0
                  i32.const 1
                  i32.shr_u
                  i32.const 1
                  i32.and
                  local.tee 4
                  i32.or
                  local.get 0
                  local.get 4
                  i32.shr_u
                  i32.add
                  i32.const 2
                  i32.shl
                  i32.const 3504
                  i32.add
                  i32.load
                  local.set 4
                  i32.const 0
                end
                local.set 0
                local.get 4
                if (result i32)  ;; label = @7
                  local.get 2
                  local.set 3
                  local.get 4
                  local.set 2
                  br 1 (;@6;)
                else
                  local.get 0
                  local.set 4
                  local.get 2
                end
                local.set 3
                br 1 (;@5;)
              end
              local.get 0
              local.set 4
              loop  ;; label = @6
                local.get 2
                i32.load offset=4
                i32.const -8
                i32.and
                local.get 8
                i32.sub
                local.tee 7
                local.get 3
                i32.lt_u
                local.set 5
                local.get 7
                local.get 3
                local.get 5
                select
                local.set 3
                local.get 2
                local.get 4
                local.get 5
                select
                local.set 4
                local.get 2
                i32.load offset=16
                local.tee 0
                i32.eqz
                if  ;; label = @7
                  local.get 2
                  i32.load offset=20
                  local.set 0
                end
                local.get 0
                if  ;; label = @7
                  local.get 0
                  local.set 2
                  br 1 (;@6;)
                end
              end
            end
            local.get 4
            if (result i32)  ;; label = @5
              local.get 3
              i32.const 3208
              i32.load
              local.get 8
              i32.sub
              i32.lt_u
              if (result i32)  ;; label = @6
                local.get 4
                local.get 8
                i32.add
                local.tee 7
                local.get 4
                i32.gt_u
                if (result i32)  ;; label = @7
                  local.get 4
                  i32.load offset=24
                  local.set 9
                  local.get 4
                  i32.load offset=12
                  local.tee 0
                  local.get 4
                  i32.eq
                  if  ;; label = @8
                    block  ;; label = @9
                      local.get 4
                      i32.const 20
                      i32.add
                      local.tee 2
                      i32.load
                      local.tee 0
                      i32.eqz
                      if  ;; label = @10
                        local.get 4
                        i32.const 16
                        i32.add
                        local.tee 2
                        i32.load
                        local.tee 0
                        i32.eqz
                        if  ;; label = @11
                          i32.const 0
                          local.set 0
                          br 2 (;@9;)
                        end
                      end
                      loop  ;; label = @10
                        block  ;; label = @11
                          local.get 0
                          i32.const 20
                          i32.add
                          local.tee 5
                          i32.load
                          local.tee 6
                          i32.eqz
                          if  ;; label = @12
                            local.get 0
                            i32.const 16
                            i32.add
                            local.tee 5
                            i32.load
                            local.tee 6
                            i32.eqz
                            br_if 1 (;@11;)
                          end
                          local.get 5
                          local.set 2
                          local.get 6
                          local.set 0
                          br 1 (;@10;)
                        end
                      end
                      local.get 2
                      i32.const 0
                      i32.store
                    end
                  else
                    local.get 4
                    i32.load offset=8
                    local.tee 2
                    local.get 0
                    i32.store offset=12
                    local.get 0
                    local.get 2
                    i32.store offset=8
                  end
                  local.get 9
                  if  ;; label = @8
                    block  ;; label = @9
                      local.get 4
                      i32.load offset=28
                      local.tee 2
                      i32.const 2
                      i32.shl
                      i32.const 3504
                      i32.add
                      local.tee 5
                      i32.load
                      local.get 4
                      i32.eq
                      if  ;; label = @10
                        local.get 5
                        local.get 0
                        i32.store
                        local.get 0
                        i32.eqz
                        if  ;; label = @11
                          i32.const 3204
                          local.get 1
                          i32.const 1
                          local.get 2
                          i32.shl
                          i32.const -1
                          i32.xor
                          i32.and
                          local.tee 0
                          i32.store
                          br 2 (;@9;)
                        end
                      else
                        local.get 9
                        i32.const 16
                        i32.add
                        local.get 9
                        i32.const 20
                        i32.add
                        local.get 9
                        i32.load offset=16
                        local.get 4
                        i32.eq
                        select
                        local.get 0
                        i32.store
                        local.get 0
                        i32.eqz
                        if  ;; label = @11
                          local.get 1
                          local.set 0
                          br 2 (;@9;)
                        end
                      end
                      local.get 0
                      local.get 9
                      i32.store offset=24
                      local.get 4
                      i32.load offset=16
                      local.tee 2
                      if  ;; label = @10
                        local.get 0
                        local.get 2
                        i32.store offset=16
                        local.get 2
                        local.get 0
                        i32.store offset=24
                      end
                      local.get 4
                      i32.load offset=20
                      local.tee 2
                      if  ;; label = @10
                        local.get 0
                        local.get 2
                        i32.store offset=20
                        local.get 2
                        local.get 0
                        i32.store offset=24
                      end
                      local.get 1
                      local.set 0
                    end
                  else
                    local.get 1
                    local.set 0
                  end
                  local.get 3
                  i32.const 16
                  i32.lt_u
                  if  ;; label = @8
                    local.get 4
                    local.get 3
                    local.get 8
                    i32.add
                    local.tee 0
                    i32.const 3
                    i32.or
                    i32.store offset=4
                    local.get 0
                    local.get 4
                    i32.add
                    local.tee 0
                    local.get 0
                    i32.load offset=4
                    i32.const 1
                    i32.or
                    i32.store offset=4
                  else
                    block  ;; label = @9
                      local.get 4
                      local.get 8
                      i32.const 3
                      i32.or
                      i32.store offset=4
                      local.get 7
                      local.get 3
                      i32.const 1
                      i32.or
                      i32.store offset=4
                      local.get 3
                      local.get 7
                      i32.add
                      local.get 3
                      i32.store
                      local.get 3
                      i32.const 3
                      i32.shr_u
                      local.set 1
                      local.get 3
                      i32.const 256
                      i32.lt_u
                      if  ;; label = @10
                        local.get 1
                        i32.const 3
                        i32.shl
                        i32.const 3240
                        i32.add
                        local.set 0
                        i32.const 3200
                        i32.load
                        local.tee 2
                        i32.const 1
                        local.get 1
                        i32.shl
                        local.tee 1
                        i32.and
                        if (result i32)  ;; label = @11
                          local.get 0
                          i32.const 8
                          i32.add
                          local.set 2
                          local.get 0
                          i32.load offset=8
                        else
                          i32.const 3200
                          local.get 1
                          local.get 2
                          i32.or
                          i32.store
                          local.get 0
                          i32.const 8
                          i32.add
                          local.set 2
                          local.get 0
                        end
                        local.set 1
                        local.get 2
                        local.get 7
                        i32.store
                        local.get 1
                        local.get 7
                        i32.store offset=12
                        local.get 7
                        local.get 1
                        i32.store offset=8
                        local.get 7
                        local.get 0
                        i32.store offset=12
                        br 1 (;@9;)
                      end
                      local.get 3
                      i32.const 8
                      i32.shr_u
                      local.tee 1
                      if (result i32)  ;; label = @10
                        local.get 3
                        i32.const 16777215
                        i32.gt_u
                        if (result i32)  ;; label = @11
                          i32.const 31
                        else
                          local.get 1
                          local.get 1
                          i32.const 1048320
                          i32.add
                          i32.const 16
                          i32.shr_u
                          i32.const 8
                          i32.and
                          local.tee 2
                          i32.shl
                          local.tee 5
                          i32.const 520192
                          i32.add
                          i32.const 16
                          i32.shr_u
                          i32.const 4
                          i32.and
                          local.set 1
                          local.get 3
                          i32.const 14
                          local.get 5
                          local.get 1
                          i32.shl
                          local.tee 5
                          i32.const 245760
                          i32.add
                          i32.const 16
                          i32.shr_u
                          i32.const 2
                          i32.and
                          local.tee 6
                          local.get 1
                          local.get 2
                          i32.or
                          i32.or
                          i32.sub
                          local.get 5
                          local.get 6
                          i32.shl
                          i32.const 15
                          i32.shr_u
                          i32.add
                          local.tee 1
                          i32.const 7
                          i32.add
                          i32.shr_u
                          i32.const 1
                          i32.and
                          local.get 1
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
                      i32.const 3504
                      i32.add
                      local.set 2
                      local.get 7
                      local.get 1
                      i32.store offset=28
                      local.get 7
                      i32.const 0
                      i32.store offset=20
                      local.get 7
                      i32.const 0
                      i32.store offset=16
                      local.get 0
                      i32.const 1
                      local.get 1
                      i32.shl
                      local.tee 5
                      i32.and
                      i32.eqz
                      if  ;; label = @10
                        i32.const 3204
                        local.get 0
                        local.get 5
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
                        br 1 (;@9;)
                      end
                      local.get 2
                      i32.load
                      local.tee 0
                      i32.load offset=4
                      i32.const -8
                      i32.and
                      local.get 3
                      i32.eq
                      if  ;; label = @10
                        local.get 0
                        local.set 1
                      else
                        block  ;; label = @11
                          local.get 3
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
                          local.set 2
                          loop  ;; label = @12
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
                            if  ;; label = @13
                              local.get 2
                              i32.const 1
                              i32.shl
                              local.set 2
                              local.get 1
                              i32.load offset=4
                              i32.const -8
                              i32.and
                              local.get 3
                              i32.eq
                              br_if 2 (;@11;)
                              local.get 1
                              local.set 0
                              br 1 (;@12;)
                            end
                          end
                          local.get 5
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
                          br 2 (;@9;)
                        end
                      end
                      local.get 1
                      i32.load offset=8
                      local.tee 0
                      local.get 7
                      i32.store offset=12
                      local.get 1
                      local.get 7
                      i32.store offset=8
                      local.get 7
                      local.get 0
                      i32.store offset=8
                      local.get 7
                      local.get 1
                      i32.store offset=12
                      local.get 7
                      i32.const 0
                      i32.store offset=24
                    end
                  end
                  local.get 10
                  global.set 2
                  local.get 4
                  i32.const 8
                  i32.add
                  return
                else
                  local.get 8
                end
              else
                local.get 8
              end
            else
              local.get 8
            end
          else
            local.get 8
          end
        end
      end
    end
    local.set 5
    block  ;; label = @1
      block  ;; label = @2
        i32.const 3208
        i32.load
        local.tee 0
        local.get 5
        i32.ge_u
        if  ;; label = @3
          i32.const 3220
          i32.load
          local.set 1
          local.get 0
          local.get 5
          i32.sub
          local.tee 2
          i32.const 15
          i32.gt_u
          if  ;; label = @4
            i32.const 3220
            local.get 1
            local.get 5
            i32.add
            local.tee 4
            i32.store
            i32.const 3208
            local.get 2
            i32.store
            local.get 4
            local.get 2
            i32.const 1
            i32.or
            i32.store offset=4
            local.get 0
            local.get 1
            i32.add
            local.get 2
            i32.store
            local.get 1
            local.get 5
            i32.const 3
            i32.or
            i32.store offset=4
          else
            i32.const 3208
            i32.const 0
            i32.store
            i32.const 3220
            i32.const 0
            i32.store
            local.get 1
            local.get 0
            i32.const 3
            i32.or
            i32.store offset=4
            local.get 0
            local.get 1
            i32.add
            local.tee 0
            local.get 0
            i32.load offset=4
            i32.const 1
            i32.or
            i32.store offset=4
          end
          br 1 (;@2;)
        end
        block  ;; label = @3
          i32.const 3212
          i32.load
          local.tee 1
          local.get 5
          i32.gt_u
          if  ;; label = @4
            i32.const 3212
            local.get 1
            local.get 5
            i32.sub
            local.tee 2
            i32.store
            br 1 (;@3;)
          end
          local.get 10
          local.set 0
          i32.const 3672
          i32.load
          if (result i32)  ;; label = @4
            i32.const 3680
            i32.load
          else
            i32.const 3680
            i32.const 4096
            i32.store
            i32.const 3676
            i32.const 4096
            i32.store
            i32.const 3684
            i32.const -1
            i32.store
            i32.const 3688
            i32.const -1
            i32.store
            i32.const 3692
            i32.const 0
            i32.store
            i32.const 3644
            i32.const 0
            i32.store
            i32.const 3672
            local.get 0
            i32.const -16
            i32.and
            i32.const 1431655768
            i32.xor
            i32.store
            i32.const 4096
          end
          local.tee 0
          local.get 5
          i32.const 47
          i32.add
          local.tee 7
          i32.add
          local.tee 2
          i32.const 0
          local.get 0
          i32.sub
          local.tee 6
          i32.and
          local.tee 4
          local.get 5
          i32.le_u
          if  ;; label = @4
            br 3 (;@1;)
          end
          i32.const 3640
          i32.load
          local.tee 0
          if  ;; label = @4
            i32.const 3632
            i32.load
            local.tee 3
            local.get 4
            i32.add
            local.tee 8
            local.get 3
            i32.le_u
            local.get 8
            local.get 0
            i32.gt_u
            i32.or
            if  ;; label = @5
              br 4 (;@1;)
            end
          end
          local.get 5
          i32.const 48
          i32.add
          local.set 8
          block  ;; label = @4
            block  ;; label = @5
              i32.const 3644
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
                      i32.const 3224
                      i32.load
                      local.tee 0
                      i32.eqz
                      br_if 0 (;@9;)
                      i32.const 3648
                      local.set 3
                      loop  ;; label = @10
                        block  ;; label = @11
                          local.get 3
                          i32.load
                          local.tee 9
                          local.get 0
                          i32.le_u
                          if  ;; label = @12
                            local.get 9
                            local.get 3
                            i32.load offset=4
                            i32.add
                            local.get 0
                            i32.gt_u
                            br_if 1 (;@11;)
                          end
                          local.get 3
                          i32.load offset=8
                          local.tee 3
                          br_if 1 (;@10;)
                          br 2 (;@9;)
                        end
                      end
                      local.get 2
                      local.get 1
                      i32.sub
                      local.get 6
                      i32.and
                      local.tee 2
                      i32.const 2147483647
                      i32.lt_u
                      if  ;; label = @10
                        local.get 2
                        call 14
                        local.set 1
                        local.get 1
                        local.get 3
                        i32.load
                        local.get 3
                        i32.load offset=4
                        i32.add
                        i32.ne
                        br_if 2 (;@8;)
                        local.get 1
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
                    call 14
                    local.tee 1
                    i32.const -1
                    i32.eq
                    if (result i32)  ;; label = @9
                      i32.const 0
                    else
                      i32.const 3632
                      i32.load
                      local.tee 3
                      local.get 1
                      i32.const 3676
                      i32.load
                      local.tee 0
                      i32.const -1
                      i32.add
                      local.tee 2
                      i32.add
                      i32.const 0
                      local.get 0
                      i32.sub
                      i32.and
                      local.get 1
                      i32.sub
                      i32.const 0
                      local.get 1
                      local.get 2
                      i32.and
                      select
                      local.get 4
                      i32.add
                      local.tee 2
                      i32.add
                      local.set 0
                      local.get 2
                      i32.const 2147483647
                      i32.lt_u
                      local.get 2
                      local.get 5
                      i32.gt_u
                      i32.and
                      if (result i32)  ;; label = @10
                        i32.const 3640
                        i32.load
                        local.tee 6
                        if  ;; label = @11
                          local.get 0
                          local.get 3
                          i32.le_u
                          local.get 0
                          local.get 6
                          i32.gt_u
                          i32.or
                          if  ;; label = @12
                            i32.const 0
                            local.set 2
                            br 5 (;@7;)
                          end
                        end
                        local.get 1
                        local.get 2
                        call 14
                        local.tee 0
                        i32.eq
                        br_if 5 (;@5;)
                        local.get 0
                        local.set 1
                        br 2 (;@8;)
                      else
                        i32.const 0
                      end
                    end
                    local.set 2
                    br 1 (;@7;)
                  end
                  local.get 1
                  i32.const -1
                  i32.ne
                  local.get 2
                  i32.const 2147483647
                  i32.lt_u
                  i32.and
                  local.get 8
                  local.get 2
                  i32.gt_u
                  i32.and
                  i32.eqz
                  if  ;; label = @8
                    local.get 1
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
                  i32.const 3680
                  i32.load
                  local.tee 0
                  local.get 7
                  local.get 2
                  i32.sub
                  i32.add
                  i32.const 0
                  local.get 0
                  i32.sub
                  i32.and
                  local.tee 0
                  i32.const 2147483647
                  i32.ge_u
                  br_if 2 (;@5;)
                  i32.const 0
                  local.get 2
                  i32.sub
                  local.set 3
                  local.get 0
                  call 14
                  i32.const -1
                  i32.eq
                  if (result i32)  ;; label = @8
                    local.get 3
                    call 14
                    drop
                    i32.const 0
                  else
                    local.get 0
                    local.get 2
                    i32.add
                    local.set 2
                    br 3 (;@5;)
                  end
                  local.set 2
                end
                i32.const 3644
                i32.const 3644
                i32.load
                i32.const 4
                i32.or
                i32.store
              end
              local.get 4
              i32.const 2147483647
              i32.lt_u
              if  ;; label = @6
                local.get 4
                call 14
                local.set 1
                i32.const 0
                call 14
                local.tee 0
                local.get 1
                i32.sub
                local.tee 3
                local.get 5
                i32.const 40
                i32.add
                i32.gt_u
                local.set 4
                local.get 3
                local.get 2
                local.get 4
                select
                local.set 2
                local.get 4
                i32.const 1
                i32.xor
                local.get 1
                i32.const -1
                i32.eq
                i32.or
                local.get 1
                i32.const -1
                i32.ne
                local.get 0
                i32.const -1
                i32.ne
                i32.and
                local.get 1
                local.get 0
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
            i32.const 3632
            i32.const 3632
            i32.load
            local.get 2
            i32.add
            local.tee 0
            i32.store
            local.get 0
            i32.const 3636
            i32.load
            i32.gt_u
            if  ;; label = @5
              i32.const 3636
              local.get 0
              i32.store
            end
            i32.const 3224
            i32.load
            local.tee 4
            if  ;; label = @5
              block  ;; label = @6
                i32.const 3648
                local.set 3
                block  ;; label = @7
                  block  ;; label = @8
                    loop  ;; label = @9
                      local.get 3
                      i32.load
                      local.tee 7
                      local.get 3
                      i32.load offset=4
                      local.tee 6
                      i32.add
                      local.get 1
                      i32.eq
                      br_if 1 (;@8;)
                      local.get 3
                      i32.load offset=8
                      local.tee 3
                      br_if 0 (;@9;)
                    end
                    br 1 (;@7;)
                  end
                  local.get 3
                  local.tee 0
                  i32.load offset=12
                  i32.const 8
                  i32.and
                  i32.eqz
                  if  ;; label = @8
                    local.get 7
                    local.get 4
                    i32.le_u
                    local.get 1
                    local.get 4
                    i32.gt_u
                    i32.and
                    if  ;; label = @9
                      local.get 0
                      local.get 2
                      local.get 6
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
                      local.set 0
                      i32.const 3212
                      i32.load
                      local.get 2
                      i32.add
                      local.tee 2
                      local.get 1
                      i32.sub
                      local.set 1
                      i32.const 3224
                      local.get 0
                      i32.store
                      i32.const 3212
                      local.get 1
                      i32.store
                      local.get 0
                      local.get 1
                      i32.const 1
                      i32.or
                      i32.store offset=4
                      local.get 2
                      local.get 4
                      i32.add
                      i32.const 40
                      i32.store offset=4
                      i32.const 3228
                      i32.const 3688
                      i32.load
                      i32.store
                      br 3 (;@6;)
                    end
                  end
                end
                local.get 1
                i32.const 3216
                i32.load
                i32.lt_u
                if  ;; label = @7
                  i32.const 3216
                  local.get 1
                  i32.store
                end
                local.get 1
                local.get 2
                i32.add
                local.set 0
                i32.const 3648
                local.set 3
                block  ;; label = @7
                  block  ;; label = @8
                    loop  ;; label = @9
                      local.get 3
                      i32.load
                      local.get 0
                      i32.eq
                      br_if 1 (;@8;)
                      local.get 3
                      i32.load offset=8
                      local.tee 3
                      br_if 0 (;@9;)
                    end
                    br 1 (;@7;)
                  end
                  local.get 3
                  i32.load offset=12
                  i32.const 8
                  i32.and
                  i32.eqz
                  if  ;; label = @8
                    local.get 3
                    local.get 1
                    i32.store
                    local.get 3
                    local.get 3
                    i32.load offset=4
                    local.get 2
                    i32.add
                    i32.store offset=4
                    local.get 1
                    i32.const 0
                    local.get 1
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
                    i32.add
                    local.tee 9
                    local.get 5
                    i32.add
                    local.set 6
                    local.get 0
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
                    i32.add
                    local.tee 2
                    local.get 9
                    i32.sub
                    local.get 5
                    i32.sub
                    local.set 3
                    local.get 9
                    local.get 5
                    i32.const 3
                    i32.or
                    i32.store offset=4
                    local.get 2
                    local.get 4
                    i32.eq
                    if  ;; label = @9
                      i32.const 3212
                      i32.const 3212
                      i32.load
                      local.get 3
                      i32.add
                      local.tee 0
                      i32.store
                      i32.const 3224
                      local.get 6
                      i32.store
                      local.get 6
                      local.get 0
                      i32.const 1
                      i32.or
                      i32.store offset=4
                    else
                      block  ;; label = @10
                        i32.const 3220
                        i32.load
                        local.get 2
                        i32.eq
                        if  ;; label = @11
                          i32.const 3208
                          i32.const 3208
                          i32.load
                          local.get 3
                          i32.add
                          local.tee 0
                          i32.store
                          i32.const 3220
                          local.get 6
                          i32.store
                          local.get 6
                          local.get 0
                          i32.const 1
                          i32.or
                          i32.store offset=4
                          local.get 0
                          local.get 6
                          i32.add
                          local.get 0
                          i32.store
                          br 1 (;@10;)
                        end
                        local.get 2
                        i32.load offset=4
                        local.tee 11
                        i32.const 3
                        i32.and
                        i32.const 1
                        i32.eq
                        if  ;; label = @11
                          local.get 11
                          i32.const 3
                          i32.shr_u
                          local.set 4
                          local.get 11
                          i32.const 256
                          i32.lt_u
                          if  ;; label = @12
                            local.get 2
                            i32.load offset=8
                            local.tee 0
                            local.get 2
                            i32.load offset=12
                            local.tee 1
                            i32.eq
                            if  ;; label = @13
                              i32.const 3200
                              i32.const 3200
                              i32.load
                              i32.const 1
                              local.get 4
                              i32.shl
                              i32.const -1
                              i32.xor
                              i32.and
                              i32.store
                            else
                              local.get 0
                              local.get 1
                              i32.store offset=12
                              local.get 1
                              local.get 0
                              i32.store offset=8
                            end
                          else
                            block  ;; label = @13
                              local.get 2
                              i32.load offset=24
                              local.set 8
                              local.get 2
                              i32.load offset=12
                              local.tee 0
                              local.get 2
                              i32.eq
                              if  ;; label = @14
                                block  ;; label = @15
                                  local.get 2
                                  local.tee 4
                                  i32.const 16
                                  i32.add
                                  local.tee 1
                                  i32.const 4
                                  i32.add
                                  local.tee 5
                                  i32.load
                                  local.tee 0
                                  if  ;; label = @16
                                    local.get 5
                                    local.set 1
                                  else
                                    local.get 4
                                    i32.load offset=16
                                    local.tee 0
                                    i32.eqz
                                    if  ;; label = @17
                                      i32.const 0
                                      local.set 0
                                      br 2 (;@15;)
                                    end
                                  end
                                  loop  ;; label = @16
                                    block  ;; label = @17
                                      local.get 0
                                      i32.const 20
                                      i32.add
                                      local.tee 7
                                      i32.load
                                      local.tee 4
                                      i32.eqz
                                      if  ;; label = @18
                                        local.get 0
                                        i32.const 16
                                        i32.add
                                        local.tee 7
                                        i32.load
                                        local.tee 4
                                        i32.eqz
                                        br_if 1 (;@17;)
                                      end
                                      local.get 7
                                      local.set 1
                                      local.get 4
                                      local.set 0
                                      br 1 (;@16;)
                                    end
                                  end
                                  local.get 1
                                  i32.const 0
                                  i32.store
                                end
                              else
                                local.get 2
                                i32.load offset=8
                                local.tee 1
                                local.get 0
                                i32.store offset=12
                                local.get 0
                                local.get 1
                                i32.store offset=8
                              end
                              local.get 8
                              i32.eqz
                              br_if 0 (;@13;)
                              local.get 2
                              i32.load offset=28
                              local.tee 1
                              i32.const 2
                              i32.shl
                              i32.const 3504
                              i32.add
                              local.tee 4
                              i32.load
                              local.get 2
                              i32.eq
                              if  ;; label = @14
                                block  ;; label = @15
                                  local.get 4
                                  local.get 0
                                  i32.store
                                  local.get 0
                                  br_if 0 (;@15;)
                                  i32.const 3204
                                  i32.const 3204
                                  i32.load
                                  i32.const 1
                                  local.get 1
                                  i32.shl
                                  i32.const -1
                                  i32.xor
                                  i32.and
                                  i32.store
                                  br 2 (;@13;)
                                end
                              else
                                local.get 8
                                i32.const 16
                                i32.add
                                local.get 8
                                i32.const 20
                                i32.add
                                local.get 8
                                i32.load offset=16
                                local.get 2
                                i32.eq
                                select
                                local.get 0
                                i32.store
                                local.get 0
                                i32.eqz
                                br_if 1 (;@13;)
                              end
                              local.get 0
                              local.get 8
                              i32.store offset=24
                              local.get 2
                              i32.load offset=16
                              local.tee 1
                              if  ;; label = @14
                                local.get 0
                                local.get 1
                                i32.store offset=16
                                local.get 1
                                local.get 0
                                i32.store offset=24
                              end
                              local.get 2
                              i32.load offset=20
                              local.tee 1
                              i32.eqz
                              br_if 0 (;@13;)
                              local.get 0
                              local.get 1
                              i32.store offset=20
                              local.get 1
                              local.get 0
                              i32.store offset=24
                            end
                          end
                          local.get 2
                          local.get 11
                          i32.const -8
                          i32.and
                          local.tee 0
                          i32.add
                          local.set 2
                          local.get 0
                          local.get 3
                          i32.add
                          local.set 3
                        end
                        local.get 2
                        local.get 2
                        i32.load offset=4
                        i32.const -2
                        i32.and
                        i32.store offset=4
                        local.get 6
                        local.get 3
                        i32.const 1
                        i32.or
                        i32.store offset=4
                        local.get 3
                        local.get 6
                        i32.add
                        local.get 3
                        i32.store
                        local.get 3
                        i32.const 3
                        i32.shr_u
                        local.set 1
                        local.get 3
                        i32.const 256
                        i32.lt_u
                        if  ;; label = @11
                          local.get 1
                          i32.const 3
                          i32.shl
                          i32.const 3240
                          i32.add
                          local.set 0
                          i32.const 3200
                          i32.load
                          local.tee 2
                          i32.const 1
                          local.get 1
                          i32.shl
                          local.tee 1
                          i32.and
                          if (result i32)  ;; label = @12
                            local.get 0
                            i32.const 8
                            i32.add
                            local.set 2
                            local.get 0
                            i32.load offset=8
                          else
                            i32.const 3200
                            local.get 1
                            local.get 2
                            i32.or
                            i32.store
                            local.get 0
                            i32.const 8
                            i32.add
                            local.set 2
                            local.get 0
                          end
                          local.set 1
                          local.get 2
                          local.get 6
                          i32.store
                          local.get 1
                          local.get 6
                          i32.store offset=12
                          local.get 6
                          local.get 1
                          i32.store offset=8
                          local.get 6
                          local.get 0
                          i32.store offset=12
                          br 1 (;@10;)
                        end
                        local.get 3
                        i32.const 8
                        i32.shr_u
                        local.tee 0
                        if (result i32)  ;; label = @11
                          local.get 3
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
                            local.tee 1
                            i32.shl
                            local.tee 2
                            i32.const 520192
                            i32.add
                            i32.const 16
                            i32.shr_u
                            i32.const 4
                            i32.and
                            local.set 0
                            local.get 3
                            i32.const 14
                            local.get 2
                            local.get 0
                            i32.shl
                            local.tee 2
                            i32.const 245760
                            i32.add
                            i32.const 16
                            i32.shr_u
                            i32.const 2
                            i32.and
                            local.tee 4
                            local.get 0
                            local.get 1
                            i32.or
                            i32.or
                            i32.sub
                            local.get 2
                            local.get 4
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
                        i32.const 3504
                        i32.add
                        local.set 0
                        local.get 6
                        local.get 1
                        i32.store offset=28
                        local.get 6
                        i32.const 0
                        i32.store offset=20
                        local.get 6
                        i32.const 0
                        i32.store offset=16
                        i32.const 3204
                        i32.load
                        local.tee 2
                        i32.const 1
                        local.get 1
                        i32.shl
                        local.tee 4
                        i32.and
                        i32.eqz
                        if  ;; label = @11
                          i32.const 3204
                          local.get 2
                          local.get 4
                          i32.or
                          i32.store
                          local.get 0
                          local.get 6
                          i32.store
                          local.get 6
                          local.get 0
                          i32.store offset=24
                          local.get 6
                          local.get 6
                          i32.store offset=12
                          local.get 6
                          local.get 6
                          i32.store offset=8
                          br 1 (;@10;)
                        end
                        local.get 0
                        i32.load
                        local.tee 0
                        i32.load offset=4
                        i32.const -8
                        i32.and
                        local.get 3
                        i32.eq
                        if  ;; label = @11
                          local.get 0
                          local.set 1
                        else
                          block  ;; label = @12
                            local.get 3
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
                              local.tee 4
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
                                local.get 3
                                i32.eq
                                br_if 2 (;@12;)
                                local.get 1
                                local.set 0
                                br 1 (;@13;)
                              end
                            end
                            local.get 4
                            local.get 6
                            i32.store
                            local.get 6
                            local.get 0
                            i32.store offset=24
                            local.get 6
                            local.get 6
                            i32.store offset=12
                            local.get 6
                            local.get 6
                            i32.store offset=8
                            br 2 (;@10;)
                          end
                        end
                        local.get 1
                        i32.load offset=8
                        local.tee 0
                        local.get 6
                        i32.store offset=12
                        local.get 1
                        local.get 6
                        i32.store offset=8
                        local.get 6
                        local.get 0
                        i32.store offset=8
                        local.get 6
                        local.get 1
                        i32.store offset=12
                        local.get 6
                        i32.const 0
                        i32.store offset=24
                      end
                    end
                    local.get 10
                    global.set 2
                    local.get 9
                    i32.const 8
                    i32.add
                    return
                  end
                end
                i32.const 3648
                local.set 3
                loop  ;; label = @7
                  block  ;; label = @8
                    local.get 3
                    i32.load
                    local.tee 0
                    local.get 4
                    i32.le_u
                    if  ;; label = @9
                      local.get 0
                      local.get 3
                      i32.load offset=4
                      i32.add
                      local.tee 7
                      local.get 4
                      i32.gt_u
                      br_if 1 (;@8;)
                    end
                    local.get 3
                    i32.load offset=8
                    local.set 3
                    br 1 (;@7;)
                  end
                end
                i32.const 3224
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
                local.tee 0
                local.get 1
                i32.add
                local.tee 3
                i32.store
                i32.const 3212
                local.get 2
                i32.const -40
                i32.add
                local.tee 6
                local.get 0
                i32.sub
                local.tee 0
                i32.store
                local.get 3
                local.get 0
                i32.const 1
                i32.or
                i32.store offset=4
                local.get 1
                local.get 6
                i32.add
                i32.const 40
                i32.store offset=4
                i32.const 3228
                i32.const 3688
                i32.load
                i32.store
                local.get 4
                i32.const 0
                local.get 7
                i32.const -47
                i32.add
                local.tee 0
                i32.const 8
                i32.add
                local.tee 3
                i32.sub
                i32.const 7
                i32.and
                i32.const 0
                local.get 3
                i32.const 7
                i32.and
                select
                local.get 0
                i32.add
                local.tee 0
                local.get 0
                local.get 4
                i32.const 16
                i32.add
                i32.lt_u
                select
                local.tee 3
                i32.const 27
                i32.store offset=4
                local.get 3
                i32.const 3648
                i64.load align=4
                i64.store offset=8 align=4
                local.get 3
                i32.const 3656
                i64.load align=4
                i64.store offset=16 align=4
                i32.const 3648
                local.get 1
                i32.store
                i32.const 3652
                local.get 2
                i32.store
                i32.const 3660
                i32.const 0
                i32.store
                i32.const 3656
                local.get 3
                i32.const 8
                i32.add
                i32.store
                local.get 3
                i32.const 24
                i32.add
                local.set 1
                loop  ;; label = @7
                  local.get 1
                  i32.const 4
                  i32.add
                  local.tee 0
                  i32.const 7
                  i32.store
                  local.get 1
                  i32.const 8
                  i32.add
                  local.get 7
                  i32.lt_u
                  if  ;; label = @8
                    local.get 0
                    local.set 1
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
                  local.tee 0
                  i32.const 1
                  i32.or
                  i32.store offset=4
                  local.get 3
                  local.get 0
                  i32.store
                  local.get 0
                  i32.const 3
                  i32.shr_u
                  local.set 1
                  local.get 0
                  i32.const 256
                  i32.lt_u
                  if  ;; label = @8
                    local.get 1
                    i32.const 3
                    i32.shl
                    i32.const 3240
                    i32.add
                    local.set 0
                    i32.const 3200
                    i32.load
                    local.tee 2
                    i32.const 1
                    local.get 1
                    i32.shl
                    local.tee 1
                    i32.and
                    if (result i32)  ;; label = @9
                      local.get 0
                      i32.const 8
                      i32.add
                      local.set 3
                      local.get 0
                      i32.load offset=8
                    else
                      i32.const 3200
                      local.get 1
                      local.get 2
                      i32.or
                      i32.store
                      local.get 0
                      i32.const 8
                      i32.add
                      local.set 3
                      local.get 0
                    end
                    local.set 1
                    local.get 3
                    local.get 4
                    i32.store
                    local.get 1
                    local.get 4
                    i32.store offset=12
                    local.get 4
                    local.get 1
                    i32.store offset=8
                    local.get 4
                    local.get 0
                    i32.store offset=12
                    br 2 (;@6;)
                  end
                  local.get 0
                  i32.const 8
                  i32.shr_u
                  local.tee 1
                  if (result i32)  ;; label = @8
                    local.get 0
                    i32.const 16777215
                    i32.gt_u
                    if (result i32)  ;; label = @9
                      i32.const 31
                    else
                      local.get 1
                      local.get 1
                      i32.const 1048320
                      i32.add
                      i32.const 16
                      i32.shr_u
                      i32.const 8
                      i32.and
                      local.tee 2
                      i32.shl
                      local.tee 3
                      i32.const 520192
                      i32.add
                      i32.const 16
                      i32.shr_u
                      i32.const 4
                      i32.and
                      local.set 1
                      local.get 0
                      i32.const 14
                      local.get 3
                      local.get 1
                      i32.shl
                      local.tee 3
                      i32.const 245760
                      i32.add
                      i32.const 16
                      i32.shr_u
                      i32.const 2
                      i32.and
                      local.tee 7
                      local.get 1
                      local.get 2
                      i32.or
                      i32.or
                      i32.sub
                      local.get 3
                      local.get 7
                      i32.shl
                      i32.const 15
                      i32.shr_u
                      i32.add
                      local.tee 1
                      i32.const 7
                      i32.add
                      i32.shr_u
                      i32.const 1
                      i32.and
                      local.get 1
                      i32.const 1
                      i32.shl
                      i32.or
                    end
                  else
                    i32.const 0
                  end
                  local.tee 2
                  i32.const 2
                  i32.shl
                  i32.const 3504
                  i32.add
                  local.set 1
                  local.get 4
                  local.get 2
                  i32.store offset=28
                  local.get 4
                  i32.const 0
                  i32.store offset=20
                  local.get 4
                  i32.const 0
                  i32.store offset=16
                  i32.const 3204
                  i32.load
                  local.tee 3
                  i32.const 1
                  local.get 2
                  i32.shl
                  local.tee 7
                  i32.and
                  i32.eqz
                  if  ;; label = @8
                    i32.const 3204
                    local.get 3
                    local.get 7
                    i32.or
                    i32.store
                    local.get 1
                    local.get 4
                    i32.store
                    local.get 4
                    local.get 1
                    i32.store offset=24
                    local.get 4
                    local.get 4
                    i32.store offset=12
                    local.get 4
                    local.get 4
                    i32.store offset=8
                    br 2 (;@6;)
                  end
                  local.get 1
                  i32.load
                  local.tee 1
                  i32.load offset=4
                  i32.const -8
                  i32.and
                  local.get 0
                  i32.eq
                  if  ;; label = @8
                    local.get 1
                    local.set 2
                  else
                    block  ;; label = @9
                      local.get 0
                      i32.const 0
                      i32.const 25
                      local.get 2
                      i32.const 1
                      i32.shr_u
                      i32.sub
                      local.get 2
                      i32.const 31
                      i32.eq
                      select
                      i32.shl
                      local.set 3
                      loop  ;; label = @10
                        local.get 1
                        i32.const 16
                        i32.add
                        local.get 3
                        i32.const 31
                        i32.shr_u
                        i32.const 2
                        i32.shl
                        i32.add
                        local.tee 7
                        i32.load
                        local.tee 2
                        if  ;; label = @11
                          local.get 3
                          i32.const 1
                          i32.shl
                          local.set 3
                          local.get 2
                          i32.load offset=4
                          i32.const -8
                          i32.and
                          local.get 0
                          i32.eq
                          br_if 2 (;@9;)
                          local.get 2
                          local.set 1
                          br 1 (;@10;)
                        end
                      end
                      local.get 7
                      local.get 4
                      i32.store
                      local.get 4
                      local.get 1
                      i32.store offset=24
                      local.get 4
                      local.get 4
                      i32.store offset=12
                      local.get 4
                      local.get 4
                      i32.store offset=8
                      br 3 (;@6;)
                    end
                  end
                  local.get 2
                  i32.load offset=8
                  local.tee 0
                  local.get 4
                  i32.store offset=12
                  local.get 2
                  local.get 4
                  i32.store offset=8
                  local.get 4
                  local.get 0
                  i32.store offset=8
                  local.get 4
                  local.get 2
                  i32.store offset=12
                  local.get 4
                  i32.const 0
                  i32.store offset=24
                end
              end
            else
              i32.const 3216
              i32.load
              local.tee 0
              i32.eqz
              local.get 1
              local.get 0
              i32.lt_u
              i32.or
              if  ;; label = @6
                i32.const 3216
                local.get 1
                i32.store
              end
              i32.const 3648
              local.get 1
              i32.store
              i32.const 3652
              local.get 2
              i32.store
              i32.const 3660
              i32.const 0
              i32.store
              i32.const 3236
              i32.const 3672
              i32.load
              i32.store
              i32.const 3232
              i32.const -1
              i32.store
              i32.const 3252
              i32.const 3240
              i32.store
              i32.const 3248
              i32.const 3240
              i32.store
              i32.const 3260
              i32.const 3248
              i32.store
              i32.const 3256
              i32.const 3248
              i32.store
              i32.const 3268
              i32.const 3256
              i32.store
              i32.const 3264
              i32.const 3256
              i32.store
              i32.const 3276
              i32.const 3264
              i32.store
              i32.const 3272
              i32.const 3264
              i32.store
              i32.const 3284
              i32.const 3272
              i32.store
              i32.const 3280
              i32.const 3272
              i32.store
              i32.const 3292
              i32.const 3280
              i32.store
              i32.const 3288
              i32.const 3280
              i32.store
              i32.const 3300
              i32.const 3288
              i32.store
              i32.const 3296
              i32.const 3288
              i32.store
              i32.const 3308
              i32.const 3296
              i32.store
              i32.const 3304
              i32.const 3296
              i32.store
              i32.const 3316
              i32.const 3304
              i32.store
              i32.const 3312
              i32.const 3304
              i32.store
              i32.const 3324
              i32.const 3312
              i32.store
              i32.const 3320
              i32.const 3312
              i32.store
              i32.const 3332
              i32.const 3320
              i32.store
              i32.const 3328
              i32.const 3320
              i32.store
              i32.const 3340
              i32.const 3328
              i32.store
              i32.const 3336
              i32.const 3328
              i32.store
              i32.const 3348
              i32.const 3336
              i32.store
              i32.const 3344
              i32.const 3336
              i32.store
              i32.const 3356
              i32.const 3344
              i32.store
              i32.const 3352
              i32.const 3344
              i32.store
              i32.const 3364
              i32.const 3352
              i32.store
              i32.const 3360
              i32.const 3352
              i32.store
              i32.const 3372
              i32.const 3360
              i32.store
              i32.const 3368
              i32.const 3360
              i32.store
              i32.const 3380
              i32.const 3368
              i32.store
              i32.const 3376
              i32.const 3368
              i32.store
              i32.const 3388
              i32.const 3376
              i32.store
              i32.const 3384
              i32.const 3376
              i32.store
              i32.const 3396
              i32.const 3384
              i32.store
              i32.const 3392
              i32.const 3384
              i32.store
              i32.const 3404
              i32.const 3392
              i32.store
              i32.const 3400
              i32.const 3392
              i32.store
              i32.const 3412
              i32.const 3400
              i32.store
              i32.const 3408
              i32.const 3400
              i32.store
              i32.const 3420
              i32.const 3408
              i32.store
              i32.const 3416
              i32.const 3408
              i32.store
              i32.const 3428
              i32.const 3416
              i32.store
              i32.const 3424
              i32.const 3416
              i32.store
              i32.const 3436
              i32.const 3424
              i32.store
              i32.const 3432
              i32.const 3424
              i32.store
              i32.const 3444
              i32.const 3432
              i32.store
              i32.const 3440
              i32.const 3432
              i32.store
              i32.const 3452
              i32.const 3440
              i32.store
              i32.const 3448
              i32.const 3440
              i32.store
              i32.const 3460
              i32.const 3448
              i32.store
              i32.const 3456
              i32.const 3448
              i32.store
              i32.const 3468
              i32.const 3456
              i32.store
              i32.const 3464
              i32.const 3456
              i32.store
              i32.const 3476
              i32.const 3464
              i32.store
              i32.const 3472
              i32.const 3464
              i32.store
              i32.const 3484
              i32.const 3472
              i32.store
              i32.const 3480
              i32.const 3472
              i32.store
              i32.const 3492
              i32.const 3480
              i32.store
              i32.const 3488
              i32.const 3480
              i32.store
              i32.const 3500
              i32.const 3488
              i32.store
              i32.const 3496
              i32.const 3488
              i32.store
              i32.const 3224
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
              local.tee 0
              local.get 1
              i32.add
              local.tee 4
              i32.store
              i32.const 3212
              local.get 2
              i32.const -40
              i32.add
              local.tee 2
              local.get 0
              i32.sub
              local.tee 0
              i32.store
              local.get 4
              local.get 0
              i32.const 1
              i32.or
              i32.store offset=4
              local.get 1
              local.get 2
              i32.add
              i32.const 40
              i32.store offset=4
              i32.const 3228
              i32.const 3688
              i32.load
              i32.store
            end
            i32.const 3212
            i32.load
            local.tee 0
            local.get 5
            i32.gt_u
            if  ;; label = @5
              i32.const 3212
              local.get 0
              local.get 5
              i32.sub
              local.tee 2
              i32.store
              br 2 (;@3;)
            end
          end
          i32.const 3120
          i32.const 12
          i32.store
          br 2 (;@1;)
        end
        i32.const 3224
        i32.const 3224
        i32.load
        local.tee 1
        local.get 5
        i32.add
        local.tee 0
        i32.store
        local.get 0
        local.get 2
        i32.const 1
        i32.or
        i32.store offset=4
        local.get 1
        local.get 5
        i32.const 3
        i32.or
        i32.store offset=4
      end
      local.get 10
      global.set 2
      local.get 1
      i32.const 8
      i32.add
      return
    end
    local.get 10
    global.set 2
    i32.const 0)
  (func (;22;) (type 4) (param i32) (result i32)
    (local i32 i32 i32)
    block (result i32)  ;; label = @1
      block  ;; label = @2
        local.get 0
        i32.load offset=20
        local.get 0
        i32.load offset=28
        i32.le_u
        br_if 0 (;@2;)
        local.get 0
        i32.load offset=36
        local.set 1
        local.get 0
        i32.const 0
        i32.const 0
        local.get 1
        i32.const 1
        i32.and
        i32.const 4
        i32.add
        call_indirect (type 0)
        drop
        local.get 0
        i32.load offset=20
        br_if 0 (;@2;)
        i32.const -1
        br 1 (;@1;)
      end
      local.get 0
      i32.load offset=4
      local.tee 1
      local.get 0
      i32.load offset=8
      local.tee 2
      i32.lt_u
      if  ;; label = @2
        local.get 0
        i32.load offset=40
        local.set 3
        local.get 0
        local.get 1
        local.get 2
        i32.sub
        i64.extend_i32_s
        i32.const 1
        local.get 3
        i32.const 1
        i32.and
        i32.const 6
        i32.add
        call_indirect (type 3)
        drop
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
      i32.const 0
      i32.store offset=8
      local.get 0
      i32.const 0
      i32.store offset=4
      i32.const 0
    end)
  (func (;23;) (type 12) (param i32 i32 i32 i32 i32) (result i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i64)
    global.get 2
    local.set 15
    global.get 2
    i32.const -64
    i32.sub
    global.set 2
    local.get 15
    i32.const 40
    i32.add
    local.set 9
    local.get 15
    i32.const 48
    i32.add
    local.set 24
    local.get 15
    i32.const 60
    i32.add
    local.set 21
    local.get 15
    i32.const 56
    i32.add
    local.tee 11
    local.get 1
    i32.store
    local.get 0
    i32.const 0
    i32.ne
    local.set 18
    local.get 15
    i32.const 40
    i32.add
    local.tee 20
    local.set 19
    local.get 15
    i32.const 39
    i32.add
    local.set 22
    i32.const 0
    local.set 1
    block  ;; label = @1
      block  ;; label = @2
        loop  ;; label = @3
          block  ;; label = @4
            loop  ;; label = @5
              local.get 8
              i32.const -1
              i32.gt_s
              if  ;; label = @6
                local.get 1
                i32.const 2147483647
                local.get 8
                i32.sub
                i32.gt_s
                if (result i32)  ;; label = @7
                  i32.const 3120
                  i32.const 75
                  i32.store
                  i32.const -1
                else
                  local.get 1
                  local.get 8
                  i32.add
                end
                local.set 8
              end
              local.get 11
              i32.load
              local.tee 10
              i32.load8_s
              local.tee 5
              i32.eqz
              br_if 3 (;@2;)
              local.get 10
              local.set 1
              block  ;; label = @6
                block  ;; label = @7
                  loop  ;; label = @8
                    block  ;; label = @9
                      block  ;; label = @10
                        local.get 5
                        i32.const 24
                        i32.shl
                        i32.const 24
                        i32.shr_s
                        local.tee 5
                        if  ;; label = @11
                          local.get 5
                          i32.const 37
                          i32.ne
                          br_if 1 (;@10;)
                          br 4 (;@7;)
                        end
                        br 1 (;@9;)
                      end
                      local.get 11
                      local.get 1
                      i32.const 1
                      i32.add
                      local.tee 1
                      i32.store
                      local.get 1
                      i32.load8_s
                      local.set 5
                      br 1 (;@8;)
                    end
                  end
                  br 1 (;@6;)
                end
                local.get 1
                local.set 5
                loop  ;; label = @7
                  local.get 5
                  i32.load8_s offset=1
                  i32.const 37
                  i32.ne
                  br_if 1 (;@6;)
                  local.get 1
                  i32.const 1
                  i32.add
                  local.set 1
                  local.get 11
                  local.get 5
                  i32.const 2
                  i32.add
                  local.tee 5
                  i32.store
                  local.get 5
                  i32.load8_s
                  i32.const 37
                  i32.eq
                  br_if 0 (;@7;)
                end
              end
              local.get 1
              local.get 10
              i32.sub
              local.set 1
              local.get 18
              if  ;; label = @6
                local.get 0
                local.get 10
                local.get 1
                call 11
              end
              local.get 1
              br_if 0 (;@5;)
            end
            local.get 11
            i32.load
            i32.load8_s offset=1
            call 18
            i32.eqz
            local.set 5
            local.get 11
            local.get 11
            i32.load
            local.tee 1
            local.get 5
            if (result i32)  ;; label = @5
              i32.const -1
              local.set 17
              i32.const 1
            else
              local.get 1
              i32.load8_s offset=2
              i32.const 36
              i32.eq
              if (result i32)  ;; label = @6
                local.get 1
                i32.load8_s offset=1
                i32.const -48
                i32.add
                local.set 17
                i32.const 1
                local.set 6
                i32.const 3
              else
                i32.const -1
                local.set 17
                i32.const 1
              end
            end
            i32.add
            local.tee 1
            i32.store
            local.get 1
            i32.load8_s
            local.tee 7
            i32.const -32
            i32.add
            local.tee 5
            i32.const 31
            i32.gt_u
            i32.const 1
            local.get 5
            i32.shl
            i32.const 75913
            i32.and
            i32.eqz
            i32.or
            if  ;; label = @5
              i32.const 0
              local.set 5
            else
              i32.const 0
              local.set 7
              loop  ;; label = @6
                local.get 7
                i32.const 1
                local.get 5
                i32.shl
                i32.or
                local.set 5
                local.get 11
                local.get 1
                i32.const 1
                i32.add
                local.tee 1
                i32.store
                local.get 1
                i32.load8_s
                local.tee 7
                i32.const -32
                i32.add
                local.tee 12
                i32.const 31
                i32.gt_u
                i32.const 1
                local.get 12
                i32.shl
                i32.const 75913
                i32.and
                i32.eqz
                i32.or
                i32.eqz
                if  ;; label = @7
                  local.get 5
                  local.set 7
                  local.get 12
                  local.set 5
                  br 1 (;@6;)
                end
              end
            end
            local.get 7
            i32.const 255
            i32.and
            i32.const 42
            i32.eq
            if (result i32)  ;; label = @5
              block (result i32)  ;; label = @6
                block  ;; label = @7
                  local.get 1
                  i32.load8_s offset=1
                  call 18
                  i32.eqz
                  br_if 0 (;@7;)
                  local.get 11
                  i32.load
                  local.tee 1
                  i32.load8_s offset=2
                  i32.const 36
                  i32.ne
                  br_if 0 (;@7;)
                  local.get 1
                  i32.load8_s offset=1
                  i32.const -48
                  i32.add
                  i32.const 2
                  i32.shl
                  local.get 4
                  i32.add
                  i32.const 10
                  i32.store
                  i32.const 1
                  local.set 13
                  local.get 1
                  i32.const 3
                  i32.add
                  local.set 7
                  local.get 1
                  i32.load8_s offset=1
                  i32.const -48
                  i32.add
                  i32.const 3
                  i32.shl
                  local.get 3
                  i32.add
                  i64.load
                  i32.wrap_i64
                  br 1 (;@6;)
                end
                local.get 6
                if  ;; label = @7
                  i32.const -1
                  local.set 8
                  br 3 (;@4;)
                end
                local.get 18
                if  ;; label = @7
                  local.get 2
                  i32.load
                  i32.const 3
                  i32.add
                  i32.const -4
                  i32.and
                  local.tee 6
                  i32.load
                  local.set 1
                  local.get 2
                  local.get 6
                  i32.const 4
                  i32.add
                  i32.store
                else
                  i32.const 0
                  local.set 1
                end
                i32.const 0
                local.set 13
                local.get 11
                i32.load
                i32.const 1
                i32.add
                local.set 7
                local.get 1
              end
              local.set 6
              local.get 11
              local.get 7
              i32.store
              local.get 7
              local.set 1
              local.get 5
              i32.const 8192
              i32.or
              local.get 5
              local.get 6
              i32.const 0
              i32.lt_s
              local.tee 5
              select
              local.set 14
              i32.const 0
              local.get 6
              i32.sub
              local.get 6
              local.get 5
              select
              local.set 16
              local.get 13
            else
              local.get 11
              call 32
              local.tee 16
              i32.const 0
              i32.lt_s
              if  ;; label = @6
                i32.const -1
                local.set 8
                br 2 (;@4;)
              end
              local.get 11
              i32.load
              local.set 1
              local.get 5
              local.set 14
              local.get 6
            end
            local.set 23
            local.get 1
            i32.load8_s
            i32.const 46
            i32.eq
            if  ;; label = @5
              block  ;; label = @6
                local.get 1
                i32.const 1
                i32.add
                local.set 5
                local.get 1
                i32.load8_s offset=1
                i32.const 42
                i32.ne
                if  ;; label = @7
                  local.get 11
                  local.get 5
                  i32.store
                  local.get 11
                  call 32
                  local.set 1
                  local.get 11
                  i32.load
                  local.set 6
                  br 1 (;@6;)
                end
                local.get 1
                i32.load8_s offset=2
                call 18
                if  ;; label = @7
                  local.get 11
                  i32.load
                  local.tee 5
                  i32.load8_s offset=3
                  i32.const 36
                  i32.eq
                  if  ;; label = @8
                    local.get 5
                    i32.load8_s offset=2
                    i32.const -48
                    i32.add
                    i32.const 2
                    i32.shl
                    local.get 4
                    i32.add
                    i32.const 10
                    i32.store
                    local.get 5
                    i32.load8_s offset=2
                    i32.const -48
                    i32.add
                    i32.const 3
                    i32.shl
                    local.get 3
                    i32.add
                    i64.load
                    i32.wrap_i64
                    local.set 1
                    local.get 11
                    local.get 5
                    i32.const 4
                    i32.add
                    local.tee 6
                    i32.store
                    br 2 (;@6;)
                  end
                end
                local.get 23
                if  ;; label = @7
                  i32.const -1
                  local.set 8
                  br 3 (;@4;)
                end
                local.get 18
                if  ;; label = @7
                  local.get 2
                  i32.load
                  i32.const 3
                  i32.add
                  i32.const -4
                  i32.and
                  local.tee 5
                  i32.load
                  local.set 1
                  local.get 2
                  local.get 5
                  i32.const 4
                  i32.add
                  i32.store
                else
                  i32.const 0
                  local.set 1
                end
                local.get 11
                local.get 11
                i32.load
                i32.const 2
                i32.add
                local.tee 6
                i32.store
              end
            else
              local.get 1
              local.set 6
              i32.const -1
              local.set 1
            end
            i32.const 0
            local.set 12
            loop  ;; label = @5
              local.get 6
              i32.load8_s
              i32.const -65
              i32.add
              i32.const 57
              i32.gt_u
              if  ;; label = @6
                i32.const -1
                local.set 8
                br 2 (;@4;)
              end
              local.get 11
              local.get 6
              i32.const 1
              i32.add
              local.tee 7
              i32.store
              local.get 6
              i32.load8_s
              local.get 12
              i32.const 58
              i32.mul
              i32.add
              i32.load8_s offset=959
              local.tee 6
              i32.const 255
              i32.and
              local.tee 5
              i32.const -1
              i32.add
              i32.const 8
              i32.lt_u
              if  ;; label = @6
                local.get 7
                local.set 6
                local.get 5
                local.set 12
                br 1 (;@5;)
              end
            end
            local.get 6
            i32.eqz
            if  ;; label = @5
              i32.const -1
              local.set 8
              br 1 (;@4;)
            end
            local.get 17
            i32.const -1
            i32.gt_s
            local.set 13
            block  ;; label = @5
              block  ;; label = @6
                local.get 6
                i32.const 19
                i32.eq
                if  ;; label = @7
                  local.get 13
                  if  ;; label = @8
                    i32.const -1
                    local.set 8
                    br 4 (;@4;)
                  end
                else
                  block  ;; label = @8
                    local.get 13
                    if  ;; label = @9
                      local.get 17
                      i32.const 2
                      i32.shl
                      local.get 4
                      i32.add
                      local.get 5
                      i32.store
                      local.get 9
                      local.get 17
                      i32.const 3
                      i32.shl
                      local.get 3
                      i32.add
                      i64.load
                      i64.store
                      br 1 (;@8;)
                    end
                    local.get 18
                    i32.eqz
                    if  ;; label = @9
                      i32.const 0
                      local.set 8
                      br 5 (;@4;)
                    end
                    local.get 9
                    local.get 5
                    local.get 2
                    call 31
                    local.get 11
                    i32.load
                    local.set 7
                    br 2 (;@6;)
                  end
                end
                local.get 18
                br_if 0 (;@6;)
                i32.const 0
                local.set 1
                br 1 (;@5;)
              end
              local.get 14
              i32.const -65537
              i32.and
              local.tee 5
              local.get 14
              local.get 14
              i32.const 8192
              i32.and
              select
              local.set 6
              block  ;; label = @6
                block  ;; label = @7
                  block  ;; label = @8
                    block  ;; label = @9
                      block  ;; label = @10
                        block  ;; label = @11
                          block  ;; label = @12
                            block  ;; label = @13
                              block  ;; label = @14
                                block  ;; label = @15
                                  block  ;; label = @16
                                    block  ;; label = @17
                                      block  ;; label = @18
                                        block  ;; label = @19
                                          block  ;; label = @20
                                            block  ;; label = @21
                                              block  ;; label = @22
                                                local.get 7
                                                i32.const -1
                                                i32.add
                                                i32.load8_s
                                                local.tee 7
                                                i32.const -33
                                                i32.and
                                                local.get 7
                                                local.get 7
                                                i32.const 15
                                                i32.and
                                                i32.const 3
                                                i32.eq
                                                local.get 12
                                                i32.const 0
                                                i32.ne
                                                i32.and
                                                select
                                                local.tee 7
                                                i32.const 65
                                                i32.sub
                                                br_table 9 (;@13;) 10 (;@12;) 7 (;@15;) 10 (;@12;) 9 (;@13;) 9 (;@13;) 9 (;@13;) 10 (;@12;) 10 (;@12;) 10 (;@12;) 10 (;@12;) 10 (;@12;) 10 (;@12;) 10 (;@12;) 10 (;@12;) 10 (;@12;) 10 (;@12;) 10 (;@12;) 8 (;@14;) 10 (;@12;) 10 (;@12;) 10 (;@12;) 10 (;@12;) 11 (;@11;) 10 (;@12;) 10 (;@12;) 10 (;@12;) 10 (;@12;) 10 (;@12;) 10 (;@12;) 10 (;@12;) 10 (;@12;) 9 (;@13;) 10 (;@12;) 5 (;@17;) 3 (;@19;) 9 (;@13;) 9 (;@13;) 9 (;@13;) 10 (;@12;) 3 (;@19;) 10 (;@12;) 10 (;@12;) 10 (;@12;) 10 (;@12;) 0 (;@22;) 2 (;@20;) 1 (;@21;) 10 (;@12;) 10 (;@12;) 6 (;@16;) 10 (;@12;) 4 (;@18;) 10 (;@12;) 10 (;@12;) 11 (;@11;) 10 (;@12;)
                                              end
                                              block  ;; label = @22
                                                block  ;; label = @23
                                                  block  ;; label = @24
                                                    block  ;; label = @25
                                                      block  ;; label = @26
                                                        block  ;; label = @27
                                                          block  ;; label = @28
                                                            block  ;; label = @29
                                                              local.get 12
                                                              i32.const 255
                                                              i32.and
                                                              i32.const 24
                                                              i32.shl
                                                              i32.const 24
                                                              i32.shr_s
                                                              br_table 0 (;@29;) 1 (;@28;) 2 (;@27;) 3 (;@26;) 4 (;@25;) 7 (;@22;) 5 (;@24;) 6 (;@23;) 7 (;@22;)
                                                            end
                                                            local.get 9
                                                            i32.load
                                                            local.get 8
                                                            i32.store
                                                            i32.const 0
                                                            local.set 1
                                                            br 23 (;@5;)
                                                          end
                                                          local.get 9
                                                          i32.load
                                                          local.get 8
                                                          i32.store
                                                          i32.const 0
                                                          local.set 1
                                                          br 22 (;@5;)
                                                        end
                                                        local.get 9
                                                        i32.load
                                                        local.get 8
                                                        i64.extend_i32_s
                                                        i64.store
                                                        i32.const 0
                                                        local.set 1
                                                        br 21 (;@5;)
                                                      end
                                                      local.get 9
                                                      i32.load
                                                      local.get 8
                                                      i32.store16
                                                      i32.const 0
                                                      local.set 1
                                                      br 20 (;@5;)
                                                    end
                                                    local.get 9
                                                    i32.load
                                                    local.get 8
                                                    i32.store8
                                                    i32.const 0
                                                    local.set 1
                                                    br 19 (;@5;)
                                                  end
                                                  local.get 9
                                                  i32.load
                                                  local.get 8
                                                  i32.store
                                                  i32.const 0
                                                  local.set 1
                                                  br 18 (;@5;)
                                                end
                                                local.get 9
                                                i32.load
                                                local.get 8
                                                i64.extend_i32_s
                                                i64.store
                                                i32.const 0
                                                local.set 1
                                                br 17 (;@5;)
                                              end
                                              i32.const 0
                                              local.set 1
                                              br 16 (;@5;)
                                            end
                                            local.get 6
                                            i32.const 8
                                            i32.or
                                            local.set 6
                                            local.get 1
                                            i32.const 8
                                            local.get 1
                                            i32.const 8
                                            i32.gt_u
                                            select
                                            local.set 1
                                            i32.const 120
                                            local.set 7
                                            br 9 (;@11;)
                                          end
                                          local.get 1
                                          local.get 19
                                          local.get 9
                                          i64.load
                                          local.get 20
                                          call 46
                                          local.tee 14
                                          i32.sub
                                          local.tee 7
                                          i32.const 1
                                          i32.add
                                          local.get 6
                                          local.tee 5
                                          i32.const 8
                                          i32.and
                                          i32.eqz
                                          local.get 1
                                          local.get 7
                                          i32.gt_s
                                          i32.or
                                          select
                                          local.set 1
                                          i32.const 0
                                          local.set 13
                                          i32.const 2014
                                          local.set 12
                                          br 11 (;@8;)
                                        end
                                        local.get 9
                                        i64.load
                                        local.tee 25
                                        i64.const 0
                                        i64.lt_s
                                        if (result i32)  ;; label = @19
                                          local.get 9
                                          i64.const 0
                                          local.get 25
                                          i64.sub
                                          local.tee 25
                                          i64.store
                                          i32.const 1
                                          local.set 13
                                          i32.const 2014
                                        else
                                          local.get 6
                                          i32.const 2049
                                          i32.and
                                          i32.const 0
                                          i32.ne
                                          local.set 13
                                          i32.const 2015
                                          i32.const 2016
                                          i32.const 2014
                                          local.get 6
                                          i32.const 1
                                          i32.and
                                          select
                                          local.get 6
                                          i32.const 2048
                                          i32.and
                                          select
                                        end
                                        local.set 12
                                        br 8 (;@10;)
                                      end
                                      local.get 9
                                      i64.load
                                      local.set 25
                                      i32.const 0
                                      local.set 13
                                      i32.const 2014
                                      local.set 12
                                      br 7 (;@10;)
                                    end
                                    local.get 22
                                    local.get 9
                                    i64.load
                                    i64.store8
                                    local.get 22
                                    local.set 7
                                    local.get 5
                                    local.set 6
                                    i32.const 1
                                    local.set 5
                                    i32.const 0
                                    local.set 13
                                    i32.const 2014
                                    local.set 12
                                    local.get 19
                                    local.set 1
                                    br 10 (;@6;)
                                  end
                                  local.get 9
                                  i32.load
                                  local.tee 6
                                  i32.const 2024
                                  local.get 6
                                  select
                                  local.tee 7
                                  local.get 1
                                  call 45
                                  local.tee 10
                                  i32.eqz
                                  local.set 14
                                  local.get 5
                                  local.set 6
                                  local.get 1
                                  local.get 10
                                  local.get 7
                                  i32.sub
                                  local.get 14
                                  select
                                  local.set 5
                                  i32.const 0
                                  local.set 13
                                  i32.const 2014
                                  local.set 12
                                  local.get 1
                                  local.get 7
                                  i32.add
                                  local.get 10
                                  local.get 14
                                  select
                                  local.set 1
                                  br 9 (;@6;)
                                end
                                local.get 15
                                local.get 9
                                i64.load
                                i64.store32 offset=48
                                local.get 15
                                i32.const 0
                                i32.store offset=52
                                local.get 9
                                local.get 24
                                i32.store
                                i32.const -1
                                local.set 5
                                br 5 (;@9;)
                              end
                              local.get 1
                              if  ;; label = @14
                                local.get 1
                                local.set 5
                                br 5 (;@9;)
                              else
                                local.get 0
                                i32.const 32
                                local.get 16
                                i32.const 0
                                local.get 6
                                call 12
                                i32.const 0
                                local.set 1
                                br 7 (;@7;)
                              end
                              unreachable
                            end
                            local.get 0
                            local.get 9
                            f64.load
                            local.get 16
                            local.get 1
                            local.get 6
                            local.get 7
                            i32.const 3
                            call_indirect (type 1)
                            local.set 1
                            br 7 (;@5;)
                          end
                          local.get 10
                          local.set 7
                          local.get 1
                          local.set 5
                          i32.const 0
                          local.set 13
                          i32.const 2014
                          local.set 12
                          local.get 19
                          local.set 1
                          br 5 (;@6;)
                        end
                        local.get 9
                        i64.load
                        local.get 20
                        local.get 7
                        i32.const 32
                        i32.and
                        call 47
                        local.set 14
                        i32.const 0
                        i32.const 2
                        local.get 6
                        local.tee 5
                        i32.const 8
                        i32.and
                        i32.eqz
                        local.get 9
                        i64.load
                        i64.const 0
                        i64.eq
                        i32.or
                        local.tee 6
                        select
                        local.set 13
                        i32.const 2014
                        local.get 7
                        i32.const 4
                        i32.shr_u
                        i32.const 2014
                        i32.add
                        local.get 6
                        select
                        local.set 12
                        br 2 (;@8;)
                      end
                      local.get 25
                      local.get 20
                      call 16
                      local.set 14
                      local.get 6
                      local.set 5
                      br 1 (;@8;)
                    end
                    i32.const 0
                    local.set 1
                    local.get 9
                    i32.load
                    local.set 7
                    block  ;; label = @9
                      block  ;; label = @10
                        loop  ;; label = @11
                          local.get 7
                          i32.load
                          local.tee 10
                          if  ;; label = @12
                            local.get 21
                            local.get 10
                            call 30
                            local.tee 10
                            i32.const 0
                            i32.lt_s
                            local.tee 12
                            local.get 10
                            local.get 5
                            local.get 1
                            i32.sub
                            i32.gt_u
                            i32.or
                            br_if 2 (;@10;)
                            local.get 7
                            i32.const 4
                            i32.add
                            local.set 7
                            local.get 5
                            local.get 1
                            local.get 10
                            i32.add
                            local.tee 1
                            i32.gt_u
                            br_if 1 (;@11;)
                          end
                        end
                        br 1 (;@9;)
                      end
                      local.get 12
                      if  ;; label = @10
                        i32.const -1
                        local.set 8
                        br 6 (;@4;)
                      end
                    end
                    local.get 0
                    i32.const 32
                    local.get 16
                    local.get 1
                    local.get 6
                    call 12
                    local.get 1
                    if  ;; label = @9
                      i32.const 0
                      local.set 5
                      local.get 9
                      i32.load
                      local.set 7
                      loop  ;; label = @10
                        local.get 7
                        i32.load
                        local.tee 10
                        i32.eqz
                        br_if 3 (;@7;)
                        local.get 21
                        local.get 10
                        call 30
                        local.tee 10
                        local.get 5
                        i32.add
                        local.tee 5
                        local.get 1
                        i32.gt_s
                        br_if 3 (;@7;)
                        local.get 7
                        i32.const 4
                        i32.add
                        local.set 7
                        local.get 0
                        local.get 21
                        local.get 10
                        call 11
                        local.get 5
                        local.get 1
                        i32.lt_u
                        br_if 0 (;@10;)
                      end
                    else
                      i32.const 0
                      local.set 1
                    end
                    br 1 (;@7;)
                  end
                  local.get 14
                  local.get 20
                  local.get 9
                  i64.load
                  i64.const 0
                  i64.ne
                  local.tee 10
                  local.get 1
                  i32.const 0
                  i32.ne
                  i32.or
                  local.tee 17
                  select
                  local.set 7
                  local.get 5
                  i32.const -65537
                  i32.and
                  local.get 5
                  local.get 1
                  i32.const -1
                  i32.gt_s
                  select
                  local.set 6
                  local.get 1
                  local.get 19
                  local.get 14
                  i32.sub
                  local.get 10
                  i32.const 1
                  i32.xor
                  i32.add
                  local.tee 5
                  local.get 1
                  local.get 5
                  i32.gt_s
                  select
                  i32.const 0
                  local.get 17
                  select
                  local.set 5
                  local.get 19
                  local.set 1
                  br 1 (;@6;)
                end
                local.get 0
                i32.const 32
                local.get 16
                local.get 1
                local.get 6
                i32.const 8192
                i32.xor
                call 12
                local.get 16
                local.get 1
                local.get 16
                local.get 1
                i32.gt_s
                select
                local.set 1
                br 1 (;@5;)
              end
              local.get 0
              i32.const 32
              local.get 13
              local.get 1
              local.get 7
              i32.sub
              local.tee 10
              local.get 5
              local.get 5
              local.get 10
              i32.lt_s
              select
              local.tee 14
              i32.add
              local.tee 5
              local.get 16
              local.get 16
              local.get 5
              i32.lt_s
              select
              local.tee 1
              local.get 5
              local.get 6
              call 12
              local.get 0
              local.get 12
              local.get 13
              call 11
              local.get 0
              i32.const 48
              local.get 1
              local.get 5
              local.get 6
              i32.const 65536
              i32.xor
              call 12
              local.get 0
              i32.const 48
              local.get 14
              local.get 10
              i32.const 0
              call 12
              local.get 0
              local.get 7
              local.get 10
              call 11
              local.get 0
              i32.const 32
              local.get 1
              local.get 5
              local.get 6
              i32.const 8192
              i32.xor
              call 12
            end
            local.get 23
            local.set 6
            br 1 (;@3;)
          end
        end
        br 1 (;@1;)
      end
      local.get 0
      i32.eqz
      if  ;; label = @2
        local.get 6
        if (result i32)  ;; label = @3
          i32.const 1
          local.set 0
          loop  ;; label = @4
            local.get 0
            i32.const 2
            i32.shl
            local.get 4
            i32.add
            i32.load
            local.tee 1
            if  ;; label = @5
              local.get 0
              i32.const 3
              i32.shl
              local.get 3
              i32.add
              local.get 1
              local.get 2
              call 31
              local.get 0
              i32.const 1
              i32.add
              local.tee 0
              i32.const 10
              i32.lt_u
              br_if 1 (;@4;)
              i32.const 1
              local.set 8
              br 4 (;@1;)
            end
          end
          loop (result i32)  ;; label = @4
            local.get 0
            i32.const 2
            i32.shl
            local.get 4
            i32.add
            i32.load
            if  ;; label = @5
              i32.const -1
              local.set 8
              br 4 (;@1;)
            end
            local.get 0
            i32.const 1
            i32.add
            local.tee 0
            i32.const 10
            i32.lt_u
            br_if 0 (;@4;)
            i32.const 1
          end
        else
          i32.const 0
        end
        local.set 8
      end
    end
    local.get 15
    global.set 2
    local.get 8)
  (func (;24;) (type 0) (param i32 i32 i32) (result i32)
    (local i32)
    i32.const 12
    call 21
    local.tee 3
    local.get 0
    i32.store
    local.get 3
    local.get 1
    i32.store offset=4
    local.get 3
    local.get 2
    i32.store offset=8
    local.get 3)
  (func (;25;) (type 4) (param i32) (result i32)
    (local i32)
    local.get 0
    if  ;; label = @1
      block (result i32)  ;; label = @2
        local.get 0
        i32.load offset=76
        i32.const -1
        i32.le_s
        if  ;; label = @3
          local.get 0
          call 22
          br 1 (;@2;)
        end
        local.get 0
        call 22
      end
      local.set 0
    else
      i32.const 1652
      i32.load
      if (result i32)  ;; label = @2
        i32.const 1652
        i32.load
        call 25
      else
        i32.const 0
      end
      local.set 0
      i32.const 3188
      call 10
      i32.const 3196
      i32.load
      local.tee 1
      if  ;; label = @2
        loop  ;; label = @3
          local.get 1
          i32.load offset=76
          i32.const -1
          i32.gt_s
          if (result i32)  ;; label = @4
            i32.const 1
          else
            i32.const 0
          end
          drop
          local.get 1
          i32.load offset=20
          local.get 1
          i32.load offset=28
          i32.gt_u
          if  ;; label = @4
            local.get 1
            call 22
            local.get 0
            i32.or
            local.set 0
          end
          local.get 1
          i32.load offset=56
          local.tee 1
          br_if 0 (;@3;)
        end
      end
      i32.const 3188
      call 8
    end
    local.get 0)
  (func (;26;) (type 10)
    i32.const 1648
    i32.load
    call 42)
  (func (;27;) (type 5) (param i32)
    (local i32 i32 i32 i32)
    global.get 2
    local.set 1
    global.get 2
    i32.const 16
    i32.add
    global.set 2
    local.get 1
    local.tee 2
    i32.const 10
    i32.store8
    block  ;; label = @1
      block  ;; label = @2
        local.get 0
        i32.load offset=16
        local.tee 3
        br_if 0 (;@2;)
        local.get 0
        call 29
        i32.eqz
        if  ;; label = @3
          local.get 0
          i32.load offset=16
          local.set 3
          br 1 (;@2;)
        end
        br 1 (;@1;)
      end
      local.get 0
      i32.load offset=20
      local.tee 4
      local.get 3
      i32.lt_u
      if  ;; label = @2
        local.get 0
        i32.load8_s offset=75
        i32.const 10
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
      local.get 2
      i32.const 1
      local.get 0
      i32.load offset=36
      i32.const 1
      i32.and
      i32.const 4
      i32.add
      call_indirect (type 0)
      i32.const 1
      i32.eq
      if (result i32)  ;; label = @2
        local.get 2
        i32.load8_u
      else
        i32.const -1
      end
      drop
    end
    local.get 1
    global.set 2)
  (func (;28;) (type 16) (param f64 i32) (result f64)
    (local i32 i64 i64)
    block  ;; label = @1
      block  ;; label = @2
        local.get 0
        i64.reinterpret_f64
        local.tee 3
        i64.const 52
        i64.shr_u
        local.tee 4
        i32.wrap_i64
        i32.const 2047
        i32.and
        local.tee 2
        if  ;; label = @3
          local.get 2
          i32.const 2047
          i32.eq
          if  ;; label = @4
            br 3 (;@1;)
          else
            br 2 (;@2;)
          end
          unreachable
        end
        local.get 1
        local.get 0
        f64.const 0x0p+0 (;=0;)
        f64.ne
        if (result i32)  ;; label = @3
          local.get 0
          f64.const 0x1p+64 (;=1.84467e+19;)
          f64.mul
          local.get 1
          call 28
          local.set 0
          local.get 1
          i32.load
          i32.const -64
          i32.add
        else
          i32.const 0
        end
        i32.store
        br 1 (;@1;)
      end
      local.get 1
      local.get 4
      i32.wrap_i64
      i32.const 2047
      i32.and
      i32.const -1022
      i32.add
      i32.store
      local.get 3
      i64.const -9218868437227405313
      i64.and
      i64.const 4602678819172646912
      i64.or
      f64.reinterpret_i64
      local.set 0
    end
    local.get 0)
  (func (;29;) (type 4) (param i32) (result i32)
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
  (func (;30;) (type 8) (param i32 i32) (result i32)
    local.get 0
    if (result i32)  ;; label = @1
      local.get 0
      local.get 1
      call 44
    else
      i32.const 0
    end)
  (func (;31;) (type 11) (param i32 i32 i32)
    (local i32 i64 f64)
    local.get 1
    i32.const 20
    i32.le_u
    if  ;; label = @1
      block  ;; label = @2
        block  ;; label = @3
          block  ;; label = @4
            block  ;; label = @5
              block  ;; label = @6
                block  ;; label = @7
                  block  ;; label = @8
                    block  ;; label = @9
                      block  ;; label = @10
                        block  ;; label = @11
                          block  ;; label = @12
                            local.get 1
                            i32.const 9
                            i32.sub
                            br_table 0 (;@12;) 1 (;@11;) 2 (;@10;) 3 (;@9;) 4 (;@8;) 5 (;@7;) 6 (;@6;) 7 (;@5;) 8 (;@4;) 9 (;@3;) 10 (;@2;)
                          end
                          local.get 2
                          i32.load
                          i32.const 3
                          i32.add
                          i32.const -4
                          i32.and
                          local.tee 1
                          i32.load
                          local.set 3
                          local.get 2
                          local.get 1
                          i32.const 4
                          i32.add
                          i32.store
                          local.get 0
                          local.get 3
                          i32.store
                          br 9 (;@2;)
                        end
                        local.get 2
                        i32.load
                        i32.const 3
                        i32.add
                        i32.const -4
                        i32.and
                        local.tee 1
                        i32.load
                        local.set 3
                        local.get 2
                        local.get 1
                        i32.const 4
                        i32.add
                        i32.store
                        local.get 0
                        local.get 3
                        i64.extend_i32_s
                        i64.store
                        br 8 (;@2;)
                      end
                      local.get 2
                      i32.load
                      i32.const 3
                      i32.add
                      i32.const -4
                      i32.and
                      local.tee 1
                      i32.load
                      local.set 3
                      local.get 2
                      local.get 1
                      i32.const 4
                      i32.add
                      i32.store
                      local.get 0
                      local.get 3
                      i64.extend_i32_u
                      i64.store
                      br 7 (;@2;)
                    end
                    local.get 2
                    i32.load
                    i32.const 7
                    i32.add
                    i32.const -8
                    i32.and
                    local.tee 1
                    i64.load
                    local.set 4
                    local.get 2
                    local.get 1
                    i32.const 8
                    i32.add
                    i32.store
                    local.get 0
                    local.get 4
                    i64.store
                    br 6 (;@2;)
                  end
                  local.get 2
                  i32.load
                  i32.const 3
                  i32.add
                  i32.const -4
                  i32.and
                  local.tee 1
                  i32.load
                  local.set 3
                  local.get 2
                  local.get 1
                  i32.const 4
                  i32.add
                  i32.store
                  local.get 0
                  local.get 3
                  i32.const 65535
                  i32.and
                  i32.const 16
                  i32.shl
                  i32.const 16
                  i32.shr_s
                  i64.extend_i32_s
                  i64.store
                  br 5 (;@2;)
                end
                local.get 2
                i32.load
                i32.const 3
                i32.add
                i32.const -4
                i32.and
                local.tee 1
                i32.load
                local.set 3
                local.get 2
                local.get 1
                i32.const 4
                i32.add
                i32.store
                local.get 0
                local.get 3
                i32.const 65535
                i32.and
                i64.extend_i32_u
                i64.store
                br 4 (;@2;)
              end
              local.get 2
              i32.load
              i32.const 3
              i32.add
              i32.const -4
              i32.and
              local.tee 1
              i32.load
              local.set 3
              local.get 2
              local.get 1
              i32.const 4
              i32.add
              i32.store
              local.get 0
              local.get 3
              i32.const 255
              i32.and
              i32.const 24
              i32.shl
              i32.const 24
              i32.shr_s
              i64.extend_i32_s
              i64.store
              br 3 (;@2;)
            end
            local.get 2
            i32.load
            i32.const 3
            i32.add
            i32.const -4
            i32.and
            local.tee 1
            i32.load
            local.set 3
            local.get 2
            local.get 1
            i32.const 4
            i32.add
            i32.store
            local.get 0
            local.get 3
            i32.const 255
            i32.and
            i64.extend_i32_u
            i64.store
            br 2 (;@2;)
          end
          local.get 2
          i32.load
          i32.const 7
          i32.add
          i32.const -8
          i32.and
          local.tee 1
          f64.load
          local.set 5
          local.get 2
          local.get 1
          i32.const 8
          i32.add
          i32.store
          local.get 0
          local.get 5
          f64.store
          br 1 (;@2;)
        end
        local.get 0
        local.get 2
        i32.const 9
        call_indirect (type 2)
      end
    end)
  (func (;32;) (type 4) (param i32) (result i32)
    (local i32 i32)
    local.get 0
    i32.load
    i32.load8_s
    call 18
    if  ;; label = @1
      loop  ;; label = @2
        local.get 0
        i32.load
        local.tee 2
        i32.load8_s
        local.get 1
        i32.const 10
        i32.mul
        i32.const -48
        i32.add
        i32.add
        local.set 1
        local.get 0
        local.get 2
        i32.const 1
        i32.add
        i32.store
        local.get 2
        i32.load8_s offset=1
        call 18
        br_if 0 (;@2;)
      end
    end
    local.get 1)
  (func (;33;) (type 4) (param i32) (result i32)
    i32.const 0)
  (func (;34;) (type 10)
    (local i32 i32 i32 i32)
    i32.const 0
    i32.const 15
    call 15
    call 17
    i32.const 0
    i32.const 14
    call 15
    drop
    i32.const 4
    local.set 0
    loop  ;; label = @1
      i32.const 18
      local.get 0
      i32.sub
      f64.convert_i32_u
      call 3
      i32.trunc_f64_s
      local.tee 2
      i32.const 1
      i32.ge_s
      if  ;; label = @2
        i32.const 1
        local.set 1
        loop  ;; label = @3
          local.get 1
          local.get 0
          call 15
          call 17
          i32.const 0
          local.get 1
          i32.sub
          local.get 0
          call 15
          call 17
          local.get 1
          i32.const 1
          i32.add
          local.set 3
          local.get 1
          local.get 2
          i32.ne
          if  ;; label = @4
            local.get 3
            local.set 1
            br 1 (;@3;)
          end
        end
      end
      i32.const 14
      local.get 0
      i32.const 2
      i32.add
      local.tee 0
      i32.ge_u
      br_if 0 (;@1;)
    end)
  (func (;35;) (type 7) (result i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32)

    ;; Initialization code -- handwritten
    global.get 1
    i32.const 5247808
    i32.store
    ;; Initialization code -- handwritten

    global.get 2
    local.set 1
    global.get 2
    i32.const 144
    i32.add
    global.set 2
    local.get 1
    i32.const 128
    i32.add
    local.set 8
    local.get 1
    i32.const 120
    i32.add
    local.set 10
    local.get 1
    i32.const 112
    i32.add
    local.set 12
    local.get 1
    i32.const 104
    i32.add
    local.set 3
    local.get 1
    i32.const 96
    i32.add
    local.set 11
    local.get 1
    i32.const 88
    i32.add
    local.set 13
    local.get 1
    i32.const 80
    i32.add
    local.set 9
    local.get 1
    i32.const -64
    i32.sub
    local.set 2
    i32.const 8
    call 21
    local.set 5
    i32.const 8
    call 21
    local.set 6
    local.get 1
    local.set 4
    i32.const 1648
    i32.load
    local.set 14
    loop  ;; label = @1
      local.get 5
      i32.const 0
      call 1
      drop
      i32.const 0
      local.set 7
      loop  ;; label = @2
        call 34
        local.get 7
        i32.const 1
        i32.add
        local.tee 7
        i32.const 10
        i32.ne
        br_if 0 (;@2;)
      end
      local.get 6
      i32.const 0
      call 1
      drop
      local.get 0
      i32.const 2
      i32.shl
      local.get 4
      i32.add
      local.get 6
      i32.load offset=4
      local.get 5
      i32.load offset=4
      i32.sub
      local.get 6
      i32.load
      local.get 5
      i32.load
      i32.sub
      i32.const 1000000
      i32.mul
      i32.add
      local.tee 7
      i32.store
      local.get 2
      local.get 0
      i32.store
      local.get 2
      i32.const 2002
      i32.store offset=4
      local.get 2
      local.get 7
      i32.store offset=8
      i32.const 1900
      local.get 2
      call 13
      local.get 14
      call 25
      drop
      local.get 0
      i32.const 1
      i32.add
      local.tee 0
      i32.const 15
      i32.ne
      br_if 0 (;@1;)
    end
    local.get 9
    i32.const 10
    i32.store
    local.get 9
    i32.const 2002
    i32.store offset=4
    i32.const 1926
    local.get 9
    call 13
    i32.const 0
    local.set 0
    loop  ;; label = @1
      local.get 0
      if  ;; label = @2
        i32.const 1958
        local.get 13
        call 13
      end
      local.get 11
      local.get 0
      i32.const 2
      i32.shl
      local.get 4
      i32.add
      i32.load
      i32.store
      i32.const 1961
      local.get 11
      call 13
      local.get 0
      i32.const 1
      i32.add
      local.tee 0
      i32.const 10
      i32.ne
      br_if 0 (;@1;)
    end
    call 26
    local.get 3
    i32.const 5
    i32.store
    local.get 3
    i32.const 2002
    i32.store offset=4
    i32.const 1965
    local.get 3
    call 13
    i32.const 10
    local.set 0
    i32.const 0
    local.set 2
    loop  ;; label = @1
      local.get 0
      i32.const 2
      i32.shl
      local.get 4
      i32.add
      i32.load
      local.tee 3
      local.get 2
      i32.add
      local.set 2
      local.get 0
      i32.const 10
      i32.gt_u
      if  ;; label = @2
        i32.const 1958
        local.get 12
        call 13
      end
      local.get 10
      local.get 3
      i32.store
      i32.const 1961
      local.get 10
      call 13
      local.get 0
      i32.const 1
      i32.add
      local.tee 0
      i32.const 15
      i32.ne
      br_if 0 (;@1;)
    end
    call 26
    local.get 2
    i32.const 5
    i32.div_s
    local.set 4
    local.get 8
    i32.const 2002
    i32.store
    local.get 8
    local.get 4
    i32.store offset=4
    i32.const 1989
    local.get 8
    call 13
    local.get 5
    call 20
    local.get 6
    call 20
    local.get 1
    global.set 2
    i32.const 0)
  (func (;36;) (type 2) (param i32 i32)
    i32.const 4
    call 0)
  (func (;37;) (type 3) (param i32 i64 i32) (result i64)
    i32.const 3
    call 0
    i64.const 0)
  (func (;38;) (type 0) (param i32 i32 i32) (result i32)
    i32.const 2
    call 0
    i32.const 0)
  (func (;39;) (type 1) (param i32 f64 i32 i32 i32 i32) (result i32)
    i32.const 1
    call 0
    i32.const 0)
  (func (;40;) (type 4) (param i32) (result i32)
    i32.const 0
    call 0
    i32.const 0)
  (func (;41;) (type 0) (param i32 i32 i32) (result i32)
    (local i32 i32 i32)
    local.get 2
    i32.const 8192
    i32.ge_s
    if  ;; label = @1
      local.get 0
      local.get 1
      local.get 2
      call 5
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
  (func (;42;) (type 5) (param i32)
    (local i32)
    block  ;; label = @1
      local.get 0
      i32.load offset=76
      i32.const 0
      i32.ge_s
      if  ;; label = @2
        block  ;; label = @3
          local.get 0
          i32.load8_s offset=75
          i32.const 10
          i32.eq
          br_if 0 (;@3;)
          local.get 0
          i32.load offset=20
          local.tee 1
          local.get 0
          i32.load offset=16
          i32.ge_u
          br_if 0 (;@3;)
          local.get 0
          local.get 1
          i32.const 1
          i32.add
          i32.store offset=20
          local.get 1
          i32.const 10
          i32.store8
          br 2 (;@1;)
        end
        local.get 0
        call 27
        br 1 (;@1;)
      end
      local.get 0
      i32.load8_s offset=75
      i32.const 10
      i32.ne
      if  ;; label = @2
        local.get 0
        i32.load offset=20
        local.tee 1
        local.get 0
        i32.load offset=16
        i32.lt_u
        if  ;; label = @3
          local.get 0
          local.get 1
          i32.const 1
          i32.add
          i32.store offset=20
          local.get 1
          i32.const 10
          i32.store8
          br 2 (;@1;)
        end
      end
      local.get 0
      call 27
    end)
  (func (;43;) (type 11) (param i32 i32 i32)
    (local i32 i32 i32)
    block  ;; label = @1
      block  ;; label = @2
        local.get 2
        i32.load offset=16
        local.tee 3
        br_if 0 (;@2;)
        local.get 2
        call 29
        i32.eqz
        if  ;; label = @3
          local.get 2
          i32.load offset=16
          local.set 3
          br 1 (;@2;)
        end
        br 1 (;@1;)
      end
      local.get 3
      local.get 2
      i32.load offset=20
      local.tee 4
      i32.sub
      local.get 1
      i32.lt_u
      if  ;; label = @2
        local.get 2
        local.get 0
        local.get 1
        local.get 2
        i32.load offset=36
        i32.const 1
        i32.and
        i32.const 4
        i32.add
        call_indirect (type 0)
        drop
        br 1 (;@1;)
      end
      local.get 1
      i32.eqz
      local.get 2
      i32.load8_s offset=75
      i32.const 0
      i32.lt_s
      i32.or
      if (result i32)  ;; label = @2
        i32.const 0
      else
        block (result i32)  ;; label = @3
          local.get 1
          local.set 3
          loop  ;; label = @4
            local.get 0
            local.get 3
            i32.const -1
            i32.add
            local.tee 5
            i32.add
            i32.load8_s
            i32.const 10
            i32.ne
            if  ;; label = @5
              local.get 5
              if  ;; label = @6
                local.get 5
                local.set 3
                br 2 (;@4;)
              else
                i32.const 0
                br 3 (;@3;)
              end
              unreachable
            end
          end
          local.get 2
          local.get 0
          local.get 3
          local.get 2
          i32.load offset=36
          i32.const 1
          i32.and
          i32.const 4
          i32.add
          call_indirect (type 0)
          local.get 3
          i32.lt_u
          br_if 2 (;@1;)
          local.get 2
          i32.load offset=20
          local.set 4
          local.get 1
          local.get 3
          i32.sub
          local.set 1
          local.get 0
          local.get 3
          i32.add
          local.set 0
          i32.const 0
        end
      end
      drop
      local.get 4
      local.get 0
      local.get 1
      call 41
      drop
      local.get 2
      local.get 2
      i32.load offset=20
      local.get 1
      i32.add
      i32.store offset=20
    end)
  (func (;44;) (type 8) (param i32 i32) (result i32)
    local.get 0
    if (result i32)  ;; label = @1
      block (result i32)  ;; label = @2
        local.get 1
        i32.const 128
        i32.lt_u
        if  ;; label = @3
          local.get 0
          local.get 1
          i32.store8
          i32.const 1
          br 1 (;@2;)
        end
        i32.const 1844
        i32.load
        i32.load
        i32.eqz
        if  ;; label = @3
          local.get 1
          i32.const -128
          i32.and
          i32.const 57216
          i32.eq
          if  ;; label = @4
            local.get 0
            local.get 1
            i32.store8
            i32.const 1
            br 2 (;@2;)
          else
            i32.const 3120
            i32.const 84
            i32.store
            i32.const -1
            br 2 (;@2;)
          end
          unreachable
        end
        local.get 1
        i32.const 2048
        i32.lt_u
        if  ;; label = @3
          local.get 0
          local.get 1
          i32.const 6
          i32.shr_u
          i32.const 192
          i32.or
          i32.store8
          local.get 0
          local.get 1
          i32.const 63
          i32.and
          i32.const 128
          i32.or
          i32.store8 offset=1
          i32.const 2
          br 1 (;@2;)
        end
        local.get 1
        i32.const -8192
        i32.and
        i32.const 57344
        i32.eq
        local.get 1
        i32.const 55296
        i32.lt_u
        i32.or
        if  ;; label = @3
          local.get 0
          local.get 1
          i32.const 12
          i32.shr_u
          i32.const 224
          i32.or
          i32.store8
          local.get 0
          local.get 1
          i32.const 6
          i32.shr_u
          i32.const 63
          i32.and
          i32.const 128
          i32.or
          i32.store8 offset=1
          local.get 0
          local.get 1
          i32.const 63
          i32.and
          i32.const 128
          i32.or
          i32.store8 offset=2
          i32.const 3
          br 1 (;@2;)
        end
        local.get 1
        i32.const -65536
        i32.add
        i32.const 1048576
        i32.lt_u
        if (result i32)  ;; label = @3
          local.get 0
          local.get 1
          i32.const 18
          i32.shr_u
          i32.const 240
          i32.or
          i32.store8
          local.get 0
          local.get 1
          i32.const 12
          i32.shr_u
          i32.const 63
          i32.and
          i32.const 128
          i32.or
          i32.store8 offset=1
          local.get 0
          local.get 1
          i32.const 6
          i32.shr_u
          i32.const 63
          i32.and
          i32.const 128
          i32.or
          i32.store8 offset=2
          local.get 0
          local.get 1
          i32.const 63
          i32.and
          i32.const 128
          i32.or
          i32.store8 offset=3
          i32.const 4
        else
          i32.const 3120
          i32.const 84
          i32.store
          i32.const -1
        end
      end
    else
      i32.const 1
    end)
  (func (;45;) (type 8) (param i32 i32) (result i32)
    (local i32)
    block  ;; label = @1
      block  ;; label = @2
        block  ;; label = @3
          local.get 1
          i32.const 0
          i32.ne
          local.tee 2
          local.get 0
          i32.const 3
          i32.and
          i32.const 0
          i32.ne
          i32.and
          if  ;; label = @4
            loop  ;; label = @5
              local.get 0
              i32.load8_u
              i32.eqz
              br_if 2 (;@3;)
              local.get 1
              i32.const -1
              i32.add
              local.tee 1
              i32.const 0
              i32.ne
              local.tee 2
              local.get 0
              i32.const 1
              i32.add
              local.tee 0
              i32.const 3
              i32.and
              i32.const 0
              i32.ne
              i32.and
              br_if 0 (;@5;)
            end
          end
          local.get 2
          i32.eqz
          br_if 1 (;@2;)
        end
        local.get 0
        i32.load8_u
        i32.eqz
        if  ;; label = @3
          local.get 1
          i32.eqz
          br_if 1 (;@2;)
          br 2 (;@1;)
        end
        block  ;; label = @3
          block  ;; label = @4
            local.get 1
            i32.const 3
            i32.le_u
            br_if 0 (;@4;)
            loop  ;; label = @5
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
              if  ;; label = @6
                local.get 0
                i32.const 4
                i32.add
                local.set 0
                local.get 1
                i32.const -4
                i32.add
                local.tee 1
                i32.const 3
                i32.gt_u
                br_if 1 (;@5;)
                br 2 (;@4;)
              end
            end
            br 1 (;@3;)
          end
          local.get 1
          i32.eqz
          br_if 1 (;@2;)
        end
        loop  ;; label = @3
          local.get 0
          i32.load8_u
          i32.eqz
          br_if 2 (;@1;)
          local.get 1
          i32.const -1
          i32.add
          local.tee 1
          i32.eqz
          br_if 1 (;@2;)
          local.get 0
          i32.const 1
          i32.add
          local.set 0
          br 0 (;@3;)
          unreachable
        end
        unreachable
      end
      i32.const 0
      local.set 0
    end
    local.get 0)
  (func (;46;) (type 14) (param i64 i32) (result i32)
    local.get 0
    i64.const 0
    i64.ne
    if  ;; label = @1
      loop  ;; label = @2
        local.get 1
        i32.const -1
        i32.add
        local.tee 1
        local.get 0
        i32.wrap_i64
        i32.const 7
        i32.and
        i32.const 48
        i32.or
        i32.store8
        local.get 0
        i64.const 3
        i64.shr_u
        local.tee 0
        i64.const 0
        i64.ne
        br_if 0 (;@2;)
      end
    end
    local.get 1)
  (func (;47;) (type 13) (param i64 i32 i32) (result i32)
    local.get 0
    i64.const 0
    i64.ne
    if  ;; label = @1
      loop  ;; label = @2
        local.get 1
        i32.const -1
        i32.add
        local.tee 1
        local.get 2
        local.get 0
        i32.wrap_i64
        i32.const 15
        i32.and
        i32.const 1488
        i32.add
        i32.load8_u
        i32.or
        i32.store8
        local.get 0
        i64.const 4
        i64.shr_u
        local.tee 0
        i64.const 0
        i64.ne
        br_if 0 (;@2;)
      end
    end
    local.get 1)
  (func (;48;) (type 11) (param i32 i32 i32)
    (local i32 i32 i32 i32 i32 i32)
    global.get 2
    local.set 4
    global.get 2
    i32.const 224
    i32.add
    global.set 2
    local.get 4
    local.set 5
    local.get 4
    i32.const 160
    i32.add
    local.tee 3
    i64.const 0
    i64.store
    local.get 3
    i64.const 0
    i64.store offset=8
    local.get 3
    i64.const 0
    i64.store offset=16
    local.get 3
    i64.const 0
    i64.store offset=24
    local.get 3
    i64.const 0
    i64.store offset=32
    local.get 4
    i32.const 208
    i32.add
    local.tee 6
    local.get 2
    i32.load
    i32.store
    i32.const 0
    local.get 1
    local.get 6
    local.get 4
    i32.const 80
    i32.add
    local.tee 2
    local.get 3
    call 23
    i32.const 0
    i32.lt_s
    if (result i32)  ;; label = @1
      i32.const -1
    else
      local.get 0
      i32.load offset=76
      i32.const -1
      i32.gt_s
      if (result i32)  ;; label = @2
        i32.const 1
      else
        i32.const 0
      end
      drop
      local.get 0
      i32.load
      local.set 7
      local.get 0
      i32.load8_s offset=74
      i32.const 1
      i32.lt_s
      if  ;; label = @2
        local.get 0
        local.get 7
        i32.const -33
        i32.and
        i32.store
      end
      local.get 0
      i32.load offset=48
      if  ;; label = @2
        local.get 0
        local.get 1
        local.get 6
        local.get 2
        local.get 3
        call 23
        drop
      else
        local.get 0
        i32.load offset=44
        local.set 8
        local.get 0
        local.get 5
        i32.store offset=44
        local.get 0
        local.get 5
        i32.store offset=28
        local.get 0
        local.get 5
        i32.store offset=20
        local.get 0
        i32.const 80
        i32.store offset=48
        local.get 0
        local.get 5
        i32.const 80
        i32.add
        i32.store offset=16
        local.get 0
        local.get 1
        local.get 6
        local.get 2
        local.get 3
        call 23
        drop
        local.get 8
        if  ;; label = @3
          local.get 0
          i32.const 0
          i32.const 0
          local.get 0
          i32.load offset=36
          i32.const 1
          i32.and
          i32.const 4
          i32.add
          call_indirect (type 0)
          drop
          local.get 0
          i32.load offset=20
          drop
          local.get 0
          local.get 8
          i32.store offset=44
          local.get 0
          i32.const 0
          i32.store offset=48
          local.get 0
          i32.const 0
          i32.store offset=16
          local.get 0
          i32.const 0
          i32.store offset=28
          local.get 0
          i32.const 0
          i32.store offset=20
        end
      end
      local.get 0
      local.get 0
      i32.load
      local.get 7
      i32.const 32
      i32.and
      i32.or
      i32.store
      i32.const 0
    end
    drop
    local.get 4
    global.set 2)
  (func (;49;) (type 2) (param i32 i32)
    (local i32 f64)
    local.get 1
    i32.load
    i32.const 7
    i32.add
    i32.const -8
    i32.and
    local.tee 2
    f64.load
    local.set 3
    local.get 1
    local.get 2
    i32.const 8
    i32.add
    i32.store
    local.get 0
    local.get 3
    f64.store)
  (func (;50;) (type 1) (param i32 f64 i32 i32 i32 i32) (result i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i64 i64 i64 f64)
    global.get 2
    local.set 25
    global.get 2
    i32.const 560
    i32.add
    global.set 2
    local.get 25
    i32.const 536
    i32.add
    local.tee 15
    i32.const 0
    i32.store
    local.get 1
    i64.reinterpret_f64
    local.tee 26
    i64.const 0
    i64.lt_s
    if (result i32)  ;; label = @1
      local.get 1
      f64.neg
      local.tee 1
      i64.reinterpret_f64
      local.set 26
      i32.const 2031
      local.set 21
      i32.const 1
    else
      i32.const 2034
      i32.const 2037
      i32.const 2032
      local.get 4
      i32.const 1
      i32.and
      select
      local.get 4
      i32.const 2048
      i32.and
      select
      local.set 21
      local.get 4
      i32.const 2049
      i32.and
      i32.const 0
      i32.ne
    end
    local.set 22
    local.get 25
    i32.const 32
    i32.add
    local.set 8
    local.get 25
    local.tee 12
    local.set 19
    local.get 12
    i32.const 540
    i32.add
    local.tee 7
    i32.const 12
    i32.add
    local.set 20
    local.get 26
    i64.const 9218868437227405312
    i64.and
    i64.const 9218868437227405312
    i64.eq
    if (result i32)  ;; label = @1
      local.get 0
      i32.const 32
      local.get 2
      local.get 22
      i32.const 3
      i32.add
      local.tee 6
      local.get 4
      i32.const -65537
      i32.and
      call 12
      local.get 0
      local.get 21
      local.get 22
      call 11
      local.get 0
      i32.const 2058
      i32.const 2062
      local.get 5
      i32.const 32
      i32.and
      i32.const 0
      i32.ne
      local.tee 3
      select
      i32.const 2050
      i32.const 2054
      local.get 3
      select
      local.get 1
      local.get 1
      f64.ne
      select
      i32.const 3
      call 11
      local.get 0
      i32.const 32
      local.get 2
      local.get 6
      local.get 4
      i32.const 8192
      i32.xor
      call 12
      local.get 6
    else
      block (result i32)  ;; label = @2
        local.get 1
        local.get 15
        call 28
        f64.const 0x1p+1 (;=2;)
        f64.mul
        local.tee 1
        f64.const 0x0p+0 (;=0;)
        f64.ne
        local.tee 6
        if  ;; label = @3
          local.get 15
          local.get 15
          i32.load
          i32.const -1
          i32.add
          i32.store
        end
        local.get 5
        i32.const 32
        i32.or
        local.tee 23
        i32.const 97
        i32.eq
        if  ;; label = @3
          local.get 21
          i32.const 9
          i32.add
          local.get 21
          local.get 5
          i32.const 32
          i32.and
          local.tee 10
          select
          local.set 9
          i32.const 12
          local.get 3
          i32.sub
          local.tee 6
          i32.eqz
          local.get 3
          i32.const 11
          i32.gt_u
          i32.or
          i32.eqz
          if  ;; label = @4
            f64.const 0x1p+3 (;=8;)
            local.set 29
            loop  ;; label = @5
              local.get 29
              f64.const 0x1p+4 (;=16;)
              f64.mul
              local.set 29
              local.get 6
              i32.const -1
              i32.add
              local.tee 6
              br_if 0 (;@5;)
            end
            local.get 9
            i32.load8_s
            i32.const 45
            i32.eq
            if (result f64)  ;; label = @5
              local.get 29
              local.get 1
              f64.neg
              local.get 29
              f64.sub
              f64.add
              f64.neg
            else
              local.get 1
              local.get 29
              f64.add
              local.get 29
              f64.sub
            end
            local.set 1
          end
          local.get 20
          i32.const 0
          local.get 15
          i32.load
          local.tee 8
          i32.sub
          local.get 8
          local.get 8
          i32.const 0
          i32.lt_s
          select
          i64.extend_i32_s
          local.get 20
          call 16
          local.tee 6
          i32.eq
          if  ;; label = @4
            local.get 7
            i32.const 11
            i32.add
            local.tee 6
            i32.const 48
            i32.store8
          end
          local.get 22
          i32.const 2
          i32.or
          local.set 14
          local.get 6
          i32.const -1
          i32.add
          local.get 8
          i32.const 31
          i32.shr_s
          i32.const 2
          i32.and
          i32.const 43
          i32.add
          i32.store8
          local.get 6
          i32.const -2
          i32.add
          local.tee 11
          local.get 5
          i32.const 15
          i32.add
          i32.store8
          local.get 3
          i32.const 1
          i32.lt_s
          local.set 8
          local.get 4
          i32.const 8
          i32.and
          i32.eqz
          local.set 7
          local.get 12
          local.set 5
          loop  ;; label = @4
            local.get 5
            local.get 10
            local.get 1
            i32.trunc_f64_s
            local.tee 6
            i32.const 1488
            i32.add
            i32.load8_u
            i32.or
            i32.store8
            local.get 1
            local.get 6
            f64.convert_i32_s
            f64.sub
            f64.const 0x1p+4 (;=16;)
            f64.mul
            local.set 1
            local.get 5
            i32.const 1
            i32.add
            local.tee 6
            local.get 19
            i32.sub
            i32.const 1
            i32.eq
            if (result i32)  ;; label = @5
              local.get 8
              local.get 1
              f64.const 0x0p+0 (;=0;)
              f64.eq
              i32.and
              local.get 7
              i32.and
              if (result i32)  ;; label = @6
                local.get 6
              else
                local.get 6
                i32.const 46
                i32.store8
                local.get 5
                i32.const 2
                i32.add
              end
            else
              local.get 6
            end
            local.set 5
            local.get 1
            f64.const 0x0p+0 (;=0;)
            f64.ne
            br_if 0 (;@4;)
          end
          block (result i32)  ;; label = @4
            local.get 3
            i32.eqz
            local.get 5
            i32.const -2
            local.get 19
            i32.sub
            i32.add
            local.get 3
            i32.ge_s
            i32.or
            i32.eqz
            if  ;; label = @5
              local.get 20
              local.get 3
              i32.const 2
              i32.add
              i32.add
              local.get 11
              i32.sub
              local.set 8
              local.get 11
              br 1 (;@4;)
            end
            local.get 5
            local.get 20
            local.get 19
            i32.sub
            local.get 11
            i32.sub
            i32.add
            local.set 8
            local.get 11
          end
          local.set 3
          local.get 0
          i32.const 32
          local.get 2
          local.get 8
          local.get 14
          i32.add
          local.tee 6
          local.get 4
          call 12
          local.get 0
          local.get 9
          local.get 14
          call 11
          local.get 0
          i32.const 48
          local.get 2
          local.get 6
          local.get 4
          i32.const 65536
          i32.xor
          call 12
          local.get 0
          local.get 12
          local.get 5
          local.get 19
          i32.sub
          local.tee 5
          call 11
          local.get 0
          i32.const 48
          local.get 8
          local.get 5
          local.get 20
          local.get 3
          i32.sub
          local.tee 3
          i32.add
          i32.sub
          i32.const 0
          i32.const 0
          call 12
          local.get 0
          local.get 11
          local.get 3
          call 11
          local.get 0
          i32.const 32
          local.get 2
          local.get 6
          local.get 4
          i32.const 8192
          i32.xor
          call 12
          local.get 6
          br 1 (;@2;)
        end
        local.get 6
        if  ;; label = @3
          local.get 15
          local.get 15
          i32.load
          i32.const -28
          i32.add
          local.tee 6
          i32.store
          local.get 1
          f64.const 0x1p+28 (;=2.68435e+08;)
          f64.mul
          local.set 1
        else
          local.get 15
          i32.load
          local.set 6
        end
        local.get 8
        local.get 8
        i32.const 288
        i32.add
        local.get 6
        i32.const 0
        i32.lt_s
        select
        local.tee 14
        local.set 7
        loop  ;; label = @3
          local.get 7
          local.get 1
          i32.trunc_f64_u
          local.tee 8
          i32.store
          local.get 7
          i32.const 4
          i32.add
          local.set 7
          local.get 1
          local.get 8
          f64.convert_i32_u
          f64.sub
          f64.const 0x1.dcd65p+29 (;=1e+09;)
          f64.mul
          local.tee 1
          f64.const 0x0p+0 (;=0;)
          f64.ne
          br_if 0 (;@3;)
        end
        local.get 6
        i32.const 0
        i32.gt_s
        if  ;; label = @3
          local.get 6
          local.set 8
          local.get 14
          local.set 6
          loop  ;; label = @4
            local.get 8
            i32.const 29
            local.get 8
            i32.const 29
            i32.lt_s
            select
            local.set 9
            local.get 7
            i32.const -4
            i32.add
            local.tee 8
            local.get 6
            i32.ge_u
            if  ;; label = @5
              local.get 9
              i64.extend_i32_u
              local.set 28
              i32.const 0
              local.set 10
              loop  ;; label = @6
                local.get 10
                i64.extend_i32_u
                local.get 8
                i32.load
                i64.extend_i32_u
                local.get 28
                i64.shl
                i64.add
                local.tee 26
                i64.const 1000000000
                i64.div_u
                local.set 27
                local.get 8
                local.get 26
                local.get 27
                i64.const 1000000000
                i64.mul
                i64.sub
                i64.store32
                local.get 27
                i32.wrap_i64
                local.set 10
                local.get 8
                i32.const -4
                i32.add
                local.tee 8
                local.get 6
                i32.ge_u
                br_if 0 (;@6;)
              end
              local.get 10
              if  ;; label = @6
                local.get 6
                i32.const -4
                i32.add
                local.tee 6
                local.get 10
                i32.store
              end
            end
            local.get 7
            local.get 6
            i32.gt_u
            if  ;; label = @5
              block  ;; label = @6
                loop (result i32)  ;; label = @7
                  local.get 7
                  i32.const -4
                  i32.add
                  local.tee 8
                  i32.load
                  br_if 1 (;@6;)
                  local.get 8
                  local.get 6
                  i32.gt_u
                  if (result i32)  ;; label = @8
                    local.get 8
                    local.set 7
                    br 1 (;@7;)
                  else
                    local.get 8
                  end
                end
                local.set 7
              end
            end
            local.get 15
            local.get 15
            i32.load
            local.get 9
            i32.sub
            local.tee 8
            i32.store
            local.get 8
            i32.const 0
            i32.gt_s
            br_if 0 (;@4;)
          end
        else
          local.get 6
          local.set 8
          local.get 14
          local.set 6
        end
        i32.const 6
        local.get 3
        local.get 3
        i32.const 0
        i32.lt_s
        select
        local.set 13
        local.get 14
        local.set 11
        local.get 8
        i32.const 0
        i32.lt_s
        if (result i32)  ;; label = @3
          local.get 13
          i32.const 25
          i32.add
          i32.const 9
          i32.div_s
          i32.const 1
          i32.add
          local.set 17
          local.get 23
          i32.const 102
          i32.eq
          local.set 24
          local.get 7
          local.set 3
          loop (result i32)  ;; label = @4
            i32.const 0
            local.get 8
            i32.sub
            local.tee 7
            i32.const 9
            local.get 7
            i32.const 9
            i32.lt_s
            select
            local.set 18
            local.get 6
            local.get 3
            i32.lt_u
            if  ;; label = @5
              i32.const 1
              local.get 18
              i32.shl
              i32.const -1
              i32.add
              local.set 16
              i32.const 1000000000
              local.get 18
              i32.shr_u
              local.set 9
              i32.const 0
              local.set 8
              local.get 6
              local.set 7
              loop  ;; label = @6
                local.get 7
                local.get 8
                local.get 7
                i32.load
                local.tee 10
                local.get 18
                i32.shr_u
                i32.add
                i32.store
                local.get 10
                local.get 16
                i32.and
                local.get 9
                i32.mul
                local.set 8
                local.get 7
                i32.const 4
                i32.add
                local.tee 7
                local.get 3
                i32.lt_u
                br_if 0 (;@6;)
              end
              local.get 6
              local.get 6
              i32.const 4
              i32.add
              local.get 6
              i32.load
              select
              local.set 6
              local.get 8
              if  ;; label = @6
                local.get 3
                local.get 8
                i32.store
                local.get 3
                i32.const 4
                i32.add
                local.set 3
              end
            else
              local.get 6
              local.get 6
              i32.const 4
              i32.add
              local.get 6
              i32.load
              select
              local.set 6
            end
            local.get 14
            local.get 6
            local.get 24
            select
            local.tee 7
            local.get 17
            i32.const 2
            i32.shl
            i32.add
            local.get 3
            local.get 3
            local.get 7
            i32.sub
            i32.const 2
            i32.shr_s
            local.get 17
            i32.gt_s
            select
            local.set 10
            local.get 15
            local.get 15
            i32.load
            local.get 18
            i32.add
            local.tee 8
            i32.store
            local.get 8
            i32.const 0
            i32.lt_s
            if (result i32)  ;; label = @5
              local.get 10
              local.set 3
              br 1 (;@4;)
            else
              local.get 6
            end
          end
        else
          local.get 7
          local.set 10
          local.get 6
        end
        local.tee 3
        local.get 10
        i32.lt_u
        if  ;; label = @3
          local.get 11
          local.get 3
          i32.sub
          i32.const 2
          i32.shr_s
          i32.const 9
          i32.mul
          local.set 6
          local.get 3
          i32.load
          local.tee 8
          i32.const 10
          i32.ge_u
          if  ;; label = @4
            i32.const 10
            local.set 7
            loop  ;; label = @5
              local.get 6
              i32.const 1
              i32.add
              local.set 6
              local.get 8
              local.get 7
              i32.const 10
              i32.mul
              local.tee 7
              i32.ge_u
              br_if 0 (;@5;)
            end
          end
        else
          i32.const 0
          local.set 6
        end
        local.get 13
        i32.const 0
        local.get 6
        local.get 23
        i32.const 102
        i32.eq
        select
        i32.sub
        local.get 23
        i32.const 103
        i32.eq
        local.tee 17
        local.get 13
        i32.const 0
        i32.ne
        local.tee 24
        i32.and
        i32.const 31
        i32.shl
        i32.const 31
        i32.shr_s
        i32.add
        local.tee 7
        local.get 10
        local.get 11
        i32.sub
        i32.const 2
        i32.shr_s
        i32.const 9
        i32.mul
        i32.const -9
        i32.add
        i32.lt_s
        if (result i32)  ;; label = @3
          local.get 7
          i32.const 9216
          i32.add
          local.tee 7
          i32.const 9
          i32.div_s
          local.set 16
          local.get 7
          local.get 16
          i32.const 9
          i32.mul
          i32.sub
          local.tee 7
          i32.const 8
          i32.lt_s
          if  ;; label = @4
            i32.const 10
            local.set 8
            loop  ;; label = @5
              local.get 7
              i32.const 1
              i32.add
              local.set 9
              local.get 8
              i32.const 10
              i32.mul
              local.set 8
              local.get 7
              i32.const 7
              i32.lt_s
              if  ;; label = @6
                local.get 9
                local.set 7
                br 1 (;@5;)
              end
            end
          else
            i32.const 10
            local.set 8
          end
          local.get 16
          i32.const 2
          i32.shl
          local.get 14
          i32.add
          i32.const -4092
          i32.add
          local.tee 7
          i32.load
          local.tee 23
          local.get 8
          i32.div_u
          local.set 9
          local.get 7
          i32.const 4
          i32.add
          local.get 10
          i32.eq
          local.tee 16
          local.get 23
          local.get 8
          local.get 9
          i32.mul
          i32.sub
          local.tee 18
          i32.eqz
          i32.and
          i32.eqz
          if  ;; label = @4
            f64.const 0x1.0000000000001p+53 (;=9.0072e+15;)
            f64.const 0x1p+53 (;=9.0072e+15;)
            local.get 9
            i32.const 1
            i32.and
            select
            local.set 1
            f64.const 0x1p-1 (;=0.5;)
            f64.const 0x1p+0 (;=1;)
            f64.const 0x1.8p+0 (;=1.5;)
            local.get 16
            local.get 18
            local.get 8
            i32.const 1
            i32.shr_u
            local.tee 9
            i32.eq
            i32.and
            select
            local.get 18
            local.get 9
            i32.lt_u
            select
            local.set 29
            local.get 22
            if  ;; label = @5
              local.get 1
              f64.neg
              local.get 1
              local.get 21
              i32.load8_s
              i32.const 45
              i32.eq
              local.tee 9
              select
              local.set 1
              local.get 29
              f64.neg
              local.get 29
              local.get 9
              select
              local.set 29
            end
            local.get 7
            local.get 23
            local.get 18
            i32.sub
            local.tee 9
            i32.store
            local.get 1
            local.get 29
            f64.add
            local.get 1
            f64.ne
            if  ;; label = @5
              local.get 7
              local.get 8
              local.get 9
              i32.add
              local.tee 6
              i32.store
              local.get 6
              i32.const 999999999
              i32.gt_u
              if  ;; label = @6
                loop  ;; label = @7
                  local.get 7
                  i32.const 0
                  i32.store
                  local.get 7
                  i32.const -4
                  i32.add
                  local.tee 7
                  local.get 3
                  i32.lt_u
                  if  ;; label = @8
                    local.get 3
                    i32.const -4
                    i32.add
                    local.tee 3
                    i32.const 0
                    i32.store
                  end
                  local.get 7
                  local.get 7
                  i32.load
                  i32.const 1
                  i32.add
                  local.tee 6
                  i32.store
                  local.get 6
                  i32.const 999999999
                  i32.gt_u
                  br_if 0 (;@7;)
                end
              end
              local.get 11
              local.get 3
              i32.sub
              i32.const 2
              i32.shr_s
              i32.const 9
              i32.mul
              local.set 6
              local.get 3
              i32.load
              local.tee 9
              i32.const 10
              i32.ge_u
              if  ;; label = @6
                i32.const 10
                local.set 8
                loop  ;; label = @7
                  local.get 6
                  i32.const 1
                  i32.add
                  local.set 6
                  local.get 9
                  local.get 8
                  i32.const 10
                  i32.mul
                  local.tee 8
                  i32.ge_u
                  br_if 0 (;@7;)
                end
              end
            end
          end
          local.get 3
          local.set 8
          local.get 6
          local.set 9
          local.get 7
          i32.const 4
          i32.add
          local.tee 3
          local.get 10
          local.get 10
          local.get 3
          i32.gt_u
          select
        else
          local.get 3
          local.set 8
          local.get 6
          local.set 9
          local.get 10
        end
        local.tee 3
        local.get 8
        i32.gt_u
        if (result i32)  ;; label = @3
          loop (result i32)  ;; label = @4
            block (result i32)  ;; label = @5
              local.get 3
              i32.const -4
              i32.add
              local.tee 6
              i32.load
              if  ;; label = @6
                local.get 3
                local.set 6
                i32.const 1
                br 1 (;@5;)
              end
              local.get 6
              local.get 8
              i32.gt_u
              if (result i32)  ;; label = @6
                local.get 6
                local.set 3
                br 2 (;@4;)
              else
                i32.const 0
              end
            end
          end
        else
          local.get 3
          local.set 6
          i32.const 0
        end
        local.set 16
        local.get 17
        if (result i32)  ;; label = @3
          local.get 24
          i32.const 1
          i32.xor
          local.get 13
          i32.add
          local.tee 3
          local.get 9
          i32.gt_s
          local.get 9
          i32.const -5
          i32.gt_s
          i32.and
          if (result i32)  ;; label = @4
            local.get 3
            i32.const -1
            i32.add
            local.get 9
            i32.sub
            local.set 10
            local.get 5
            i32.const -1
            i32.add
          else
            local.get 3
            i32.const -1
            i32.add
            local.set 10
            local.get 5
            i32.const -2
            i32.add
          end
          local.set 5
          local.get 4
          i32.const 8
          i32.and
          if (result i32)  ;; label = @4
            local.get 10
          else
            local.get 16
            if  ;; label = @5
              local.get 6
              i32.const -4
              i32.add
              i32.load
              local.tee 13
              if  ;; label = @6
                local.get 13
                i32.const 10
                i32.rem_u
                if  ;; label = @7
                  i32.const 0
                  local.set 3
                else
                  i32.const 10
                  local.set 7
                  i32.const 0
                  local.set 3
                  loop  ;; label = @8
                    local.get 3
                    i32.const 1
                    i32.add
                    local.set 3
                    local.get 13
                    local.get 7
                    i32.const 10
                    i32.mul
                    local.tee 7
                    i32.rem_u
                    i32.eqz
                    br_if 0 (;@8;)
                  end
                end
              else
                i32.const 9
                local.set 3
              end
            else
              i32.const 9
              local.set 3
            end
            local.get 6
            local.get 11
            i32.sub
            i32.const 2
            i32.shr_s
            i32.const 9
            i32.mul
            i32.const -9
            i32.add
            local.set 7
            local.get 5
            i32.const 32
            i32.or
            i32.const 102
            i32.eq
            if (result i32)  ;; label = @5
              local.get 10
              local.get 7
              local.get 3
              i32.sub
              local.tee 3
              i32.const 0
              local.get 3
              i32.const 0
              i32.gt_s
              select
              local.tee 3
              local.get 10
              local.get 3
              i32.lt_s
              select
            else
              local.get 10
              local.get 7
              local.get 9
              i32.add
              local.get 3
              i32.sub
              local.tee 3
              i32.const 0
              local.get 3
              i32.const 0
              i32.gt_s
              select
              local.tee 3
              local.get 10
              local.get 3
              i32.lt_s
              select
            end
          end
        else
          local.get 13
        end
        local.set 3
        i32.const 0
        local.get 9
        i32.sub
        local.set 7
        local.get 0
        i32.const 32
        local.get 2
        local.get 5
        i32.const 32
        i32.or
        i32.const 102
        i32.eq
        local.tee 13
        if (result i32)  ;; label = @3
          i32.const 0
          local.set 10
          local.get 9
          i32.const 0
          local.get 9
          i32.const 0
          i32.gt_s
          select
        else
          local.get 20
          local.tee 11
          local.get 7
          local.get 9
          local.get 9
          i32.const 0
          i32.lt_s
          select
          i64.extend_i32_s
          local.get 11
          call 16
          local.tee 7
          i32.sub
          i32.const 2
          i32.lt_s
          if  ;; label = @4
            loop  ;; label = @5
              local.get 7
              i32.const -1
              i32.add
              local.tee 7
              i32.const 48
              i32.store8
              local.get 11
              local.get 7
              i32.sub
              i32.const 2
              i32.lt_s
              br_if 0 (;@5;)
            end
          end
          local.get 7
          i32.const -1
          i32.add
          local.get 9
          i32.const 31
          i32.shr_s
          i32.const 2
          i32.and
          i32.const 43
          i32.add
          i32.store8
          local.get 7
          i32.const -2
          i32.add
          local.tee 10
          local.get 5
          i32.store8
          local.get 11
          local.get 10
          i32.sub
        end
        local.get 22
        i32.const 1
        i32.add
        local.get 3
        i32.add
        i32.const 1
        local.get 4
        i32.const 3
        i32.shr_u
        i32.const 1
        i32.and
        local.get 3
        i32.const 0
        i32.ne
        local.tee 11
        select
        i32.add
        i32.add
        local.tee 17
        local.get 4
        call 12
        local.get 0
        local.get 21
        local.get 22
        call 11
        local.get 0
        i32.const 48
        local.get 2
        local.get 17
        local.get 4
        i32.const 65536
        i32.xor
        call 12
        local.get 13
        if  ;; label = @3
          local.get 12
          i32.const 9
          i32.add
          local.tee 13
          local.set 9
          local.get 12
          i32.const 8
          i32.add
          local.set 10
          local.get 14
          local.get 8
          local.get 8
          local.get 14
          i32.gt_u
          select
          local.tee 8
          local.set 7
          loop  ;; label = @4
            local.get 7
            i32.load
            i64.extend_i32_u
            local.get 13
            call 16
            local.set 5
            local.get 7
            local.get 8
            i32.eq
            if  ;; label = @5
              local.get 5
              local.get 13
              i32.eq
              if  ;; label = @6
                local.get 10
                i32.const 48
                i32.store8
                local.get 10
                local.set 5
              end
            else
              local.get 5
              local.get 12
              i32.gt_u
              if  ;; label = @6
                local.get 12
                i32.const 48
                local.get 5
                local.get 19
                i32.sub
                call 19
                drop
                loop  ;; label = @7
                  local.get 5
                  i32.const -1
                  i32.add
                  local.tee 5
                  local.get 12
                  i32.gt_u
                  br_if 0 (;@7;)
                end
              end
            end
            local.get 0
            local.get 5
            local.get 9
            local.get 5
            i32.sub
            call 11
            local.get 7
            i32.const 4
            i32.add
            local.tee 5
            local.get 14
            i32.le_u
            if  ;; label = @5
              local.get 5
              local.set 7
              br 1 (;@4;)
            end
          end
          local.get 4
          i32.const 8
          i32.and
          i32.eqz
          local.get 11
          i32.const 1
          i32.xor
          i32.and
          i32.eqz
          if  ;; label = @4
            local.get 0
            i32.const 2066
            i32.const 1
            call 11
          end
          local.get 0
          i32.const 48
          local.get 5
          local.get 6
          i32.lt_u
          local.get 3
          i32.const 0
          i32.gt_s
          i32.and
          if (result i32)  ;; label = @4
            loop (result i32)  ;; label = @5
              local.get 5
              i32.load
              i64.extend_i32_u
              local.get 13
              call 16
              local.tee 7
              local.get 12
              i32.gt_u
              if  ;; label = @6
                local.get 12
                i32.const 48
                local.get 7
                local.get 19
                i32.sub
                call 19
                drop
                loop  ;; label = @7
                  local.get 7
                  i32.const -1
                  i32.add
                  local.tee 7
                  local.get 12
                  i32.gt_u
                  br_if 0 (;@7;)
                end
              end
              local.get 0
              local.get 7
              local.get 3
              i32.const 9
              local.get 3
              i32.const 9
              i32.lt_s
              select
              call 11
              local.get 3
              i32.const -9
              i32.add
              local.set 7
              local.get 5
              i32.const 4
              i32.add
              local.tee 5
              local.get 6
              i32.lt_u
              local.get 3
              i32.const 9
              i32.gt_s
              i32.and
              if (result i32)  ;; label = @6
                local.get 7
                local.set 3
                br 1 (;@5;)
              else
                local.get 7
              end
            end
          else
            local.get 3
          end
          i32.const 9
          i32.add
          i32.const 9
          i32.const 0
          call 12
        else
          local.get 0
          i32.const 48
          local.get 8
          local.get 6
          local.get 8
          i32.const 4
          i32.add
          local.get 16
          select
          local.tee 16
          i32.lt_u
          local.get 3
          i32.const -1
          i32.gt_s
          i32.and
          if (result i32)  ;; label = @4
            local.get 4
            i32.const 8
            i32.and
            i32.eqz
            local.set 13
            local.get 12
            i32.const 9
            i32.add
            local.tee 24
            local.set 11
            i32.const 0
            local.get 19
            i32.sub
            local.set 9
            local.get 12
            i32.const 8
            i32.add
            local.set 14
            local.get 8
            local.set 6
            local.get 3
            local.set 5
            loop (result i32)  ;; label = @5
              local.get 24
              local.get 6
              i32.load
              i64.extend_i32_u
              local.get 24
              call 16
              local.tee 3
              i32.eq
              if  ;; label = @6
                local.get 14
                i32.const 48
                i32.store8
                local.get 14
                local.set 3
              end
              block  ;; label = @6
                local.get 6
                local.get 8
                i32.eq
                if  ;; label = @7
                  local.get 3
                  i32.const 1
                  i32.add
                  local.set 7
                  local.get 0
                  local.get 3
                  i32.const 1
                  call 11
                  local.get 5
                  i32.const 1
                  i32.lt_s
                  local.get 13
                  i32.and
                  if  ;; label = @8
                    local.get 7
                    local.set 3
                    br 2 (;@6;)
                  end
                  local.get 0
                  i32.const 2066
                  i32.const 1
                  call 11
                  local.get 7
                  local.set 3
                else
                  local.get 3
                  local.get 12
                  i32.le_u
                  br_if 1 (;@6;)
                  local.get 12
                  i32.const 48
                  local.get 3
                  local.get 9
                  i32.add
                  call 19
                  drop
                  loop  ;; label = @8
                    local.get 3
                    i32.const -1
                    i32.add
                    local.tee 3
                    local.get 12
                    i32.gt_u
                    br_if 0 (;@8;)
                  end
                end
              end
              local.get 0
              local.get 3
              local.get 11
              local.get 3
              i32.sub
              local.tee 3
              local.get 5
              local.get 5
              local.get 3
              i32.gt_s
              select
              call 11
              local.get 6
              i32.const 4
              i32.add
              local.tee 6
              local.get 16
              i32.lt_u
              local.get 5
              local.get 3
              i32.sub
              local.tee 5
              i32.const -1
              i32.gt_s
              i32.and
              br_if 0 (;@5;)
              local.get 5
            end
          else
            local.get 3
          end
          i32.const 18
          i32.add
          i32.const 18
          i32.const 0
          call 12
          local.get 0
          local.get 10
          local.get 20
          local.get 10
          i32.sub
          call 11
        end
        local.get 0
        i32.const 32
        local.get 2
        local.get 17
        local.get 4
        i32.const 8192
        i32.xor
        call 12
        local.get 17
      end
    end
    local.set 0
    local.get 25
    global.set 2
    local.get 2
    local.get 0
    local.get 0
    local.get 2
    i32.lt_s
    select)
  (func (;51;) (type 7) (result i32)
    i32.const 3120)
  (func (;52;) (type 3) (param i32 i64 i32) (result i64)
    i64.const 0)
  (func (;53;) (type 0) (param i32 i32 i32) (result i32)
    (local i32 i32 i32 i32 i32 i32 i32)
    global.get 2
    local.set 6
    global.get 2
    i32.const 32
    i32.add
    global.set 2
    local.get 6
    i32.const 16
    i32.add
    local.set 7
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
    local.tee 5
    i32.store offset=4
    local.get 3
    local.get 1
    i32.store offset=8
    local.get 3
    local.get 2
    i32.store offset=12
    local.get 3
    local.set 1
    i32.const 2
    local.set 4
    local.get 2
    local.get 5
    i32.add
    local.set 5
    block  ;; label = @1
      block  ;; label = @2
        loop  ;; label = @3
          local.get 5
          local.get 0
          i32.load offset=60
          local.get 1
          local.get 4
          local.get 7
          call 7
          i32.const 65535
          i32.and
          if (result i32)  ;; label = @4
            local.get 7
            i32.const -1
            i32.store
            i32.const -1
          else
            local.get 7
            i32.load
          end
          local.tee 3
          i32.ne
          if  ;; label = @4
            local.get 3
            i32.const 0
            i32.lt_s
            br_if 2 (;@2;)
            local.get 1
            i32.const 8
            i32.add
            local.get 1
            local.get 3
            local.get 1
            i32.load offset=4
            local.tee 8
            i32.gt_u
            local.tee 9
            select
            local.tee 1
            local.get 3
            local.get 8
            i32.const 0
            local.get 9
            select
            i32.sub
            local.tee 8
            local.get 1
            i32.load
            i32.add
            i32.store
            local.get 1
            local.get 1
            i32.load offset=4
            local.get 8
            i32.sub
            i32.store offset=4
            local.get 9
            i32.const 31
            i32.shl
            i32.const 31
            i32.shr_s
            local.get 4
            i32.add
            local.set 4
            local.get 5
            local.get 3
            i32.sub
            local.set 5
            br 1 (;@3;)
          end
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
        br 1 (;@1;)
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
      local.get 4
      i32.const 2
      i32.eq
      if (result i32)  ;; label = @2
        i32.const 0
      else
        local.get 2
        local.get 1
        i32.load offset=4
        i32.sub
      end
      local.set 2
    end
    local.get 6
    global.set 2
    local.get 2)
  (func (;54;) (type 4) (param i32) (result i32)
    (local i32 i32)
    global.get 2
    local.set 2
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
    local.get 2)
  (global (;0;) i32 (i32.const 0))
  (global (;1;) i32 (i32.const 4896))
  (global (;2;) (mut i32) (i32.const 4928))
  (export "___errno_location" (func 51))
  (export "_main" (func 35))
  (export "stackAlloc" (func 54))
  (elem (;0;) (i32.const 0) 40 33 39 50 38 53 37 52 36 49)
  (data (;0;) (i32.const 1024) "\11\00\0a\00\11\11\11\00\00\00\00\05\00\00\00\00\00\00\09\00\00\00\00\0b")
  (data (;1;) (i32.const 1056) "\11\00\0f\0a\11\11\11\03\0a\07\00\01\13\09\0b\0b\00\00\09\06\0b\00\00\0b\00\06\11\00\00\00\11\11\11")
  (data (;2;) (i32.const 1105) "\0b")
  (data (;3;) (i32.const 1114) "\11\00\0a\0a\11\11\11\00\0a\00\00\02\00\09\0b\00\00\00\09\00\0b\00\00\0b")
  (data (;4;) (i32.const 1163) "\0c")
  (data (;5;) (i32.const 1175) "\0c\00\00\00\00\0c\00\00\00\00\09\0c\00\00\00\00\00\0c\00\00\0c")
  (data (;6;) (i32.const 1221) "\0e")
  (data (;7;) (i32.const 1233) "\0d\00\00\00\04\0d\00\00\00\00\09\0e\00\00\00\00\00\0e\00\00\0e")
  (data (;8;) (i32.const 1279) "\10")
  (data (;9;) (i32.const 1291) "\0f\00\00\00\00\0f\00\00\00\00\09\10\00\00\00\00\00\10\00\00\10\00\00\12\00\00\00\12\12\12")
  (data (;10;) (i32.const 1346) "\12\00\00\00\12\12\12\00\00\00\00\00\00\09")
  (data (;11;) (i32.const 1395) "\0b")
  (data (;12;) (i32.const 1407) "\0a\00\00\00\00\0a\00\00\00\00\09\0b\00\00\00\00\00\0b\00\00\0b")
  (data (;13;) (i32.const 1453) "\0c")
  (data (;14;) (i32.const 1465) "\0c\00\00\00\00\0c\00\00\00\00\09\0c\00\00\00\00\00\0c\00\00\0c\00\000123456789ABCDEF\05")
  (data (;15;) (i32.const 1516) "\01")
  (data (;16;) (i32.const 1540) "\01\00\00\00\01\00\00\00(\08\00\00\00\04")
  (data (;17;) (i32.const 1564) "\01")
  (data (;18;) (i32.const 1579) "\0a\ff\ff\ff\ff")
  (data (;19;) (i32.const 1648) "\e0\05\00\00\e0\05")
  (data (;20;) (i32.const 1844) "\5c\0c")
  (data (;21;) (i32.const 1900) "iteration %d of %s: \09%ld\0a\00first %d warmup iterations %s: \00, \00%ld\00last %d iterations %s: \00### %s: %ld\0a\00BinaryTrees\00-+   0X0x\00(null)\00-0X+0X 0X-0x+0x 0x\00inf\00INF\00nan\00NAN\00."))
