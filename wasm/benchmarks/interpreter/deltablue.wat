;;
;; Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
;; DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
;;
;; The Universal Permissive License (UPL), Version 1.0
;;
;; Subject to the condition set forth below, permission is hereby granted to any
;; person obtaining a copy of this software, associated documentation and/or
;; data (collectively the "Software"), free of charge and under any and all
;; copyright rights in the Software, and any and all patent rights owned or
;; freely licensable by each licensor hereunder covering either (i) the
;; unmodified Software as contributed to or provided by such licensor, or (ii)
;; the Larger Works (as defined below), to deal in both
;;
;; (a) the Software, and
;;
;; (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
;; one is included with the Software each a "Larger Work" to which the Software
;; is contributed by such licensors),
;;
;; without restriction, including without limitation the rights to copy, create
;; derivative works of, display, perform, and distribute the Software and make,
;; use, sell, offer for sale, import, export, have made, and have sold the
;; Software and the Larger Work(s), and to sublicense the foregoing rights on
;; either these or other terms.
;;
;; This license is subject to the following condition:
;;
;; The above copyright notice and either this complete permission notice or at a
;; minimum a reference to the UPL must be included in all copies or substantial
;; portions of the Software.
;;
;; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
;; IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
;; FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
;; AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
;; LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
;; OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
;; SOFTWARE.
;;
(module
  (type (;0;) (func (param i32)))
  (type (;1;) (func (param i32 i32 i32) (result i32)))
  (type (;2;) (func (param i32 i64 i32) (result i64)))
  (type (;3;) (func (param i32 i32) (result i32)))
  (type (;4;) (func (param i32) (result i32)))
  (type (;5;) (func (param i32 i64 i32 i32) (result i32)))
  (type (;6;) (func (param i32 i32 i32 i32) (result i32)))
  (type (;7;) (func))
  (type (;8;) (func (param i32 i32)))
  (type (;9;) (func (result i32)))
  (type (;10;) (func (param i32 i32 i32 i32 i32) (result i32)))
  (type (;11;) (func (param i32 i32 i32)))
  (type (;12;) (func (param i32 i32 i32 i32 i32)))
  (type (;13;) (func (param f64 i32) (result f64)))
  (import "wasi_snapshot_preview1" "args_get" (func $__imported_wasi_snapshot_preview1_args_get (type 3)))
  (import "wasi_snapshot_preview1" "args_sizes_get" (func $__imported_wasi_snapshot_preview1_args_sizes_get (type 3)))
  (import "wasi_snapshot_preview1" "fd_close" (func $__imported_wasi_snapshot_preview1_fd_close (type 4)))
  (import "wasi_snapshot_preview1" "fd_fdstat_get" (func $__imported_wasi_snapshot_preview1_fd_fdstat_get (type 3)))
  (import "wasi_snapshot_preview1" "fd_seek" (func $__imported_wasi_snapshot_preview1_fd_seek (type 5)))
  (import "wasi_snapshot_preview1" "fd_write" (func $__imported_wasi_snapshot_preview1_fd_write (type 6)))
  (import "wasi_snapshot_preview1" "proc_exit" (func $__imported_wasi_snapshot_preview1_proc_exit (type 0)))
  (func $_start (type 7)
    (local i32)
    block  ;; label = @1
      call $__main_void
      local.tee 0
      i32.eqz
      br_if 0 (;@1;)
      local.get 0
      call $exit
      unreachable
    end)
  (func $List_Add (type 8) (param i32 i32)
    (local i32 i32 i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 2
    global.set $__stack_pointer
    block  ;; label = @1
      block  ;; label = @2
        local.get 0
        i32.load offset=12
        local.tee 3
        local.get 0
        i32.load offset=4
        local.tee 4
        i32.const -1
        i32.add
        i32.lt_s
        br_if 0 (;@2;)
        local.get 0
        i32.load
        local.set 5
        local.get 0
        i32.load offset=8
        local.tee 6
        local.set 7
        block  ;; label = @3
          local.get 3
          local.get 6
          i32.sub
          i32.const 1
          i32.add
          local.get 4
          i32.lt_s
          br_if 0 (;@3;)
          local.get 0
          local.get 4
          i32.const 2
          local.get 4
          i32.const 2
          i32.gt_s
          select
          local.tee 7
          i32.const 512
          local.get 7
          i32.const 512
          i32.lt_u
          select
          local.get 4
          i32.add
          local.tee 4
          i32.store offset=4
          local.get 0
          local.get 5
          local.get 4
          i32.const 2
          i32.shl
          call $realloc
          local.tee 4
          i32.store
          local.get 4
          i32.eqz
          br_if 2 (;@1;)
          local.get 0
          i32.load offset=8
          local.set 7
        end
        block  ;; label = @3
          local.get 7
          br_if 0 (;@3;)
          local.get 0
          i32.load offset=12
          local.set 3
          br 1 (;@2;)
        end
        block  ;; label = @3
          local.get 6
          local.get 3
          i32.gt_s
          br_if 0 (;@3;)
          local.get 5
          local.get 3
          i32.const 2
          i32.shl
          i32.add
          local.set 4
          local.get 6
          i32.const 2
          i32.shl
          local.set 3
          loop  ;; label = @4
            local.get 5
            local.get 5
            local.get 3
            i32.add
            i32.load
            i32.store
            local.get 5
            i32.const 4
            i32.add
            local.tee 5
            local.get 3
            i32.add
            local.get 4
            i32.le_u
            br_if 0 (;@4;)
          end
        end
        local.get 0
        i32.const 0
        i32.store offset=8
        local.get 0
        i32.load offset=12
        local.get 7
        i32.sub
        local.set 3
      end
      local.get 0
      local.get 3
      i32.const 1
      i32.add
      local.tee 5
      i32.store offset=12
      local.get 0
      i32.load
      local.get 5
      i32.const 2
      i32.shl
      i32.add
      local.get 1
      i32.store
      local.get 2
      i32.const 16
      i32.add
      global.set $__stack_pointer
      return
    end
    local.get 2
    i32.const 1024
    i32.store
    i32.const 1408
    local.get 2
    call $printf
    drop
    i32.const -1
    call $exit
    unreachable)
  (func $Noop (type 0) (param i32))
  (func $Variable_Create (type 3) (param i32 i32) (result i32)
    (local i32 i32 i32)
    global.get $__stack_pointer
    i32.const 48
    i32.sub
    local.tee 2
    global.set $__stack_pointer
    block  ;; label = @1
      block  ;; label = @2
        block  ;; label = @3
          i32.const 36
          call $malloc
          local.tee 3
          i32.eqz
          br_if 0 (;@3;)
          local.get 3
          local.get 1
          i32.store
          i32.const 16
          call $malloc
          local.tee 1
          i32.eqz
          br_if 1 (;@2;)
          local.get 1
          i32.const 8
          call $malloc
          local.tee 4
          i32.store
          local.get 4
          i32.eqz
          br_if 2 (;@1;)
          local.get 1
          i32.const -1
          i32.store offset=12
          local.get 1
          i64.const 2
          i64.store offset=4 align=4
          local.get 3
          i64.const 4294967302
          i64.store offset=16 align=4
          local.get 3
          i64.const 0
          i64.store offset=8 align=4
          local.get 3
          local.get 1
          i32.store offset=4
          local.get 3
          i32.const 24
          i32.add
          local.get 0
          i32.const 10
          call $strncpy
          drop
          local.get 3
          i32.const 33
          i32.add
          i32.const 0
          i32.store8
          i32.const 0
          i32.load offset=3984
          local.get 3
          call $List_Add
          local.get 2
          i32.const 48
          i32.add
          global.set $__stack_pointer
          local.get 3
          return
        end
        local.get 2
        i32.const 1024
        i32.store
        i32.const 1408
        local.get 2
        call $printf
        drop
        i32.const -1
        call $exit
        unreachable
      end
      local.get 2
      i32.const 1024
      i32.store offset=16
      i32.const 1408
      local.get 2
      i32.const 16
      i32.add
      call $printf
      drop
      i32.const -1
      call $exit
      unreachable
    end
    local.get 2
    i32.const 1024
    i32.store offset=32
    i32.const 1408
    local.get 2
    i32.const 32
    i32.add
    call $printf
    drop
    i32.const -1
    call $exit
    unreachable)
  (func $Variable_Destroy (type 0) (param i32)
    (local i32 i32 i32)
    global.get $__stack_pointer
    i32.const 32
    i32.sub
    local.tee 1
    global.set $__stack_pointer
    block  ;; label = @1
      block  ;; label = @2
        local.get 0
        i32.load offset=4
        local.tee 2
        i32.eqz
        br_if 0 (;@2;)
        local.get 2
        i32.load
        local.tee 3
        i32.eqz
        br_if 1 (;@1;)
        local.get 3
        call $free
        local.get 2
        call $free
        local.get 0
        call $free
        local.get 1
        i32.const 32
        i32.add
        global.set $__stack_pointer
        return
      end
      local.get 1
      i32.const 1242
      i32.store
      i32.const 1408
      local.get 1
      call $printf
      drop
      i32.const -1
      call $exit
      unreachable
    end
    local.get 1
    i32.const 1174
    i32.store offset=16
    i32.const 1408
    local.get 1
    i32.const 16
    i32.add
    call $printf
    drop
    i32.const -1
    call $exit
    unreachable)
  (func $InitDeltaBlue (type 7)
    (local i32 i32 i32)
    global.get $__stack_pointer
    i32.const 32
    i32.sub
    local.tee 0
    global.set $__stack_pointer
    block  ;; label = @1
      block  ;; label = @2
        block  ;; label = @3
          block  ;; label = @4
            i32.const 0
            i32.load offset=3984
            local.tee 1
            br_if 0 (;@4;)
            i32.const 16
            call $malloc
            local.tee 1
            i32.eqz
            br_if 2 (;@2;)
            local.get 1
            i32.const 512
            call $malloc
            local.tee 2
            i32.store
            local.get 2
            i32.eqz
            br_if 3 (;@1;)
            local.get 1
            i32.const -1
            i32.store offset=12
            local.get 1
            i64.const 128
            i64.store offset=4 align=4
            i32.const 0
            local.get 1
            i32.store offset=3984
            br 1 (;@3;)
          end
          local.get 1
          i32.load offset=12
          local.get 1
          i32.load offset=8
          local.tee 2
          i32.lt_s
          br_if 0 (;@3;)
          local.get 1
          local.get 2
          i32.const 1
          i32.add
          i32.store offset=8
          local.get 1
          i32.load
          local.get 2
          i32.const 2
          i32.shl
          i32.add
          i32.load
          local.tee 2
          i32.eqz
          br_if 0 (;@3;)
          loop  ;; label = @4
            local.get 2
            call $FreeVariable
            i32.const 0
            i32.load offset=3984
            local.tee 1
            i32.load offset=12
            local.get 1
            i32.load offset=8
            local.tee 2
            i32.lt_s
            br_if 1 (;@3;)
            local.get 1
            local.get 2
            i32.const 1
            i32.add
            i32.store offset=8
            local.get 1
            i32.load
            local.get 2
            i32.const 2
            i32.shl
            i32.add
            i32.load
            local.tee 2
            br_if 0 (;@4;)
          end
        end
        local.get 1
        i64.const -4294967296
        i64.store offset=8 align=4
        i32.const 0
        i32.const 0
        i32.store offset=3988
        local.get 0
        i32.const 32
        i32.add
        global.set $__stack_pointer
        return
      end
      local.get 0
      i32.const 1024
      i32.store
      i32.const 1408
      local.get 0
      call $printf
      drop
      i32.const -1
      call $exit
      unreachable
    end
    local.get 0
    i32.const 1024
    i32.store offset=16
    i32.const 1408
    local.get 0
    i32.const 16
    i32.add
    call $printf
    drop
    i32.const -1
    call $exit
    unreachable)
  (func $FreeVariable (type 0) (param i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 1
    global.set $__stack_pointer
    block  ;; label = @1
      block  ;; label = @2
        local.get 0
        i32.load offset=4
        local.tee 2
        i32.load offset=12
        local.get 2
        i32.load offset=8
        local.tee 3
        i32.lt_s
        br_if 0 (;@2;)
        local.get 2
        local.get 3
        i32.const 1
        i32.add
        i32.store offset=8
        local.get 2
        i32.load
        local.get 3
        i32.const 2
        i32.shl
        i32.add
        i32.load
        local.tee 4
        i32.eqz
        br_if 0 (;@2;)
        loop  ;; label = @3
          block  ;; label = @4
            local.get 4
            i32.load8_s offset=14
            local.tee 2
            i32.const 1
            i32.lt_s
            br_if 0 (;@4;)
            local.get 2
            i32.const 255
            i32.and
            local.set 5
            local.get 4
            i32.const 24
            i32.add
            local.set 6
            loop  ;; label = @5
              local.get 6
              local.get 5
              i32.const -1
              i32.add
              local.tee 7
              i32.const 2
              i32.shl
              i32.add
              i32.load
              i32.load offset=4
              local.tee 8
              i32.load offset=8
              local.set 2
              local.get 8
              i32.const 0
              i32.store offset=8
              local.get 8
              local.get 8
              i32.load offset=12
              local.tee 9
              local.get 2
              i32.sub
              local.tee 10
              i32.store offset=12
              block  ;; label = @6
                local.get 9
                local.get 2
                i32.lt_s
                br_if 0 (;@6;)
                local.get 8
                i32.load
                local.tee 3
                local.get 9
                i32.const 2
                i32.shl
                i32.add
                local.set 11
                local.get 3
                local.get 2
                i32.const 2
                i32.shl
                i32.add
                local.set 2
                loop  ;; label = @7
                  block  ;; label = @8
                    block  ;; label = @9
                      local.get 2
                      i32.load
                      local.tee 9
                      local.get 4
                      i32.ne
                      br_if 0 (;@9;)
                      local.get 8
                      local.get 10
                      i32.const -1
                      i32.add
                      local.tee 10
                      i32.store offset=12
                      br 1 (;@8;)
                    end
                    local.get 3
                    local.get 9
                    i32.store
                    local.get 3
                    i32.const 4
                    i32.add
                    local.set 3
                  end
                  local.get 2
                  i32.const 4
                  i32.add
                  local.tee 2
                  local.get 11
                  i32.le_u
                  br_if 0 (;@7;)
                end
              end
              local.get 5
              i32.const 1
              i32.gt_s
              local.set 2
              local.get 7
              local.set 5
              local.get 2
              br_if 0 (;@5;)
            end
          end
          local.get 4
          i32.load
          i32.eqz
          br_if 2 (;@1;)
          local.get 4
          call $free
          local.get 0
          i32.load offset=4
          local.tee 2
          i32.load offset=12
          local.get 2
          i32.load offset=8
          local.tee 3
          i32.lt_s
          br_if 1 (;@2;)
          local.get 2
          local.get 3
          i32.const 1
          i32.add
          i32.store offset=8
          local.get 2
          i32.load
          local.get 3
          i32.const 2
          i32.shl
          i32.add
          i32.load
          local.tee 4
          br_if 0 (;@3;)
        end
      end
      local.get 0
      call $Variable_Destroy
      local.get 1
      i32.const 16
      i32.add
      global.set $__stack_pointer
      return
    end
    local.get 1
    i32.const 1205
    i32.store
    i32.const 1408
    local.get 1
    call $printf
    drop
    i32.const -1
    call $exit
    unreachable)
  (func $DestroyConstraint (type 0) (param i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 1
    global.set $__stack_pointer
    block  ;; label = @1
      local.get 0
      i32.load8_u offset=12
      i32.const 255
      i32.eq
      br_if 0 (;@1;)
      local.get 0
      call $IncrementalRemove
    end
    block  ;; label = @1
      local.get 0
      i32.load8_s offset=14
      local.tee 2
      i32.const 1
      i32.lt_s
      br_if 0 (;@1;)
      local.get 2
      i32.const 255
      i32.and
      local.set 3
      loop  ;; label = @2
        local.get 0
        local.get 3
        i32.const -1
        i32.add
        local.tee 4
        i32.const 2
        i32.shl
        i32.add
        i32.const 24
        i32.add
        i32.load
        i32.load offset=4
        local.tee 5
        i32.load offset=8
        local.set 2
        local.get 5
        i32.const 0
        i32.store offset=8
        local.get 5
        local.get 5
        i32.load offset=12
        local.tee 6
        local.get 2
        i32.sub
        local.tee 7
        i32.store offset=12
        block  ;; label = @3
          local.get 6
          local.get 2
          i32.lt_s
          br_if 0 (;@3;)
          local.get 5
          i32.load
          local.tee 8
          local.get 6
          i32.const 2
          i32.shl
          i32.add
          local.set 9
          local.get 8
          local.get 2
          i32.const 2
          i32.shl
          i32.add
          local.set 2
          loop  ;; label = @4
            block  ;; label = @5
              block  ;; label = @6
                local.get 2
                i32.load
                local.tee 6
                local.get 0
                i32.ne
                br_if 0 (;@6;)
                local.get 5
                local.get 7
                i32.const -1
                i32.add
                local.tee 7
                i32.store offset=12
                br 1 (;@5;)
              end
              local.get 8
              local.get 6
              i32.store
              local.get 8
              i32.const 4
              i32.add
              local.set 8
            end
            local.get 2
            i32.const 4
            i32.add
            local.tee 2
            local.get 9
            i32.le_u
            br_if 0 (;@4;)
          end
        end
        local.get 3
        i32.const 1
        i32.gt_s
        local.set 2
        local.get 4
        local.set 3
        local.get 2
        br_if 0 (;@2;)
      end
    end
    block  ;; label = @1
      local.get 0
      i32.load
      br_if 0 (;@1;)
      local.get 1
      i32.const 1205
      i32.store
      i32.const 1408
      local.get 1
      call $printf
      drop
      i32.const -1
      call $exit
      unreachable
    end
    local.get 0
    call $free
    local.get 1
    i32.const 16
    i32.add
    global.set $__stack_pointer)
  (func $IncrementalRemove (type 0) (param i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 112
    i32.sub
    local.tee 1
    global.set $__stack_pointer
    local.get 0
    local.get 0
    i32.load8_s offset=12
    i32.add
    i32.const 15
    i32.add
    i32.load8_s
    local.set 2
    local.get 0
    i32.const 255
    i32.store8 offset=12
    local.get 0
    local.get 2
    i32.const 2
    i32.shl
    i32.add
    i32.const 24
    i32.add
    i32.load
    local.set 3
    block  ;; label = @1
      local.get 0
      i32.load8_u offset=14
      local.tee 4
      i32.const 24
      i32.shl
      i32.const 24
      i32.shr_s
      i32.const 1
      i32.lt_s
      br_if 0 (;@1;)
      loop  ;; label = @2
        local.get 0
        local.get 4
        i32.const -1
        i32.add
        local.tee 5
        i32.const 2
        i32.shl
        i32.add
        i32.const 24
        i32.add
        i32.load
        i32.load offset=4
        local.tee 6
        i32.load offset=8
        local.set 2
        local.get 6
        i32.const 0
        i32.store offset=8
        local.get 6
        local.get 6
        i32.load offset=12
        local.tee 7
        local.get 2
        i32.sub
        local.tee 8
        i32.store offset=12
        block  ;; label = @3
          local.get 7
          local.get 2
          i32.lt_s
          br_if 0 (;@3;)
          local.get 6
          i32.load
          local.tee 9
          local.get 7
          i32.const 2
          i32.shl
          i32.add
          local.set 10
          local.get 9
          local.get 2
          i32.const 2
          i32.shl
          i32.add
          local.set 2
          loop  ;; label = @4
            block  ;; label = @5
              block  ;; label = @6
                local.get 2
                i32.load
                local.tee 7
                local.get 0
                i32.ne
                br_if 0 (;@6;)
                local.get 6
                local.get 8
                i32.const -1
                i32.add
                local.tee 8
                i32.store offset=12
                br 1 (;@5;)
              end
              local.get 9
              local.get 7
              i32.store
              local.get 9
              i32.const 4
              i32.add
              local.set 9
            end
            local.get 2
            i32.const 4
            i32.add
            local.tee 2
            local.get 10
            i32.le_u
            br_if 0 (;@4;)
          end
        end
        local.get 4
        i32.const 1
        i32.gt_s
        local.set 2
        local.get 5
        local.set 4
        local.get 2
        br_if 0 (;@2;)
      end
    end
    block  ;; label = @1
      block  ;; label = @2
        block  ;; label = @3
          i32.const 16
          call $malloc
          local.tee 2
          i32.eqz
          br_if 0 (;@3;)
          local.get 2
          i32.const 32
          call $malloc
          local.tee 9
          i32.store
          block  ;; label = @4
            local.get 9
            i32.eqz
            br_if 0 (;@4;)
            local.get 2
            i32.const -1
            i32.store offset=12
            local.get 2
            i64.const 8
            i64.store offset=4 align=4
            i32.const 0
            local.get 2
            i32.store offset=4000
            local.get 3
            call $RemovePropagateFrom
            i32.const 0
            i32.const 0
            i32.store offset=3996
            i32.const 0
            i32.load offset=4000
            local.tee 2
            i32.load offset=8
            local.tee 9
            local.get 2
            i32.load offset=12
            local.tee 7
            i32.gt_s
            br_if 2 (;@2;)
            i32.const 0
            local.set 11
            loop  ;; label = @5
              block  ;; label = @6
                local.get 9
                local.get 7
                i32.gt_s
                br_if 0 (;@6;)
                local.get 2
                i32.load
                local.tee 2
                local.get 7
                i32.const 2
                i32.shl
                i32.add
                local.set 12
                local.get 2
                local.get 9
                i32.const 2
                i32.shl
                i32.add
                local.set 13
                loop  ;; label = @7
                  block  ;; label = @8
                    local.get 13
                    i32.load
                    local.tee 10
                    i32.load offset=8
                    local.get 11
                    i32.ne
                    br_if 0 (;@8;)
                    i32.const 0
                    i32.const 0
                    i32.load offset=3988
                    i32.const 1
                    i32.add
                    local.tee 7
                    i32.store offset=3988
                    block  ;; label = @9
                      block  ;; label = @10
                        local.get 10
                        i32.load8_u offset=13
                        local.tee 5
                        i32.const 24
                        i32.shl
                        i32.const 24
                        i32.shr_s
                        local.tee 2
                        i32.const 0
                        i32.gt_s
                        br_if 0 (;@10;)
                        local.get 10
                        i32.const 255
                        i32.store8 offset=12
                        br 1 (;@9;)
                      end
                      local.get 5
                      i32.const 1
                      i32.and
                      local.set 14
                      i32.const -1
                      local.set 0
                      block  ;; label = @10
                        block  ;; label = @11
                          local.get 2
                          i32.const 1
                          i32.ne
                          br_if 0 (;@11;)
                          local.get 11
                          local.set 9
                          br 1 (;@10;)
                        end
                        local.get 10
                        local.get 5
                        i32.add
                        local.set 3
                        i32.const 0
                        local.set 2
                        i32.const 0
                        local.get 5
                        i32.const 254
                        i32.and
                        i32.sub
                        local.set 15
                        i32.const -1
                        local.set 0
                        local.get 11
                        local.set 9
                        loop  ;; label = @11
                          local.get 5
                          local.get 2
                          i32.add
                          local.set 8
                          block  ;; label = @12
                            local.get 10
                            local.get 3
                            local.get 2
                            i32.add
                            local.tee 6
                            i32.const 14
                            i32.add
                            i32.load8_s
                            i32.const 2
                            i32.shl
                            i32.add
                            i32.const 24
                            i32.add
                            i32.load
                            local.tee 4
                            i32.load offset=12
                            local.get 7
                            i32.eq
                            br_if 0 (;@12;)
                            local.get 8
                            i32.const -1
                            i32.add
                            local.get 0
                            local.get 4
                            i32.load offset=16
                            local.tee 4
                            local.get 9
                            i32.gt_u
                            local.tee 16
                            select
                            local.set 0
                            local.get 4
                            local.get 9
                            local.get 16
                            select
                            local.set 9
                          end
                          block  ;; label = @12
                            local.get 10
                            local.get 6
                            i32.const 13
                            i32.add
                            i32.load8_s
                            i32.const 2
                            i32.shl
                            i32.add
                            i32.const 24
                            i32.add
                            i32.load
                            local.tee 6
                            i32.load offset=12
                            local.get 7
                            i32.eq
                            br_if 0 (;@12;)
                            local.get 8
                            i32.const -2
                            i32.add
                            local.get 0
                            local.get 6
                            i32.load offset=16
                            local.tee 8
                            local.get 9
                            i32.gt_u
                            local.tee 6
                            select
                            local.set 0
                            local.get 8
                            local.get 9
                            local.get 6
                            select
                            local.set 9
                          end
                          local.get 15
                          local.get 2
                          i32.const -2
                          i32.add
                          local.tee 2
                          i32.ne
                          br_if 0 (;@11;)
                        end
                        local.get 5
                        local.get 2
                        i32.add
                        local.set 5
                      end
                      block  ;; label = @10
                        local.get 14
                        i32.eqz
                        br_if 0 (;@10;)
                        local.get 10
                        local.get 10
                        local.get 5
                        i32.const -1
                        i32.add
                        local.tee 2
                        i32.add
                        i32.const 15
                        i32.add
                        i32.load8_s
                        i32.const 2
                        i32.shl
                        i32.add
                        i32.const 24
                        i32.add
                        i32.load
                        local.tee 8
                        i32.load offset=12
                        local.get 7
                        i32.eq
                        br_if 0 (;@10;)
                        local.get 2
                        local.get 0
                        local.get 8
                        i32.load offset=16
                        local.get 9
                        i32.gt_u
                        select
                        local.set 0
                      end
                      local.get 10
                      local.get 0
                      i32.store8 offset=12
                      local.get 0
                      i32.const 24
                      i32.shl
                      local.tee 2
                      i32.const -16777216
                      i32.eq
                      br_if 0 (;@9;)
                      local.get 10
                      local.get 2
                      i32.const 24
                      i32.shr_s
                      i32.add
                      i32.const 15
                      i32.add
                      i32.load8_s
                      local.set 3
                      block  ;; label = @10
                        local.get 10
                        i32.load8_s offset=14
                        local.tee 2
                        i32.const 1
                        i32.lt_s
                        br_if 0 (;@10;)
                        local.get 2
                        i32.const 255
                        i32.and
                        local.tee 2
                        i32.const -1
                        i32.add
                        local.set 6
                        block  ;; label = @11
                          local.get 2
                          i32.const 3
                          i32.and
                          local.tee 0
                          i32.eqz
                          br_if 0 (;@11;)
                          local.get 3
                          i32.const 1
                          i32.add
                          local.set 8
                          local.get 10
                          local.get 2
                          i32.const 2
                          i32.shl
                          i32.add
                          i32.const 20
                          i32.add
                          local.set 9
                          loop  ;; label = @12
                            block  ;; label = @13
                              local.get 8
                              local.get 2
                              i32.eq
                              br_if 0 (;@13;)
                              local.get 9
                              i32.load
                              local.get 7
                              i32.store offset=12
                            end
                            local.get 2
                            i32.const -1
                            i32.add
                            local.set 2
                            local.get 9
                            i32.const -4
                            i32.add
                            local.set 9
                            local.get 0
                            i32.const -1
                            i32.add
                            local.tee 0
                            br_if 0 (;@12;)
                          end
                        end
                        local.get 6
                        i32.const 3
                        i32.lt_u
                        br_if 0 (;@10;)
                        local.get 3
                        i32.const 1
                        i32.add
                        local.set 8
                        local.get 3
                        i32.const 2
                        i32.add
                        local.set 6
                        local.get 3
                        i32.const 3
                        i32.add
                        local.set 4
                        local.get 3
                        i32.const 4
                        i32.add
                        local.set 5
                        local.get 10
                        local.get 2
                        i32.const 2
                        i32.shl
                        i32.add
                        i32.const 12
                        i32.add
                        local.set 9
                        loop  ;; label = @11
                          block  ;; label = @12
                            local.get 8
                            local.get 2
                            i32.eq
                            br_if 0 (;@12;)
                            local.get 9
                            i32.const 8
                            i32.add
                            i32.load
                            local.get 7
                            i32.store offset=12
                          end
                          block  ;; label = @12
                            local.get 6
                            local.get 2
                            i32.eq
                            br_if 0 (;@12;)
                            local.get 9
                            i32.const 4
                            i32.add
                            i32.load
                            local.get 7
                            i32.store offset=12
                          end
                          block  ;; label = @12
                            local.get 4
                            local.get 2
                            i32.eq
                            br_if 0 (;@12;)
                            local.get 9
                            i32.load
                            local.get 7
                            i32.store offset=12
                          end
                          local.get 2
                          i32.const -4
                          i32.add
                          local.set 0
                          block  ;; label = @12
                            local.get 5
                            local.get 2
                            i32.eq
                            br_if 0 (;@12;)
                            local.get 9
                            i32.const -4
                            i32.add
                            i32.load
                            local.get 7
                            i32.store offset=12
                          end
                          local.get 9
                          i32.const -16
                          i32.add
                          local.set 9
                          local.get 0
                          local.set 2
                          local.get 0
                          i32.const 1
                          i32.add
                          i32.const 1
                          i32.gt_u
                          br_if 0 (;@11;)
                        end
                      end
                      block  ;; label = @10
                        local.get 10
                        local.get 3
                        i32.const 2
                        i32.shl
                        i32.add
                        i32.const 24
                        i32.add
                        i32.load
                        local.tee 2
                        i32.load offset=8
                        local.tee 0
                        i32.eqz
                        br_if 0 (;@10;)
                        local.get 0
                        i32.const 255
                        i32.store8 offset=12
                      end
                      local.get 2
                      local.get 10
                      i32.store offset=8
                      block  ;; label = @10
                        block  ;; label = @11
                          block  ;; label = @12
                            local.get 10
                            call $AddPropagate
                            i32.eqz
                            br_if 0 (;@12;)
                            local.get 2
                            i32.const 0
                            i32.load offset=3988
                            local.tee 9
                            i32.store offset=12
                            local.get 0
                            i32.eqz
                            br_if 4 (;@8;)
                            loop  ;; label = @13
                              block  ;; label = @14
                                local.get 0
                                i32.load8_u offset=13
                                local.tee 5
                                i32.const 24
                                i32.shl
                                i32.const 24
                                i32.shr_s
                                local.tee 2
                                i32.const 0
                                i32.gt_s
                                br_if 0 (;@14;)
                                local.get 0
                                i32.const 255
                                i32.store8 offset=12
                                local.get 0
                                i32.load offset=8
                                local.set 11
                                br 4 (;@10;)
                              end
                              local.get 5
                              i32.const 1
                              i32.and
                              local.set 14
                              local.get 0
                              i32.load offset=8
                              local.set 11
                              i32.const -1
                              local.set 10
                              block  ;; label = @14
                                block  ;; label = @15
                                  local.get 2
                                  i32.const 1
                                  i32.ne
                                  br_if 0 (;@15;)
                                  local.get 11
                                  local.set 7
                                  br 1 (;@14;)
                                end
                                local.get 0
                                local.get 5
                                i32.add
                                local.set 3
                                i32.const 0
                                local.set 2
                                i32.const 0
                                local.get 5
                                i32.const 254
                                i32.and
                                i32.sub
                                local.set 15
                                i32.const -1
                                local.set 10
                                local.get 11
                                local.set 7
                                loop  ;; label = @15
                                  local.get 5
                                  local.get 2
                                  i32.add
                                  local.set 8
                                  block  ;; label = @16
                                    local.get 0
                                    local.get 3
                                    local.get 2
                                    i32.add
                                    local.tee 6
                                    i32.const 14
                                    i32.add
                                    i32.load8_s
                                    i32.const 2
                                    i32.shl
                                    i32.add
                                    i32.const 24
                                    i32.add
                                    i32.load
                                    local.tee 4
                                    i32.load offset=12
                                    local.get 9
                                    i32.eq
                                    br_if 0 (;@16;)
                                    local.get 8
                                    i32.const -1
                                    i32.add
                                    local.get 10
                                    local.get 4
                                    i32.load offset=16
                                    local.tee 4
                                    local.get 7
                                    i32.gt_u
                                    local.tee 16
                                    select
                                    local.set 10
                                    local.get 4
                                    local.get 7
                                    local.get 16
                                    select
                                    local.set 7
                                  end
                                  block  ;; label = @16
                                    local.get 0
                                    local.get 6
                                    i32.const 13
                                    i32.add
                                    i32.load8_s
                                    i32.const 2
                                    i32.shl
                                    i32.add
                                    i32.const 24
                                    i32.add
                                    i32.load
                                    local.tee 6
                                    i32.load offset=12
                                    local.get 9
                                    i32.eq
                                    br_if 0 (;@16;)
                                    local.get 8
                                    i32.const -2
                                    i32.add
                                    local.get 10
                                    local.get 6
                                    i32.load offset=16
                                    local.tee 8
                                    local.get 7
                                    i32.gt_u
                                    local.tee 6
                                    select
                                    local.set 10
                                    local.get 8
                                    local.get 7
                                    local.get 6
                                    select
                                    local.set 7
                                  end
                                  local.get 15
                                  local.get 2
                                  i32.const -2
                                  i32.add
                                  local.tee 2
                                  i32.ne
                                  br_if 0 (;@15;)
                                end
                                local.get 5
                                local.get 2
                                i32.add
                                local.set 5
                              end
                              block  ;; label = @14
                                local.get 14
                                i32.eqz
                                br_if 0 (;@14;)
                                local.get 0
                                local.get 0
                                local.get 5
                                i32.const -1
                                i32.add
                                local.tee 2
                                i32.add
                                i32.const 15
                                i32.add
                                i32.load8_s
                                i32.const 2
                                i32.shl
                                i32.add
                                i32.const 24
                                i32.add
                                i32.load
                                local.tee 8
                                i32.load offset=12
                                local.get 9
                                i32.eq
                                br_if 0 (;@14;)
                                local.get 2
                                local.get 10
                                local.get 8
                                i32.load offset=16
                                local.get 7
                                i32.gt_u
                                select
                                local.set 10
                              end
                              local.get 0
                              local.get 10
                              i32.store8 offset=12
                              local.get 10
                              i32.const 24
                              i32.shl
                              local.tee 2
                              i32.const -16777216
                              i32.eq
                              br_if 3 (;@10;)
                              local.get 0
                              local.get 2
                              i32.const 24
                              i32.shr_s
                              i32.add
                              i32.const 15
                              i32.add
                              i32.load8_s
                              local.set 3
                              block  ;; label = @14
                                local.get 0
                                i32.load8_s offset=14
                                local.tee 2
                                i32.const 1
                                i32.lt_s
                                br_if 0 (;@14;)
                                local.get 2
                                i32.const 255
                                i32.and
                                local.tee 2
                                i32.const -1
                                i32.add
                                local.set 6
                                block  ;; label = @15
                                  local.get 2
                                  i32.const 3
                                  i32.and
                                  local.tee 10
                                  i32.eqz
                                  br_if 0 (;@15;)
                                  local.get 3
                                  i32.const 1
                                  i32.add
                                  local.set 8
                                  local.get 0
                                  local.get 2
                                  i32.const 2
                                  i32.shl
                                  i32.add
                                  i32.const 20
                                  i32.add
                                  local.set 7
                                  loop  ;; label = @16
                                    block  ;; label = @17
                                      local.get 8
                                      local.get 2
                                      i32.eq
                                      br_if 0 (;@17;)
                                      local.get 7
                                      i32.load
                                      local.get 9
                                      i32.store offset=12
                                    end
                                    local.get 2
                                    i32.const -1
                                    i32.add
                                    local.set 2
                                    local.get 7
                                    i32.const -4
                                    i32.add
                                    local.set 7
                                    local.get 10
                                    i32.const -1
                                    i32.add
                                    local.tee 10
                                    br_if 0 (;@16;)
                                  end
                                end
                                local.get 6
                                i32.const 3
                                i32.lt_u
                                br_if 0 (;@14;)
                                local.get 3
                                i32.const 1
                                i32.add
                                local.set 8
                                local.get 3
                                i32.const 2
                                i32.add
                                local.set 6
                                local.get 3
                                i32.const 3
                                i32.add
                                local.set 4
                                local.get 3
                                i32.const 4
                                i32.add
                                local.set 5
                                local.get 0
                                local.get 2
                                i32.const 2
                                i32.shl
                                i32.add
                                i32.const 12
                                i32.add
                                local.set 7
                                loop  ;; label = @15
                                  block  ;; label = @16
                                    local.get 8
                                    local.get 2
                                    i32.eq
                                    br_if 0 (;@16;)
                                    local.get 7
                                    i32.const 8
                                    i32.add
                                    i32.load
                                    local.get 9
                                    i32.store offset=12
                                  end
                                  block  ;; label = @16
                                    local.get 6
                                    local.get 2
                                    i32.eq
                                    br_if 0 (;@16;)
                                    local.get 7
                                    i32.const 4
                                    i32.add
                                    i32.load
                                    local.get 9
                                    i32.store offset=12
                                  end
                                  block  ;; label = @16
                                    local.get 4
                                    local.get 2
                                    i32.eq
                                    br_if 0 (;@16;)
                                    local.get 7
                                    i32.load
                                    local.get 9
                                    i32.store offset=12
                                  end
                                  local.get 2
                                  i32.const -4
                                  i32.add
                                  local.set 10
                                  block  ;; label = @16
                                    local.get 5
                                    local.get 2
                                    i32.eq
                                    br_if 0 (;@16;)
                                    local.get 7
                                    i32.const -4
                                    i32.add
                                    i32.load
                                    local.get 9
                                    i32.store offset=12
                                  end
                                  local.get 7
                                  i32.const -16
                                  i32.add
                                  local.set 7
                                  local.get 10
                                  local.set 2
                                  local.get 10
                                  i32.const 1
                                  i32.add
                                  i32.const 1
                                  i32.gt_u
                                  br_if 0 (;@15;)
                                end
                              end
                              block  ;; label = @14
                                local.get 0
                                local.get 3
                                i32.const 2
                                i32.shl
                                i32.add
                                i32.const 24
                                i32.add
                                i32.load
                                local.tee 7
                                i32.load offset=8
                                local.tee 2
                                i32.eqz
                                br_if 0 (;@14;)
                                local.get 2
                                i32.const 255
                                i32.store8 offset=12
                              end
                              local.get 7
                              local.get 0
                              i32.store offset=8
                              local.get 0
                              call $AddPropagate
                              i32.eqz
                              br_if 2 (;@11;)
                              local.get 7
                              i32.const 0
                              i32.load offset=3988
                              local.tee 9
                              i32.store offset=12
                              local.get 2
                              local.set 0
                              local.get 2
                              br_if 0 (;@13;)
                              br 5 (;@8;)
                            end
                          end
                          local.get 1
                          i32.const 1148
                          i32.store offset=64
                          i32.const 1408
                          local.get 1
                          i32.const 64
                          i32.add
                          call $printf
                          drop
                          i32.const -1
                          call $exit
                          unreachable
                        end
                        local.get 1
                        i32.const 1148
                        i32.store offset=96
                        i32.const 1408
                        local.get 1
                        i32.const 96
                        i32.add
                        call $printf
                        drop
                        i32.const -1
                        call $exit
                        unreachable
                      end
                      local.get 11
                      br_if 1 (;@8;)
                      local.get 1
                      i32.const 1067
                      i32.store offset=80
                      i32.const 1408
                      local.get 1
                      i32.const 80
                      i32.add
                      call $printf
                      drop
                      i32.const -1
                      call $exit
                      unreachable
                    end
                    local.get 11
                    br_if 0 (;@8;)
                    local.get 1
                    i32.const 1067
                    i32.store offset=48
                    i32.const 1408
                    local.get 1
                    i32.const 48
                    i32.add
                    call $printf
                    drop
                    i32.const -1
                    call $exit
                    unreachable
                  end
                  i32.const 0
                  i32.load offset=3996
                  local.set 11
                  local.get 13
                  i32.const 4
                  i32.add
                  local.tee 13
                  local.get 12
                  i32.le_u
                  br_if 0 (;@7;)
                end
                i32.const 0
                i32.load offset=4000
                local.set 2
              end
              i32.const 0
              local.get 11
              i32.const 1
              i32.add
              local.tee 11
              i32.store offset=3996
              local.get 11
              i32.const 6
              i32.gt_u
              br_if 4 (;@1;)
              local.get 2
              i32.load offset=12
              local.set 7
              local.get 2
              i32.load offset=8
              local.set 9
              br 0 (;@5;)
            end
          end
          local.get 1
          i32.const 1024
          i32.store offset=16
          i32.const 1408
          local.get 1
          i32.const 16
          i32.add
          call $printf
          drop
          i32.const -1
          call $exit
          unreachable
        end
        local.get 1
        i32.const 1024
        i32.store
        i32.const 1408
        local.get 1
        call $printf
        drop
        i32.const -1
        call $exit
        unreachable
      end
      i32.const 0
      i32.const 7
      i32.store offset=3996
    end
    block  ;; label = @1
      local.get 2
      i32.load
      local.tee 9
      i32.eqz
      br_if 0 (;@1;)
      local.get 9
      call $free
      local.get 2
      call $free
      local.get 1
      i32.const 112
      i32.add
      global.set $__stack_pointer
      return
    end
    local.get 1
    i32.const 1174
    i32.store offset=32
    i32.const 1408
    local.get 1
    i32.const 32
    i32.add
    call $printf
    drop
    i32.const -1
    call $exit
    unreachable)
  (func $Satisfy (type 4) (param i32) (result i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 32
    i32.sub
    local.tee 1
    global.set $__stack_pointer
    block  ;; label = @1
      block  ;; label = @2
        block  ;; label = @3
          local.get 0
          i32.load8_u offset=13
          local.tee 2
          i32.const 24
          i32.shl
          i32.const 24
          i32.shr_s
          local.tee 3
          i32.const 0
          i32.gt_s
          br_if 0 (;@3;)
          local.get 0
          i32.const 255
          i32.store8 offset=12
          local.get 0
          i32.load offset=8
          local.set 4
          br 1 (;@2;)
        end
        local.get 2
        i32.const 1
        i32.and
        local.set 5
        i32.const 0
        i32.load offset=3988
        local.set 6
        local.get 0
        i32.load offset=8
        local.set 4
        block  ;; label = @3
          block  ;; label = @4
            local.get 3
            i32.const 1
            i32.ne
            br_if 0 (;@4;)
            i32.const -1
            local.set 7
            local.get 4
            local.set 8
            br 1 (;@3;)
          end
          local.get 0
          local.get 2
          i32.add
          local.set 9
          i32.const 0
          local.set 3
          i32.const 0
          local.get 2
          i32.const 254
          i32.and
          i32.sub
          local.set 10
          i32.const -1
          local.set 7
          local.get 4
          local.set 8
          loop  ;; label = @4
            local.get 2
            local.get 3
            i32.add
            local.set 11
            block  ;; label = @5
              local.get 0
              local.get 9
              local.get 3
              i32.add
              local.tee 12
              i32.const 14
              i32.add
              i32.load8_s
              i32.const 2
              i32.shl
              i32.add
              i32.const 24
              i32.add
              i32.load
              local.tee 13
              i32.load offset=12
              local.get 6
              i32.eq
              br_if 0 (;@5;)
              local.get 11
              i32.const -1
              i32.add
              local.get 7
              local.get 13
              i32.load offset=16
              local.tee 13
              local.get 8
              i32.gt_u
              local.tee 14
              select
              local.set 7
              local.get 13
              local.get 8
              local.get 14
              select
              local.set 8
            end
            block  ;; label = @5
              local.get 0
              local.get 12
              i32.const 13
              i32.add
              i32.load8_s
              i32.const 2
              i32.shl
              i32.add
              i32.const 24
              i32.add
              i32.load
              local.tee 12
              i32.load offset=12
              local.get 6
              i32.eq
              br_if 0 (;@5;)
              local.get 11
              i32.const -2
              i32.add
              local.get 7
              local.get 12
              i32.load offset=16
              local.tee 11
              local.get 8
              i32.gt_u
              local.tee 12
              select
              local.set 7
              local.get 11
              local.get 8
              local.get 12
              select
              local.set 8
            end
            local.get 10
            local.get 3
            i32.const -2
            i32.add
            local.tee 3
            i32.ne
            br_if 0 (;@4;)
          end
          local.get 2
          local.get 3
          i32.add
          local.set 2
        end
        block  ;; label = @3
          local.get 5
          i32.eqz
          br_if 0 (;@3;)
          local.get 0
          local.get 0
          local.get 2
          i32.const -1
          i32.add
          local.tee 3
          i32.add
          i32.const 15
          i32.add
          i32.load8_s
          i32.const 2
          i32.shl
          i32.add
          i32.const 24
          i32.add
          i32.load
          local.tee 11
          i32.load offset=12
          local.get 6
          i32.eq
          br_if 0 (;@3;)
          local.get 3
          local.get 7
          local.get 11
          i32.load offset=16
          local.get 8
          i32.gt_u
          select
          local.set 7
        end
        local.get 0
        local.get 7
        i32.store8 offset=12
        local.get 7
        i32.const 24
        i32.shl
        local.tee 3
        i32.const -16777216
        i32.eq
        br_if 0 (;@2;)
        local.get 0
        local.get 3
        i32.const 24
        i32.shr_s
        i32.add
        i32.const 15
        i32.add
        i32.load8_s
        local.set 9
        block  ;; label = @3
          local.get 0
          i32.load8_s offset=14
          local.tee 3
          i32.const 1
          i32.lt_s
          br_if 0 (;@3;)
          local.get 3
          i32.const 255
          i32.and
          local.tee 3
          i32.const -1
          i32.add
          local.set 12
          block  ;; label = @4
            local.get 3
            i32.const 3
            i32.and
            local.tee 7
            i32.eqz
            br_if 0 (;@4;)
            local.get 9
            i32.const 1
            i32.add
            local.set 11
            local.get 3
            i32.const 2
            i32.shl
            local.get 0
            i32.add
            i32.const 20
            i32.add
            local.set 8
            loop  ;; label = @5
              block  ;; label = @6
                local.get 11
                local.get 3
                i32.eq
                br_if 0 (;@6;)
                local.get 8
                i32.load
                local.get 6
                i32.store offset=12
              end
              local.get 3
              i32.const -1
              i32.add
              local.set 3
              local.get 8
              i32.const -4
              i32.add
              local.set 8
              local.get 7
              i32.const -1
              i32.add
              local.tee 7
              br_if 0 (;@5;)
            end
          end
          local.get 12
          i32.const 3
          i32.lt_u
          br_if 0 (;@3;)
          local.get 9
          i32.const 1
          i32.add
          local.set 11
          local.get 9
          i32.const 2
          i32.add
          local.set 12
          local.get 9
          i32.const 3
          i32.add
          local.set 13
          local.get 9
          i32.const 4
          i32.add
          local.set 2
          local.get 3
          i32.const 2
          i32.shl
          local.get 0
          i32.add
          i32.const 12
          i32.add
          local.set 8
          loop  ;; label = @4
            block  ;; label = @5
              local.get 11
              local.get 3
              i32.eq
              br_if 0 (;@5;)
              local.get 8
              i32.const 8
              i32.add
              i32.load
              local.get 6
              i32.store offset=12
            end
            block  ;; label = @5
              local.get 12
              local.get 3
              i32.eq
              br_if 0 (;@5;)
              local.get 8
              i32.const 4
              i32.add
              i32.load
              local.get 6
              i32.store offset=12
            end
            block  ;; label = @5
              local.get 13
              local.get 3
              i32.eq
              br_if 0 (;@5;)
              local.get 8
              i32.load
              local.get 6
              i32.store offset=12
            end
            local.get 3
            i32.const -4
            i32.add
            local.set 7
            block  ;; label = @5
              local.get 2
              local.get 3
              i32.eq
              br_if 0 (;@5;)
              local.get 8
              i32.const -4
              i32.add
              i32.load
              local.get 6
              i32.store offset=12
            end
            local.get 8
            i32.const -16
            i32.add
            local.set 8
            local.get 7
            local.set 3
            local.get 7
            i32.const 1
            i32.add
            i32.const 1
            i32.gt_u
            br_if 0 (;@4;)
          end
        end
        block  ;; label = @3
          local.get 0
          local.get 9
          i32.const 2
          i32.shl
          i32.add
          i32.const 24
          i32.add
          i32.load
          local.tee 8
          i32.load offset=8
          local.tee 3
          i32.eqz
          br_if 0 (;@3;)
          local.get 3
          i32.const 255
          i32.store8 offset=12
        end
        local.get 8
        local.get 0
        i32.store offset=8
        block  ;; label = @3
          local.get 0
          call $AddPropagate
          i32.eqz
          br_if 0 (;@3;)
          local.get 8
          i32.const 0
          i32.load offset=3988
          i32.store offset=12
          br 2 (;@1;)
        end
        local.get 1
        i32.const 1148
        i32.store offset=16
        i32.const 1408
        local.get 1
        i32.const 16
        i32.add
        call $printf
        drop
        i32.const -1
        call $exit
        unreachable
      end
      i32.const 0
      local.set 3
      local.get 4
      br_if 0 (;@1;)
      local.get 1
      i32.const 1067
      i32.store
      i32.const 1408
      local.get 1
      call $printf
      drop
      i32.const -1
      call $exit
      unreachable
    end
    local.get 1
    i32.const 32
    i32.add
    global.set $__stack_pointer
    local.get 3)
  (func $RemovePropagateFrom (type 0) (param i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 80
    i32.sub
    local.tee 1
    global.set $__stack_pointer
    local.get 0
    i64.const 4294967302
    i64.store offset=16 align=4
    local.get 0
    i32.const 0
    i32.store offset=8
    block  ;; label = @1
      i32.const 16
      call $malloc
      local.tee 2
      i32.eqz
      br_if 0 (;@1;)
      local.get 2
      i32.const 32
      call $malloc
      local.tee 3
      i32.store
      block  ;; label = @2
        local.get 3
        i32.eqz
        br_if 0 (;@2;)
        local.get 2
        i32.const -1
        i32.store offset=12
        local.get 2
        i64.const 8
        i64.store offset=4 align=4
        i32.const 8
        local.set 4
        i32.const -1
        local.set 5
        local.get 3
        local.set 6
        loop  ;; label = @3
          local.get 0
          i32.load offset=4
          local.tee 7
          i32.load
          local.set 8
          block  ;; label = @4
            block  ;; label = @5
              block  ;; label = @6
                block  ;; label = @7
                  block  ;; label = @8
                    block  ;; label = @9
                      local.get 7
                      i32.load offset=8
                      local.tee 9
                      local.get 7
                      i32.load offset=12
                      local.tee 7
                      i32.gt_s
                      br_if 0 (;@9;)
                      local.get 8
                      local.get 7
                      i32.const 2
                      i32.shl
                      i32.add
                      local.set 10
                      local.get 8
                      local.get 9
                      i32.const 2
                      i32.shl
                      i32.add
                      local.set 7
                      loop  ;; label = @10
                        block  ;; label = @11
                          local.get 7
                          i32.load
                          local.tee 11
                          i32.load8_u offset=12
                          i32.const 255
                          i32.ne
                          br_if 0 (;@11;)
                          block  ;; label = @12
                            i32.const 0
                            i32.load offset=4000
                            local.tee 12
                            i32.load offset=12
                            local.tee 9
                            local.get 12
                            i32.load offset=4
                            local.tee 13
                            i32.const -1
                            i32.add
                            i32.lt_s
                            br_if 0 (;@12;)
                            local.get 12
                            i32.load
                            local.set 8
                            local.get 12
                            i32.load offset=8
                            local.tee 14
                            local.set 15
                            block  ;; label = @13
                              local.get 9
                              local.get 14
                              i32.sub
                              i32.const 1
                              i32.add
                              local.get 13
                              i32.lt_s
                              br_if 0 (;@13;)
                              local.get 12
                              local.get 13
                              i32.const 2
                              local.get 13
                              i32.const 2
                              i32.gt_s
                              select
                              local.tee 15
                              i32.const 512
                              local.get 15
                              i32.const 512
                              i32.lt_u
                              select
                              local.get 13
                              i32.add
                              local.tee 13
                              i32.store offset=4
                              local.get 12
                              local.get 8
                              local.get 13
                              i32.const 2
                              i32.shl
                              call $realloc
                              local.tee 13
                              i32.store
                              local.get 13
                              i32.eqz
                              br_if 5 (;@8;)
                              local.get 12
                              i32.load offset=8
                              local.set 15
                            end
                            block  ;; label = @13
                              local.get 15
                              br_if 0 (;@13;)
                              local.get 12
                              i32.load offset=12
                              local.set 9
                              br 1 (;@12;)
                            end
                            block  ;; label = @13
                              local.get 14
                              local.get 9
                              i32.gt_s
                              br_if 0 (;@13;)
                              local.get 8
                              local.get 9
                              i32.const 2
                              i32.shl
                              i32.add
                              local.set 13
                              local.get 14
                              i32.const 2
                              i32.shl
                              local.set 9
                              loop  ;; label = @14
                                local.get 8
                                local.get 8
                                local.get 9
                                i32.add
                                i32.load
                                i32.store
                                local.get 8
                                i32.const 4
                                i32.add
                                local.tee 8
                                local.get 9
                                i32.add
                                local.get 13
                                i32.le_u
                                br_if 0 (;@14;)
                              end
                            end
                            local.get 12
                            i32.const 0
                            i32.store offset=8
                            local.get 12
                            i32.load offset=12
                            local.get 15
                            i32.sub
                            local.set 9
                          end
                          local.get 12
                          local.get 9
                          i32.const 1
                          i32.add
                          local.tee 8
                          i32.store offset=12
                          local.get 12
                          i32.load
                          local.get 8
                          i32.const 2
                          i32.shl
                          i32.add
                          local.get 11
                          i32.store
                        end
                        local.get 7
                        i32.const 4
                        i32.add
                        local.tee 7
                        local.get 10
                        i32.le_u
                        br_if 0 (;@10;)
                      end
                      local.get 0
                      i32.load offset=4
                      local.tee 8
                      i32.load offset=12
                      local.set 7
                      local.get 8
                      i32.load offset=8
                      local.set 9
                      local.get 8
                      i32.load
                      local.set 8
                    end
                    block  ;; label = @9
                      block  ;; label = @10
                        block  ;; label = @11
                          block  ;; label = @12
                            local.get 9
                            local.get 7
                            i32.gt_s
                            br_if 0 (;@12;)
                            local.get 8
                            local.get 7
                            i32.const 2
                            i32.shl
                            i32.add
                            local.set 10
                            local.get 0
                            i32.load offset=8
                            local.set 11
                            local.get 8
                            local.get 9
                            i32.const 2
                            i32.shl
                            i32.add
                            local.set 8
                            i32.const 0
                            local.set 7
                            loop  ;; label = @13
                              block  ;; label = @14
                                local.get 8
                                i32.load
                                local.tee 12
                                local.get 11
                                i32.eq
                                br_if 0 (;@14;)
                                local.get 12
                                i32.load8_u offset=12
                                i32.const 255
                                i32.eq
                                br_if 0 (;@14;)
                                block  ;; label = @15
                                  local.get 7
                                  br_if 0 (;@15;)
                                  local.get 12
                                  local.set 7
                                  br 1 (;@14;)
                                end
                                block  ;; label = @15
                                  block  ;; label = @16
                                    local.get 5
                                    local.get 4
                                    i32.const -1
                                    i32.add
                                    i32.ge_s
                                    br_if 0 (;@16;)
                                    local.get 5
                                    local.set 15
                                    br 1 (;@15;)
                                  end
                                  block  ;; label = @16
                                    local.get 5
                                    local.get 2
                                    i32.load offset=8
                                    local.tee 9
                                    i32.sub
                                    local.tee 15
                                    i32.const 1
                                    i32.add
                                    local.get 4
                                    i32.lt_s
                                    br_if 0 (;@16;)
                                    local.get 2
                                    local.get 4
                                    i32.const 2
                                    local.get 4
                                    i32.const 2
                                    i32.gt_s
                                    select
                                    local.tee 13
                                    i32.const 512
                                    local.get 13
                                    i32.const 512
                                    i32.lt_u
                                    select
                                    local.get 4
                                    i32.add
                                    local.tee 4
                                    i32.store offset=4
                                    local.get 2
                                    local.get 6
                                    local.get 4
                                    i32.const 2
                                    i32.shl
                                    call $realloc
                                    local.tee 3
                                    i32.store
                                    local.get 3
                                    i32.eqz
                                    br_if 9 (;@7;)
                                  end
                                  block  ;; label = @16
                                    local.get 9
                                    br_if 0 (;@16;)
                                    local.get 5
                                    local.set 15
                                    br 1 (;@15;)
                                  end
                                  block  ;; label = @16
                                    local.get 5
                                    local.get 9
                                    i32.lt_s
                                    br_if 0 (;@16;)
                                    local.get 6
                                    local.get 5
                                    i32.const 2
                                    i32.shl
                                    i32.add
                                    local.set 13
                                    local.get 9
                                    i32.const 2
                                    i32.shl
                                    local.set 9
                                    loop  ;; label = @17
                                      local.get 6
                                      local.get 6
                                      local.get 9
                                      i32.add
                                      i32.load
                                      i32.store
                                      local.get 6
                                      i32.const 4
                                      i32.add
                                      local.tee 6
                                      local.get 9
                                      i32.add
                                      local.get 13
                                      i32.le_u
                                      br_if 0 (;@17;)
                                    end
                                  end
                                  local.get 2
                                  i32.const 0
                                  i32.store offset=8
                                  local.get 2
                                  i32.load
                                  local.set 3
                                end
                                local.get 2
                                local.get 15
                                i32.const 1
                                i32.add
                                local.tee 5
                                i32.store offset=12
                                local.get 3
                                local.get 5
                                i32.const 2
                                i32.shl
                                i32.add
                                local.get 12
                                i32.store
                                local.get 3
                                local.set 6
                              end
                              local.get 8
                              i32.const 4
                              i32.add
                              local.tee 8
                              local.get 10
                              i32.le_u
                              br_if 0 (;@13;)
                            end
                            local.get 7
                            br_if 1 (;@11;)
                          end
                          local.get 2
                          i32.load
                          local.set 3
                          local.get 5
                          local.get 2
                          i32.load offset=8
                          local.tee 8
                          i32.lt_s
                          br_if 1 (;@10;)
                          local.get 2
                          local.get 8
                          i32.const 1
                          i32.add
                          i32.store offset=8
                          local.get 3
                          local.get 8
                          i32.const 2
                          i32.shl
                          i32.add
                          i32.load
                          local.tee 7
                          i32.eqz
                          br_if 2 (;@9;)
                          local.get 3
                          local.set 6
                        end
                        local.get 7
                        local.get 7
                        local.get 7
                        i32.load8_s offset=12
                        local.tee 14
                        i32.add
                        i32.const 15
                        i32.add
                        local.tee 0
                        i32.load8_s
                        local.tee 10
                        i32.const 2
                        i32.shl
                        i32.add
                        i32.const 24
                        i32.add
                        i32.load
                        local.set 15
                        local.get 7
                        i32.load offset=8
                        local.set 9
                        block  ;; label = @11
                          local.get 7
                          i32.load8_u offset=13
                          local.tee 8
                          i32.const 24
                          i32.shl
                          i32.const 24
                          i32.shr_s
                          local.tee 12
                          i32.const 1
                          i32.lt_s
                          br_if 0 (;@11;)
                          block  ;; label = @12
                            local.get 8
                            i32.const 1
                            i32.and
                            i32.eqz
                            br_if 0 (;@12;)
                            local.get 7
                            local.get 8
                            i32.const -1
                            i32.add
                            local.tee 8
                            i32.add
                            i32.const 15
                            i32.add
                            i32.load8_u
                            local.tee 13
                            local.get 10
                            i32.const 255
                            i32.and
                            i32.eq
                            br_if 0 (;@12;)
                            local.get 7
                            local.get 13
                            i32.const 24
                            i32.shl
                            i32.const 24
                            i32.shr_s
                            i32.const 2
                            i32.shl
                            i32.add
                            i32.const 24
                            i32.add
                            i32.load
                            i32.load offset=16
                            local.tee 13
                            local.get 9
                            local.get 13
                            local.get 9
                            i32.gt_u
                            select
                            local.set 9
                          end
                          local.get 12
                          i32.const 1
                          i32.eq
                          br_if 0 (;@11;)
                          loop  ;; label = @12
                            block  ;; label = @13
                              local.get 7
                              local.get 8
                              i32.add
                              local.tee 12
                              i32.const 14
                              i32.add
                              i32.load8_u
                              local.tee 11
                              local.get 10
                              i32.const 255
                              i32.and
                              local.tee 13
                              i32.eq
                              br_if 0 (;@13;)
                              local.get 7
                              local.get 11
                              i32.const 24
                              i32.shl
                              i32.const 24
                              i32.shr_s
                              i32.const 2
                              i32.shl
                              i32.add
                              i32.const 24
                              i32.add
                              i32.load
                              i32.load offset=16
                              local.tee 11
                              local.get 9
                              local.get 11
                              local.get 9
                              i32.gt_u
                              select
                              local.set 9
                            end
                            local.get 8
                            i32.const -2
                            i32.add
                            local.set 8
                            block  ;; label = @13
                              local.get 12
                              i32.const 13
                              i32.add
                              i32.load8_u
                              local.tee 12
                              local.get 13
                              i32.eq
                              br_if 0 (;@13;)
                              local.get 7
                              local.get 12
                              i32.const 24
                              i32.shl
                              i32.const 24
                              i32.shr_s
                              i32.const 2
                              i32.shl
                              i32.add
                              i32.const 24
                              i32.add
                              i32.load
                              i32.load offset=16
                              local.tee 12
                              local.get 9
                              local.get 12
                              local.get 9
                              i32.gt_u
                              select
                              local.set 9
                            end
                            local.get 8
                            i32.const 1
                            i32.add
                            i32.const 1
                            i32.gt_u
                            br_if 0 (;@12;)
                          end
                        end
                        local.get 15
                        local.get 9
                        i32.store offset=16
                        local.get 7
                        i32.load offset=4
                        br_if 4 (;@6;)
                        block  ;; label = @11
                          local.get 7
                          i32.load8_s offset=14
                          local.tee 8
                          i32.const 1
                          i32.lt_s
                          br_if 0 (;@11;)
                          local.get 0
                          i32.load8_s
                          i32.const 1
                          i32.add
                          local.set 12
                          local.get 7
                          local.get 8
                          i32.const 255
                          i32.and
                          local.tee 8
                          i32.const 2
                          i32.shl
                          i32.add
                          i32.const 20
                          i32.add
                          local.set 9
                          loop  ;; label = @12
                            block  ;; label = @13
                              local.get 12
                              local.get 8
                              i32.eq
                              br_if 0 (;@13;)
                              local.get 9
                              i32.load
                              i32.load offset=20
                              i32.eqz
                              br_if 7 (;@6;)
                            end
                            local.get 9
                            i32.const -4
                            i32.add
                            local.set 9
                            local.get 8
                            i32.const -1
                            i32.add
                            local.tee 8
                            i32.const 1
                            i32.add
                            i32.const 1
                            i32.gt_u
                            br_if 0 (;@12;)
                          end
                        end
                        local.get 15
                        i32.const 1
                        i32.store offset=20
                        local.get 7
                        local.get 7
                        i32.load
                        call_indirect (type 0)
                        local.get 7
                        i32.load8_s offset=12
                        local.set 14
                        br 6 (;@4;)
                      end
                      local.get 3
                      i32.eqz
                      br_if 4 (;@5;)
                    end
                    local.get 3
                    call $free
                    local.get 2
                    call $free
                    local.get 1
                    i32.const 80
                    i32.add
                    global.set $__stack_pointer
                    return
                  end
                  local.get 1
                  i32.const 1024
                  i32.store offset=64
                  i32.const 1408
                  local.get 1
                  i32.const 64
                  i32.add
                  call $printf
                  drop
                  i32.const -1
                  call $exit
                  unreachable
                end
                local.get 1
                i32.const 1024
                i32.store offset=48
                i32.const 1408
                local.get 1
                i32.const 48
                i32.add
                call $printf
                drop
                i32.const -1
                call $exit
                unreachable
              end
              local.get 15
              i32.const 0
              i32.store offset=20
              br 1 (;@4;)
            end
            local.get 1
            i32.const 1174
            i32.store offset=32
            i32.const 1408
            local.get 1
            i32.const 32
            i32.add
            call $printf
            drop
            i32.const -1
            call $exit
            unreachable
          end
          local.get 7
          local.get 7
          local.get 14
          i32.add
          i32.const 15
          i32.add
          i32.load8_s
          i32.const 2
          i32.shl
          i32.add
          i32.const 24
          i32.add
          i32.load
          local.set 0
          br 0 (;@3;)
        end
      end
      local.get 1
      i32.const 1024
      i32.store offset=16
      i32.const 1408
      local.get 1
      i32.const 16
      i32.add
      call $printf
      drop
      i32.const -1
      call $exit
      unreachable
    end
    local.get 1
    i32.const 1024
    i32.store
    i32.const 1408
    local.get 1
    call $printf
    drop
    i32.const -1
    call $exit
    unreachable)
  (func $AddPropagate (type 4) (param i32) (result i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 48
    i32.sub
    local.tee 1
    global.set $__stack_pointer
    block  ;; label = @1
      i32.const 16
      call $malloc
      local.tee 2
      i32.eqz
      br_if 0 (;@1;)
      local.get 2
      i32.const 32
      call $malloc
      local.tee 3
      i32.store
      block  ;; label = @2
        local.get 3
        i32.eqz
        br_if 0 (;@2;)
        local.get 2
        i32.const -1
        i32.store offset=12
        local.get 2
        i64.const 8
        i64.store offset=4 align=4
        block  ;; label = @3
          block  ;; label = @4
            block  ;; label = @5
              local.get 0
              i32.eqz
              br_if 0 (;@5;)
              local.get 0
              local.set 4
              loop  ;; label = @6
                block  ;; label = @7
                  local.get 4
                  local.get 4
                  local.get 4
                  i32.load8_s offset=12
                  i32.add
                  i32.const 15
                  i32.add
                  local.tee 5
                  i32.load8_s
                  local.tee 6
                  i32.const 2
                  i32.shl
                  i32.add
                  i32.const 24
                  i32.add
                  i32.load
                  local.tee 7
                  i32.load offset=12
                  i32.const 0
                  i32.load offset=3988
                  i32.ne
                  br_if 0 (;@7;)
                  local.get 0
                  call $IncrementalRemove
                  i32.const 0
                  local.set 3
                  br 3 (;@4;)
                end
                local.get 4
                i32.load offset=8
                local.set 8
                block  ;; label = @7
                  local.get 4
                  i32.load8_u offset=13
                  local.tee 3
                  i32.const 24
                  i32.shl
                  i32.const 24
                  i32.shr_s
                  local.tee 9
                  i32.const 1
                  i32.lt_s
                  br_if 0 (;@7;)
                  block  ;; label = @8
                    local.get 3
                    i32.const 1
                    i32.and
                    i32.eqz
                    br_if 0 (;@8;)
                    local.get 4
                    local.get 3
                    i32.const -1
                    i32.add
                    local.tee 3
                    i32.add
                    i32.const 15
                    i32.add
                    i32.load8_u
                    local.tee 10
                    local.get 6
                    i32.const 255
                    i32.and
                    i32.eq
                    br_if 0 (;@8;)
                    local.get 4
                    local.get 10
                    i32.const 24
                    i32.shl
                    i32.const 24
                    i32.shr_s
                    i32.const 2
                    i32.shl
                    i32.add
                    i32.const 24
                    i32.add
                    i32.load
                    i32.load offset=16
                    local.tee 10
                    local.get 8
                    local.get 10
                    local.get 8
                    i32.gt_u
                    select
                    local.set 8
                  end
                  local.get 9
                  i32.const 1
                  i32.eq
                  br_if 0 (;@7;)
                  loop  ;; label = @8
                    block  ;; label = @9
                      local.get 4
                      local.get 3
                      i32.add
                      local.tee 9
                      i32.const 14
                      i32.add
                      i32.load8_u
                      local.tee 11
                      local.get 6
                      i32.const 255
                      i32.and
                      local.tee 10
                      i32.eq
                      br_if 0 (;@9;)
                      local.get 4
                      local.get 11
                      i32.const 24
                      i32.shl
                      i32.const 24
                      i32.shr_s
                      i32.const 2
                      i32.shl
                      i32.add
                      i32.const 24
                      i32.add
                      i32.load
                      i32.load offset=16
                      local.tee 11
                      local.get 8
                      local.get 11
                      local.get 8
                      i32.gt_u
                      select
                      local.set 8
                    end
                    local.get 3
                    i32.const -2
                    i32.add
                    local.set 3
                    block  ;; label = @9
                      local.get 9
                      i32.const 13
                      i32.add
                      i32.load8_u
                      local.tee 9
                      local.get 10
                      i32.eq
                      br_if 0 (;@9;)
                      local.get 4
                      local.get 9
                      i32.const 24
                      i32.shl
                      i32.const 24
                      i32.shr_s
                      i32.const 2
                      i32.shl
                      i32.add
                      i32.const 24
                      i32.add
                      i32.load
                      i32.load offset=16
                      local.tee 9
                      local.get 8
                      local.get 9
                      local.get 8
                      i32.gt_u
                      select
                      local.set 8
                    end
                    local.get 3
                    i32.const 1
                    i32.add
                    i32.const 1
                    i32.gt_u
                    br_if 0 (;@8;)
                  end
                end
                local.get 7
                local.get 8
                i32.store offset=16
                block  ;; label = @7
                  block  ;; label = @8
                    local.get 4
                    i32.load offset=4
                    br_if 0 (;@8;)
                    block  ;; label = @9
                      local.get 4
                      i32.load8_s offset=14
                      local.tee 3
                      i32.const 1
                      i32.lt_s
                      br_if 0 (;@9;)
                      local.get 5
                      i32.load8_s
                      i32.const 1
                      i32.add
                      local.set 9
                      local.get 4
                      local.get 3
                      i32.const 255
                      i32.and
                      local.tee 3
                      i32.const 2
                      i32.shl
                      i32.add
                      i32.const 20
                      i32.add
                      local.set 8
                      loop  ;; label = @10
                        block  ;; label = @11
                          local.get 9
                          local.get 3
                          i32.eq
                          br_if 0 (;@11;)
                          local.get 8
                          i32.load
                          i32.load offset=20
                          i32.eqz
                          br_if 3 (;@8;)
                        end
                        local.get 8
                        i32.const -4
                        i32.add
                        local.set 8
                        local.get 3
                        i32.const -1
                        i32.add
                        local.tee 3
                        i32.const 1
                        i32.add
                        i32.const 1
                        i32.gt_u
                        br_if 0 (;@10;)
                      end
                    end
                    local.get 7
                    i32.const 1
                    i32.store offset=20
                    local.get 4
                    local.get 4
                    i32.load
                    call_indirect (type 0)
                    br 1 (;@7;)
                  end
                  local.get 7
                  i32.const 0
                  i32.store offset=20
                end
                block  ;; label = @7
                  block  ;; label = @8
                    block  ;; label = @9
                      local.get 7
                      i32.load offset=4
                      local.tee 3
                      i32.load offset=8
                      local.tee 4
                      local.get 3
                      i32.load offset=12
                      local.tee 8
                      i32.gt_s
                      br_if 0 (;@9;)
                      local.get 3
                      i32.load
                      local.tee 3
                      local.get 8
                      i32.const 2
                      i32.shl
                      i32.add
                      local.set 9
                      local.get 7
                      i32.load offset=8
                      local.set 10
                      local.get 3
                      local.get 4
                      i32.const 2
                      i32.shl
                      i32.add
                      local.set 3
                      i32.const 0
                      local.set 4
                      loop  ;; label = @10
                        block  ;; label = @11
                          local.get 3
                          i32.load
                          local.tee 8
                          local.get 10
                          i32.eq
                          br_if 0 (;@11;)
                          local.get 8
                          i32.load8_u offset=12
                          i32.const 255
                          i32.eq
                          br_if 0 (;@11;)
                          block  ;; label = @12
                            local.get 4
                            br_if 0 (;@12;)
                            local.get 8
                            local.set 4
                            br 1 (;@11;)
                          end
                          local.get 2
                          local.get 8
                          call $List_Add
                        end
                        local.get 3
                        i32.const 4
                        i32.add
                        local.tee 3
                        local.get 9
                        i32.le_u
                        br_if 0 (;@10;)
                      end
                      local.get 4
                      br_if 1 (;@8;)
                    end
                    local.get 2
                    i32.load offset=12
                    local.get 2
                    i32.load offset=8
                    local.tee 3
                    i32.lt_s
                    br_if 1 (;@7;)
                    local.get 2
                    local.get 3
                    i32.const 1
                    i32.add
                    i32.store offset=8
                    local.get 2
                    i32.load
                    local.get 3
                    i32.const 2
                    i32.shl
                    i32.add
                    i32.load
                    local.set 4
                  end
                  local.get 4
                  br_if 1 (;@6;)
                end
              end
              local.get 2
              i32.load
              local.set 3
            end
            local.get 3
            i32.eqz
            br_if 1 (;@3;)
            local.get 3
            call $free
            local.get 2
            call $free
            i32.const 1
            local.set 3
          end
          local.get 1
          i32.const 48
          i32.add
          global.set $__stack_pointer
          local.get 3
          return
        end
        local.get 1
        i32.const 1174
        i32.store offset=32
        i32.const 1408
        local.get 1
        i32.const 32
        i32.add
        call $printf
        drop
        i32.const -1
        call $exit
        unreachable
      end
      local.get 1
      i32.const 1024
      i32.store offset=16
      i32.const 1408
      local.get 1
      i32.const 16
      i32.add
      call $printf
      drop
      i32.const -1
      call $exit
      unreachable
    end
    local.get 1
    i32.const 1024
    i32.store
    i32.const 1408
    local.get 1
    call $printf
    drop
    i32.const -1
    call $exit
    unreachable)
  (func $MakePlan (type 9) (result i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 32
    i32.sub
    local.tee 0
    global.set $__stack_pointer
    i32.const 0
    i32.const 0
    i32.load offset=3988
    i32.const 1
    i32.add
    local.tee 1
    i32.store offset=3988
    block  ;; label = @1
      block  ;; label = @2
        block  ;; label = @3
          i32.const 16
          call $malloc
          local.tee 2
          i32.eqz
          br_if 0 (;@3;)
          local.get 2
          i32.const 512
          call $malloc
          local.tee 3
          i32.store
          local.get 3
          i32.eqz
          br_if 1 (;@2;)
          local.get 2
          i32.const -1
          i32.store offset=12
          local.get 2
          i64.const 128
          i64.store offset=4 align=4
          i32.const 0
          i32.load offset=3992
          local.tee 3
          i32.load offset=12
          local.get 3
          i32.load offset=8
          local.tee 4
          i32.lt_s
          br_if 2 (;@1;)
          local.get 3
          local.get 4
          i32.const 1
          i32.add
          i32.store offset=8
          local.get 3
          i32.load
          local.get 4
          i32.const 2
          i32.shl
          i32.add
          i32.load
          local.tee 5
          i32.eqz
          br_if 2 (;@1;)
          loop  ;; label = @4
            block  ;; label = @5
              block  ;; label = @6
                block  ;; label = @7
                  local.get 5
                  local.get 5
                  local.get 5
                  i32.load8_s offset=12
                  i32.add
                  i32.const 15
                  i32.add
                  i32.load8_s
                  local.tee 3
                  i32.const 2
                  i32.shl
                  i32.add
                  i32.const 24
                  i32.add
                  i32.load
                  local.tee 6
                  i32.load offset=12
                  local.get 1
                  i32.eq
                  br_if 0 (;@7;)
                  block  ;; label = @8
                    local.get 5
                    i32.load8_s offset=14
                    local.tee 4
                    i32.const 1
                    i32.lt_s
                    br_if 0 (;@8;)
                    local.get 3
                    i32.const 1
                    i32.add
                    local.set 7
                    local.get 5
                    local.get 4
                    i32.const 255
                    i32.and
                    local.tee 3
                    i32.const 2
                    i32.shl
                    i32.add
                    i32.const 20
                    i32.add
                    local.set 4
                    loop  ;; label = @9
                      block  ;; label = @10
                        local.get 7
                        local.get 3
                        i32.eq
                        br_if 0 (;@10;)
                        local.get 4
                        i32.load
                        local.tee 8
                        i32.load offset=12
                        local.get 1
                        i32.eq
                        br_if 0 (;@10;)
                        local.get 8
                        i32.load offset=20
                        br_if 0 (;@10;)
                        local.get 8
                        i32.load offset=8
                        br_if 3 (;@7;)
                      end
                      local.get 4
                      i32.const -4
                      i32.add
                      local.set 4
                      local.get 3
                      i32.const -1
                      i32.add
                      local.tee 3
                      i32.const 1
                      i32.add
                      i32.const 1
                      i32.gt_u
                      br_if 0 (;@9;)
                    end
                  end
                  local.get 2
                  local.get 5
                  call $List_Add
                  local.get 6
                  i32.const 0
                  i32.load offset=3988
                  i32.store offset=12
                  i32.const 0
                  i32.load offset=3992
                  local.set 1
                  block  ;; label = @8
                    local.get 6
                    i32.load offset=4
                    local.tee 3
                    i32.load offset=8
                    local.tee 4
                    local.get 3
                    i32.load offset=12
                    local.tee 5
                    i32.gt_s
                    br_if 0 (;@8;)
                    local.get 3
                    i32.load
                    local.tee 3
                    local.get 5
                    i32.const 2
                    i32.shl
                    i32.add
                    local.set 7
                    local.get 6
                    i32.load offset=8
                    local.set 8
                    local.get 3
                    local.get 4
                    i32.const 2
                    i32.shl
                    i32.add
                    local.set 3
                    i32.const 0
                    local.set 5
                    loop  ;; label = @9
                      block  ;; label = @10
                        local.get 3
                        i32.load
                        local.tee 4
                        local.get 8
                        i32.eq
                        br_if 0 (;@10;)
                        local.get 4
                        i32.load8_u offset=12
                        i32.const 255
                        i32.eq
                        br_if 0 (;@10;)
                        block  ;; label = @11
                          local.get 5
                          br_if 0 (;@11;)
                          local.get 4
                          local.set 5
                          br 1 (;@10;)
                        end
                        local.get 1
                        local.get 4
                        call $List_Add
                      end
                      local.get 3
                      i32.const 4
                      i32.add
                      local.tee 3
                      local.get 7
                      i32.le_u
                      br_if 0 (;@9;)
                    end
                    local.get 5
                    br_if 3 (;@5;)
                  end
                  local.get 1
                  i32.load offset=12
                  local.get 1
                  i32.load offset=8
                  local.tee 3
                  i32.ge_s
                  br_if 1 (;@6;)
                  br 6 (;@1;)
                end
                i32.const 0
                i32.load offset=3992
                local.tee 1
                i32.load offset=12
                local.get 1
                i32.load offset=8
                local.tee 3
                i32.lt_s
                br_if 5 (;@1;)
              end
              local.get 1
              local.get 3
              i32.const 1
              i32.add
              i32.store offset=8
              local.get 1
              i32.load
              local.get 3
              i32.const 2
              i32.shl
              i32.add
              i32.load
              local.tee 5
              i32.eqz
              br_if 4 (;@1;)
            end
            i32.const 0
            i32.load offset=3988
            local.set 1
            br 0 (;@4;)
          end
        end
        local.get 0
        i32.const 1024
        i32.store
        i32.const 1408
        local.get 0
        call $printf
        drop
        i32.const -1
        call $exit
        unreachable
      end
      local.get 0
      i32.const 1024
      i32.store offset=16
      i32.const 1408
      local.get 0
      i32.const 16
      i32.add
      call $printf
      drop
      i32.const -1
      call $exit
      unreachable
    end
    local.get 0
    i32.const 32
    i32.add
    global.set $__stack_pointer
    local.get 2)
  (func $ExtractPlanFromConstraint (type 4) (param i32) (result i32)
    (local i32 i32 i32)
    global.get $__stack_pointer
    i32.const 32
    i32.sub
    local.tee 1
    global.set $__stack_pointer
    block  ;; label = @1
      block  ;; label = @2
        block  ;; label = @3
          i32.const 0
          i32.load offset=3992
          local.tee 2
          br_if 0 (;@3;)
          i32.const 16
          call $malloc
          local.tee 2
          i32.eqz
          br_if 1 (;@2;)
          local.get 2
          i32.const 512
          call $malloc
          local.tee 3
          i32.store
          local.get 3
          i32.eqz
          br_if 2 (;@1;)
          local.get 2
          i32.const -1
          i32.store offset=12
          local.get 2
          i64.const 128
          i64.store offset=4 align=4
          i32.const 0
          local.get 2
          i32.store offset=3992
        end
        local.get 2
        i64.const -4294967296
        i64.store offset=8 align=4
        block  ;; label = @3
          local.get 0
          i32.load offset=4
          i32.eqz
          br_if 0 (;@3;)
          local.get 0
          i32.load8_u offset=12
          i32.const 255
          i32.eq
          br_if 0 (;@3;)
          local.get 2
          local.get 0
          call $List_Add
        end
        call $MakePlan
        local.set 2
        local.get 1
        i32.const 32
        i32.add
        global.set $__stack_pointer
        local.get 2
        return
      end
      local.get 1
      i32.const 1024
      i32.store
      i32.const 1408
      local.get 1
      call $printf
      drop
      i32.const -1
      call $exit
      unreachable
    end
    local.get 1
    i32.const 1024
    i32.store offset=16
    i32.const 1408
    local.get 1
    i32.const 16
    i32.add
    call $printf
    drop
    i32.const -1
    call $exit
    unreachable)
  (func $StayC (type 3) (param i32 i32) (result i32)
    (local i32 i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 2
    global.set $__stack_pointer
    block  ;; label = @1
      i32.const 28
      call $malloc
      local.tee 3
      i32.eqz
      br_if 0 (;@1;)
      local.get 3
      i32.const 255
      i32.store8 offset=12
      local.get 3
      local.get 1
      i32.store offset=8
      local.get 3
      i32.const 0
      i32.store offset=4
      local.get 3
      i32.const 1
      i32.store
      local.get 3
      local.get 0
      i32.store offset=24
      local.get 3
      i32.const 0
      i32.store8 offset=15
      local.get 3
      i32.const 257
      i32.store16 offset=13 align=1
      local.get 3
      i32.const 16
      i32.add
      i32.const 0
      i32.store align=1
      local.get 3
      i32.const 20
      i32.add
      i32.const 0
      i32.store16 align=1
      local.get 0
      i32.load offset=4
      local.get 3
      call $List_Add
      local.get 3
      i32.const 255
      i32.store8 offset=12
      i32.const 0
      i32.const 0
      i32.load offset=3988
      i32.const 1
      i32.add
      i32.store offset=3988
      block  ;; label = @2
        local.get 3
        call $Satisfy
        local.tee 0
        i32.eqz
        br_if 0 (;@2;)
        loop  ;; label = @3
          local.get 0
          call $Satisfy
          local.tee 0
          br_if 0 (;@3;)
        end
      end
      local.get 2
      i32.const 16
      i32.add
      global.set $__stack_pointer
      local.get 3
      return
    end
    local.get 2
    i32.const 1024
    i32.store
    i32.const 1408
    local.get 2
    call $printf
    drop
    i32.const -1
    call $exit
    unreachable)
  (func $EditC (type 3) (param i32 i32) (result i32)
    (local i32 i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 2
    global.set $__stack_pointer
    block  ;; label = @1
      i32.const 28
      call $malloc
      local.tee 3
      i32.eqz
      br_if 0 (;@1;)
      local.get 3
      i32.const 255
      i32.store8 offset=12
      local.get 3
      local.get 1
      i32.store offset=8
      local.get 3
      i32.const 1
      i32.store
      local.get 3
      local.get 0
      i32.store offset=24
      local.get 3
      i32.const 1
      i32.store offset=4
      local.get 3
      i32.const 0
      i32.store8 offset=15
      local.get 3
      i32.const 257
      i32.store16 offset=13 align=1
      local.get 3
      i32.const 16
      i32.add
      i32.const 0
      i32.store align=1
      local.get 3
      i32.const 20
      i32.add
      i32.const 0
      i32.store16 align=1
      local.get 0
      i32.load offset=4
      local.get 3
      call $List_Add
      local.get 3
      i32.const 255
      i32.store8 offset=12
      i32.const 0
      i32.const 0
      i32.load offset=3988
      i32.const 1
      i32.add
      i32.store offset=3988
      block  ;; label = @2
        local.get 3
        call $Satisfy
        local.tee 0
        i32.eqz
        br_if 0 (;@2;)
        loop  ;; label = @3
          local.get 0
          call $Satisfy
          local.tee 0
          br_if 0 (;@3;)
        end
      end
      local.get 2
      i32.const 16
      i32.add
      global.set $__stack_pointer
      local.get 3
      return
    end
    local.get 2
    i32.const 1024
    i32.store
    i32.const 1408
    local.get 2
    call $printf
    drop
    i32.const -1
    call $exit
    unreachable)
  (func $EqualsC_Execute (type 0) (param i32)
    (local i32)
    block  ;; label = @1
      block  ;; label = @2
        block  ;; label = @3
          block  ;; label = @4
            local.get 0
            i32.load8_s offset=12
            br_table 0 (;@4;) 1 (;@3;) 3 (;@1;)
          end
          local.get 0
          i32.const 24
          i32.add
          local.set 1
          local.get 0
          i32.const 28
          i32.add
          local.set 0
          br 1 (;@2;)
        end
        local.get 0
        i32.const 28
        i32.add
        local.set 1
        local.get 0
        i32.const 24
        i32.add
        local.set 0
      end
      local.get 1
      i32.load
      local.get 0
      i32.load
      i32.load
      i32.store
    end)
  (func $EqualsC (type 1) (param i32 i32 i32) (result i32)
    (local i32 i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 3
    global.set $__stack_pointer
    block  ;; label = @1
      i32.const 32
      call $malloc
      local.tee 4
      i32.eqz
      br_if 0 (;@1;)
      local.get 4
      i32.const 255
      i32.store8 offset=12
      local.get 4
      local.get 2
      i32.store offset=8
      local.get 4
      i32.const 0
      i32.store offset=4
      local.get 4
      local.get 0
      i32.store offset=24
      local.get 4
      i32.const 2
      i32.store
      local.get 4
      i64.const 16777730
      i64.store offset=13 align=1
      local.get 4
      i32.const 21
      i32.add
      i32.const 0
      i32.store8
      local.get 4
      i32.const 28
      i32.add
      local.get 1
      i32.store
      local.get 1
      i32.load offset=4
      local.get 4
      call $List_Add
      local.get 4
      i32.load offset=24
      i32.load offset=4
      local.get 4
      call $List_Add
      local.get 4
      i32.const 255
      i32.store8 offset=12
      i32.const 0
      i32.const 0
      i32.load offset=3988
      i32.const 1
      i32.add
      i32.store offset=3988
      block  ;; label = @2
        local.get 4
        call $Satisfy
        local.tee 1
        i32.eqz
        br_if 0 (;@2;)
        loop  ;; label = @3
          local.get 1
          call $Satisfy
          local.tee 1
          br_if 0 (;@3;)
        end
      end
      local.get 3
      i32.const 16
      i32.add
      global.set $__stack_pointer
      local.get 4
      return
    end
    local.get 3
    i32.const 1024
    i32.store
    i32.const 1408
    local.get 3
    call $printf
    drop
    i32.const -1
    call $exit
    unreachable)
  (func $ScaleOffsetC_Execute (type 0) (param i32)
    (local i32)
    block  ;; label = @1
      block  ;; label = @2
        block  ;; label = @3
          block  ;; label = @4
            local.get 0
            i32.load8_s offset=12
            br_table 0 (;@4;) 1 (;@3;) 3 (;@1;)
          end
          local.get 0
          i32.const 32
          i32.add
          i32.load
          i32.load
          local.get 0
          i32.const 28
          i32.add
          i32.load
          i32.load
          local.get 0
          i32.load offset=24
          i32.load
          i32.mul
          i32.add
          local.set 1
          local.get 0
          i32.const 36
          i32.add
          local.set 0
          br 1 (;@2;)
        end
        local.get 0
        i32.const 36
        i32.add
        i32.load
        i32.load
        local.get 0
        i32.const 32
        i32.add
        i32.load
        i32.load
        i32.sub
        local.get 0
        i32.const 28
        i32.add
        i32.load
        i32.load
        i32.div_s
        local.set 1
        local.get 0
        i32.const 24
        i32.add
        local.set 0
      end
      local.get 0
      i32.load
      local.get 1
      i32.store
    end)
  (func $ScaleOffsetC (type 10) (param i32 i32 i32 i32 i32) (result i32)
    (local i32 i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 5
    global.set $__stack_pointer
    block  ;; label = @1
      i32.const 40
      call $malloc
      local.tee 6
      i32.eqz
      br_if 0 (;@1;)
      local.get 6
      i32.const 255
      i32.store8 offset=12
      local.get 6
      local.get 4
      i32.store offset=8
      local.get 6
      i32.const 0
      i32.store offset=4
      local.get 6
      local.get 0
      i32.store offset=24
      local.get 6
      i32.const 3
      i32.store
      local.get 6
      i64.const 197634
      i64.store offset=13 align=1
      local.get 6
      i32.const 21
      i32.add
      i32.const 0
      i32.store8
      local.get 6
      i32.const 36
      i32.add
      local.get 3
      i32.store
      local.get 6
      i32.const 32
      i32.add
      local.tee 0
      local.get 2
      i32.store
      local.get 6
      i32.const 28
      i32.add
      local.tee 2
      local.get 1
      i32.store
      local.get 3
      i32.load offset=4
      local.get 6
      call $List_Add
      local.get 0
      i32.load
      i32.load offset=4
      local.get 6
      call $List_Add
      local.get 2
      i32.load
      i32.load offset=4
      local.get 6
      call $List_Add
      local.get 6
      i32.load offset=24
      i32.load offset=4
      local.get 6
      call $List_Add
      local.get 6
      i32.const 255
      i32.store8 offset=12
      i32.const 0
      i32.const 0
      i32.load offset=3988
      i32.const 1
      i32.add
      i32.store offset=3988
      block  ;; label = @2
        local.get 6
        call $Satisfy
        local.tee 3
        i32.eqz
        br_if 0 (;@2;)
        loop  ;; label = @3
          local.get 3
          call $Satisfy
          local.tee 3
          br_if 0 (;@3;)
        end
      end
      local.get 5
      i32.const 16
      i32.add
      global.set $__stack_pointer
      local.get 6
      return
    end
    local.get 5
    i32.const 1024
    i32.store
    i32.const 1408
    local.get 5
    call $printf
    drop
    i32.const -1
    call $exit
    unreachable)
  (func $ChainTest (type 0) (param i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 80
    i32.sub
    local.tee 1
    global.set $__stack_pointer
    call $InitDeltaBlue
    block  ;; label = @1
      block  ;; label = @2
        local.get 0
        i32.const 1
        i32.ge_s
        br_if 0 (;@2;)
        i32.const 0
        local.set 2
        i32.const 0
        local.set 3
        br 1 (;@1;)
      end
      local.get 0
      i32.const -1
      i32.add
      local.set 4
      i32.const 0
      local.set 5
      i32.const 0
      local.set 6
      i32.const 0
      local.set 3
      i32.const 0
      local.set 2
      loop  ;; label = @2
        local.get 1
        local.get 5
        i32.store offset=32
        local.get 1
        i32.const 48
        i32.add
        i32.const 1128
        local.get 1
        i32.const 32
        i32.add
        call $sprintf
        drop
        local.get 1
        i32.const 48
        i32.add
        i32.const 0
        call $Variable_Create
        local.set 7
        block  ;; label = @3
          local.get 6
          i32.eqz
          br_if 0 (;@3;)
          local.get 6
          local.get 7
          i32.const 0
          call $EqualsC
          drop
        end
        local.get 2
        local.get 7
        local.get 5
        select
        local.set 2
        local.get 7
        local.get 3
        local.get 4
        local.get 5
        i32.eq
        select
        local.set 3
        local.get 7
        local.set 6
        local.get 0
        local.get 5
        i32.const 1
        i32.add
        local.tee 5
        i32.ne
        br_if 0 (;@2;)
      end
    end
    local.get 3
    i32.const 4
    call $StayC
    drop
    local.get 2
    i32.const 3
    call $EditC
    local.tee 8
    call $ExtractPlanFromConstraint
    local.tee 9
    i32.load
    local.set 10
    block  ;; label = @1
      block  ;; label = @2
        block  ;; label = @3
          block  ;; label = @4
            local.get 9
            i32.load offset=8
            local.tee 5
            local.get 9
            i32.load offset=12
            local.tee 7
            i32.gt_s
            br_if 0 (;@4;)
            local.get 10
            local.get 5
            i32.const 2
            i32.shl
            i32.add
            local.set 4
            local.get 10
            local.get 7
            i32.const 2
            i32.shl
            i32.add
            local.set 7
            i32.const 0
            local.set 0
            br 1 (;@3;)
          end
          i32.const 0
          local.set 5
          loop  ;; label = @4
            local.get 2
            local.get 5
            i32.store
            local.get 3
            i32.load
            local.get 5
            i32.ne
            br_if 2 (;@2;)
            local.get 2
            local.get 5
            i32.const 1
            i32.add
            local.tee 5
            i32.store
            local.get 3
            i32.load
            local.get 5
            i32.ne
            br_if 2 (;@2;)
            local.get 2
            local.get 5
            i32.const 1
            i32.add
            local.tee 5
            i32.store
            local.get 3
            i32.load
            local.get 5
            i32.ne
            br_if 2 (;@2;)
            local.get 2
            local.get 5
            i32.const 1
            i32.add
            local.tee 5
            i32.store
            local.get 3
            i32.load
            local.get 5
            i32.ne
            br_if 2 (;@2;)
            local.get 2
            local.get 5
            i32.const 1
            i32.add
            local.tee 5
            i32.store
            local.get 3
            i32.load
            local.get 5
            i32.ne
            br_if 2 (;@2;)
            local.get 5
            i32.const 1
            i32.add
            local.tee 5
            i32.const 100
            i32.ne
            br_if 0 (;@4;)
            br 3 (;@1;)
          end
        end
        loop  ;; label = @3
          local.get 2
          local.get 0
          i32.store
          local.get 4
          local.set 5
          loop  ;; label = @4
            local.get 5
            i32.load
            local.tee 6
            local.get 6
            i32.load
            call_indirect (type 0)
            local.get 5
            i32.const 4
            i32.add
            local.tee 5
            local.get 7
            i32.le_u
            br_if 0 (;@4;)
          end
          local.get 3
          i32.load
          local.get 0
          i32.ne
          br_if 1 (;@2;)
          local.get 2
          local.get 0
          i32.const 1
          i32.or
          local.tee 11
          i32.store
          local.get 4
          local.set 5
          loop  ;; label = @4
            local.get 5
            i32.load
            local.tee 6
            local.get 6
            i32.load
            call_indirect (type 0)
            local.get 5
            i32.const 4
            i32.add
            local.tee 5
            local.get 7
            i32.le_u
            br_if 0 (;@4;)
          end
          local.get 3
          i32.load
          local.get 11
          i32.ne
          br_if 1 (;@2;)
          local.get 0
          i32.const 2
          i32.add
          local.tee 0
          i32.const 100
          i32.eq
          br_if 2 (;@1;)
          br 0 (;@3;)
        end
      end
      local.get 1
      i32.const 1286
      i32.store offset=16
      i32.const 1408
      local.get 1
      i32.const 16
      i32.add
      call $printf
      drop
      i32.const -1
      call $exit
      unreachable
    end
    block  ;; label = @1
      local.get 10
      br_if 0 (;@1;)
      local.get 1
      i32.const 1174
      i32.store
      i32.const 1408
      local.get 1
      call $printf
      drop
      i32.const -1
      call $exit
      unreachable
    end
    local.get 10
    call $free
    local.get 9
    call $free
    local.get 8
    call $DestroyConstraint
    local.get 1
    i32.const 80
    i32.add
    global.set $__stack_pointer)
  (func $Change (type 8) (param i32 i32)
    (local i32 i32 i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 2
    global.set $__stack_pointer
    local.get 0
    i32.const 3
    call $EditC
    local.tee 3
    call $ExtractPlanFromConstraint
    local.set 4
    local.get 0
    local.get 1
    i32.store
    local.get 4
    i32.load
    local.set 5
    block  ;; label = @1
      local.get 4
      i32.load offset=8
      local.tee 1
      local.get 4
      i32.load offset=12
      local.tee 0
      i32.gt_s
      br_if 0 (;@1;)
      local.get 5
      local.get 0
      i32.const 2
      i32.shl
      i32.add
      local.set 0
      local.get 5
      local.get 1
      i32.const 2
      i32.shl
      i32.add
      local.tee 1
      local.set 6
      loop  ;; label = @2
        local.get 6
        i32.load
        local.tee 7
        local.get 7
        i32.load
        call_indirect (type 0)
        local.get 6
        i32.const 4
        i32.add
        local.tee 6
        local.get 0
        i32.le_u
        br_if 0 (;@2;)
      end
      local.get 1
      local.set 6
      loop  ;; label = @2
        local.get 6
        i32.load
        local.tee 7
        local.get 7
        i32.load
        call_indirect (type 0)
        local.get 6
        i32.const 4
        i32.add
        local.tee 6
        local.get 0
        i32.le_u
        br_if 0 (;@2;)
      end
      local.get 1
      local.set 6
      loop  ;; label = @2
        local.get 6
        i32.load
        local.tee 7
        local.get 7
        i32.load
        call_indirect (type 0)
        local.get 6
        i32.const 4
        i32.add
        local.tee 6
        local.get 0
        i32.le_u
        br_if 0 (;@2;)
      end
      local.get 1
      local.set 6
      loop  ;; label = @2
        local.get 6
        i32.load
        local.tee 7
        local.get 7
        i32.load
        call_indirect (type 0)
        local.get 6
        i32.const 4
        i32.add
        local.tee 6
        local.get 0
        i32.le_u
        br_if 0 (;@2;)
      end
      local.get 1
      local.set 6
      loop  ;; label = @2
        local.get 6
        i32.load
        local.tee 7
        local.get 7
        i32.load
        call_indirect (type 0)
        local.get 6
        i32.const 4
        i32.add
        local.tee 6
        local.get 0
        i32.le_u
        br_if 0 (;@2;)
      end
      local.get 1
      local.set 6
      loop  ;; label = @2
        local.get 6
        i32.load
        local.tee 7
        local.get 7
        i32.load
        call_indirect (type 0)
        local.get 6
        i32.const 4
        i32.add
        local.tee 6
        local.get 0
        i32.le_u
        br_if 0 (;@2;)
      end
      local.get 1
      local.set 6
      loop  ;; label = @2
        local.get 6
        i32.load
        local.tee 7
        local.get 7
        i32.load
        call_indirect (type 0)
        local.get 6
        i32.const 4
        i32.add
        local.tee 6
        local.get 0
        i32.le_u
        br_if 0 (;@2;)
      end
      local.get 1
      local.set 6
      loop  ;; label = @2
        local.get 6
        i32.load
        local.tee 7
        local.get 7
        i32.load
        call_indirect (type 0)
        local.get 6
        i32.const 4
        i32.add
        local.tee 6
        local.get 0
        i32.le_u
        br_if 0 (;@2;)
      end
      local.get 1
      local.set 6
      loop  ;; label = @2
        local.get 6
        i32.load
        local.tee 7
        local.get 7
        i32.load
        call_indirect (type 0)
        local.get 6
        i32.const 4
        i32.add
        local.tee 6
        local.get 0
        i32.le_u
        br_if 0 (;@2;)
      end
      loop  ;; label = @2
        local.get 1
        i32.load
        local.tee 6
        local.get 6
        i32.load
        call_indirect (type 0)
        local.get 1
        i32.const 4
        i32.add
        local.tee 1
        local.get 0
        i32.le_u
        br_if 0 (;@2;)
      end
    end
    block  ;; label = @1
      local.get 5
      br_if 0 (;@1;)
      local.get 2
      i32.const 1174
      i32.store
      i32.const 1408
      local.get 2
      call $printf
      drop
      i32.const -1
      call $exit
      unreachable
    end
    local.get 5
    call $free
    local.get 4
    call $free
    local.get 3
    call $DestroyConstraint
    local.get 2
    i32.const 16
    i32.add
    global.set $__stack_pointer)
  (func $ProjectionTest (type 0) (param i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 176
    i32.sub
    local.tee 1
    global.set $__stack_pointer
    call $InitDeltaBlue
    i32.const 1122
    i32.const 10
    call $Variable_Create
    local.set 2
    i32.const 1107
    i32.const 1000
    call $Variable_Create
    local.set 3
    block  ;; label = @1
      block  ;; label = @2
        block  ;; label = @3
          block  ;; label = @4
            block  ;; label = @5
              block  ;; label = @6
                i32.const 16
                call $malloc
                local.tee 4
                i32.eqz
                br_if 0 (;@6;)
                local.get 4
                local.get 0
                i32.const 2
                i32.shl
                call $malloc
                local.tee 5
                i32.store
                local.get 5
                i32.eqz
                br_if 1 (;@5;)
                local.get 4
                i64.const -4294967296
                i64.store offset=8 align=4
                local.get 4
                local.get 0
                i32.store offset=4
                i32.const 0
                local.set 5
                loop  ;; label = @7
                  local.get 1
                  local.get 5
                  i32.const 1
                  i32.add
                  local.tee 5
                  i32.store offset=128
                  local.get 1
                  i32.const 144
                  i32.add
                  i32.const 1141
                  local.get 1
                  i32.const 128
                  i32.add
                  call $sprintf
                  drop
                  local.get 1
                  i32.const 144
                  i32.add
                  local.get 5
                  call $Variable_Create
                  local.set 6
                  local.get 1
                  local.get 5
                  i32.store offset=112
                  local.get 1
                  i32.const 144
                  i32.add
                  i32.const 1133
                  local.get 1
                  i32.const 112
                  i32.add
                  call $sprintf
                  drop
                  local.get 4
                  local.get 1
                  i32.const 144
                  i32.add
                  local.get 5
                  call $Variable_Create
                  local.tee 7
                  call $List_Add
                  local.get 6
                  i32.const 4
                  call $StayC
                  drop
                  local.get 6
                  local.get 2
                  local.get 3
                  local.get 7
                  i32.const 0
                  call $ScaleOffsetC
                  drop
                  local.get 0
                  local.get 5
                  i32.ne
                  br_if 0 (;@7;)
                end
                local.get 6
                i32.const 17
                call $Change
                local.get 7
                i32.load
                i32.const 1170
                i32.ne
                br_if 2 (;@4;)
                local.get 7
                i32.const 1050
                call $Change
                local.get 6
                i32.load
                i32.const 5
                i32.ne
                br_if 3 (;@3;)
                local.get 2
                i32.const 5
                call $Change
                block  ;; label = @7
                  local.get 4
                  i32.load offset=12
                  local.tee 8
                  local.get 4
                  i32.load offset=8
                  local.tee 0
                  i32.sub
                  local.tee 6
                  i32.const 1
                  i32.add
                  local.tee 2
                  i32.const 1
                  i32.gt_s
                  br_if 0 (;@7;)
                  local.get 3
                  i32.const 2000
                  call $Change
                  br 5 (;@2;)
                end
                local.get 4
                i32.load
                local.get 0
                i32.const 2
                i32.shl
                i32.add
                local.set 5
                i32.const 1000
                local.set 7
                block  ;; label = @7
                  block  ;; label = @8
                    loop  ;; label = @9
                      local.get 7
                      i32.const 5
                      i32.add
                      local.tee 7
                      local.get 5
                      i32.load
                      i32.load
                      i32.ne
                      br_if 1 (;@8;)
                      local.get 5
                      i32.const 4
                      i32.add
                      local.set 5
                      local.get 6
                      i32.const -1
                      i32.add
                      local.tee 6
                      i32.eqz
                      br_if 2 (;@7;)
                      br 0 (;@9;)
                    end
                  end
                  local.get 1
                  i32.const 1330
                  i32.store offset=64
                  i32.const 1408
                  local.get 1
                  i32.const 64
                  i32.add
                  call $printf
                  drop
                  i32.const -1
                  call $exit
                  unreachable
                end
                i32.const 2000
                local.set 6
                local.get 3
                i32.const 2000
                call $Change
                local.get 2
                i32.const 2
                i32.lt_s
                br_if 4 (;@2;)
                local.get 8
                local.get 0
                i32.sub
                local.set 7
                local.get 4
                i32.load
                local.tee 2
                local.get 0
                i32.const 2
                i32.shl
                i32.add
                local.set 5
                block  ;; label = @7
                  loop  ;; label = @8
                    local.get 6
                    i32.const 5
                    i32.add
                    local.tee 6
                    local.get 5
                    i32.load
                    i32.load
                    i32.ne
                    br_if 1 (;@7;)
                    local.get 5
                    i32.const 4
                    i32.add
                    local.set 5
                    local.get 7
                    i32.const -1
                    i32.add
                    local.tee 7
                    i32.eqz
                    br_if 7 (;@1;)
                    br 0 (;@8;)
                  end
                end
                local.get 1
                i32.const 1304
                i32.store offset=32
                i32.const 1408
                local.get 1
                i32.const 32
                i32.add
                call $printf
                drop
                i32.const -1
                call $exit
                unreachable
              end
              local.get 1
              i32.const 1024
              i32.store
              i32.const 1408
              local.get 1
              call $printf
              drop
              i32.const -1
              call $exit
              unreachable
            end
            local.get 1
            i32.const 1024
            i32.store offset=16
            i32.const 1408
            local.get 1
            i32.const 16
            i32.add
            call $printf
            drop
            i32.const -1
            call $exit
            unreachable
          end
          local.get 1
          i32.const 1382
          i32.store offset=96
          i32.const 1408
          local.get 1
          i32.const 96
          i32.add
          call $printf
          drop
          i32.const -1
          call $exit
          unreachable
        end
        local.get 1
        i32.const 1356
        i32.store offset=80
        i32.const 1408
        local.get 1
        i32.const 80
        i32.add
        call $printf
        drop
        i32.const -1
        call $exit
        unreachable
      end
      local.get 4
      i32.load
      local.tee 2
      br_if 0 (;@1;)
      local.get 1
      i32.const 1174
      i32.store offset=48
      i32.const 1408
      local.get 1
      i32.const 48
      i32.add
      call $printf
      drop
      i32.const -1
      call $exit
      unreachable
    end
    local.get 2
    call $free
    local.get 4
    call $free
    local.get 1
    i32.const 176
    i32.add
    global.set $__stack_pointer)
  (func $run (type 9) (result i32)
    i32.const 1000
    call $ChainTest
    i32.const 1000
    call $ProjectionTest
    i32.const 0)
  (func $main (type 3) (param i32 i32) (result i32)
    i32.const 1000
    call $ChainTest
    i32.const 1000
    call $ProjectionTest
    i32.const 0)
  (func $malloc (type 4) (param i32) (result i32)
    local.get 0
    call $dlmalloc)
  (func $dlmalloc (type 4) (param i32) (result i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 1
    global.set $__stack_pointer
    block  ;; label = @1
      i32.const 0
      i32.load offset=4028
      br_if 0 (;@1;)
      i32.const 0
      call $sbrk
      i32.const 71136
      i32.sub
      local.tee 2
      i32.const 89
      i32.lt_u
      br_if 0 (;@1;)
      i32.const 0
      local.set 3
      block  ;; label = @2
        i32.const 0
        i32.load offset=4476
        local.tee 4
        br_if 0 (;@2;)
        i32.const 0
        i64.const -1
        i64.store offset=4488 align=4
        i32.const 0
        i64.const 281474976776192
        i64.store offset=4480 align=4
        i32.const 0
        local.get 1
        i32.const 8
        i32.add
        i32.const -16
        i32.and
        i32.const 1431655768
        i32.xor
        local.tee 4
        i32.store offset=4476
        i32.const 0
        i32.const 0
        i32.store offset=4496
        i32.const 0
        i32.const 0
        i32.store offset=4448
      end
      i32.const 0
      local.get 2
      i32.store offset=4456
      i32.const 0
      i32.const 71136
      i32.store offset=4452
      i32.const 0
      i32.const 71136
      i32.store offset=4020
      i32.const 0
      local.get 4
      i32.store offset=4040
      i32.const 0
      i32.const -1
      i32.store offset=4036
      loop  ;; label = @2
        local.get 3
        i32.const 4064
        i32.add
        local.get 3
        i32.const 4052
        i32.add
        local.tee 4
        i32.store
        local.get 4
        local.get 3
        i32.const 4044
        i32.add
        local.tee 5
        i32.store
        local.get 3
        i32.const 4056
        i32.add
        local.get 5
        i32.store
        local.get 3
        i32.const 4072
        i32.add
        local.get 3
        i32.const 4060
        i32.add
        local.tee 5
        i32.store
        local.get 5
        local.get 4
        i32.store
        local.get 3
        i32.const 4080
        i32.add
        local.get 3
        i32.const 4068
        i32.add
        local.tee 4
        i32.store
        local.get 4
        local.get 5
        i32.store
        local.get 3
        i32.const 4076
        i32.add
        local.get 4
        i32.store
        local.get 3
        i32.const 32
        i32.add
        local.tee 3
        i32.const 256
        i32.ne
        br_if 0 (;@2;)
      end
      i32.const 71136
      i32.const -8
      i32.const 71136
      i32.sub
      i32.const 15
      i32.and
      i32.const 0
      i32.const 71136
      i32.const 8
      i32.add
      i32.const 15
      i32.and
      select
      local.tee 3
      i32.add
      local.tee 4
      i32.const 4
      i32.add
      local.get 2
      i32.const -56
      i32.add
      local.tee 5
      local.get 3
      i32.sub
      local.tee 3
      i32.const 1
      i32.or
      i32.store
      i32.const 0
      i32.const 0
      i32.load offset=4492
      i32.store offset=4032
      i32.const 0
      local.get 3
      i32.store offset=4016
      i32.const 0
      local.get 4
      i32.store offset=4028
      i32.const 71136
      local.get 5
      i32.add
      i32.const 56
      i32.store offset=4
    end
    block  ;; label = @1
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
                            local.get 0
                            i32.const 236
                            i32.gt_u
                            br_if 0 (;@12;)
                            block  ;; label = @13
                              i32.const 0
                              i32.load offset=4004
                              local.tee 6
                              i32.const 16
                              local.get 0
                              i32.const 19
                              i32.add
                              i32.const -16
                              i32.and
                              local.get 0
                              i32.const 11
                              i32.lt_u
                              select
                              local.tee 2
                              i32.const 3
                              i32.shr_u
                              local.tee 4
                              i32.shr_u
                              local.tee 3
                              i32.const 3
                              i32.and
                              i32.eqz
                              br_if 0 (;@13;)
                              local.get 3
                              i32.const 1
                              i32.and
                              local.get 4
                              i32.or
                              i32.const 1
                              i32.xor
                              local.tee 5
                              i32.const 3
                              i32.shl
                              local.tee 0
                              i32.const 4052
                              i32.add
                              i32.load
                              local.tee 4
                              i32.const 8
                              i32.add
                              local.set 3
                              block  ;; label = @14
                                block  ;; label = @15
                                  local.get 4
                                  i32.load offset=8
                                  local.tee 2
                                  local.get 0
                                  i32.const 4044
                                  i32.add
                                  local.tee 0
                                  i32.ne
                                  br_if 0 (;@15;)
                                  i32.const 0
                                  local.get 6
                                  i32.const -2
                                  local.get 5
                                  i32.rotl
                                  i32.and
                                  i32.store offset=4004
                                  br 1 (;@14;)
                                end
                                local.get 0
                                local.get 2
                                i32.store offset=8
                                local.get 2
                                local.get 0
                                i32.store offset=12
                              end
                              local.get 4
                              local.get 5
                              i32.const 3
                              i32.shl
                              local.tee 5
                              i32.const 3
                              i32.or
                              i32.store offset=4
                              local.get 4
                              local.get 5
                              i32.add
                              local.tee 4
                              local.get 4
                              i32.load offset=4
                              i32.const 1
                              i32.or
                              i32.store offset=4
                              br 12 (;@1;)
                            end
                            local.get 2
                            i32.const 0
                            i32.load offset=4012
                            local.tee 7
                            i32.le_u
                            br_if 1 (;@11;)
                            block  ;; label = @13
                              local.get 3
                              i32.eqz
                              br_if 0 (;@13;)
                              block  ;; label = @14
                                block  ;; label = @15
                                  local.get 3
                                  local.get 4
                                  i32.shl
                                  i32.const 2
                                  local.get 4
                                  i32.shl
                                  local.tee 3
                                  i32.const 0
                                  local.get 3
                                  i32.sub
                                  i32.or
                                  i32.and
                                  local.tee 3
                                  i32.const 0
                                  local.get 3
                                  i32.sub
                                  i32.and
                                  i32.const -1
                                  i32.add
                                  local.tee 3
                                  local.get 3
                                  i32.const 12
                                  i32.shr_u
                                  i32.const 16
                                  i32.and
                                  local.tee 3
                                  i32.shr_u
                                  local.tee 4
                                  i32.const 5
                                  i32.shr_u
                                  i32.const 8
                                  i32.and
                                  local.tee 5
                                  local.get 3
                                  i32.or
                                  local.get 4
                                  local.get 5
                                  i32.shr_u
                                  local.tee 3
                                  i32.const 2
                                  i32.shr_u
                                  i32.const 4
                                  i32.and
                                  local.tee 4
                                  i32.or
                                  local.get 3
                                  local.get 4
                                  i32.shr_u
                                  local.tee 3
                                  i32.const 1
                                  i32.shr_u
                                  i32.const 2
                                  i32.and
                                  local.tee 4
                                  i32.or
                                  local.get 3
                                  local.get 4
                                  i32.shr_u
                                  local.tee 3
                                  i32.const 1
                                  i32.shr_u
                                  i32.const 1
                                  i32.and
                                  local.tee 4
                                  i32.or
                                  local.get 3
                                  local.get 4
                                  i32.shr_u
                                  i32.add
                                  local.tee 5
                                  i32.const 3
                                  i32.shl
                                  local.tee 0
                                  i32.const 4052
                                  i32.add
                                  i32.load
                                  local.tee 4
                                  i32.load offset=8
                                  local.tee 3
                                  local.get 0
                                  i32.const 4044
                                  i32.add
                                  local.tee 0
                                  i32.ne
                                  br_if 0 (;@15;)
                                  i32.const 0
                                  local.get 6
                                  i32.const -2
                                  local.get 5
                                  i32.rotl
                                  i32.and
                                  local.tee 6
                                  i32.store offset=4004
                                  br 1 (;@14;)
                                end
                                local.get 0
                                local.get 3
                                i32.store offset=8
                                local.get 3
                                local.get 0
                                i32.store offset=12
                              end
                              local.get 4
                              i32.const 8
                              i32.add
                              local.set 3
                              local.get 4
                              local.get 2
                              i32.const 3
                              i32.or
                              i32.store offset=4
                              local.get 4
                              local.get 5
                              i32.const 3
                              i32.shl
                              local.tee 5
                              i32.add
                              local.get 5
                              local.get 2
                              i32.sub
                              local.tee 5
                              i32.store
                              local.get 4
                              local.get 2
                              i32.add
                              local.tee 0
                              local.get 5
                              i32.const 1
                              i32.or
                              i32.store offset=4
                              block  ;; label = @14
                                local.get 7
                                i32.eqz
                                br_if 0 (;@14;)
                                local.get 7
                                i32.const 3
                                i32.shr_u
                                local.tee 8
                                i32.const 3
                                i32.shl
                                i32.const 4044
                                i32.add
                                local.set 2
                                i32.const 0
                                i32.load offset=4024
                                local.set 4
                                block  ;; label = @15
                                  block  ;; label = @16
                                    local.get 6
                                    i32.const 1
                                    local.get 8
                                    i32.shl
                                    local.tee 8
                                    i32.and
                                    br_if 0 (;@16;)
                                    i32.const 0
                                    local.get 6
                                    local.get 8
                                    i32.or
                                    i32.store offset=4004
                                    local.get 2
                                    local.set 8
                                    br 1 (;@15;)
                                  end
                                  local.get 2
                                  i32.load offset=8
                                  local.set 8
                                end
                                local.get 8
                                local.get 4
                                i32.store offset=12
                                local.get 2
                                local.get 4
                                i32.store offset=8
                                local.get 4
                                local.get 2
                                i32.store offset=12
                                local.get 4
                                local.get 8
                                i32.store offset=8
                              end
                              i32.const 0
                              local.get 0
                              i32.store offset=4024
                              i32.const 0
                              local.get 5
                              i32.store offset=4012
                              br 12 (;@1;)
                            end
                            i32.const 0
                            i32.load offset=4008
                            local.tee 9
                            i32.eqz
                            br_if 1 (;@11;)
                            local.get 9
                            i32.const 0
                            local.get 9
                            i32.sub
                            i32.and
                            i32.const -1
                            i32.add
                            local.tee 3
                            local.get 3
                            i32.const 12
                            i32.shr_u
                            i32.const 16
                            i32.and
                            local.tee 3
                            i32.shr_u
                            local.tee 4
                            i32.const 5
                            i32.shr_u
                            i32.const 8
                            i32.and
                            local.tee 5
                            local.get 3
                            i32.or
                            local.get 4
                            local.get 5
                            i32.shr_u
                            local.tee 3
                            i32.const 2
                            i32.shr_u
                            i32.const 4
                            i32.and
                            local.tee 4
                            i32.or
                            local.get 3
                            local.get 4
                            i32.shr_u
                            local.tee 3
                            i32.const 1
                            i32.shr_u
                            i32.const 2
                            i32.and
                            local.tee 4
                            i32.or
                            local.get 3
                            local.get 4
                            i32.shr_u
                            local.tee 3
                            i32.const 1
                            i32.shr_u
                            i32.const 1
                            i32.and
                            local.tee 4
                            i32.or
                            local.get 3
                            local.get 4
                            i32.shr_u
                            i32.add
                            i32.const 2
                            i32.shl
                            i32.const 4308
                            i32.add
                            i32.load
                            local.tee 0
                            i32.load offset=4
                            i32.const -8
                            i32.and
                            local.get 2
                            i32.sub
                            local.set 4
                            local.get 0
                            local.set 5
                            block  ;; label = @13
                              loop  ;; label = @14
                                block  ;; label = @15
                                  local.get 5
                                  i32.load offset=16
                                  local.tee 3
                                  br_if 0 (;@15;)
                                  local.get 5
                                  i32.const 20
                                  i32.add
                                  i32.load
                                  local.tee 3
                                  i32.eqz
                                  br_if 2 (;@13;)
                                end
                                local.get 3
                                i32.load offset=4
                                i32.const -8
                                i32.and
                                local.get 2
                                i32.sub
                                local.tee 5
                                local.get 4
                                local.get 5
                                local.get 4
                                i32.lt_u
                                local.tee 5
                                select
                                local.set 4
                                local.get 3
                                local.get 0
                                local.get 5
                                select
                                local.set 0
                                local.get 3
                                local.set 5
                                br 0 (;@14;)
                              end
                            end
                            local.get 0
                            i32.load offset=24
                            local.set 10
                            block  ;; label = @13
                              local.get 0
                              i32.load offset=12
                              local.tee 8
                              local.get 0
                              i32.eq
                              br_if 0 (;@13;)
                              i32.const 0
                              i32.load offset=4020
                              local.get 0
                              i32.load offset=8
                              local.tee 3
                              i32.gt_u
                              drop
                              local.get 8
                              local.get 3
                              i32.store offset=8
                              local.get 3
                              local.get 8
                              i32.store offset=12
                              br 11 (;@2;)
                            end
                            block  ;; label = @13
                              local.get 0
                              i32.const 20
                              i32.add
                              local.tee 5
                              i32.load
                              local.tee 3
                              br_if 0 (;@13;)
                              local.get 0
                              i32.load offset=16
                              local.tee 3
                              i32.eqz
                              br_if 3 (;@10;)
                              local.get 0
                              i32.const 16
                              i32.add
                              local.set 5
                            end
                            loop  ;; label = @13
                              local.get 5
                              local.set 11
                              local.get 3
                              local.tee 8
                              i32.const 20
                              i32.add
                              local.tee 5
                              i32.load
                              local.tee 3
                              br_if 0 (;@13;)
                              local.get 8
                              i32.const 16
                              i32.add
                              local.set 5
                              local.get 8
                              i32.load offset=16
                              local.tee 3
                              br_if 0 (;@13;)
                            end
                            local.get 11
                            i32.const 0
                            i32.store
                            br 10 (;@2;)
                          end
                          i32.const -1
                          local.set 2
                          local.get 0
                          i32.const -65
                          i32.gt_u
                          br_if 0 (;@11;)
                          local.get 0
                          i32.const 19
                          i32.add
                          local.tee 3
                          i32.const -16
                          i32.and
                          local.set 2
                          i32.const 0
                          i32.load offset=4008
                          local.tee 7
                          i32.eqz
                          br_if 0 (;@11;)
                          i32.const 0
                          local.set 11
                          block  ;; label = @12
                            local.get 2
                            i32.const 256
                            i32.lt_u
                            br_if 0 (;@12;)
                            i32.const 31
                            local.set 11
                            local.get 2
                            i32.const 16777215
                            i32.gt_u
                            br_if 0 (;@12;)
                            local.get 3
                            i32.const 8
                            i32.shr_u
                            local.tee 3
                            local.get 3
                            i32.const 1048320
                            i32.add
                            i32.const 16
                            i32.shr_u
                            i32.const 8
                            i32.and
                            local.tee 3
                            i32.shl
                            local.tee 4
                            local.get 4
                            i32.const 520192
                            i32.add
                            i32.const 16
                            i32.shr_u
                            i32.const 4
                            i32.and
                            local.tee 4
                            i32.shl
                            local.tee 5
                            local.get 5
                            i32.const 245760
                            i32.add
                            i32.const 16
                            i32.shr_u
                            i32.const 2
                            i32.and
                            local.tee 5
                            i32.shl
                            i32.const 15
                            i32.shr_u
                            local.get 3
                            local.get 4
                            i32.or
                            local.get 5
                            i32.or
                            i32.sub
                            local.tee 3
                            i32.const 1
                            i32.shl
                            local.get 2
                            local.get 3
                            i32.const 21
                            i32.add
                            i32.shr_u
                            i32.const 1
                            i32.and
                            i32.or
                            i32.const 28
                            i32.add
                            local.set 11
                          end
                          i32.const 0
                          local.get 2
                          i32.sub
                          local.set 4
                          block  ;; label = @12
                            block  ;; label = @13
                              block  ;; label = @14
                                block  ;; label = @15
                                  local.get 11
                                  i32.const 2
                                  i32.shl
                                  i32.const 4308
                                  i32.add
                                  i32.load
                                  local.tee 5
                                  br_if 0 (;@15;)
                                  i32.const 0
                                  local.set 3
                                  i32.const 0
                                  local.set 8
                                  br 1 (;@14;)
                                end
                                i32.const 0
                                local.set 3
                                local.get 2
                                i32.const 0
                                i32.const 25
                                local.get 11
                                i32.const 1
                                i32.shr_u
                                i32.sub
                                local.get 11
                                i32.const 31
                                i32.eq
                                select
                                i32.shl
                                local.set 0
                                i32.const 0
                                local.set 8
                                loop  ;; label = @15
                                  block  ;; label = @16
                                    local.get 5
                                    i32.load offset=4
                                    i32.const -8
                                    i32.and
                                    local.get 2
                                    i32.sub
                                    local.tee 6
                                    local.get 4
                                    i32.ge_u
                                    br_if 0 (;@16;)
                                    local.get 6
                                    local.set 4
                                    local.get 5
                                    local.set 8
                                    local.get 6
                                    br_if 0 (;@16;)
                                    i32.const 0
                                    local.set 4
                                    local.get 5
                                    local.set 8
                                    local.get 5
                                    local.set 3
                                    br 3 (;@13;)
                                  end
                                  local.get 3
                                  local.get 5
                                  i32.const 20
                                  i32.add
                                  i32.load
                                  local.tee 6
                                  local.get 6
                                  local.get 5
                                  local.get 0
                                  i32.const 29
                                  i32.shr_u
                                  i32.const 4
                                  i32.and
                                  i32.add
                                  i32.const 16
                                  i32.add
                                  i32.load
                                  local.tee 5
                                  i32.eq
                                  select
                                  local.get 3
                                  local.get 6
                                  select
                                  local.set 3
                                  local.get 0
                                  i32.const 1
                                  i32.shl
                                  local.set 0
                                  local.get 5
                                  br_if 0 (;@15;)
                                end
                              end
                              block  ;; label = @14
                                local.get 3
                                local.get 8
                                i32.or
                                br_if 0 (;@14;)
                                i32.const 0
                                local.set 8
                                i32.const 2
                                local.get 11
                                i32.shl
                                local.tee 3
                                i32.const 0
                                local.get 3
                                i32.sub
                                i32.or
                                local.get 7
                                i32.and
                                local.tee 3
                                i32.eqz
                                br_if 3 (;@11;)
                                local.get 3
                                i32.const 0
                                local.get 3
                                i32.sub
                                i32.and
                                i32.const -1
                                i32.add
                                local.tee 3
                                local.get 3
                                i32.const 12
                                i32.shr_u
                                i32.const 16
                                i32.and
                                local.tee 3
                                i32.shr_u
                                local.tee 5
                                i32.const 5
                                i32.shr_u
                                i32.const 8
                                i32.and
                                local.tee 0
                                local.get 3
                                i32.or
                                local.get 5
                                local.get 0
                                i32.shr_u
                                local.tee 3
                                i32.const 2
                                i32.shr_u
                                i32.const 4
                                i32.and
                                local.tee 5
                                i32.or
                                local.get 3
                                local.get 5
                                i32.shr_u
                                local.tee 3
                                i32.const 1
                                i32.shr_u
                                i32.const 2
                                i32.and
                                local.tee 5
                                i32.or
                                local.get 3
                                local.get 5
                                i32.shr_u
                                local.tee 3
                                i32.const 1
                                i32.shr_u
                                i32.const 1
                                i32.and
                                local.tee 5
                                i32.or
                                local.get 3
                                local.get 5
                                i32.shr_u
                                i32.add
                                i32.const 2
                                i32.shl
                                i32.const 4308
                                i32.add
                                i32.load
                                local.set 3
                              end
                              local.get 3
                              i32.eqz
                              br_if 1 (;@12;)
                            end
                            loop  ;; label = @13
                              local.get 3
                              i32.load offset=4
                              i32.const -8
                              i32.and
                              local.get 2
                              i32.sub
                              local.tee 6
                              local.get 4
                              i32.lt_u
                              local.set 0
                              block  ;; label = @14
                                local.get 3
                                i32.load offset=16
                                local.tee 5
                                br_if 0 (;@14;)
                                local.get 3
                                i32.const 20
                                i32.add
                                i32.load
                                local.set 5
                              end
                              local.get 6
                              local.get 4
                              local.get 0
                              select
                              local.set 4
                              local.get 3
                              local.get 8
                              local.get 0
                              select
                              local.set 8
                              local.get 5
                              local.set 3
                              local.get 5
                              br_if 0 (;@13;)
                            end
                          end
                          local.get 8
                          i32.eqz
                          br_if 0 (;@11;)
                          local.get 4
                          i32.const 0
                          i32.load offset=4012
                          local.get 2
                          i32.sub
                          i32.ge_u
                          br_if 0 (;@11;)
                          local.get 8
                          i32.load offset=24
                          local.set 11
                          block  ;; label = @12
                            local.get 8
                            i32.load offset=12
                            local.tee 0
                            local.get 8
                            i32.eq
                            br_if 0 (;@12;)
                            i32.const 0
                            i32.load offset=4020
                            local.get 8
                            i32.load offset=8
                            local.tee 3
                            i32.gt_u
                            drop
                            local.get 0
                            local.get 3
                            i32.store offset=8
                            local.get 3
                            local.get 0
                            i32.store offset=12
                            br 9 (;@3;)
                          end
                          block  ;; label = @12
                            local.get 8
                            i32.const 20
                            i32.add
                            local.tee 5
                            i32.load
                            local.tee 3
                            br_if 0 (;@12;)
                            local.get 8
                            i32.load offset=16
                            local.tee 3
                            i32.eqz
                            br_if 3 (;@9;)
                            local.get 8
                            i32.const 16
                            i32.add
                            local.set 5
                          end
                          loop  ;; label = @12
                            local.get 5
                            local.set 6
                            local.get 3
                            local.tee 0
                            i32.const 20
                            i32.add
                            local.tee 5
                            i32.load
                            local.tee 3
                            br_if 0 (;@12;)
                            local.get 0
                            i32.const 16
                            i32.add
                            local.set 5
                            local.get 0
                            i32.load offset=16
                            local.tee 3
                            br_if 0 (;@12;)
                          end
                          local.get 6
                          i32.const 0
                          i32.store
                          br 8 (;@3;)
                        end
                        block  ;; label = @11
                          i32.const 0
                          i32.load offset=4012
                          local.tee 3
                          local.get 2
                          i32.lt_u
                          br_if 0 (;@11;)
                          i32.const 0
                          i32.load offset=4024
                          local.set 4
                          block  ;; label = @12
                            block  ;; label = @13
                              local.get 3
                              local.get 2
                              i32.sub
                              local.tee 5
                              i32.const 16
                              i32.lt_u
                              br_if 0 (;@13;)
                              local.get 4
                              local.get 2
                              i32.add
                              local.tee 0
                              local.get 5
                              i32.const 1
                              i32.or
                              i32.store offset=4
                              i32.const 0
                              local.get 5
                              i32.store offset=4012
                              i32.const 0
                              local.get 0
                              i32.store offset=4024
                              local.get 4
                              local.get 3
                              i32.add
                              local.get 5
                              i32.store
                              local.get 4
                              local.get 2
                              i32.const 3
                              i32.or
                              i32.store offset=4
                              br 1 (;@12;)
                            end
                            local.get 4
                            local.get 3
                            i32.const 3
                            i32.or
                            i32.store offset=4
                            local.get 4
                            local.get 3
                            i32.add
                            local.tee 3
                            local.get 3
                            i32.load offset=4
                            i32.const 1
                            i32.or
                            i32.store offset=4
                            i32.const 0
                            i32.const 0
                            i32.store offset=4024
                            i32.const 0
                            i32.const 0
                            i32.store offset=4012
                          end
                          local.get 4
                          i32.const 8
                          i32.add
                          local.set 3
                          br 10 (;@1;)
                        end
                        block  ;; label = @11
                          i32.const 0
                          i32.load offset=4016
                          local.tee 0
                          local.get 2
                          i32.le_u
                          br_if 0 (;@11;)
                          i32.const 0
                          i32.load offset=4028
                          local.tee 3
                          local.get 2
                          i32.add
                          local.tee 4
                          local.get 0
                          local.get 2
                          i32.sub
                          local.tee 5
                          i32.const 1
                          i32.or
                          i32.store offset=4
                          i32.const 0
                          local.get 5
                          i32.store offset=4016
                          i32.const 0
                          local.get 4
                          i32.store offset=4028
                          local.get 3
                          local.get 2
                          i32.const 3
                          i32.or
                          i32.store offset=4
                          local.get 3
                          i32.const 8
                          i32.add
                          local.set 3
                          br 10 (;@1;)
                        end
                        block  ;; label = @11
                          block  ;; label = @12
                            i32.const 0
                            i32.load offset=4476
                            i32.eqz
                            br_if 0 (;@12;)
                            i32.const 0
                            i32.load offset=4484
                            local.set 4
                            br 1 (;@11;)
                          end
                          i32.const 0
                          i64.const -1
                          i64.store offset=4488 align=4
                          i32.const 0
                          i64.const 281474976776192
                          i64.store offset=4480 align=4
                          i32.const 0
                          local.get 1
                          i32.const 12
                          i32.add
                          i32.const -16
                          i32.and
                          i32.const 1431655768
                          i32.xor
                          i32.store offset=4476
                          i32.const 0
                          i32.const 0
                          i32.store offset=4496
                          i32.const 0
                          i32.const 0
                          i32.store offset=4448
                          i32.const 65536
                          local.set 4
                        end
                        i32.const 0
                        local.set 3
                        block  ;; label = @11
                          local.get 4
                          local.get 2
                          i32.const 71
                          i32.add
                          local.tee 7
                          i32.add
                          local.tee 6
                          i32.const 0
                          local.get 4
                          i32.sub
                          local.tee 11
                          i32.and
                          local.tee 8
                          local.get 2
                          i32.gt_u
                          br_if 0 (;@11;)
                          i32.const 0
                          i32.const 48
                          i32.store offset=4500
                          br 10 (;@1;)
                        end
                        block  ;; label = @11
                          i32.const 0
                          i32.load offset=4444
                          local.tee 3
                          i32.eqz
                          br_if 0 (;@11;)
                          block  ;; label = @12
                            i32.const 0
                            i32.load offset=4436
                            local.tee 4
                            local.get 8
                            i32.add
                            local.tee 5
                            local.get 4
                            i32.le_u
                            br_if 0 (;@12;)
                            local.get 5
                            local.get 3
                            i32.le_u
                            br_if 1 (;@11;)
                          end
                          i32.const 0
                          local.set 3
                          i32.const 0
                          i32.const 48
                          i32.store offset=4500
                          br 10 (;@1;)
                        end
                        i32.const 0
                        i32.load8_u offset=4448
                        i32.const 4
                        i32.and
                        br_if 4 (;@6;)
                        block  ;; label = @11
                          block  ;; label = @12
                            block  ;; label = @13
                              i32.const 0
                              i32.load offset=4028
                              local.tee 4
                              i32.eqz
                              br_if 0 (;@13;)
                              i32.const 4452
                              local.set 3
                              loop  ;; label = @14
                                block  ;; label = @15
                                  local.get 3
                                  i32.load
                                  local.tee 5
                                  local.get 4
                                  i32.gt_u
                                  br_if 0 (;@15;)
                                  local.get 5
                                  local.get 3
                                  i32.load offset=4
                                  i32.add
                                  local.get 4
                                  i32.gt_u
                                  br_if 3 (;@12;)
                                end
                                local.get 3
                                i32.load offset=8
                                local.tee 3
                                br_if 0 (;@14;)
                              end
                            end
                            i32.const 0
                            call $sbrk
                            local.tee 0
                            i32.const -1
                            i32.eq
                            br_if 5 (;@7;)
                            local.get 8
                            local.set 6
                            block  ;; label = @13
                              i32.const 0
                              i32.load offset=4480
                              local.tee 3
                              i32.const -1
                              i32.add
                              local.tee 4
                              local.get 0
                              i32.and
                              i32.eqz
                              br_if 0 (;@13;)
                              local.get 8
                              local.get 0
                              i32.sub
                              local.get 4
                              local.get 0
                              i32.add
                              i32.const 0
                              local.get 3
                              i32.sub
                              i32.and
                              i32.add
                              local.set 6
                            end
                            local.get 6
                            local.get 2
                            i32.le_u
                            br_if 5 (;@7;)
                            local.get 6
                            i32.const 2147483646
                            i32.gt_u
                            br_if 5 (;@7;)
                            block  ;; label = @13
                              i32.const 0
                              i32.load offset=4444
                              local.tee 3
                              i32.eqz
                              br_if 0 (;@13;)
                              i32.const 0
                              i32.load offset=4436
                              local.tee 4
                              local.get 6
                              i32.add
                              local.tee 5
                              local.get 4
                              i32.le_u
                              br_if 6 (;@7;)
                              local.get 5
                              local.get 3
                              i32.gt_u
                              br_if 6 (;@7;)
                            end
                            local.get 6
                            call $sbrk
                            local.tee 3
                            local.get 0
                            i32.ne
                            br_if 1 (;@11;)
                            br 7 (;@5;)
                          end
                          local.get 6
                          local.get 0
                          i32.sub
                          local.get 11
                          i32.and
                          local.tee 6
                          i32.const 2147483646
                          i32.gt_u
                          br_if 4 (;@7;)
                          local.get 6
                          call $sbrk
                          local.tee 0
                          local.get 3
                          i32.load
                          local.get 3
                          i32.load offset=4
                          i32.add
                          i32.eq
                          br_if 3 (;@8;)
                          local.get 0
                          local.set 3
                        end
                        block  ;; label = @11
                          local.get 3
                          i32.const -1
                          i32.eq
                          br_if 0 (;@11;)
                          local.get 2
                          i32.const 72
                          i32.add
                          local.get 6
                          i32.le_u
                          br_if 0 (;@11;)
                          block  ;; label = @12
                            local.get 7
                            local.get 6
                            i32.sub
                            i32.const 0
                            i32.load offset=4484
                            local.tee 4
                            i32.add
                            i32.const 0
                            local.get 4
                            i32.sub
                            i32.and
                            local.tee 4
                            i32.const 2147483646
                            i32.le_u
                            br_if 0 (;@12;)
                            local.get 3
                            local.set 0
                            br 7 (;@5;)
                          end
                          block  ;; label = @12
                            local.get 4
                            call $sbrk
                            i32.const -1
                            i32.eq
                            br_if 0 (;@12;)
                            local.get 4
                            local.get 6
                            i32.add
                            local.set 6
                            local.get 3
                            local.set 0
                            br 7 (;@5;)
                          end
                          i32.const 0
                          local.get 6
                          i32.sub
                          call $sbrk
                          drop
                          br 4 (;@7;)
                        end
                        local.get 3
                        local.set 0
                        local.get 3
                        i32.const -1
                        i32.ne
                        br_if 5 (;@5;)
                        br 3 (;@7;)
                      end
                      i32.const 0
                      local.set 8
                      br 7 (;@2;)
                    end
                    i32.const 0
                    local.set 0
                    br 5 (;@3;)
                  end
                  local.get 0
                  i32.const -1
                  i32.ne
                  br_if 2 (;@5;)
                end
                i32.const 0
                i32.const 0
                i32.load offset=4448
                i32.const 4
                i32.or
                i32.store offset=4448
              end
              local.get 8
              i32.const 2147483646
              i32.gt_u
              br_if 1 (;@4;)
              local.get 8
              call $sbrk
              local.set 0
              i32.const 0
              call $sbrk
              local.set 3
              local.get 0
              i32.const -1
              i32.eq
              br_if 1 (;@4;)
              local.get 3
              i32.const -1
              i32.eq
              br_if 1 (;@4;)
              local.get 0
              local.get 3
              i32.ge_u
              br_if 1 (;@4;)
              local.get 3
              local.get 0
              i32.sub
              local.tee 6
              local.get 2
              i32.const 56
              i32.add
              i32.le_u
              br_if 1 (;@4;)
            end
            i32.const 0
            i32.const 0
            i32.load offset=4436
            local.get 6
            i32.add
            local.tee 3
            i32.store offset=4436
            block  ;; label = @5
              local.get 3
              i32.const 0
              i32.load offset=4440
              i32.le_u
              br_if 0 (;@5;)
              i32.const 0
              local.get 3
              i32.store offset=4440
            end
            block  ;; label = @5
              block  ;; label = @6
                block  ;; label = @7
                  block  ;; label = @8
                    i32.const 0
                    i32.load offset=4028
                    local.tee 4
                    i32.eqz
                    br_if 0 (;@8;)
                    i32.const 4452
                    local.set 3
                    loop  ;; label = @9
                      local.get 0
                      local.get 3
                      i32.load
                      local.tee 5
                      local.get 3
                      i32.load offset=4
                      local.tee 8
                      i32.add
                      i32.eq
                      br_if 2 (;@7;)
                      local.get 3
                      i32.load offset=8
                      local.tee 3
                      br_if 0 (;@9;)
                      br 3 (;@6;)
                    end
                  end
                  block  ;; label = @8
                    block  ;; label = @9
                      i32.const 0
                      i32.load offset=4020
                      local.tee 3
                      i32.eqz
                      br_if 0 (;@9;)
                      local.get 0
                      local.get 3
                      i32.ge_u
                      br_if 1 (;@8;)
                    end
                    i32.const 0
                    local.get 0
                    i32.store offset=4020
                  end
                  i32.const 0
                  local.set 3
                  i32.const 0
                  local.get 6
                  i32.store offset=4456
                  i32.const 0
                  local.get 0
                  i32.store offset=4452
                  i32.const 0
                  i32.const -1
                  i32.store offset=4036
                  i32.const 0
                  i32.const 0
                  i32.load offset=4476
                  i32.store offset=4040
                  i32.const 0
                  i32.const 0
                  i32.store offset=4464
                  loop  ;; label = @8
                    local.get 3
                    i32.const 4064
                    i32.add
                    local.get 3
                    i32.const 4052
                    i32.add
                    local.tee 4
                    i32.store
                    local.get 4
                    local.get 3
                    i32.const 4044
                    i32.add
                    local.tee 5
                    i32.store
                    local.get 3
                    i32.const 4056
                    i32.add
                    local.get 5
                    i32.store
                    local.get 3
                    i32.const 4072
                    i32.add
                    local.get 3
                    i32.const 4060
                    i32.add
                    local.tee 5
                    i32.store
                    local.get 5
                    local.get 4
                    i32.store
                    local.get 3
                    i32.const 4080
                    i32.add
                    local.get 3
                    i32.const 4068
                    i32.add
                    local.tee 4
                    i32.store
                    local.get 4
                    local.get 5
                    i32.store
                    local.get 3
                    i32.const 4076
                    i32.add
                    local.get 4
                    i32.store
                    local.get 3
                    i32.const 32
                    i32.add
                    local.tee 3
                    i32.const 256
                    i32.ne
                    br_if 0 (;@8;)
                  end
                  local.get 0
                  i32.const -8
                  local.get 0
                  i32.sub
                  i32.const 15
                  i32.and
                  i32.const 0
                  local.get 0
                  i32.const 8
                  i32.add
                  i32.const 15
                  i32.and
                  select
                  local.tee 3
                  i32.add
                  local.tee 4
                  local.get 6
                  i32.const -56
                  i32.add
                  local.tee 5
                  local.get 3
                  i32.sub
                  local.tee 3
                  i32.const 1
                  i32.or
                  i32.store offset=4
                  i32.const 0
                  i32.const 0
                  i32.load offset=4492
                  i32.store offset=4032
                  i32.const 0
                  local.get 3
                  i32.store offset=4016
                  i32.const 0
                  local.get 4
                  i32.store offset=4028
                  local.get 0
                  local.get 5
                  i32.add
                  i32.const 56
                  i32.store offset=4
                  br 2 (;@5;)
                end
                local.get 3
                i32.load8_u offset=12
                i32.const 8
                i32.and
                br_if 0 (;@6;)
                local.get 5
                local.get 4
                i32.gt_u
                br_if 0 (;@6;)
                local.get 0
                local.get 4
                i32.le_u
                br_if 0 (;@6;)
                local.get 4
                i32.const -8
                local.get 4
                i32.sub
                i32.const 15
                i32.and
                i32.const 0
                local.get 4
                i32.const 8
                i32.add
                i32.const 15
                i32.and
                select
                local.tee 5
                i32.add
                local.tee 0
                i32.const 0
                i32.load offset=4016
                local.get 6
                i32.add
                local.tee 11
                local.get 5
                i32.sub
                local.tee 5
                i32.const 1
                i32.or
                i32.store offset=4
                local.get 3
                local.get 8
                local.get 6
                i32.add
                i32.store offset=4
                i32.const 0
                i32.const 0
                i32.load offset=4492
                i32.store offset=4032
                i32.const 0
                local.get 5
                i32.store offset=4016
                i32.const 0
                local.get 0
                i32.store offset=4028
                local.get 4
                local.get 11
                i32.add
                i32.const 56
                i32.store offset=4
                br 1 (;@5;)
              end
              block  ;; label = @6
                local.get 0
                i32.const 0
                i32.load offset=4020
                local.tee 8
                i32.ge_u
                br_if 0 (;@6;)
                i32.const 0
                local.get 0
                i32.store offset=4020
                local.get 0
                local.set 8
              end
              local.get 0
              local.get 6
              i32.add
              local.set 5
              i32.const 4452
              local.set 3
              block  ;; label = @6
                block  ;; label = @7
                  block  ;; label = @8
                    block  ;; label = @9
                      block  ;; label = @10
                        block  ;; label = @11
                          block  ;; label = @12
                            loop  ;; label = @13
                              local.get 3
                              i32.load
                              local.get 5
                              i32.eq
                              br_if 1 (;@12;)
                              local.get 3
                              i32.load offset=8
                              local.tee 3
                              br_if 0 (;@13;)
                              br 2 (;@11;)
                            end
                          end
                          local.get 3
                          i32.load8_u offset=12
                          i32.const 8
                          i32.and
                          i32.eqz
                          br_if 1 (;@10;)
                        end
                        i32.const 4452
                        local.set 3
                        loop  ;; label = @11
                          block  ;; label = @12
                            local.get 3
                            i32.load
                            local.tee 5
                            local.get 4
                            i32.gt_u
                            br_if 0 (;@12;)
                            local.get 5
                            local.get 3
                            i32.load offset=4
                            i32.add
                            local.tee 5
                            local.get 4
                            i32.gt_u
                            br_if 3 (;@9;)
                          end
                          local.get 3
                          i32.load offset=8
                          local.set 3
                          br 0 (;@11;)
                        end
                      end
                      local.get 3
                      local.get 0
                      i32.store
                      local.get 3
                      local.get 3
                      i32.load offset=4
                      local.get 6
                      i32.add
                      i32.store offset=4
                      local.get 0
                      i32.const -8
                      local.get 0
                      i32.sub
                      i32.const 15
                      i32.and
                      i32.const 0
                      local.get 0
                      i32.const 8
                      i32.add
                      i32.const 15
                      i32.and
                      select
                      i32.add
                      local.tee 11
                      local.get 2
                      i32.const 3
                      i32.or
                      i32.store offset=4
                      local.get 5
                      i32.const -8
                      local.get 5
                      i32.sub
                      i32.const 15
                      i32.and
                      i32.const 0
                      local.get 5
                      i32.const 8
                      i32.add
                      i32.const 15
                      i32.and
                      select
                      i32.add
                      local.tee 6
                      local.get 11
                      local.get 2
                      i32.add
                      local.tee 2
                      i32.sub
                      local.set 5
                      block  ;; label = @10
                        local.get 4
                        local.get 6
                        i32.ne
                        br_if 0 (;@10;)
                        i32.const 0
                        local.get 2
                        i32.store offset=4028
                        i32.const 0
                        i32.const 0
                        i32.load offset=4016
                        local.get 5
                        i32.add
                        local.tee 3
                        i32.store offset=4016
                        local.get 2
                        local.get 3
                        i32.const 1
                        i32.or
                        i32.store offset=4
                        br 3 (;@7;)
                      end
                      block  ;; label = @10
                        i32.const 0
                        i32.load offset=4024
                        local.get 6
                        i32.ne
                        br_if 0 (;@10;)
                        i32.const 0
                        local.get 2
                        i32.store offset=4024
                        i32.const 0
                        i32.const 0
                        i32.load offset=4012
                        local.get 5
                        i32.add
                        local.tee 3
                        i32.store offset=4012
                        local.get 2
                        local.get 3
                        i32.const 1
                        i32.or
                        i32.store offset=4
                        local.get 2
                        local.get 3
                        i32.add
                        local.get 3
                        i32.store
                        br 3 (;@7;)
                      end
                      block  ;; label = @10
                        local.get 6
                        i32.load offset=4
                        local.tee 3
                        i32.const 3
                        i32.and
                        i32.const 1
                        i32.ne
                        br_if 0 (;@10;)
                        local.get 3
                        i32.const -8
                        i32.and
                        local.set 7
                        block  ;; label = @11
                          block  ;; label = @12
                            local.get 3
                            i32.const 255
                            i32.gt_u
                            br_if 0 (;@12;)
                            local.get 6
                            i32.load offset=8
                            local.tee 4
                            local.get 3
                            i32.const 3
                            i32.shr_u
                            local.tee 8
                            i32.const 3
                            i32.shl
                            i32.const 4044
                            i32.add
                            local.tee 0
                            i32.eq
                            drop
                            block  ;; label = @13
                              local.get 6
                              i32.load offset=12
                              local.tee 3
                              local.get 4
                              i32.ne
                              br_if 0 (;@13;)
                              i32.const 0
                              i32.const 0
                              i32.load offset=4004
                              i32.const -2
                              local.get 8
                              i32.rotl
                              i32.and
                              i32.store offset=4004
                              br 2 (;@11;)
                            end
                            local.get 3
                            local.get 0
                            i32.eq
                            drop
                            local.get 3
                            local.get 4
                            i32.store offset=8
                            local.get 4
                            local.get 3
                            i32.store offset=12
                            br 1 (;@11;)
                          end
                          local.get 6
                          i32.load offset=24
                          local.set 9
                          block  ;; label = @12
                            block  ;; label = @13
                              local.get 6
                              i32.load offset=12
                              local.tee 0
                              local.get 6
                              i32.eq
                              br_if 0 (;@13;)
                              local.get 8
                              local.get 6
                              i32.load offset=8
                              local.tee 3
                              i32.gt_u
                              drop
                              local.get 0
                              local.get 3
                              i32.store offset=8
                              local.get 3
                              local.get 0
                              i32.store offset=12
                              br 1 (;@12;)
                            end
                            block  ;; label = @13
                              local.get 6
                              i32.const 20
                              i32.add
                              local.tee 3
                              i32.load
                              local.tee 4
                              br_if 0 (;@13;)
                              local.get 6
                              i32.const 16
                              i32.add
                              local.tee 3
                              i32.load
                              local.tee 4
                              br_if 0 (;@13;)
                              i32.const 0
                              local.set 0
                              br 1 (;@12;)
                            end
                            loop  ;; label = @13
                              local.get 3
                              local.set 8
                              local.get 4
                              local.tee 0
                              i32.const 20
                              i32.add
                              local.tee 3
                              i32.load
                              local.tee 4
                              br_if 0 (;@13;)
                              local.get 0
                              i32.const 16
                              i32.add
                              local.set 3
                              local.get 0
                              i32.load offset=16
                              local.tee 4
                              br_if 0 (;@13;)
                            end
                            local.get 8
                            i32.const 0
                            i32.store
                          end
                          local.get 9
                          i32.eqz
                          br_if 0 (;@11;)
                          block  ;; label = @12
                            block  ;; label = @13
                              local.get 6
                              i32.load offset=28
                              local.tee 4
                              i32.const 2
                              i32.shl
                              i32.const 4308
                              i32.add
                              local.tee 3
                              i32.load
                              local.get 6
                              i32.ne
                              br_if 0 (;@13;)
                              local.get 3
                              local.get 0
                              i32.store
                              local.get 0
                              br_if 1 (;@12;)
                              i32.const 0
                              i32.const 0
                              i32.load offset=4008
                              i32.const -2
                              local.get 4
                              i32.rotl
                              i32.and
                              i32.store offset=4008
                              br 2 (;@11;)
                            end
                            local.get 9
                            i32.const 16
                            i32.const 20
                            local.get 9
                            i32.load offset=16
                            local.get 6
                            i32.eq
                            select
                            i32.add
                            local.get 0
                            i32.store
                            local.get 0
                            i32.eqz
                            br_if 1 (;@11;)
                          end
                          local.get 0
                          local.get 9
                          i32.store offset=24
                          block  ;; label = @12
                            local.get 6
                            i32.load offset=16
                            local.tee 3
                            i32.eqz
                            br_if 0 (;@12;)
                            local.get 0
                            local.get 3
                            i32.store offset=16
                            local.get 3
                            local.get 0
                            i32.store offset=24
                          end
                          local.get 6
                          i32.load offset=20
                          local.tee 3
                          i32.eqz
                          br_if 0 (;@11;)
                          local.get 0
                          i32.const 20
                          i32.add
                          local.get 3
                          i32.store
                          local.get 3
                          local.get 0
                          i32.store offset=24
                        end
                        local.get 7
                        local.get 5
                        i32.add
                        local.set 5
                        local.get 6
                        local.get 7
                        i32.add
                        local.set 6
                      end
                      local.get 6
                      local.get 6
                      i32.load offset=4
                      i32.const -2
                      i32.and
                      i32.store offset=4
                      local.get 2
                      local.get 5
                      i32.add
                      local.get 5
                      i32.store
                      local.get 2
                      local.get 5
                      i32.const 1
                      i32.or
                      i32.store offset=4
                      block  ;; label = @10
                        local.get 5
                        i32.const 255
                        i32.gt_u
                        br_if 0 (;@10;)
                        local.get 5
                        i32.const 3
                        i32.shr_u
                        local.tee 4
                        i32.const 3
                        i32.shl
                        i32.const 4044
                        i32.add
                        local.set 3
                        block  ;; label = @11
                          block  ;; label = @12
                            i32.const 0
                            i32.load offset=4004
                            local.tee 5
                            i32.const 1
                            local.get 4
                            i32.shl
                            local.tee 4
                            i32.and
                            br_if 0 (;@12;)
                            i32.const 0
                            local.get 5
                            local.get 4
                            i32.or
                            i32.store offset=4004
                            local.get 3
                            local.set 4
                            br 1 (;@11;)
                          end
                          local.get 3
                          i32.load offset=8
                          local.set 4
                        end
                        local.get 4
                        local.get 2
                        i32.store offset=12
                        local.get 3
                        local.get 2
                        i32.store offset=8
                        local.get 2
                        local.get 3
                        i32.store offset=12
                        local.get 2
                        local.get 4
                        i32.store offset=8
                        br 3 (;@7;)
                      end
                      i32.const 31
                      local.set 3
                      block  ;; label = @10
                        local.get 5
                        i32.const 16777215
                        i32.gt_u
                        br_if 0 (;@10;)
                        local.get 5
                        i32.const 8
                        i32.shr_u
                        local.tee 3
                        local.get 3
                        i32.const 1048320
                        i32.add
                        i32.const 16
                        i32.shr_u
                        i32.const 8
                        i32.and
                        local.tee 3
                        i32.shl
                        local.tee 4
                        local.get 4
                        i32.const 520192
                        i32.add
                        i32.const 16
                        i32.shr_u
                        i32.const 4
                        i32.and
                        local.tee 4
                        i32.shl
                        local.tee 0
                        local.get 0
                        i32.const 245760
                        i32.add
                        i32.const 16
                        i32.shr_u
                        i32.const 2
                        i32.and
                        local.tee 0
                        i32.shl
                        i32.const 15
                        i32.shr_u
                        local.get 3
                        local.get 4
                        i32.or
                        local.get 0
                        i32.or
                        i32.sub
                        local.tee 3
                        i32.const 1
                        i32.shl
                        local.get 5
                        local.get 3
                        i32.const 21
                        i32.add
                        i32.shr_u
                        i32.const 1
                        i32.and
                        i32.or
                        i32.const 28
                        i32.add
                        local.set 3
                      end
                      local.get 2
                      local.get 3
                      i32.store offset=28
                      local.get 2
                      i64.const 0
                      i64.store offset=16 align=4
                      local.get 3
                      i32.const 2
                      i32.shl
                      i32.const 4308
                      i32.add
                      local.set 4
                      block  ;; label = @10
                        i32.const 0
                        i32.load offset=4008
                        local.tee 0
                        i32.const 1
                        local.get 3
                        i32.shl
                        local.tee 8
                        i32.and
                        br_if 0 (;@10;)
                        local.get 4
                        local.get 2
                        i32.store
                        i32.const 0
                        local.get 0
                        local.get 8
                        i32.or
                        i32.store offset=4008
                        local.get 2
                        local.get 4
                        i32.store offset=24
                        local.get 2
                        local.get 2
                        i32.store offset=8
                        local.get 2
                        local.get 2
                        i32.store offset=12
                        br 3 (;@7;)
                      end
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
                      local.set 3
                      local.get 4
                      i32.load
                      local.set 0
                      loop  ;; label = @10
                        local.get 0
                        local.tee 4
                        i32.load offset=4
                        i32.const -8
                        i32.and
                        local.get 5
                        i32.eq
                        br_if 2 (;@8;)
                        local.get 3
                        i32.const 29
                        i32.shr_u
                        local.set 0
                        local.get 3
                        i32.const 1
                        i32.shl
                        local.set 3
                        local.get 4
                        local.get 0
                        i32.const 4
                        i32.and
                        i32.add
                        i32.const 16
                        i32.add
                        local.tee 8
                        i32.load
                        local.tee 0
                        br_if 0 (;@10;)
                      end
                      local.get 8
                      local.get 2
                      i32.store
                      local.get 2
                      local.get 4
                      i32.store offset=24
                      local.get 2
                      local.get 2
                      i32.store offset=12
                      local.get 2
                      local.get 2
                      i32.store offset=8
                      br 2 (;@7;)
                    end
                    local.get 0
                    i32.const -8
                    local.get 0
                    i32.sub
                    i32.const 15
                    i32.and
                    i32.const 0
                    local.get 0
                    i32.const 8
                    i32.add
                    i32.const 15
                    i32.and
                    select
                    local.tee 3
                    i32.add
                    local.tee 11
                    local.get 6
                    i32.const -56
                    i32.add
                    local.tee 8
                    local.get 3
                    i32.sub
                    local.tee 3
                    i32.const 1
                    i32.or
                    i32.store offset=4
                    local.get 0
                    local.get 8
                    i32.add
                    i32.const 56
                    i32.store offset=4
                    local.get 4
                    local.get 5
                    i32.const 55
                    local.get 5
                    i32.sub
                    i32.const 15
                    i32.and
                    i32.const 0
                    local.get 5
                    i32.const -55
                    i32.add
                    i32.const 15
                    i32.and
                    select
                    i32.add
                    i32.const -63
                    i32.add
                    local.tee 8
                    local.get 8
                    local.get 4
                    i32.const 16
                    i32.add
                    i32.lt_u
                    select
                    local.tee 8
                    i32.const 35
                    i32.store offset=4
                    i32.const 0
                    i32.const 0
                    i32.load offset=4492
                    i32.store offset=4032
                    i32.const 0
                    local.get 3
                    i32.store offset=4016
                    i32.const 0
                    local.get 11
                    i32.store offset=4028
                    local.get 8
                    i32.const 16
                    i32.add
                    i32.const 0
                    i64.load offset=4460 align=4
                    i64.store align=4
                    local.get 8
                    i32.const 0
                    i64.load offset=4452 align=4
                    i64.store offset=8 align=4
                    i32.const 0
                    local.get 8
                    i32.const 8
                    i32.add
                    i32.store offset=4460
                    i32.const 0
                    local.get 6
                    i32.store offset=4456
                    i32.const 0
                    local.get 0
                    i32.store offset=4452
                    i32.const 0
                    i32.const 0
                    i32.store offset=4464
                    local.get 8
                    i32.const 36
                    i32.add
                    local.set 3
                    loop  ;; label = @9
                      local.get 3
                      i32.const 7
                      i32.store
                      local.get 5
                      local.get 3
                      i32.const 4
                      i32.add
                      local.tee 3
                      i32.gt_u
                      br_if 0 (;@9;)
                    end
                    local.get 8
                    local.get 4
                    i32.eq
                    br_if 3 (;@5;)
                    local.get 8
                    local.get 8
                    i32.load offset=4
                    i32.const -2
                    i32.and
                    i32.store offset=4
                    local.get 8
                    local.get 8
                    local.get 4
                    i32.sub
                    local.tee 6
                    i32.store
                    local.get 4
                    local.get 6
                    i32.const 1
                    i32.or
                    i32.store offset=4
                    block  ;; label = @9
                      local.get 6
                      i32.const 255
                      i32.gt_u
                      br_if 0 (;@9;)
                      local.get 6
                      i32.const 3
                      i32.shr_u
                      local.tee 5
                      i32.const 3
                      i32.shl
                      i32.const 4044
                      i32.add
                      local.set 3
                      block  ;; label = @10
                        block  ;; label = @11
                          i32.const 0
                          i32.load offset=4004
                          local.tee 0
                          i32.const 1
                          local.get 5
                          i32.shl
                          local.tee 5
                          i32.and
                          br_if 0 (;@11;)
                          i32.const 0
                          local.get 0
                          local.get 5
                          i32.or
                          i32.store offset=4004
                          local.get 3
                          local.set 5
                          br 1 (;@10;)
                        end
                        local.get 3
                        i32.load offset=8
                        local.set 5
                      end
                      local.get 5
                      local.get 4
                      i32.store offset=12
                      local.get 3
                      local.get 4
                      i32.store offset=8
                      local.get 4
                      local.get 3
                      i32.store offset=12
                      local.get 4
                      local.get 5
                      i32.store offset=8
                      br 4 (;@5;)
                    end
                    i32.const 31
                    local.set 3
                    block  ;; label = @9
                      local.get 6
                      i32.const 16777215
                      i32.gt_u
                      br_if 0 (;@9;)
                      local.get 6
                      i32.const 8
                      i32.shr_u
                      local.tee 3
                      local.get 3
                      i32.const 1048320
                      i32.add
                      i32.const 16
                      i32.shr_u
                      i32.const 8
                      i32.and
                      local.tee 3
                      i32.shl
                      local.tee 5
                      local.get 5
                      i32.const 520192
                      i32.add
                      i32.const 16
                      i32.shr_u
                      i32.const 4
                      i32.and
                      local.tee 5
                      i32.shl
                      local.tee 0
                      local.get 0
                      i32.const 245760
                      i32.add
                      i32.const 16
                      i32.shr_u
                      i32.const 2
                      i32.and
                      local.tee 0
                      i32.shl
                      i32.const 15
                      i32.shr_u
                      local.get 3
                      local.get 5
                      i32.or
                      local.get 0
                      i32.or
                      i32.sub
                      local.tee 3
                      i32.const 1
                      i32.shl
                      local.get 6
                      local.get 3
                      i32.const 21
                      i32.add
                      i32.shr_u
                      i32.const 1
                      i32.and
                      i32.or
                      i32.const 28
                      i32.add
                      local.set 3
                    end
                    local.get 4
                    i64.const 0
                    i64.store offset=16 align=4
                    local.get 4
                    i32.const 28
                    i32.add
                    local.get 3
                    i32.store
                    local.get 3
                    i32.const 2
                    i32.shl
                    i32.const 4308
                    i32.add
                    local.set 5
                    block  ;; label = @9
                      i32.const 0
                      i32.load offset=4008
                      local.tee 0
                      i32.const 1
                      local.get 3
                      i32.shl
                      local.tee 8
                      i32.and
                      br_if 0 (;@9;)
                      local.get 5
                      local.get 4
                      i32.store
                      i32.const 0
                      local.get 0
                      local.get 8
                      i32.or
                      i32.store offset=4008
                      local.get 4
                      i32.const 24
                      i32.add
                      local.get 5
                      i32.store
                      local.get 4
                      local.get 4
                      i32.store offset=8
                      local.get 4
                      local.get 4
                      i32.store offset=12
                      br 4 (;@5;)
                    end
                    local.get 6
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
                    local.set 3
                    local.get 5
                    i32.load
                    local.set 0
                    loop  ;; label = @9
                      local.get 0
                      local.tee 5
                      i32.load offset=4
                      i32.const -8
                      i32.and
                      local.get 6
                      i32.eq
                      br_if 3 (;@6;)
                      local.get 3
                      i32.const 29
                      i32.shr_u
                      local.set 0
                      local.get 3
                      i32.const 1
                      i32.shl
                      local.set 3
                      local.get 5
                      local.get 0
                      i32.const 4
                      i32.and
                      i32.add
                      i32.const 16
                      i32.add
                      local.tee 8
                      i32.load
                      local.tee 0
                      br_if 0 (;@9;)
                    end
                    local.get 8
                    local.get 4
                    i32.store
                    local.get 4
                    i32.const 24
                    i32.add
                    local.get 5
                    i32.store
                    local.get 4
                    local.get 4
                    i32.store offset=12
                    local.get 4
                    local.get 4
                    i32.store offset=8
                    br 3 (;@5;)
                  end
                  local.get 4
                  i32.load offset=8
                  local.tee 3
                  local.get 2
                  i32.store offset=12
                  local.get 4
                  local.get 2
                  i32.store offset=8
                  local.get 2
                  i32.const 0
                  i32.store offset=24
                  local.get 2
                  local.get 4
                  i32.store offset=12
                  local.get 2
                  local.get 3
                  i32.store offset=8
                end
                local.get 11
                i32.const 8
                i32.add
                local.set 3
                br 5 (;@1;)
              end
              local.get 5
              i32.load offset=8
              local.tee 3
              local.get 4
              i32.store offset=12
              local.get 5
              local.get 4
              i32.store offset=8
              local.get 4
              i32.const 24
              i32.add
              i32.const 0
              i32.store
              local.get 4
              local.get 5
              i32.store offset=12
              local.get 4
              local.get 3
              i32.store offset=8
            end
            i32.const 0
            i32.load offset=4016
            local.tee 3
            local.get 2
            i32.le_u
            br_if 0 (;@4;)
            i32.const 0
            i32.load offset=4028
            local.tee 4
            local.get 2
            i32.add
            local.tee 5
            local.get 3
            local.get 2
            i32.sub
            local.tee 3
            i32.const 1
            i32.or
            i32.store offset=4
            i32.const 0
            local.get 3
            i32.store offset=4016
            i32.const 0
            local.get 5
            i32.store offset=4028
            local.get 4
            local.get 2
            i32.const 3
            i32.or
            i32.store offset=4
            local.get 4
            i32.const 8
            i32.add
            local.set 3
            br 3 (;@1;)
          end
          i32.const 0
          local.set 3
          i32.const 0
          i32.const 48
          i32.store offset=4500
          br 2 (;@1;)
        end
        block  ;; label = @3
          local.get 11
          i32.eqz
          br_if 0 (;@3;)
          block  ;; label = @4
            block  ;; label = @5
              local.get 8
              local.get 8
              i32.load offset=28
              local.tee 5
              i32.const 2
              i32.shl
              i32.const 4308
              i32.add
              local.tee 3
              i32.load
              i32.ne
              br_if 0 (;@5;)
              local.get 3
              local.get 0
              i32.store
              local.get 0
              br_if 1 (;@4;)
              i32.const 0
              local.get 7
              i32.const -2
              local.get 5
              i32.rotl
              i32.and
              local.tee 7
              i32.store offset=4008
              br 2 (;@3;)
            end
            local.get 11
            i32.const 16
            i32.const 20
            local.get 11
            i32.load offset=16
            local.get 8
            i32.eq
            select
            i32.add
            local.get 0
            i32.store
            local.get 0
            i32.eqz
            br_if 1 (;@3;)
          end
          local.get 0
          local.get 11
          i32.store offset=24
          block  ;; label = @4
            local.get 8
            i32.load offset=16
            local.tee 3
            i32.eqz
            br_if 0 (;@4;)
            local.get 0
            local.get 3
            i32.store offset=16
            local.get 3
            local.get 0
            i32.store offset=24
          end
          local.get 8
          i32.const 20
          i32.add
          i32.load
          local.tee 3
          i32.eqz
          br_if 0 (;@3;)
          local.get 0
          i32.const 20
          i32.add
          local.get 3
          i32.store
          local.get 3
          local.get 0
          i32.store offset=24
        end
        block  ;; label = @3
          block  ;; label = @4
            local.get 4
            i32.const 15
            i32.gt_u
            br_if 0 (;@4;)
            local.get 8
            local.get 4
            local.get 2
            i32.add
            local.tee 3
            i32.const 3
            i32.or
            i32.store offset=4
            local.get 8
            local.get 3
            i32.add
            local.tee 3
            local.get 3
            i32.load offset=4
            i32.const 1
            i32.or
            i32.store offset=4
            br 1 (;@3;)
          end
          local.get 8
          local.get 2
          i32.add
          local.tee 0
          local.get 4
          i32.const 1
          i32.or
          i32.store offset=4
          local.get 8
          local.get 2
          i32.const 3
          i32.or
          i32.store offset=4
          local.get 0
          local.get 4
          i32.add
          local.get 4
          i32.store
          block  ;; label = @4
            local.get 4
            i32.const 255
            i32.gt_u
            br_if 0 (;@4;)
            local.get 4
            i32.const 3
            i32.shr_u
            local.tee 4
            i32.const 3
            i32.shl
            i32.const 4044
            i32.add
            local.set 3
            block  ;; label = @5
              block  ;; label = @6
                i32.const 0
                i32.load offset=4004
                local.tee 5
                i32.const 1
                local.get 4
                i32.shl
                local.tee 4
                i32.and
                br_if 0 (;@6;)
                i32.const 0
                local.get 5
                local.get 4
                i32.or
                i32.store offset=4004
                local.get 3
                local.set 4
                br 1 (;@5;)
              end
              local.get 3
              i32.load offset=8
              local.set 4
            end
            local.get 4
            local.get 0
            i32.store offset=12
            local.get 3
            local.get 0
            i32.store offset=8
            local.get 0
            local.get 3
            i32.store offset=12
            local.get 0
            local.get 4
            i32.store offset=8
            br 1 (;@3;)
          end
          i32.const 31
          local.set 3
          block  ;; label = @4
            local.get 4
            i32.const 16777215
            i32.gt_u
            br_if 0 (;@4;)
            local.get 4
            i32.const 8
            i32.shr_u
            local.tee 3
            local.get 3
            i32.const 1048320
            i32.add
            i32.const 16
            i32.shr_u
            i32.const 8
            i32.and
            local.tee 3
            i32.shl
            local.tee 5
            local.get 5
            i32.const 520192
            i32.add
            i32.const 16
            i32.shr_u
            i32.const 4
            i32.and
            local.tee 5
            i32.shl
            local.tee 2
            local.get 2
            i32.const 245760
            i32.add
            i32.const 16
            i32.shr_u
            i32.const 2
            i32.and
            local.tee 2
            i32.shl
            i32.const 15
            i32.shr_u
            local.get 3
            local.get 5
            i32.or
            local.get 2
            i32.or
            i32.sub
            local.tee 3
            i32.const 1
            i32.shl
            local.get 4
            local.get 3
            i32.const 21
            i32.add
            i32.shr_u
            i32.const 1
            i32.and
            i32.or
            i32.const 28
            i32.add
            local.set 3
          end
          local.get 0
          local.get 3
          i32.store offset=28
          local.get 0
          i64.const 0
          i64.store offset=16 align=4
          local.get 3
          i32.const 2
          i32.shl
          i32.const 4308
          i32.add
          local.set 5
          block  ;; label = @4
            local.get 7
            i32.const 1
            local.get 3
            i32.shl
            local.tee 2
            i32.and
            br_if 0 (;@4;)
            local.get 5
            local.get 0
            i32.store
            i32.const 0
            local.get 7
            local.get 2
            i32.or
            i32.store offset=4008
            local.get 0
            local.get 5
            i32.store offset=24
            local.get 0
            local.get 0
            i32.store offset=8
            local.get 0
            local.get 0
            i32.store offset=12
            br 1 (;@3;)
          end
          local.get 4
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
          local.set 3
          local.get 5
          i32.load
          local.set 2
          block  ;; label = @4
            loop  ;; label = @5
              local.get 2
              local.tee 5
              i32.load offset=4
              i32.const -8
              i32.and
              local.get 4
              i32.eq
              br_if 1 (;@4;)
              local.get 3
              i32.const 29
              i32.shr_u
              local.set 2
              local.get 3
              i32.const 1
              i32.shl
              local.set 3
              local.get 5
              local.get 2
              i32.const 4
              i32.and
              i32.add
              i32.const 16
              i32.add
              local.tee 6
              i32.load
              local.tee 2
              br_if 0 (;@5;)
            end
            local.get 6
            local.get 0
            i32.store
            local.get 0
            local.get 5
            i32.store offset=24
            local.get 0
            local.get 0
            i32.store offset=12
            local.get 0
            local.get 0
            i32.store offset=8
            br 1 (;@3;)
          end
          local.get 5
          i32.load offset=8
          local.tee 3
          local.get 0
          i32.store offset=12
          local.get 5
          local.get 0
          i32.store offset=8
          local.get 0
          i32.const 0
          i32.store offset=24
          local.get 0
          local.get 5
          i32.store offset=12
          local.get 0
          local.get 3
          i32.store offset=8
        end
        local.get 8
        i32.const 8
        i32.add
        local.set 3
        br 1 (;@1;)
      end
      block  ;; label = @2
        local.get 10
        i32.eqz
        br_if 0 (;@2;)
        block  ;; label = @3
          block  ;; label = @4
            local.get 0
            local.get 0
            i32.load offset=28
            local.tee 5
            i32.const 2
            i32.shl
            i32.const 4308
            i32.add
            local.tee 3
            i32.load
            i32.ne
            br_if 0 (;@4;)
            local.get 3
            local.get 8
            i32.store
            local.get 8
            br_if 1 (;@3;)
            i32.const 0
            local.get 9
            i32.const -2
            local.get 5
            i32.rotl
            i32.and
            i32.store offset=4008
            br 2 (;@2;)
          end
          local.get 10
          i32.const 16
          i32.const 20
          local.get 10
          i32.load offset=16
          local.get 0
          i32.eq
          select
          i32.add
          local.get 8
          i32.store
          local.get 8
          i32.eqz
          br_if 1 (;@2;)
        end
        local.get 8
        local.get 10
        i32.store offset=24
        block  ;; label = @3
          local.get 0
          i32.load offset=16
          local.tee 3
          i32.eqz
          br_if 0 (;@3;)
          local.get 8
          local.get 3
          i32.store offset=16
          local.get 3
          local.get 8
          i32.store offset=24
        end
        local.get 0
        i32.const 20
        i32.add
        i32.load
        local.tee 3
        i32.eqz
        br_if 0 (;@2;)
        local.get 8
        i32.const 20
        i32.add
        local.get 3
        i32.store
        local.get 3
        local.get 8
        i32.store offset=24
      end
      block  ;; label = @2
        block  ;; label = @3
          local.get 4
          i32.const 15
          i32.gt_u
          br_if 0 (;@3;)
          local.get 0
          local.get 4
          local.get 2
          i32.add
          local.tee 3
          i32.const 3
          i32.or
          i32.store offset=4
          local.get 0
          local.get 3
          i32.add
          local.tee 3
          local.get 3
          i32.load offset=4
          i32.const 1
          i32.or
          i32.store offset=4
          br 1 (;@2;)
        end
        local.get 0
        local.get 2
        i32.add
        local.tee 5
        local.get 4
        i32.const 1
        i32.or
        i32.store offset=4
        local.get 0
        local.get 2
        i32.const 3
        i32.or
        i32.store offset=4
        local.get 5
        local.get 4
        i32.add
        local.get 4
        i32.store
        block  ;; label = @3
          local.get 7
          i32.eqz
          br_if 0 (;@3;)
          local.get 7
          i32.const 3
          i32.shr_u
          local.tee 8
          i32.const 3
          i32.shl
          i32.const 4044
          i32.add
          local.set 2
          i32.const 0
          i32.load offset=4024
          local.set 3
          block  ;; label = @4
            block  ;; label = @5
              i32.const 1
              local.get 8
              i32.shl
              local.tee 8
              local.get 6
              i32.and
              br_if 0 (;@5;)
              i32.const 0
              local.get 8
              local.get 6
              i32.or
              i32.store offset=4004
              local.get 2
              local.set 8
              br 1 (;@4;)
            end
            local.get 2
            i32.load offset=8
            local.set 8
          end
          local.get 8
          local.get 3
          i32.store offset=12
          local.get 2
          local.get 3
          i32.store offset=8
          local.get 3
          local.get 2
          i32.store offset=12
          local.get 3
          local.get 8
          i32.store offset=8
        end
        i32.const 0
        local.get 5
        i32.store offset=4024
        i32.const 0
        local.get 4
        i32.store offset=4012
      end
      local.get 0
      i32.const 8
      i32.add
      local.set 3
    end
    local.get 1
    i32.const 16
    i32.add
    global.set $__stack_pointer
    local.get 3)
  (func $free (type 0) (param i32)
    local.get 0
    call $dlfree)
  (func $dlfree (type 0) (param i32)
    (local i32 i32 i32 i32 i32 i32 i32)
    block  ;; label = @1
      local.get 0
      i32.eqz
      br_if 0 (;@1;)
      local.get 0
      i32.const -8
      i32.add
      local.tee 1
      local.get 0
      i32.const -4
      i32.add
      i32.load
      local.tee 2
      i32.const -8
      i32.and
      local.tee 0
      i32.add
      local.set 3
      block  ;; label = @2
        local.get 2
        i32.const 1
        i32.and
        br_if 0 (;@2;)
        local.get 2
        i32.const 3
        i32.and
        i32.eqz
        br_if 1 (;@1;)
        local.get 1
        local.get 1
        i32.load
        local.tee 2
        i32.sub
        local.tee 1
        i32.const 0
        i32.load offset=4020
        local.tee 4
        i32.lt_u
        br_if 1 (;@1;)
        local.get 2
        local.get 0
        i32.add
        local.set 0
        block  ;; label = @3
          i32.const 0
          i32.load offset=4024
          local.get 1
          i32.eq
          br_if 0 (;@3;)
          block  ;; label = @4
            local.get 2
            i32.const 255
            i32.gt_u
            br_if 0 (;@4;)
            local.get 1
            i32.load offset=8
            local.tee 4
            local.get 2
            i32.const 3
            i32.shr_u
            local.tee 5
            i32.const 3
            i32.shl
            i32.const 4044
            i32.add
            local.tee 6
            i32.eq
            drop
            block  ;; label = @5
              local.get 1
              i32.load offset=12
              local.tee 2
              local.get 4
              i32.ne
              br_if 0 (;@5;)
              i32.const 0
              i32.const 0
              i32.load offset=4004
              i32.const -2
              local.get 5
              i32.rotl
              i32.and
              i32.store offset=4004
              br 3 (;@2;)
            end
            local.get 2
            local.get 6
            i32.eq
            drop
            local.get 2
            local.get 4
            i32.store offset=8
            local.get 4
            local.get 2
            i32.store offset=12
            br 2 (;@2;)
          end
          local.get 1
          i32.load offset=24
          local.set 7
          block  ;; label = @4
            block  ;; label = @5
              local.get 1
              i32.load offset=12
              local.tee 6
              local.get 1
              i32.eq
              br_if 0 (;@5;)
              local.get 4
              local.get 1
              i32.load offset=8
              local.tee 2
              i32.gt_u
              drop
              local.get 6
              local.get 2
              i32.store offset=8
              local.get 2
              local.get 6
              i32.store offset=12
              br 1 (;@4;)
            end
            block  ;; label = @5
              local.get 1
              i32.const 20
              i32.add
              local.tee 2
              i32.load
              local.tee 4
              br_if 0 (;@5;)
              local.get 1
              i32.const 16
              i32.add
              local.tee 2
              i32.load
              local.tee 4
              br_if 0 (;@5;)
              i32.const 0
              local.set 6
              br 1 (;@4;)
            end
            loop  ;; label = @5
              local.get 2
              local.set 5
              local.get 4
              local.tee 6
              i32.const 20
              i32.add
              local.tee 2
              i32.load
              local.tee 4
              br_if 0 (;@5;)
              local.get 6
              i32.const 16
              i32.add
              local.set 2
              local.get 6
              i32.load offset=16
              local.tee 4
              br_if 0 (;@5;)
            end
            local.get 5
            i32.const 0
            i32.store
          end
          local.get 7
          i32.eqz
          br_if 1 (;@2;)
          block  ;; label = @4
            block  ;; label = @5
              local.get 1
              i32.load offset=28
              local.tee 4
              i32.const 2
              i32.shl
              i32.const 4308
              i32.add
              local.tee 2
              i32.load
              local.get 1
              i32.ne
              br_if 0 (;@5;)
              local.get 2
              local.get 6
              i32.store
              local.get 6
              br_if 1 (;@4;)
              i32.const 0
              i32.const 0
              i32.load offset=4008
              i32.const -2
              local.get 4
              i32.rotl
              i32.and
              i32.store offset=4008
              br 3 (;@2;)
            end
            local.get 7
            i32.const 16
            i32.const 20
            local.get 7
            i32.load offset=16
            local.get 1
            i32.eq
            select
            i32.add
            local.get 6
            i32.store
            local.get 6
            i32.eqz
            br_if 2 (;@2;)
          end
          local.get 6
          local.get 7
          i32.store offset=24
          block  ;; label = @4
            local.get 1
            i32.load offset=16
            local.tee 2
            i32.eqz
            br_if 0 (;@4;)
            local.get 6
            local.get 2
            i32.store offset=16
            local.get 2
            local.get 6
            i32.store offset=24
          end
          local.get 1
          i32.load offset=20
          local.tee 2
          i32.eqz
          br_if 1 (;@2;)
          local.get 6
          i32.const 20
          i32.add
          local.get 2
          i32.store
          local.get 2
          local.get 6
          i32.store offset=24
          br 1 (;@2;)
        end
        local.get 3
        i32.load offset=4
        local.tee 2
        i32.const 3
        i32.and
        i32.const 3
        i32.ne
        br_if 0 (;@2;)
        local.get 3
        local.get 2
        i32.const -2
        i32.and
        i32.store offset=4
        i32.const 0
        local.get 0
        i32.store offset=4012
        local.get 1
        local.get 0
        i32.add
        local.get 0
        i32.store
        local.get 1
        local.get 0
        i32.const 1
        i32.or
        i32.store offset=4
        return
      end
      local.get 3
      local.get 1
      i32.le_u
      br_if 0 (;@1;)
      local.get 3
      i32.load offset=4
      local.tee 2
      i32.const 1
      i32.and
      i32.eqz
      br_if 0 (;@1;)
      block  ;; label = @2
        block  ;; label = @3
          local.get 2
          i32.const 2
          i32.and
          br_if 0 (;@3;)
          block  ;; label = @4
            i32.const 0
            i32.load offset=4028
            local.get 3
            i32.ne
            br_if 0 (;@4;)
            i32.const 0
            local.get 1
            i32.store offset=4028
            i32.const 0
            i32.const 0
            i32.load offset=4016
            local.get 0
            i32.add
            local.tee 0
            i32.store offset=4016
            local.get 1
            local.get 0
            i32.const 1
            i32.or
            i32.store offset=4
            local.get 1
            i32.const 0
            i32.load offset=4024
            i32.ne
            br_if 3 (;@1;)
            i32.const 0
            i32.const 0
            i32.store offset=4012
            i32.const 0
            i32.const 0
            i32.store offset=4024
            return
          end
          block  ;; label = @4
            i32.const 0
            i32.load offset=4024
            local.get 3
            i32.ne
            br_if 0 (;@4;)
            i32.const 0
            local.get 1
            i32.store offset=4024
            i32.const 0
            i32.const 0
            i32.load offset=4012
            local.get 0
            i32.add
            local.tee 0
            i32.store offset=4012
            local.get 1
            local.get 0
            i32.const 1
            i32.or
            i32.store offset=4
            local.get 1
            local.get 0
            i32.add
            local.get 0
            i32.store
            return
          end
          local.get 2
          i32.const -8
          i32.and
          local.get 0
          i32.add
          local.set 0
          block  ;; label = @4
            block  ;; label = @5
              local.get 2
              i32.const 255
              i32.gt_u
              br_if 0 (;@5;)
              local.get 3
              i32.load offset=8
              local.tee 4
              local.get 2
              i32.const 3
              i32.shr_u
              local.tee 5
              i32.const 3
              i32.shl
              i32.const 4044
              i32.add
              local.tee 6
              i32.eq
              drop
              block  ;; label = @6
                local.get 3
                i32.load offset=12
                local.tee 2
                local.get 4
                i32.ne
                br_if 0 (;@6;)
                i32.const 0
                i32.const 0
                i32.load offset=4004
                i32.const -2
                local.get 5
                i32.rotl
                i32.and
                i32.store offset=4004
                br 2 (;@4;)
              end
              local.get 2
              local.get 6
              i32.eq
              drop
              local.get 2
              local.get 4
              i32.store offset=8
              local.get 4
              local.get 2
              i32.store offset=12
              br 1 (;@4;)
            end
            local.get 3
            i32.load offset=24
            local.set 7
            block  ;; label = @5
              block  ;; label = @6
                local.get 3
                i32.load offset=12
                local.tee 6
                local.get 3
                i32.eq
                br_if 0 (;@6;)
                i32.const 0
                i32.load offset=4020
                local.get 3
                i32.load offset=8
                local.tee 2
                i32.gt_u
                drop
                local.get 6
                local.get 2
                i32.store offset=8
                local.get 2
                local.get 6
                i32.store offset=12
                br 1 (;@5;)
              end
              block  ;; label = @6
                local.get 3
                i32.const 20
                i32.add
                local.tee 2
                i32.load
                local.tee 4
                br_if 0 (;@6;)
                local.get 3
                i32.const 16
                i32.add
                local.tee 2
                i32.load
                local.tee 4
                br_if 0 (;@6;)
                i32.const 0
                local.set 6
                br 1 (;@5;)
              end
              loop  ;; label = @6
                local.get 2
                local.set 5
                local.get 4
                local.tee 6
                i32.const 20
                i32.add
                local.tee 2
                i32.load
                local.tee 4
                br_if 0 (;@6;)
                local.get 6
                i32.const 16
                i32.add
                local.set 2
                local.get 6
                i32.load offset=16
                local.tee 4
                br_if 0 (;@6;)
              end
              local.get 5
              i32.const 0
              i32.store
            end
            local.get 7
            i32.eqz
            br_if 0 (;@4;)
            block  ;; label = @5
              block  ;; label = @6
                local.get 3
                i32.load offset=28
                local.tee 4
                i32.const 2
                i32.shl
                i32.const 4308
                i32.add
                local.tee 2
                i32.load
                local.get 3
                i32.ne
                br_if 0 (;@6;)
                local.get 2
                local.get 6
                i32.store
                local.get 6
                br_if 1 (;@5;)
                i32.const 0
                i32.const 0
                i32.load offset=4008
                i32.const -2
                local.get 4
                i32.rotl
                i32.and
                i32.store offset=4008
                br 2 (;@4;)
              end
              local.get 7
              i32.const 16
              i32.const 20
              local.get 7
              i32.load offset=16
              local.get 3
              i32.eq
              select
              i32.add
              local.get 6
              i32.store
              local.get 6
              i32.eqz
              br_if 1 (;@4;)
            end
            local.get 6
            local.get 7
            i32.store offset=24
            block  ;; label = @5
              local.get 3
              i32.load offset=16
              local.tee 2
              i32.eqz
              br_if 0 (;@5;)
              local.get 6
              local.get 2
              i32.store offset=16
              local.get 2
              local.get 6
              i32.store offset=24
            end
            local.get 3
            i32.load offset=20
            local.tee 2
            i32.eqz
            br_if 0 (;@4;)
            local.get 6
            i32.const 20
            i32.add
            local.get 2
            i32.store
            local.get 2
            local.get 6
            i32.store offset=24
          end
          local.get 1
          local.get 0
          i32.add
          local.get 0
          i32.store
          local.get 1
          local.get 0
          i32.const 1
          i32.or
          i32.store offset=4
          local.get 1
          i32.const 0
          i32.load offset=4024
          i32.ne
          br_if 1 (;@2;)
          i32.const 0
          local.get 0
          i32.store offset=4012
          return
        end
        local.get 3
        local.get 2
        i32.const -2
        i32.and
        i32.store offset=4
        local.get 1
        local.get 0
        i32.add
        local.get 0
        i32.store
        local.get 1
        local.get 0
        i32.const 1
        i32.or
        i32.store offset=4
      end
      block  ;; label = @2
        local.get 0
        i32.const 255
        i32.gt_u
        br_if 0 (;@2;)
        local.get 0
        i32.const 3
        i32.shr_u
        local.tee 2
        i32.const 3
        i32.shl
        i32.const 4044
        i32.add
        local.set 0
        block  ;; label = @3
          block  ;; label = @4
            i32.const 0
            i32.load offset=4004
            local.tee 4
            i32.const 1
            local.get 2
            i32.shl
            local.tee 2
            i32.and
            br_if 0 (;@4;)
            i32.const 0
            local.get 4
            local.get 2
            i32.or
            i32.store offset=4004
            local.get 0
            local.set 2
            br 1 (;@3;)
          end
          local.get 0
          i32.load offset=8
          local.set 2
        end
        local.get 2
        local.get 1
        i32.store offset=12
        local.get 0
        local.get 1
        i32.store offset=8
        local.get 1
        local.get 0
        i32.store offset=12
        local.get 1
        local.get 2
        i32.store offset=8
        return
      end
      i32.const 31
      local.set 2
      block  ;; label = @2
        local.get 0
        i32.const 16777215
        i32.gt_u
        br_if 0 (;@2;)
        local.get 0
        i32.const 8
        i32.shr_u
        local.tee 2
        local.get 2
        i32.const 1048320
        i32.add
        i32.const 16
        i32.shr_u
        i32.const 8
        i32.and
        local.tee 2
        i32.shl
        local.tee 4
        local.get 4
        i32.const 520192
        i32.add
        i32.const 16
        i32.shr_u
        i32.const 4
        i32.and
        local.tee 4
        i32.shl
        local.tee 6
        local.get 6
        i32.const 245760
        i32.add
        i32.const 16
        i32.shr_u
        i32.const 2
        i32.and
        local.tee 6
        i32.shl
        i32.const 15
        i32.shr_u
        local.get 2
        local.get 4
        i32.or
        local.get 6
        i32.or
        i32.sub
        local.tee 2
        i32.const 1
        i32.shl
        local.get 0
        local.get 2
        i32.const 21
        i32.add
        i32.shr_u
        i32.const 1
        i32.and
        i32.or
        i32.const 28
        i32.add
        local.set 2
      end
      local.get 1
      i64.const 0
      i64.store offset=16 align=4
      local.get 1
      i32.const 28
      i32.add
      local.get 2
      i32.store
      local.get 2
      i32.const 2
      i32.shl
      i32.const 4308
      i32.add
      local.set 4
      block  ;; label = @2
        block  ;; label = @3
          i32.const 0
          i32.load offset=4008
          local.tee 6
          i32.const 1
          local.get 2
          i32.shl
          local.tee 3
          i32.and
          br_if 0 (;@3;)
          local.get 4
          local.get 1
          i32.store
          i32.const 0
          local.get 6
          local.get 3
          i32.or
          i32.store offset=4008
          local.get 1
          i32.const 24
          i32.add
          local.get 4
          i32.store
          local.get 1
          local.get 1
          i32.store offset=8
          local.get 1
          local.get 1
          i32.store offset=12
          br 1 (;@2;)
        end
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
        local.set 2
        local.get 4
        i32.load
        local.set 6
        block  ;; label = @3
          loop  ;; label = @4
            local.get 6
            local.tee 4
            i32.load offset=4
            i32.const -8
            i32.and
            local.get 0
            i32.eq
            br_if 1 (;@3;)
            local.get 2
            i32.const 29
            i32.shr_u
            local.set 6
            local.get 2
            i32.const 1
            i32.shl
            local.set 2
            local.get 4
            local.get 6
            i32.const 4
            i32.and
            i32.add
            i32.const 16
            i32.add
            local.tee 3
            i32.load
            local.tee 6
            br_if 0 (;@4;)
          end
          local.get 3
          local.get 1
          i32.store
          local.get 1
          i32.const 24
          i32.add
          local.get 4
          i32.store
          local.get 1
          local.get 1
          i32.store offset=12
          local.get 1
          local.get 1
          i32.store offset=8
          br 1 (;@2;)
        end
        local.get 4
        i32.load offset=8
        local.tee 0
        local.get 1
        i32.store offset=12
        local.get 4
        local.get 1
        i32.store offset=8
        local.get 1
        i32.const 24
        i32.add
        i32.const 0
        i32.store
        local.get 1
        local.get 4
        i32.store offset=12
        local.get 1
        local.get 0
        i32.store offset=8
      end
      i32.const 0
      i32.const 0
      i32.load offset=4036
      i32.const -1
      i32.add
      local.tee 1
      i32.const -1
      local.get 1
      select
      i32.store offset=4036
    end)
  (func $calloc (type 3) (param i32 i32) (result i32)
    (local i32 i64)
    block  ;; label = @1
      block  ;; label = @2
        local.get 0
        br_if 0 (;@2;)
        i32.const 0
        local.set 2
        br 1 (;@1;)
      end
      local.get 0
      i64.extend_i32_u
      local.get 1
      i64.extend_i32_u
      i64.mul
      local.tee 3
      i32.wrap_i64
      local.set 2
      local.get 1
      local.get 0
      i32.or
      i32.const 65536
      i32.lt_u
      br_if 0 (;@1;)
      i32.const -1
      local.get 2
      local.get 3
      i64.const 32
      i64.shr_u
      i32.wrap_i64
      i32.const 0
      i32.ne
      select
      local.set 2
    end
    block  ;; label = @1
      local.get 2
      call $dlmalloc
      local.tee 0
      i32.eqz
      br_if 0 (;@1;)
      local.get 0
      i32.const -4
      i32.add
      i32.load8_u
      i32.const 3
      i32.and
      i32.eqz
      br_if 0 (;@1;)
      local.get 0
      i32.const 0
      local.get 2
      call $memset
      drop
    end
    local.get 0)
  (func $realloc (type 3) (param i32 i32) (result i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32)
    block  ;; label = @1
      local.get 0
      br_if 0 (;@1;)
      local.get 1
      call $dlmalloc
      return
    end
    block  ;; label = @1
      local.get 1
      i32.const -64
      i32.lt_u
      br_if 0 (;@1;)
      i32.const 0
      i32.const 48
      i32.store offset=4500
      i32.const 0
      return
    end
    i32.const 16
    local.get 1
    i32.const 19
    i32.add
    i32.const -16
    i32.and
    local.get 1
    i32.const 11
    i32.lt_u
    select
    local.set 2
    local.get 0
    i32.const -4
    i32.add
    local.tee 3
    i32.load
    local.tee 4
    i32.const -8
    i32.and
    local.set 5
    block  ;; label = @1
      block  ;; label = @2
        block  ;; label = @3
          local.get 4
          i32.const 3
          i32.and
          br_if 0 (;@3;)
          local.get 2
          i32.const 256
          i32.lt_u
          br_if 1 (;@2;)
          local.get 5
          local.get 2
          i32.const 4
          i32.or
          i32.lt_u
          br_if 1 (;@2;)
          local.get 5
          local.get 2
          i32.sub
          i32.const 0
          i32.load offset=4484
          i32.const 1
          i32.shl
          i32.le_u
          br_if 2 (;@1;)
          br 1 (;@2;)
        end
        local.get 0
        i32.const -8
        i32.add
        local.tee 6
        local.get 5
        i32.add
        local.set 7
        block  ;; label = @3
          local.get 5
          local.get 2
          i32.lt_u
          br_if 0 (;@3;)
          local.get 5
          local.get 2
          i32.sub
          local.tee 1
          i32.const 16
          i32.lt_u
          br_if 2 (;@1;)
          local.get 3
          local.get 2
          local.get 4
          i32.const 1
          i32.and
          i32.or
          i32.const 2
          i32.or
          i32.store
          local.get 6
          local.get 2
          i32.add
          local.tee 2
          local.get 1
          i32.const 3
          i32.or
          i32.store offset=4
          local.get 7
          local.get 7
          i32.load offset=4
          i32.const 1
          i32.or
          i32.store offset=4
          local.get 2
          local.get 1
          call $dispose_chunk
          local.get 0
          return
        end
        block  ;; label = @3
          i32.const 0
          i32.load offset=4028
          local.get 7
          i32.ne
          br_if 0 (;@3;)
          i32.const 0
          i32.load offset=4016
          local.get 5
          i32.add
          local.tee 5
          local.get 2
          i32.le_u
          br_if 1 (;@2;)
          local.get 3
          local.get 2
          local.get 4
          i32.const 1
          i32.and
          i32.or
          i32.const 2
          i32.or
          i32.store
          i32.const 0
          local.get 6
          local.get 2
          i32.add
          local.tee 1
          i32.store offset=4028
          i32.const 0
          local.get 5
          local.get 2
          i32.sub
          local.tee 2
          i32.store offset=4016
          local.get 1
          local.get 2
          i32.const 1
          i32.or
          i32.store offset=4
          local.get 0
          return
        end
        block  ;; label = @3
          i32.const 0
          i32.load offset=4024
          local.get 7
          i32.ne
          br_if 0 (;@3;)
          i32.const 0
          i32.load offset=4012
          local.get 5
          i32.add
          local.tee 5
          local.get 2
          i32.lt_u
          br_if 1 (;@2;)
          block  ;; label = @4
            block  ;; label = @5
              local.get 5
              local.get 2
              i32.sub
              local.tee 1
              i32.const 16
              i32.lt_u
              br_if 0 (;@5;)
              local.get 3
              local.get 2
              local.get 4
              i32.const 1
              i32.and
              i32.or
              i32.const 2
              i32.or
              i32.store
              local.get 6
              local.get 2
              i32.add
              local.tee 2
              local.get 1
              i32.const 1
              i32.or
              i32.store offset=4
              local.get 6
              local.get 5
              i32.add
              local.tee 5
              local.get 1
              i32.store
              local.get 5
              local.get 5
              i32.load offset=4
              i32.const -2
              i32.and
              i32.store offset=4
              br 1 (;@4;)
            end
            local.get 3
            local.get 4
            i32.const 1
            i32.and
            local.get 5
            i32.or
            i32.const 2
            i32.or
            i32.store
            local.get 6
            local.get 5
            i32.add
            local.tee 1
            local.get 1
            i32.load offset=4
            i32.const 1
            i32.or
            i32.store offset=4
            i32.const 0
            local.set 1
            i32.const 0
            local.set 2
          end
          i32.const 0
          local.get 2
          i32.store offset=4024
          i32.const 0
          local.get 1
          i32.store offset=4012
          local.get 0
          return
        end
        local.get 7
        i32.load offset=4
        local.tee 8
        i32.const 2
        i32.and
        br_if 0 (;@2;)
        local.get 8
        i32.const -8
        i32.and
        local.get 5
        i32.add
        local.tee 9
        local.get 2
        i32.lt_u
        br_if 0 (;@2;)
        local.get 9
        local.get 2
        i32.sub
        local.set 10
        block  ;; label = @3
          block  ;; label = @4
            local.get 8
            i32.const 255
            i32.gt_u
            br_if 0 (;@4;)
            local.get 7
            i32.load offset=8
            local.tee 1
            local.get 8
            i32.const 3
            i32.shr_u
            local.tee 11
            i32.const 3
            i32.shl
            i32.const 4044
            i32.add
            local.tee 8
            i32.eq
            drop
            block  ;; label = @5
              local.get 7
              i32.load offset=12
              local.tee 5
              local.get 1
              i32.ne
              br_if 0 (;@5;)
              i32.const 0
              i32.const 0
              i32.load offset=4004
              i32.const -2
              local.get 11
              i32.rotl
              i32.and
              i32.store offset=4004
              br 2 (;@3;)
            end
            local.get 5
            local.get 8
            i32.eq
            drop
            local.get 5
            local.get 1
            i32.store offset=8
            local.get 1
            local.get 5
            i32.store offset=12
            br 1 (;@3;)
          end
          local.get 7
          i32.load offset=24
          local.set 12
          block  ;; label = @4
            block  ;; label = @5
              local.get 7
              i32.load offset=12
              local.tee 8
              local.get 7
              i32.eq
              br_if 0 (;@5;)
              i32.const 0
              i32.load offset=4020
              local.get 7
              i32.load offset=8
              local.tee 1
              i32.gt_u
              drop
              local.get 8
              local.get 1
              i32.store offset=8
              local.get 1
              local.get 8
              i32.store offset=12
              br 1 (;@4;)
            end
            block  ;; label = @5
              local.get 7
              i32.const 20
              i32.add
              local.tee 1
              i32.load
              local.tee 5
              br_if 0 (;@5;)
              local.get 7
              i32.const 16
              i32.add
              local.tee 1
              i32.load
              local.tee 5
              br_if 0 (;@5;)
              i32.const 0
              local.set 8
              br 1 (;@4;)
            end
            loop  ;; label = @5
              local.get 1
              local.set 11
              local.get 5
              local.tee 8
              i32.const 20
              i32.add
              local.tee 1
              i32.load
              local.tee 5
              br_if 0 (;@5;)
              local.get 8
              i32.const 16
              i32.add
              local.set 1
              local.get 8
              i32.load offset=16
              local.tee 5
              br_if 0 (;@5;)
            end
            local.get 11
            i32.const 0
            i32.store
          end
          local.get 12
          i32.eqz
          br_if 0 (;@3;)
          block  ;; label = @4
            block  ;; label = @5
              local.get 7
              i32.load offset=28
              local.tee 5
              i32.const 2
              i32.shl
              i32.const 4308
              i32.add
              local.tee 1
              i32.load
              local.get 7
              i32.ne
              br_if 0 (;@5;)
              local.get 1
              local.get 8
              i32.store
              local.get 8
              br_if 1 (;@4;)
              i32.const 0
              i32.const 0
              i32.load offset=4008
              i32.const -2
              local.get 5
              i32.rotl
              i32.and
              i32.store offset=4008
              br 2 (;@3;)
            end
            local.get 12
            i32.const 16
            i32.const 20
            local.get 12
            i32.load offset=16
            local.get 7
            i32.eq
            select
            i32.add
            local.get 8
            i32.store
            local.get 8
            i32.eqz
            br_if 1 (;@3;)
          end
          local.get 8
          local.get 12
          i32.store offset=24
          block  ;; label = @4
            local.get 7
            i32.load offset=16
            local.tee 1
            i32.eqz
            br_if 0 (;@4;)
            local.get 8
            local.get 1
            i32.store offset=16
            local.get 1
            local.get 8
            i32.store offset=24
          end
          local.get 7
          i32.load offset=20
          local.tee 1
          i32.eqz
          br_if 0 (;@3;)
          local.get 8
          i32.const 20
          i32.add
          local.get 1
          i32.store
          local.get 1
          local.get 8
          i32.store offset=24
        end
        block  ;; label = @3
          local.get 10
          i32.const 15
          i32.gt_u
          br_if 0 (;@3;)
          local.get 3
          local.get 4
          i32.const 1
          i32.and
          local.get 9
          i32.or
          i32.const 2
          i32.or
          i32.store
          local.get 6
          local.get 9
          i32.add
          local.tee 1
          local.get 1
          i32.load offset=4
          i32.const 1
          i32.or
          i32.store offset=4
          local.get 0
          return
        end
        local.get 3
        local.get 2
        local.get 4
        i32.const 1
        i32.and
        i32.or
        i32.const 2
        i32.or
        i32.store
        local.get 6
        local.get 2
        i32.add
        local.tee 1
        local.get 10
        i32.const 3
        i32.or
        i32.store offset=4
        local.get 6
        local.get 9
        i32.add
        local.tee 2
        local.get 2
        i32.load offset=4
        i32.const 1
        i32.or
        i32.store offset=4
        local.get 1
        local.get 10
        call $dispose_chunk
        local.get 0
        return
      end
      block  ;; label = @2
        local.get 1
        call $dlmalloc
        local.tee 2
        br_if 0 (;@2;)
        i32.const 0
        return
      end
      local.get 2
      local.get 0
      i32.const -4
      i32.const -8
      local.get 3
      i32.load
      local.tee 5
      i32.const 3
      i32.and
      select
      local.get 5
      i32.const -8
      i32.and
      i32.add
      local.tee 5
      local.get 1
      local.get 5
      local.get 1
      i32.lt_u
      select
      call $memcpy
      local.set 1
      local.get 0
      call $dlfree
      local.get 1
      local.set 0
    end
    local.get 0)
  (func $dispose_chunk (type 8) (param i32 i32)
    (local i32 i32 i32 i32 i32 i32)
    local.get 0
    local.get 1
    i32.add
    local.set 2
    block  ;; label = @1
      block  ;; label = @2
        local.get 0
        i32.load offset=4
        local.tee 3
        i32.const 1
        i32.and
        br_if 0 (;@2;)
        local.get 3
        i32.const 3
        i32.and
        i32.eqz
        br_if 1 (;@1;)
        local.get 0
        i32.load
        local.tee 3
        local.get 1
        i32.add
        local.set 1
        block  ;; label = @3
          block  ;; label = @4
            i32.const 0
            i32.load offset=4024
            local.get 0
            local.get 3
            i32.sub
            local.tee 0
            i32.eq
            br_if 0 (;@4;)
            block  ;; label = @5
              local.get 3
              i32.const 255
              i32.gt_u
              br_if 0 (;@5;)
              local.get 0
              i32.load offset=8
              local.tee 4
              local.get 3
              i32.const 3
              i32.shr_u
              local.tee 5
              i32.const 3
              i32.shl
              i32.const 4044
              i32.add
              local.tee 6
              i32.eq
              drop
              local.get 0
              i32.load offset=12
              local.tee 3
              local.get 4
              i32.ne
              br_if 2 (;@3;)
              i32.const 0
              i32.const 0
              i32.load offset=4004
              i32.const -2
              local.get 5
              i32.rotl
              i32.and
              i32.store offset=4004
              br 3 (;@2;)
            end
            local.get 0
            i32.load offset=24
            local.set 7
            block  ;; label = @5
              block  ;; label = @6
                local.get 0
                i32.load offset=12
                local.tee 6
                local.get 0
                i32.eq
                br_if 0 (;@6;)
                i32.const 0
                i32.load offset=4020
                local.get 0
                i32.load offset=8
                local.tee 3
                i32.gt_u
                drop
                local.get 6
                local.get 3
                i32.store offset=8
                local.get 3
                local.get 6
                i32.store offset=12
                br 1 (;@5;)
              end
              block  ;; label = @6
                local.get 0
                i32.const 20
                i32.add
                local.tee 3
                i32.load
                local.tee 4
                br_if 0 (;@6;)
                local.get 0
                i32.const 16
                i32.add
                local.tee 3
                i32.load
                local.tee 4
                br_if 0 (;@6;)
                i32.const 0
                local.set 6
                br 1 (;@5;)
              end
              loop  ;; label = @6
                local.get 3
                local.set 5
                local.get 4
                local.tee 6
                i32.const 20
                i32.add
                local.tee 3
                i32.load
                local.tee 4
                br_if 0 (;@6;)
                local.get 6
                i32.const 16
                i32.add
                local.set 3
                local.get 6
                i32.load offset=16
                local.tee 4
                br_if 0 (;@6;)
              end
              local.get 5
              i32.const 0
              i32.store
            end
            local.get 7
            i32.eqz
            br_if 2 (;@2;)
            block  ;; label = @5
              block  ;; label = @6
                local.get 0
                i32.load offset=28
                local.tee 4
                i32.const 2
                i32.shl
                i32.const 4308
                i32.add
                local.tee 3
                i32.load
                local.get 0
                i32.ne
                br_if 0 (;@6;)
                local.get 3
                local.get 6
                i32.store
                local.get 6
                br_if 1 (;@5;)
                i32.const 0
                i32.const 0
                i32.load offset=4008
                i32.const -2
                local.get 4
                i32.rotl
                i32.and
                i32.store offset=4008
                br 4 (;@2;)
              end
              local.get 7
              i32.const 16
              i32.const 20
              local.get 7
              i32.load offset=16
              local.get 0
              i32.eq
              select
              i32.add
              local.get 6
              i32.store
              local.get 6
              i32.eqz
              br_if 3 (;@2;)
            end
            local.get 6
            local.get 7
            i32.store offset=24
            block  ;; label = @5
              local.get 0
              i32.load offset=16
              local.tee 3
              i32.eqz
              br_if 0 (;@5;)
              local.get 6
              local.get 3
              i32.store offset=16
              local.get 3
              local.get 6
              i32.store offset=24
            end
            local.get 0
            i32.load offset=20
            local.tee 3
            i32.eqz
            br_if 2 (;@2;)
            local.get 6
            i32.const 20
            i32.add
            local.get 3
            i32.store
            local.get 3
            local.get 6
            i32.store offset=24
            br 2 (;@2;)
          end
          local.get 2
          i32.load offset=4
          local.tee 3
          i32.const 3
          i32.and
          i32.const 3
          i32.ne
          br_if 1 (;@2;)
          local.get 2
          local.get 3
          i32.const -2
          i32.and
          i32.store offset=4
          i32.const 0
          local.get 1
          i32.store offset=4012
          local.get 2
          local.get 1
          i32.store
          local.get 0
          local.get 1
          i32.const 1
          i32.or
          i32.store offset=4
          return
        end
        local.get 3
        local.get 6
        i32.eq
        drop
        local.get 3
        local.get 4
        i32.store offset=8
        local.get 4
        local.get 3
        i32.store offset=12
      end
      block  ;; label = @2
        block  ;; label = @3
          local.get 2
          i32.load offset=4
          local.tee 3
          i32.const 2
          i32.and
          br_if 0 (;@3;)
          block  ;; label = @4
            i32.const 0
            i32.load offset=4028
            local.get 2
            i32.ne
            br_if 0 (;@4;)
            i32.const 0
            local.get 0
            i32.store offset=4028
            i32.const 0
            i32.const 0
            i32.load offset=4016
            local.get 1
            i32.add
            local.tee 1
            i32.store offset=4016
            local.get 0
            local.get 1
            i32.const 1
            i32.or
            i32.store offset=4
            local.get 0
            i32.const 0
            i32.load offset=4024
            i32.ne
            br_if 3 (;@1;)
            i32.const 0
            i32.const 0
            i32.store offset=4012
            i32.const 0
            i32.const 0
            i32.store offset=4024
            return
          end
          block  ;; label = @4
            i32.const 0
            i32.load offset=4024
            local.get 2
            i32.ne
            br_if 0 (;@4;)
            i32.const 0
            local.get 0
            i32.store offset=4024
            i32.const 0
            i32.const 0
            i32.load offset=4012
            local.get 1
            i32.add
            local.tee 1
            i32.store offset=4012
            local.get 0
            local.get 1
            i32.const 1
            i32.or
            i32.store offset=4
            local.get 0
            local.get 1
            i32.add
            local.get 1
            i32.store
            return
          end
          local.get 3
          i32.const -8
          i32.and
          local.get 1
          i32.add
          local.set 1
          block  ;; label = @4
            block  ;; label = @5
              local.get 3
              i32.const 255
              i32.gt_u
              br_if 0 (;@5;)
              local.get 2
              i32.load offset=8
              local.tee 4
              local.get 3
              i32.const 3
              i32.shr_u
              local.tee 5
              i32.const 3
              i32.shl
              i32.const 4044
              i32.add
              local.tee 6
              i32.eq
              drop
              block  ;; label = @6
                local.get 2
                i32.load offset=12
                local.tee 3
                local.get 4
                i32.ne
                br_if 0 (;@6;)
                i32.const 0
                i32.const 0
                i32.load offset=4004
                i32.const -2
                local.get 5
                i32.rotl
                i32.and
                i32.store offset=4004
                br 2 (;@4;)
              end
              local.get 3
              local.get 6
              i32.eq
              drop
              local.get 3
              local.get 4
              i32.store offset=8
              local.get 4
              local.get 3
              i32.store offset=12
              br 1 (;@4;)
            end
            local.get 2
            i32.load offset=24
            local.set 7
            block  ;; label = @5
              block  ;; label = @6
                local.get 2
                i32.load offset=12
                local.tee 6
                local.get 2
                i32.eq
                br_if 0 (;@6;)
                i32.const 0
                i32.load offset=4020
                local.get 2
                i32.load offset=8
                local.tee 3
                i32.gt_u
                drop
                local.get 6
                local.get 3
                i32.store offset=8
                local.get 3
                local.get 6
                i32.store offset=12
                br 1 (;@5;)
              end
              block  ;; label = @6
                local.get 2
                i32.const 20
                i32.add
                local.tee 4
                i32.load
                local.tee 3
                br_if 0 (;@6;)
                local.get 2
                i32.const 16
                i32.add
                local.tee 4
                i32.load
                local.tee 3
                br_if 0 (;@6;)
                i32.const 0
                local.set 6
                br 1 (;@5;)
              end
              loop  ;; label = @6
                local.get 4
                local.set 5
                local.get 3
                local.tee 6
                i32.const 20
                i32.add
                local.tee 4
                i32.load
                local.tee 3
                br_if 0 (;@6;)
                local.get 6
                i32.const 16
                i32.add
                local.set 4
                local.get 6
                i32.load offset=16
                local.tee 3
                br_if 0 (;@6;)
              end
              local.get 5
              i32.const 0
              i32.store
            end
            local.get 7
            i32.eqz
            br_if 0 (;@4;)
            block  ;; label = @5
              block  ;; label = @6
                local.get 2
                i32.load offset=28
                local.tee 4
                i32.const 2
                i32.shl
                i32.const 4308
                i32.add
                local.tee 3
                i32.load
                local.get 2
                i32.ne
                br_if 0 (;@6;)
                local.get 3
                local.get 6
                i32.store
                local.get 6
                br_if 1 (;@5;)
                i32.const 0
                i32.const 0
                i32.load offset=4008
                i32.const -2
                local.get 4
                i32.rotl
                i32.and
                i32.store offset=4008
                br 2 (;@4;)
              end
              local.get 7
              i32.const 16
              i32.const 20
              local.get 7
              i32.load offset=16
              local.get 2
              i32.eq
              select
              i32.add
              local.get 6
              i32.store
              local.get 6
              i32.eqz
              br_if 1 (;@4;)
            end
            local.get 6
            local.get 7
            i32.store offset=24
            block  ;; label = @5
              local.get 2
              i32.load offset=16
              local.tee 3
              i32.eqz
              br_if 0 (;@5;)
              local.get 6
              local.get 3
              i32.store offset=16
              local.get 3
              local.get 6
              i32.store offset=24
            end
            local.get 2
            i32.load offset=20
            local.tee 3
            i32.eqz
            br_if 0 (;@4;)
            local.get 6
            i32.const 20
            i32.add
            local.get 3
            i32.store
            local.get 3
            local.get 6
            i32.store offset=24
          end
          local.get 0
          local.get 1
          i32.add
          local.get 1
          i32.store
          local.get 0
          local.get 1
          i32.const 1
          i32.or
          i32.store offset=4
          local.get 0
          i32.const 0
          i32.load offset=4024
          i32.ne
          br_if 1 (;@2;)
          i32.const 0
          local.get 1
          i32.store offset=4012
          return
        end
        local.get 2
        local.get 3
        i32.const -2
        i32.and
        i32.store offset=4
        local.get 0
        local.get 1
        i32.add
        local.get 1
        i32.store
        local.get 0
        local.get 1
        i32.const 1
        i32.or
        i32.store offset=4
      end
      block  ;; label = @2
        local.get 1
        i32.const 255
        i32.gt_u
        br_if 0 (;@2;)
        local.get 1
        i32.const 3
        i32.shr_u
        local.tee 3
        i32.const 3
        i32.shl
        i32.const 4044
        i32.add
        local.set 1
        block  ;; label = @3
          block  ;; label = @4
            i32.const 0
            i32.load offset=4004
            local.tee 4
            i32.const 1
            local.get 3
            i32.shl
            local.tee 3
            i32.and
            br_if 0 (;@4;)
            i32.const 0
            local.get 4
            local.get 3
            i32.or
            i32.store offset=4004
            local.get 1
            local.set 3
            br 1 (;@3;)
          end
          local.get 1
          i32.load offset=8
          local.set 3
        end
        local.get 3
        local.get 0
        i32.store offset=12
        local.get 1
        local.get 0
        i32.store offset=8
        local.get 0
        local.get 1
        i32.store offset=12
        local.get 0
        local.get 3
        i32.store offset=8
        return
      end
      i32.const 31
      local.set 3
      block  ;; label = @2
        local.get 1
        i32.const 16777215
        i32.gt_u
        br_if 0 (;@2;)
        local.get 1
        i32.const 8
        i32.shr_u
        local.tee 3
        local.get 3
        i32.const 1048320
        i32.add
        i32.const 16
        i32.shr_u
        i32.const 8
        i32.and
        local.tee 3
        i32.shl
        local.tee 4
        local.get 4
        i32.const 520192
        i32.add
        i32.const 16
        i32.shr_u
        i32.const 4
        i32.and
        local.tee 4
        i32.shl
        local.tee 6
        local.get 6
        i32.const 245760
        i32.add
        i32.const 16
        i32.shr_u
        i32.const 2
        i32.and
        local.tee 6
        i32.shl
        i32.const 15
        i32.shr_u
        local.get 3
        local.get 4
        i32.or
        local.get 6
        i32.or
        i32.sub
        local.tee 3
        i32.const 1
        i32.shl
        local.get 1
        local.get 3
        i32.const 21
        i32.add
        i32.shr_u
        i32.const 1
        i32.and
        i32.or
        i32.const 28
        i32.add
        local.set 3
      end
      local.get 0
      i64.const 0
      i64.store offset=16 align=4
      local.get 0
      i32.const 28
      i32.add
      local.get 3
      i32.store
      local.get 3
      i32.const 2
      i32.shl
      i32.const 4308
      i32.add
      local.set 4
      block  ;; label = @2
        i32.const 0
        i32.load offset=4008
        local.tee 6
        i32.const 1
        local.get 3
        i32.shl
        local.tee 2
        i32.and
        br_if 0 (;@2;)
        local.get 4
        local.get 0
        i32.store
        i32.const 0
        local.get 6
        local.get 2
        i32.or
        i32.store offset=4008
        local.get 0
        i32.const 24
        i32.add
        local.get 4
        i32.store
        local.get 0
        local.get 0
        i32.store offset=8
        local.get 0
        local.get 0
        i32.store offset=12
        return
      end
      local.get 1
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
      local.set 3
      local.get 4
      i32.load
      local.set 6
      block  ;; label = @2
        loop  ;; label = @3
          local.get 6
          local.tee 4
          i32.load offset=4
          i32.const -8
          i32.and
          local.get 1
          i32.eq
          br_if 1 (;@2;)
          local.get 3
          i32.const 29
          i32.shr_u
          local.set 6
          local.get 3
          i32.const 1
          i32.shl
          local.set 3
          local.get 4
          local.get 6
          i32.const 4
          i32.and
          i32.add
          i32.const 16
          i32.add
          local.tee 2
          i32.load
          local.tee 6
          br_if 0 (;@3;)
        end
        local.get 2
        local.get 0
        i32.store
        local.get 0
        i32.const 24
        i32.add
        local.get 4
        i32.store
        local.get 0
        local.get 0
        i32.store offset=12
        local.get 0
        local.get 0
        i32.store offset=8
        return
      end
      local.get 4
      i32.load offset=8
      local.tee 1
      local.get 0
      i32.store offset=12
      local.get 4
      local.get 0
      i32.store offset=8
      local.get 0
      i32.const 24
      i32.add
      i32.const 0
      i32.store
      local.get 0
      local.get 4
      i32.store offset=12
      local.get 0
      local.get 1
      i32.store offset=8
    end)
  (func $sbrk (type 4) (param i32) (result i32)
    block  ;; label = @1
      local.get 0
      br_if 0 (;@1;)
      memory.size
      i32.const 16
      i32.shl
      return
    end
    block  ;; label = @1
      local.get 0
      i32.const 65535
      i32.and
      br_if 0 (;@1;)
      local.get 0
      i32.const -1
      i32.le_s
      br_if 0 (;@1;)
      block  ;; label = @2
        local.get 0
        i32.const 16
        i32.shr_u
        memory.grow
        local.tee 0
        i32.const -1
        i32.ne
        br_if 0 (;@2;)
        i32.const 0
        i32.const 48
        i32.store offset=4500
        i32.const -1
        return
      end
      local.get 0
      i32.const 16
      i32.shl
      return
    end
    call $abort
    unreachable)
  (func $_Exit (type 0) (param i32)
    local.get 0
    call $__wasi_proc_exit
    unreachable)
  (func $__main_void (type 9) (result i32)
    (local i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 0
    global.set $__stack_pointer
    block  ;; label = @1
      block  ;; label = @2
        block  ;; label = @3
          block  ;; label = @4
            block  ;; label = @5
              local.get 0
              i32.const 8
              i32.add
              local.get 0
              i32.const 12
              i32.add
              call $__wasi_args_sizes_get
              br_if 0 (;@5;)
              local.get 0
              i32.load offset=8
              local.tee 1
              i32.const 1
              i32.add
              local.tee 2
              local.get 1
              i32.lt_u
              br_if 1 (;@4;)
              local.get 0
              i32.load offset=12
              call $malloc
              local.tee 3
              i32.eqz
              br_if 2 (;@3;)
              local.get 2
              i32.const 4
              call $calloc
              local.tee 1
              i32.eqz
              br_if 3 (;@2;)
              local.get 1
              local.get 3
              call $__wasi_args_get
              br_if 4 (;@1;)
              local.get 0
              i32.load offset=8
              local.get 1
              call $main
              local.set 1
              local.get 0
              i32.const 16
              i32.add
              global.set $__stack_pointer
              local.get 1
              return
            end
            i32.const 71
            call $_Exit
            unreachable
          end
          i32.const 70
          call $_Exit
          unreachable
        end
        i32.const 70
        call $_Exit
        unreachable
      end
      local.get 3
      call $free
      i32.const 70
      call $_Exit
      unreachable
    end
    local.get 3
    call $free
    local.get 1
    call $free
    i32.const 71
    call $_Exit
    unreachable)
  (func $abort (type 7)
    unreachable
    unreachable)
  (func $__wasi_args_get (type 3) (param i32 i32) (result i32)
    local.get 0
    local.get 1
    call $__imported_wasi_snapshot_preview1_args_get
    i32.const 65535
    i32.and)
  (func $__wasi_args_sizes_get (type 3) (param i32 i32) (result i32)
    local.get 0
    local.get 1
    call $__imported_wasi_snapshot_preview1_args_sizes_get
    i32.const 65535
    i32.and)
  (func $__wasi_fd_close (type 4) (param i32) (result i32)
    local.get 0
    call $__imported_wasi_snapshot_preview1_fd_close
    i32.const 65535
    i32.and)
  (func $__wasi_fd_fdstat_get (type 3) (param i32 i32) (result i32)
    local.get 0
    local.get 1
    call $__imported_wasi_snapshot_preview1_fd_fdstat_get
    i32.const 65535
    i32.and)
  (func $__wasi_fd_seek (type 5) (param i32 i64 i32 i32) (result i32)
    local.get 0
    local.get 1
    local.get 2
    local.get 3
    call $__imported_wasi_snapshot_preview1_fd_seek
    i32.const 65535
    i32.and)
  (func $__wasi_fd_write (type 6) (param i32 i32 i32 i32) (result i32)
    local.get 0
    local.get 1
    local.get 2
    local.get 3
    call $__imported_wasi_snapshot_preview1_fd_write
    i32.const 65535
    i32.and)
  (func $__wasi_proc_exit (type 0) (param i32)
    local.get 0
    call $__imported_wasi_snapshot_preview1_proc_exit
    unreachable)
  (func $dummy (type 7))
  (func $__wasm_call_dtors (type 7)
    call $dummy
    call $__stdio_exit)
  (func $exit (type 0) (param i32)
    call $dummy
    call $__stdio_exit
    local.get 0
    call $_Exit
    unreachable)
  (func $close (type 4) (param i32) (result i32)
    block  ;; label = @1
      local.get 0
      call $__wasi_fd_close
      local.tee 0
      br_if 0 (;@1;)
      i32.const 0
      return
    end
    i32.const 0
    local.get 0
    i32.store offset=4500
    i32.const -1)
  (func $__stdio_close (type 4) (param i32) (result i32)
    local.get 0
    i32.load offset=56
    call $close)
  (func $printf (type 3) (param i32 i32) (result i32)
    (local i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 2
    global.set $__stack_pointer
    local.get 2
    local.get 1
    i32.store offset=12
    i32.const 3744
    local.get 0
    local.get 1
    call $vfprintf
    local.set 1
    local.get 2
    i32.const 16
    i32.add
    global.set $__stack_pointer
    local.get 1)
  (func $__stdio_exit (type 7)
    (local i32 i32 i32)
    block  ;; label = @1
      call $__ofl_lock
      i32.load
      local.tee 0
      i32.eqz
      br_if 0 (;@1;)
      loop  ;; label = @2
        block  ;; label = @3
          local.get 0
          i32.load offset=20
          local.get 0
          i32.load offset=24
          i32.eq
          br_if 0 (;@3;)
          local.get 0
          i32.const 0
          i32.const 0
          local.get 0
          i32.load offset=32
          call_indirect (type 1)
          drop
        end
        block  ;; label = @3
          local.get 0
          i32.load offset=4
          local.tee 1
          local.get 0
          i32.load offset=8
          local.tee 2
          i32.eq
          br_if 0 (;@3;)
          local.get 0
          local.get 1
          local.get 2
          i32.sub
          i64.extend_i32_s
          i32.const 1
          local.get 0
          i32.load offset=36
          call_indirect (type 2)
          drop
        end
        local.get 0
        i32.load offset=52
        local.tee 0
        br_if 0 (;@2;)
      end
    end
    block  ;; label = @1
      i32.const 0
      i32.load offset=5544
      local.tee 0
      i32.eqz
      br_if 0 (;@1;)
      block  ;; label = @2
        local.get 0
        i32.load offset=20
        local.get 0
        i32.load offset=24
        i32.eq
        br_if 0 (;@2;)
        local.get 0
        i32.const 0
        i32.const 0
        local.get 0
        i32.load offset=32
        call_indirect (type 1)
        drop
      end
      local.get 0
      i32.load offset=4
      local.tee 1
      local.get 0
      i32.load offset=8
      local.tee 2
      i32.eq
      br_if 0 (;@1;)
      local.get 0
      local.get 1
      local.get 2
      i32.sub
      i64.extend_i32_s
      i32.const 1
      local.get 0
      i32.load offset=36
      call_indirect (type 2)
      drop
    end
    block  ;; label = @1
      i32.const 0
      i32.load offset=3856
      local.tee 0
      i32.eqz
      br_if 0 (;@1;)
      block  ;; label = @2
        local.get 0
        i32.load offset=20
        local.get 0
        i32.load offset=24
        i32.eq
        br_if 0 (;@2;)
        local.get 0
        i32.const 0
        i32.const 0
        local.get 0
        i32.load offset=32
        call_indirect (type 1)
        drop
      end
      local.get 0
      i32.load offset=4
      local.tee 1
      local.get 0
      i32.load offset=8
      local.tee 2
      i32.eq
      br_if 0 (;@1;)
      local.get 0
      local.get 1
      local.get 2
      i32.sub
      i64.extend_i32_s
      i32.const 1
      local.get 0
      i32.load offset=36
      call_indirect (type 2)
      drop
    end
    block  ;; label = @1
      i32.const 0
      i32.load offset=3976
      local.tee 0
      i32.eqz
      br_if 0 (;@1;)
      block  ;; label = @2
        local.get 0
        i32.load offset=20
        local.get 0
        i32.load offset=24
        i32.eq
        br_if 0 (;@2;)
        local.get 0
        i32.const 0
        i32.const 0
        local.get 0
        i32.load offset=32
        call_indirect (type 1)
        drop
      end
      local.get 0
      i32.load offset=4
      local.tee 1
      local.get 0
      i32.load offset=8
      local.tee 2
      i32.eq
      br_if 0 (;@1;)
      local.get 0
      local.get 1
      local.get 2
      i32.sub
      i64.extend_i32_s
      i32.const 1
      local.get 0
      i32.load offset=36
      call_indirect (type 2)
      drop
    end)
  (func $__towrite (type 4) (param i32) (result i32)
    (local i32)
    local.get 0
    local.get 0
    i32.load offset=60
    local.tee 1
    i32.const -1
    i32.add
    local.get 1
    i32.or
    i32.store offset=60
    block  ;; label = @1
      local.get 0
      i32.load
      local.tee 1
      i32.const 8
      i32.and
      i32.eqz
      br_if 0 (;@1;)
      local.get 0
      local.get 1
      i32.const 32
      i32.or
      i32.store
      i32.const -1
      return
    end
    local.get 0
    i64.const 0
    i64.store offset=4 align=4
    local.get 0
    local.get 0
    i32.load offset=40
    local.tee 1
    i32.store offset=24
    local.get 0
    local.get 1
    i32.store offset=20
    local.get 0
    local.get 1
    local.get 0
    i32.load offset=44
    i32.add
    i32.store offset=16
    i32.const 0)
  (func $vsnprintf (type 6) (param i32 i32 i32 i32) (result i32)
    (local i32 i32)
    global.get $__stack_pointer
    i32.const 128
    i32.sub
    local.tee 4
    global.set $__stack_pointer
    i32.const -1
    local.set 5
    local.get 4
    local.get 1
    i32.const -1
    i32.add
    i32.const 0
    local.get 1
    select
    i32.store offset=116
    local.get 4
    local.get 0
    local.get 4
    i32.const 126
    i32.add
    local.get 1
    select
    local.tee 0
    i32.store offset=112
    local.get 4
    i32.const 0
    i32.const 112
    call $memset
    local.tee 4
    i32.const -1
    i32.store offset=64
    local.get 4
    i32.const 7
    i32.store offset=32
    local.get 4
    local.get 4
    i32.const 112
    i32.add
    i32.store offset=68
    local.get 4
    local.get 4
    i32.const 127
    i32.add
    i32.store offset=40
    block  ;; label = @1
      block  ;; label = @2
        local.get 1
        i32.const -1
        i32.gt_s
        br_if 0 (;@2;)
        i32.const 0
        i32.const 61
        i32.store offset=4500
        br 1 (;@1;)
      end
      local.get 0
      i32.const 0
      i32.store8
      local.get 4
      local.get 2
      local.get 3
      call $vfprintf
      local.set 5
    end
    local.get 4
    i32.const 128
    i32.add
    global.set $__stack_pointer
    local.get 5)
  (func $sn_write (type 1) (param i32 i32 i32) (result i32)
    (local i32 i32 i32 i32)
    block  ;; label = @1
      local.get 0
      i32.load offset=68
      local.tee 3
      i32.load offset=4
      local.tee 4
      local.get 0
      i32.load offset=20
      local.get 0
      i32.load offset=24
      local.tee 5
      i32.sub
      local.tee 6
      local.get 4
      local.get 6
      i32.lt_u
      select
      local.tee 6
      i32.eqz
      br_if 0 (;@1;)
      local.get 3
      i32.load
      local.get 5
      local.get 6
      call $memcpy
      drop
      local.get 3
      local.get 3
      i32.load
      local.get 6
      i32.add
      i32.store
      local.get 3
      local.get 3
      i32.load offset=4
      local.get 6
      i32.sub
      local.tee 4
      i32.store offset=4
    end
    local.get 3
    i32.load
    local.set 6
    block  ;; label = @1
      local.get 4
      local.get 2
      local.get 4
      local.get 2
      i32.lt_u
      select
      local.tee 4
      i32.eqz
      br_if 0 (;@1;)
      local.get 6
      local.get 1
      local.get 4
      call $memcpy
      drop
      local.get 3
      local.get 3
      i32.load
      local.get 4
      i32.add
      local.tee 6
      i32.store
      local.get 3
      local.get 3
      i32.load offset=4
      local.get 4
      i32.sub
      i32.store offset=4
    end
    local.get 6
    i32.const 0
    i32.store8
    local.get 0
    local.get 0
    i32.load offset=40
    local.tee 3
    i32.store offset=24
    local.get 0
    local.get 3
    i32.store offset=20
    local.get 2)
  (func $vsprintf (type 1) (param i32 i32 i32) (result i32)
    local.get 0
    i32.const 2147483647
    local.get 1
    local.get 2
    call $vsnprintf)
  (func $sprintf (type 1) (param i32 i32 i32) (result i32)
    (local i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 3
    global.set $__stack_pointer
    local.get 3
    local.get 2
    i32.store offset=12
    local.get 0
    local.get 1
    local.get 2
    call $vsprintf
    local.set 2
    local.get 3
    i32.const 16
    i32.add
    global.set $__stack_pointer
    local.get 2)
  (func $__ofl_lock (type 9) (result i32)
    i32.const 5548)
  (func $__lseek (type 2) (param i32 i64 i32) (result i64)
    (local i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 3
    global.set $__stack_pointer
    block  ;; label = @1
      block  ;; label = @2
        local.get 0
        local.get 1
        local.get 2
        i32.const 255
        i32.and
        local.get 3
        i32.const 8
        i32.add
        call $__wasi_fd_seek
        local.tee 0
        i32.eqz
        br_if 0 (;@2;)
        i32.const 0
        i32.const 70
        local.get 0
        local.get 0
        i32.const 76
        i32.eq
        select
        i32.store offset=4500
        i64.const -1
        local.set 1
        br 1 (;@1;)
      end
      local.get 3
      i64.load offset=8
      local.set 1
    end
    local.get 3
    i32.const 16
    i32.add
    global.set $__stack_pointer
    local.get 1)
  (func $__stdio_seek (type 2) (param i32 i64 i32) (result i64)
    local.get 0
    i32.load offset=56
    local.get 1
    local.get 2
    call $__lseek)
  (func $__fwritex (type 1) (param i32 i32 i32) (result i32)
    (local i32 i32 i32 i32 i32 i32)
    block  ;; label = @1
      block  ;; label = @2
        local.get 2
        i32.load offset=16
        local.tee 3
        br_if 0 (;@2;)
        i32.const 0
        local.set 4
        local.get 2
        call $__towrite
        br_if 1 (;@1;)
        local.get 2
        i32.load offset=16
        local.set 3
      end
      block  ;; label = @2
        local.get 3
        local.get 2
        i32.load offset=20
        local.tee 5
        i32.sub
        local.get 1
        i32.ge_u
        br_if 0 (;@2;)
        local.get 2
        local.get 0
        local.get 1
        local.get 2
        i32.load offset=32
        call_indirect (type 1)
        return
      end
      i32.const 0
      local.set 6
      block  ;; label = @2
        block  ;; label = @3
          local.get 2
          i32.load offset=64
          i32.const 0
          i32.ge_s
          br_if 0 (;@3;)
          local.get 1
          local.set 3
          br 1 (;@2;)
        end
        i32.const 0
        local.set 6
        local.get 0
        local.set 4
        i32.const 0
        local.set 3
        loop  ;; label = @3
          block  ;; label = @4
            local.get 1
            local.get 3
            i32.ne
            br_if 0 (;@4;)
            local.get 1
            local.set 3
            br 2 (;@2;)
          end
          local.get 3
          i32.const 1
          i32.add
          local.set 3
          local.get 4
          local.get 1
          i32.add
          local.set 7
          local.get 4
          i32.const -1
          i32.add
          local.tee 8
          local.set 4
          local.get 7
          i32.const -1
          i32.add
          i32.load8_u
          i32.const 10
          i32.ne
          br_if 0 (;@3;)
        end
        local.get 2
        local.get 0
        local.get 1
        local.get 3
        i32.sub
        i32.const 1
        i32.add
        local.tee 6
        local.get 2
        i32.load offset=32
        call_indirect (type 1)
        local.tee 4
        local.get 6
        i32.lt_u
        br_if 1 (;@1;)
        local.get 3
        i32.const -1
        i32.add
        local.set 3
        local.get 8
        local.get 1
        i32.add
        i32.const 1
        i32.add
        local.set 0
        local.get 2
        i32.load offset=20
        local.set 5
      end
      local.get 5
      local.get 0
      local.get 3
      call $memcpy
      drop
      local.get 2
      local.get 2
      i32.load offset=20
      local.get 3
      i32.add
      i32.store offset=20
      local.get 6
      local.get 3
      i32.add
      local.set 4
    end
    local.get 4)
  (func $fwrite (type 6) (param i32 i32 i32 i32) (result i32)
    (local i32 i32 i32 i32 i32 i32 i32)
    local.get 2
    local.get 1
    i32.mul
    local.set 4
    block  ;; label = @1
      block  ;; label = @2
        local.get 3
        i32.load offset=16
        local.tee 5
        br_if 0 (;@2;)
        i32.const 0
        local.set 5
        local.get 3
        call $__towrite
        br_if 1 (;@1;)
        local.get 3
        i32.load offset=16
        local.set 5
      end
      block  ;; label = @2
        local.get 5
        local.get 3
        i32.load offset=20
        local.tee 6
        i32.sub
        local.get 4
        i32.ge_u
        br_if 0 (;@2;)
        local.get 3
        local.get 0
        local.get 4
        local.get 3
        i32.load offset=32
        call_indirect (type 1)
        local.set 5
        br 1 (;@1;)
      end
      i32.const 0
      local.set 7
      block  ;; label = @2
        block  ;; label = @3
          local.get 3
          i32.load offset=64
          i32.const 0
          i32.ge_s
          br_if 0 (;@3;)
          local.get 4
          local.set 5
          br 1 (;@2;)
        end
        local.get 0
        local.get 4
        i32.add
        local.set 8
        i32.const 0
        local.set 7
        i32.const 0
        local.set 5
        loop  ;; label = @3
          block  ;; label = @4
            local.get 4
            local.get 5
            i32.add
            br_if 0 (;@4;)
            local.get 4
            local.set 5
            br 2 (;@2;)
          end
          local.get 8
          local.get 5
          i32.add
          local.set 9
          local.get 5
          i32.const -1
          i32.add
          local.tee 10
          local.set 5
          local.get 9
          i32.const -1
          i32.add
          i32.load8_u
          i32.const 10
          i32.ne
          br_if 0 (;@3;)
        end
        local.get 3
        local.get 0
        local.get 4
        local.get 10
        i32.add
        i32.const 1
        i32.add
        local.tee 7
        local.get 3
        i32.load offset=32
        call_indirect (type 1)
        local.tee 5
        local.get 7
        i32.lt_u
        br_if 1 (;@1;)
        local.get 10
        i32.const -1
        i32.xor
        local.set 5
        local.get 8
        local.get 10
        i32.add
        i32.const 1
        i32.add
        local.set 0
        local.get 3
        i32.load offset=20
        local.set 6
      end
      local.get 6
      local.get 0
      local.get 5
      call $memcpy
      drop
      local.get 3
      local.get 3
      i32.load offset=20
      local.get 5
      i32.add
      i32.store offset=20
      local.get 7
      local.get 5
      i32.add
      local.set 5
    end
    block  ;; label = @1
      local.get 5
      local.get 4
      i32.ne
      br_if 0 (;@1;)
      local.get 2
      i32.const 0
      local.get 1
      select
      return
    end
    local.get 5
    local.get 1
    i32.div_u)
  (func $strerror (type 4) (param i32) (result i32)
    (local i32)
    block  ;; label = @1
      i32.const 0
      i32.load offset=5576
      local.tee 1
      br_if 0 (;@1;)
      i32.const 5552
      local.set 1
      i32.const 0
      i32.const 5552
      i32.store offset=5576
    end
    i32.const 0
    local.get 0
    local.get 0
    i32.const 76
    i32.gt_u
    select
    i32.const 1
    i32.shl
    i32.const 3104
    i32.add
    i32.load16_u
    i32.const 1551
    i32.add
    local.get 1
    i32.load offset=20
    call $__lctrans)
  (func $writev (type 1) (param i32 i32 i32) (result i32)
    (local i32 i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 3
    global.set $__stack_pointer
    i32.const -1
    local.set 4
    block  ;; label = @1
      block  ;; label = @2
        local.get 2
        i32.const -1
        i32.gt_s
        br_if 0 (;@2;)
        i32.const 0
        i32.const 28
        i32.store offset=4500
        br 1 (;@1;)
      end
      block  ;; label = @2
        local.get 0
        local.get 1
        local.get 2
        local.get 3
        i32.const 12
        i32.add
        call $__wasi_fd_write
        local.tee 2
        i32.eqz
        br_if 0 (;@2;)
        i32.const 0
        local.get 2
        i32.store offset=4500
        i32.const -1
        local.set 4
        br 1 (;@1;)
      end
      local.get 3
      i32.load offset=12
      local.set 4
    end
    local.get 3
    i32.const 16
    i32.add
    global.set $__stack_pointer
    local.get 4)
  (func $__stdio_write (type 1) (param i32 i32 i32) (result i32)
    (local i32 i32 i32 i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 3
    global.set $__stack_pointer
    local.get 3
    local.get 2
    i32.store offset=12
    local.get 3
    local.get 1
    i32.store offset=8
    local.get 3
    local.get 0
    i32.load offset=24
    local.tee 1
    i32.store
    local.get 3
    local.get 0
    i32.load offset=20
    local.get 1
    i32.sub
    local.tee 1
    i32.store offset=4
    i32.const 2
    local.set 4
    block  ;; label = @1
      block  ;; label = @2
        local.get 1
        local.get 2
        i32.add
        local.tee 5
        local.get 0
        i32.load offset=56
        local.get 3
        i32.const 2
        call $writev
        local.tee 6
        i32.eq
        br_if 0 (;@2;)
        local.get 3
        local.set 1
        loop  ;; label = @3
          block  ;; label = @4
            local.get 6
            i32.const -1
            i32.gt_s
            br_if 0 (;@4;)
            i32.const 0
            local.set 6
            local.get 0
            i32.const 0
            i32.store offset=24
            local.get 0
            i64.const 0
            i64.store offset=16
            local.get 0
            local.get 0
            i32.load
            i32.const 32
            i32.or
            i32.store
            local.get 4
            i32.const 2
            i32.eq
            br_if 3 (;@1;)
            local.get 2
            local.get 1
            i32.load offset=4
            i32.sub
            local.set 6
            br 3 (;@1;)
          end
          local.get 1
          local.get 6
          local.get 1
          i32.load offset=4
          local.tee 7
          i32.gt_u
          local.tee 8
          i32.const 3
          i32.shl
          i32.add
          local.tee 9
          local.get 9
          i32.load
          local.get 6
          local.get 7
          i32.const 0
          local.get 8
          select
          i32.sub
          local.tee 7
          i32.add
          i32.store
          local.get 1
          i32.const 12
          i32.const 4
          local.get 8
          select
          i32.add
          local.tee 9
          local.get 9
          i32.load
          local.get 7
          i32.sub
          i32.store
          local.get 5
          local.get 6
          i32.sub
          local.tee 5
          local.get 0
          i32.load offset=56
          local.get 1
          i32.const 8
          i32.add
          local.get 1
          local.get 8
          select
          local.tee 1
          local.get 4
          local.get 8
          i32.sub
          local.tee 4
          call $writev
          local.tee 6
          i32.ne
          br_if 0 (;@3;)
        end
      end
      local.get 0
      local.get 0
      i32.load offset=40
      local.tee 1
      i32.store offset=24
      local.get 0
      local.get 1
      i32.store offset=20
      local.get 0
      local.get 1
      local.get 0
      i32.load offset=44
      i32.add
      i32.store offset=16
      local.get 2
      local.set 6
    end
    local.get 3
    i32.const 16
    i32.add
    global.set $__stack_pointer
    local.get 6)
  (func $fputs (type 3) (param i32 i32) (result i32)
    (local i32)
    local.get 0
    call $strlen
    local.set 2
    i32.const -1
    i32.const 0
    local.get 2
    local.get 0
    i32.const 1
    local.get 2
    local.get 1
    call $fwrite
    i32.ne
    select)
  (func $vfprintf (type 1) (param i32 i32 i32) (result i32)
    (local i32 i32 i32)
    global.get $__stack_pointer
    i32.const 208
    i32.sub
    local.tee 3
    global.set $__stack_pointer
    local.get 3
    local.get 2
    i32.store offset=204
    local.get 3
    i32.const 160
    i32.add
    i32.const 32
    i32.add
    i64.const 0
    i64.store
    local.get 3
    i32.const 184
    i32.add
    i64.const 0
    i64.store
    local.get 3
    i32.const 176
    i32.add
    i64.const 0
    i64.store
    local.get 3
    i64.const 0
    i64.store offset=168
    local.get 3
    i64.const 0
    i64.store offset=160
    local.get 3
    local.get 2
    i32.store offset=200
    block  ;; label = @1
      block  ;; label = @2
        i32.const 0
        local.get 1
        local.get 3
        i32.const 200
        i32.add
        local.get 3
        i32.const 80
        i32.add
        local.get 3
        i32.const 160
        i32.add
        call $printf_core
        i32.const 0
        i32.ge_s
        br_if 0 (;@2;)
        i32.const -1
        local.set 0
        br 1 (;@1;)
      end
      local.get 0
      i32.load
      local.set 4
      block  ;; label = @2
        local.get 0
        i32.load offset=60
        i32.const 0
        i32.gt_s
        br_if 0 (;@2;)
        local.get 0
        local.get 4
        i32.const -33
        i32.and
        i32.store
      end
      block  ;; label = @2
        block  ;; label = @3
          block  ;; label = @4
            block  ;; label = @5
              local.get 0
              i32.load offset=44
              br_if 0 (;@5;)
              local.get 0
              i32.const 80
              i32.store offset=44
              local.get 0
              i32.const 0
              i32.store offset=24
              local.get 0
              i64.const 0
              i64.store offset=16
              local.get 0
              i32.load offset=40
              local.set 5
              local.get 0
              local.get 3
              i32.store offset=40
              br 1 (;@4;)
            end
            i32.const 0
            local.set 5
            local.get 0
            i32.load offset=16
            br_if 1 (;@3;)
          end
          i32.const -1
          local.set 2
          local.get 0
          call $__towrite
          br_if 1 (;@2;)
        end
        local.get 0
        local.get 1
        local.get 3
        i32.const 200
        i32.add
        local.get 3
        i32.const 80
        i32.add
        local.get 3
        i32.const 160
        i32.add
        call $printf_core
        local.set 2
      end
      local.get 4
      i32.const 32
      i32.and
      local.set 1
      block  ;; label = @2
        local.get 5
        i32.eqz
        br_if 0 (;@2;)
        local.get 0
        i32.const 0
        i32.const 0
        local.get 0
        i32.load offset=32
        call_indirect (type 1)
        drop
        local.get 0
        i32.const 0
        i32.store offset=44
        local.get 0
        local.get 5
        i32.store offset=40
        local.get 0
        i32.const 0
        i32.store offset=24
        local.get 0
        i32.load offset=20
        local.set 5
        local.get 0
        i64.const 0
        i64.store offset=16
        local.get 2
        i32.const -1
        local.get 5
        select
        local.set 2
      end
      local.get 0
      local.get 0
      i32.load
      local.tee 5
      local.get 1
      i32.or
      i32.store
      i32.const -1
      local.get 2
      local.get 5
      i32.const 32
      i32.and
      select
      local.set 0
    end
    local.get 3
    i32.const 208
    i32.add
    global.set $__stack_pointer
    local.get 0)
  (func $printf_core (type 10) (param i32 i32 i32 i32 i32) (result i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i64 i64 f64 i32 i32 i32 i32 i32 i32 f64)
    global.get $__stack_pointer
    i32.const 880
    i32.sub
    local.tee 5
    global.set $__stack_pointer
    local.get 5
    i32.const 68
    i32.add
    i32.const 12
    i32.add
    local.set 6
    local.get 5
    i32.const 55
    i32.add
    local.set 7
    i32.const -2
    local.get 5
    i32.const 80
    i32.add
    i32.sub
    local.set 8
    local.get 5
    i32.const 68
    i32.add
    i32.const 11
    i32.add
    local.set 9
    local.get 5
    i32.const 80
    i32.add
    i32.const 8
    i32.or
    local.set 10
    local.get 5
    i32.const 80
    i32.add
    i32.const 9
    i32.or
    local.set 11
    i32.const -10
    local.get 5
    i32.const 68
    i32.add
    i32.sub
    local.set 12
    local.get 5
    i32.const 68
    i32.add
    i32.const 10
    i32.add
    local.set 13
    local.get 5
    i32.const 404
    i32.add
    local.set 14
    local.get 5
    i32.const 112
    i32.add
    i32.const 4
    i32.or
    local.set 15
    local.get 5
    i32.const 400
    i32.add
    local.set 16
    local.get 5
    i32.const 56
    i32.add
    local.set 17
    i32.const 0
    local.set 18
    i32.const 0
    local.set 19
    i32.const 0
    local.set 20
    block  ;; label = @1
      block  ;; label = @2
        block  ;; label = @3
          loop  ;; label = @4
            local.get 1
            local.set 21
            local.get 20
            i32.const 2147483647
            local.get 19
            i32.sub
            i32.gt_s
            br_if 1 (;@3;)
            local.get 20
            local.get 19
            i32.add
            local.set 19
            block  ;; label = @5
              block  ;; label = @6
                block  ;; label = @7
                  block  ;; label = @8
                    block  ;; label = @9
                      local.get 21
                      i32.load8_u
                      local.tee 20
                      i32.eqz
                      br_if 0 (;@9;)
                      local.get 21
                      local.set 1
                      loop  ;; label = @10
                        block  ;; label = @11
                          block  ;; label = @12
                            block  ;; label = @13
                              local.get 20
                              i32.const 255
                              i32.and
                              local.tee 20
                              i32.eqz
                              br_if 0 (;@13;)
                              local.get 20
                              i32.const 37
                              i32.ne
                              br_if 2 (;@11;)
                              local.get 1
                              local.set 20
                              loop  ;; label = @14
                                local.get 1
                                i32.const 1
                                i32.add
                                i32.load8_u
                                i32.const 37
                                i32.ne
                                br_if 2 (;@12;)
                                local.get 20
                                i32.const 1
                                i32.add
                                local.set 20
                                local.get 1
                                i32.const 2
                                i32.add
                                local.tee 1
                                i32.load8_u
                                i32.const 37
                                i32.eq
                                br_if 0 (;@14;)
                                br 2 (;@12;)
                              end
                            end
                            local.get 1
                            local.set 20
                          end
                          local.get 20
                          local.get 21
                          i32.sub
                          local.tee 20
                          i32.const 2147483647
                          local.get 19
                          i32.sub
                          local.tee 22
                          i32.gt_s
                          br_if 8 (;@3;)
                          block  ;; label = @12
                            local.get 0
                            i32.eqz
                            br_if 0 (;@12;)
                            local.get 0
                            i32.load8_u
                            i32.const 32
                            i32.and
                            br_if 0 (;@12;)
                            local.get 21
                            local.get 20
                            local.get 0
                            call $__fwritex
                            drop
                          end
                          local.get 20
                          br_if 7 (;@4;)
                          local.get 1
                          i32.const 1
                          i32.add
                          local.set 20
                          i32.const -1
                          local.set 23
                          block  ;; label = @12
                            local.get 1
                            i32.load8_s offset=1
                            local.tee 24
                            i32.const -48
                            i32.add
                            local.tee 25
                            i32.const 9
                            i32.gt_u
                            br_if 0 (;@12;)
                            local.get 1
                            i32.load8_u offset=2
                            i32.const 36
                            i32.ne
                            br_if 0 (;@12;)
                            local.get 1
                            i32.const 3
                            i32.add
                            local.set 20
                            local.get 1
                            i32.load8_s offset=3
                            local.set 24
                            i32.const 1
                            local.set 18
                            local.get 25
                            local.set 23
                          end
                          i32.const 0
                          local.set 26
                          block  ;; label = @12
                            local.get 24
                            i32.const -32
                            i32.add
                            local.tee 1
                            i32.const 31
                            i32.gt_u
                            br_if 0 (;@12;)
                            i32.const 1
                            local.get 1
                            i32.shl
                            local.tee 1
                            i32.const 75913
                            i32.and
                            i32.eqz
                            br_if 0 (;@12;)
                            local.get 20
                            i32.const 1
                            i32.add
                            local.set 25
                            i32.const 0
                            local.set 26
                            loop  ;; label = @13
                              local.get 1
                              local.get 26
                              i32.or
                              local.set 26
                              local.get 25
                              local.tee 20
                              i32.load8_s
                              local.tee 24
                              i32.const -32
                              i32.add
                              local.tee 1
                              i32.const 32
                              i32.ge_u
                              br_if 1 (;@12;)
                              local.get 20
                              i32.const 1
                              i32.add
                              local.set 25
                              i32.const 1
                              local.get 1
                              i32.shl
                              local.tee 1
                              i32.const 75913
                              i32.and
                              br_if 0 (;@13;)
                            end
                          end
                          block  ;; label = @12
                            local.get 24
                            i32.const 42
                            i32.ne
                            br_if 0 (;@12;)
                            block  ;; label = @13
                              block  ;; label = @14
                                local.get 20
                                i32.load8_s offset=1
                                i32.const -48
                                i32.add
                                local.tee 1
                                i32.const 9
                                i32.gt_u
                                br_if 0 (;@14;)
                                local.get 20
                                i32.load8_u offset=2
                                i32.const 36
                                i32.ne
                                br_if 0 (;@14;)
                                local.get 4
                                local.get 1
                                i32.const 2
                                i32.shl
                                i32.add
                                i32.const 10
                                i32.store
                                local.get 20
                                i32.const 3
                                i32.add
                                local.set 25
                                local.get 20
                                i32.load8_s offset=1
                                i32.const 3
                                i32.shl
                                local.get 3
                                i32.add
                                i32.const -384
                                i32.add
                                i32.load
                                local.set 27
                                i32.const 1
                                local.set 18
                                br 1 (;@13;)
                              end
                              local.get 18
                              br_if 6 (;@7;)
                              local.get 20
                              i32.const 1
                              i32.add
                              local.set 25
                              block  ;; label = @14
                                local.get 0
                                br_if 0 (;@14;)
                                i32.const 0
                                local.set 18
                                i32.const 0
                                local.set 27
                                br 6 (;@8;)
                              end
                              local.get 2
                              local.get 2
                              i32.load
                              local.tee 1
                              i32.const 4
                              i32.add
                              i32.store
                              local.get 1
                              i32.load
                              local.set 27
                              i32.const 0
                              local.set 18
                            end
                            local.get 27
                            i32.const -1
                            i32.gt_s
                            br_if 4 (;@8;)
                            i32.const 0
                            local.get 27
                            i32.sub
                            local.set 27
                            local.get 26
                            i32.const 8192
                            i32.or
                            local.set 26
                            br 4 (;@8;)
                          end
                          i32.const 0
                          local.set 27
                          block  ;; label = @12
                            local.get 24
                            i32.const -48
                            i32.add
                            local.tee 1
                            i32.const 9
                            i32.le_u
                            br_if 0 (;@12;)
                            local.get 20
                            local.set 25
                            br 4 (;@8;)
                          end
                          i32.const 0
                          local.set 27
                          loop  ;; label = @12
                            block  ;; label = @13
                              local.get 27
                              i32.const 214748364
                              i32.gt_u
                              br_if 0 (;@13;)
                              i32.const -1
                              local.get 27
                              i32.const 10
                              i32.mul
                              local.tee 25
                              local.get 1
                              i32.add
                              local.get 1
                              i32.const 2147483647
                              local.get 25
                              i32.sub
                              i32.gt_u
                              select
                              local.set 27
                              local.get 20
                              i32.load8_s offset=1
                              local.set 1
                              local.get 20
                              i32.const 1
                              i32.add
                              local.tee 25
                              local.set 20
                              local.get 1
                              i32.const -48
                              i32.add
                              local.tee 1
                              i32.const 10
                              i32.lt_u
                              br_if 1 (;@12;)
                              local.get 27
                              i32.const 0
                              i32.lt_s
                              br_if 10 (;@3;)
                              br 5 (;@8;)
                            end
                            local.get 20
                            i32.load8_s offset=1
                            local.set 1
                            i32.const -1
                            local.set 27
                            local.get 20
                            i32.const 1
                            i32.add
                            local.set 20
                            local.get 1
                            i32.const -48
                            i32.add
                            local.tee 1
                            i32.const 10
                            i32.lt_u
                            br_if 0 (;@12;)
                            br 9 (;@3;)
                          end
                        end
                        local.get 1
                        i32.const 1
                        i32.add
                        local.tee 1
                        i32.load8_u
                        local.set 20
                        br 0 (;@10;)
                      end
                    end
                    local.get 0
                    br_if 7 (;@1;)
                    block  ;; label = @9
                      local.get 18
                      br_if 0 (;@9;)
                      i32.const 0
                      local.set 19
                      br 8 (;@1;)
                    end
                    block  ;; label = @9
                      block  ;; label = @10
                        local.get 4
                        i32.load offset=4
                        local.tee 1
                        br_if 0 (;@10;)
                        i32.const 1
                        local.set 1
                        br 1 (;@9;)
                      end
                      local.get 3
                      i32.const 8
                      i32.add
                      local.get 1
                      local.get 2
                      call $pop_arg
                      block  ;; label = @10
                        local.get 4
                        i32.load offset=8
                        local.tee 1
                        br_if 0 (;@10;)
                        i32.const 2
                        local.set 1
                        br 1 (;@9;)
                      end
                      local.get 3
                      i32.const 16
                      i32.add
                      local.get 1
                      local.get 2
                      call $pop_arg
                      block  ;; label = @10
                        local.get 4
                        i32.load offset=12
                        local.tee 1
                        br_if 0 (;@10;)
                        i32.const 3
                        local.set 1
                        br 1 (;@9;)
                      end
                      local.get 3
                      i32.const 24
                      i32.add
                      local.get 1
                      local.get 2
                      call $pop_arg
                      block  ;; label = @10
                        local.get 4
                        i32.load offset=16
                        local.tee 1
                        br_if 0 (;@10;)
                        i32.const 4
                        local.set 1
                        br 1 (;@9;)
                      end
                      local.get 3
                      i32.const 32
                      i32.add
                      local.get 1
                      local.get 2
                      call $pop_arg
                      block  ;; label = @10
                        local.get 4
                        i32.load offset=20
                        local.tee 1
                        br_if 0 (;@10;)
                        i32.const 5
                        local.set 1
                        br 1 (;@9;)
                      end
                      local.get 3
                      i32.const 40
                      i32.add
                      local.get 1
                      local.get 2
                      call $pop_arg
                      block  ;; label = @10
                        local.get 4
                        i32.load offset=24
                        local.tee 1
                        br_if 0 (;@10;)
                        i32.const 6
                        local.set 1
                        br 1 (;@9;)
                      end
                      local.get 3
                      i32.const 48
                      i32.add
                      local.get 1
                      local.get 2
                      call $pop_arg
                      block  ;; label = @10
                        local.get 4
                        i32.load offset=28
                        local.tee 1
                        br_if 0 (;@10;)
                        i32.const 7
                        local.set 1
                        br 1 (;@9;)
                      end
                      local.get 3
                      i32.const 56
                      i32.add
                      local.get 1
                      local.get 2
                      call $pop_arg
                      block  ;; label = @10
                        local.get 4
                        i32.load offset=32
                        local.tee 1
                        br_if 0 (;@10;)
                        i32.const 8
                        local.set 1
                        br 1 (;@9;)
                      end
                      local.get 3
                      i32.const 64
                      i32.add
                      local.get 1
                      local.get 2
                      call $pop_arg
                      block  ;; label = @10
                        local.get 4
                        i32.load offset=36
                        local.tee 1
                        br_if 0 (;@10;)
                        i32.const 9
                        local.set 1
                        br 1 (;@9;)
                      end
                      local.get 3
                      i32.const 72
                      i32.add
                      local.get 1
                      local.get 2
                      call $pop_arg
                      i32.const 1
                      local.set 19
                      br 8 (;@1;)
                    end
                    local.get 1
                    i32.const 2
                    i32.shl
                    local.set 1
                    loop  ;; label = @9
                      local.get 4
                      local.get 1
                      i32.add
                      i32.load
                      br_if 2 (;@7;)
                      local.get 1
                      i32.const 4
                      i32.add
                      local.tee 1
                      i32.const 40
                      i32.ne
                      br_if 0 (;@9;)
                    end
                    i32.const 1
                    local.set 19
                    br 7 (;@1;)
                  end
                  i32.const 0
                  local.set 20
                  i32.const -1
                  local.set 24
                  block  ;; label = @8
                    block  ;; label = @9
                      local.get 25
                      i32.load8_u
                      i32.const 46
                      i32.eq
                      br_if 0 (;@9;)
                      local.get 25
                      local.set 1
                      i32.const 0
                      local.set 28
                      br 1 (;@8;)
                    end
                    block  ;; label = @9
                      local.get 25
                      i32.load8_s offset=1
                      local.tee 24
                      i32.const 42
                      i32.ne
                      br_if 0 (;@9;)
                      block  ;; label = @10
                        block  ;; label = @11
                          local.get 25
                          i32.load8_s offset=2
                          i32.const -48
                          i32.add
                          local.tee 1
                          i32.const 9
                          i32.gt_u
                          br_if 0 (;@11;)
                          local.get 25
                          i32.load8_u offset=3
                          i32.const 36
                          i32.ne
                          br_if 0 (;@11;)
                          local.get 4
                          local.get 1
                          i32.const 2
                          i32.shl
                          i32.add
                          i32.const 10
                          i32.store
                          local.get 25
                          i32.const 4
                          i32.add
                          local.set 1
                          local.get 25
                          i32.load8_s offset=2
                          i32.const 3
                          i32.shl
                          local.get 3
                          i32.add
                          i32.const -384
                          i32.add
                          i32.load
                          local.set 24
                          br 1 (;@10;)
                        end
                        local.get 18
                        br_if 3 (;@7;)
                        local.get 25
                        i32.const 2
                        i32.add
                        local.set 1
                        block  ;; label = @11
                          local.get 0
                          br_if 0 (;@11;)
                          i32.const 0
                          local.set 24
                          br 1 (;@10;)
                        end
                        local.get 2
                        local.get 2
                        i32.load
                        local.tee 25
                        i32.const 4
                        i32.add
                        i32.store
                        local.get 25
                        i32.load
                        local.set 24
                      end
                      local.get 24
                      i32.const -1
                      i32.xor
                      i32.const 31
                      i32.shr_u
                      local.set 28
                      br 1 (;@8;)
                    end
                    local.get 25
                    i32.const 1
                    i32.add
                    local.set 1
                    block  ;; label = @9
                      local.get 24
                      i32.const -48
                      i32.add
                      local.tee 29
                      i32.const 9
                      i32.le_u
                      br_if 0 (;@9;)
                      i32.const 1
                      local.set 28
                      i32.const 0
                      local.set 24
                      br 1 (;@8;)
                    end
                    i32.const 0
                    local.set 25
                    loop  ;; label = @9
                      i32.const -1
                      local.set 24
                      block  ;; label = @10
                        local.get 25
                        i32.const 214748364
                        i32.gt_u
                        br_if 0 (;@10;)
                        i32.const -1
                        local.get 25
                        i32.const 10
                        i32.mul
                        local.tee 25
                        local.get 29
                        i32.add
                        local.get 29
                        i32.const 2147483647
                        local.get 25
                        i32.sub
                        i32.gt_u
                        select
                        local.set 24
                      end
                      i32.const 1
                      local.set 28
                      local.get 24
                      local.set 25
                      local.get 1
                      i32.const 1
                      i32.add
                      local.tee 1
                      i32.load8_s
                      i32.const -48
                      i32.add
                      local.tee 29
                      i32.const 10
                      i32.lt_u
                      br_if 0 (;@9;)
                    end
                  end
                  loop  ;; label = @8
                    local.get 20
                    local.set 25
                    local.get 1
                    i32.load8_s
                    local.tee 20
                    i32.const -123
                    i32.add
                    i32.const -58
                    i32.lt_u
                    br_if 1 (;@7;)
                    local.get 1
                    i32.const 1
                    i32.add
                    local.set 1
                    local.get 20
                    local.get 25
                    i32.const 58
                    i32.mul
                    i32.add
                    i32.const 3199
                    i32.add
                    i32.load8_u
                    local.tee 20
                    i32.const -1
                    i32.add
                    i32.const 8
                    i32.lt_u
                    br_if 0 (;@8;)
                  end
                  block  ;; label = @8
                    block  ;; label = @9
                      block  ;; label = @10
                        local.get 20
                        i32.const 27
                        i32.eq
                        br_if 0 (;@10;)
                        local.get 20
                        i32.eqz
                        br_if 3 (;@7;)
                        block  ;; label = @11
                          local.get 23
                          i32.const 0
                          i32.lt_s
                          br_if 0 (;@11;)
                          local.get 4
                          local.get 23
                          i32.const 2
                          i32.shl
                          i32.add
                          local.get 20
                          i32.store
                          local.get 5
                          local.get 3
                          local.get 23
                          i32.const 3
                          i32.shl
                          i32.add
                          i64.load
                          i64.store offset=56
                          br 2 (;@9;)
                        end
                        block  ;; label = @11
                          local.get 0
                          br_if 0 (;@11;)
                          i32.const 0
                          local.set 19
                          br 10 (;@1;)
                        end
                        local.get 5
                        i32.const 56
                        i32.add
                        local.get 20
                        local.get 2
                        call $pop_arg
                        br 2 (;@8;)
                      end
                      local.get 23
                      i32.const -1
                      i32.gt_s
                      br_if 2 (;@7;)
                    end
                    i32.const 0
                    local.set 20
                    local.get 0
                    i32.eqz
                    br_if 4 (;@4;)
                  end
                  local.get 26
                  i32.const -65537
                  i32.and
                  local.tee 29
                  local.get 26
                  local.get 26
                  i32.const 8192
                  i32.and
                  select
                  local.set 30
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
                                                block  ;; label = @23
                                                  block  ;; label = @24
                                                    block  ;; label = @25
                                                      block  ;; label = @26
                                                        local.get 1
                                                        i32.const -1
                                                        i32.add
                                                        i32.load8_s
                                                        local.tee 20
                                                        i32.const -33
                                                        i32.and
                                                        local.get 20
                                                        local.get 20
                                                        i32.const 15
                                                        i32.and
                                                        i32.const 3
                                                        i32.eq
                                                        select
                                                        local.get 20
                                                        local.get 25
                                                        select
                                                        local.tee 31
                                                        i32.const -65
                                                        i32.add
                                                        br_table 16 (;@10;) 17 (;@9;) 13 (;@13;) 17 (;@9;) 16 (;@10;) 16 (;@10;) 16 (;@10;) 17 (;@9;) 17 (;@9;) 17 (;@9;) 17 (;@9;) 17 (;@9;) 17 (;@9;) 17 (;@9;) 17 (;@9;) 17 (;@9;) 17 (;@9;) 17 (;@9;) 12 (;@14;) 17 (;@9;) 17 (;@9;) 17 (;@9;) 17 (;@9;) 3 (;@23;) 17 (;@9;) 17 (;@9;) 17 (;@9;) 17 (;@9;) 17 (;@9;) 17 (;@9;) 17 (;@9;) 17 (;@9;) 16 (;@10;) 17 (;@9;) 8 (;@18;) 5 (;@21;) 16 (;@10;) 16 (;@10;) 16 (;@10;) 17 (;@9;) 5 (;@21;) 17 (;@9;) 17 (;@9;) 17 (;@9;) 9 (;@17;) 1 (;@25;) 4 (;@22;) 2 (;@24;) 17 (;@9;) 17 (;@9;) 10 (;@16;) 17 (;@9;) 0 (;@26;) 17 (;@9;) 17 (;@9;) 3 (;@23;) 17 (;@9;)
                                                      end
                                                      i32.const 0
                                                      local.set 23
                                                      i32.const 1038
                                                      local.set 32
                                                      local.get 5
                                                      i64.load offset=56
                                                      local.set 33
                                                      br 5 (;@20;)
                                                    end
                                                    i32.const 0
                                                    local.set 20
                                                    block  ;; label = @25
                                                      block  ;; label = @26
                                                        block  ;; label = @27
                                                          block  ;; label = @28
                                                            block  ;; label = @29
                                                              block  ;; label = @30
                                                                block  ;; label = @31
                                                                  local.get 25
                                                                  i32.const 255
                                                                  i32.and
                                                                  br_table 0 (;@31;) 1 (;@30;) 2 (;@29;) 3 (;@28;) 4 (;@27;) 27 (;@4;) 5 (;@26;) 6 (;@25;) 27 (;@4;)
                                                                end
                                                                local.get 5
                                                                i32.load offset=56
                                                                local.get 19
                                                                i32.store
                                                                br 26 (;@4;)
                                                              end
                                                              local.get 5
                                                              i32.load offset=56
                                                              local.get 19
                                                              i32.store
                                                              br 25 (;@4;)
                                                            end
                                                            local.get 5
                                                            i32.load offset=56
                                                            local.get 19
                                                            i64.extend_i32_s
                                                            i64.store
                                                            br 24 (;@4;)
                                                          end
                                                          local.get 5
                                                          i32.load offset=56
                                                          local.get 19
                                                          i32.store16
                                                          br 23 (;@4;)
                                                        end
                                                        local.get 5
                                                        i32.load offset=56
                                                        local.get 19
                                                        i32.store8
                                                        br 22 (;@4;)
                                                      end
                                                      local.get 5
                                                      i32.load offset=56
                                                      local.get 19
                                                      i32.store
                                                      br 21 (;@4;)
                                                    end
                                                    local.get 5
                                                    i32.load offset=56
                                                    local.get 19
                                                    i64.extend_i32_s
                                                    i64.store
                                                    br 20 (;@4;)
                                                  end
                                                  local.get 24
                                                  i32.const 8
                                                  local.get 24
                                                  i32.const 8
                                                  i32.gt_u
                                                  select
                                                  local.set 24
                                                  local.get 30
                                                  i32.const 8
                                                  i32.or
                                                  local.set 30
                                                  i32.const 120
                                                  local.set 31
                                                end
                                                i32.const 0
                                                local.set 23
                                                i32.const 1038
                                                local.set 32
                                                block  ;; label = @23
                                                  local.get 5
                                                  i64.load offset=56
                                                  local.tee 33
                                                  i64.eqz
                                                  i32.eqz
                                                  br_if 0 (;@23;)
                                                  local.get 17
                                                  local.set 21
                                                  br 4 (;@19;)
                                                end
                                                local.get 31
                                                i32.const 32
                                                i32.and
                                                local.set 25
                                                local.get 17
                                                local.set 21
                                                loop  ;; label = @23
                                                  local.get 21
                                                  i32.const -1
                                                  i32.add
                                                  local.tee 21
                                                  local.get 33
                                                  i32.wrap_i64
                                                  i32.const 15
                                                  i32.and
                                                  i32.const 3728
                                                  i32.add
                                                  i32.load8_u
                                                  local.get 25
                                                  i32.or
                                                  i32.store8
                                                  local.get 33
                                                  i64.const 15
                                                  i64.gt_u
                                                  local.set 20
                                                  local.get 33
                                                  i64.const 4
                                                  i64.shr_u
                                                  local.set 33
                                                  local.get 20
                                                  br_if 0 (;@23;)
                                                end
                                                local.get 30
                                                i32.const 8
                                                i32.and
                                                i32.eqz
                                                br_if 3 (;@19;)
                                                local.get 31
                                                i32.const 4
                                                i32.shr_s
                                                i32.const 1038
                                                i32.add
                                                local.set 32
                                                i32.const 2
                                                local.set 23
                                                br 3 (;@19;)
                                              end
                                              local.get 17
                                              local.set 21
                                              block  ;; label = @22
                                                local.get 5
                                                i64.load offset=56
                                                local.tee 33
                                                i64.eqz
                                                br_if 0 (;@22;)
                                                local.get 17
                                                local.set 21
                                                loop  ;; label = @23
                                                  local.get 21
                                                  i32.const -1
                                                  i32.add
                                                  local.tee 21
                                                  local.get 33
                                                  i32.wrap_i64
                                                  i32.const 7
                                                  i32.and
                                                  i32.const 48
                                                  i32.or
                                                  i32.store8
                                                  local.get 33
                                                  i64.const 7
                                                  i64.gt_u
                                                  local.set 20
                                                  local.get 33
                                                  i64.const 3
                                                  i64.shr_u
                                                  local.set 33
                                                  local.get 20
                                                  br_if 0 (;@23;)
                                                end
                                              end
                                              i32.const 0
                                              local.set 23
                                              i32.const 1038
                                              local.set 32
                                              local.get 30
                                              i32.const 8
                                              i32.and
                                              i32.eqz
                                              br_if 2 (;@19;)
                                              local.get 24
                                              local.get 17
                                              local.get 21
                                              i32.sub
                                              local.tee 20
                                              i32.const 1
                                              i32.add
                                              local.get 24
                                              local.get 20
                                              i32.gt_s
                                              select
                                              local.set 24
                                              br 2 (;@19;)
                                            end
                                            block  ;; label = @21
                                              local.get 5
                                              i64.load offset=56
                                              local.tee 33
                                              i64.const -1
                                              i64.gt_s
                                              br_if 0 (;@21;)
                                              local.get 5
                                              i64.const 0
                                              local.get 33
                                              i64.sub
                                              local.tee 33
                                              i64.store offset=56
                                              i32.const 1
                                              local.set 23
                                              i32.const 1038
                                              local.set 32
                                              br 1 (;@20;)
                                            end
                                            block  ;; label = @21
                                              local.get 30
                                              i32.const 2048
                                              i32.and
                                              i32.eqz
                                              br_if 0 (;@21;)
                                              i32.const 1
                                              local.set 23
                                              i32.const 1039
                                              local.set 32
                                              br 1 (;@20;)
                                            end
                                            i32.const 1040
                                            i32.const 1038
                                            local.get 30
                                            i32.const 1
                                            i32.and
                                            local.tee 23
                                            select
                                            local.set 32
                                          end
                                          block  ;; label = @20
                                            block  ;; label = @21
                                              local.get 33
                                              i64.const 4294967296
                                              i64.ge_u
                                              br_if 0 (;@21;)
                                              local.get 33
                                              local.set 34
                                              local.get 17
                                              local.set 21
                                              br 1 (;@20;)
                                            end
                                            local.get 17
                                            local.set 21
                                            loop  ;; label = @21
                                              local.get 21
                                              i32.const -1
                                              i32.add
                                              local.tee 21
                                              local.get 33
                                              local.get 33
                                              i64.const 10
                                              i64.div_u
                                              local.tee 34
                                              i64.const 10
                                              i64.mul
                                              i64.sub
                                              i32.wrap_i64
                                              i32.const 48
                                              i32.or
                                              i32.store8
                                              local.get 33
                                              i64.const 42949672959
                                              i64.gt_u
                                              local.set 20
                                              local.get 34
                                              local.set 33
                                              local.get 20
                                              br_if 0 (;@21;)
                                            end
                                          end
                                          local.get 34
                                          i32.wrap_i64
                                          local.tee 20
                                          i32.eqz
                                          br_if 0 (;@19;)
                                          loop  ;; label = @20
                                            local.get 21
                                            i32.const -1
                                            i32.add
                                            local.tee 21
                                            local.get 20
                                            local.get 20
                                            i32.const 10
                                            i32.div_u
                                            local.tee 25
                                            i32.const 10
                                            i32.mul
                                            i32.sub
                                            i32.const 48
                                            i32.or
                                            i32.store8
                                            local.get 20
                                            i32.const 9
                                            i32.gt_u
                                            local.set 26
                                            local.get 25
                                            local.set 20
                                            local.get 26
                                            br_if 0 (;@20;)
                                          end
                                        end
                                        block  ;; label = @19
                                          local.get 28
                                          i32.eqz
                                          br_if 0 (;@19;)
                                          local.get 24
                                          i32.const 0
                                          i32.lt_s
                                          br_if 16 (;@3;)
                                        end
                                        local.get 30
                                        i32.const -65537
                                        i32.and
                                        local.get 30
                                        local.get 28
                                        select
                                        local.set 29
                                        block  ;; label = @19
                                          local.get 5
                                          i64.load offset=56
                                          local.tee 33
                                          i64.const 0
                                          i64.ne
                                          br_if 0 (;@19;)
                                          i32.const 0
                                          local.set 26
                                          local.get 24
                                          br_if 0 (;@19;)
                                          local.get 17
                                          local.set 21
                                          local.get 17
                                          local.set 20
                                          br 11 (;@8;)
                                        end
                                        local.get 24
                                        local.get 17
                                        local.get 21
                                        i32.sub
                                        local.get 33
                                        i64.eqz
                                        i32.add
                                        local.tee 20
                                        local.get 24
                                        local.get 20
                                        i32.gt_s
                                        select
                                        local.set 26
                                        local.get 17
                                        local.set 20
                                        br 10 (;@8;)
                                      end
                                      local.get 5
                                      local.get 5
                                      i64.load offset=56
                                      i64.store8 offset=55
                                      i32.const 0
                                      local.set 23
                                      i32.const 1038
                                      local.set 32
                                      i32.const 1
                                      local.set 26
                                      local.get 7
                                      local.set 21
                                      local.get 17
                                      local.set 20
                                      br 9 (;@8;)
                                    end
                                    i32.const 0
                                    i32.load offset=4500
                                    call $strerror
                                    local.set 21
                                    br 1 (;@15;)
                                  end
                                  local.get 5
                                  i32.load offset=56
                                  local.tee 20
                                  i32.const 1279
                                  local.get 20
                                  select
                                  local.set 21
                                end
                                i32.const 0
                                local.set 23
                                local.get 21
                                local.get 21
                                i32.const 2147483647
                                local.get 24
                                local.get 24
                                i32.const 0
                                i32.lt_s
                                select
                                call $strnlen
                                local.tee 26
                                i32.add
                                local.set 20
                                i32.const 1038
                                local.set 32
                                local.get 24
                                i32.const -1
                                i32.gt_s
                                br_if 6 (;@8;)
                                local.get 20
                                i32.load8_u
                                i32.eqz
                                br_if 6 (;@8;)
                                br 11 (;@3;)
                              end
                              local.get 5
                              i32.load offset=56
                              local.set 25
                              local.get 24
                              br_if 1 (;@12;)
                              i32.const 0
                              local.set 20
                              br 2 (;@11;)
                            end
                            local.get 5
                            i32.const 0
                            i32.store offset=12
                            local.get 5
                            local.get 5
                            i64.load offset=56
                            i64.store32 offset=8
                            local.get 5
                            local.get 5
                            i32.const 8
                            i32.add
                            i32.store offset=56
                            i32.const -1
                            local.set 24
                            local.get 5
                            i32.const 8
                            i32.add
                            local.set 25
                          end
                          i32.const 0
                          local.set 20
                          local.get 25
                          local.set 21
                          block  ;; label = @12
                            loop  ;; label = @13
                              local.get 21
                              i32.load
                              local.tee 22
                              i32.eqz
                              br_if 1 (;@12;)
                              block  ;; label = @14
                                local.get 5
                                i32.const 4
                                i32.add
                                local.get 22
                                call $wctomb
                                local.tee 22
                                i32.const 0
                                i32.lt_s
                                local.tee 26
                                br_if 0 (;@14;)
                                local.get 22
                                local.get 24
                                local.get 20
                                i32.sub
                                i32.gt_u
                                br_if 0 (;@14;)
                                local.get 21
                                i32.const 4
                                i32.add
                                local.set 21
                                local.get 24
                                local.get 22
                                local.get 20
                                i32.add
                                local.tee 20
                                i32.gt_u
                                br_if 1 (;@13;)
                                br 2 (;@12;)
                              end
                            end
                            local.get 26
                            br_if 10 (;@2;)
                          end
                          local.get 20
                          i32.const 0
                          i32.lt_s
                          br_if 8 (;@3;)
                        end
                        block  ;; label = @11
                          local.get 30
                          i32.const 73728
                          i32.and
                          local.tee 26
                          br_if 0 (;@11;)
                          local.get 27
                          local.get 20
                          i32.le_s
                          br_if 0 (;@11;)
                          local.get 5
                          i32.const 112
                          i32.add
                          i32.const 32
                          local.get 27
                          local.get 20
                          i32.sub
                          local.tee 21
                          i32.const 256
                          local.get 21
                          i32.const 256
                          i32.lt_u
                          local.tee 22
                          select
                          call $memset
                          drop
                          block  ;; label = @12
                            local.get 22
                            br_if 0 (;@12;)
                            loop  ;; label = @13
                              block  ;; label = @14
                                local.get 0
                                i32.load8_u
                                i32.const 32
                                i32.and
                                br_if 0 (;@14;)
                                local.get 5
                                i32.const 112
                                i32.add
                                i32.const 256
                                local.get 0
                                call $__fwritex
                                drop
                              end
                              local.get 21
                              i32.const -256
                              i32.add
                              local.tee 21
                              i32.const 255
                              i32.gt_u
                              br_if 0 (;@13;)
                            end
                          end
                          local.get 0
                          i32.load8_u
                          i32.const 32
                          i32.and
                          br_if 0 (;@11;)
                          local.get 5
                          i32.const 112
                          i32.add
                          local.get 21
                          local.get 0
                          call $__fwritex
                          drop
                        end
                        block  ;; label = @11
                          local.get 20
                          i32.eqz
                          br_if 0 (;@11;)
                          i32.const 0
                          local.set 21
                          loop  ;; label = @12
                            local.get 25
                            i32.load
                            local.tee 22
                            i32.eqz
                            br_if 1 (;@11;)
                            local.get 5
                            i32.const 4
                            i32.add
                            local.get 22
                            call $wctomb
                            local.tee 22
                            local.get 21
                            i32.add
                            local.tee 21
                            local.get 20
                            i32.gt_u
                            br_if 1 (;@11;)
                            block  ;; label = @13
                              local.get 0
                              i32.load8_u
                              i32.const 32
                              i32.and
                              br_if 0 (;@13;)
                              local.get 5
                              i32.const 4
                              i32.add
                              local.get 22
                              local.get 0
                              call $__fwritex
                              drop
                            end
                            local.get 25
                            i32.const 4
                            i32.add
                            local.set 25
                            local.get 21
                            local.get 20
                            i32.lt_u
                            br_if 0 (;@12;)
                          end
                        end
                        block  ;; label = @11
                          local.get 26
                          i32.const 8192
                          i32.ne
                          br_if 0 (;@11;)
                          local.get 27
                          local.get 20
                          i32.le_s
                          br_if 0 (;@11;)
                          local.get 5
                          i32.const 112
                          i32.add
                          i32.const 32
                          local.get 27
                          local.get 20
                          i32.sub
                          local.tee 21
                          i32.const 256
                          local.get 21
                          i32.const 256
                          i32.lt_u
                          local.tee 22
                          select
                          call $memset
                          drop
                          block  ;; label = @12
                            local.get 22
                            br_if 0 (;@12;)
                            loop  ;; label = @13
                              block  ;; label = @14
                                local.get 0
                                i32.load8_u
                                i32.const 32
                                i32.and
                                br_if 0 (;@14;)
                                local.get 5
                                i32.const 112
                                i32.add
                                i32.const 256
                                local.get 0
                                call $__fwritex
                                drop
                              end
                              local.get 21
                              i32.const -256
                              i32.add
                              local.tee 21
                              i32.const 255
                              i32.gt_u
                              br_if 0 (;@13;)
                            end
                          end
                          local.get 0
                          i32.load8_u
                          i32.const 32
                          i32.and
                          br_if 0 (;@11;)
                          local.get 5
                          i32.const 112
                          i32.add
                          local.get 21
                          local.get 0
                          call $__fwritex
                          drop
                        end
                        local.get 27
                        local.get 20
                        local.get 27
                        local.get 20
                        i32.gt_s
                        select
                        local.set 20
                        br 6 (;@4;)
                      end
                      block  ;; label = @10
                        local.get 28
                        i32.eqz
                        br_if 0 (;@10;)
                        local.get 24
                        i32.const 0
                        i32.lt_s
                        br_if 7 (;@3;)
                      end
                      local.get 5
                      f64.load offset=56
                      local.set 35
                      local.get 5
                      i32.const 0
                      i32.store offset=108
                      block  ;; label = @10
                        block  ;; label = @11
                          local.get 35
                          i64.reinterpret_f64
                          i64.const -1
                          i64.gt_s
                          br_if 0 (;@11;)
                          local.get 35
                          f64.neg
                          local.set 35
                          i32.const 1
                          local.set 36
                          i32.const 0
                          local.set 37
                          i32.const 1048
                          local.set 38
                          br 1 (;@10;)
                        end
                        block  ;; label = @11
                          local.get 30
                          i32.const 2048
                          i32.and
                          i32.eqz
                          br_if 0 (;@11;)
                          i32.const 1
                          local.set 36
                          i32.const 0
                          local.set 37
                          i32.const 1051
                          local.set 38
                          br 1 (;@10;)
                        end
                        i32.const 1054
                        i32.const 1049
                        local.get 30
                        i32.const 1
                        i32.and
                        local.tee 36
                        select
                        local.set 38
                        local.get 36
                        i32.eqz
                        local.set 37
                      end
                      block  ;; label = @10
                        local.get 35
                        f64.abs
                        f64.const inf (;=inf;)
                        f64.lt
                        br_if 0 (;@10;)
                        local.get 36
                        i32.const 3
                        i32.add
                        local.set 21
                        block  ;; label = @11
                          local.get 30
                          i32.const 8192
                          i32.and
                          br_if 0 (;@11;)
                          local.get 27
                          local.get 21
                          i32.le_s
                          br_if 0 (;@11;)
                          local.get 5
                          i32.const 624
                          i32.add
                          i32.const 32
                          local.get 27
                          local.get 21
                          i32.sub
                          local.tee 20
                          i32.const 256
                          local.get 20
                          i32.const 256
                          i32.lt_u
                          local.tee 22
                          select
                          call $memset
                          drop
                          block  ;; label = @12
                            local.get 22
                            br_if 0 (;@12;)
                            loop  ;; label = @13
                              block  ;; label = @14
                                local.get 0
                                i32.load8_u
                                i32.const 32
                                i32.and
                                br_if 0 (;@14;)
                                local.get 5
                                i32.const 624
                                i32.add
                                i32.const 256
                                local.get 0
                                call $__fwritex
                                drop
                              end
                              local.get 20
                              i32.const -256
                              i32.add
                              local.tee 20
                              i32.const 255
                              i32.gt_u
                              br_if 0 (;@13;)
                            end
                          end
                          local.get 0
                          i32.load8_u
                          i32.const 32
                          i32.and
                          br_if 0 (;@11;)
                          local.get 5
                          i32.const 624
                          i32.add
                          local.get 20
                          local.get 0
                          call $__fwritex
                          drop
                        end
                        block  ;; label = @11
                          local.get 0
                          i32.load
                          local.tee 20
                          i32.const 32
                          i32.and
                          br_if 0 (;@11;)
                          local.get 38
                          local.get 36
                          local.get 0
                          call $__fwritex
                          drop
                          local.get 0
                          i32.load
                          local.set 20
                        end
                        block  ;; label = @11
                          local.get 20
                          i32.const 32
                          i32.and
                          br_if 0 (;@11;)
                          i32.const 1114
                          i32.const 1166
                          local.get 31
                          i32.const 32
                          i32.and
                          local.tee 20
                          select
                          i32.const 1118
                          i32.const 1170
                          local.get 20
                          select
                          local.get 35
                          local.get 35
                          f64.ne
                          select
                          i32.const 3
                          local.get 0
                          call $__fwritex
                          drop
                        end
                        block  ;; label = @11
                          local.get 30
                          i32.const 73728
                          i32.and
                          i32.const 8192
                          i32.ne
                          br_if 0 (;@11;)
                          local.get 27
                          local.get 21
                          i32.le_s
                          br_if 0 (;@11;)
                          local.get 5
                          i32.const 624
                          i32.add
                          i32.const 32
                          local.get 27
                          local.get 21
                          i32.sub
                          local.tee 20
                          i32.const 256
                          local.get 20
                          i32.const 256
                          i32.lt_u
                          local.tee 22
                          select
                          call $memset
                          drop
                          block  ;; label = @12
                            local.get 22
                            br_if 0 (;@12;)
                            loop  ;; label = @13
                              block  ;; label = @14
                                local.get 0
                                i32.load8_u
                                i32.const 32
                                i32.and
                                br_if 0 (;@14;)
                                local.get 5
                                i32.const 624
                                i32.add
                                i32.const 256
                                local.get 0
                                call $__fwritex
                                drop
                              end
                              local.get 20
                              i32.const -256
                              i32.add
                              local.tee 20
                              i32.const 255
                              i32.gt_u
                              br_if 0 (;@13;)
                            end
                          end
                          local.get 0
                          i32.load8_u
                          i32.const 32
                          i32.and
                          br_if 0 (;@11;)
                          local.get 5
                          i32.const 624
                          i32.add
                          local.get 20
                          local.get 0
                          call $__fwritex
                          drop
                        end
                        local.get 27
                        local.get 21
                        local.get 27
                        local.get 21
                        i32.gt_s
                        select
                        local.set 20
                        br 6 (;@4;)
                      end
                      block  ;; label = @10
                        block  ;; label = @11
                          block  ;; label = @12
                            local.get 35
                            local.get 5
                            i32.const 108
                            i32.add
                            call $frexp
                            local.tee 35
                            local.get 35
                            f64.add
                            local.tee 35
                            f64.const 0x0p+0 (;=0;)
                            f64.eq
                            br_if 0 (;@12;)
                            local.get 5
                            local.get 5
                            i32.load offset=108
                            local.tee 20
                            i32.const -1
                            i32.add
                            i32.store offset=108
                            local.get 31
                            i32.const 32
                            i32.or
                            local.tee 39
                            i32.const 97
                            i32.ne
                            br_if 1 (;@11;)
                            br 6 (;@6;)
                          end
                          local.get 31
                          i32.const 32
                          i32.or
                          local.tee 39
                          i32.const 97
                          i32.eq
                          br_if 5 (;@6;)
                          i32.const 6
                          local.get 24
                          local.get 24
                          i32.const 0
                          i32.lt_s
                          select
                          local.set 23
                          local.get 5
                          i32.load offset=108
                          local.set 25
                          br 1 (;@10;)
                        end
                        local.get 5
                        local.get 20
                        i32.const -29
                        i32.add
                        local.tee 25
                        i32.store offset=108
                        i32.const 6
                        local.get 24
                        local.get 24
                        i32.const 0
                        i32.lt_s
                        select
                        local.set 23
                        local.get 35
                        f64.const 0x1p+28 (;=2.68435e+08;)
                        f64.mul
                        local.set 35
                      end
                      local.get 5
                      i32.const 112
                      i32.add
                      local.get 16
                      local.get 25
                      i32.const 0
                      i32.lt_s
                      local.tee 40
                      select
                      local.tee 32
                      local.set 21
                      loop  ;; label = @10
                        block  ;; label = @11
                          block  ;; label = @12
                            local.get 35
                            f64.const 0x1p+32 (;=4.29497e+09;)
                            f64.lt
                            local.get 35
                            f64.const 0x0p+0 (;=0;)
                            f64.ge
                            i32.and
                            i32.eqz
                            br_if 0 (;@12;)
                            local.get 35
                            i32.trunc_f64_u
                            local.set 20
                            br 1 (;@11;)
                          end
                          i32.const 0
                          local.set 20
                        end
                        local.get 21
                        local.get 20
                        i32.store
                        local.get 21
                        i32.const 4
                        i32.add
                        local.set 21
                        local.get 35
                        local.get 20
                        f64.convert_i32_u
                        f64.sub
                        f64.const 0x1.dcd65p+29 (;=1e+09;)
                        f64.mul
                        local.tee 35
                        f64.const 0x0p+0 (;=0;)
                        f64.ne
                        br_if 0 (;@10;)
                      end
                      block  ;; label = @10
                        block  ;; label = @11
                          local.get 25
                          i32.const 1
                          i32.ge_s
                          br_if 0 (;@11;)
                          local.get 21
                          local.set 20
                          local.get 32
                          local.set 22
                          br 1 (;@10;)
                        end
                        local.get 32
                        local.set 22
                        loop  ;; label = @11
                          local.get 25
                          i32.const 29
                          local.get 25
                          i32.const 29
                          i32.lt_u
                          select
                          local.set 25
                          block  ;; label = @12
                            local.get 21
                            i32.const -4
                            i32.add
                            local.tee 20
                            local.get 22
                            i32.lt_u
                            br_if 0 (;@12;)
                            local.get 25
                            i64.extend_i32_u
                            local.set 34
                            i64.const 0
                            local.set 33
                            loop  ;; label = @13
                              local.get 20
                              local.get 20
                              i64.load32_u
                              local.get 34
                              i64.shl
                              local.get 33
                              i64.const 4294967295
                              i64.and
                              i64.add
                              local.tee 33
                              local.get 33
                              i64.const 1000000000
                              i64.div_u
                              local.tee 33
                              i64.const 1000000000
                              i64.mul
                              i64.sub
                              i64.store32
                              local.get 20
                              i32.const -4
                              i32.add
                              local.tee 20
                              local.get 22
                              i32.ge_u
                              br_if 0 (;@13;)
                            end
                            local.get 33
                            i32.wrap_i64
                            local.tee 20
                            i32.eqz
                            br_if 0 (;@12;)
                            local.get 22
                            i32.const -4
                            i32.add
                            local.tee 22
                            local.get 20
                            i32.store
                          end
                          block  ;; label = @12
                            loop  ;; label = @13
                              local.get 21
                              local.tee 20
                              local.get 22
                              i32.le_u
                              br_if 1 (;@12;)
                              local.get 20
                              i32.const -4
                              i32.add
                              local.tee 21
                              i32.load
                              i32.eqz
                              br_if 0 (;@13;)
                            end
                          end
                          local.get 5
                          local.get 5
                          i32.load offset=108
                          local.get 25
                          i32.sub
                          local.tee 25
                          i32.store offset=108
                          local.get 20
                          local.set 21
                          local.get 25
                          i32.const 0
                          i32.gt_s
                          br_if 0 (;@11;)
                        end
                      end
                      block  ;; label = @10
                        local.get 25
                        i32.const -1
                        i32.gt_s
                        br_if 0 (;@10;)
                        local.get 23
                        i32.const 25
                        i32.add
                        i32.const 9
                        i32.div_u
                        i32.const 1
                        i32.add
                        local.set 41
                        loop  ;; label = @11
                          i32.const 0
                          local.get 25
                          i32.sub
                          local.tee 21
                          i32.const 9
                          local.get 21
                          i32.const 9
                          i32.lt_u
                          select
                          local.set 24
                          block  ;; label = @12
                            block  ;; label = @13
                              local.get 22
                              local.get 20
                              i32.lt_u
                              br_if 0 (;@13;)
                              local.get 22
                              i32.load
                              local.set 21
                              br 1 (;@12;)
                            end
                            i32.const 1000000000
                            local.get 24
                            i32.shr_u
                            local.set 29
                            i32.const -1
                            local.get 24
                            i32.shl
                            i32.const -1
                            i32.xor
                            local.set 28
                            i32.const 0
                            local.set 25
                            local.get 22
                            local.set 21
                            loop  ;; label = @13
                              local.get 21
                              local.get 21
                              i32.load
                              local.tee 26
                              local.get 24
                              i32.shr_u
                              local.get 25
                              i32.add
                              i32.store
                              local.get 26
                              local.get 28
                              i32.and
                              local.get 29
                              i32.mul
                              local.set 25
                              local.get 21
                              i32.const 4
                              i32.add
                              local.tee 21
                              local.get 20
                              i32.lt_u
                              br_if 0 (;@13;)
                            end
                            local.get 22
                            i32.load
                            local.set 21
                            local.get 25
                            i32.eqz
                            br_if 0 (;@12;)
                            local.get 20
                            local.get 25
                            i32.store
                            local.get 20
                            i32.const 4
                            i32.add
                            local.set 20
                          end
                          local.get 5
                          local.get 5
                          i32.load offset=108
                          local.get 24
                          i32.add
                          local.tee 25
                          i32.store offset=108
                          local.get 32
                          local.get 22
                          local.get 21
                          i32.eqz
                          i32.const 2
                          i32.shl
                          i32.add
                          local.tee 22
                          local.get 39
                          i32.const 102
                          i32.eq
                          select
                          local.tee 21
                          local.get 41
                          i32.const 2
                          i32.shl
                          i32.add
                          local.get 20
                          local.get 20
                          local.get 21
                          i32.sub
                          i32.const 2
                          i32.shr_s
                          local.get 41
                          i32.gt_s
                          select
                          local.set 20
                          local.get 25
                          i32.const 0
                          i32.lt_s
                          br_if 0 (;@11;)
                        end
                      end
                      i32.const 0
                      local.set 26
                      block  ;; label = @10
                        local.get 22
                        local.get 20
                        i32.ge_u
                        br_if 0 (;@10;)
                        local.get 32
                        local.get 22
                        i32.sub
                        i32.const 2
                        i32.shr_s
                        i32.const 9
                        i32.mul
                        local.set 26
                        local.get 22
                        i32.load
                        local.tee 25
                        i32.const 10
                        i32.lt_u
                        br_if 0 (;@10;)
                        i32.const 10
                        local.set 21
                        loop  ;; label = @11
                          local.get 26
                          i32.const 1
                          i32.add
                          local.set 26
                          local.get 25
                          local.get 21
                          i32.const 10
                          i32.mul
                          local.tee 21
                          i32.ge_u
                          br_if 0 (;@11;)
                        end
                      end
                      block  ;; label = @10
                        local.get 23
                        i32.const 0
                        local.get 26
                        local.get 39
                        i32.const 102
                        i32.eq
                        select
                        i32.sub
                        local.get 23
                        i32.const 0
                        i32.ne
                        local.get 39
                        i32.const 103
                        i32.eq
                        local.tee 28
                        i32.and
                        i32.sub
                        local.tee 21
                        local.get 20
                        local.get 32
                        i32.sub
                        i32.const 2
                        i32.shr_s
                        i32.const 9
                        i32.mul
                        i32.const -9
                        i32.add
                        i32.ge_s
                        br_if 0 (;@10;)
                        local.get 21
                        i32.const 9216
                        i32.add
                        local.tee 25
                        i32.const 9
                        i32.div_s
                        local.tee 24
                        i32.const 2
                        i32.shl
                        local.get 15
                        local.get 14
                        local.get 40
                        select
                        i32.add
                        local.tee 40
                        i32.const -4096
                        i32.add
                        local.set 29
                        i32.const 10
                        local.set 21
                        block  ;; label = @11
                          local.get 25
                          local.get 24
                          i32.const 9
                          i32.mul
                          i32.sub
                          local.tee 24
                          i32.const 7
                          i32.gt_s
                          br_if 0 (;@11;)
                          i32.const 8
                          local.get 24
                          i32.sub
                          local.tee 41
                          i32.const 7
                          i32.and
                          local.set 25
                          i32.const 10
                          local.set 21
                          block  ;; label = @12
                            local.get 24
                            i32.const -1
                            i32.add
                            i32.const 7
                            i32.lt_u
                            br_if 0 (;@12;)
                            local.get 41
                            i32.const -8
                            i32.and
                            local.set 24
                            i32.const 10
                            local.set 21
                            loop  ;; label = @13
                              local.get 21
                              i32.const 100000000
                              i32.mul
                              local.set 21
                              local.get 24
                              i32.const -8
                              i32.add
                              local.tee 24
                              br_if 0 (;@13;)
                            end
                          end
                          local.get 25
                          i32.eqz
                          br_if 0 (;@11;)
                          loop  ;; label = @12
                            local.get 21
                            i32.const 10
                            i32.mul
                            local.set 21
                            local.get 25
                            i32.const -1
                            i32.add
                            local.tee 25
                            br_if 0 (;@12;)
                          end
                        end
                        local.get 29
                        i32.const 4
                        i32.add
                        local.set 41
                        block  ;; label = @11
                          block  ;; label = @12
                            local.get 29
                            i32.load
                            local.tee 25
                            local.get 25
                            local.get 21
                            i32.div_u
                            local.tee 39
                            local.get 21
                            i32.mul
                            i32.sub
                            local.tee 24
                            br_if 0 (;@12;)
                            local.get 41
                            local.get 20
                            i32.eq
                            br_if 1 (;@11;)
                          end
                          block  ;; label = @12
                            block  ;; label = @13
                              local.get 39
                              i32.const 1
                              i32.and
                              br_if 0 (;@13;)
                              f64.const 0x1p+53 (;=9.0072e+15;)
                              local.set 35
                              local.get 21
                              i32.const 1000000000
                              i32.ne
                              br_if 1 (;@12;)
                              local.get 29
                              local.get 22
                              i32.le_u
                              br_if 1 (;@12;)
                              local.get 29
                              i32.const -4
                              i32.add
                              i32.load8_u
                              i32.const 1
                              i32.and
                              i32.eqz
                              br_if 1 (;@12;)
                            end
                            f64.const 0x1.0000000000001p+53 (;=9.0072e+15;)
                            local.set 35
                          end
                          f64.const 0x1p-1 (;=0.5;)
                          f64.const 0x1p+0 (;=1;)
                          f64.const 0x1.8p+0 (;=1.5;)
                          local.get 41
                          local.get 20
                          i32.eq
                          select
                          f64.const 0x1.8p+0 (;=1.5;)
                          local.get 24
                          local.get 21
                          i32.const 1
                          i32.shr_u
                          local.tee 41
                          i32.eq
                          select
                          local.get 24
                          local.get 41
                          i32.lt_u
                          select
                          local.set 42
                          block  ;; label = @12
                            local.get 37
                            br_if 0 (;@12;)
                            local.get 38
                            i32.load8_u
                            i32.const 45
                            i32.ne
                            br_if 0 (;@12;)
                            local.get 42
                            f64.neg
                            local.set 42
                            local.get 35
                            f64.neg
                            local.set 35
                          end
                          local.get 29
                          local.get 25
                          local.get 24
                          i32.sub
                          local.tee 25
                          i32.store
                          local.get 35
                          local.get 42
                          f64.add
                          local.get 35
                          f64.eq
                          br_if 0 (;@11;)
                          local.get 29
                          local.get 25
                          local.get 21
                          i32.add
                          local.tee 21
                          i32.store
                          block  ;; label = @12
                            local.get 21
                            i32.const 1000000000
                            i32.lt_u
                            br_if 0 (;@12;)
                            local.get 40
                            i32.const -4100
                            i32.add
                            local.set 21
                            loop  ;; label = @13
                              local.get 21
                              i32.const 4
                              i32.add
                              i32.const 0
                              i32.store
                              block  ;; label = @14
                                local.get 21
                                local.get 22
                                i32.ge_u
                                br_if 0 (;@14;)
                                local.get 22
                                i32.const -4
                                i32.add
                                local.tee 22
                                i32.const 0
                                i32.store
                              end
                              local.get 21
                              local.get 21
                              i32.load
                              i32.const 1
                              i32.add
                              local.tee 25
                              i32.store
                              local.get 21
                              i32.const -4
                              i32.add
                              local.set 21
                              local.get 25
                              i32.const 999999999
                              i32.gt_u
                              br_if 0 (;@13;)
                            end
                            local.get 21
                            i32.const 4
                            i32.add
                            local.set 29
                          end
                          local.get 32
                          local.get 22
                          i32.sub
                          i32.const 2
                          i32.shr_s
                          i32.const 9
                          i32.mul
                          local.set 26
                          local.get 22
                          i32.load
                          local.tee 25
                          i32.const 10
                          i32.lt_u
                          br_if 0 (;@11;)
                          i32.const 10
                          local.set 21
                          loop  ;; label = @12
                            local.get 26
                            i32.const 1
                            i32.add
                            local.set 26
                            local.get 25
                            local.get 21
                            i32.const 10
                            i32.mul
                            local.tee 21
                            i32.ge_u
                            br_if 0 (;@12;)
                          end
                        end
                        local.get 29
                        i32.const 4
                        i32.add
                        local.tee 21
                        local.get 20
                        local.get 20
                        local.get 21
                        i32.gt_u
                        select
                        local.set 20
                      end
                      local.get 20
                      local.get 32
                      i32.sub
                      local.set 21
                      block  ;; label = @10
                        loop  ;; label = @11
                          local.get 21
                          local.set 25
                          local.get 20
                          local.tee 29
                          local.get 22
                          i32.le_u
                          local.tee 24
                          br_if 1 (;@10;)
                          local.get 25
                          i32.const -4
                          i32.add
                          local.set 21
                          local.get 29
                          i32.const -4
                          i32.add
                          local.tee 20
                          i32.load
                          i32.eqz
                          br_if 0 (;@11;)
                        end
                      end
                      block  ;; label = @10
                        block  ;; label = @11
                          local.get 28
                          br_if 0 (;@11;)
                          local.get 30
                          i32.const 8
                          i32.and
                          local.set 39
                          br 1 (;@10;)
                        end
                        local.get 26
                        i32.const -1
                        i32.xor
                        i32.const -1
                        local.get 23
                        i32.const 1
                        local.get 23
                        select
                        local.tee 20
                        local.get 26
                        i32.gt_s
                        local.get 26
                        i32.const -5
                        i32.gt_s
                        i32.and
                        local.tee 21
                        select
                        local.get 20
                        i32.add
                        local.set 23
                        i32.const -1
                        i32.const -2
                        local.get 21
                        select
                        local.get 31
                        i32.add
                        local.set 31
                        local.get 30
                        i32.const 8
                        i32.and
                        local.tee 39
                        br_if 0 (;@10;)
                        i32.const -9
                        local.set 20
                        block  ;; label = @11
                          local.get 24
                          br_if 0 (;@11;)
                          local.get 29
                          i32.const -4
                          i32.add
                          i32.load
                          local.tee 24
                          i32.eqz
                          br_if 0 (;@11;)
                          i32.const 0
                          local.set 20
                          local.get 24
                          i32.const 10
                          i32.rem_u
                          br_if 0 (;@11;)
                          i32.const 10
                          local.set 21
                          i32.const 0
                          local.set 20
                          loop  ;; label = @12
                            local.get 20
                            i32.const -1
                            i32.add
                            local.set 20
                            local.get 24
                            local.get 21
                            i32.const 10
                            i32.mul
                            local.tee 21
                            i32.rem_u
                            i32.eqz
                            br_if 0 (;@12;)
                          end
                        end
                        local.get 25
                        i32.const 2
                        i32.shr_s
                        i32.const 9
                        i32.mul
                        i32.const -9
                        i32.add
                        local.set 21
                        block  ;; label = @11
                          local.get 31
                          i32.const -33
                          i32.and
                          i32.const 70
                          i32.ne
                          br_if 0 (;@11;)
                          i32.const 0
                          local.set 39
                          local.get 23
                          local.get 21
                          local.get 20
                          i32.add
                          local.tee 20
                          i32.const 0
                          local.get 20
                          i32.const 0
                          i32.gt_s
                          select
                          local.tee 20
                          local.get 23
                          local.get 20
                          i32.lt_s
                          select
                          local.set 23
                          br 1 (;@10;)
                        end
                        i32.const 0
                        local.set 39
                        local.get 23
                        local.get 21
                        local.get 26
                        i32.add
                        local.get 20
                        i32.add
                        local.tee 20
                        i32.const 0
                        local.get 20
                        i32.const 0
                        i32.gt_s
                        select
                        local.tee 20
                        local.get 23
                        local.get 20
                        i32.lt_s
                        select
                        local.set 23
                      end
                      local.get 23
                      i32.const 2147483645
                      i32.const 2147483646
                      local.get 23
                      local.get 39
                      i32.or
                      local.tee 37
                      select
                      i32.gt_s
                      br_if 6 (;@3;)
                      local.get 23
                      local.get 37
                      i32.const 0
                      i32.ne
                      i32.add
                      i32.const 1
                      i32.add
                      local.set 41
                      block  ;; label = @10
                        block  ;; label = @11
                          local.get 31
                          i32.const -33
                          i32.and
                          i32.const 70
                          i32.ne
                          local.tee 40
                          br_if 0 (;@11;)
                          local.get 26
                          i32.const 2147483647
                          local.get 41
                          i32.sub
                          i32.gt_s
                          br_if 8 (;@3;)
                          local.get 26
                          i32.const 0
                          local.get 26
                          i32.const 0
                          i32.gt_s
                          select
                          local.set 20
                          br 1 (;@10;)
                        end
                        local.get 6
                        local.set 25
                        local.get 6
                        local.set 21
                        block  ;; label = @11
                          local.get 26
                          local.get 26
                          i32.const 31
                          i32.shr_s
                          local.tee 20
                          i32.add
                          local.get 20
                          i32.xor
                          local.tee 20
                          i32.eqz
                          br_if 0 (;@11;)
                          loop  ;; label = @12
                            local.get 21
                            i32.const -1
                            i32.add
                            local.tee 21
                            local.get 20
                            local.get 20
                            i32.const 10
                            i32.div_u
                            local.tee 24
                            i32.const 10
                            i32.mul
                            i32.sub
                            i32.const 48
                            i32.or
                            i32.store8
                            local.get 25
                            i32.const -1
                            i32.add
                            local.set 25
                            local.get 20
                            i32.const 9
                            i32.gt_u
                            local.set 28
                            local.get 24
                            local.set 20
                            local.get 28
                            br_if 0 (;@12;)
                          end
                        end
                        block  ;; label = @11
                          local.get 6
                          local.get 25
                          i32.sub
                          i32.const 1
                          i32.gt_s
                          br_if 0 (;@11;)
                          local.get 21
                          local.get 13
                          local.get 25
                          i32.sub
                          i32.add
                          local.tee 21
                          i32.const 48
                          local.get 12
                          local.get 25
                          i32.add
                          call $memset
                          drop
                        end
                        local.get 21
                        i32.const -2
                        i32.add
                        local.tee 28
                        local.get 31
                        i32.store8
                        local.get 21
                        i32.const -1
                        i32.add
                        i32.const 45
                        i32.const 43
                        local.get 26
                        i32.const 0
                        i32.lt_s
                        select
                        i32.store8
                        local.get 6
                        local.get 28
                        i32.sub
                        local.tee 20
                        i32.const 2147483647
                        local.get 41
                        i32.sub
                        i32.gt_s
                        br_if 7 (;@3;)
                      end
                      local.get 20
                      local.get 41
                      i32.add
                      local.tee 20
                      local.get 36
                      i32.const 2147483647
                      i32.xor
                      i32.gt_s
                      br_if 6 (;@3;)
                      local.get 20
                      local.get 36
                      i32.add
                      local.set 41
                      block  ;; label = @10
                        local.get 30
                        i32.const 73728
                        i32.and
                        local.tee 30
                        br_if 0 (;@10;)
                        local.get 27
                        local.get 41
                        i32.le_s
                        br_if 0 (;@10;)
                        local.get 5
                        i32.const 624
                        i32.add
                        i32.const 32
                        local.get 27
                        local.get 41
                        i32.sub
                        local.tee 20
                        i32.const 256
                        local.get 20
                        i32.const 256
                        i32.lt_u
                        local.tee 21
                        select
                        call $memset
                        drop
                        block  ;; label = @11
                          local.get 21
                          br_if 0 (;@11;)
                          loop  ;; label = @12
                            block  ;; label = @13
                              local.get 0
                              i32.load8_u
                              i32.const 32
                              i32.and
                              br_if 0 (;@13;)
                              local.get 5
                              i32.const 624
                              i32.add
                              i32.const 256
                              local.get 0
                              call $__fwritex
                              drop
                            end
                            local.get 20
                            i32.const -256
                            i32.add
                            local.tee 20
                            i32.const 255
                            i32.gt_u
                            br_if 0 (;@12;)
                          end
                        end
                        local.get 0
                        i32.load8_u
                        i32.const 32
                        i32.and
                        br_if 0 (;@10;)
                        local.get 5
                        i32.const 624
                        i32.add
                        local.get 20
                        local.get 0
                        call $__fwritex
                        drop
                      end
                      block  ;; label = @10
                        local.get 0
                        i32.load8_u
                        i32.const 32
                        i32.and
                        br_if 0 (;@10;)
                        local.get 38
                        local.get 36
                        local.get 0
                        call $__fwritex
                        drop
                      end
                      block  ;; label = @10
                        local.get 30
                        i32.const 65536
                        i32.ne
                        br_if 0 (;@10;)
                        local.get 27
                        local.get 41
                        i32.le_s
                        br_if 0 (;@10;)
                        local.get 5
                        i32.const 624
                        i32.add
                        i32.const 48
                        local.get 27
                        local.get 41
                        i32.sub
                        local.tee 20
                        i32.const 256
                        local.get 20
                        i32.const 256
                        i32.lt_u
                        local.tee 21
                        select
                        call $memset
                        drop
                        block  ;; label = @11
                          local.get 21
                          br_if 0 (;@11;)
                          loop  ;; label = @12
                            block  ;; label = @13
                              local.get 0
                              i32.load8_u
                              i32.const 32
                              i32.and
                              br_if 0 (;@13;)
                              local.get 5
                              i32.const 624
                              i32.add
                              i32.const 256
                              local.get 0
                              call $__fwritex
                              drop
                            end
                            local.get 20
                            i32.const -256
                            i32.add
                            local.tee 20
                            i32.const 255
                            i32.gt_u
                            br_if 0 (;@12;)
                          end
                        end
                        local.get 0
                        i32.load8_u
                        i32.const 32
                        i32.and
                        br_if 0 (;@10;)
                        local.get 5
                        i32.const 624
                        i32.add
                        local.get 20
                        local.get 0
                        call $__fwritex
                        drop
                      end
                      block  ;; label = @10
                        block  ;; label = @11
                          local.get 40
                          br_if 0 (;@11;)
                          local.get 32
                          local.get 22
                          local.get 22
                          local.get 32
                          i32.gt_u
                          select
                          local.tee 28
                          local.set 24
                          loop  ;; label = @12
                            block  ;; label = @13
                              block  ;; label = @14
                                local.get 24
                                i32.load
                                local.tee 20
                                br_if 0 (;@14;)
                                local.get 11
                                local.set 22
                                local.get 11
                                local.set 21
                                br 1 (;@13;)
                              end
                              local.get 11
                              local.set 22
                              local.get 11
                              local.set 21
                              loop  ;; label = @14
                                local.get 21
                                i32.const -1
                                i32.add
                                local.tee 21
                                local.get 20
                                local.get 20
                                i32.const 10
                                i32.div_u
                                local.tee 25
                                i32.const 10
                                i32.mul
                                i32.sub
                                i32.const 48
                                i32.or
                                i32.store8
                                local.get 22
                                i32.const -1
                                i32.add
                                local.set 22
                                local.get 20
                                i32.const 9
                                i32.gt_u
                                local.set 26
                                local.get 25
                                local.set 20
                                local.get 26
                                br_if 0 (;@14;)
                              end
                            end
                            block  ;; label = @13
                              block  ;; label = @14
                                local.get 24
                                local.get 28
                                i32.eq
                                br_if 0 (;@14;)
                                local.get 21
                                local.get 5
                                i32.const 80
                                i32.add
                                i32.le_u
                                br_if 1 (;@13;)
                                local.get 21
                                local.get 5
                                i32.const 80
                                i32.add
                                i32.add
                                local.get 22
                                i32.sub
                                local.tee 21
                                i32.const 48
                                local.get 22
                                local.get 5
                                i32.const 80
                                i32.add
                                i32.sub
                                call $memset
                                drop
                                br 1 (;@13;)
                              end
                              local.get 21
                              local.get 11
                              i32.ne
                              br_if 0 (;@13;)
                              local.get 5
                              i32.const 48
                              i32.store8 offset=88
                              local.get 10
                              local.set 21
                            end
                            block  ;; label = @13
                              local.get 0
                              i32.load8_u
                              i32.const 32
                              i32.and
                              br_if 0 (;@13;)
                              local.get 21
                              local.get 11
                              local.get 21
                              i32.sub
                              local.get 0
                              call $__fwritex
                              drop
                            end
                            local.get 24
                            i32.const 4
                            i32.add
                            local.tee 24
                            local.get 32
                            i32.le_u
                            br_if 0 (;@12;)
                          end
                          block  ;; label = @12
                            local.get 37
                            i32.eqz
                            br_if 0 (;@12;)
                            local.get 0
                            i32.load8_u
                            i32.const 32
                            i32.and
                            br_if 0 (;@12;)
                            i32.const 1277
                            i32.const 1
                            local.get 0
                            call $__fwritex
                            drop
                          end
                          block  ;; label = @12
                            block  ;; label = @13
                              local.get 24
                              local.get 29
                              i32.lt_u
                              br_if 0 (;@13;)
                              local.get 23
                              local.set 20
                              br 1 (;@12;)
                            end
                            block  ;; label = @13
                              local.get 23
                              i32.const 1
                              i32.ge_s
                              br_if 0 (;@13;)
                              local.get 23
                              local.set 20
                              br 1 (;@12;)
                            end
                            loop  ;; label = @13
                              block  ;; label = @14
                                block  ;; label = @15
                                  block  ;; label = @16
                                    local.get 24
                                    i32.load
                                    local.tee 20
                                    br_if 0 (;@16;)
                                    local.get 11
                                    local.set 21
                                    local.get 11
                                    local.set 22
                                    br 1 (;@15;)
                                  end
                                  local.get 11
                                  local.set 22
                                  local.get 11
                                  local.set 21
                                  loop  ;; label = @16
                                    local.get 21
                                    i32.const -1
                                    i32.add
                                    local.tee 21
                                    local.get 20
                                    local.get 20
                                    i32.const 10
                                    i32.div_u
                                    local.tee 25
                                    i32.const 10
                                    i32.mul
                                    i32.sub
                                    i32.const 48
                                    i32.or
                                    i32.store8
                                    local.get 22
                                    i32.const -1
                                    i32.add
                                    local.set 22
                                    local.get 20
                                    i32.const 9
                                    i32.gt_u
                                    local.set 26
                                    local.get 25
                                    local.set 20
                                    local.get 26
                                    br_if 0 (;@16;)
                                  end
                                  local.get 21
                                  local.get 5
                                  i32.const 80
                                  i32.add
                                  i32.le_u
                                  br_if 1 (;@14;)
                                end
                                local.get 21
                                local.get 5
                                i32.const 80
                                i32.add
                                i32.add
                                local.get 22
                                i32.sub
                                local.tee 21
                                i32.const 48
                                local.get 22
                                local.get 5
                                i32.const 80
                                i32.add
                                i32.sub
                                call $memset
                                drop
                              end
                              block  ;; label = @14
                                local.get 0
                                i32.load8_u
                                i32.const 32
                                i32.and
                                br_if 0 (;@14;)
                                local.get 21
                                local.get 23
                                i32.const 9
                                local.get 23
                                i32.const 9
                                i32.lt_s
                                select
                                local.get 0
                                call $__fwritex
                                drop
                              end
                              local.get 23
                              i32.const -9
                              i32.add
                              local.set 20
                              local.get 24
                              i32.const 4
                              i32.add
                              local.tee 24
                              local.get 29
                              i32.ge_u
                              br_if 1 (;@12;)
                              local.get 23
                              i32.const 9
                              i32.gt_s
                              local.set 21
                              local.get 20
                              local.set 23
                              local.get 21
                              br_if 0 (;@13;)
                            end
                          end
                          local.get 0
                          i32.const 48
                          local.get 20
                          i32.const 9
                          i32.add
                          i32.const 9
                          i32.const 0
                          call $pad
                          br 1 (;@10;)
                        end
                        block  ;; label = @11
                          local.get 23
                          i32.const 0
                          i32.lt_s
                          br_if 0 (;@11;)
                          local.get 29
                          local.get 22
                          i32.const 4
                          i32.add
                          local.get 29
                          local.get 22
                          i32.gt_u
                          select
                          local.set 29
                          local.get 22
                          local.set 24
                          loop  ;; label = @12
                            block  ;; label = @13
                              block  ;; label = @14
                                local.get 24
                                i32.load
                                local.tee 20
                                i32.eqz
                                br_if 0 (;@14;)
                                i32.const 0
                                local.set 21
                                loop  ;; label = @15
                                  local.get 5
                                  i32.const 80
                                  i32.add
                                  local.get 21
                                  i32.add
                                  i32.const 8
                                  i32.add
                                  local.get 20
                                  local.get 20
                                  i32.const 10
                                  i32.div_u
                                  local.tee 25
                                  i32.const 10
                                  i32.mul
                                  i32.sub
                                  i32.const 48
                                  i32.or
                                  i32.store8
                                  local.get 21
                                  i32.const -1
                                  i32.add
                                  local.set 21
                                  local.get 20
                                  i32.const 9
                                  i32.gt_u
                                  local.set 26
                                  local.get 25
                                  local.set 20
                                  local.get 26
                                  br_if 0 (;@15;)
                                end
                                local.get 21
                                i32.eqz
                                br_if 0 (;@14;)
                                local.get 5
                                i32.const 80
                                i32.add
                                local.get 21
                                i32.add
                                i32.const 9
                                i32.add
                                local.set 20
                                br 1 (;@13;)
                              end
                              local.get 5
                              i32.const 48
                              i32.store8 offset=88
                              local.get 10
                              local.set 20
                            end
                            block  ;; label = @13
                              block  ;; label = @14
                                local.get 24
                                local.get 22
                                i32.eq
                                br_if 0 (;@14;)
                                local.get 20
                                local.get 5
                                i32.const 80
                                i32.add
                                i32.le_u
                                br_if 1 (;@13;)
                                local.get 5
                                i32.const 80
                                i32.add
                                i32.const 48
                                local.get 20
                                local.get 5
                                i32.const 80
                                i32.add
                                i32.sub
                                call $memset
                                drop
                                local.get 5
                                i32.const 80
                                i32.add
                                local.set 20
                                br 1 (;@13;)
                              end
                              block  ;; label = @14
                                local.get 0
                                i32.load8_u
                                i32.const 32
                                i32.and
                                br_if 0 (;@14;)
                                local.get 20
                                i32.const 1
                                local.get 0
                                call $__fwritex
                                drop
                              end
                              local.get 20
                              i32.const 1
                              i32.add
                              local.set 20
                              block  ;; label = @14
                                local.get 39
                                br_if 0 (;@14;)
                                local.get 23
                                i32.const 1
                                i32.lt_s
                                br_if 1 (;@13;)
                              end
                              local.get 0
                              i32.load8_u
                              i32.const 32
                              i32.and
                              br_if 0 (;@13;)
                              i32.const 1277
                              i32.const 1
                              local.get 0
                              call $__fwritex
                              drop
                            end
                            local.get 11
                            local.get 20
                            i32.sub
                            local.set 21
                            block  ;; label = @13
                              local.get 0
                              i32.load8_u
                              i32.const 32
                              i32.and
                              br_if 0 (;@13;)
                              local.get 20
                              local.get 21
                              local.get 23
                              local.get 23
                              local.get 21
                              i32.gt_s
                              select
                              local.get 0
                              call $__fwritex
                              drop
                            end
                            local.get 23
                            local.get 21
                            i32.sub
                            local.set 23
                            local.get 24
                            i32.const 4
                            i32.add
                            local.tee 24
                            local.get 29
                            i32.ge_u
                            br_if 1 (;@11;)
                            local.get 23
                            i32.const -1
                            i32.gt_s
                            br_if 0 (;@12;)
                          end
                        end
                        local.get 0
                        i32.const 48
                        local.get 23
                        i32.const 18
                        i32.add
                        i32.const 18
                        i32.const 0
                        call $pad
                        local.get 0
                        i32.load8_u
                        i32.const 32
                        i32.and
                        br_if 0 (;@10;)
                        local.get 28
                        local.get 6
                        local.get 28
                        i32.sub
                        local.get 0
                        call $__fwritex
                        drop
                      end
                      block  ;; label = @10
                        local.get 30
                        i32.const 8192
                        i32.ne
                        br_if 0 (;@10;)
                        local.get 27
                        local.get 41
                        i32.le_s
                        br_if 0 (;@10;)
                        local.get 5
                        i32.const 624
                        i32.add
                        i32.const 32
                        local.get 27
                        local.get 41
                        i32.sub
                        local.tee 20
                        i32.const 256
                        local.get 20
                        i32.const 256
                        i32.lt_u
                        local.tee 21
                        select
                        call $memset
                        drop
                        block  ;; label = @11
                          local.get 21
                          br_if 0 (;@11;)
                          loop  ;; label = @12
                            block  ;; label = @13
                              local.get 0
                              i32.load8_u
                              i32.const 32
                              i32.and
                              br_if 0 (;@13;)
                              local.get 5
                              i32.const 624
                              i32.add
                              i32.const 256
                              local.get 0
                              call $__fwritex
                              drop
                            end
                            local.get 20
                            i32.const -256
                            i32.add
                            local.tee 20
                            i32.const 255
                            i32.gt_u
                            br_if 0 (;@12;)
                          end
                        end
                        local.get 0
                        i32.load8_u
                        i32.const 32
                        i32.and
                        br_if 0 (;@10;)
                        local.get 5
                        i32.const 624
                        i32.add
                        local.get 20
                        local.get 0
                        call $__fwritex
                        drop
                      end
                      local.get 27
                      local.get 41
                      local.get 27
                      local.get 41
                      i32.gt_s
                      select
                      local.set 20
                      br 4 (;@5;)
                    end
                    i32.const 0
                    local.set 23
                    i32.const 1038
                    local.set 32
                    local.get 17
                    local.set 20
                    local.get 30
                    local.set 29
                    local.get 24
                    local.set 26
                  end
                  local.get 20
                  local.get 21
                  i32.sub
                  local.tee 24
                  local.get 26
                  local.get 26
                  local.get 24
                  i32.lt_s
                  select
                  local.tee 28
                  i32.const 2147483647
                  local.get 23
                  i32.sub
                  i32.gt_s
                  br_if 4 (;@3;)
                  local.get 23
                  local.get 28
                  i32.add
                  local.tee 25
                  local.get 27
                  local.get 27
                  local.get 25
                  i32.lt_s
                  select
                  local.tee 20
                  local.get 22
                  i32.gt_s
                  br_if 4 (;@3;)
                  block  ;; label = @8
                    local.get 29
                    i32.const 73728
                    i32.and
                    local.tee 29
                    br_if 0 (;@8;)
                    local.get 25
                    local.get 27
                    i32.ge_s
                    br_if 0 (;@8;)
                    local.get 5
                    i32.const 112
                    i32.add
                    i32.const 32
                    local.get 20
                    local.get 25
                    i32.sub
                    local.tee 22
                    i32.const 256
                    local.get 22
                    i32.const 256
                    i32.lt_u
                    local.tee 30
                    select
                    call $memset
                    drop
                    block  ;; label = @9
                      local.get 30
                      br_if 0 (;@9;)
                      loop  ;; label = @10
                        block  ;; label = @11
                          local.get 0
                          i32.load8_u
                          i32.const 32
                          i32.and
                          br_if 0 (;@11;)
                          local.get 5
                          i32.const 112
                          i32.add
                          i32.const 256
                          local.get 0
                          call $__fwritex
                          drop
                        end
                        local.get 22
                        i32.const -256
                        i32.add
                        local.tee 22
                        i32.const 255
                        i32.gt_u
                        br_if 0 (;@10;)
                      end
                    end
                    local.get 0
                    i32.load8_u
                    i32.const 32
                    i32.and
                    br_if 0 (;@8;)
                    local.get 5
                    i32.const 112
                    i32.add
                    local.get 22
                    local.get 0
                    call $__fwritex
                    drop
                  end
                  block  ;; label = @8
                    local.get 0
                    i32.load8_u
                    i32.const 32
                    i32.and
                    br_if 0 (;@8;)
                    local.get 32
                    local.get 23
                    local.get 0
                    call $__fwritex
                    drop
                  end
                  block  ;; label = @8
                    local.get 29
                    i32.const 65536
                    i32.ne
                    br_if 0 (;@8;)
                    local.get 25
                    local.get 27
                    i32.ge_s
                    br_if 0 (;@8;)
                    local.get 5
                    i32.const 112
                    i32.add
                    i32.const 48
                    local.get 20
                    local.get 25
                    i32.sub
                    local.tee 22
                    i32.const 256
                    local.get 22
                    i32.const 256
                    i32.lt_u
                    local.tee 23
                    select
                    call $memset
                    drop
                    block  ;; label = @9
                      local.get 23
                      br_if 0 (;@9;)
                      loop  ;; label = @10
                        block  ;; label = @11
                          local.get 0
                          i32.load8_u
                          i32.const 32
                          i32.and
                          br_if 0 (;@11;)
                          local.get 5
                          i32.const 112
                          i32.add
                          i32.const 256
                          local.get 0
                          call $__fwritex
                          drop
                        end
                        local.get 22
                        i32.const -256
                        i32.add
                        local.tee 22
                        i32.const 255
                        i32.gt_u
                        br_if 0 (;@10;)
                      end
                    end
                    local.get 0
                    i32.load8_u
                    i32.const 32
                    i32.and
                    br_if 0 (;@8;)
                    local.get 5
                    i32.const 112
                    i32.add
                    local.get 22
                    local.get 0
                    call $__fwritex
                    drop
                  end
                  block  ;; label = @8
                    local.get 24
                    local.get 26
                    i32.ge_s
                    br_if 0 (;@8;)
                    local.get 5
                    i32.const 112
                    i32.add
                    i32.const 48
                    local.get 28
                    local.get 24
                    i32.sub
                    local.tee 22
                    i32.const 256
                    local.get 22
                    i32.const 256
                    i32.lt_u
                    local.tee 26
                    select
                    call $memset
                    drop
                    block  ;; label = @9
                      local.get 26
                      br_if 0 (;@9;)
                      loop  ;; label = @10
                        block  ;; label = @11
                          local.get 0
                          i32.load8_u
                          i32.const 32
                          i32.and
                          br_if 0 (;@11;)
                          local.get 5
                          i32.const 112
                          i32.add
                          i32.const 256
                          local.get 0
                          call $__fwritex
                          drop
                        end
                        local.get 22
                        i32.const -256
                        i32.add
                        local.tee 22
                        i32.const 255
                        i32.gt_u
                        br_if 0 (;@10;)
                      end
                    end
                    local.get 0
                    i32.load8_u
                    i32.const 32
                    i32.and
                    br_if 0 (;@8;)
                    local.get 5
                    i32.const 112
                    i32.add
                    local.get 22
                    local.get 0
                    call $__fwritex
                    drop
                  end
                  block  ;; label = @8
                    local.get 0
                    i32.load8_u
                    i32.const 32
                    i32.and
                    br_if 0 (;@8;)
                    local.get 21
                    local.get 24
                    local.get 0
                    call $__fwritex
                    drop
                  end
                  local.get 29
                  i32.const 8192
                  i32.ne
                  br_if 3 (;@4;)
                  local.get 25
                  local.get 27
                  i32.ge_s
                  br_if 3 (;@4;)
                  local.get 5
                  i32.const 112
                  i32.add
                  i32.const 32
                  local.get 20
                  local.get 25
                  i32.sub
                  local.tee 21
                  i32.const 256
                  local.get 21
                  i32.const 256
                  i32.lt_u
                  local.tee 22
                  select
                  call $memset
                  drop
                  block  ;; label = @8
                    local.get 22
                    br_if 0 (;@8;)
                    loop  ;; label = @9
                      block  ;; label = @10
                        local.get 0
                        i32.load8_u
                        i32.const 32
                        i32.and
                        br_if 0 (;@10;)
                        local.get 5
                        i32.const 112
                        i32.add
                        i32.const 256
                        local.get 0
                        call $__fwritex
                        drop
                      end
                      local.get 21
                      i32.const -256
                      i32.add
                      local.tee 21
                      i32.const 255
                      i32.gt_u
                      br_if 0 (;@9;)
                    end
                  end
                  local.get 0
                  i32.load8_u
                  i32.const 32
                  i32.and
                  br_if 3 (;@4;)
                  local.get 5
                  i32.const 112
                  i32.add
                  local.get 21
                  local.get 0
                  call $__fwritex
                  drop
                  br 3 (;@4;)
                end
                i32.const 0
                i32.const 28
                i32.store offset=4500
                br 4 (;@2;)
              end
              local.get 38
              local.get 31
              i32.const 26
              i32.shl
              i32.const 31
              i32.shr_s
              i32.const 9
              i32.and
              i32.add
              local.set 23
              block  ;; label = @6
                local.get 24
                i32.const 11
                i32.gt_u
                br_if 0 (;@6;)
                block  ;; label = @7
                  block  ;; label = @8
                    i32.const 12
                    local.get 24
                    i32.sub
                    local.tee 20
                    i32.const 7
                    i32.and
                    local.tee 21
                    br_if 0 (;@8;)
                    f64.const 0x1p+4 (;=16;)
                    local.set 42
                    br 1 (;@7;)
                  end
                  local.get 24
                  i32.const -12
                  i32.add
                  local.set 20
                  f64.const 0x1p+4 (;=16;)
                  local.set 42
                  loop  ;; label = @8
                    local.get 20
                    i32.const 1
                    i32.add
                    local.set 20
                    local.get 42
                    f64.const 0x1p+4 (;=16;)
                    f64.mul
                    local.set 42
                    local.get 21
                    i32.const -1
                    i32.add
                    local.tee 21
                    br_if 0 (;@8;)
                  end
                  i32.const 0
                  local.get 20
                  i32.sub
                  local.set 20
                end
                block  ;; label = @7
                  local.get 24
                  i32.const -5
                  i32.add
                  i32.const 7
                  i32.lt_u
                  br_if 0 (;@7;)
                  loop  ;; label = @8
                    local.get 42
                    f64.const 0x1p+4 (;=16;)
                    f64.mul
                    f64.const 0x1p+4 (;=16;)
                    f64.mul
                    f64.const 0x1p+4 (;=16;)
                    f64.mul
                    f64.const 0x1p+4 (;=16;)
                    f64.mul
                    f64.const 0x1p+4 (;=16;)
                    f64.mul
                    f64.const 0x1p+4 (;=16;)
                    f64.mul
                    f64.const 0x1p+4 (;=16;)
                    f64.mul
                    f64.const 0x1p+4 (;=16;)
                    f64.mul
                    local.set 42
                    local.get 20
                    i32.const -8
                    i32.add
                    local.tee 20
                    br_if 0 (;@8;)
                  end
                end
                block  ;; label = @7
                  local.get 23
                  i32.load8_u
                  i32.const 45
                  i32.ne
                  br_if 0 (;@7;)
                  local.get 42
                  local.get 35
                  f64.neg
                  local.get 42
                  f64.sub
                  f64.add
                  f64.neg
                  local.set 35
                  br 1 (;@6;)
                end
                local.get 35
                local.get 42
                f64.add
                local.get 42
                f64.sub
                local.set 35
              end
              block  ;; label = @6
                block  ;; label = @7
                  local.get 5
                  i32.load offset=108
                  local.tee 26
                  i32.eqz
                  br_if 0 (;@7;)
                  local.get 26
                  local.get 26
                  i32.const 31
                  i32.shr_s
                  local.tee 20
                  i32.add
                  local.get 20
                  i32.xor
                  local.set 20
                  i32.const 0
                  local.set 21
                  loop  ;; label = @8
                    local.get 5
                    i32.const 68
                    i32.add
                    local.get 21
                    i32.add
                    i32.const 11
                    i32.add
                    local.get 20
                    local.get 20
                    i32.const 10
                    i32.div_u
                    local.tee 22
                    i32.const 10
                    i32.mul
                    i32.sub
                    i32.const 48
                    i32.or
                    i32.store8
                    local.get 21
                    i32.const -1
                    i32.add
                    local.set 21
                    local.get 20
                    i32.const 9
                    i32.gt_u
                    local.set 25
                    local.get 22
                    local.set 20
                    local.get 25
                    br_if 0 (;@8;)
                  end
                  local.get 21
                  i32.eqz
                  br_if 0 (;@7;)
                  local.get 5
                  i32.const 68
                  i32.add
                  local.get 21
                  i32.add
                  i32.const 12
                  i32.add
                  local.set 20
                  br 1 (;@6;)
                end
                local.get 5
                i32.const 48
                i32.store8 offset=79
                local.get 9
                local.set 20
              end
              local.get 36
              i32.const 2
              i32.or
              local.set 28
              local.get 31
              i32.const 32
              i32.and
              local.set 22
              local.get 20
              i32.const -2
              i32.add
              local.tee 29
              local.get 31
              i32.const 15
              i32.add
              i32.store8
              local.get 20
              i32.const -1
              i32.add
              i32.const 45
              i32.const 43
              local.get 26
              i32.const 0
              i32.lt_s
              select
              i32.store8
              local.get 30
              i32.const 8
              i32.and
              local.set 25
              local.get 5
              i32.const 80
              i32.add
              local.set 21
              loop  ;; label = @6
                local.get 21
                local.set 20
                block  ;; label = @7
                  block  ;; label = @8
                    local.get 35
                    f64.abs
                    f64.const 0x1p+31 (;=2.14748e+09;)
                    f64.lt
                    i32.eqz
                    br_if 0 (;@8;)
                    local.get 35
                    i32.trunc_f64_s
                    local.set 21
                    br 1 (;@7;)
                  end
                  i32.const -2147483648
                  local.set 21
                end
                local.get 20
                local.get 21
                i32.const 3728
                i32.add
                i32.load8_u
                local.get 22
                i32.or
                i32.store8
                local.get 35
                local.get 21
                f64.convert_i32_s
                f64.sub
                f64.const 0x1p+4 (;=16;)
                f64.mul
                local.set 35
                block  ;; label = @7
                  local.get 20
                  i32.const 1
                  i32.add
                  local.tee 21
                  local.get 5
                  i32.const 80
                  i32.add
                  i32.sub
                  i32.const 1
                  i32.ne
                  br_if 0 (;@7;)
                  block  ;; label = @8
                    local.get 25
                    br_if 0 (;@8;)
                    local.get 24
                    i32.const 0
                    i32.gt_s
                    br_if 0 (;@8;)
                    local.get 35
                    f64.const 0x0p+0 (;=0;)
                    f64.eq
                    br_if 1 (;@7;)
                  end
                  local.get 20
                  i32.const 46
                  i32.store8 offset=1
                  local.get 20
                  i32.const 2
                  i32.add
                  local.set 21
                end
                local.get 35
                f64.const 0x0p+0 (;=0;)
                f64.ne
                br_if 0 (;@6;)
              end
              i32.const 2147483645
              local.get 6
              local.get 29
              i32.sub
              local.tee 32
              local.get 28
              i32.add
              local.tee 20
              i32.sub
              local.get 24
              i32.lt_s
              br_if 2 (;@3;)
              local.get 24
              i32.const 2
              i32.add
              local.get 21
              local.get 5
              i32.const 80
              i32.add
              i32.sub
              local.tee 22
              local.get 8
              local.get 21
              i32.add
              local.get 24
              i32.lt_s
              select
              local.get 22
              local.get 24
              select
              local.tee 26
              local.get 20
              i32.add
              local.set 21
              block  ;; label = @6
                local.get 30
                i32.const 73728
                i32.and
                local.tee 25
                br_if 0 (;@6;)
                local.get 27
                local.get 21
                i32.le_s
                br_if 0 (;@6;)
                local.get 5
                i32.const 624
                i32.add
                i32.const 32
                local.get 27
                local.get 21
                i32.sub
                local.tee 20
                i32.const 256
                local.get 20
                i32.const 256
                i32.lt_u
                local.tee 24
                select
                call $memset
                drop
                block  ;; label = @7
                  local.get 24
                  br_if 0 (;@7;)
                  loop  ;; label = @8
                    block  ;; label = @9
                      local.get 0
                      i32.load8_u
                      i32.const 32
                      i32.and
                      br_if 0 (;@9;)
                      local.get 5
                      i32.const 624
                      i32.add
                      i32.const 256
                      local.get 0
                      call $__fwritex
                      drop
                    end
                    local.get 20
                    i32.const -256
                    i32.add
                    local.tee 20
                    i32.const 255
                    i32.gt_u
                    br_if 0 (;@8;)
                  end
                end
                local.get 0
                i32.load8_u
                i32.const 32
                i32.and
                br_if 0 (;@6;)
                local.get 5
                i32.const 624
                i32.add
                local.get 20
                local.get 0
                call $__fwritex
                drop
              end
              block  ;; label = @6
                local.get 0
                i32.load8_u
                i32.const 32
                i32.and
                br_if 0 (;@6;)
                local.get 23
                local.get 28
                local.get 0
                call $__fwritex
                drop
              end
              block  ;; label = @6
                local.get 25
                i32.const 65536
                i32.ne
                br_if 0 (;@6;)
                local.get 27
                local.get 21
                i32.le_s
                br_if 0 (;@6;)
                local.get 5
                i32.const 624
                i32.add
                i32.const 48
                local.get 27
                local.get 21
                i32.sub
                local.tee 20
                i32.const 256
                local.get 20
                i32.const 256
                i32.lt_u
                local.tee 24
                select
                call $memset
                drop
                block  ;; label = @7
                  local.get 24
                  br_if 0 (;@7;)
                  loop  ;; label = @8
                    block  ;; label = @9
                      local.get 0
                      i32.load8_u
                      i32.const 32
                      i32.and
                      br_if 0 (;@9;)
                      local.get 5
                      i32.const 624
                      i32.add
                      i32.const 256
                      local.get 0
                      call $__fwritex
                      drop
                    end
                    local.get 20
                    i32.const -256
                    i32.add
                    local.tee 20
                    i32.const 255
                    i32.gt_u
                    br_if 0 (;@8;)
                  end
                end
                local.get 0
                i32.load8_u
                i32.const 32
                i32.and
                br_if 0 (;@6;)
                local.get 5
                i32.const 624
                i32.add
                local.get 20
                local.get 0
                call $__fwritex
                drop
              end
              block  ;; label = @6
                local.get 0
                i32.load8_u
                i32.const 32
                i32.and
                br_if 0 (;@6;)
                local.get 5
                i32.const 80
                i32.add
                local.get 22
                local.get 0
                call $__fwritex
                drop
              end
              block  ;; label = @6
                local.get 26
                local.get 22
                i32.sub
                local.tee 20
                i32.const 1
                i32.lt_s
                br_if 0 (;@6;)
                local.get 5
                i32.const 624
                i32.add
                i32.const 48
                local.get 20
                i32.const 256
                local.get 20
                i32.const 256
                i32.lt_u
                local.tee 22
                select
                call $memset
                drop
                block  ;; label = @7
                  local.get 22
                  br_if 0 (;@7;)
                  loop  ;; label = @8
                    block  ;; label = @9
                      local.get 0
                      i32.load8_u
                      i32.const 32
                      i32.and
                      br_if 0 (;@9;)
                      local.get 5
                      i32.const 624
                      i32.add
                      i32.const 256
                      local.get 0
                      call $__fwritex
                      drop
                    end
                    local.get 20
                    i32.const -256
                    i32.add
                    local.tee 20
                    i32.const 255
                    i32.gt_u
                    br_if 0 (;@8;)
                  end
                end
                local.get 0
                i32.load8_u
                i32.const 32
                i32.and
                br_if 0 (;@6;)
                local.get 5
                i32.const 624
                i32.add
                local.get 20
                local.get 0
                call $__fwritex
                drop
              end
              block  ;; label = @6
                local.get 0
                i32.load8_u
                i32.const 32
                i32.and
                br_if 0 (;@6;)
                local.get 29
                local.get 32
                local.get 0
                call $__fwritex
                drop
              end
              block  ;; label = @6
                local.get 25
                i32.const 8192
                i32.ne
                br_if 0 (;@6;)
                local.get 27
                local.get 21
                i32.le_s
                br_if 0 (;@6;)
                local.get 5
                i32.const 624
                i32.add
                i32.const 32
                local.get 27
                local.get 21
                i32.sub
                local.tee 20
                i32.const 256
                local.get 20
                i32.const 256
                i32.lt_u
                local.tee 22
                select
                call $memset
                drop
                block  ;; label = @7
                  local.get 22
                  br_if 0 (;@7;)
                  loop  ;; label = @8
                    block  ;; label = @9
                      local.get 0
                      i32.load8_u
                      i32.const 32
                      i32.and
                      br_if 0 (;@9;)
                      local.get 5
                      i32.const 624
                      i32.add
                      i32.const 256
                      local.get 0
                      call $__fwritex
                      drop
                    end
                    local.get 20
                    i32.const -256
                    i32.add
                    local.tee 20
                    i32.const 255
                    i32.gt_u
                    br_if 0 (;@8;)
                  end
                end
                local.get 0
                i32.load8_u
                i32.const 32
                i32.and
                br_if 0 (;@6;)
                local.get 5
                i32.const 624
                i32.add
                local.get 20
                local.get 0
                call $__fwritex
                drop
              end
              local.get 27
              local.get 21
              local.get 27
              local.get 21
              i32.gt_s
              select
              local.set 20
            end
            local.get 20
            i32.const 0
            i32.ge_s
            br_if 0 (;@4;)
          end
        end
        i32.const 0
        i32.const 61
        i32.store offset=4500
      end
      i32.const -1
      local.set 19
    end
    local.get 5
    i32.const 880
    i32.add
    global.set $__stack_pointer
    local.get 19)
  (func $pop_arg (type 11) (param i32 i32 i32)
    block  ;; label = @1
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
                            block  ;; label = @13
                              block  ;; label = @14
                                block  ;; label = @15
                                  block  ;; label = @16
                                    block  ;; label = @17
                                      block  ;; label = @18
                                        block  ;; label = @19
                                          local.get 1
                                          i32.const -9
                                          i32.add
                                          br_table 17 (;@2;) 0 (;@19;) 1 (;@18;) 4 (;@15;) 2 (;@17;) 3 (;@16;) 5 (;@14;) 6 (;@13;) 7 (;@12;) 8 (;@11;) 9 (;@10;) 10 (;@9;) 11 (;@8;) 12 (;@7;) 13 (;@6;) 14 (;@5;) 15 (;@4;) 16 (;@3;) 18 (;@1;)
                                        end
                                        local.get 2
                                        local.get 2
                                        i32.load
                                        local.tee 1
                                        i32.const 4
                                        i32.add
                                        i32.store
                                        local.get 0
                                        local.get 1
                                        i64.load32_s
                                        i64.store
                                        return
                                      end
                                      local.get 2
                                      local.get 2
                                      i32.load
                                      local.tee 1
                                      i32.const 4
                                      i32.add
                                      i32.store
                                      local.get 0
                                      local.get 1
                                      i64.load32_u
                                      i64.store
                                      return
                                    end
                                    local.get 2
                                    local.get 2
                                    i32.load
                                    local.tee 1
                                    i32.const 4
                                    i32.add
                                    i32.store
                                    local.get 0
                                    local.get 1
                                    i64.load32_s
                                    i64.store
                                    return
                                  end
                                  local.get 2
                                  local.get 2
                                  i32.load
                                  local.tee 1
                                  i32.const 4
                                  i32.add
                                  i32.store
                                  local.get 0
                                  local.get 1
                                  i64.load32_u
                                  i64.store
                                  return
                                end
                                local.get 2
                                local.get 2
                                i32.load
                                i32.const 7
                                i32.add
                                i32.const -8
                                i32.and
                                local.tee 1
                                i32.const 8
                                i32.add
                                i32.store
                                local.get 0
                                local.get 1
                                i64.load
                                i64.store
                                return
                              end
                              local.get 2
                              local.get 2
                              i32.load
                              local.tee 1
                              i32.const 4
                              i32.add
                              i32.store
                              local.get 0
                              local.get 1
                              i64.load16_s
                              i64.store
                              return
                            end
                            local.get 2
                            local.get 2
                            i32.load
                            local.tee 1
                            i32.const 4
                            i32.add
                            i32.store
                            local.get 0
                            local.get 1
                            i64.load16_u
                            i64.store
                            return
                          end
                          local.get 2
                          local.get 2
                          i32.load
                          local.tee 1
                          i32.const 4
                          i32.add
                          i32.store
                          local.get 0
                          local.get 1
                          i64.load8_s
                          i64.store
                          return
                        end
                        local.get 2
                        local.get 2
                        i32.load
                        local.tee 1
                        i32.const 4
                        i32.add
                        i32.store
                        local.get 0
                        local.get 1
                        i64.load8_u
                        i64.store
                        return
                      end
                      local.get 2
                      local.get 2
                      i32.load
                      i32.const 7
                      i32.add
                      i32.const -8
                      i32.and
                      local.tee 1
                      i32.const 8
                      i32.add
                      i32.store
                      local.get 0
                      local.get 1
                      i64.load
                      i64.store
                      return
                    end
                    local.get 2
                    local.get 2
                    i32.load
                    local.tee 1
                    i32.const 4
                    i32.add
                    i32.store
                    local.get 0
                    local.get 1
                    i64.load32_u
                    i64.store
                    return
                  end
                  local.get 2
                  local.get 2
                  i32.load
                  i32.const 7
                  i32.add
                  i32.const -8
                  i32.and
                  local.tee 1
                  i32.const 8
                  i32.add
                  i32.store
                  local.get 0
                  local.get 1
                  i64.load
                  i64.store
                  return
                end
                local.get 2
                local.get 2
                i32.load
                i32.const 7
                i32.add
                i32.const -8
                i32.and
                local.tee 1
                i32.const 8
                i32.add
                i32.store
                local.get 0
                local.get 1
                i64.load
                i64.store
                return
              end
              local.get 2
              local.get 2
              i32.load
              local.tee 1
              i32.const 4
              i32.add
              i32.store
              local.get 0
              local.get 1
              i64.load32_s
              i64.store
              return
            end
            local.get 2
            local.get 2
            i32.load
            local.tee 1
            i32.const 4
            i32.add
            i32.store
            local.get 0
            local.get 1
            i64.load32_u
            i64.store
            return
          end
          local.get 2
          local.get 2
          i32.load
          i32.const 7
          i32.add
          i32.const -8
          i32.and
          local.tee 1
          i32.const 8
          i32.add
          i32.store
          local.get 0
          local.get 1
          f64.load
          f64.store
          return
        end
        call $long_double_not_supported
        unreachable
      end
      local.get 2
      local.get 2
      i32.load
      local.tee 1
      i32.const 4
      i32.add
      i32.store
      local.get 0
      local.get 1
      i32.load
      i32.store
    end)
  (func $pad (type 12) (param i32 i32 i32 i32 i32)
    (local i32)
    global.get $__stack_pointer
    i32.const 256
    i32.sub
    local.tee 5
    global.set $__stack_pointer
    block  ;; label = @1
      local.get 2
      local.get 3
      i32.le_s
      br_if 0 (;@1;)
      local.get 4
      i32.const 73728
      i32.and
      br_if 0 (;@1;)
      local.get 5
      local.get 1
      local.get 2
      local.get 3
      i32.sub
      local.tee 2
      i32.const 256
      local.get 2
      i32.const 256
      i32.lt_u
      local.tee 4
      select
      call $memset
      local.set 3
      block  ;; label = @2
        local.get 4
        br_if 0 (;@2;)
        loop  ;; label = @3
          block  ;; label = @4
            local.get 0
            i32.load8_u
            i32.const 32
            i32.and
            br_if 0 (;@4;)
            local.get 3
            i32.const 256
            local.get 0
            call $__fwritex
            drop
          end
          local.get 2
          i32.const -256
          i32.add
          local.tee 2
          i32.const 255
          i32.gt_u
          br_if 0 (;@3;)
        end
      end
      local.get 0
      i32.load8_u
      i32.const 32
      i32.and
      br_if 0 (;@1;)
      local.get 3
      local.get 2
      local.get 0
      call $__fwritex
      drop
    end
    local.get 5
    i32.const 256
    i32.add
    global.set $__stack_pointer)
  (func $long_double_not_supported (type 7)
    i32.const 1420
    i32.const 3864
    call $fputs
    drop
    call $abort
    unreachable)
  (func $__isatty (type 4) (param i32) (result i32)
    (local i32 i32)
    global.get $__stack_pointer
    i32.const 32
    i32.sub
    local.tee 1
    global.set $__stack_pointer
    block  ;; label = @1
      block  ;; label = @2
        local.get 0
        local.get 1
        i32.const 8
        i32.add
        call $__wasi_fd_fdstat_get
        local.tee 0
        br_if 0 (;@2;)
        i32.const 59
        local.set 0
        local.get 1
        i32.load8_u offset=8
        i32.const 2
        i32.ne
        br_if 0 (;@2;)
        local.get 1
        i32.load8_u offset=16
        i32.const 36
        i32.and
        br_if 0 (;@2;)
        i32.const 1
        local.set 2
        br 1 (;@1;)
      end
      i32.const 0
      local.set 2
      i32.const 0
      local.get 0
      i32.store offset=4500
    end
    local.get 1
    i32.const 32
    i32.add
    global.set $__stack_pointer
    local.get 2)
  (func $__stdout_write (type 1) (param i32 i32 i32) (result i32)
    local.get 0
    i32.const 8
    i32.store offset=32
    block  ;; label = @1
      local.get 0
      i32.load8_u
      i32.const 64
      i32.and
      br_if 0 (;@1;)
      local.get 0
      i32.load offset=56
      call $__isatty
      br_if 0 (;@1;)
      local.get 0
      i32.const -1
      i32.store offset=64
    end
    local.get 0
    local.get 1
    local.get 2
    call $__stdio_write)
  (func $strncpy (type 1) (param i32 i32 i32) (result i32)
    local.get 0
    local.get 1
    local.get 2
    call $__stpncpy
    drop
    local.get 0)
  (func $strnlen (type 3) (param i32 i32) (result i32)
    (local i32)
    local.get 0
    i32.const 0
    local.get 1
    call $memchr
    local.tee 2
    local.get 0
    i32.sub
    local.get 1
    local.get 2
    select)
  (func $__stpncpy (type 1) (param i32 i32 i32) (result i32)
    (local i32 i32 i32 i32)
    block  ;; label = @1
      block  ;; label = @2
        block  ;; label = @3
          local.get 1
          local.get 0
          i32.xor
          i32.const 3
          i32.and
          br_if 0 (;@3;)
          local.get 2
          i32.const 0
          i32.ne
          local.set 3
          block  ;; label = @4
            local.get 1
            i32.const 3
            i32.and
            i32.eqz
            br_if 0 (;@4;)
            local.get 2
            i32.eqz
            br_if 0 (;@4;)
            local.get 0
            local.get 1
            i32.load8_u
            local.tee 4
            i32.store8
            local.get 4
            i32.eqz
            br_if 2 (;@2;)
            local.get 0
            i32.const 1
            i32.add
            local.set 4
            local.get 2
            i32.const -1
            i32.add
            local.tee 5
            i32.const 0
            i32.ne
            local.set 3
            block  ;; label = @5
              local.get 1
              i32.const 1
              i32.add
              local.tee 6
              i32.const 3
              i32.and
              i32.eqz
              br_if 0 (;@5;)
              local.get 5
              i32.eqz
              br_if 0 (;@5;)
              local.get 4
              local.get 6
              i32.load8_u
              local.tee 3
              i32.store8
              local.get 3
              i32.eqz
              br_if 4 (;@1;)
              local.get 0
              i32.const 2
              i32.add
              local.set 4
              local.get 2
              i32.const -2
              i32.add
              local.tee 5
              i32.const 0
              i32.ne
              local.set 3
              block  ;; label = @6
                local.get 1
                i32.const 2
                i32.add
                local.tee 6
                i32.const 3
                i32.and
                i32.eqz
                br_if 0 (;@6;)
                local.get 5
                i32.eqz
                br_if 0 (;@6;)
                local.get 4
                local.get 6
                i32.load8_u
                local.tee 3
                i32.store8
                local.get 3
                i32.eqz
                br_if 5 (;@1;)
                local.get 0
                i32.const 3
                i32.add
                local.set 4
                local.get 2
                i32.const -3
                i32.add
                local.tee 5
                i32.const 0
                i32.ne
                local.set 3
                block  ;; label = @7
                  local.get 1
                  i32.const 3
                  i32.add
                  local.tee 6
                  i32.const 3
                  i32.and
                  i32.eqz
                  br_if 0 (;@7;)
                  local.get 5
                  i32.eqz
                  br_if 0 (;@7;)
                  local.get 4
                  local.get 6
                  i32.load8_u
                  local.tee 3
                  i32.store8
                  local.get 3
                  i32.eqz
                  br_if 6 (;@1;)
                  local.get 0
                  i32.const 4
                  i32.add
                  local.set 0
                  local.get 1
                  i32.const 4
                  i32.add
                  local.set 1
                  local.get 2
                  i32.const -4
                  i32.add
                  local.tee 2
                  i32.const 0
                  i32.ne
                  local.set 3
                  br 3 (;@4;)
                end
                local.get 4
                local.set 0
                local.get 6
                local.set 1
                local.get 5
                local.set 2
                br 2 (;@4;)
              end
              local.get 4
              local.set 0
              local.get 6
              local.set 1
              local.get 5
              local.set 2
              br 1 (;@4;)
            end
            local.get 4
            local.set 0
            local.get 6
            local.set 1
            local.get 5
            local.set 2
          end
          block  ;; label = @4
            local.get 3
            br_if 0 (;@4;)
            local.get 0
            local.set 4
            i32.const 0
            local.set 5
            br 3 (;@1;)
          end
          local.get 1
          i32.load8_u
          i32.eqz
          br_if 1 (;@2;)
          local.get 2
          i32.const 4
          i32.lt_u
          br_if 0 (;@3;)
          loop  ;; label = @4
            local.get 1
            i32.load
            local.tee 4
            i32.const -1
            i32.xor
            local.get 4
            i32.const -16843009
            i32.add
            i32.and
            i32.const -2139062144
            i32.and
            br_if 1 (;@3;)
            local.get 0
            local.get 4
            i32.store
            local.get 0
            i32.const 4
            i32.add
            local.set 0
            local.get 1
            i32.const 4
            i32.add
            local.set 1
            local.get 2
            i32.const -4
            i32.add
            local.tee 2
            i32.const 3
            i32.gt_u
            br_if 0 (;@4;)
          end
        end
        i32.const 0
        local.set 5
        block  ;; label = @3
          local.get 2
          i32.eqz
          br_if 0 (;@3;)
          loop  ;; label = @4
            local.get 0
            local.get 1
            i32.load8_u
            local.tee 4
            i32.store8
            local.get 4
            i32.eqz
            br_if 2 (;@2;)
            local.get 0
            i32.const 1
            i32.add
            local.set 0
            local.get 1
            i32.const 1
            i32.add
            local.set 1
            local.get 2
            i32.const -1
            i32.add
            local.tee 2
            br_if 0 (;@4;)
          end
        end
        local.get 0
        local.set 4
        br 1 (;@1;)
      end
      local.get 0
      local.set 4
      local.get 2
      local.set 5
    end
    local.get 4
    i32.const 0
    local.get 5
    call $memset)
  (func $memcpy (type 1) (param i32 i32 i32) (result i32)
    (local i32 i32 i32 i32)
    block  ;; label = @1
      block  ;; label = @2
        block  ;; label = @3
          local.get 2
          i32.const 32
          i32.gt_u
          br_if 0 (;@3;)
          local.get 1
          i32.const 3
          i32.and
          i32.eqz
          br_if 1 (;@2;)
          local.get 2
          i32.eqz
          br_if 1 (;@2;)
          local.get 0
          local.get 1
          i32.load8_u
          i32.store8
          local.get 2
          i32.const -1
          i32.add
          local.set 3
          local.get 0
          i32.const 1
          i32.add
          local.set 4
          local.get 1
          i32.const 1
          i32.add
          local.tee 5
          i32.const 3
          i32.and
          i32.eqz
          br_if 2 (;@1;)
          local.get 3
          i32.eqz
          br_if 2 (;@1;)
          local.get 0
          local.get 1
          i32.load8_u offset=1
          i32.store8 offset=1
          local.get 2
          i32.const -2
          i32.add
          local.set 3
          local.get 0
          i32.const 2
          i32.add
          local.set 4
          local.get 1
          i32.const 2
          i32.add
          local.tee 5
          i32.const 3
          i32.and
          i32.eqz
          br_if 2 (;@1;)
          local.get 3
          i32.eqz
          br_if 2 (;@1;)
          local.get 0
          local.get 1
          i32.load8_u offset=2
          i32.store8 offset=2
          local.get 2
          i32.const -3
          i32.add
          local.set 3
          local.get 0
          i32.const 3
          i32.add
          local.set 4
          local.get 1
          i32.const 3
          i32.add
          local.tee 5
          i32.const 3
          i32.and
          i32.eqz
          br_if 2 (;@1;)
          local.get 3
          i32.eqz
          br_if 2 (;@1;)
          local.get 0
          local.get 1
          i32.load8_u offset=3
          i32.store8 offset=3
          local.get 2
          i32.const -4
          i32.add
          local.set 3
          local.get 0
          i32.const 4
          i32.add
          local.set 4
          local.get 1
          i32.const 4
          i32.add
          local.set 5
          br 2 (;@1;)
        end
        local.get 0
        local.get 1
        local.get 2
        memory.copy
        local.get 0
        return
      end
      local.get 2
      local.set 3
      local.get 0
      local.set 4
      local.get 1
      local.set 5
    end
    block  ;; label = @1
      block  ;; label = @2
        local.get 4
        i32.const 3
        i32.and
        local.tee 2
        br_if 0 (;@2;)
        block  ;; label = @3
          block  ;; label = @4
            local.get 3
            i32.const 16
            i32.lt_u
            br_if 0 (;@4;)
            block  ;; label = @5
              local.get 3
              i32.const -16
              i32.add
              local.tee 2
              i32.const 16
              i32.and
              br_if 0 (;@5;)
              local.get 4
              local.get 5
              i64.load align=4
              i64.store align=4
              local.get 4
              local.get 5
              i64.load offset=8 align=4
              i64.store offset=8 align=4
              local.get 4
              i32.const 16
              i32.add
              local.set 4
              local.get 5
              i32.const 16
              i32.add
              local.set 5
              local.get 2
              local.set 3
            end
            local.get 2
            i32.const 16
            i32.lt_u
            br_if 1 (;@3;)
            loop  ;; label = @5
              local.get 4
              local.get 5
              i64.load align=4
              i64.store align=4
              local.get 4
              i32.const 8
              i32.add
              local.get 5
              i32.const 8
              i32.add
              i64.load align=4
              i64.store align=4
              local.get 4
              i32.const 16
              i32.add
              local.get 5
              i32.const 16
              i32.add
              i64.load align=4
              i64.store align=4
              local.get 4
              i32.const 24
              i32.add
              local.get 5
              i32.const 24
              i32.add
              i64.load align=4
              i64.store align=4
              local.get 4
              i32.const 32
              i32.add
              local.set 4
              local.get 5
              i32.const 32
              i32.add
              local.set 5
              local.get 3
              i32.const -32
              i32.add
              local.tee 3
              i32.const 15
              i32.gt_u
              br_if 0 (;@5;)
            end
          end
          local.get 3
          local.set 2
        end
        block  ;; label = @3
          local.get 2
          i32.const 8
          i32.and
          i32.eqz
          br_if 0 (;@3;)
          local.get 4
          local.get 5
          i64.load align=4
          i64.store align=4
          local.get 5
          i32.const 8
          i32.add
          local.set 5
          local.get 4
          i32.const 8
          i32.add
          local.set 4
        end
        block  ;; label = @3
          local.get 2
          i32.const 4
          i32.and
          i32.eqz
          br_if 0 (;@3;)
          local.get 4
          local.get 5
          i32.load
          i32.store
          local.get 5
          i32.const 4
          i32.add
          local.set 5
          local.get 4
          i32.const 4
          i32.add
          local.set 4
        end
        block  ;; label = @3
          local.get 2
          i32.const 2
          i32.and
          i32.eqz
          br_if 0 (;@3;)
          local.get 4
          local.get 5
          i32.load16_u align=1
          i32.store16 align=1
          local.get 4
          i32.const 2
          i32.add
          local.set 4
          local.get 5
          i32.const 2
          i32.add
          local.set 5
        end
        local.get 2
        i32.const 1
        i32.and
        i32.eqz
        br_if 1 (;@1;)
        local.get 4
        local.get 5
        i32.load8_u
        i32.store8
        local.get 0
        return
      end
      block  ;; label = @2
        block  ;; label = @3
          block  ;; label = @4
            block  ;; label = @5
              block  ;; label = @6
                local.get 3
                i32.const 32
                i32.lt_u
                br_if 0 (;@6;)
                block  ;; label = @7
                  block  ;; label = @8
                    local.get 2
                    i32.const -1
                    i32.add
                    br_table 3 (;@5;) 0 (;@8;) 1 (;@7;) 7 (;@1;)
                  end
                  local.get 4
                  local.get 5
                  i32.load
                  i32.store16 align=1
                  local.get 4
                  local.get 5
                  i32.const 2
                  i32.add
                  i32.load align=2
                  i32.store offset=2
                  local.get 4
                  local.get 5
                  i32.const 6
                  i32.add
                  i64.load align=2
                  i64.store offset=6 align=4
                  i32.const 14
                  local.set 1
                  local.get 5
                  i32.const 14
                  i32.add
                  i32.load align=2
                  local.set 6
                  i32.const 18
                  local.set 2
                  i32.const 14
                  local.set 3
                  br 3 (;@4;)
                end
                local.get 4
                local.get 5
                i32.load
                i32.store8
                local.get 4
                local.get 5
                i32.const 1
                i32.add
                i32.load align=1
                i32.store offset=1
                local.get 4
                local.get 5
                i32.const 5
                i32.add
                i64.load align=1
                i64.store offset=5 align=4
                i32.const 13
                local.set 1
                local.get 5
                i32.const 13
                i32.add
                i32.load align=1
                local.set 6
                i32.const 15
                local.set 3
                i32.const 17
                local.set 2
                br 2 (;@4;)
              end
              block  ;; label = @6
                local.get 3
                i32.const 16
                i32.and
                i32.eqz
                br_if 0 (;@6;)
                local.get 4
                local.get 5
                i32.load8_u
                i32.store8
                local.get 4
                local.get 5
                i32.load offset=1 align=1
                i32.store offset=1 align=1
                local.get 4
                local.get 5
                i64.load offset=5 align=1
                i64.store offset=5 align=1
                local.get 4
                local.get 5
                i32.load16_u offset=13 align=1
                i32.store16 offset=13 align=1
                local.get 4
                local.get 5
                i32.load8_u offset=15
                i32.store8 offset=15
                local.get 4
                i32.const 16
                i32.add
                local.set 4
                local.get 5
                i32.const 16
                i32.add
                local.set 5
              end
              local.get 3
              i32.const 8
              i32.and
              br_if 2 (;@3;)
              br 3 (;@2;)
            end
            local.get 4
            local.get 5
            i32.load
            local.tee 3
            i32.store8
            local.get 4
            local.get 3
            i32.const 16
            i32.shr_u
            i32.store8 offset=2
            local.get 4
            local.get 3
            i32.const 8
            i32.shr_u
            i32.store8 offset=1
            local.get 4
            local.get 5
            i32.const 3
            i32.add
            i32.load align=1
            i32.store offset=3
            local.get 4
            local.get 5
            i32.const 7
            i32.add
            i64.load align=1
            i64.store offset=7 align=4
            i32.const 15
            local.set 1
            local.get 5
            i32.const 15
            i32.add
            i32.load align=1
            local.set 6
            i32.const 13
            local.set 3
            i32.const 19
            local.set 2
          end
          local.get 4
          local.get 1
          i32.add
          local.get 6
          i32.store
          local.get 4
          local.get 2
          i32.add
          local.set 4
          local.get 5
          local.get 2
          i32.add
          local.set 5
        end
        local.get 4
        local.get 5
        i64.load align=1
        i64.store align=1
        local.get 4
        i32.const 8
        i32.add
        local.set 4
        local.get 5
        i32.const 8
        i32.add
        local.set 5
      end
      block  ;; label = @2
        local.get 3
        i32.const 4
        i32.and
        i32.eqz
        br_if 0 (;@2;)
        local.get 4
        local.get 5
        i32.load align=1
        i32.store align=1
        local.get 4
        i32.const 4
        i32.add
        local.set 4
        local.get 5
        i32.const 4
        i32.add
        local.set 5
      end
      block  ;; label = @2
        local.get 3
        i32.const 2
        i32.and
        i32.eqz
        br_if 0 (;@2;)
        local.get 4
        local.get 5
        i32.load16_u align=1
        i32.store16 align=1
        local.get 4
        i32.const 2
        i32.add
        local.set 4
        local.get 5
        i32.const 2
        i32.add
        local.set 5
      end
      local.get 3
      i32.const 1
      i32.and
      i32.eqz
      br_if 0 (;@1;)
      local.get 4
      local.get 5
      i32.load8_u
      i32.store8
    end
    local.get 0)
  (func $memset (type 1) (param i32 i32 i32) (result i32)
    (local i32 i32 i32 i64)
    block  ;; label = @1
      local.get 2
      i32.const 33
      i32.lt_u
      br_if 0 (;@1;)
      local.get 0
      local.get 1
      local.get 2
      memory.fill
      local.get 0
      return
    end
    block  ;; label = @1
      local.get 2
      i32.eqz
      br_if 0 (;@1;)
      local.get 0
      local.get 1
      i32.store8
      local.get 2
      local.get 0
      i32.add
      local.tee 3
      i32.const -1
      i32.add
      local.get 1
      i32.store8
      local.get 2
      i32.const 3
      i32.lt_u
      br_if 0 (;@1;)
      local.get 0
      local.get 1
      i32.store8 offset=2
      local.get 0
      local.get 1
      i32.store8 offset=1
      local.get 3
      i32.const -3
      i32.add
      local.get 1
      i32.store8
      local.get 3
      i32.const -2
      i32.add
      local.get 1
      i32.store8
      local.get 2
      i32.const 7
      i32.lt_u
      br_if 0 (;@1;)
      local.get 0
      local.get 1
      i32.store8 offset=3
      local.get 3
      i32.const -4
      i32.add
      local.get 1
      i32.store8
      local.get 2
      i32.const 9
      i32.lt_u
      br_if 0 (;@1;)
      local.get 0
      i32.const 0
      local.get 0
      i32.sub
      i32.const 3
      i32.and
      local.tee 4
      i32.add
      local.tee 5
      local.get 1
      i32.const 255
      i32.and
      i32.const 16843009
      i32.mul
      local.tee 3
      i32.store
      local.get 5
      local.get 2
      local.get 4
      i32.sub
      i32.const -4
      i32.and
      local.tee 1
      i32.add
      local.tee 2
      i32.const -4
      i32.add
      local.get 3
      i32.store
      local.get 1
      i32.const 9
      i32.lt_u
      br_if 0 (;@1;)
      local.get 5
      local.get 3
      i32.store offset=8
      local.get 5
      local.get 3
      i32.store offset=4
      local.get 2
      i32.const -8
      i32.add
      local.get 3
      i32.store
      local.get 2
      i32.const -12
      i32.add
      local.get 3
      i32.store
      local.get 1
      i32.const 25
      i32.lt_u
      br_if 0 (;@1;)
      local.get 5
      local.get 3
      i32.store offset=24
      local.get 5
      local.get 3
      i32.store offset=20
      local.get 5
      local.get 3
      i32.store offset=16
      local.get 5
      local.get 3
      i32.store offset=12
      local.get 2
      i32.const -16
      i32.add
      local.get 3
      i32.store
      local.get 2
      i32.const -20
      i32.add
      local.get 3
      i32.store
      local.get 2
      i32.const -24
      i32.add
      local.get 3
      i32.store
      local.get 2
      i32.const -28
      i32.add
      local.get 3
      i32.store
      local.get 1
      local.get 5
      i32.const 4
      i32.and
      i32.const 24
      i32.or
      local.tee 2
      i32.sub
      local.tee 1
      i32.const 32
      i32.lt_u
      br_if 0 (;@1;)
      local.get 3
      i64.extend_i32_u
      i64.const 4294967297
      i64.mul
      local.set 6
      local.get 5
      local.get 2
      i32.add
      local.set 2
      loop  ;; label = @2
        local.get 2
        local.get 6
        i64.store
        local.get 2
        i32.const 24
        i32.add
        local.get 6
        i64.store
        local.get 2
        i32.const 16
        i32.add
        local.get 6
        i64.store
        local.get 2
        i32.const 8
        i32.add
        local.get 6
        i64.store
        local.get 2
        i32.const 32
        i32.add
        local.set 2
        local.get 1
        i32.const -32
        i32.add
        local.tee 1
        i32.const 31
        i32.gt_u
        br_if 0 (;@2;)
      end
    end
    local.get 0)
  (func $strlen (type 4) (param i32) (result i32)
    (local i32 i32)
    local.get 0
    local.set 1
    block  ;; label = @1
      block  ;; label = @2
        local.get 0
        i32.const 3
        i32.and
        i32.eqz
        br_if 0 (;@2;)
        local.get 0
        local.set 1
        local.get 0
        i32.load8_u
        i32.eqz
        br_if 1 (;@1;)
        local.get 0
        i32.const 1
        i32.add
        local.tee 1
        i32.const 3
        i32.and
        i32.eqz
        br_if 0 (;@2;)
        local.get 1
        i32.load8_u
        i32.eqz
        br_if 1 (;@1;)
        local.get 0
        i32.const 2
        i32.add
        local.tee 1
        i32.const 3
        i32.and
        i32.eqz
        br_if 0 (;@2;)
        local.get 1
        i32.load8_u
        i32.eqz
        br_if 1 (;@1;)
        local.get 0
        i32.const 3
        i32.add
        local.tee 1
        i32.const 3
        i32.and
        i32.eqz
        br_if 0 (;@2;)
        local.get 1
        i32.load8_u
        i32.eqz
        br_if 1 (;@1;)
        local.get 0
        i32.const 4
        i32.add
        local.set 1
      end
      local.get 1
      i32.const -4
      i32.add
      local.set 1
      loop  ;; label = @2
        local.get 1
        i32.const 4
        i32.add
        local.tee 1
        i32.load
        local.tee 2
        i32.const -1
        i32.xor
        local.get 2
        i32.const -16843009
        i32.add
        i32.and
        i32.const -2139062144
        i32.and
        i32.eqz
        br_if 0 (;@2;)
      end
      local.get 2
      i32.const 255
      i32.and
      i32.eqz
      br_if 0 (;@1;)
      loop  ;; label = @2
        local.get 1
        i32.const 1
        i32.add
        local.tee 1
        i32.load8_u
        br_if 0 (;@2;)
      end
    end
    local.get 1
    local.get 0
    i32.sub)
  (func $memchr (type 1) (param i32 i32 i32) (result i32)
    (local i32 i32 i32)
    local.get 2
    i32.const 0
    i32.ne
    local.set 3
    block  ;; label = @1
      block  ;; label = @2
        block  ;; label = @3
          block  ;; label = @4
            local.get 0
            i32.const 3
            i32.and
            i32.eqz
            br_if 0 (;@4;)
            local.get 2
            i32.eqz
            br_if 0 (;@4;)
            block  ;; label = @5
              local.get 0
              i32.load8_u
              local.get 1
              i32.const 255
              i32.and
              i32.ne
              br_if 0 (;@5;)
              local.get 0
              local.set 4
              local.get 2
              local.set 5
              br 3 (;@2;)
            end
            local.get 2
            i32.const -1
            i32.add
            local.tee 5
            i32.const 0
            i32.ne
            local.set 3
            local.get 0
            i32.const 1
            i32.add
            local.tee 4
            i32.const 3
            i32.and
            i32.eqz
            br_if 1 (;@3;)
            local.get 5
            i32.eqz
            br_if 1 (;@3;)
            local.get 4
            i32.load8_u
            local.get 1
            i32.const 255
            i32.and
            i32.eq
            br_if 2 (;@2;)
            local.get 2
            i32.const -2
            i32.add
            local.tee 5
            i32.const 0
            i32.ne
            local.set 3
            local.get 0
            i32.const 2
            i32.add
            local.tee 4
            i32.const 3
            i32.and
            i32.eqz
            br_if 1 (;@3;)
            local.get 5
            i32.eqz
            br_if 1 (;@3;)
            local.get 4
            i32.load8_u
            local.get 1
            i32.const 255
            i32.and
            i32.eq
            br_if 2 (;@2;)
            local.get 2
            i32.const -3
            i32.add
            local.tee 5
            i32.const 0
            i32.ne
            local.set 3
            local.get 0
            i32.const 3
            i32.add
            local.tee 4
            i32.const 3
            i32.and
            i32.eqz
            br_if 1 (;@3;)
            local.get 5
            i32.eqz
            br_if 1 (;@3;)
            local.get 4
            i32.load8_u
            local.get 1
            i32.const 255
            i32.and
            i32.eq
            br_if 2 (;@2;)
            local.get 0
            i32.const 4
            i32.add
            local.set 4
            local.get 2
            i32.const -4
            i32.add
            local.tee 5
            i32.const 0
            i32.ne
            local.set 3
            br 1 (;@3;)
          end
          local.get 2
          local.set 5
          local.get 0
          local.set 4
        end
        local.get 3
        i32.eqz
        br_if 1 (;@1;)
      end
      block  ;; label = @2
        local.get 4
        i32.load8_u
        local.get 1
        i32.const 255
        i32.and
        i32.eq
        br_if 0 (;@2;)
        local.get 5
        i32.const 4
        i32.lt_u
        br_if 0 (;@2;)
        local.get 1
        i32.const 255
        i32.and
        i32.const 16843009
        i32.mul
        local.set 0
        loop  ;; label = @3
          local.get 4
          i32.load
          local.get 0
          i32.xor
          local.tee 2
          i32.const -1
          i32.xor
          local.get 2
          i32.const -16843009
          i32.add
          i32.and
          i32.const -2139062144
          i32.and
          br_if 1 (;@2;)
          local.get 4
          i32.const 4
          i32.add
          local.set 4
          local.get 5
          i32.const -4
          i32.add
          local.tee 5
          i32.const 3
          i32.gt_u
          br_if 0 (;@3;)
        end
      end
      local.get 5
      i32.eqz
      br_if 0 (;@1;)
      local.get 1
      i32.const 255
      i32.and
      local.set 2
      loop  ;; label = @2
        block  ;; label = @3
          local.get 4
          i32.load8_u
          local.get 2
          i32.ne
          br_if 0 (;@3;)
          local.get 4
          return
        end
        local.get 4
        i32.const 1
        i32.add
        local.set 4
        local.get 5
        i32.const -1
        i32.add
        local.tee 5
        br_if 0 (;@2;)
      end
    end
    i32.const 0)
  (func $dummy.1 (type 3) (param i32 i32) (result i32)
    local.get 0)
  (func $__lctrans (type 3) (param i32 i32) (result i32)
    local.get 0
    local.get 1
    call $dummy.1)
  (func $wctomb (type 3) (param i32 i32) (result i32)
    block  ;; label = @1
      local.get 0
      br_if 0 (;@1;)
      i32.const 0
      return
    end
    local.get 0
    local.get 1
    i32.const 0
    call $wcrtomb)
  (func $wcrtomb (type 1) (param i32 i32 i32) (result i32)
    (local i32)
    i32.const 1
    local.set 3
    block  ;; label = @1
      local.get 0
      i32.eqz
      br_if 0 (;@1;)
      block  ;; label = @2
        local.get 1
        i32.const 127
        i32.gt_u
        br_if 0 (;@2;)
        local.get 0
        local.get 1
        i32.store8
        i32.const 1
        return
      end
      block  ;; label = @2
        block  ;; label = @3
          i32.const 0
          i32.load offset=5552
          br_if 0 (;@3;)
          block  ;; label = @4
            local.get 1
            i32.const -128
            i32.and
            i32.const 57216
            i32.eq
            br_if 0 (;@4;)
            i32.const 0
            i32.const 25
            i32.store offset=4500
            br 2 (;@2;)
          end
          local.get 0
          local.get 1
          i32.store8
          i32.const 1
          return
        end
        block  ;; label = @3
          local.get 1
          i32.const 2047
          i32.gt_u
          br_if 0 (;@3;)
          local.get 0
          local.get 1
          i32.const 63
          i32.and
          i32.const 128
          i32.or
          i32.store8 offset=1
          local.get 0
          local.get 1
          i32.const 6
          i32.shr_u
          i32.const 192
          i32.or
          i32.store8
          i32.const 2
          return
        end
        block  ;; label = @3
          block  ;; label = @4
            local.get 1
            i32.const 55296
            i32.lt_u
            br_if 0 (;@4;)
            local.get 1
            i32.const -8192
            i32.and
            i32.const 57344
            i32.ne
            br_if 1 (;@3;)
          end
          local.get 0
          local.get 1
          i32.const 63
          i32.and
          i32.const 128
          i32.or
          i32.store8 offset=2
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
          i32.const 3
          return
        end
        block  ;; label = @3
          local.get 1
          i32.const -65536
          i32.add
          i32.const 1048575
          i32.gt_u
          br_if 0 (;@3;)
          local.get 0
          local.get 1
          i32.const 63
          i32.and
          i32.const 128
          i32.or
          i32.store8 offset=3
          local.get 0
          local.get 1
          i32.const 18
          i32.shr_u
          i32.const 240
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
          i32.store8 offset=2
          local.get 0
          local.get 1
          i32.const 12
          i32.shr_u
          i32.const 63
          i32.and
          i32.const 128
          i32.or
          i32.store8 offset=1
          i32.const 4
          return
        end
        i32.const 0
        i32.const 25
        i32.store offset=4500
      end
      i32.const -1
      local.set 3
    end
    local.get 3)
  (func $frexp (type 13) (param f64 i32) (result f64)
    (local i64 i32)
    block  ;; label = @1
      local.get 0
      i64.reinterpret_f64
      local.tee 2
      i64.const 52
      i64.shr_u
      i32.wrap_i64
      i32.const 2047
      i32.and
      local.tee 3
      i32.const 2047
      i32.eq
      br_if 0 (;@1;)
      block  ;; label = @2
        local.get 3
        br_if 0 (;@2;)
        block  ;; label = @3
          local.get 0
          f64.const 0x0p+0 (;=0;)
          f64.ne
          br_if 0 (;@3;)
          local.get 1
          i32.const 0
          i32.store
          local.get 0
          return
        end
        local.get 0
        f64.const 0x1p+64 (;=1.84467e+19;)
        f64.mul
        local.get 1
        call $frexp
        local.set 0
        local.get 1
        local.get 1
        i32.load
        i32.const -64
        i32.add
        i32.store
        local.get 0
        return
      end
      local.get 1
      local.get 3
      i32.const -1022
      i32.add
      i32.store
      local.get 2
      i64.const -9218868437227405313
      i64.and
      i64.const 4602678819172646912
      i64.or
      f64.reinterpret_i64
      local.set 0
    end
    local.get 0)
  (func $_start.command_export (type 7)
    call $_start
    call $__wasm_call_dtors)
  (func $run.command_export (type 9) (result i32)
    call $run
    call $__wasm_call_dtors)
  (table (;0;) 9 9 funcref)
  (memory (;0;) 2)
  (global $__stack_pointer (mut i32) (i32.const 71136))
  (export "memory" (memory 0))
  (export "_start" (func $_start.command_export))
  (export "run" (func $run.command_export))
  (elem (;0;) (i32.const 1) func $Noop $EqualsC_Execute $ScaleOffsetC_Execute $__stdio_close $__stdout_write $__stdio_seek $sn_write $__stdio_write)
  (data $.rodata (i32.const 1024) "out of memory\00-+   0X0x\00-0X+0X 0X-0x+0x 0x\00Could not satisfy a required constraint\00offset\00nan\00inf\00scale\00v%ld\00dest%ld\00src%ld\00Cycle encountered\00NAN\00INF\00bad ListStruct; already freed?\00bad ConstraintStruct; already freed?\00bad VariableStruct; already freed?\00.\00(null)\00ChainTest failed!\00Projection Test 4 failed!\00Projection Test 3 failed!\00Projection Test 2 failed!\00Projection Test 1 failed!\00error: %s.\0a\00Support for formatting long double values is currently disabled.\0aTo enable it, add -lc-printscan-long-double to the link command.\0a\00Success\00Illegal byte sequence\00Domain error\00Result not representable\00Not a tty\00Permission denied\00Operation not permitted\00No such file or directory\00No such process\00File exists\00Value too large for data type\00No space left on device\00Out of memory\00Resource busy\00Interrupted system call\00Resource temporarily unavailable\00Invalid seek\00Cross-device link\00Read-only file system\00Directory not empty\00Connection reset by peer\00Operation timed out\00Connection refused\00Host is unreachable\00Address in use\00Broken pipe\00I/O error\00No such device or address\00No such device\00Not a directory\00Is a directory\00Text file busy\00Exec format error\00Invalid argument\00Argument list too long\00Symbolic link loop\00Filename too long\00Too many open files in system\00No file descriptors available\00Bad file descriptor\00No child process\00Bad address\00File too large\00Too many links\00No locks available\00Resource deadlock would occur\00State not recoverable\00Previous owner died\00Operation canceled\00Function not implemented\00No message of desired type\00Identifier removed\00Link has been severed\00Protocol error\00Bad message\00Not a socket\00Destination address required\00Message too large\00Protocol wrong type for socket\00Protocol not available\00Protocol not supported\00Not supported\00Address family not supported by protocol\00Address not available\00Network is down\00Network unreachable\00Connection reset by network\00Connection aborted\00No buffer space available\00Socket is connected\00Socket not connected\00Operation already in progress\00Operation in progress\00Stale file handle\00Quota exceeded\00Multihop attempted\00Capabilities insufficient\00\00\00\00u\02N\00\d6\01\e2\04\b9\04\18\01\8e\05\ed\02\16\04\f2\00\97\03\01\038\05\af\01\82\01O\03/\04\1e\00\d4\05\a2\00\12\03\1e\03\c2\01\de\03\08\00\ac\05\00\01d\02\f1\01e\054\02\8c\02\cf\02-\03L\04\e3\05\9f\02\f8\04\1c\05\08\05\b1\02K\05\15\02x\00R\02<\03\f1\03\e4\00\c3\03}\04\cc\00\aa\03y\05$\02n\01m\03\22\04\ab\04D\00\fb\01\ae\00\83\03`\00\e5\01\07\04\94\04^\04+\00X\019\01\92\00\c2\05\9b\01C\02F\01\f6\05\00\00\00\00\00\00\19\00\0a\00\19\19\19\00\00\00\00\05\00\00\00\00\00\00\09\00\00\00\00\0b\00\00\00\00\00\00\00\00\19\00\11\0a\19\19\19\03\0a\07\00\01\1b\09\0b\18\00\00\09\06\0b\00\00\0b\00\06\19\00\00\00\19\19\19\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\0e\00\00\00\00\00\00\00\00\19\00\0a\0d\19\19\19\00\0d\00\00\02\00\09\0e\00\00\00\09\00\0e\00\00\0e\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\0c\00\00\00\00\00\00\00\00\00\00\00\13\00\00\00\00\13\00\00\00\00\09\0c\00\00\00\00\00\0c\00\00\0c\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\10\00\00\00\00\00\00\00\00\00\00\00\0f\00\00\00\04\0f\00\00\00\00\09\10\00\00\00\00\00\10\00\00\10\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\12\00\00\00\00\00\00\00\00\00\00\00\11\00\00\00\00\11\00\00\00\00\09\12\00\00\00\00\00\12\00\00\12\00\00\1a\00\00\00\1a\1a\1a\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\1a\00\00\00\1a\1a\1a\00\00\00\00\00\00\09\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\14\00\00\00\00\00\00\00\00\00\00\00\17\00\00\00\00\17\00\00\00\00\09\14\00\00\00\00\00\14\00\00\14\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\16\00\00\00\00\00\00\00\00\00\00\00\15\00\00\00\00\15\00\00\00\00\09\16\00\00\00\00\00\16\00\00\16\00\000123456789ABCDEF")
  (data $.data (i32.const 3744) "\05\00\00\00\00\00\00\00\00\00\00\00\04\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\05\00\00\00\06\00\00\00\a8\11\00\00\00\04\00\00\00\00\00\00\00\00\00\00\01\00\00\00\00\00\00\00\0a\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\a0\0e\00\00\00\00\00\00\05\00\00\00\00\00\00\00\00\00\00\00\04\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\08\00\00\00\06\00\00\00\d4\15\00\00\00\00\00\00\00\00\00\00\00\00\00\00\02\00\00\00\00\00\00\00\ff\ff\ff\ff\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\18\0f\00\00"))
