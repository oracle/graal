;;
;; Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
(module $richards.wasm
  (type (;0;) (func (param i32) (result i32)))
  (type (;1;) (func (param i32 i32 i32) (result i32)))
  (type (;2;) (func (param i32 i64 i32) (result i64)))
  (type (;3;) (func (param i32 i32) (result i32)))
  (type (;4;) (func (param i32 i32 i32 i32) (result i32)))
  (type (;5;) (func (param i32 i64 i32 i32) (result i32)))
  (type (;6;) (func (param i32 i32 i32 i32 i32 i64 i64 i32 i32) (result i32)))
  (type (;7;) (func (param i32)))
  (type (;8;) (func))
  (type (;9;) (func (param i32 i32 i32 i32 i32) (result i32)))
  (type (;10;) (func (result i32)))
  (type (;11;) (func (result f64)))
  (type (;12;) (func (param i32 i32 i32 i32 i64 i64 i32 i32) (result i32)))
  (type (;13;) (func (param f64 i32) (result f64)))
  (type (;14;) (func (param i32 i32 i32)))
  (type (;15;) (func (param i32 i32 i32 i32 i32)))
  (import "wasi_snapshot_preview1" "args_get" (func $__imported_wasi_snapshot_preview1_args_get (type 3)))
  (import "wasi_snapshot_preview1" "args_sizes_get" (func $__imported_wasi_snapshot_preview1_args_sizes_get (type 3)))
  (import "wasi_snapshot_preview1" "fd_close" (func $__imported_wasi_snapshot_preview1_fd_close (type 0)))
  (import "wasi_snapshot_preview1" "fd_fdstat_get" (func $__imported_wasi_snapshot_preview1_fd_fdstat_get (type 3)))
  (import "wasi_snapshot_preview1" "fd_fdstat_set_flags" (func $__imported_wasi_snapshot_preview1_fd_fdstat_set_flags (type 3)))
  (import "wasi_snapshot_preview1" "fd_prestat_get" (func $__imported_wasi_snapshot_preview1_fd_prestat_get (type 3)))
  (import "wasi_snapshot_preview1" "fd_prestat_dir_name" (func $__imported_wasi_snapshot_preview1_fd_prestat_dir_name (type 1)))
  (import "wasi_snapshot_preview1" "fd_read" (func $__imported_wasi_snapshot_preview1_fd_read (type 4)))
  (import "wasi_snapshot_preview1" "fd_seek" (func $__imported_wasi_snapshot_preview1_fd_seek (type 5)))
  (import "wasi_snapshot_preview1" "fd_write" (func $__imported_wasi_snapshot_preview1_fd_write (type 4)))
  (import "wasi_snapshot_preview1" "path_open" (func $__imported_wasi_snapshot_preview1_path_open (type 6)))
  (import "wasi_snapshot_preview1" "proc_exit" (func $__imported_wasi_snapshot_preview1_proc_exit (type 7)))
  (func $__wasm_call_ctors (type 8))
  (func $undefined_weak:__wasilibc_find_relpath_alloc (type 9) (param i32 i32 i32 i32 i32) (result i32)
    unreachable)
  (func $_start (type 8)
    (local i32)
    block  ;; label = @1
      block  ;; label = @2
        global.get $GOT.data.internal.__memory_base
        i32.const 3856
        i32.add
        i32.load
        br_if 0 (;@2;)
        global.get $GOT.data.internal.__memory_base
        i32.const 3856
        i32.add
        i32.const 1
        i32.store
        call $__wasi_init_tp
        call $__wasm_call_ctors
        call $__main_void
        local.set 0
        call $__wasm_call_dtors
        local.get 0
        br_if 1 (;@1;)
        return
      end
      unreachable
    end
    local.get 0
    call $__wasi_proc_exit
    unreachable)
  (func $schedule (type 8)
    (local i32 i32 i32)
    block  ;; label = @1
      i32.const 0
      i32.load offset=3880
      local.tee 0
      i32.eqz
      br_if 0 (;@1;)
      loop  ;; label = @2
        local.get 0
        i32.load offset=16
        local.tee 1
        i32.const 7
        i32.gt_u
        br_if 1 (;@1;)
        block  ;; label = @3
          block  ;; label = @4
            i32.const 1
            local.get 1
            i32.shl
            local.tee 1
            i32.const 244
            i32.and
            br_if 0 (;@4;)
            i32.const 0
            local.set 2
            block  ;; label = @5
              local.get 1
              i32.const 3
              i32.and
              br_if 0 (;@5;)
              local.get 0
              local.get 0
              i32.load offset=12
              local.tee 2
              i32.load
              local.tee 1
              i32.store offset=12
              local.get 0
              local.get 1
              i32.const 0
              i32.ne
              i32.store offset=16
            end
            i32.const 0
            local.get 0
            i32.load offset=4
            local.tee 1
            i32.store offset=3884
            i32.const 0
            local.get 0
            i32.load offset=24
            i32.store offset=3888
            i32.const 0
            local.get 0
            i32.load offset=28
            i32.store offset=3892
            block  ;; label = @5
              i32.const 0
              i32.load offset=3872
              i32.eqz
              br_if 0 (;@5;)
              i32.const 0
              i32.const 0
              i32.load offset=3876
              local.tee 0
              i32.const -1
              i32.add
              i32.store offset=3876
              block  ;; label = @6
                local.get 0
                i32.const 1
                i32.gt_s
                br_if 0 (;@6;)
                i32.const 10
                call $putchar
                drop
                i32.const 0
                i32.const 50
                i32.store offset=3876
              end
              local.get 1
              i32.const 24
              i32.shl
              i32.const 805306368
              i32.add
              i32.const 24
              i32.shr_s
              call $putchar
              drop
              i32.const 0
              i32.load offset=3880
              local.set 0
            end
            local.get 2
            local.get 0
            i32.load offset=20
            call_indirect (type 0)
            local.set 0
            i32.const 0
            i32.load offset=3880
            local.tee 1
            i32.const 0
            i32.load offset=3888
            i32.store offset=24
            local.get 1
            i32.const 0
            i32.load offset=3892
            i32.store offset=28
            br 1 (;@3;)
          end
          local.get 0
          i32.load
          local.set 0
        end
        i32.const 0
        local.get 0
        i32.store offset=3880
        local.get 0
        br_if 0 (;@2;)
      end
    end)
  (func $idlefn (type 0) (param i32) (result i32)
    (local i32 i32 i32)
    global.get $__stack_pointer
    i32.const 32
    i32.sub
    local.tee 1
    global.set $__stack_pointer
    i32.const 0
    i32.const 0
    i32.load offset=3892
    i32.const -1
    i32.add
    local.tee 2
    i32.store offset=3892
    block  ;; label = @1
      block  ;; label = @2
        local.get 2
        br_if 0 (;@2;)
        i32.const 0
        i32.const 0
        i32.load offset=3868
        i32.const 1
        i32.add
        i32.store offset=3868
        i32.const 0
        i32.load offset=3880
        local.tee 2
        local.get 2
        i32.load offset=16
        i32.const 4
        i32.or
        i32.store offset=16
        local.get 2
        i32.load
        local.set 2
        br 1 (;@1;)
      end
      i32.const 0
      i32.load offset=3888
      local.tee 2
      i32.const 1
      i32.shr_u
      i32.const 32767
      i32.and
      local.set 3
      block  ;; label = @2
        local.get 2
        i32.const 1
        i32.and
        br_if 0 (;@2;)
        i32.const 0
        local.set 2
        i32.const 0
        local.get 3
        i32.store offset=3888
        block  ;; label = @3
          block  ;; label = @4
            i32.const 0
            i32.load offset=3568
            i32.const 5
            i32.lt_s
            br_if 0 (;@4;)
            i32.const 0
            i32.load offset=3588
            local.tee 3
            br_if 1 (;@3;)
          end
          local.get 1
          i32.const 5
          i32.store
          i32.const 1157
          local.get 1
          call $printf
          drop
          br 2 (;@1;)
        end
        local.get 3
        local.get 3
        i32.load offset=16
        i32.const 65531
        i32.and
        i32.store offset=16
        local.get 3
        i32.const 0
        i32.load offset=3880
        local.tee 2
        local.get 3
        i32.load offset=8
        local.get 2
        i32.load offset=8
        i32.gt_s
        select
        local.set 2
        br 1 (;@1;)
      end
      i32.const 0
      local.set 2
      i32.const 0
      local.get 3
      i32.const 53256
      i32.xor
      i32.store offset=3888
      block  ;; label = @2
        block  ;; label = @3
          i32.const 0
          i32.load offset=3568
          i32.const 6
          i32.lt_s
          br_if 0 (;@3;)
          i32.const 0
          i32.load offset=3592
          local.tee 3
          br_if 1 (;@2;)
        end
        local.get 1
        i32.const 6
        i32.store offset=16
        i32.const 1157
        local.get 1
        i32.const 16
        i32.add
        call $printf
        drop
        br 1 (;@1;)
      end
      local.get 3
      local.get 3
      i32.load offset=16
      i32.const 65531
      i32.and
      i32.store offset=16
      local.get 3
      i32.const 0
      i32.load offset=3880
      local.tee 2
      local.get 3
      i32.load offset=8
      local.get 2
      i32.load offset=8
      i32.gt_s
      select
      local.set 2
    end
    local.get 1
    i32.const 32
    i32.add
    global.set $__stack_pointer
    local.get 2)
  (func $workfn (type 0) (param i32) (result i32)
    (local i32 i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 1
    global.set $__stack_pointer
    block  ;; label = @1
      block  ;; label = @2
        local.get 0
        br_if 0 (;@2;)
        i32.const 0
        i32.load offset=3880
        local.tee 2
        local.get 2
        i32.load offset=16
        i32.const 2
        i32.or
        i32.store offset=16
        br 1 (;@1;)
      end
      i32.const 0
      local.set 2
      local.get 0
      i32.const 0
      i32.store offset=12
      local.get 0
      i32.const 7
      i32.const 0
      i32.load offset=3888
      local.tee 3
      i32.sub
      local.tee 4
      i32.store offset=4
      local.get 0
      i32.const 1
      i32.const 0
      i32.load offset=3892
      local.tee 5
      i32.const 1
      i32.add
      local.get 5
      i32.const 25
      i32.gt_s
      select
      local.tee 5
      i32.const 3536
      i32.add
      i32.load8_u
      i32.store8 offset=16
      local.get 0
      i32.const 1
      local.get 5
      i32.const 1
      i32.add
      local.get 5
      i32.const 25
      i32.gt_s
      select
      local.tee 5
      i32.const 3536
      i32.add
      i32.load8_u
      i32.store8 offset=17
      local.get 0
      i32.const 1
      local.get 5
      i32.const 1
      i32.add
      local.get 5
      i32.const 25
      i32.gt_s
      select
      local.tee 5
      i32.const 3536
      i32.add
      i32.load8_u
      i32.store8 offset=18
      i32.const 0
      local.get 4
      i32.store offset=3888
      i32.const 0
      i32.const 1
      local.get 5
      i32.const 1
      i32.add
      local.get 5
      i32.const 25
      i32.gt_s
      select
      local.tee 5
      i32.store offset=3892
      local.get 0
      local.get 5
      i32.const 3536
      i32.add
      i32.load8_u
      i32.store8 offset=19
      block  ;; label = @2
        block  ;; label = @3
          local.get 3
          i32.const 6
          i32.gt_s
          br_if 0 (;@3;)
          local.get 4
          i32.const 0
          i32.load offset=3568
          i32.gt_s
          br_if 0 (;@3;)
          local.get 4
          i32.const 2
          i32.shl
          i32.load offset=3568
          local.tee 5
          br_if 1 (;@2;)
        end
        local.get 1
        local.get 4
        i32.store
        i32.const 1157
        local.get 1
        call $printf
        drop
        br 1 (;@1;)
      end
      local.get 0
      i32.const 0
      i32.store
      local.get 0
      i32.const 0
      i32.load offset=3884
      i32.store offset=4
      i32.const 0
      i32.const 0
      i32.load offset=3864
      i32.const 1
      i32.add
      i32.store offset=3864
      block  ;; label = @2
        local.get 5
        i32.load offset=12
        local.tee 2
        br_if 0 (;@2;)
        local.get 5
        local.get 0
        i32.store offset=12
        local.get 5
        local.get 5
        i32.load offset=16
        i32.const 1
        i32.or
        i32.store offset=16
        local.get 5
        i32.const 0
        i32.load offset=3880
        local.tee 2
        local.get 5
        i32.load offset=8
        local.get 2
        i32.load offset=8
        i32.gt_s
        select
        local.set 2
        br 1 (;@1;)
      end
      loop  ;; label = @2
        local.get 2
        local.tee 4
        i32.load
        local.tee 2
        br_if 0 (;@2;)
      end
      local.get 4
      local.get 0
      i32.store
      i32.const 0
      i32.load offset=3880
      local.set 2
    end
    local.get 1
    i32.const 16
    i32.add
    global.set $__stack_pointer
    local.get 2)
  (func $handlerfn (type 0) (param i32) (result i32)
    (local i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 32
    i32.sub
    local.tee 1
    global.set $__stack_pointer
    block  ;; label = @1
      local.get 0
      i32.eqz
      br_if 0 (;@1;)
      local.get 0
      i32.const 0
      i32.store
      i32.const 3888
      i32.const 3892
      local.get 0
      i32.load offset=8
      i32.const 1001
      i32.eq
      select
      local.set 2
      loop  ;; label = @2
        local.get 2
        local.tee 3
        i32.load
        local.tee 2
        br_if 0 (;@2;)
      end
      local.get 3
      local.get 0
      i32.store
    end
    block  ;; label = @1
      block  ;; label = @2
        i32.const 0
        i32.load offset=3888
        local.tee 3
        i32.eqz
        br_if 0 (;@2;)
        block  ;; label = @3
          local.get 3
          i32.load offset=12
          local.tee 2
          i32.const 4
          i32.lt_s
          br_if 0 (;@3;)
          i32.const 0
          local.set 2
          i32.const 0
          local.get 3
          i32.load
          i32.store offset=3888
          block  ;; label = @4
            block  ;; label = @5
              local.get 3
              i32.load offset=4
              local.tee 0
              i32.const 1
              i32.lt_s
              br_if 0 (;@5;)
              local.get 0
              i32.const 0
              i32.load offset=3568
              i32.gt_s
              br_if 0 (;@5;)
              local.get 0
              i32.const 2
              i32.shl
              i32.load offset=3568
              local.tee 4
              br_if 1 (;@4;)
            end
            local.get 1
            local.get 0
            i32.store
            i32.const 1157
            local.get 1
            call $printf
            drop
            br 3 (;@1;)
          end
          local.get 3
          i32.const 0
          i32.store
          local.get 3
          i32.const 0
          i32.load offset=3884
          i32.store offset=4
          i32.const 0
          i32.const 0
          i32.load offset=3864
          i32.const 1
          i32.add
          i32.store offset=3864
          block  ;; label = @4
            local.get 4
            i32.load offset=12
            local.tee 2
            br_if 0 (;@4;)
            local.get 4
            local.get 3
            i32.store offset=12
            local.get 4
            local.get 4
            i32.load offset=16
            i32.const 1
            i32.or
            i32.store offset=16
            local.get 4
            i32.const 0
            i32.load offset=3880
            local.tee 2
            local.get 4
            i32.load offset=8
            local.get 2
            i32.load offset=8
            i32.gt_s
            select
            local.set 2
            br 3 (;@1;)
          end
          loop  ;; label = @4
            local.get 2
            local.tee 0
            i32.load
            local.tee 2
            br_if 0 (;@4;)
          end
          local.get 0
          local.get 3
          i32.store
          i32.const 0
          i32.load offset=3880
          local.set 2
          br 2 (;@1;)
        end
        i32.const 0
        i32.load offset=3892
        local.tee 0
        i32.eqz
        br_if 0 (;@2;)
        i32.const 0
        local.get 0
        i32.load
        i32.store offset=3892
        local.get 0
        local.get 3
        local.get 2
        i32.add
        i32.const 16
        i32.add
        i32.load8_s
        i32.store offset=12
        local.get 3
        local.get 2
        i32.const 1
        i32.add
        i32.store offset=12
        block  ;; label = @3
          block  ;; label = @4
            local.get 0
            i32.load offset=4
            local.tee 2
            i32.const 1
            i32.lt_s
            br_if 0 (;@4;)
            local.get 2
            i32.const 0
            i32.load offset=3568
            i32.gt_s
            br_if 0 (;@4;)
            local.get 2
            i32.const 2
            i32.shl
            i32.load offset=3568
            local.tee 3
            br_if 1 (;@3;)
          end
          local.get 1
          local.get 2
          i32.store offset=16
          i32.const 1157
          local.get 1
          i32.const 16
          i32.add
          call $printf
          drop
          i32.const 0
          local.set 2
          br 2 (;@1;)
        end
        local.get 0
        i32.const 0
        i32.store
        local.get 0
        i32.const 0
        i32.load offset=3884
        i32.store offset=4
        i32.const 0
        i32.const 0
        i32.load offset=3864
        i32.const 1
        i32.add
        i32.store offset=3864
        block  ;; label = @3
          local.get 3
          i32.load offset=12
          local.tee 2
          br_if 0 (;@3;)
          local.get 3
          local.get 0
          i32.store offset=12
          local.get 3
          local.get 3
          i32.load offset=16
          i32.const 1
          i32.or
          i32.store offset=16
          local.get 3
          i32.const 0
          i32.load offset=3880
          local.tee 2
          local.get 3
          i32.load offset=8
          local.get 2
          i32.load offset=8
          i32.gt_s
          select
          local.set 2
          br 2 (;@1;)
        end
        loop  ;; label = @3
          local.get 2
          local.tee 3
          i32.load
          local.tee 2
          br_if 0 (;@3;)
        end
        local.get 3
        local.get 0
        i32.store
        i32.const 0
        i32.load offset=3880
        local.set 2
        br 1 (;@1;)
      end
      i32.const 0
      i32.load offset=3880
      local.tee 2
      local.get 2
      i32.load offset=16
      i32.const 2
      i32.or
      i32.store offset=16
    end
    local.get 1
    i32.const 32
    i32.add
    global.set $__stack_pointer
    local.get 2)
  (func $devfn (type 0) (param i32) (result i32)
    (local i32 i32 i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 1
    global.set $__stack_pointer
    block  ;; label = @1
      block  ;; label = @2
        local.get 0
        br_if 0 (;@2;)
        block  ;; label = @3
          i32.const 0
          i32.load offset=3888
          local.tee 2
          br_if 0 (;@3;)
          i32.const 0
          i32.load offset=3880
          local.tee 0
          local.get 0
          i32.load offset=16
          i32.const 2
          i32.or
          i32.store offset=16
          br 2 (;@1;)
        end
        local.get 2
        i32.load offset=4
        local.set 0
        i32.const 0
        i32.const 0
        i32.store offset=3888
        block  ;; label = @3
          block  ;; label = @4
            local.get 0
            i32.const 1
            i32.lt_s
            br_if 0 (;@4;)
            local.get 0
            i32.const 0
            i32.load offset=3568
            i32.gt_s
            br_if 0 (;@4;)
            local.get 0
            i32.const 2
            i32.shl
            i32.load offset=3568
            local.tee 3
            br_if 1 (;@3;)
          end
          local.get 1
          local.get 0
          i32.store
          i32.const 1157
          local.get 1
          call $printf
          drop
          i32.const 0
          local.set 0
          br 2 (;@1;)
        end
        local.get 2
        i32.const 0
        i32.store
        local.get 2
        i32.const 0
        i32.load offset=3884
        i32.store offset=4
        i32.const 0
        i32.const 0
        i32.load offset=3864
        i32.const 1
        i32.add
        i32.store offset=3864
        block  ;; label = @3
          local.get 3
          i32.load offset=12
          local.tee 0
          br_if 0 (;@3;)
          local.get 3
          local.get 2
          i32.store offset=12
          local.get 3
          local.get 3
          i32.load offset=16
          i32.const 1
          i32.or
          i32.store offset=16
          local.get 3
          i32.const 0
          i32.load offset=3880
          local.tee 0
          local.get 3
          i32.load offset=8
          local.get 0
          i32.load offset=8
          i32.gt_s
          select
          local.set 0
          br 2 (;@1;)
        end
        loop  ;; label = @3
          local.get 0
          local.tee 3
          i32.load
          local.tee 0
          br_if 0 (;@3;)
        end
        local.get 3
        local.get 2
        i32.store
        i32.const 0
        i32.load offset=3880
        local.set 0
        br 1 (;@1;)
      end
      i32.const 0
      local.get 0
      i32.store offset=3888
      block  ;; label = @2
        i32.const 0
        i32.load offset=3872
        i32.eqz
        br_if 0 (;@2;)
        i32.const 0
        i32.const 0
        i32.load offset=3876
        local.tee 3
        i32.const -1
        i32.add
        i32.store offset=3876
        local.get 0
        i32.load offset=12
        local.set 0
        block  ;; label = @3
          local.get 3
          i32.const 1
          i32.gt_s
          br_if 0 (;@3;)
          i32.const 10
          call $putchar
          drop
          i32.const 0
          i32.const 50
          i32.store offset=3876
        end
        local.get 0
        i32.extend8_s
        call $putchar
        drop
      end
      i32.const 0
      i32.const 0
      i32.load offset=3868
      i32.const 1
      i32.add
      i32.store offset=3868
      i32.const 0
      i32.load offset=3880
      local.tee 0
      local.get 0
      i32.load offset=16
      i32.const 4
      i32.or
      i32.store offset=16
      local.get 0
      i32.load
      local.set 0
    end
    local.get 1
    i32.const 16
    i32.add
    global.set $__stack_pointer
    local.get 0)
  (func $richards (type 10) (result i32)
    (local i32 i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 0
    global.set $__stack_pointer
    i32.const 1069
    i32.const 1053
    call $fopen
    local.set 1
    i32.const 0
    i64.const 0
    i64.store offset=3588 align=4
    i32.const 0
    i32.const 10
    i32.store offset=3568
    i32.const 0
    local.get 1
    i32.store offset=3896
    i32.const 0
    i64.const 0
    i64.store offset=3596 align=4
    i32.const 0
    i64.const 0
    i64.store offset=3604 align=4
    i32.const 0
    i32.const 0
    i32.store offset=3864
    i32.const 0
    i32.const 0
    i32.store offset=3868
    i32.const 0
    i32.const 0
    i32.store offset=3872
    i32.const 0
    i32.const 0
    i32.store offset=3876
    i32.const 32
    call $malloc
    local.tee 2
    i64.const 42949672960001
    i64.store offset=24 align=4
    local.get 2
    i32.const 1
    i32.store offset=20
    local.get 2
    i32.const 0
    i32.store offset=16
    local.get 2
    i64.const 0
    i64.store offset=8 align=4
    local.get 2
    i64.const 4294967296
    i64.store align=4
    i32.const 0
    local.get 2
    i32.store offset=3572
    i32.const 20
    call $malloc
    local.tee 1
    i64.const 1001
    i64.store offset=8 align=4
    local.get 1
    i64.const 0
    i64.store align=4
    local.get 1
    i32.const 0
    i32.store offset=16 align=1
    i32.const 20
    call $malloc
    local.tee 3
    i64.const 4299262263296
    i64.store offset=4 align=4
    local.get 3
    local.get 1
    i32.store
    local.get 3
    i64.const 0
    i64.store offset=12 align=4
    i32.const 32
    call $malloc
    local.tee 1
    i64.const 3
    i64.store offset=24 align=4
    local.get 1
    i32.const 2
    i32.store offset=20
    local.get 1
    i32.const 3
    i32.store offset=16
    local.get 1
    local.get 3
    i32.store offset=12
    local.get 1
    i64.const 4294967296002
    i64.store offset=4 align=4
    local.get 1
    local.get 2
    i32.store
    i32.const 0
    local.get 1
    i32.store offset=3576
    i32.const 20
    call $malloc
    local.tee 2
    i64.const 1000
    i64.store offset=8 align=4
    local.get 2
    i64.const 21474836480
    i64.store align=4
    local.get 2
    i32.const 0
    i32.store offset=16 align=1
    i32.const 20
    call $malloc
    local.tee 3
    i64.const 4294967296005
    i64.store offset=4 align=4
    local.get 3
    local.get 2
    i32.store
    local.get 3
    i64.const 0
    i64.store offset=12 align=4
    i32.const 20
    call $malloc
    local.tee 4
    i64.const 4294967296005
    i64.store offset=4 align=4
    local.get 4
    local.get 3
    i32.store
    local.get 4
    i64.const 0
    i64.store offset=12 align=4
    i32.const 32
    call $malloc
    local.tee 2
    i64.const 0
    i64.store offset=24 align=4
    local.get 2
    i32.const 3
    i32.store offset=20
    local.get 2
    i32.const 3
    i32.store offset=16
    local.get 2
    local.get 4
    i32.store offset=12
    local.get 2
    i64.const 8589934592003
    i64.store offset=4 align=4
    local.get 2
    local.get 1
    i32.store
    i32.const 0
    local.get 2
    i32.store offset=3580
    i32.const 20
    call $malloc
    local.tee 1
    i64.const 1000
    i64.store offset=8 align=4
    local.get 1
    i64.const 25769803776
    i64.store align=4
    local.get 1
    i32.const 0
    i32.store offset=16 align=1
    i32.const 20
    call $malloc
    local.tee 3
    i64.const 4294967296006
    i64.store offset=4 align=4
    local.get 3
    local.get 1
    i32.store
    local.get 3
    i64.const 0
    i64.store offset=12 align=4
    i32.const 20
    call $malloc
    local.tee 4
    i64.const 4294967296006
    i64.store offset=4 align=4
    local.get 4
    local.get 3
    i32.store
    local.get 4
    i64.const 0
    i64.store offset=12 align=4
    i32.const 32
    call $malloc
    local.tee 1
    i64.const 0
    i64.store offset=24 align=4
    local.get 1
    i32.const 3
    i32.store offset=20
    local.get 1
    i32.const 3
    i32.store offset=16
    local.get 1
    local.get 4
    i32.store offset=12
    local.get 1
    i64.const 12884901888004
    i64.store offset=4 align=4
    local.get 1
    local.get 2
    i32.store
    i32.const 0
    local.get 1
    i32.store offset=3584
    i32.const 32
    call $malloc
    local.tee 2
    i64.const 0
    i64.store offset=24 align=4
    local.get 2
    i32.const 4
    i32.store offset=20
    local.get 2
    i64.const 8589934592
    i64.store offset=12 align=4
    local.get 2
    i64.const 17179869184005
    i64.store offset=4 align=4
    local.get 2
    local.get 1
    i32.store
    i32.const 0
    local.get 2
    i32.store offset=3588
    i32.const 32
    call $malloc
    local.tee 1
    i64.const 0
    i64.store offset=24 align=4
    local.get 1
    i32.const 4
    i32.store offset=20
    local.get 1
    i64.const 8589934592
    i64.store offset=12 align=4
    local.get 1
    i64.const 21474836480006
    i64.store offset=4 align=4
    local.get 1
    local.get 2
    i32.store
    i32.const 0
    local.get 1
    i32.store offset=3592
    i32.const 0
    local.get 1
    i32.store offset=3860
    i32.const 0
    local.get 1
    i32.store offset=3880
    i32.const 0
    i32.const 0
    i32.store offset=3868
    i32.const 0
    i32.const 0
    i32.store offset=3864
    i32.const 1136
    i32.const 9
    i32.const 1
    i32.const 0
    i32.load offset=3896
    call $fwrite
    drop
    i32.const 0
    i32.const 0
    i32.store offset=3876
    i32.const 0
    i32.const 0
    i32.store offset=3872
    call $schedule
    i32.const 1146
    i32.const 10
    i32.const 1
    i32.const 0
    i32.load offset=3896
    call $fwrite
    drop
    local.get 0
    i32.const 0
    i32.load offset=3864
    i32.store
    local.get 0
    i32.const 0
    i32.load offset=3868
    i32.store offset=4
    i32.const 0
    i32.load offset=3896
    i32.const 1174
    local.get 0
    call $fprintf
    drop
    i32.const 1104
    i32.const 18
    i32.const 1
    i32.const 0
    i32.load offset=3896
    call $fwrite
    drop
    block  ;; label = @1
      block  ;; label = @2
        i32.const 0
        i32.load offset=3864
        i32.const 23246
        i32.ne
        br_if 0 (;@2;)
        i32.const 0
        i32.load offset=3868
        i32.const 9297
        i32.ne
        br_if 0 (;@2;)
        i32.const 1057
        i32.const 7
        i32.const 1
        i32.const 0
        i32.load offset=3896
        call $fwrite
        drop
        br 1 (;@1;)
      end
      i32.const 1055
      i32.const 9
      i32.const 1
      i32.const 0
      i32.load offset=1340
      call $fwrite
      drop
    end
    i32.const 1123
    i32.const 12
    i32.const 1
    i32.const 0
    i32.load offset=3896
    call $fwrite
    drop
    local.get 0
    i32.const 16
    i32.add
    global.set $__stack_pointer
    i32.const 0)
  (func $OutlierRemovalAverageSummary (type 8))
  (func $OutlierRemovalAverageSummaryLowerThreshold (type 11) (result f64)
    f64.const 0x0p+0 (;=0;))
  (func $OutlierRemovalAverageSummaryUpperThreshold (type 11) (result f64)
    f64.const 0x1p-1 (;=0.5;))
  (func $setup (type 8))
  (func $run (type 10) (result i32)
    call $richards
    drop
    i32.const 0)
  (func $main (type 3) (param i32 i32) (result i32)
    call $richards
    drop
    i32.const 0)
  (func $malloc (type 0) (param i32) (result i32)
    local.get 0
    call $dlmalloc)
  (func $dlmalloc (type 0) (param i32) (result i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 1
    global.set $__stack_pointer
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
                              i32.const 0
                              i32.load offset=3924
                              local.tee 2
                              br_if 0 (;@13;)
                              block  ;; label = @14
                                i32.const 0
                                i32.load offset=4372
                                local.tee 3
                                br_if 0 (;@14;)
                                i32.const 0
                                i64.const -1
                                i64.store offset=4384 align=4
                                i32.const 0
                                i64.const 281474976776192
                                i64.store offset=4376 align=4
                                i32.const 0
                                local.get 1
                                i32.const 8
                                i32.add
                                i32.const -16
                                i32.and
                                i32.const 1431655768
                                i32.xor
                                local.tee 3
                                i32.store offset=4372
                                i32.const 0
                                i32.const 0
                                i32.store offset=4392
                                i32.const 0
                                i32.const 0
                                i32.store offset=4344
                              end
                              i32.const 131072
                              i32.const 71168
                              i32.lt_u
                              br_if 1 (;@12;)
                              i32.const 0
                              local.set 2
                              i32.const 131072
                              i32.const 71168
                              i32.sub
                              i32.const 89
                              i32.lt_u
                              br_if 0 (;@13;)
                              i32.const 0
                              local.set 4
                              i32.const 0
                              i32.const 71168
                              i32.store offset=4348
                              i32.const 0
                              i32.const 71168
                              i32.store offset=3916
                              i32.const 0
                              local.get 3
                              i32.store offset=3936
                              i32.const 0
                              i32.const -1
                              i32.store offset=3932
                              i32.const 0
                              i32.const 131072
                              i32.const 71168
                              i32.sub
                              local.tee 3
                              i32.store offset=4352
                              i32.const 0
                              local.get 3
                              i32.store offset=4336
                              i32.const 0
                              local.get 3
                              i32.store offset=4332
                              loop  ;; label = @14
                                local.get 4
                                i32.const 3960
                                i32.add
                                local.get 4
                                i32.const 3948
                                i32.add
                                local.tee 3
                                i32.store
                                local.get 3
                                local.get 4
                                i32.const 3940
                                i32.add
                                local.tee 5
                                i32.store
                                local.get 4
                                i32.const 3952
                                i32.add
                                local.get 5
                                i32.store
                                local.get 4
                                i32.const 3968
                                i32.add
                                local.get 4
                                i32.const 3956
                                i32.add
                                local.tee 5
                                i32.store
                                local.get 5
                                local.get 3
                                i32.store
                                local.get 4
                                i32.const 3976
                                i32.add
                                local.get 4
                                i32.const 3964
                                i32.add
                                local.tee 3
                                i32.store
                                local.get 3
                                local.get 5
                                i32.store
                                local.get 4
                                i32.const 3972
                                i32.add
                                local.get 3
                                i32.store
                                local.get 4
                                i32.const 32
                                i32.add
                                local.tee 4
                                i32.const 256
                                i32.ne
                                br_if 0 (;@14;)
                              end
                              i32.const 131072
                              i32.const -52
                              i32.add
                              i32.const 56
                              i32.store
                              i32.const 0
                              i32.const 0
                              i32.load offset=4388
                              i32.store offset=3928
                              i32.const 0
                              i32.const 71168
                              i32.const -8
                              i32.const 71168
                              i32.sub
                              i32.const 15
                              i32.and
                              local.tee 4
                              i32.add
                              local.tee 2
                              i32.store offset=3924
                              i32.const 0
                              i32.const 131072
                              i32.const 71168
                              i32.sub
                              local.get 4
                              i32.sub
                              i32.const -56
                              i32.add
                              local.tee 4
                              i32.store offset=3912
                              local.get 2
                              local.get 4
                              i32.const 1
                              i32.or
                              i32.store offset=4
                            end
                            block  ;; label = @13
                              block  ;; label = @14
                                local.get 0
                                i32.const 236
                                i32.gt_u
                                br_if 0 (;@14;)
                                block  ;; label = @15
                                  i32.const 0
                                  i32.load offset=3900
                                  local.tee 6
                                  i32.const 16
                                  local.get 0
                                  i32.const 19
                                  i32.add
                                  i32.const 496
                                  i32.and
                                  local.get 0
                                  i32.const 11
                                  i32.lt_u
                                  select
                                  local.tee 5
                                  i32.const 3
                                  i32.shr_u
                                  local.tee 3
                                  i32.shr_u
                                  local.tee 4
                                  i32.const 3
                                  i32.and
                                  i32.eqz
                                  br_if 0 (;@15;)
                                  block  ;; label = @16
                                    block  ;; label = @17
                                      local.get 4
                                      i32.const 1
                                      i32.and
                                      local.get 3
                                      i32.or
                                      i32.const 1
                                      i32.xor
                                      local.tee 5
                                      i32.const 3
                                      i32.shl
                                      local.tee 3
                                      i32.const 3940
                                      i32.add
                                      local.tee 4
                                      local.get 3
                                      i32.load offset=3948
                                      local.tee 3
                                      i32.load offset=8
                                      local.tee 0
                                      i32.ne
                                      br_if 0 (;@17;)
                                      i32.const 0
                                      local.get 6
                                      i32.const -2
                                      local.get 5
                                      i32.rotl
                                      i32.and
                                      i32.store offset=3900
                                      br 1 (;@16;)
                                    end
                                    local.get 4
                                    local.get 0
                                    i32.store offset=8
                                    local.get 0
                                    local.get 4
                                    i32.store offset=12
                                  end
                                  local.get 3
                                  i32.const 8
                                  i32.add
                                  local.set 4
                                  local.get 3
                                  local.get 5
                                  i32.const 3
                                  i32.shl
                                  local.tee 5
                                  i32.const 3
                                  i32.or
                                  i32.store offset=4
                                  local.get 3
                                  local.get 5
                                  i32.add
                                  local.tee 3
                                  local.get 3
                                  i32.load offset=4
                                  i32.const 1
                                  i32.or
                                  i32.store offset=4
                                  br 14 (;@1;)
                                end
                                local.get 5
                                i32.const 0
                                i32.load offset=3908
                                local.tee 7
                                i32.le_u
                                br_if 1 (;@13;)
                                block  ;; label = @15
                                  local.get 4
                                  i32.eqz
                                  br_if 0 (;@15;)
                                  block  ;; label = @16
                                    block  ;; label = @17
                                      local.get 4
                                      local.get 3
                                      i32.shl
                                      i32.const 2
                                      local.get 3
                                      i32.shl
                                      local.tee 4
                                      i32.const 0
                                      local.get 4
                                      i32.sub
                                      i32.or
                                      i32.and
                                      i32.ctz
                                      local.tee 3
                                      i32.const 3
                                      i32.shl
                                      local.tee 4
                                      i32.const 3940
                                      i32.add
                                      local.tee 0
                                      local.get 4
                                      i32.load offset=3948
                                      local.tee 4
                                      i32.load offset=8
                                      local.tee 8
                                      i32.ne
                                      br_if 0 (;@17;)
                                      i32.const 0
                                      local.get 6
                                      i32.const -2
                                      local.get 3
                                      i32.rotl
                                      i32.and
                                      local.tee 6
                                      i32.store offset=3900
                                      br 1 (;@16;)
                                    end
                                    local.get 0
                                    local.get 8
                                    i32.store offset=8
                                    local.get 8
                                    local.get 0
                                    i32.store offset=12
                                  end
                                  local.get 4
                                  local.get 5
                                  i32.const 3
                                  i32.or
                                  i32.store offset=4
                                  local.get 4
                                  local.get 3
                                  i32.const 3
                                  i32.shl
                                  local.tee 3
                                  i32.add
                                  local.get 3
                                  local.get 5
                                  i32.sub
                                  local.tee 0
                                  i32.store
                                  local.get 4
                                  local.get 5
                                  i32.add
                                  local.tee 8
                                  local.get 0
                                  i32.const 1
                                  i32.or
                                  i32.store offset=4
                                  block  ;; label = @16
                                    local.get 7
                                    i32.eqz
                                    br_if 0 (;@16;)
                                    local.get 7
                                    i32.const -8
                                    i32.and
                                    i32.const 3940
                                    i32.add
                                    local.set 5
                                    i32.const 0
                                    i32.load offset=3920
                                    local.set 3
                                    block  ;; label = @17
                                      block  ;; label = @18
                                        local.get 6
                                        i32.const 1
                                        local.get 7
                                        i32.const 3
                                        i32.shr_u
                                        i32.shl
                                        local.tee 9
                                        i32.and
                                        br_if 0 (;@18;)
                                        i32.const 0
                                        local.get 6
                                        local.get 9
                                        i32.or
                                        i32.store offset=3900
                                        local.get 5
                                        local.set 9
                                        br 1 (;@17;)
                                      end
                                      local.get 5
                                      i32.load offset=8
                                      local.set 9
                                    end
                                    local.get 9
                                    local.get 3
                                    i32.store offset=12
                                    local.get 5
                                    local.get 3
                                    i32.store offset=8
                                    local.get 3
                                    local.get 5
                                    i32.store offset=12
                                    local.get 3
                                    local.get 9
                                    i32.store offset=8
                                  end
                                  local.get 4
                                  i32.const 8
                                  i32.add
                                  local.set 4
                                  i32.const 0
                                  local.get 8
                                  i32.store offset=3920
                                  i32.const 0
                                  local.get 0
                                  i32.store offset=3908
                                  br 14 (;@1;)
                                end
                                i32.const 0
                                i32.load offset=3904
                                local.tee 10
                                i32.eqz
                                br_if 1 (;@13;)
                                local.get 10
                                i32.ctz
                                i32.const 2
                                i32.shl
                                i32.load offset=4204
                                local.tee 8
                                i32.load offset=4
                                i32.const -8
                                i32.and
                                local.get 5
                                i32.sub
                                local.set 3
                                local.get 8
                                local.set 0
                                block  ;; label = @15
                                  loop  ;; label = @16
                                    block  ;; label = @17
                                      local.get 0
                                      i32.load offset=16
                                      local.tee 4
                                      br_if 0 (;@17;)
                                      local.get 0
                                      i32.load offset=20
                                      local.tee 4
                                      i32.eqz
                                      br_if 2 (;@15;)
                                    end
                                    local.get 4
                                    i32.load offset=4
                                    i32.const -8
                                    i32.and
                                    local.get 5
                                    i32.sub
                                    local.tee 0
                                    local.get 3
                                    local.get 0
                                    local.get 3
                                    i32.lt_u
                                    local.tee 0
                                    select
                                    local.set 3
                                    local.get 4
                                    local.get 8
                                    local.get 0
                                    select
                                    local.set 8
                                    local.get 4
                                    local.set 0
                                    br 0 (;@16;)
                                  end
                                end
                                local.get 8
                                i32.load offset=24
                                local.set 2
                                block  ;; label = @15
                                  local.get 8
                                  i32.load offset=12
                                  local.tee 4
                                  local.get 8
                                  i32.eq
                                  br_if 0 (;@15;)
                                  local.get 8
                                  i32.load offset=8
                                  local.tee 0
                                  local.get 4
                                  i32.store offset=12
                                  local.get 4
                                  local.get 0
                                  i32.store offset=8
                                  br 13 (;@2;)
                                end
                                block  ;; label = @15
                                  block  ;; label = @16
                                    local.get 8
                                    i32.load offset=20
                                    local.tee 0
                                    i32.eqz
                                    br_if 0 (;@16;)
                                    local.get 8
                                    i32.const 20
                                    i32.add
                                    local.set 9
                                    br 1 (;@15;)
                                  end
                                  local.get 8
                                  i32.load offset=16
                                  local.tee 0
                                  i32.eqz
                                  br_if 4 (;@11;)
                                  local.get 8
                                  i32.const 16
                                  i32.add
                                  local.set 9
                                end
                                loop  ;; label = @15
                                  local.get 9
                                  local.set 11
                                  local.get 0
                                  local.tee 4
                                  i32.const 20
                                  i32.add
                                  local.set 9
                                  local.get 4
                                  i32.load offset=20
                                  local.tee 0
                                  br_if 0 (;@15;)
                                  local.get 4
                                  i32.const 16
                                  i32.add
                                  local.set 9
                                  local.get 4
                                  i32.load offset=16
                                  local.tee 0
                                  br_if 0 (;@15;)
                                end
                                local.get 11
                                i32.const 0
                                i32.store
                                br 12 (;@2;)
                              end
                              i32.const -1
                              local.set 5
                              local.get 0
                              i32.const -65
                              i32.gt_u
                              br_if 0 (;@13;)
                              local.get 0
                              i32.const 19
                              i32.add
                              local.tee 4
                              i32.const -16
                              i32.and
                              local.set 5
                              i32.const 0
                              i32.load offset=3904
                              local.tee 10
                              i32.eqz
                              br_if 0 (;@13;)
                              i32.const 31
                              local.set 7
                              block  ;; label = @14
                                local.get 0
                                i32.const 16777196
                                i32.gt_u
                                br_if 0 (;@14;)
                                local.get 5
                                i32.const 38
                                local.get 4
                                i32.const 8
                                i32.shr_u
                                i32.clz
                                local.tee 4
                                i32.sub
                                i32.shr_u
                                i32.const 1
                                i32.and
                                local.get 4
                                i32.const 1
                                i32.shl
                                i32.sub
                                i32.const 62
                                i32.add
                                local.set 7
                              end
                              i32.const 0
                              local.get 5
                              i32.sub
                              local.set 3
                              block  ;; label = @14
                                block  ;; label = @15
                                  block  ;; label = @16
                                    block  ;; label = @17
                                      local.get 7
                                      i32.const 2
                                      i32.shl
                                      i32.load offset=4204
                                      local.tee 0
                                      br_if 0 (;@17;)
                                      i32.const 0
                                      local.set 4
                                      i32.const 0
                                      local.set 9
                                      br 1 (;@16;)
                                    end
                                    i32.const 0
                                    local.set 4
                                    local.get 5
                                    i32.const 0
                                    i32.const 25
                                    local.get 7
                                    i32.const 1
                                    i32.shr_u
                                    i32.sub
                                    local.get 7
                                    i32.const 31
                                    i32.eq
                                    select
                                    i32.shl
                                    local.set 8
                                    i32.const 0
                                    local.set 9
                                    loop  ;; label = @17
                                      block  ;; label = @18
                                        local.get 0
                                        i32.load offset=4
                                        i32.const -8
                                        i32.and
                                        local.get 5
                                        i32.sub
                                        local.tee 6
                                        local.get 3
                                        i32.ge_u
                                        br_if 0 (;@18;)
                                        local.get 6
                                        local.set 3
                                        local.get 0
                                        local.set 9
                                        local.get 6
                                        br_if 0 (;@18;)
                                        i32.const 0
                                        local.set 3
                                        local.get 0
                                        local.set 9
                                        local.get 0
                                        local.set 4
                                        br 3 (;@15;)
                                      end
                                      local.get 4
                                      local.get 0
                                      i32.load offset=20
                                      local.tee 6
                                      local.get 6
                                      local.get 0
                                      local.get 8
                                      i32.const 29
                                      i32.shr_u
                                      i32.const 4
                                      i32.and
                                      i32.add
                                      i32.load offset=16
                                      local.tee 11
                                      i32.eq
                                      select
                                      local.get 4
                                      local.get 6
                                      select
                                      local.set 4
                                      local.get 8
                                      i32.const 1
                                      i32.shl
                                      local.set 8
                                      local.get 11
                                      local.set 0
                                      local.get 11
                                      br_if 0 (;@17;)
                                    end
                                  end
                                  block  ;; label = @16
                                    local.get 4
                                    local.get 9
                                    i32.or
                                    br_if 0 (;@16;)
                                    i32.const 0
                                    local.set 9
                                    i32.const 2
                                    local.get 7
                                    i32.shl
                                    local.tee 4
                                    i32.const 0
                                    local.get 4
                                    i32.sub
                                    i32.or
                                    local.get 10
                                    i32.and
                                    local.tee 4
                                    i32.eqz
                                    br_if 3 (;@13;)
                                    local.get 4
                                    i32.ctz
                                    i32.const 2
                                    i32.shl
                                    i32.load offset=4204
                                    local.set 4
                                  end
                                  local.get 4
                                  i32.eqz
                                  br_if 1 (;@14;)
                                end
                                loop  ;; label = @15
                                  local.get 4
                                  i32.load offset=4
                                  i32.const -8
                                  i32.and
                                  local.get 5
                                  i32.sub
                                  local.tee 6
                                  local.get 3
                                  i32.lt_u
                                  local.set 8
                                  block  ;; label = @16
                                    local.get 4
                                    i32.load offset=16
                                    local.tee 0
                                    br_if 0 (;@16;)
                                    local.get 4
                                    i32.load offset=20
                                    local.set 0
                                  end
                                  local.get 6
                                  local.get 3
                                  local.get 8
                                  select
                                  local.set 3
                                  local.get 4
                                  local.get 9
                                  local.get 8
                                  select
                                  local.set 9
                                  local.get 0
                                  local.set 4
                                  local.get 0
                                  br_if 0 (;@15;)
                                end
                              end
                              local.get 9
                              i32.eqz
                              br_if 0 (;@13;)
                              local.get 3
                              i32.const 0
                              i32.load offset=3908
                              local.get 5
                              i32.sub
                              i32.ge_u
                              br_if 0 (;@13;)
                              local.get 9
                              i32.load offset=24
                              local.set 11
                              block  ;; label = @14
                                local.get 9
                                i32.load offset=12
                                local.tee 4
                                local.get 9
                                i32.eq
                                br_if 0 (;@14;)
                                local.get 9
                                i32.load offset=8
                                local.tee 0
                                local.get 4
                                i32.store offset=12
                                local.get 4
                                local.get 0
                                i32.store offset=8
                                br 11 (;@3;)
                              end
                              block  ;; label = @14
                                block  ;; label = @15
                                  local.get 9
                                  i32.load offset=20
                                  local.tee 0
                                  i32.eqz
                                  br_if 0 (;@15;)
                                  local.get 9
                                  i32.const 20
                                  i32.add
                                  local.set 8
                                  br 1 (;@14;)
                                end
                                local.get 9
                                i32.load offset=16
                                local.tee 0
                                i32.eqz
                                br_if 4 (;@10;)
                                local.get 9
                                i32.const 16
                                i32.add
                                local.set 8
                              end
                              loop  ;; label = @14
                                local.get 8
                                local.set 6
                                local.get 0
                                local.tee 4
                                i32.const 20
                                i32.add
                                local.set 8
                                local.get 4
                                i32.load offset=20
                                local.tee 0
                                br_if 0 (;@14;)
                                local.get 4
                                i32.const 16
                                i32.add
                                local.set 8
                                local.get 4
                                i32.load offset=16
                                local.tee 0
                                br_if 0 (;@14;)
                              end
                              local.get 6
                              i32.const 0
                              i32.store
                              br 10 (;@3;)
                            end
                            block  ;; label = @13
                              i32.const 0
                              i32.load offset=3908
                              local.tee 4
                              local.get 5
                              i32.lt_u
                              br_if 0 (;@13;)
                              i32.const 0
                              i32.load offset=3920
                              local.set 3
                              block  ;; label = @14
                                block  ;; label = @15
                                  local.get 4
                                  local.get 5
                                  i32.sub
                                  local.tee 0
                                  i32.const 16
                                  i32.lt_u
                                  br_if 0 (;@15;)
                                  local.get 3
                                  local.get 5
                                  i32.add
                                  local.tee 8
                                  local.get 0
                                  i32.const 1
                                  i32.or
                                  i32.store offset=4
                                  local.get 3
                                  local.get 4
                                  i32.add
                                  local.get 0
                                  i32.store
                                  local.get 3
                                  local.get 5
                                  i32.const 3
                                  i32.or
                                  i32.store offset=4
                                  br 1 (;@14;)
                                end
                                local.get 3
                                local.get 4
                                i32.const 3
                                i32.or
                                i32.store offset=4
                                local.get 3
                                local.get 4
                                i32.add
                                local.tee 4
                                local.get 4
                                i32.load offset=4
                                i32.const 1
                                i32.or
                                i32.store offset=4
                                i32.const 0
                                local.set 8
                                i32.const 0
                                local.set 0
                              end
                              i32.const 0
                              local.get 0
                              i32.store offset=3908
                              i32.const 0
                              local.get 8
                              i32.store offset=3920
                              local.get 3
                              i32.const 8
                              i32.add
                              local.set 4
                              br 12 (;@1;)
                            end
                            block  ;; label = @13
                              i32.const 0
                              i32.load offset=3912
                              local.tee 0
                              local.get 5
                              i32.le_u
                              br_if 0 (;@13;)
                              local.get 2
                              local.get 5
                              i32.add
                              local.tee 4
                              local.get 0
                              local.get 5
                              i32.sub
                              local.tee 3
                              i32.const 1
                              i32.or
                              i32.store offset=4
                              i32.const 0
                              local.get 4
                              i32.store offset=3924
                              i32.const 0
                              local.get 3
                              i32.store offset=3912
                              local.get 2
                              local.get 5
                              i32.const 3
                              i32.or
                              i32.store offset=4
                              local.get 2
                              i32.const 8
                              i32.add
                              local.set 4
                              br 12 (;@1;)
                            end
                            block  ;; label = @13
                              block  ;; label = @14
                                i32.const 0
                                i32.load offset=4372
                                i32.eqz
                                br_if 0 (;@14;)
                                i32.const 0
                                i32.load offset=4380
                                local.set 3
                                br 1 (;@13;)
                              end
                              i32.const 0
                              i64.const -1
                              i64.store offset=4384 align=4
                              i32.const 0
                              i64.const 281474976776192
                              i64.store offset=4376 align=4
                              i32.const 0
                              local.get 1
                              i32.const 12
                              i32.add
                              i32.const -16
                              i32.and
                              i32.const 1431655768
                              i32.xor
                              i32.store offset=4372
                              i32.const 0
                              i32.const 0
                              i32.store offset=4392
                              i32.const 0
                              i32.const 0
                              i32.store offset=4344
                              i32.const 65536
                              local.set 3
                            end
                            i32.const 0
                            local.set 4
                            block  ;; label = @13
                              local.get 3
                              local.get 5
                              i32.const 71
                              i32.add
                              local.tee 11
                              i32.add
                              local.tee 8
                              i32.const 0
                              local.get 3
                              i32.sub
                              local.tee 6
                              i32.and
                              local.tee 9
                              local.get 5
                              i32.gt_u
                              br_if 0 (;@13;)
                              i32.const 0
                              i32.const 48
                              i32.store offset=4396
                              br 12 (;@1;)
                            end
                            block  ;; label = @13
                              i32.const 0
                              i32.load offset=4340
                              local.tee 4
                              i32.eqz
                              br_if 0 (;@13;)
                              block  ;; label = @14
                                i32.const 0
                                i32.load offset=4332
                                local.tee 3
                                local.get 9
                                i32.add
                                local.tee 7
                                local.get 3
                                i32.le_u
                                br_if 0 (;@14;)
                                local.get 7
                                local.get 4
                                i32.le_u
                                br_if 1 (;@13;)
                              end
                              i32.const 0
                              local.set 4
                              i32.const 0
                              i32.const 48
                              i32.store offset=4396
                              br 12 (;@1;)
                            end
                            i32.const 0
                            i32.load8_u offset=4344
                            i32.const 4
                            i32.and
                            br_if 5 (;@7;)
                            block  ;; label = @13
                              block  ;; label = @14
                                block  ;; label = @15
                                  local.get 2
                                  i32.eqz
                                  br_if 0 (;@15;)
                                  i32.const 4348
                                  local.set 4
                                  loop  ;; label = @16
                                    block  ;; label = @17
                                      local.get 2
                                      local.get 4
                                      i32.load
                                      local.tee 3
                                      i32.lt_u
                                      br_if 0 (;@17;)
                                      local.get 2
                                      local.get 3
                                      local.get 4
                                      i32.load offset=4
                                      i32.add
                                      i32.lt_u
                                      br_if 3 (;@14;)
                                    end
                                    local.get 4
                                    i32.load offset=8
                                    local.tee 4
                                    br_if 0 (;@16;)
                                  end
                                end
                                i32.const 0
                                call $sbrk
                                local.tee 8
                                i32.const -1
                                i32.eq
                                br_if 6 (;@8;)
                                local.get 9
                                local.set 6
                                block  ;; label = @15
                                  i32.const 0
                                  i32.load offset=4376
                                  local.tee 4
                                  i32.const -1
                                  i32.add
                                  local.tee 3
                                  local.get 8
                                  i32.and
                                  i32.eqz
                                  br_if 0 (;@15;)
                                  local.get 9
                                  local.get 8
                                  i32.sub
                                  local.get 3
                                  local.get 8
                                  i32.add
                                  i32.const 0
                                  local.get 4
                                  i32.sub
                                  i32.and
                                  i32.add
                                  local.set 6
                                end
                                local.get 6
                                local.get 5
                                i32.le_u
                                br_if 6 (;@8;)
                                local.get 6
                                i32.const 2147483646
                                i32.gt_u
                                br_if 6 (;@8;)
                                block  ;; label = @15
                                  i32.const 0
                                  i32.load offset=4340
                                  local.tee 4
                                  i32.eqz
                                  br_if 0 (;@15;)
                                  i32.const 0
                                  i32.load offset=4332
                                  local.tee 3
                                  local.get 6
                                  i32.add
                                  local.tee 0
                                  local.get 3
                                  i32.le_u
                                  br_if 7 (;@8;)
                                  local.get 0
                                  local.get 4
                                  i32.gt_u
                                  br_if 7 (;@8;)
                                end
                                local.get 6
                                call $sbrk
                                local.tee 4
                                local.get 8
                                i32.ne
                                br_if 1 (;@13;)
                                br 8 (;@6;)
                              end
                              local.get 8
                              local.get 0
                              i32.sub
                              local.get 6
                              i32.and
                              local.tee 6
                              i32.const 2147483646
                              i32.gt_u
                              br_if 5 (;@8;)
                              local.get 6
                              call $sbrk
                              local.tee 8
                              local.get 4
                              i32.load
                              local.get 4
                              i32.load offset=4
                              i32.add
                              i32.eq
                              br_if 4 (;@9;)
                              local.get 8
                              local.set 4
                            end
                            block  ;; label = @13
                              local.get 6
                              local.get 5
                              i32.const 72
                              i32.add
                              i32.ge_u
                              br_if 0 (;@13;)
                              local.get 4
                              i32.const -1
                              i32.eq
                              br_if 0 (;@13;)
                              block  ;; label = @14
                                local.get 11
                                local.get 6
                                i32.sub
                                i32.const 0
                                i32.load offset=4380
                                local.tee 3
                                i32.add
                                i32.const 0
                                local.get 3
                                i32.sub
                                i32.and
                                local.tee 3
                                i32.const 2147483646
                                i32.le_u
                                br_if 0 (;@14;)
                                local.get 4
                                local.set 8
                                br 8 (;@6;)
                              end
                              block  ;; label = @14
                                local.get 3
                                call $sbrk
                                i32.const -1
                                i32.eq
                                br_if 0 (;@14;)
                                local.get 3
                                local.get 6
                                i32.add
                                local.set 6
                                local.get 4
                                local.set 8
                                br 8 (;@6;)
                              end
                              i32.const 0
                              local.get 6
                              i32.sub
                              call $sbrk
                              drop
                              br 5 (;@8;)
                            end
                            local.get 4
                            local.set 8
                            local.get 4
                            i32.const -1
                            i32.ne
                            br_if 6 (;@6;)
                            br 4 (;@8;)
                          end
                          unreachable
                        end
                        i32.const 0
                        local.set 4
                        br 8 (;@2;)
                      end
                      i32.const 0
                      local.set 4
                      br 6 (;@3;)
                    end
                    local.get 8
                    i32.const -1
                    i32.ne
                    br_if 2 (;@6;)
                  end
                  i32.const 0
                  i32.const 0
                  i32.load offset=4344
                  i32.const 4
                  i32.or
                  i32.store offset=4344
                end
                local.get 9
                i32.const 2147483646
                i32.gt_u
                br_if 1 (;@5;)
                local.get 9
                call $sbrk
                local.set 8
                i32.const 0
                call $sbrk
                local.set 4
                local.get 8
                i32.const -1
                i32.eq
                br_if 1 (;@5;)
                local.get 4
                i32.const -1
                i32.eq
                br_if 1 (;@5;)
                local.get 8
                local.get 4
                i32.ge_u
                br_if 1 (;@5;)
                local.get 4
                local.get 8
                i32.sub
                local.tee 6
                local.get 5
                i32.const 56
                i32.add
                i32.le_u
                br_if 1 (;@5;)
              end
              i32.const 0
              i32.const 0
              i32.load offset=4332
              local.get 6
              i32.add
              local.tee 4
              i32.store offset=4332
              block  ;; label = @6
                local.get 4
                i32.const 0
                i32.load offset=4336
                i32.le_u
                br_if 0 (;@6;)
                i32.const 0
                local.get 4
                i32.store offset=4336
              end
              block  ;; label = @6
                block  ;; label = @7
                  block  ;; label = @8
                    block  ;; label = @9
                      i32.const 0
                      i32.load offset=3924
                      local.tee 3
                      i32.eqz
                      br_if 0 (;@9;)
                      i32.const 4348
                      local.set 4
                      loop  ;; label = @10
                        local.get 8
                        local.get 4
                        i32.load
                        local.tee 0
                        local.get 4
                        i32.load offset=4
                        local.tee 9
                        i32.add
                        i32.eq
                        br_if 2 (;@8;)
                        local.get 4
                        i32.load offset=8
                        local.tee 4
                        br_if 0 (;@10;)
                        br 3 (;@7;)
                      end
                    end
                    block  ;; label = @9
                      block  ;; label = @10
                        i32.const 0
                        i32.load offset=3916
                        local.tee 4
                        i32.eqz
                        br_if 0 (;@10;)
                        local.get 8
                        local.get 4
                        i32.ge_u
                        br_if 1 (;@9;)
                      end
                      i32.const 0
                      local.get 8
                      i32.store offset=3916
                    end
                    i32.const 0
                    local.set 4
                    i32.const 0
                    local.get 6
                    i32.store offset=4352
                    i32.const 0
                    local.get 8
                    i32.store offset=4348
                    i32.const 0
                    i32.const -1
                    i32.store offset=3932
                    i32.const 0
                    i32.const 0
                    i32.load offset=4372
                    i32.store offset=3936
                    i32.const 0
                    i32.const 0
                    i32.store offset=4360
                    loop  ;; label = @9
                      local.get 4
                      i32.const 3960
                      i32.add
                      local.get 4
                      i32.const 3948
                      i32.add
                      local.tee 3
                      i32.store
                      local.get 3
                      local.get 4
                      i32.const 3940
                      i32.add
                      local.tee 0
                      i32.store
                      local.get 4
                      i32.const 3952
                      i32.add
                      local.get 0
                      i32.store
                      local.get 4
                      i32.const 3968
                      i32.add
                      local.get 4
                      i32.const 3956
                      i32.add
                      local.tee 0
                      i32.store
                      local.get 0
                      local.get 3
                      i32.store
                      local.get 4
                      i32.const 3976
                      i32.add
                      local.get 4
                      i32.const 3964
                      i32.add
                      local.tee 3
                      i32.store
                      local.get 3
                      local.get 0
                      i32.store
                      local.get 4
                      i32.const 3972
                      i32.add
                      local.get 3
                      i32.store
                      local.get 4
                      i32.const 32
                      i32.add
                      local.tee 4
                      i32.const 256
                      i32.ne
                      br_if 0 (;@9;)
                    end
                    local.get 8
                    i32.const -8
                    local.get 8
                    i32.sub
                    i32.const 15
                    i32.and
                    local.tee 4
                    i32.add
                    local.tee 3
                    local.get 6
                    i32.const -56
                    i32.add
                    local.tee 0
                    local.get 4
                    i32.sub
                    local.tee 4
                    i32.const 1
                    i32.or
                    i32.store offset=4
                    i32.const 0
                    i32.const 0
                    i32.load offset=4388
                    i32.store offset=3928
                    i32.const 0
                    local.get 4
                    i32.store offset=3912
                    i32.const 0
                    local.get 3
                    i32.store offset=3924
                    local.get 8
                    local.get 0
                    i32.add
                    i32.const 56
                    i32.store offset=4
                    br 2 (;@6;)
                  end
                  local.get 3
                  local.get 8
                  i32.ge_u
                  br_if 0 (;@7;)
                  local.get 3
                  local.get 0
                  i32.lt_u
                  br_if 0 (;@7;)
                  local.get 4
                  i32.load offset=12
                  i32.const 8
                  i32.and
                  br_if 0 (;@7;)
                  local.get 3
                  i32.const -8
                  local.get 3
                  i32.sub
                  i32.const 15
                  i32.and
                  local.tee 0
                  i32.add
                  local.tee 8
                  i32.const 0
                  i32.load offset=3912
                  local.get 6
                  i32.add
                  local.tee 11
                  local.get 0
                  i32.sub
                  local.tee 0
                  i32.const 1
                  i32.or
                  i32.store offset=4
                  local.get 4
                  local.get 9
                  local.get 6
                  i32.add
                  i32.store offset=4
                  i32.const 0
                  i32.const 0
                  i32.load offset=4388
                  i32.store offset=3928
                  i32.const 0
                  local.get 0
                  i32.store offset=3912
                  i32.const 0
                  local.get 8
                  i32.store offset=3924
                  local.get 3
                  local.get 11
                  i32.add
                  i32.const 56
                  i32.store offset=4
                  br 1 (;@6;)
                end
                block  ;; label = @7
                  local.get 8
                  i32.const 0
                  i32.load offset=3916
                  i32.ge_u
                  br_if 0 (;@7;)
                  i32.const 0
                  local.get 8
                  i32.store offset=3916
                end
                local.get 8
                local.get 6
                i32.add
                local.set 0
                i32.const 4348
                local.set 4
                block  ;; label = @7
                  block  ;; label = @8
                    loop  ;; label = @9
                      local.get 4
                      i32.load
                      local.tee 9
                      local.get 0
                      i32.eq
                      br_if 1 (;@8;)
                      local.get 4
                      i32.load offset=8
                      local.tee 4
                      br_if 0 (;@9;)
                      br 2 (;@7;)
                    end
                  end
                  local.get 4
                  i32.load8_u offset=12
                  i32.const 8
                  i32.and
                  i32.eqz
                  br_if 3 (;@4;)
                end
                i32.const 4348
                local.set 4
                block  ;; label = @7
                  loop  ;; label = @8
                    block  ;; label = @9
                      local.get 3
                      local.get 4
                      i32.load
                      local.tee 0
                      i32.lt_u
                      br_if 0 (;@9;)
                      local.get 3
                      local.get 0
                      local.get 4
                      i32.load offset=4
                      i32.add
                      local.tee 0
                      i32.lt_u
                      br_if 2 (;@7;)
                    end
                    local.get 4
                    i32.load offset=8
                    local.set 4
                    br 0 (;@8;)
                  end
                end
                local.get 8
                i32.const -8
                local.get 8
                i32.sub
                i32.const 15
                i32.and
                local.tee 4
                i32.add
                local.tee 11
                local.get 6
                i32.const -56
                i32.add
                local.tee 9
                local.get 4
                i32.sub
                local.tee 4
                i32.const 1
                i32.or
                i32.store offset=4
                local.get 8
                local.get 9
                i32.add
                i32.const 56
                i32.store offset=4
                local.get 3
                local.get 0
                i32.const 55
                local.get 0
                i32.sub
                i32.const 15
                i32.and
                i32.add
                i32.const -63
                i32.add
                local.tee 9
                local.get 9
                local.get 3
                i32.const 16
                i32.add
                i32.lt_u
                select
                local.tee 9
                i32.const 35
                i32.store offset=4
                i32.const 0
                i32.const 0
                i32.load offset=4388
                i32.store offset=3928
                i32.const 0
                local.get 4
                i32.store offset=3912
                i32.const 0
                local.get 11
                i32.store offset=3924
                local.get 9
                i32.const 16
                i32.add
                i32.const 0
                i64.load offset=4356 align=4
                i64.store align=4
                local.get 9
                i32.const 0
                i64.load offset=4348 align=4
                i64.store offset=8 align=4
                i32.const 0
                local.get 9
                i32.const 8
                i32.add
                i32.store offset=4356
                i32.const 0
                local.get 6
                i32.store offset=4352
                i32.const 0
                local.get 8
                i32.store offset=4348
                i32.const 0
                i32.const 0
                i32.store offset=4360
                local.get 9
                i32.const 36
                i32.add
                local.set 4
                loop  ;; label = @7
                  local.get 4
                  i32.const 7
                  i32.store
                  local.get 4
                  i32.const 4
                  i32.add
                  local.tee 4
                  local.get 0
                  i32.lt_u
                  br_if 0 (;@7;)
                end
                local.get 9
                local.get 3
                i32.eq
                br_if 0 (;@6;)
                local.get 9
                local.get 9
                i32.load offset=4
                i32.const -2
                i32.and
                i32.store offset=4
                local.get 9
                local.get 9
                local.get 3
                i32.sub
                local.tee 8
                i32.store
                local.get 3
                local.get 8
                i32.const 1
                i32.or
                i32.store offset=4
                block  ;; label = @7
                  block  ;; label = @8
                    local.get 8
                    i32.const 255
                    i32.gt_u
                    br_if 0 (;@8;)
                    local.get 8
                    i32.const -8
                    i32.and
                    i32.const 3940
                    i32.add
                    local.set 4
                    block  ;; label = @9
                      block  ;; label = @10
                        i32.const 0
                        i32.load offset=3900
                        local.tee 0
                        i32.const 1
                        local.get 8
                        i32.const 3
                        i32.shr_u
                        i32.shl
                        local.tee 8
                        i32.and
                        br_if 0 (;@10;)
                        i32.const 0
                        local.get 0
                        local.get 8
                        i32.or
                        i32.store offset=3900
                        local.get 4
                        local.set 0
                        br 1 (;@9;)
                      end
                      local.get 4
                      i32.load offset=8
                      local.set 0
                    end
                    local.get 0
                    local.get 3
                    i32.store offset=12
                    local.get 4
                    local.get 3
                    i32.store offset=8
                    i32.const 12
                    local.set 8
                    i32.const 8
                    local.set 9
                    br 1 (;@7;)
                  end
                  i32.const 31
                  local.set 4
                  block  ;; label = @8
                    local.get 8
                    i32.const 16777215
                    i32.gt_u
                    br_if 0 (;@8;)
                    local.get 8
                    i32.const 38
                    local.get 8
                    i32.const 8
                    i32.shr_u
                    i32.clz
                    local.tee 4
                    i32.sub
                    i32.shr_u
                    i32.const 1
                    i32.and
                    local.get 4
                    i32.const 1
                    i32.shl
                    i32.sub
                    i32.const 62
                    i32.add
                    local.set 4
                  end
                  local.get 3
                  local.get 4
                  i32.store offset=28
                  local.get 3
                  i64.const 0
                  i64.store offset=16 align=4
                  local.get 4
                  i32.const 2
                  i32.shl
                  i32.const 4204
                  i32.add
                  local.set 0
                  block  ;; label = @8
                    block  ;; label = @9
                      block  ;; label = @10
                        i32.const 0
                        i32.load offset=3904
                        local.tee 9
                        i32.const 1
                        local.get 4
                        i32.shl
                        local.tee 6
                        i32.and
                        br_if 0 (;@10;)
                        local.get 0
                        local.get 3
                        i32.store
                        i32.const 0
                        local.get 9
                        local.get 6
                        i32.or
                        i32.store offset=3904
                        local.get 3
                        local.get 0
                        i32.store offset=24
                        br 1 (;@9;)
                      end
                      local.get 8
                      i32.const 0
                      i32.const 25
                      local.get 4
                      i32.const 1
                      i32.shr_u
                      i32.sub
                      local.get 4
                      i32.const 31
                      i32.eq
                      select
                      i32.shl
                      local.set 4
                      local.get 0
                      i32.load
                      local.set 9
                      loop  ;; label = @10
                        local.get 9
                        local.tee 0
                        i32.load offset=4
                        i32.const -8
                        i32.and
                        local.get 8
                        i32.eq
                        br_if 2 (;@8;)
                        local.get 4
                        i32.const 29
                        i32.shr_u
                        local.set 9
                        local.get 4
                        i32.const 1
                        i32.shl
                        local.set 4
                        local.get 0
                        local.get 9
                        i32.const 4
                        i32.and
                        i32.add
                        local.tee 6
                        i32.load offset=16
                        local.tee 9
                        br_if 0 (;@10;)
                      end
                      local.get 6
                      i32.const 16
                      i32.add
                      local.get 3
                      i32.store
                      local.get 3
                      local.get 0
                      i32.store offset=24
                    end
                    i32.const 8
                    local.set 8
                    i32.const 12
                    local.set 9
                    local.get 3
                    local.set 0
                    local.get 3
                    local.set 4
                    br 1 (;@7;)
                  end
                  local.get 0
                  i32.load offset=8
                  local.set 4
                  local.get 0
                  local.get 3
                  i32.store offset=8
                  local.get 4
                  local.get 3
                  i32.store offset=12
                  local.get 3
                  local.get 4
                  i32.store offset=8
                  i32.const 0
                  local.set 4
                  i32.const 24
                  local.set 8
                  i32.const 12
                  local.set 9
                end
                local.get 3
                local.get 9
                i32.add
                local.get 0
                i32.store
                local.get 3
                local.get 8
                i32.add
                local.get 4
                i32.store
              end
              i32.const 0
              i32.load offset=3912
              local.tee 4
              local.get 5
              i32.le_u
              br_if 0 (;@5;)
              i32.const 0
              i32.load offset=3924
              local.tee 3
              local.get 5
              i32.add
              local.tee 0
              local.get 4
              local.get 5
              i32.sub
              local.tee 4
              i32.const 1
              i32.or
              i32.store offset=4
              i32.const 0
              local.get 4
              i32.store offset=3912
              i32.const 0
              local.get 0
              i32.store offset=3924
              local.get 3
              local.get 5
              i32.const 3
              i32.or
              i32.store offset=4
              local.get 3
              i32.const 8
              i32.add
              local.set 4
              br 4 (;@1;)
            end
            i32.const 0
            local.set 4
            i32.const 0
            i32.const 48
            i32.store offset=4396
            br 3 (;@1;)
          end
          local.get 4
          local.get 8
          i32.store
          local.get 4
          local.get 4
          i32.load offset=4
          local.get 6
          i32.add
          i32.store offset=4
          local.get 8
          local.get 9
          local.get 5
          call $prepend_alloc
          local.set 4
          br 2 (;@1;)
        end
        block  ;; label = @3
          local.get 11
          i32.eqz
          br_if 0 (;@3;)
          block  ;; label = @4
            block  ;; label = @5
              local.get 9
              local.get 9
              i32.load offset=28
              local.tee 8
              i32.const 2
              i32.shl
              local.tee 0
              i32.load offset=4204
              i32.ne
              br_if 0 (;@5;)
              local.get 0
              i32.const 4204
              i32.add
              local.get 4
              i32.store
              local.get 4
              br_if 1 (;@4;)
              i32.const 0
              local.get 10
              i32.const -2
              local.get 8
              i32.rotl
              i32.and
              local.tee 10
              i32.store offset=3904
              br 2 (;@3;)
            end
            block  ;; label = @5
              block  ;; label = @6
                local.get 11
                i32.load offset=16
                local.get 9
                i32.ne
                br_if 0 (;@6;)
                local.get 11
                local.get 4
                i32.store offset=16
                br 1 (;@5;)
              end
              local.get 11
              local.get 4
              i32.store offset=20
            end
            local.get 4
            i32.eqz
            br_if 1 (;@3;)
          end
          local.get 4
          local.get 11
          i32.store offset=24
          block  ;; label = @4
            local.get 9
            i32.load offset=16
            local.tee 0
            i32.eqz
            br_if 0 (;@4;)
            local.get 4
            local.get 0
            i32.store offset=16
            local.get 0
            local.get 4
            i32.store offset=24
          end
          local.get 9
          i32.load offset=20
          local.tee 0
          i32.eqz
          br_if 0 (;@3;)
          local.get 4
          local.get 0
          i32.store offset=20
          local.get 0
          local.get 4
          i32.store offset=24
        end
        block  ;; label = @3
          block  ;; label = @4
            local.get 3
            i32.const 15
            i32.gt_u
            br_if 0 (;@4;)
            local.get 9
            local.get 3
            local.get 5
            i32.or
            local.tee 4
            i32.const 3
            i32.or
            i32.store offset=4
            local.get 9
            local.get 4
            i32.add
            local.tee 4
            local.get 4
            i32.load offset=4
            i32.const 1
            i32.or
            i32.store offset=4
            br 1 (;@3;)
          end
          local.get 9
          local.get 5
          i32.add
          local.tee 8
          local.get 3
          i32.const 1
          i32.or
          i32.store offset=4
          local.get 9
          local.get 5
          i32.const 3
          i32.or
          i32.store offset=4
          local.get 8
          local.get 3
          i32.add
          local.get 3
          i32.store
          block  ;; label = @4
            local.get 3
            i32.const 255
            i32.gt_u
            br_if 0 (;@4;)
            local.get 3
            i32.const -8
            i32.and
            i32.const 3940
            i32.add
            local.set 4
            block  ;; label = @5
              block  ;; label = @6
                i32.const 0
                i32.load offset=3900
                local.tee 5
                i32.const 1
                local.get 3
                i32.const 3
                i32.shr_u
                i32.shl
                local.tee 3
                i32.and
                br_if 0 (;@6;)
                i32.const 0
                local.get 5
                local.get 3
                i32.or
                i32.store offset=3900
                local.get 4
                local.set 3
                br 1 (;@5;)
              end
              local.get 4
              i32.load offset=8
              local.set 3
            end
            local.get 3
            local.get 8
            i32.store offset=12
            local.get 4
            local.get 8
            i32.store offset=8
            local.get 8
            local.get 4
            i32.store offset=12
            local.get 8
            local.get 3
            i32.store offset=8
            br 1 (;@3;)
          end
          i32.const 31
          local.set 4
          block  ;; label = @4
            local.get 3
            i32.const 16777215
            i32.gt_u
            br_if 0 (;@4;)
            local.get 3
            i32.const 38
            local.get 3
            i32.const 8
            i32.shr_u
            i32.clz
            local.tee 4
            i32.sub
            i32.shr_u
            i32.const 1
            i32.and
            local.get 4
            i32.const 1
            i32.shl
            i32.sub
            i32.const 62
            i32.add
            local.set 4
          end
          local.get 8
          local.get 4
          i32.store offset=28
          local.get 8
          i64.const 0
          i64.store offset=16 align=4
          local.get 4
          i32.const 2
          i32.shl
          i32.const 4204
          i32.add
          local.set 5
          block  ;; label = @4
            local.get 10
            i32.const 1
            local.get 4
            i32.shl
            local.tee 0
            i32.and
            br_if 0 (;@4;)
            local.get 5
            local.get 8
            i32.store
            i32.const 0
            local.get 10
            local.get 0
            i32.or
            i32.store offset=3904
            local.get 8
            local.get 5
            i32.store offset=24
            local.get 8
            local.get 8
            i32.store offset=8
            local.get 8
            local.get 8
            i32.store offset=12
            br 1 (;@3;)
          end
          local.get 3
          i32.const 0
          i32.const 25
          local.get 4
          i32.const 1
          i32.shr_u
          i32.sub
          local.get 4
          i32.const 31
          i32.eq
          select
          i32.shl
          local.set 4
          local.get 5
          i32.load
          local.set 0
          block  ;; label = @4
            loop  ;; label = @5
              local.get 0
              local.tee 5
              i32.load offset=4
              i32.const -8
              i32.and
              local.get 3
              i32.eq
              br_if 1 (;@4;)
              local.get 4
              i32.const 29
              i32.shr_u
              local.set 0
              local.get 4
              i32.const 1
              i32.shl
              local.set 4
              local.get 5
              local.get 0
              i32.const 4
              i32.and
              i32.add
              local.tee 6
              i32.load offset=16
              local.tee 0
              br_if 0 (;@5;)
            end
            local.get 6
            i32.const 16
            i32.add
            local.get 8
            i32.store
            local.get 8
            local.get 5
            i32.store offset=24
            local.get 8
            local.get 8
            i32.store offset=12
            local.get 8
            local.get 8
            i32.store offset=8
            br 1 (;@3;)
          end
          local.get 5
          i32.load offset=8
          local.tee 4
          local.get 8
          i32.store offset=12
          local.get 5
          local.get 8
          i32.store offset=8
          local.get 8
          i32.const 0
          i32.store offset=24
          local.get 8
          local.get 5
          i32.store offset=12
          local.get 8
          local.get 4
          i32.store offset=8
        end
        local.get 9
        i32.const 8
        i32.add
        local.set 4
        br 1 (;@1;)
      end
      block  ;; label = @2
        local.get 2
        i32.eqz
        br_if 0 (;@2;)
        block  ;; label = @3
          block  ;; label = @4
            local.get 8
            local.get 8
            i32.load offset=28
            local.tee 9
            i32.const 2
            i32.shl
            local.tee 0
            i32.load offset=4204
            i32.ne
            br_if 0 (;@4;)
            local.get 0
            i32.const 4204
            i32.add
            local.get 4
            i32.store
            local.get 4
            br_if 1 (;@3;)
            i32.const 0
            local.get 10
            i32.const -2
            local.get 9
            i32.rotl
            i32.and
            i32.store offset=3904
            br 2 (;@2;)
          end
          block  ;; label = @4
            block  ;; label = @5
              local.get 2
              i32.load offset=16
              local.get 8
              i32.ne
              br_if 0 (;@5;)
              local.get 2
              local.get 4
              i32.store offset=16
              br 1 (;@4;)
            end
            local.get 2
            local.get 4
            i32.store offset=20
          end
          local.get 4
          i32.eqz
          br_if 1 (;@2;)
        end
        local.get 4
        local.get 2
        i32.store offset=24
        block  ;; label = @3
          local.get 8
          i32.load offset=16
          local.tee 0
          i32.eqz
          br_if 0 (;@3;)
          local.get 4
          local.get 0
          i32.store offset=16
          local.get 0
          local.get 4
          i32.store offset=24
        end
        local.get 8
        i32.load offset=20
        local.tee 0
        i32.eqz
        br_if 0 (;@2;)
        local.get 4
        local.get 0
        i32.store offset=20
        local.get 0
        local.get 4
        i32.store offset=24
      end
      block  ;; label = @2
        block  ;; label = @3
          local.get 3
          i32.const 15
          i32.gt_u
          br_if 0 (;@3;)
          local.get 8
          local.get 3
          local.get 5
          i32.or
          local.tee 4
          i32.const 3
          i32.or
          i32.store offset=4
          local.get 8
          local.get 4
          i32.add
          local.tee 4
          local.get 4
          i32.load offset=4
          i32.const 1
          i32.or
          i32.store offset=4
          br 1 (;@2;)
        end
        local.get 8
        local.get 5
        i32.add
        local.tee 0
        local.get 3
        i32.const 1
        i32.or
        i32.store offset=4
        local.get 8
        local.get 5
        i32.const 3
        i32.or
        i32.store offset=4
        local.get 0
        local.get 3
        i32.add
        local.get 3
        i32.store
        block  ;; label = @3
          local.get 7
          i32.eqz
          br_if 0 (;@3;)
          local.get 7
          i32.const -8
          i32.and
          i32.const 3940
          i32.add
          local.set 5
          i32.const 0
          i32.load offset=3920
          local.set 4
          block  ;; label = @4
            block  ;; label = @5
              i32.const 1
              local.get 7
              i32.const 3
              i32.shr_u
              i32.shl
              local.tee 9
              local.get 6
              i32.and
              br_if 0 (;@5;)
              i32.const 0
              local.get 9
              local.get 6
              i32.or
              i32.store offset=3900
              local.get 5
              local.set 9
              br 1 (;@4;)
            end
            local.get 5
            i32.load offset=8
            local.set 9
          end
          local.get 9
          local.get 4
          i32.store offset=12
          local.get 5
          local.get 4
          i32.store offset=8
          local.get 4
          local.get 5
          i32.store offset=12
          local.get 4
          local.get 9
          i32.store offset=8
        end
        i32.const 0
        local.get 0
        i32.store offset=3920
        i32.const 0
        local.get 3
        i32.store offset=3908
      end
      local.get 8
      i32.const 8
      i32.add
      local.set 4
    end
    local.get 1
    i32.const 16
    i32.add
    global.set $__stack_pointer
    local.get 4)
  (func $prepend_alloc (type 1) (param i32 i32 i32) (result i32)
    (local i32 i32 i32 i32 i32 i32 i32)
    local.get 0
    i32.const -8
    local.get 0
    i32.sub
    i32.const 15
    i32.and
    i32.add
    local.tee 3
    local.get 2
    i32.const 3
    i32.or
    i32.store offset=4
    local.get 1
    i32.const -8
    local.get 1
    i32.sub
    i32.const 15
    i32.and
    i32.add
    local.tee 4
    local.get 3
    local.get 2
    i32.add
    local.tee 5
    i32.sub
    local.set 0
    block  ;; label = @1
      block  ;; label = @2
        local.get 4
        i32.const 0
        i32.load offset=3924
        i32.ne
        br_if 0 (;@2;)
        i32.const 0
        local.get 5
        i32.store offset=3924
        i32.const 0
        i32.const 0
        i32.load offset=3912
        local.get 0
        i32.add
        local.tee 2
        i32.store offset=3912
        local.get 5
        local.get 2
        i32.const 1
        i32.or
        i32.store offset=4
        br 1 (;@1;)
      end
      block  ;; label = @2
        local.get 4
        i32.const 0
        i32.load offset=3920
        i32.ne
        br_if 0 (;@2;)
        i32.const 0
        local.get 5
        i32.store offset=3920
        i32.const 0
        i32.const 0
        i32.load offset=3908
        local.get 0
        i32.add
        local.tee 2
        i32.store offset=3908
        local.get 5
        local.get 2
        i32.const 1
        i32.or
        i32.store offset=4
        local.get 5
        local.get 2
        i32.add
        local.get 2
        i32.store
        br 1 (;@1;)
      end
      block  ;; label = @2
        local.get 4
        i32.load offset=4
        local.tee 1
        i32.const 3
        i32.and
        i32.const 1
        i32.ne
        br_if 0 (;@2;)
        local.get 1
        i32.const -8
        i32.and
        local.set 6
        local.get 4
        i32.load offset=12
        local.set 2
        block  ;; label = @3
          block  ;; label = @4
            local.get 1
            i32.const 255
            i32.gt_u
            br_if 0 (;@4;)
            block  ;; label = @5
              local.get 2
              local.get 4
              i32.load offset=8
              local.tee 7
              i32.ne
              br_if 0 (;@5;)
              i32.const 0
              i32.const 0
              i32.load offset=3900
              i32.const -2
              local.get 1
              i32.const 3
              i32.shr_u
              i32.rotl
              i32.and
              i32.store offset=3900
              br 2 (;@3;)
            end
            local.get 2
            local.get 7
            i32.store offset=8
            local.get 7
            local.get 2
            i32.store offset=12
            br 1 (;@3;)
          end
          local.get 4
          i32.load offset=24
          local.set 8
          block  ;; label = @4
            block  ;; label = @5
              local.get 2
              local.get 4
              i32.eq
              br_if 0 (;@5;)
              local.get 4
              i32.load offset=8
              local.tee 1
              local.get 2
              i32.store offset=12
              local.get 2
              local.get 1
              i32.store offset=8
              br 1 (;@4;)
            end
            block  ;; label = @5
              block  ;; label = @6
                block  ;; label = @7
                  local.get 4
                  i32.load offset=20
                  local.tee 1
                  i32.eqz
                  br_if 0 (;@7;)
                  local.get 4
                  i32.const 20
                  i32.add
                  local.set 7
                  br 1 (;@6;)
                end
                local.get 4
                i32.load offset=16
                local.tee 1
                i32.eqz
                br_if 1 (;@5;)
                local.get 4
                i32.const 16
                i32.add
                local.set 7
              end
              loop  ;; label = @6
                local.get 7
                local.set 9
                local.get 1
                local.tee 2
                i32.const 20
                i32.add
                local.set 7
                local.get 2
                i32.load offset=20
                local.tee 1
                br_if 0 (;@6;)
                local.get 2
                i32.const 16
                i32.add
                local.set 7
                local.get 2
                i32.load offset=16
                local.tee 1
                br_if 0 (;@6;)
              end
              local.get 9
              i32.const 0
              i32.store
              br 1 (;@4;)
            end
            i32.const 0
            local.set 2
          end
          local.get 8
          i32.eqz
          br_if 0 (;@3;)
          block  ;; label = @4
            block  ;; label = @5
              local.get 4
              local.get 4
              i32.load offset=28
              local.tee 7
              i32.const 2
              i32.shl
              local.tee 1
              i32.load offset=4204
              i32.ne
              br_if 0 (;@5;)
              local.get 1
              i32.const 4204
              i32.add
              local.get 2
              i32.store
              local.get 2
              br_if 1 (;@4;)
              i32.const 0
              i32.const 0
              i32.load offset=3904
              i32.const -2
              local.get 7
              i32.rotl
              i32.and
              i32.store offset=3904
              br 2 (;@3;)
            end
            block  ;; label = @5
              block  ;; label = @6
                local.get 8
                i32.load offset=16
                local.get 4
                i32.ne
                br_if 0 (;@6;)
                local.get 8
                local.get 2
                i32.store offset=16
                br 1 (;@5;)
              end
              local.get 8
              local.get 2
              i32.store offset=20
            end
            local.get 2
            i32.eqz
            br_if 1 (;@3;)
          end
          local.get 2
          local.get 8
          i32.store offset=24
          block  ;; label = @4
            local.get 4
            i32.load offset=16
            local.tee 1
            i32.eqz
            br_if 0 (;@4;)
            local.get 2
            local.get 1
            i32.store offset=16
            local.get 1
            local.get 2
            i32.store offset=24
          end
          local.get 4
          i32.load offset=20
          local.tee 1
          i32.eqz
          br_if 0 (;@3;)
          local.get 2
          local.get 1
          i32.store offset=20
          local.get 1
          local.get 2
          i32.store offset=24
        end
        local.get 6
        local.get 0
        i32.add
        local.set 0
        local.get 4
        local.get 6
        i32.add
        local.tee 4
        i32.load offset=4
        local.set 1
      end
      local.get 4
      local.get 1
      i32.const -2
      i32.and
      i32.store offset=4
      local.get 5
      local.get 0
      i32.add
      local.get 0
      i32.store
      local.get 5
      local.get 0
      i32.const 1
      i32.or
      i32.store offset=4
      block  ;; label = @2
        local.get 0
        i32.const 255
        i32.gt_u
        br_if 0 (;@2;)
        local.get 0
        i32.const -8
        i32.and
        i32.const 3940
        i32.add
        local.set 2
        block  ;; label = @3
          block  ;; label = @4
            i32.const 0
            i32.load offset=3900
            local.tee 1
            i32.const 1
            local.get 0
            i32.const 3
            i32.shr_u
            i32.shl
            local.tee 0
            i32.and
            br_if 0 (;@4;)
            i32.const 0
            local.get 1
            local.get 0
            i32.or
            i32.store offset=3900
            local.get 2
            local.set 0
            br 1 (;@3;)
          end
          local.get 2
          i32.load offset=8
          local.set 0
        end
        local.get 0
        local.get 5
        i32.store offset=12
        local.get 2
        local.get 5
        i32.store offset=8
        local.get 5
        local.get 2
        i32.store offset=12
        local.get 5
        local.get 0
        i32.store offset=8
        br 1 (;@1;)
      end
      i32.const 31
      local.set 2
      block  ;; label = @2
        local.get 0
        i32.const 16777215
        i32.gt_u
        br_if 0 (;@2;)
        local.get 0
        i32.const 38
        local.get 0
        i32.const 8
        i32.shr_u
        i32.clz
        local.tee 2
        i32.sub
        i32.shr_u
        i32.const 1
        i32.and
        local.get 2
        i32.const 1
        i32.shl
        i32.sub
        i32.const 62
        i32.add
        local.set 2
      end
      local.get 5
      local.get 2
      i32.store offset=28
      local.get 5
      i64.const 0
      i64.store offset=16 align=4
      local.get 2
      i32.const 2
      i32.shl
      i32.const 4204
      i32.add
      local.set 1
      block  ;; label = @2
        i32.const 0
        i32.load offset=3904
        local.tee 7
        i32.const 1
        local.get 2
        i32.shl
        local.tee 4
        i32.and
        br_if 0 (;@2;)
        local.get 1
        local.get 5
        i32.store
        i32.const 0
        local.get 7
        local.get 4
        i32.or
        i32.store offset=3904
        local.get 5
        local.get 1
        i32.store offset=24
        local.get 5
        local.get 5
        i32.store offset=8
        local.get 5
        local.get 5
        i32.store offset=12
        br 1 (;@1;)
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
      local.get 1
      i32.load
      local.set 7
      block  ;; label = @2
        loop  ;; label = @3
          local.get 7
          local.tee 1
          i32.load offset=4
          i32.const -8
          i32.and
          local.get 0
          i32.eq
          br_if 1 (;@2;)
          local.get 2
          i32.const 29
          i32.shr_u
          local.set 7
          local.get 2
          i32.const 1
          i32.shl
          local.set 2
          local.get 1
          local.get 7
          i32.const 4
          i32.and
          i32.add
          local.tee 4
          i32.load offset=16
          local.tee 7
          br_if 0 (;@3;)
        end
        local.get 4
        i32.const 16
        i32.add
        local.get 5
        i32.store
        local.get 5
        local.get 1
        i32.store offset=24
        local.get 5
        local.get 5
        i32.store offset=12
        local.get 5
        local.get 5
        i32.store offset=8
        br 1 (;@1;)
      end
      local.get 1
      i32.load offset=8
      local.tee 2
      local.get 5
      i32.store offset=12
      local.get 1
      local.get 5
      i32.store offset=8
      local.get 5
      i32.const 0
      i32.store offset=24
      local.get 5
      local.get 1
      i32.store offset=12
      local.get 5
      local.get 2
      i32.store offset=8
    end
    local.get 3
    i32.const 8
    i32.add)
  (func $free (type 7) (param i32)
    local.get 0
    call $dlfree)
  (func $dlfree (type 7) (param i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32)
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
        i32.const 2
        i32.and
        i32.eqz
        br_if 1 (;@1;)
        local.get 1
        local.get 1
        i32.load
        local.tee 4
        i32.sub
        local.tee 1
        i32.const 0
        i32.load offset=3916
        i32.lt_u
        br_if 1 (;@1;)
        local.get 4
        local.get 0
        i32.add
        local.set 0
        block  ;; label = @3
          block  ;; label = @4
            block  ;; label = @5
              block  ;; label = @6
                local.get 1
                i32.const 0
                i32.load offset=3920
                i32.eq
                br_if 0 (;@6;)
                local.get 1
                i32.load offset=12
                local.set 2
                block  ;; label = @7
                  local.get 4
                  i32.const 255
                  i32.gt_u
                  br_if 0 (;@7;)
                  local.get 2
                  local.get 1
                  i32.load offset=8
                  local.tee 5
                  i32.ne
                  br_if 2 (;@5;)
                  i32.const 0
                  i32.const 0
                  i32.load offset=3900
                  i32.const -2
                  local.get 4
                  i32.const 3
                  i32.shr_u
                  i32.rotl
                  i32.and
                  i32.store offset=3900
                  br 5 (;@2;)
                end
                local.get 1
                i32.load offset=24
                local.set 6
                block  ;; label = @7
                  local.get 2
                  local.get 1
                  i32.eq
                  br_if 0 (;@7;)
                  local.get 1
                  i32.load offset=8
                  local.tee 4
                  local.get 2
                  i32.store offset=12
                  local.get 2
                  local.get 4
                  i32.store offset=8
                  br 4 (;@3;)
                end
                block  ;; label = @7
                  block  ;; label = @8
                    local.get 1
                    i32.load offset=20
                    local.tee 4
                    i32.eqz
                    br_if 0 (;@8;)
                    local.get 1
                    i32.const 20
                    i32.add
                    local.set 5
                    br 1 (;@7;)
                  end
                  local.get 1
                  i32.load offset=16
                  local.tee 4
                  i32.eqz
                  br_if 3 (;@4;)
                  local.get 1
                  i32.const 16
                  i32.add
                  local.set 5
                end
                loop  ;; label = @7
                  local.get 5
                  local.set 7
                  local.get 4
                  local.tee 2
                  i32.const 20
                  i32.add
                  local.set 5
                  local.get 2
                  i32.load offset=20
                  local.tee 4
                  br_if 0 (;@7;)
                  local.get 2
                  i32.const 16
                  i32.add
                  local.set 5
                  local.get 2
                  i32.load offset=16
                  local.tee 4
                  br_if 0 (;@7;)
                end
                local.get 7
                i32.const 0
                i32.store
                br 3 (;@3;)
              end
              local.get 3
              i32.load offset=4
              local.tee 2
              i32.const 3
              i32.and
              i32.const 3
              i32.ne
              br_if 3 (;@2;)
              local.get 3
              local.get 2
              i32.const -2
              i32.and
              i32.store offset=4
              i32.const 0
              local.get 0
              i32.store offset=3908
              local.get 3
              local.get 0
              i32.store
              local.get 1
              local.get 0
              i32.const 1
              i32.or
              i32.store offset=4
              return
            end
            local.get 2
            local.get 5
            i32.store offset=8
            local.get 5
            local.get 2
            i32.store offset=12
            br 2 (;@2;)
          end
          i32.const 0
          local.set 2
        end
        local.get 6
        i32.eqz
        br_if 0 (;@2;)
        block  ;; label = @3
          block  ;; label = @4
            local.get 1
            local.get 1
            i32.load offset=28
            local.tee 5
            i32.const 2
            i32.shl
            local.tee 4
            i32.load offset=4204
            i32.ne
            br_if 0 (;@4;)
            local.get 4
            i32.const 4204
            i32.add
            local.get 2
            i32.store
            local.get 2
            br_if 1 (;@3;)
            i32.const 0
            i32.const 0
            i32.load offset=3904
            i32.const -2
            local.get 5
            i32.rotl
            i32.and
            i32.store offset=3904
            br 2 (;@2;)
          end
          block  ;; label = @4
            block  ;; label = @5
              local.get 6
              i32.load offset=16
              local.get 1
              i32.ne
              br_if 0 (;@5;)
              local.get 6
              local.get 2
              i32.store offset=16
              br 1 (;@4;)
            end
            local.get 6
            local.get 2
            i32.store offset=20
          end
          local.get 2
          i32.eqz
          br_if 1 (;@2;)
        end
        local.get 2
        local.get 6
        i32.store offset=24
        block  ;; label = @3
          local.get 1
          i32.load offset=16
          local.tee 4
          i32.eqz
          br_if 0 (;@3;)
          local.get 2
          local.get 4
          i32.store offset=16
          local.get 4
          local.get 2
          i32.store offset=24
        end
        local.get 1
        i32.load offset=20
        local.tee 4
        i32.eqz
        br_if 0 (;@2;)
        local.get 2
        local.get 4
        i32.store offset=20
        local.get 4
        local.get 2
        i32.store offset=24
      end
      local.get 1
      local.get 3
      i32.ge_u
      br_if 0 (;@1;)
      local.get 3
      i32.load offset=4
      local.tee 4
      i32.const 1
      i32.and
      i32.eqz
      br_if 0 (;@1;)
      block  ;; label = @2
        block  ;; label = @3
          block  ;; label = @4
            block  ;; label = @5
              block  ;; label = @6
                local.get 4
                i32.const 2
                i32.and
                br_if 0 (;@6;)
                block  ;; label = @7
                  local.get 3
                  i32.const 0
                  i32.load offset=3924
                  i32.ne
                  br_if 0 (;@7;)
                  i32.const 0
                  local.get 1
                  i32.store offset=3924
                  i32.const 0
                  i32.const 0
                  i32.load offset=3912
                  local.get 0
                  i32.add
                  local.tee 0
                  i32.store offset=3912
                  local.get 1
                  local.get 0
                  i32.const 1
                  i32.or
                  i32.store offset=4
                  local.get 1
                  i32.const 0
                  i32.load offset=3920
                  i32.ne
                  br_if 6 (;@1;)
                  i32.const 0
                  i32.const 0
                  i32.store offset=3908
                  i32.const 0
                  i32.const 0
                  i32.store offset=3920
                  return
                end
                block  ;; label = @7
                  local.get 3
                  i32.const 0
                  i32.load offset=3920
                  local.tee 6
                  i32.ne
                  br_if 0 (;@7;)
                  i32.const 0
                  local.get 1
                  i32.store offset=3920
                  i32.const 0
                  i32.const 0
                  i32.load offset=3908
                  local.get 0
                  i32.add
                  local.tee 0
                  i32.store offset=3908
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
                local.get 4
                i32.const -8
                i32.and
                local.get 0
                i32.add
                local.set 0
                local.get 3
                i32.load offset=12
                local.set 2
                block  ;; label = @7
                  local.get 4
                  i32.const 255
                  i32.gt_u
                  br_if 0 (;@7;)
                  block  ;; label = @8
                    local.get 2
                    local.get 3
                    i32.load offset=8
                    local.tee 5
                    i32.ne
                    br_if 0 (;@8;)
                    i32.const 0
                    i32.const 0
                    i32.load offset=3900
                    i32.const -2
                    local.get 4
                    i32.const 3
                    i32.shr_u
                    i32.rotl
                    i32.and
                    i32.store offset=3900
                    br 5 (;@3;)
                  end
                  local.get 2
                  local.get 5
                  i32.store offset=8
                  local.get 5
                  local.get 2
                  i32.store offset=12
                  br 4 (;@3;)
                end
                local.get 3
                i32.load offset=24
                local.set 8
                block  ;; label = @7
                  local.get 2
                  local.get 3
                  i32.eq
                  br_if 0 (;@7;)
                  local.get 3
                  i32.load offset=8
                  local.tee 4
                  local.get 2
                  i32.store offset=12
                  local.get 2
                  local.get 4
                  i32.store offset=8
                  br 3 (;@4;)
                end
                block  ;; label = @7
                  block  ;; label = @8
                    local.get 3
                    i32.load offset=20
                    local.tee 4
                    i32.eqz
                    br_if 0 (;@8;)
                    local.get 3
                    i32.const 20
                    i32.add
                    local.set 5
                    br 1 (;@7;)
                  end
                  local.get 3
                  i32.load offset=16
                  local.tee 4
                  i32.eqz
                  br_if 2 (;@5;)
                  local.get 3
                  i32.const 16
                  i32.add
                  local.set 5
                end
                loop  ;; label = @7
                  local.get 5
                  local.set 7
                  local.get 4
                  local.tee 2
                  i32.const 20
                  i32.add
                  local.set 5
                  local.get 2
                  i32.load offset=20
                  local.tee 4
                  br_if 0 (;@7;)
                  local.get 2
                  i32.const 16
                  i32.add
                  local.set 5
                  local.get 2
                  i32.load offset=16
                  local.tee 4
                  br_if 0 (;@7;)
                end
                local.get 7
                i32.const 0
                i32.store
                br 2 (;@4;)
              end
              local.get 3
              local.get 4
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
              br 3 (;@2;)
            end
            i32.const 0
            local.set 2
          end
          local.get 8
          i32.eqz
          br_if 0 (;@3;)
          block  ;; label = @4
            block  ;; label = @5
              local.get 3
              local.get 3
              i32.load offset=28
              local.tee 5
              i32.const 2
              i32.shl
              local.tee 4
              i32.load offset=4204
              i32.ne
              br_if 0 (;@5;)
              local.get 4
              i32.const 4204
              i32.add
              local.get 2
              i32.store
              local.get 2
              br_if 1 (;@4;)
              i32.const 0
              i32.const 0
              i32.load offset=3904
              i32.const -2
              local.get 5
              i32.rotl
              i32.and
              i32.store offset=3904
              br 2 (;@3;)
            end
            block  ;; label = @5
              block  ;; label = @6
                local.get 8
                i32.load offset=16
                local.get 3
                i32.ne
                br_if 0 (;@6;)
                local.get 8
                local.get 2
                i32.store offset=16
                br 1 (;@5;)
              end
              local.get 8
              local.get 2
              i32.store offset=20
            end
            local.get 2
            i32.eqz
            br_if 1 (;@3;)
          end
          local.get 2
          local.get 8
          i32.store offset=24
          block  ;; label = @4
            local.get 3
            i32.load offset=16
            local.tee 4
            i32.eqz
            br_if 0 (;@4;)
            local.get 2
            local.get 4
            i32.store offset=16
            local.get 4
            local.get 2
            i32.store offset=24
          end
          local.get 3
          i32.load offset=20
          local.tee 4
          i32.eqz
          br_if 0 (;@3;)
          local.get 2
          local.get 4
          i32.store offset=20
          local.get 4
          local.get 2
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
        local.get 6
        i32.ne
        br_if 0 (;@2;)
        i32.const 0
        local.get 0
        i32.store offset=3908
        return
      end
      block  ;; label = @2
        local.get 0
        i32.const 255
        i32.gt_u
        br_if 0 (;@2;)
        local.get 0
        i32.const -8
        i32.and
        i32.const 3940
        i32.add
        local.set 2
        block  ;; label = @3
          block  ;; label = @4
            i32.const 0
            i32.load offset=3900
            local.tee 4
            i32.const 1
            local.get 0
            i32.const 3
            i32.shr_u
            i32.shl
            local.tee 0
            i32.and
            br_if 0 (;@4;)
            i32.const 0
            local.get 4
            local.get 0
            i32.or
            i32.store offset=3900
            local.get 2
            local.set 0
            br 1 (;@3;)
          end
          local.get 2
          i32.load offset=8
          local.set 0
        end
        local.get 0
        local.get 1
        i32.store offset=12
        local.get 2
        local.get 1
        i32.store offset=8
        local.get 1
        local.get 2
        i32.store offset=12
        local.get 1
        local.get 0
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
        i32.const 38
        local.get 0
        i32.const 8
        i32.shr_u
        i32.clz
        local.tee 2
        i32.sub
        i32.shr_u
        i32.const 1
        i32.and
        local.get 2
        i32.const 1
        i32.shl
        i32.sub
        i32.const 62
        i32.add
        local.set 2
      end
      local.get 1
      local.get 2
      i32.store offset=28
      local.get 1
      i64.const 0
      i64.store offset=16 align=4
      local.get 2
      i32.const 2
      i32.shl
      i32.const 4204
      i32.add
      local.set 5
      block  ;; label = @2
        block  ;; label = @3
          block  ;; label = @4
            block  ;; label = @5
              i32.const 0
              i32.load offset=3904
              local.tee 4
              i32.const 1
              local.get 2
              i32.shl
              local.tee 3
              i32.and
              br_if 0 (;@5;)
              local.get 5
              local.get 1
              i32.store
              i32.const 0
              local.get 4
              local.get 3
              i32.or
              i32.store offset=3904
              i32.const 8
              local.set 0
              i32.const 24
              local.set 2
              br 1 (;@4;)
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
            local.get 5
            i32.load
            local.set 5
            loop  ;; label = @5
              local.get 5
              local.tee 4
              i32.load offset=4
              i32.const -8
              i32.and
              local.get 0
              i32.eq
              br_if 2 (;@3;)
              local.get 2
              i32.const 29
              i32.shr_u
              local.set 5
              local.get 2
              i32.const 1
              i32.shl
              local.set 2
              local.get 4
              local.get 5
              i32.const 4
              i32.and
              i32.add
              local.tee 3
              i32.load offset=16
              local.tee 5
              br_if 0 (;@5;)
            end
            local.get 3
            i32.const 16
            i32.add
            local.get 1
            i32.store
            i32.const 8
            local.set 0
            i32.const 24
            local.set 2
            local.get 4
            local.set 5
          end
          local.get 1
          local.set 4
          local.get 1
          local.set 3
          br 1 (;@2;)
        end
        local.get 4
        i32.load offset=8
        local.tee 5
        local.get 1
        i32.store offset=12
        local.get 4
        local.get 1
        i32.store offset=8
        i32.const 0
        local.set 3
        i32.const 24
        local.set 0
        i32.const 8
        local.set 2
      end
      local.get 1
      local.get 2
      i32.add
      local.get 5
      i32.store
      local.get 1
      local.get 4
      i32.store offset=12
      local.get 1
      local.get 0
      i32.add
      local.get 3
      i32.store
      i32.const 0
      i32.const 0
      i32.load offset=3932
      i32.const -1
      i32.add
      local.tee 1
      i32.const -1
      local.get 1
      select
      i32.store offset=3932
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
      local.get 2
      i32.eqz
      br_if 0 (;@1;)
      local.get 0
      i32.const 0
      local.get 2
      memory.fill
    end
    local.get 0)
  (func $_Exit (type 7) (param i32)
    local.get 0
    call $__wasi_proc_exit
    unreachable)
  (func $__main_void (type 10) (result i32)
    (local i32 i32 i32)
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
              i32.const 1
              i32.add
              local.tee 1
              i32.eqz
              br_if 1 (;@4;)
              local.get 0
              i32.load offset=12
              call $malloc
              local.tee 2
              i32.eqz
              br_if 2 (;@3;)
              local.get 1
              i32.const 4
              call $calloc
              local.tee 1
              i32.eqz
              br_if 3 (;@2;)
              local.get 1
              local.get 2
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
      local.get 2
      call $free
      i32.const 70
      call $_Exit
      unreachable
    end
    local.get 2
    call $free
    local.get 1
    call $free
    i32.const 71
    call $_Exit
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
  (func $__wasi_fd_close (type 0) (param i32) (result i32)
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
  (func $__wasi_fd_fdstat_set_flags (type 3) (param i32 i32) (result i32)
    local.get 0
    local.get 1
    call $__imported_wasi_snapshot_preview1_fd_fdstat_set_flags
    i32.const 65535
    i32.and)
  (func $__wasi_fd_prestat_get (type 3) (param i32 i32) (result i32)
    local.get 0
    local.get 1
    call $__imported_wasi_snapshot_preview1_fd_prestat_get
    i32.const 65535
    i32.and)
  (func $__wasi_fd_prestat_dir_name (type 1) (param i32 i32 i32) (result i32)
    local.get 0
    local.get 1
    local.get 2
    call $__imported_wasi_snapshot_preview1_fd_prestat_dir_name
    i32.const 65535
    i32.and)
  (func $__wasi_fd_read (type 4) (param i32 i32 i32 i32) (result i32)
    local.get 0
    local.get 1
    local.get 2
    local.get 3
    call $__imported_wasi_snapshot_preview1_fd_read
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
  (func $__wasi_fd_write (type 4) (param i32 i32 i32 i32) (result i32)
    local.get 0
    local.get 1
    local.get 2
    local.get 3
    call $__imported_wasi_snapshot_preview1_fd_write
    i32.const 65535
    i32.and)
  (func $__wasi_path_open (type 12) (param i32 i32 i32 i32 i64 i64 i32 i32) (result i32)
    local.get 0
    local.get 1
    local.get 2
    local.get 2
    call $strlen
    local.get 3
    local.get 4
    local.get 5
    local.get 6
    local.get 7
    call $__imported_wasi_snapshot_preview1_path_open
    i32.const 65535
    i32.and)
  (func $__wasi_proc_exit (type 7) (param i32)
    local.get 0
    call $__imported_wasi_snapshot_preview1_proc_exit
    unreachable)
  (func $abort (type 8)
    unreachable)
  (func $sbrk (type 0) (param i32) (result i32)
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
        i32.store offset=4396
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
  (func $__wasi_init_tp (type 8)
    (local i32 i32)
    i32.const 0
    i32.const 5516
    i32.store offset=5516
    i32.const 71168
    local.set 0
    block  ;; label = @1
      block  ;; label = @2
        i32.const 71168
        i32.eqz
        br_if 0 (;@2;)
        i32.const 71168
        i32.const 5632
        i32.sub
        local.set 1
        br 1 (;@1;)
      end
      global.get $__stack_pointer
      local.set 1
      i32.const 71168
      i32.const 5624
      i32.sub
      i32.const 1024
      local.get 1
      i32.const 1024
      i32.gt_u
      local.tee 0
      select
      local.set 1
      i32.const 71168
      i32.const 1024
      local.get 0
      select
      local.set 0
    end
    i32.const 56
    i32.const 0
    i32.store offset=5516
    i32.const 52
    local.get 1
    i32.store offset=5516
    i32.const 48
    local.get 0
    i32.store offset=5516
    i32.const 8
    i32.const 5516
    i32.store offset=5516
    i32.const 4
    i32.const 5516
    i32.store offset=5516
    i32.const 12
    i32.const 0
    i32.load offset=4400
    i32.store offset=5516
    i32.const 0
    local.get 1
    i32.const 8388608
    local.get 1
    i32.const 8388608
    i32.lt_u
    select
    i32.store offset=3852)
  (func $dummy (type 8))
  (func $__wasm_call_dtors (type 8)
    call $dummy
    call $__stdio_exit)
  (func $__fmodeflags (type 0) (param i32) (result i32)
    (local i32 i32 i32)
    i32.const 335544320
    i32.const 67108864
    i32.const 268435456
    local.get 0
    i32.load8_u
    local.tee 1
    i32.const 114
    i32.eq
    local.tee 2
    select
    local.get 0
    i32.const 43
    call $strchr
    select
    local.tee 3
    i32.const 16384
    i32.or
    local.get 3
    local.get 0
    i32.const 120
    call $strchr
    select
    local.tee 0
    local.get 0
    i32.const 4096
    i32.or
    local.get 2
    select
    local.tee 0
    i32.const 32768
    i32.or
    local.get 0
    local.get 1
    i32.const 119
    i32.eq
    select
    local.get 1
    i32.const 97
    i32.eq
    i32.or)
  (func $__wasilibc_populate_preopens (type 8)
    (local i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 0
    global.set $__stack_pointer
    block  ;; label = @1
      i32.const 0
      i32.load8_u offset=4412
      i32.const 1
      i32.and
      br_if 0 (;@1;)
      i32.const 0
      i32.load8_u offset=4412
      i32.const 1
      i32.and
      br_if 0 (;@1;)
      i32.const 3
      local.set 1
      block  ;; label = @2
        block  ;; label = @3
          loop  ;; label = @4
            block  ;; label = @5
              local.get 1
              local.get 0
              i32.const 8
              i32.add
              call $__wasi_fd_prestat_get
              local.tee 2
              i32.eqz
              br_if 0 (;@5;)
              local.get 2
              i32.const 8
              i32.ne
              br_if 2 (;@3;)
              i32.const 0
              i32.const 1
              i32.store8 offset=4412
              br 4 (;@1;)
            end
            block  ;; label = @5
              local.get 0
              i32.load8_u offset=8
              br_if 0 (;@5;)
              local.get 0
              i32.load offset=12
              local.tee 3
              i32.const 1
              i32.add
              call $malloc
              local.tee 2
              i32.eqz
              br_if 3 (;@2;)
              local.get 1
              local.get 2
              local.get 3
              call $__wasi_fd_prestat_dir_name
              br_if 2 (;@3;)
              local.get 2
              local.get 0
              i32.load offset=12
              i32.add
              i32.const 0
              i32.store8
              local.get 1
              local.get 2
              call $internal_register_preopened_fd_unlocked
              local.set 3
              local.get 2
              call $free
              local.get 3
              br_if 3 (;@2;)
            end
            local.get 1
            i32.const 1
            i32.add
            local.set 1
            br 0 (;@4;)
          end
        end
        i32.const 71
        call $_Exit
        unreachable
      end
      i32.const 70
      call $_Exit
      unreachable
    end
    local.get 0
    i32.const 16
    i32.add
    global.set $__stack_pointer)
  (func $internal_register_preopened_fd_unlocked (type 3) (param i32 i32) (result i32)
    (local i32 i32 i32 i32 i32)
    block  ;; label = @1
      block  ;; label = @2
        local.get 0
        i32.const 2
        i32.add
        br_table 1 (;@1;) 1 (;@1;) 0 (;@2;)
      end
      local.get 1
      i32.eqz
      br_if 0 (;@1;)
      block  ;; label = @2
        i32.const 0
        i32.load offset=4404
        local.tee 2
        i32.const 0
        i32.load offset=4416
        i32.ne
        br_if 0 (;@2;)
        i32.const 0
        i32.load offset=4408
        local.set 3
        block  ;; label = @3
          i32.const 8
          local.get 2
          i32.const 1
          i32.shl
          i32.const 4
          local.get 2
          select
          local.tee 4
          call $calloc
          local.tee 5
          br_if 0 (;@3;)
          i32.const -1
          return
        end
        block  ;; label = @3
          local.get 2
          i32.const 3
          i32.shl
          local.tee 6
          i32.eqz
          br_if 0 (;@3;)
          local.get 5
          local.get 3
          local.get 6
          memory.copy
        end
        i32.const 0
        local.get 4
        i32.store offset=4416
        i32.const 0
        local.get 5
        i32.store offset=4408
        local.get 3
        call $free
      end
      block  ;; label = @2
        loop  ;; label = @3
          block  ;; label = @4
            block  ;; label = @5
              local.get 1
              local.tee 3
              i32.load8_u
              i32.const -46
              i32.add
              br_table 1 (;@4;) 0 (;@5;) 3 (;@2;)
            end
            local.get 3
            i32.const 1
            i32.add
            local.set 1
            br 1 (;@3;)
          end
          local.get 3
          i32.const 1
          i32.add
          local.set 1
          local.get 3
          i32.load8_u offset=1
          local.tee 5
          i32.eqz
          br_if 0 (;@3;)
          local.get 5
          i32.const 47
          i32.ne
          br_if 1 (;@2;)
          local.get 3
          i32.const 2
          i32.add
          local.set 1
          br 0 (;@3;)
        end
      end
      block  ;; label = @2
        local.get 3
        call $strdup
        local.tee 3
        br_if 0 (;@2;)
        i32.const -1
        return
      end
      i32.const 0
      local.get 2
      i32.const 1
      i32.add
      i32.store offset=4404
      i32.const 0
      i32.load offset=4408
      local.get 2
      i32.const 3
      i32.shl
      i32.add
      local.tee 1
      local.get 0
      i32.store offset=4
      local.get 1
      local.get 3
      i32.store
      i32.const 0
      return
    end
    call $abort
    unreachable)
  (func $__wasilibc_find_relpath (type 4) (param i32 i32 i32 i32) (result i32)
    (local i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 4
    global.set $__stack_pointer
    local.get 4
    local.get 3
    i32.store offset=12
    block  ;; label = @1
      block  ;; label = @2
        i32.const 0
        i32.eqz
        br_if 0 (;@2;)
        local.get 0
        local.get 1
        local.get 2
        local.get 4
        i32.const 12
        i32.add
        i32.const 0
        call $undefined_weak:__wasilibc_find_relpath_alloc
        local.set 3
        br 1 (;@1;)
      end
      local.get 0
      local.get 1
      local.get 2
      call $__wasilibc_find_abspath
      local.set 3
    end
    local.get 4
    i32.const 16
    i32.add
    global.set $__stack_pointer
    local.get 3)
  (func $__wasilibc_find_abspath (type 1) (param i32 i32 i32) (result i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32 i32)
    local.get 0
    i32.const -1
    i32.add
    local.set 0
    call $__wasilibc_populate_preopens
    loop  ;; label = @1
      local.get 0
      i32.const 1
      i32.add
      local.tee 0
      i32.load8_u
      i32.const 47
      i32.eq
      br_if 0 (;@1;)
    end
    i32.const 0
    local.set 3
    block  ;; label = @1
      block  ;; label = @2
        i32.const 0
        i32.load offset=4404
        local.tee 4
        i32.eqz
        br_if 0 (;@2;)
        i32.const 0
        i32.load offset=4408
        local.set 5
        i32.const -1
        local.set 6
        loop  ;; label = @3
          local.get 5
          local.get 4
          i32.const 3
          i32.shl
          i32.add
          local.tee 7
          i32.const -8
          i32.add
          i32.load
          local.tee 8
          call $strlen
          local.set 9
          block  ;; label = @4
            block  ;; label = @5
              local.get 6
              i32.const -1
              i32.eq
              br_if 0 (;@5;)
              local.get 9
              local.get 3
              i32.le_u
              br_if 1 (;@4;)
            end
            local.get 0
            i32.load8_u
            local.set 10
            block  ;; label = @5
              block  ;; label = @6
                local.get 9
                br_if 0 (;@6;)
                local.get 10
                i32.const 255
                i32.and
                i32.const 47
                i32.ne
                br_if 1 (;@5;)
              end
              local.get 0
              local.get 8
              local.get 9
              call $memcmp
              br_if 1 (;@4;)
              block  ;; label = @6
                local.get 9
                i32.eqz
                br_if 0 (;@6;)
                local.get 8
                i32.const -1
                i32.add
                local.set 11
                local.get 9
                local.set 10
                block  ;; label = @7
                  loop  ;; label = @8
                    local.get 11
                    local.get 10
                    i32.add
                    i32.load8_u
                    i32.const 47
                    i32.ne
                    br_if 1 (;@7;)
                    local.get 10
                    i32.const -1
                    i32.add
                    local.tee 10
                    br_if 0 (;@8;)
                  end
                  i32.const 0
                  local.set 10
                end
                local.get 0
                local.get 10
                i32.add
                i32.load8_u
                local.set 10
              end
              local.get 10
              i32.const 255
              i32.and
              local.tee 10
              i32.const 47
              i32.eq
              br_if 0 (;@5;)
              local.get 10
              br_if 1 (;@4;)
            end
            local.get 1
            local.get 8
            i32.store
            local.get 7
            i32.const -4
            i32.add
            i32.load
            local.set 6
            local.get 9
            local.set 3
          end
          local.get 4
          i32.const -1
          i32.add
          local.tee 4
          br_if 0 (;@3;)
        end
        local.get 6
        i32.const -1
        i32.ne
        br_if 1 (;@1;)
      end
      i32.const 0
      i32.const 44
      i32.store offset=4396
      i32.const -1
      return
    end
    local.get 0
    local.get 3
    i32.add
    i32.const -1
    i32.add
    local.set 0
    loop  ;; label = @1
      local.get 0
      i32.const 1
      i32.add
      local.tee 0
      i32.load8_u
      local.tee 4
      i32.const 47
      i32.eq
      br_if 0 (;@1;)
    end
    local.get 2
    local.get 0
    i32.const 1095
    local.get 4
    select
    i32.store
    local.get 6)
  (func $__wasilibc_nocwd_openat_nomode (type 1) (param i32 i32 i32) (result i32)
    (local i32 i32 i64 i64)
    global.get $__stack_pointer
    i32.const 32
    i32.sub
    local.tee 3
    global.set $__stack_pointer
    block  ;; label = @1
      block  ;; label = @2
        block  ;; label = @3
          block  ;; label = @4
            local.get 2
            i32.const 503316480
            i32.and
            i32.const -33554432
            i32.add
            i32.const 25
            i32.shr_u
            local.tee 4
            i32.const 9
            i32.gt_u
            br_if 0 (;@4;)
            i32.const 1
            local.get 4
            i32.shl
            local.tee 4
            i32.const 642
            i32.and
            br_if 1 (;@3;)
            i64.const -4211012
            local.set 5
            local.get 4
            i32.const 9
            i32.and
            br_if 2 (;@2;)
          end
          i32.const 0
          i32.const 28
          i32.store offset=4396
          i32.const -1
          local.set 4
          br 2 (;@1;)
        end
        i64.const -4194626
        i64.const -4211012
        local.get 2
        i32.const 67108864
        i32.and
        select
        local.tee 5
        i64.const 4194625
        i64.or
        local.get 5
        local.get 2
        i32.const 268435456
        i32.and
        select
        local.set 5
      end
      block  ;; label = @2
        local.get 0
        local.get 3
        i32.const 8
        i32.add
        call $__wasi_fd_fdstat_get
        local.tee 4
        i32.eqz
        br_if 0 (;@2;)
        i32.const 0
        local.get 4
        i32.store offset=4396
        i32.const -1
        local.set 4
        br 1 (;@1;)
      end
      i32.const -1
      local.set 4
      block  ;; label = @2
        local.get 0
        local.get 2
        i32.const -1
        i32.xor
        i32.const 24
        i32.shr_u
        i32.const 1
        i32.and
        local.get 1
        local.get 2
        i32.const 12
        i32.shr_u
        i32.const 4095
        i32.and
        local.get 3
        i64.load offset=24
        local.tee 6
        local.get 5
        i64.and
        local.get 6
        local.get 2
        i32.const 4095
        i32.and
        local.get 3
        i32.const 4
        i32.add
        call $__wasi_path_open
        local.tee 2
        i32.eqz
        br_if 0 (;@2;)
        i32.const 0
        local.get 2
        i32.store offset=4396
        br 1 (;@1;)
      end
      local.get 3
      i32.load offset=4
      local.set 4
    end
    local.get 3
    i32.const 32
    i32.add
    global.set $__stack_pointer
    local.get 4)
  (func $close (type 0) (param i32) (result i32)
    call $__wasilibc_populate_preopens
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
    i32.store offset=4396
    i32.const -1)
  (func $__wasilibc_open_nomode (type 3) (param i32 i32) (result i32)
    (local i32 i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 2
    global.set $__stack_pointer
    block  ;; label = @1
      block  ;; label = @2
        i32.const 0
        i32.eqz
        br_if 0 (;@2;)
        local.get 0
        local.get 2
        i32.const 12
        i32.add
        i32.const 4420
        i32.const 4424
        i32.const 1
        call $undefined_weak:__wasilibc_find_relpath_alloc
        local.set 0
        br 1 (;@1;)
      end
      local.get 0
      local.get 2
      i32.const 12
      i32.add
      i32.const 4420
      i32.const 0
      i32.load offset=4424
      call $__wasilibc_find_relpath
      local.set 0
    end
    i32.const -1
    local.set 3
    block  ;; label = @1
      block  ;; label = @2
        local.get 0
        i32.const -1
        i32.ne
        br_if 0 (;@2;)
        i32.const 0
        i32.const 44
        i32.store offset=4396
        br 1 (;@1;)
      end
      local.get 0
      i32.const 0
      i32.load offset=4420
      local.get 1
      call $__wasilibc_nocwd_openat_nomode
      local.set 3
    end
    local.get 2
    i32.const 16
    i32.add
    global.set $__stack_pointer
    local.get 3)
  (func $fcntl (type 1) (param i32 i32 i32) (result i32)
    (local i32 i64 i64)
    global.get $__stack_pointer
    i32.const 32
    i32.sub
    local.tee 3
    global.set $__stack_pointer
    block  ;; label = @1
      block  ;; label = @2
        block  ;; label = @3
          block  ;; label = @4
            block  ;; label = @5
              block  ;; label = @6
                local.get 1
                i32.const -1
                i32.add
                br_table 5 (;@1;) 0 (;@6;) 1 (;@5;) 2 (;@4;) 3 (;@3;)
              end
              i32.const 0
              local.set 1
              br 4 (;@1;)
            end
            block  ;; label = @5
              local.get 0
              local.get 3
              i32.const 8
              i32.add
              call $__wasi_fd_fdstat_get
              local.tee 1
              i32.eqz
              br_if 0 (;@5;)
              i32.const 0
              local.get 1
              i32.store offset=4396
              br 3 (;@2;)
            end
            local.get 3
            i64.load offset=16
            local.tee 4
            i64.const 64
            i64.and
            local.set 5
            local.get 3
            i32.load16_u offset=10
            local.set 1
            block  ;; label = @5
              local.get 4
              i64.const 16386
              i64.and
              i64.eqz
              br_if 0 (;@5;)
              block  ;; label = @6
                local.get 5
                i64.eqz
                br_if 0 (;@6;)
                local.get 1
                i32.const 335544320
                i32.or
                local.set 1
                br 5 (;@1;)
              end
              local.get 1
              i32.const 67108864
              i32.or
              local.set 1
              br 4 (;@1;)
            end
            block  ;; label = @5
              local.get 5
              i64.eqz
              br_if 0 (;@5;)
              local.get 1
              i32.const 268435456
              i32.or
              local.set 1
              br 4 (;@1;)
            end
            local.get 1
            i32.const 134217728
            i32.or
            local.set 1
            br 3 (;@1;)
          end
          local.get 3
          local.get 2
          i32.const 4
          i32.add
          i32.store offset=8
          block  ;; label = @4
            local.get 0
            local.get 2
            i32.load
            i32.const 4095
            i32.and
            call $__wasi_fd_fdstat_set_flags
            local.tee 1
            br_if 0 (;@4;)
            i32.const 0
            local.set 1
            br 3 (;@1;)
          end
          i32.const 0
          local.get 1
          i32.store offset=4396
          br 1 (;@2;)
        end
        i32.const 0
        i32.const 28
        i32.store offset=4396
      end
      i32.const -1
      local.set 1
    end
    local.get 3
    i32.const 32
    i32.add
    global.set $__stack_pointer
    local.get 1)
  (func $__isatty (type 0) (param i32) (result i32)
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
      i32.store offset=4396
    end
    local.get 1
    i32.const 32
    i32.add
    global.set $__stack_pointer
    local.get 2)
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
        local.tee 2
        i32.eqz
        br_if 0 (;@2;)
        i32.const 0
        i32.const 70
        local.get 2
        local.get 2
        i32.const 76
        i32.eq
        select
        i32.store offset=4396
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
        i32.store offset=4396
        br 1 (;@1;)
      end
      local.get 3
      i32.const 0
      i32.store offset=12
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
        i32.store offset=4396
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
    local.tee 4
    i32.store offset=4
    i32.const 2
    local.set 5
    block  ;; label = @1
      block  ;; label = @2
        local.get 0
        i32.load offset=56
        local.get 3
        i32.const 2
        call $writev
        local.tee 1
        local.get 4
        local.get 2
        i32.add
        local.tee 6
        i32.eq
        br_if 0 (;@2;)
        local.get 3
        local.set 4
        loop  ;; label = @3
          block  ;; label = @4
            local.get 1
            i32.const -1
            i32.gt_s
            br_if 0 (;@4;)
            i32.const 0
            local.set 1
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
            local.get 5
            i32.const 2
            i32.eq
            br_if 3 (;@1;)
            local.get 2
            local.get 4
            i32.load offset=4
            i32.sub
            local.set 1
            br 3 (;@1;)
          end
          local.get 4
          i32.const 8
          i32.const 0
          local.get 1
          local.get 4
          i32.load offset=4
          local.tee 7
          i32.gt_u
          local.tee 8
          select
          i32.add
          local.tee 9
          local.get 9
          i32.load
          local.get 1
          local.get 7
          i32.const 0
          local.get 8
          select
          i32.sub
          local.tee 7
          i32.add
          i32.store
          local.get 4
          i32.const 12
          i32.const 4
          local.get 8
          select
          i32.add
          local.tee 4
          local.get 4
          i32.load
          local.get 7
          i32.sub
          i32.store
          local.get 9
          local.set 4
          local.get 6
          local.get 1
          i32.sub
          local.tee 6
          local.get 0
          i32.load offset=56
          local.get 9
          local.get 5
          local.get 8
          i32.sub
          local.tee 5
          call $writev
          local.tee 1
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
      local.set 1
    end
    local.get 3
    i32.const 16
    i32.add
    global.set $__stack_pointer
    local.get 1)
  (func $readv (type 1) (param i32 i32 i32) (result i32)
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
        i32.store offset=4396
        br 1 (;@1;)
      end
      block  ;; label = @2
        local.get 0
        local.get 1
        local.get 2
        local.get 3
        i32.const 12
        i32.add
        call $__wasi_fd_read
        local.tee 2
        i32.eqz
        br_if 0 (;@2;)
        i32.const 0
        local.get 2
        i32.store offset=4396
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
  (func $read (type 1) (param i32 i32 i32) (result i32)
    (local i32)
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
    block  ;; label = @1
      block  ;; label = @2
        local.get 0
        local.get 3
        i32.const 8
        i32.add
        i32.const 1
        local.get 3
        i32.const 4
        i32.add
        call $__wasi_fd_read
        local.tee 2
        i32.eqz
        br_if 0 (;@2;)
        i32.const 0
        i32.const 8
        local.get 2
        local.get 2
        i32.const 76
        i32.eq
        select
        i32.store offset=4396
        i32.const -1
        local.set 2
        br 1 (;@1;)
      end
      local.get 3
      i32.load offset=4
      local.set 2
    end
    local.get 3
    i32.const 16
    i32.add
    global.set $__stack_pointer
    local.get 2)
  (func $__stdio_read (type 1) (param i32 i32 i32) (result i32)
    (local i32 i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 3
    global.set $__stack_pointer
    local.get 3
    local.get 1
    i32.store
    local.get 3
    local.get 0
    i32.load offset=44
    local.tee 4
    i32.store offset=12
    local.get 3
    local.get 0
    i32.load offset=40
    local.tee 5
    i32.store offset=8
    local.get 3
    local.get 2
    local.get 4
    i32.const 0
    i32.ne
    i32.sub
    local.tee 6
    i32.store offset=4
    local.get 0
    i32.load offset=56
    local.set 7
    block  ;; label = @1
      block  ;; label = @2
        local.get 6
        i32.eqz
        br_if 0 (;@2;)
        local.get 7
        local.get 3
        i32.const 2
        call $readv
        local.set 4
        br 1 (;@1;)
      end
      local.get 7
      local.get 5
      local.get 4
      call $read
      local.set 4
    end
    i32.const 0
    local.set 6
    block  ;; label = @1
      block  ;; label = @2
        local.get 4
        i32.const 0
        i32.gt_s
        br_if 0 (;@2;)
        local.get 0
        local.get 0
        i32.load
        i32.const 32
        i32.const 16
        local.get 4
        select
        i32.or
        i32.store
        br 1 (;@1;)
      end
      block  ;; label = @2
        local.get 4
        local.get 3
        i32.load offset=4
        local.tee 7
        i32.gt_u
        br_if 0 (;@2;)
        local.get 4
        local.set 6
        br 1 (;@1;)
      end
      local.get 0
      local.get 0
      i32.load offset=40
      local.tee 6
      i32.store offset=4
      local.get 0
      local.get 6
      local.get 4
      local.get 7
      i32.sub
      i32.add
      i32.store offset=8
      block  ;; label = @2
        local.get 0
        i32.load offset=44
        i32.eqz
        br_if 0 (;@2;)
        local.get 0
        local.get 6
        i32.const 1
        i32.add
        i32.store offset=4
        local.get 1
        local.get 2
        i32.add
        i32.const -1
        i32.add
        local.get 6
        i32.load8_u
        i32.store8
      end
      local.get 2
      local.set 6
    end
    local.get 3
    i32.const 16
    i32.add
    global.set $__stack_pointer
    local.get 6)
  (func $__stdio_close (type 0) (param i32) (result i32)
    local.get 0
    i32.load offset=56
    call $close)
  (func $__fdopen (type 3) (param i32 i32) (result i32)
    (local i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 32
    i32.sub
    local.tee 2
    global.set $__stack_pointer
    block  ;; label = @1
      block  ;; label = @2
        i32.const 1083
        local.get 1
        i32.load8_s
        local.tee 3
        i32.const 4
        call $memchr
        br_if 0 (;@2;)
        i32.const 0
        local.set 4
        i32.const 0
        i32.const 28
        i32.store offset=4396
        br 1 (;@1;)
      end
      block  ;; label = @2
        i32.const 1144
        call $malloc
        local.tee 4
        br_if 0 (;@2;)
        i32.const 0
        local.set 4
        br 1 (;@1;)
      end
      i32.const 0
      local.set 5
      block  ;; label = @2
        i32.const 112
        i32.eqz
        br_if 0 (;@2;)
        local.get 4
        i32.const 0
        i32.const 112
        memory.fill
      end
      block  ;; label = @2
        local.get 1
        i32.const 43
        call $strchr
        br_if 0 (;@2;)
        local.get 4
        i32.const 8
        i32.const 4
        local.get 3
        i32.const 114
        i32.eq
        select
        local.tee 5
        i32.store
      end
      block  ;; label = @2
        local.get 1
        i32.const 101
        call $strchr
        i32.eqz
        br_if 0 (;@2;)
        local.get 2
        i32.const 1
        i32.store offset=16
        local.get 0
        i32.const 2
        local.get 2
        i32.const 16
        i32.add
        call $fcntl
        drop
        local.get 1
        i32.load8_u
        local.set 3
      end
      block  ;; label = @2
        local.get 3
        i32.const 255
        i32.and
        i32.const 97
        i32.ne
        br_if 0 (;@2;)
        block  ;; label = @3
          local.get 0
          i32.const 3
          i32.const 0
          call $fcntl
          local.tee 1
          i32.const 1
          i32.and
          br_if 0 (;@3;)
          local.get 2
          local.get 1
          i32.const 1
          i32.or
          i32.store
          local.get 0
          i32.const 4
          local.get 2
          call $fcntl
          drop
        end
        local.get 4
        local.get 5
        i32.const 128
        i32.or
        local.tee 5
        i32.store
      end
      local.get 4
      i32.const -1
      i32.store offset=64
      local.get 4
      i32.const 1024
      i32.store offset=44
      local.get 4
      local.get 0
      i32.store offset=56
      local.get 4
      local.get 4
      i32.const 120
      i32.add
      i32.store offset=40
      block  ;; label = @2
        local.get 5
        i32.const 8
        i32.and
        br_if 0 (;@2;)
        local.get 0
        call $__isatty
        i32.eqz
        br_if 0 (;@2;)
        local.get 4
        i32.const 10
        i32.store offset=64
      end
      local.get 4
      i32.const 5
      i32.store offset=36
      local.get 4
      i32.const 6
      i32.store offset=32
      local.get 4
      i32.const 7
      i32.store offset=28
      local.get 4
      i32.const 8
      i32.store offset=12
      local.get 4
      call $__ofl_add
      local.set 4
    end
    local.get 2
    i32.const 32
    i32.add
    global.set $__stack_pointer
    local.get 4)
  (func $fopen (type 3) (param i32 i32) (result i32)
    (local i32)
    block  ;; label = @1
      i32.const 1083
      local.get 1
      i32.load8_s
      i32.const 4
      call $memchr
      br_if 0 (;@1;)
      i32.const 0
      i32.const 28
      i32.store offset=4396
      i32.const 0
      return
    end
    i32.const 0
    local.set 2
    block  ;; label = @1
      local.get 0
      local.get 1
      call $__fmodeflags
      call $__wasilibc_open_nomode
      local.tee 0
      i32.const 0
      i32.lt_s
      br_if 0 (;@1;)
      local.get 0
      local.get 1
      call $__fdopen
      local.tee 2
      br_if 0 (;@1;)
      local.get 0
      call $close
      drop
      i32.const 0
      local.set 2
    end
    local.get 2)
  (func $fprintf (type 1) (param i32 i32 i32) (result i32)
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
    call $vfprintf
    local.set 2
    local.get 3
    i32.const 16
    i32.add
    global.set $__stack_pointer
    local.get 2)
  (func $__stdio_exit (type 8)
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
      i32.load offset=4428
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
      i32.load offset=3728
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
      i32.load offset=3848
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
  (func $__towrite (type 0) (param i32) (result i32)
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
  (func $__fwritex (type 1) (param i32 i32 i32) (result i32)
    (local i32 i32 i32 i32)
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
        local.get 1
        local.get 3
        local.get 2
        i32.load offset=20
        local.tee 5
        i32.sub
        i32.le_u
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
        local.get 2
        i32.load offset=64
        i32.const 0
        i32.lt_s
        br_if 0 (;@2;)
        local.get 1
        i32.eqz
        br_if 0 (;@2;)
        local.get 0
        local.get 1
        i32.add
        local.set 4
        i32.const 0
        local.set 3
        block  ;; label = @3
          loop  ;; label = @4
            local.get 4
            local.get 3
            i32.add
            i32.const -1
            i32.add
            i32.load8_u
            i32.const 10
            i32.eq
            br_if 1 (;@3;)
            local.get 1
            local.get 3
            i32.const -1
            i32.add
            local.tee 3
            i32.add
            br_if 0 (;@4;)
          end
          i32.const 0
          local.set 6
          br 1 (;@2;)
        end
        local.get 2
        local.get 0
        local.get 1
        local.get 3
        i32.add
        local.tee 6
        local.get 2
        i32.load offset=32
        call_indirect (type 1)
        local.tee 4
        local.get 6
        i32.lt_u
        br_if 1 (;@1;)
        local.get 6
        local.get 0
        i32.add
        local.set 0
        i32.const 0
        local.get 3
        i32.sub
        local.set 1
        local.get 2
        i32.load offset=20
        local.set 5
      end
      block  ;; label = @2
        local.get 1
        i32.eqz
        br_if 0 (;@2;)
        local.get 5
        local.get 0
        local.get 1
        memory.copy
      end
      local.get 2
      local.get 2
      i32.load offset=20
      local.get 1
      i32.add
      i32.store offset=20
      local.get 6
      local.get 1
      i32.add
      local.set 4
    end
    local.get 4)
  (func $fwrite (type 4) (param i32 i32 i32 i32) (result i32)
    (local i32 i32 i32 i32 i32)
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
        local.set 6
        local.get 3
        call $__towrite
        br_if 1 (;@1;)
        local.get 3
        i32.load offset=16
        local.set 5
      end
      block  ;; label = @2
        local.get 4
        local.get 5
        local.get 3
        i32.load offset=20
        local.tee 7
        i32.sub
        i32.le_u
        br_if 0 (;@2;)
        local.get 3
        local.get 0
        local.get 4
        local.get 3
        i32.load offset=32
        call_indirect (type 1)
        local.set 6
        br 1 (;@1;)
      end
      i32.const 0
      local.set 8
      block  ;; label = @2
        block  ;; label = @3
          local.get 4
          br_if 0 (;@3;)
          local.get 4
          local.set 5
          br 1 (;@2;)
        end
        i32.const 0
        local.set 5
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
        local.set 6
        block  ;; label = @3
          loop  ;; label = @4
            local.get 6
            local.get 5
            i32.add
            i32.const -1
            i32.add
            i32.load8_u
            i32.const 10
            i32.eq
            br_if 1 (;@3;)
            local.get 4
            local.get 5
            i32.const -1
            i32.add
            local.tee 5
            i32.add
            br_if 0 (;@4;)
          end
          i32.const 0
          local.set 8
          local.get 4
          local.set 5
          br 1 (;@2;)
        end
        local.get 3
        local.get 0
        local.get 4
        local.get 5
        i32.add
        local.tee 8
        local.get 3
        i32.load offset=32
        call_indirect (type 1)
        local.tee 6
        local.get 8
        i32.lt_u
        br_if 1 (;@1;)
        local.get 8
        local.get 0
        i32.add
        local.set 0
        i32.const 0
        local.get 5
        i32.sub
        local.set 5
        local.get 3
        i32.load offset=20
        local.set 7
      end
      block  ;; label = @2
        local.get 5
        i32.eqz
        br_if 0 (;@2;)
        local.get 7
        local.get 0
        local.get 5
        memory.copy
      end
      local.get 3
      local.get 3
      i32.load offset=20
      local.get 5
      i32.add
      i32.store offset=20
      local.get 8
      local.get 5
      i32.add
      local.set 6
    end
    block  ;; label = @1
      local.get 6
      local.get 4
      i32.ne
      br_if 0 (;@1;)
      local.get 2
      i32.const 0
      local.get 1
      select
      return
    end
    local.get 6
    local.get 1
    i32.div_u)
  (func $__ofl_lock (type 10) (result i32)
    i32.const 4432)
  (func $__ofl_unlock (type 8))
  (func $__ofl_add (type 0) (param i32) (result i32)
    (local i32 i32)
    local.get 0
    call $__ofl_lock
    local.tee 1
    i32.load
    local.tee 2
    i32.store offset=52
    block  ;; label = @1
      local.get 2
      i32.eqz
      br_if 0 (;@1;)
      local.get 2
      local.get 0
      i32.store offset=48
    end
    local.get 1
    local.get 0
    i32.store
    call $__ofl_unlock
    local.get 0)
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
    i32.const 3616
    local.get 0
    local.get 1
    call $vfprintf
    local.set 1
    local.get 2
    i32.const 16
    i32.add
    global.set $__stack_pointer
    local.get 1)
  (func $__overflow (type 3) (param i32 i32) (result i32)
    (local i32 i32 i32)
    global.get $__stack_pointer
    i32.const 16
    i32.sub
    local.tee 2
    global.set $__stack_pointer
    local.get 2
    local.get 1
    i32.store8 offset=15
    block  ;; label = @1
      block  ;; label = @2
        local.get 0
        i32.load offset=16
        local.tee 3
        br_if 0 (;@2;)
        block  ;; label = @3
          local.get 0
          call $__towrite
          i32.eqz
          br_if 0 (;@3;)
          i32.const -1
          local.set 3
          br 2 (;@1;)
        end
        local.get 0
        i32.load offset=16
        local.set 3
      end
      block  ;; label = @2
        local.get 0
        i32.load offset=20
        local.tee 4
        local.get 3
        i32.eq
        br_if 0 (;@2;)
        local.get 0
        i32.load offset=64
        local.get 1
        i32.const 255
        i32.and
        local.tee 3
        i32.eq
        br_if 0 (;@2;)
        local.get 0
        local.get 4
        i32.const 1
        i32.add
        i32.store offset=20
        local.get 4
        local.get 1
        i32.store8
        br 1 (;@1;)
      end
      block  ;; label = @2
        local.get 0
        local.get 2
        i32.const 15
        i32.add
        i32.const 1
        local.get 0
        i32.load offset=32
        call_indirect (type 1)
        i32.const 1
        i32.eq
        br_if 0 (;@2;)
        i32.const -1
        local.set 3
        br 1 (;@1;)
      end
      local.get 2
      i32.load8_u offset=15
      local.set 3
    end
    local.get 2
    i32.const 16
    i32.add
    global.set $__stack_pointer
    local.get 3)
  (func $putchar (type 0) (param i32) (result i32)
    (local i32 i32)
    block  ;; label = @1
      local.get 0
      i32.const 255
      i32.and
      local.tee 1
      i32.const 0
      i32.load offset=3680
      i32.eq
      br_if 0 (;@1;)
      i32.const 0
      i32.load offset=3636
      local.tee 2
      i32.const 0
      i32.load offset=3632
      i32.eq
      br_if 0 (;@1;)
      i32.const 0
      local.get 2
      i32.const 1
      i32.add
      i32.store offset=3636
      local.get 2
      local.get 0
      i32.store8
      local.get 1
      return
    end
    i32.const 3616
    local.get 1
    call $__overflow)
  (func $__stdout_write (type 1) (param i32 i32 i32) (result i32)
    local.get 0
    i32.const 6
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
  (func $dummy.1 (type 3) (param i32 i32) (result i32)
    local.get 0)
  (func $__lctrans (type 3) (param i32 i32) (result i32)
    local.get 0
    local.get 1
    call $dummy.1)
  (func $strerror (type 0) (param i32) (result i32)
    (local i32)
    block  ;; label = @1
      i32.const 0
      i32.load offset=5504
      local.tee 1
      br_if 0 (;@1;)
      i32.const 5480
      local.set 1
      i32.const 0
      i32.const 5480
      i32.store offset=5504
    end
    i32.const 0
    local.get 0
    local.get 0
    i32.const 76
    i32.gt_u
    select
    i32.const 1
    i32.shl
    i32.load16_u offset=2896
    i32.const 1344
    i32.add
    local.get 1
    i32.load offset=20
    call $__lctrans)
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
        i32.const 0
        i32.load offset=5504
        local.tee 3
        br_if 0 (;@2;)
        i32.const 5480
        local.set 3
        i32.const 0
        i32.const 5480
        i32.store offset=5504
      end
      block  ;; label = @2
        block  ;; label = @3
          local.get 3
          i32.load
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
            i32.store offset=4396
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
        i32.store offset=4396
      end
      i32.const -1
      local.set 3
    end
    local.get 3)
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
      local.get 0
      i32.load
      local.tee 4
      i32.const -33
      i32.and
      i32.store
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
  (func $printf_core (type 9) (param i32 i32 i32 i32 i32) (result i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i64 i64 f64 i32 i32 i32 i32 i32 i64 i32 i32 f64)
    global.get $__stack_pointer
    i32.const 864
    i32.sub
    local.tee 5
    global.set $__stack_pointer
    local.get 5
    i32.const 52
    i32.add
    i32.const 12
    i32.add
    local.set 6
    local.get 5
    i32.const 96
    i32.add
    i32.const -4
    i32.add
    local.set 7
    local.get 5
    i32.const 16
    i32.add
    i32.const 25
    i32.add
    local.set 8
    local.get 5
    i32.const 39
    i32.add
    local.set 9
    local.get 5
    i32.const 52
    i32.add
    i32.const 11
    i32.add
    local.set 10
    local.get 5
    i32.const 64
    i32.add
    i32.const -1
    i32.add
    local.set 11
    local.get 5
    i32.const 64
    i32.add
    i32.const 8
    i32.or
    local.set 12
    local.get 5
    i32.const 64
    i32.add
    i32.const 9
    i32.or
    local.set 13
    local.get 5
    i32.const 52
    i32.add
    i32.const 10
    i32.add
    local.set 14
    local.get 5
    i32.const 40
    i32.add
    local.set 15
    i32.const 0
    local.set 16
    i32.const 0
    local.set 17
    block  ;; label = @1
      block  ;; label = @2
        loop  ;; label = @3
          i32.const 0
          local.set 18
          block  ;; label = @4
            loop  ;; label = @5
              local.get 1
              local.set 19
              local.get 18
              local.get 17
              i32.const 2147483647
              i32.xor
              i32.gt_s
              br_if 1 (;@4;)
              local.get 18
              local.get 17
              i32.add
              local.set 17
              block  ;; label = @6
                block  ;; label = @7
                  block  ;; label = @8
                    block  ;; label = @9
                      block  ;; label = @10
                        block  ;; label = @11
                          block  ;; label = @12
                            block  ;; label = @13
                              block  ;; label = @14
                                local.get 19
                                i32.load8_u
                                local.tee 18
                                i32.eqz
                                br_if 0 (;@14;)
                                local.get 19
                                local.set 1
                                loop  ;; label = @15
                                  block  ;; label = @16
                                    block  ;; label = @17
                                      block  ;; label = @18
                                        local.get 18
                                        i32.const 255
                                        i32.and
                                        local.tee 18
                                        i32.eqz
                                        br_if 0 (;@18;)
                                        local.get 18
                                        i32.const 37
                                        i32.ne
                                        br_if 2 (;@16;)
                                        local.get 1
                                        local.set 18
                                        loop  ;; label = @19
                                          local.get 1
                                          i32.const 1
                                          i32.add
                                          i32.load8_u
                                          i32.const 37
                                          i32.ne
                                          br_if 2 (;@17;)
                                          local.get 18
                                          i32.const 1
                                          i32.add
                                          local.set 18
                                          local.get 1
                                          i32.const 2
                                          i32.add
                                          local.tee 1
                                          i32.load8_u
                                          i32.const 37
                                          i32.eq
                                          br_if 0 (;@19;)
                                          br 2 (;@17;)
                                        end
                                      end
                                      local.get 1
                                      local.set 18
                                    end
                                    local.get 18
                                    local.get 19
                                    i32.sub
                                    local.tee 18
                                    local.get 17
                                    i32.const 2147483647
                                    i32.xor
                                    local.tee 20
                                    i32.gt_s
                                    br_if 12 (;@4;)
                                    block  ;; label = @17
                                      local.get 0
                                      i32.eqz
                                      br_if 0 (;@17;)
                                      local.get 0
                                      i32.load8_u
                                      i32.const 32
                                      i32.and
                                      br_if 0 (;@17;)
                                      local.get 19
                                      local.get 18
                                      local.get 0
                                      call $__fwritex
                                      drop
                                    end
                                    local.get 18
                                    br_if 11 (;@5;)
                                    local.get 1
                                    i32.const 1
                                    i32.add
                                    local.set 21
                                    i32.const -1
                                    local.set 22
                                    block  ;; label = @17
                                      local.get 1
                                      i32.load8_s offset=1
                                      local.tee 23
                                      i32.const -48
                                      i32.add
                                      local.tee 18
                                      i32.const 9
                                      i32.gt_u
                                      br_if 0 (;@17;)
                                      local.get 1
                                      i32.load8_u offset=2
                                      i32.const 36
                                      i32.ne
                                      br_if 0 (;@17;)
                                      local.get 1
                                      i32.const 3
                                      i32.add
                                      local.set 21
                                      local.get 1
                                      i32.load8_s offset=3
                                      local.set 23
                                      i32.const 1
                                      local.set 16
                                      local.get 18
                                      local.set 22
                                    end
                                    i32.const 0
                                    local.set 24
                                    block  ;; label = @17
                                      block  ;; label = @18
                                        local.get 23
                                        i32.const -32
                                        i32.add
                                        local.tee 1
                                        i32.const 31
                                        i32.le_u
                                        br_if 0 (;@18;)
                                        local.get 21
                                        local.set 1
                                        br 1 (;@17;)
                                      end
                                      block  ;; label = @18
                                        i32.const 1
                                        local.get 1
                                        i32.shl
                                        local.tee 18
                                        i32.const 75913
                                        i32.and
                                        br_if 0 (;@18;)
                                        local.get 21
                                        local.set 1
                                        br 1 (;@17;)
                                      end
                                      local.get 21
                                      i32.const 1
                                      i32.add
                                      local.set 21
                                      i32.const 0
                                      local.set 24
                                      loop  ;; label = @18
                                        local.get 18
                                        local.get 24
                                        i32.or
                                        local.set 24
                                        local.get 21
                                        local.tee 1
                                        i32.load8_s
                                        local.tee 23
                                        i32.const -32
                                        i32.add
                                        local.tee 18
                                        i32.const 32
                                        i32.ge_u
                                        br_if 1 (;@17;)
                                        local.get 1
                                        i32.const 1
                                        i32.add
                                        local.set 21
                                        i32.const 1
                                        local.get 18
                                        i32.shl
                                        local.tee 18
                                        i32.const 75913
                                        i32.and
                                        br_if 0 (;@18;)
                                      end
                                    end
                                    block  ;; label = @17
                                      local.get 23
                                      i32.const 42
                                      i32.ne
                                      br_if 0 (;@17;)
                                      block  ;; label = @18
                                        block  ;; label = @19
                                          local.get 1
                                          i32.load8_s offset=1
                                          i32.const -48
                                          i32.add
                                          local.tee 18
                                          i32.const 9
                                          i32.gt_u
                                          br_if 0 (;@19;)
                                          local.get 1
                                          i32.load8_u offset=2
                                          i32.const 36
                                          i32.ne
                                          br_if 0 (;@19;)
                                          block  ;; label = @20
                                            block  ;; label = @21
                                              local.get 0
                                              br_if 0 (;@21;)
                                              local.get 4
                                              local.get 18
                                              i32.const 2
                                              i32.shl
                                              i32.add
                                              i32.const 10
                                              i32.store
                                              i32.const 0
                                              local.set 25
                                              br 1 (;@20;)
                                            end
                                            local.get 3
                                            local.get 18
                                            i32.const 3
                                            i32.shl
                                            i32.add
                                            i32.load
                                            local.set 25
                                          end
                                          local.get 1
                                          i32.const 3
                                          i32.add
                                          local.set 1
                                          i32.const 1
                                          local.set 16
                                          br 1 (;@18;)
                                        end
                                        local.get 16
                                        br_if 6 (;@12;)
                                        local.get 1
                                        i32.const 1
                                        i32.add
                                        local.set 1
                                        block  ;; label = @19
                                          local.get 0
                                          br_if 0 (;@19;)
                                          i32.const 0
                                          local.set 16
                                          i32.const 0
                                          local.set 25
                                          br 6 (;@13;)
                                        end
                                        local.get 2
                                        local.get 2
                                        i32.load
                                        local.tee 18
                                        i32.const 4
                                        i32.add
                                        i32.store
                                        local.get 18
                                        i32.load
                                        local.set 25
                                        i32.const 0
                                        local.set 16
                                      end
                                      local.get 25
                                      i32.const -1
                                      i32.gt_s
                                      br_if 4 (;@13;)
                                      i32.const 0
                                      local.get 25
                                      i32.sub
                                      local.set 25
                                      local.get 24
                                      i32.const 8192
                                      i32.or
                                      local.set 24
                                      br 4 (;@13;)
                                    end
                                    i32.const 0
                                    local.set 25
                                    local.get 23
                                    i32.const -48
                                    i32.add
                                    local.tee 21
                                    i32.const 9
                                    i32.gt_u
                                    br_if 3 (;@13;)
                                    local.get 1
                                    local.set 18
                                    loop  ;; label = @17
                                      block  ;; label = @18
                                        local.get 25
                                        i32.const 214748364
                                        i32.gt_u
                                        br_if 0 (;@18;)
                                        i32.const -1
                                        local.get 25
                                        i32.const 10
                                        i32.mul
                                        local.tee 1
                                        local.get 21
                                        i32.add
                                        local.get 21
                                        local.get 1
                                        i32.const 2147483647
                                        i32.xor
                                        i32.gt_u
                                        local.tee 23
                                        select
                                        local.set 25
                                        local.get 18
                                        i32.load8_s offset=1
                                        local.set 21
                                        local.get 18
                                        i32.const 1
                                        i32.add
                                        local.tee 1
                                        local.set 18
                                        local.get 21
                                        i32.const -48
                                        i32.add
                                        local.tee 21
                                        i32.const 10
                                        i32.lt_u
                                        br_if 1 (;@17;)
                                        local.get 23
                                        br_if 14 (;@4;)
                                        br 5 (;@13;)
                                      end
                                      local.get 18
                                      i32.load8_s offset=1
                                      local.set 1
                                      i32.const -1
                                      local.set 25
                                      local.get 18
                                      i32.const 1
                                      i32.add
                                      local.set 18
                                      local.get 1
                                      i32.const -48
                                      i32.add
                                      local.tee 21
                                      i32.const 10
                                      i32.lt_u
                                      br_if 0 (;@17;)
                                      br 13 (;@4;)
                                    end
                                  end
                                  local.get 1
                                  i32.const 1
                                  i32.add
                                  local.tee 1
                                  i32.load8_u
                                  local.set 18
                                  br 0 (;@15;)
                                end
                              end
                              local.get 0
                              br_if 12 (;@1;)
                              block  ;; label = @14
                                local.get 16
                                br_if 0 (;@14;)
                                i32.const 0
                                local.set 17
                                br 13 (;@1;)
                              end
                              block  ;; label = @14
                                block  ;; label = @15
                                  block  ;; label = @16
                                    local.get 4
                                    i32.load offset=4
                                    local.tee 1
                                    br_if 0 (;@16;)
                                    i32.const 1
                                    local.set 1
                                    br 1 (;@15;)
                                  end
                                  local.get 3
                                  i32.const 8
                                  i32.add
                                  local.get 1
                                  local.get 2
                                  call $pop_arg
                                  block  ;; label = @16
                                    local.get 4
                                    i32.load offset=8
                                    local.tee 1
                                    br_if 0 (;@16;)
                                    i32.const 2
                                    local.set 1
                                    br 1 (;@15;)
                                  end
                                  local.get 3
                                  i32.const 16
                                  i32.add
                                  local.get 1
                                  local.get 2
                                  call $pop_arg
                                  block  ;; label = @16
                                    local.get 4
                                    i32.load offset=12
                                    local.tee 1
                                    br_if 0 (;@16;)
                                    i32.const 3
                                    local.set 1
                                    br 1 (;@15;)
                                  end
                                  local.get 3
                                  i32.const 24
                                  i32.add
                                  local.get 1
                                  local.get 2
                                  call $pop_arg
                                  block  ;; label = @16
                                    local.get 4
                                    i32.load offset=16
                                    local.tee 1
                                    br_if 0 (;@16;)
                                    i32.const 4
                                    local.set 1
                                    br 1 (;@15;)
                                  end
                                  local.get 3
                                  i32.const 32
                                  i32.add
                                  local.get 1
                                  local.get 2
                                  call $pop_arg
                                  block  ;; label = @16
                                    local.get 4
                                    i32.load offset=20
                                    local.tee 1
                                    br_if 0 (;@16;)
                                    i32.const 5
                                    local.set 1
                                    br 1 (;@15;)
                                  end
                                  local.get 3
                                  i32.const 40
                                  i32.add
                                  local.get 1
                                  local.get 2
                                  call $pop_arg
                                  block  ;; label = @16
                                    local.get 4
                                    i32.load offset=24
                                    local.tee 1
                                    br_if 0 (;@16;)
                                    i32.const 6
                                    local.set 1
                                    br 1 (;@15;)
                                  end
                                  local.get 3
                                  i32.const 48
                                  i32.add
                                  local.get 1
                                  local.get 2
                                  call $pop_arg
                                  block  ;; label = @16
                                    local.get 4
                                    i32.load offset=28
                                    local.tee 1
                                    br_if 0 (;@16;)
                                    i32.const 7
                                    local.set 1
                                    br 1 (;@15;)
                                  end
                                  local.get 3
                                  i32.const 56
                                  i32.add
                                  local.get 1
                                  local.get 2
                                  call $pop_arg
                                  block  ;; label = @16
                                    local.get 4
                                    i32.load offset=32
                                    local.tee 1
                                    br_if 0 (;@16;)
                                    i32.const 8
                                    local.set 1
                                    br 1 (;@15;)
                                  end
                                  local.get 3
                                  i32.const 64
                                  i32.add
                                  local.get 1
                                  local.get 2
                                  call $pop_arg
                                  local.get 4
                                  i32.load offset=36
                                  local.tee 1
                                  br_if 1 (;@14;)
                                  i32.const 9
                                  local.set 1
                                end
                                local.get 1
                                i32.const 2
                                i32.shl
                                local.set 1
                                loop  ;; label = @15
                                  local.get 4
                                  local.get 1
                                  i32.add
                                  i32.load
                                  br_if 3 (;@12;)
                                  local.get 1
                                  i32.const 4
                                  i32.add
                                  local.tee 1
                                  i32.const 40
                                  i32.ne
                                  br_if 0 (;@15;)
                                end
                                i32.const 1
                                local.set 17
                                br 13 (;@1;)
                              end
                              local.get 3
                              i32.const 72
                              i32.add
                              local.get 1
                              local.get 2
                              call $pop_arg
                              i32.const 1
                              local.set 17
                              br 12 (;@1;)
                            end
                            i32.const 0
                            local.set 18
                            block  ;; label = @13
                              block  ;; label = @14
                                local.get 1
                                i32.load8_u
                                i32.const 46
                                i32.eq
                                br_if 0 (;@14;)
                                i32.const -1
                                local.set 23
                                i32.const 0
                                local.set 26
                                br 1 (;@13;)
                              end
                              block  ;; label = @14
                                local.get 1
                                i32.load8_s offset=1
                                local.tee 21
                                i32.const 42
                                i32.ne
                                br_if 0 (;@14;)
                                block  ;; label = @15
                                  local.get 1
                                  i32.load8_s offset=2
                                  i32.const -48
                                  i32.add
                                  local.tee 21
                                  i32.const 9
                                  i32.gt_u
                                  br_if 0 (;@15;)
                                  local.get 1
                                  i32.load8_u offset=3
                                  i32.const 36
                                  i32.ne
                                  br_if 0 (;@15;)
                                  block  ;; label = @16
                                    local.get 0
                                    br_if 0 (;@16;)
                                    local.get 4
                                    local.get 21
                                    i32.const 2
                                    i32.shl
                                    i32.add
                                    i32.const 10
                                    i32.store
                                    i32.const 0
                                    local.set 23
                                    local.get 1
                                    i32.const 4
                                    i32.add
                                    local.set 1
                                    i32.const 0
                                    i32.const -1
                                    i32.gt_s
                                    local.set 26
                                    br 3 (;@13;)
                                  end
                                  local.get 1
                                  i32.const 4
                                  i32.add
                                  local.set 1
                                  local.get 3
                                  local.get 21
                                  i32.const 3
                                  i32.shl
                                  i32.add
                                  i32.load
                                  local.tee 23
                                  i32.const -1
                                  i32.gt_s
                                  local.set 26
                                  br 2 (;@13;)
                                end
                                local.get 16
                                br_if 2 (;@12;)
                                local.get 1
                                i32.const 2
                                i32.add
                                local.set 1
                                block  ;; label = @15
                                  local.get 0
                                  br_if 0 (;@15;)
                                  i32.const 0
                                  local.set 23
                                  i32.const 0
                                  i32.const -1
                                  i32.gt_s
                                  local.set 26
                                  br 2 (;@13;)
                                end
                                local.get 2
                                local.get 2
                                i32.load
                                local.tee 21
                                i32.const 4
                                i32.add
                                i32.store
                                local.get 21
                                i32.load
                                local.tee 23
                                i32.const -1
                                i32.gt_s
                                local.set 26
                                br 1 (;@13;)
                              end
                              local.get 1
                              i32.const 1
                              i32.add
                              local.set 1
                              block  ;; label = @14
                                local.get 21
                                i32.const -48
                                i32.add
                                local.tee 27
                                i32.const 9
                                i32.le_u
                                br_if 0 (;@14;)
                                i32.const 1
                                local.set 26
                                i32.const 0
                                local.set 23
                                br 1 (;@13;)
                              end
                              i32.const 0
                              local.set 21
                              loop  ;; label = @14
                                i32.const -1
                                local.set 23
                                block  ;; label = @15
                                  local.get 21
                                  i32.const 214748364
                                  i32.gt_u
                                  br_if 0 (;@15;)
                                  i32.const -1
                                  local.get 21
                                  i32.const 10
                                  i32.mul
                                  local.tee 21
                                  local.get 27
                                  i32.add
                                  local.get 27
                                  local.get 21
                                  i32.const 2147483647
                                  i32.xor
                                  i32.gt_u
                                  select
                                  local.set 23
                                end
                                i32.const 1
                                local.set 26
                                local.get 23
                                local.set 21
                                local.get 1
                                i32.const 1
                                i32.add
                                local.tee 1
                                i32.load8_s
                                i32.const -48
                                i32.add
                                local.tee 27
                                i32.const 10
                                i32.lt_u
                                br_if 0 (;@14;)
                              end
                            end
                            loop  ;; label = @13
                              local.get 18
                              local.set 21
                              local.get 1
                              i32.load8_s
                              local.tee 18
                              i32.const -123
                              i32.add
                              i32.const -58
                              i32.lt_u
                              br_if 1 (;@12;)
                              local.get 1
                              i32.const 1
                              i32.add
                              local.set 1
                              local.get 18
                              local.get 21
                              i32.const 58
                              i32.mul
                              i32.add
                              i32.const 2991
                              i32.add
                              i32.load8_u
                              local.tee 18
                              i32.const -1
                              i32.add
                              i32.const 255
                              i32.and
                              i32.const 8
                              i32.lt_u
                              br_if 0 (;@13;)
                            end
                            block  ;; label = @13
                              block  ;; label = @14
                                local.get 18
                                i32.const 27
                                i32.eq
                                br_if 0 (;@14;)
                                local.get 18
                                i32.eqz
                                br_if 2 (;@12;)
                                block  ;; label = @15
                                  local.get 22
                                  i32.const 0
                                  i32.lt_s
                                  br_if 0 (;@15;)
                                  block  ;; label = @16
                                    local.get 0
                                    br_if 0 (;@16;)
                                    local.get 4
                                    local.get 22
                                    i32.const 2
                                    i32.shl
                                    i32.add
                                    local.get 18
                                    i32.store
                                    br 13 (;@3;)
                                  end
                                  local.get 5
                                  local.get 3
                                  local.get 22
                                  i32.const 3
                                  i32.shl
                                  i32.add
                                  i64.load
                                  i64.store offset=40
                                  br 2 (;@13;)
                                end
                                block  ;; label = @15
                                  local.get 0
                                  br_if 0 (;@15;)
                                  i32.const 0
                                  local.set 17
                                  br 14 (;@1;)
                                end
                                local.get 5
                                i32.const 40
                                i32.add
                                local.get 18
                                local.get 2
                                call $pop_arg
                                br 1 (;@13;)
                              end
                              local.get 22
                              i32.const -1
                              i32.gt_s
                              br_if 1 (;@12;)
                              i32.const 0
                              local.set 18
                              local.get 0
                              i32.eqz
                              br_if 8 (;@5;)
                            end
                            local.get 0
                            i32.load
                            local.tee 22
                            i32.const 32
                            i32.and
                            br_if 10 (;@2;)
                            local.get 24
                            i32.const -65537
                            i32.and
                            local.tee 27
                            local.get 24
                            local.get 24
                            i32.const 8192
                            i32.and
                            select
                            local.set 28
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
                                                        block  ;; label = @27
                                                          block  ;; label = @28
                                                            block  ;; label = @29
                                                              block  ;; label = @30
                                                                local.get 1
                                                                i32.const -1
                                                                i32.add
                                                                i32.load8_u
                                                                local.tee 24
                                                                i32.extend8_s
                                                                local.tee 18
                                                                i32.const -45
                                                                i32.and
                                                                local.get 18
                                                                local.get 24
                                                                i32.const 15
                                                                i32.and
                                                                i32.const 3
                                                                i32.eq
                                                                select
                                                                local.get 18
                                                                local.get 21
                                                                select
                                                                local.tee 29
                                                                i32.const -65
                                                                i32.add
                                                                br_table 17 (;@13;) 19 (;@11;) 12 (;@18;) 19 (;@11;) 17 (;@13;) 17 (;@13;) 17 (;@13;) 19 (;@11;) 19 (;@11;) 19 (;@11;) 19 (;@11;) 19 (;@11;) 19 (;@11;) 19 (;@11;) 19 (;@11;) 19 (;@11;) 19 (;@11;) 19 (;@11;) 13 (;@17;) 19 (;@11;) 19 (;@11;) 19 (;@11;) 19 (;@11;) 3 (;@27;) 19 (;@11;) 19 (;@11;) 19 (;@11;) 19 (;@11;) 19 (;@11;) 19 (;@11;) 19 (;@11;) 19 (;@11;) 17 (;@13;) 19 (;@11;) 8 (;@22;) 5 (;@25;) 17 (;@13;) 17 (;@13;) 17 (;@13;) 19 (;@11;) 5 (;@25;) 19 (;@11;) 19 (;@11;) 19 (;@11;) 9 (;@21;) 1 (;@29;) 4 (;@26;) 2 (;@28;) 19 (;@11;) 19 (;@11;) 10 (;@20;) 19 (;@11;) 0 (;@30;) 19 (;@11;) 19 (;@11;) 3 (;@27;) 19 (;@11;)
                                                              end
                                                              i32.const 0
                                                              local.set 22
                                                              i32.const 1024
                                                              local.set 30
                                                              local.get 5
                                                              i64.load offset=40
                                                              local.set 31
                                                              br 5 (;@24;)
                                                            end
                                                            i32.const 0
                                                            local.set 18
                                                            block  ;; label = @29
                                                              block  ;; label = @30
                                                                block  ;; label = @31
                                                                  block  ;; label = @32
                                                                    block  ;; label = @33
                                                                      block  ;; label = @34
                                                                        block  ;; label = @35
                                                                          local.get 21
                                                                          br_table 0 (;@35;) 1 (;@34;) 2 (;@33;) 3 (;@32;) 4 (;@31;) 30 (;@5;) 5 (;@30;) 6 (;@29;) 30 (;@5;)
                                                                        end
                                                                        local.get 5
                                                                        i32.load offset=40
                                                                        local.get 17
                                                                        i32.store
                                                                        br 29 (;@5;)
                                                                      end
                                                                      local.get 5
                                                                      i32.load offset=40
                                                                      local.get 17
                                                                      i32.store
                                                                      br 28 (;@5;)
                                                                    end
                                                                    local.get 5
                                                                    i32.load offset=40
                                                                    local.get 17
                                                                    i64.extend_i32_s
                                                                    i64.store
                                                                    br 27 (;@5;)
                                                                  end
                                                                  local.get 5
                                                                  i32.load offset=40
                                                                  local.get 17
                                                                  i32.store16
                                                                  br 26 (;@5;)
                                                                end
                                                                local.get 5
                                                                i32.load offset=40
                                                                local.get 17
                                                                i32.store8
                                                                br 25 (;@5;)
                                                              end
                                                              local.get 5
                                                              i32.load offset=40
                                                              local.get 17
                                                              i32.store
                                                              br 24 (;@5;)
                                                            end
                                                            local.get 5
                                                            i32.load offset=40
                                                            local.get 17
                                                            i64.extend_i32_s
                                                            i64.store
                                                            br 23 (;@5;)
                                                          end
                                                          local.get 23
                                                          i32.const 8
                                                          local.get 23
                                                          i32.const 8
                                                          i32.gt_u
                                                          select
                                                          local.set 23
                                                          local.get 28
                                                          i32.const 8
                                                          i32.or
                                                          local.set 28
                                                          i32.const 120
                                                          local.set 29
                                                        end
                                                        i32.const 0
                                                        local.set 22
                                                        i32.const 1024
                                                        local.set 30
                                                        block  ;; label = @27
                                                          local.get 5
                                                          i64.load offset=40
                                                          local.tee 31
                                                          i64.eqz
                                                          i32.eqz
                                                          br_if 0 (;@27;)
                                                          local.get 15
                                                          local.set 19
                                                          br 4 (;@23;)
                                                        end
                                                        local.get 29
                                                        i32.const 32
                                                        i32.and
                                                        local.set 21
                                                        local.get 15
                                                        local.set 19
                                                        loop  ;; label = @27
                                                          local.get 19
                                                          i32.const -1
                                                          i32.add
                                                          local.tee 19
                                                          local.get 31
                                                          i32.wrap_i64
                                                          i32.const 15
                                                          i32.and
                                                          i32.load8_u offset=3520
                                                          local.get 21
                                                          i32.or
                                                          i32.store8
                                                          local.get 31
                                                          i64.const 15
                                                          i64.gt_u
                                                          local.set 18
                                                          local.get 31
                                                          i64.const 4
                                                          i64.shr_u
                                                          local.set 31
                                                          local.get 18
                                                          br_if 0 (;@27;)
                                                        end
                                                        local.get 28
                                                        i32.const 8
                                                        i32.and
                                                        i32.eqz
                                                        br_if 3 (;@23;)
                                                        local.get 29
                                                        i32.const 4
                                                        i32.shr_u
                                                        i32.const 1024
                                                        i32.add
                                                        local.set 30
                                                        i32.const 2
                                                        local.set 22
                                                        br 3 (;@23;)
                                                      end
                                                      local.get 15
                                                      local.set 19
                                                      block  ;; label = @26
                                                        local.get 5
                                                        i64.load offset=40
                                                        local.tee 31
                                                        i64.eqz
                                                        br_if 0 (;@26;)
                                                        local.get 15
                                                        local.set 19
                                                        loop  ;; label = @27
                                                          local.get 19
                                                          i32.const -1
                                                          i32.add
                                                          local.tee 19
                                                          local.get 31
                                                          i32.wrap_i64
                                                          i32.const 7
                                                          i32.and
                                                          i32.const 48
                                                          i32.or
                                                          i32.store8
                                                          local.get 31
                                                          i64.const 7
                                                          i64.gt_u
                                                          local.set 18
                                                          local.get 31
                                                          i64.const 3
                                                          i64.shr_u
                                                          local.set 31
                                                          local.get 18
                                                          br_if 0 (;@27;)
                                                        end
                                                      end
                                                      i32.const 0
                                                      local.set 22
                                                      i32.const 1024
                                                      local.set 30
                                                      local.get 28
                                                      i32.const 8
                                                      i32.and
                                                      i32.eqz
                                                      br_if 2 (;@23;)
                                                      local.get 23
                                                      local.get 8
                                                      local.get 19
                                                      i32.sub
                                                      local.tee 18
                                                      local.get 23
                                                      local.get 18
                                                      i32.gt_s
                                                      select
                                                      local.set 23
                                                      br 2 (;@23;)
                                                    end
                                                    block  ;; label = @25
                                                      local.get 5
                                                      i64.load offset=40
                                                      local.tee 31
                                                      i64.const -1
                                                      i64.gt_s
                                                      br_if 0 (;@25;)
                                                      local.get 5
                                                      i64.const 0
                                                      local.get 31
                                                      i64.sub
                                                      local.tee 31
                                                      i64.store offset=40
                                                      i32.const 1
                                                      local.set 22
                                                      i32.const 1024
                                                      local.set 30
                                                      br 1 (;@24;)
                                                    end
                                                    block  ;; label = @25
                                                      local.get 28
                                                      i32.const 2048
                                                      i32.and
                                                      i32.eqz
                                                      br_if 0 (;@25;)
                                                      i32.const 1
                                                      local.set 22
                                                      i32.const 1025
                                                      local.set 30
                                                      br 1 (;@24;)
                                                    end
                                                    i32.const 1026
                                                    i32.const 1024
                                                    local.get 28
                                                    i32.const 1
                                                    i32.and
                                                    local.tee 22
                                                    select
                                                    local.set 30
                                                  end
                                                  block  ;; label = @24
                                                    block  ;; label = @25
                                                      local.get 31
                                                      i64.const 4294967296
                                                      i64.ge_u
                                                      br_if 0 (;@25;)
                                                      local.get 31
                                                      local.set 32
                                                      local.get 15
                                                      local.set 19
                                                      br 1 (;@24;)
                                                    end
                                                    local.get 15
                                                    local.set 19
                                                    loop  ;; label = @25
                                                      local.get 19
                                                      i32.const -1
                                                      i32.add
                                                      local.tee 19
                                                      local.get 31
                                                      local.get 31
                                                      i64.const 10
                                                      i64.div_u
                                                      local.tee 32
                                                      i64.const 10
                                                      i64.mul
                                                      i64.sub
                                                      i32.wrap_i64
                                                      i32.const 48
                                                      i32.or
                                                      i32.store8
                                                      local.get 31
                                                      i64.const 42949672959
                                                      i64.gt_u
                                                      local.set 18
                                                      local.get 32
                                                      local.set 31
                                                      local.get 18
                                                      br_if 0 (;@25;)
                                                    end
                                                  end
                                                  local.get 32
                                                  i64.eqz
                                                  br_if 0 (;@23;)
                                                  local.get 32
                                                  i32.wrap_i64
                                                  local.set 18
                                                  loop  ;; label = @24
                                                    local.get 19
                                                    i32.const -1
                                                    i32.add
                                                    local.tee 19
                                                    local.get 18
                                                    local.get 18
                                                    i32.const 10
                                                    i32.div_u
                                                    local.tee 21
                                                    i32.const 10
                                                    i32.mul
                                                    i32.sub
                                                    i32.const 48
                                                    i32.or
                                                    i32.store8
                                                    local.get 18
                                                    i32.const 9
                                                    i32.gt_u
                                                    local.set 24
                                                    local.get 21
                                                    local.set 18
                                                    local.get 24
                                                    br_if 0 (;@24;)
                                                  end
                                                end
                                                local.get 26
                                                local.get 23
                                                i32.const 0
                                                i32.lt_s
                                                i32.and
                                                br_if 18 (;@4;)
                                                local.get 28
                                                i32.const -65537
                                                i32.and
                                                local.get 28
                                                local.get 26
                                                select
                                                local.set 27
                                                block  ;; label = @23
                                                  local.get 5
                                                  i64.load offset=40
                                                  local.tee 31
                                                  i64.const 0
                                                  i64.ne
                                                  br_if 0 (;@23;)
                                                  i32.const 0
                                                  local.set 24
                                                  local.get 23
                                                  br_if 0 (;@23;)
                                                  local.get 15
                                                  local.set 19
                                                  local.get 15
                                                  local.set 18
                                                  br 17 (;@6;)
                                                end
                                                local.get 23
                                                local.get 15
                                                local.get 19
                                                i32.sub
                                                local.get 31
                                                i64.eqz
                                                i32.add
                                                local.tee 18
                                                local.get 23
                                                local.get 18
                                                i32.gt_s
                                                select
                                                local.set 24
                                                local.get 15
                                                local.set 18
                                                br 16 (;@6;)
                                              end
                                              local.get 5
                                              i32.load8_u offset=40
                                              local.set 18
                                              br 14 (;@7;)
                                            end
                                            i32.const 0
                                            i32.load offset=4396
                                            call $strerror
                                            local.set 19
                                            br 1 (;@19;)
                                          end
                                          local.get 5
                                          i32.load offset=40
                                          local.tee 18
                                          i32.const 1097
                                          local.get 18
                                          select
                                          local.set 19
                                        end
                                        local.get 19
                                        local.get 19
                                        local.get 23
                                        i32.const 2147483647
                                        local.get 23
                                        i32.const 2147483647
                                        i32.lt_u
                                        select
                                        call $strnlen
                                        local.tee 24
                                        i32.add
                                        local.set 18
                                        i32.const 0
                                        local.set 22
                                        i32.const 1024
                                        local.set 30
                                        local.get 23
                                        i32.const -1
                                        i32.gt_s
                                        br_if 12 (;@6;)
                                        local.get 18
                                        i32.load8_u
                                        i32.eqz
                                        br_if 12 (;@6;)
                                        br 14 (;@4;)
                                      end
                                      local.get 5
                                      i64.load offset=40
                                      local.tee 31
                                      i64.eqz
                                      i32.eqz
                                      br_if 1 (;@16;)
                                      i32.const 0
                                      local.set 18
                                      br 10 (;@7;)
                                    end
                                    block  ;; label = @17
                                      local.get 23
                                      i32.eqz
                                      br_if 0 (;@17;)
                                      local.get 5
                                      i32.load offset=40
                                      local.set 21
                                      br 2 (;@15;)
                                    end
                                    i32.const 0
                                    local.set 18
                                    local.get 0
                                    i32.const 32
                                    local.get 25
                                    i32.const 0
                                    local.get 28
                                    call $pad
                                    br 2 (;@14;)
                                  end
                                  local.get 5
                                  i32.const 0
                                  i32.store offset=12
                                  local.get 5
                                  local.get 31
                                  i64.store32 offset=8
                                  local.get 5
                                  local.get 5
                                  i32.const 8
                                  i32.add
                                  i32.store offset=40
                                  local.get 5
                                  i32.const 8
                                  i32.add
                                  local.set 21
                                  i32.const -1
                                  local.set 23
                                end
                                i32.const 0
                                local.set 18
                                local.get 21
                                local.set 19
                                block  ;; label = @15
                                  loop  ;; label = @16
                                    local.get 19
                                    i32.load
                                    local.tee 20
                                    i32.eqz
                                    br_if 1 (;@15;)
                                    local.get 5
                                    i32.const 4
                                    i32.add
                                    local.get 20
                                    call $wctomb
                                    local.tee 20
                                    i32.const 0
                                    i32.lt_s
                                    br_if 14 (;@2;)
                                    local.get 20
                                    local.get 23
                                    local.get 18
                                    i32.sub
                                    i32.gt_u
                                    br_if 1 (;@15;)
                                    local.get 19
                                    i32.const 4
                                    i32.add
                                    local.set 19
                                    local.get 20
                                    local.get 18
                                    i32.add
                                    local.tee 18
                                    local.get 23
                                    i32.lt_u
                                    br_if 0 (;@16;)
                                  end
                                end
                                local.get 18
                                i32.const 0
                                i32.lt_s
                                br_if 10 (;@4;)
                                local.get 0
                                i32.const 32
                                local.get 25
                                local.get 18
                                local.get 28
                                call $pad
                                block  ;; label = @15
                                  local.get 18
                                  br_if 0 (;@15;)
                                  i32.const 0
                                  local.set 18
                                  br 1 (;@14;)
                                end
                                i32.const 0
                                local.set 19
                                loop  ;; label = @15
                                  local.get 21
                                  i32.load
                                  local.tee 20
                                  i32.eqz
                                  br_if 1 (;@14;)
                                  local.get 5
                                  i32.const 4
                                  i32.add
                                  local.get 20
                                  call $wctomb
                                  local.tee 20
                                  local.get 19
                                  i32.add
                                  local.tee 19
                                  local.get 18
                                  i32.gt_u
                                  br_if 1 (;@14;)
                                  block  ;; label = @16
                                    local.get 0
                                    i32.load8_u
                                    i32.const 32
                                    i32.and
                                    br_if 0 (;@16;)
                                    local.get 5
                                    i32.const 4
                                    i32.add
                                    local.get 20
                                    local.get 0
                                    call $__fwritex
                                    drop
                                  end
                                  local.get 21
                                  i32.const 4
                                  i32.add
                                  local.set 21
                                  local.get 19
                                  local.get 18
                                  i32.lt_u
                                  br_if 0 (;@15;)
                                end
                              end
                              local.get 0
                              i32.const 32
                              local.get 25
                              local.get 18
                              local.get 28
                              i32.const 8192
                              i32.xor
                              call $pad
                              local.get 25
                              local.get 18
                              local.get 25
                              local.get 18
                              i32.gt_s
                              select
                              local.set 18
                              br 8 (;@5;)
                            end
                            local.get 26
                            local.get 23
                            i32.const 0
                            i32.lt_s
                            local.tee 18
                            i32.and
                            br_if 8 (;@4;)
                            local.get 5
                            f64.load offset=40
                            local.set 33
                            local.get 5
                            i32.const 0
                            i32.store offset=92
                            block  ;; label = @13
                              block  ;; label = @14
                                local.get 33
                                i64.reinterpret_f64
                                i64.const -1
                                i64.gt_s
                                br_if 0 (;@14;)
                                local.get 33
                                f64.neg
                                local.set 33
                                i32.const 1
                                local.set 34
                                i32.const 0
                                local.set 35
                                i32.const 1034
                                local.set 36
                                br 1 (;@13;)
                              end
                              block  ;; label = @14
                                local.get 28
                                i32.const 2048
                                i32.and
                                i32.eqz
                                br_if 0 (;@14;)
                                i32.const 1
                                local.set 34
                                i32.const 0
                                local.set 35
                                i32.const 1037
                                local.set 36
                                br 1 (;@13;)
                              end
                              i32.const 1040
                              i32.const 1035
                              local.get 28
                              i32.const 1
                              i32.and
                              local.tee 34
                              select
                              local.set 36
                              local.get 34
                              i32.eqz
                              local.set 35
                            end
                            block  ;; label = @13
                              local.get 33
                              f64.const inf (;=inf;)
                              f64.lt
                              br_if 0 (;@13;)
                              local.get 34
                              i32.const 3
                              i32.add
                              local.set 19
                              block  ;; label = @14
                                local.get 28
                                i32.const 8192
                                i32.and
                                br_if 0 (;@14;)
                                local.get 25
                                local.get 19
                                i32.le_u
                                br_if 0 (;@14;)
                                block  ;; label = @15
                                  local.get 25
                                  local.get 19
                                  i32.sub
                                  local.tee 18
                                  i32.const 256
                                  local.get 18
                                  i32.const 256
                                  i32.lt_u
                                  local.tee 20
                                  select
                                  local.tee 21
                                  i32.eqz
                                  br_if 0 (;@15;)
                                  local.get 5
                                  i32.const 608
                                  i32.add
                                  i32.const 32
                                  local.get 21
                                  memory.fill
                                end
                                block  ;; label = @15
                                  local.get 20
                                  br_if 0 (;@15;)
                                  loop  ;; label = @16
                                    block  ;; label = @17
                                      local.get 0
                                      i32.load8_u
                                      i32.const 32
                                      i32.and
                                      br_if 0 (;@17;)
                                      local.get 5
                                      i32.const 608
                                      i32.add
                                      i32.const 256
                                      local.get 0
                                      call $__fwritex
                                      drop
                                    end
                                    local.get 18
                                    i32.const -256
                                    i32.add
                                    local.tee 18
                                    i32.const 255
                                    i32.gt_u
                                    br_if 0 (;@16;)
                                  end
                                  local.get 0
                                  i32.load
                                  local.set 22
                                end
                                local.get 22
                                i32.const 32
                                i32.and
                                br_if 0 (;@14;)
                                local.get 5
                                i32.const 608
                                i32.add
                                local.get 18
                                local.get 0
                                call $__fwritex
                                drop
                                local.get 0
                                i32.load
                                local.set 22
                              end
                              block  ;; label = @14
                                local.get 22
                                i32.const 32
                                i32.and
                                br_if 0 (;@14;)
                                local.get 36
                                local.get 34
                                local.get 0
                                call $__fwritex
                                drop
                                local.get 0
                                i32.load
                                local.set 22
                              end
                              block  ;; label = @14
                                local.get 22
                                i32.const 32
                                i32.and
                                br_if 0 (;@14;)
                                i32.const 1065
                                i32.const 1087
                                local.get 29
                                i32.const 32
                                i32.and
                                local.tee 18
                                select
                                i32.const 1079
                                i32.const 1091
                                local.get 18
                                select
                                local.get 33
                                local.get 33
                                f64.ne
                                select
                                i32.const 3
                                local.get 0
                                call $__fwritex
                                drop
                              end
                              block  ;; label = @14
                                local.get 28
                                i32.const 73728
                                i32.and
                                i32.const 8192
                                i32.ne
                                br_if 0 (;@14;)
                                local.get 25
                                local.get 19
                                i32.le_u
                                br_if 0 (;@14;)
                                block  ;; label = @15
                                  local.get 25
                                  local.get 19
                                  i32.sub
                                  local.tee 18
                                  i32.const 256
                                  local.get 18
                                  i32.const 256
                                  i32.lt_u
                                  local.tee 20
                                  select
                                  local.tee 21
                                  i32.eqz
                                  br_if 0 (;@15;)
                                  local.get 5
                                  i32.const 608
                                  i32.add
                                  i32.const 32
                                  local.get 21
                                  memory.fill
                                end
                                block  ;; label = @15
                                  local.get 20
                                  br_if 0 (;@15;)
                                  loop  ;; label = @16
                                    block  ;; label = @17
                                      local.get 0
                                      i32.load8_u
                                      i32.const 32
                                      i32.and
                                      br_if 0 (;@17;)
                                      local.get 5
                                      i32.const 608
                                      i32.add
                                      i32.const 256
                                      local.get 0
                                      call $__fwritex
                                      drop
                                    end
                                    local.get 18
                                    i32.const -256
                                    i32.add
                                    local.tee 18
                                    i32.const 255
                                    i32.gt_u
                                    br_if 0 (;@16;)
                                  end
                                end
                                local.get 0
                                i32.load8_u
                                i32.const 32
                                i32.and
                                br_if 0 (;@14;)
                                local.get 5
                                i32.const 608
                                i32.add
                                local.get 18
                                local.get 0
                                call $__fwritex
                                drop
                              end
                              local.get 25
                              local.get 19
                              local.get 25
                              local.get 19
                              i32.gt_u
                              select
                              local.set 18
                              br 8 (;@5;)
                            end
                            block  ;; label = @13
                              block  ;; label = @14
                                block  ;; label = @15
                                  local.get 33
                                  local.get 5
                                  i32.const 92
                                  i32.add
                                  call $frexp
                                  local.tee 33
                                  local.get 33
                                  f64.add
                                  local.tee 33
                                  f64.const 0x0p+0 (;=0;)
                                  f64.eq
                                  br_if 0 (;@15;)
                                  local.get 5
                                  local.get 5
                                  i32.load offset=92
                                  local.tee 19
                                  i32.const -1
                                  i32.add
                                  i32.store offset=92
                                  local.get 29
                                  i32.const 32
                                  i32.or
                                  local.tee 37
                                  i32.const 97
                                  i32.ne
                                  br_if 1 (;@14;)
                                  br 7 (;@8;)
                                end
                                local.get 29
                                i32.const 32
                                i32.or
                                local.tee 37
                                i32.const 97
                                i32.eq
                                br_if 6 (;@8;)
                                i32.const 6
                                local.get 23
                                local.get 18
                                select
                                local.set 22
                                local.get 5
                                i32.load offset=92
                                local.set 21
                                br 1 (;@13;)
                              end
                              local.get 5
                              local.get 19
                              i32.const -29
                              i32.add
                              local.tee 21
                              i32.store offset=92
                              i32.const 6
                              local.get 23
                              local.get 18
                              select
                              local.set 22
                              local.get 33
                              f64.const 0x1p+28 (;=2.68435e+08;)
                              f64.mul
                              local.set 33
                            end
                            local.get 5
                            i32.const 96
                            i32.add
                            i32.const 0
                            i32.const 288
                            local.get 21
                            i32.const 0
                            i32.lt_s
                            local.tee 38
                            select
                            i32.add
                            local.tee 30
                            local.set 19
                            loop  ;; label = @13
                              local.get 19
                              local.get 33
                              i32.trunc_sat_f64_u
                              local.tee 18
                              i32.store
                              local.get 19
                              i32.const 4
                              i32.add
                              local.set 19
                              local.get 33
                              local.get 18
                              f64.convert_i32_u
                              f64.sub
                              f64.const 0x1.dcd65p+29 (;=1e+09;)
                              f64.mul
                              local.tee 33
                              f64.const 0x0p+0 (;=0;)
                              f64.ne
                              br_if 0 (;@13;)
                            end
                            block  ;; label = @13
                              block  ;; label = @14
                                local.get 21
                                i32.const 1
                                i32.ge_s
                                br_if 0 (;@14;)
                                local.get 19
                                local.set 18
                                local.get 30
                                local.set 20
                                br 1 (;@13;)
                              end
                              local.get 30
                              local.set 20
                              loop  ;; label = @14
                                local.get 21
                                i32.const 29
                                local.get 21
                                i32.const 29
                                i32.lt_u
                                select
                                local.set 21
                                block  ;; label = @15
                                  local.get 19
                                  i32.const -4
                                  i32.add
                                  local.tee 18
                                  local.get 20
                                  i32.lt_u
                                  br_if 0 (;@15;)
                                  local.get 21
                                  i64.extend_i32_u
                                  local.set 39
                                  i64.const 0
                                  local.set 31
                                  loop  ;; label = @16
                                    local.get 18
                                    local.get 18
                                    i64.load32_u
                                    local.get 39
                                    i64.shl
                                    local.get 31
                                    i64.add
                                    local.tee 32
                                    local.get 32
                                    i64.const 1000000000
                                    i64.div_u
                                    local.tee 31
                                    i64.const 1000000000
                                    i64.mul
                                    i64.sub
                                    i64.store32
                                    local.get 18
                                    i32.const -4
                                    i32.add
                                    local.tee 18
                                    local.get 20
                                    i32.ge_u
                                    br_if 0 (;@16;)
                                  end
                                  local.get 32
                                  i64.const 1000000000
                                  i64.lt_u
                                  br_if 0 (;@15;)
                                  local.get 20
                                  i32.const -4
                                  i32.add
                                  local.tee 20
                                  local.get 31
                                  i64.store32
                                end
                                block  ;; label = @15
                                  loop  ;; label = @16
                                    local.get 19
                                    local.tee 18
                                    local.get 20
                                    i32.le_u
                                    br_if 1 (;@15;)
                                    local.get 18
                                    i32.const -4
                                    i32.add
                                    local.tee 19
                                    i32.load
                                    i32.eqz
                                    br_if 0 (;@16;)
                                  end
                                end
                                local.get 5
                                local.get 5
                                i32.load offset=92
                                local.get 21
                                i32.sub
                                local.tee 21
                                i32.store offset=92
                                local.get 18
                                local.set 19
                                local.get 21
                                i32.const 0
                                i32.gt_s
                                br_if 0 (;@14;)
                              end
                            end
                            block  ;; label = @13
                              local.get 21
                              i32.const -1
                              i32.gt_s
                              br_if 0 (;@13;)
                              local.get 22
                              i32.const 25
                              i32.add
                              i32.const 9
                              i32.div_u
                              i32.const 1
                              i32.add
                              local.set 40
                              local.get 37
                              i32.const 102
                              i32.eq
                              local.set 41
                              loop  ;; label = @14
                                i32.const 0
                                local.get 21
                                i32.sub
                                local.tee 19
                                i32.const 9
                                local.get 19
                                i32.const 9
                                i32.lt_u
                                select
                                local.set 23
                                block  ;; label = @15
                                  block  ;; label = @16
                                    local.get 20
                                    local.get 18
                                    i32.lt_u
                                    br_if 0 (;@16;)
                                    i32.const 0
                                    i32.const 4
                                    local.get 20
                                    i32.load
                                    select
                                    local.set 19
                                    br 1 (;@15;)
                                  end
                                  i32.const 1000000000
                                  local.get 23
                                  i32.shr_u
                                  local.set 27
                                  i32.const -1
                                  local.get 23
                                  i32.shl
                                  i32.const -1
                                  i32.xor
                                  local.set 26
                                  i32.const 0
                                  local.set 21
                                  local.get 20
                                  local.set 19
                                  loop  ;; label = @16
                                    local.get 19
                                    local.get 19
                                    i32.load
                                    local.tee 24
                                    local.get 23
                                    i32.shr_u
                                    local.get 21
                                    i32.add
                                    i32.store
                                    local.get 24
                                    local.get 26
                                    i32.and
                                    local.get 27
                                    i32.mul
                                    local.set 21
                                    local.get 19
                                    i32.const 4
                                    i32.add
                                    local.tee 19
                                    local.get 18
                                    i32.lt_u
                                    br_if 0 (;@16;)
                                  end
                                  i32.const 0
                                  i32.const 4
                                  local.get 20
                                  i32.load
                                  select
                                  local.set 19
                                  local.get 21
                                  i32.eqz
                                  br_if 0 (;@15;)
                                  local.get 18
                                  local.get 21
                                  i32.store
                                  local.get 18
                                  i32.const 4
                                  i32.add
                                  local.set 18
                                end
                                local.get 5
                                local.get 5
                                i32.load offset=92
                                local.get 23
                                i32.add
                                local.tee 21
                                i32.store offset=92
                                local.get 30
                                local.get 20
                                local.get 19
                                i32.add
                                local.tee 20
                                local.get 41
                                select
                                local.tee 19
                                local.get 40
                                i32.const 2
                                i32.shl
                                i32.add
                                local.get 18
                                local.get 18
                                local.get 19
                                i32.sub
                                i32.const 2
                                i32.shr_s
                                local.get 40
                                i32.gt_s
                                select
                                local.set 18
                                local.get 21
                                i32.const 0
                                i32.lt_s
                                br_if 0 (;@14;)
                              end
                            end
                            i32.const 0
                            local.set 24
                            block  ;; label = @13
                              local.get 20
                              local.get 18
                              i32.ge_u
                              br_if 0 (;@13;)
                              local.get 30
                              local.get 20
                              i32.sub
                              i32.const 2
                              i32.shr_s
                              i32.const 9
                              i32.mul
                              local.set 24
                              local.get 20
                              i32.load
                              local.tee 21
                              i32.const 10
                              i32.lt_u
                              br_if 0 (;@13;)
                              i32.const 10
                              local.set 19
                              loop  ;; label = @14
                                local.get 24
                                i32.const 1
                                i32.add
                                local.set 24
                                local.get 21
                                local.get 19
                                i32.const 10
                                i32.mul
                                local.tee 19
                                i32.ge_u
                                br_if 0 (;@14;)
                              end
                            end
                            block  ;; label = @13
                              local.get 22
                              i32.const 0
                              local.get 24
                              local.get 37
                              i32.const 102
                              i32.eq
                              select
                              i32.sub
                              local.get 22
                              i32.const 0
                              i32.ne
                              local.get 37
                              i32.const 103
                              i32.eq
                              local.tee 26
                              i32.and
                              i32.sub
                              local.tee 19
                              local.get 18
                              local.get 30
                              i32.sub
                              i32.const 2
                              i32.shr_s
                              i32.const 9
                              i32.mul
                              i32.const -9
                              i32.add
                              i32.ge_s
                              br_if 0 (;@13;)
                              local.get 5
                              i32.const 96
                              i32.add
                              i32.const -4092
                              i32.const -3804
                              local.get 38
                              select
                              local.tee 37
                              i32.add
                              local.get 19
                              i32.const 9216
                              i32.add
                              local.tee 21
                              i32.const 9
                              i32.div_s
                              local.tee 23
                              i32.const 2
                              i32.shl
                              local.tee 38
                              i32.add
                              local.set 27
                              i32.const 10
                              local.set 19
                              block  ;; label = @14
                                local.get 21
                                local.get 23
                                i32.const 9
                                i32.mul
                                i32.sub
                                local.tee 23
                                i32.const 7
                                i32.gt_s
                                br_if 0 (;@14;)
                                i32.const 8
                                local.get 23
                                i32.sub
                                local.tee 40
                                i32.const 7
                                i32.and
                                local.set 21
                                i32.const 10
                                local.set 19
                                block  ;; label = @15
                                  local.get 23
                                  i32.const -1
                                  i32.add
                                  i32.const 7
                                  i32.lt_u
                                  br_if 0 (;@15;)
                                  local.get 40
                                  i32.const -8
                                  i32.and
                                  local.set 23
                                  i32.const 10
                                  local.set 19
                                  loop  ;; label = @16
                                    local.get 19
                                    i32.const 100000000
                                    i32.mul
                                    local.set 19
                                    local.get 23
                                    i32.const -8
                                    i32.add
                                    local.tee 23
                                    br_if 0 (;@16;)
                                  end
                                end
                                local.get 21
                                i32.eqz
                                br_if 0 (;@14;)
                                loop  ;; label = @15
                                  local.get 19
                                  i32.const 10
                                  i32.mul
                                  local.set 19
                                  local.get 21
                                  i32.const -1
                                  i32.add
                                  local.tee 21
                                  br_if 0 (;@15;)
                                end
                              end
                              local.get 27
                              i32.const 4
                              i32.add
                              local.set 40
                              block  ;; label = @14
                                block  ;; label = @15
                                  local.get 27
                                  i32.load
                                  local.tee 21
                                  local.get 21
                                  local.get 19
                                  i32.div_u
                                  local.tee 41
                                  local.get 19
                                  i32.mul
                                  i32.sub
                                  local.tee 23
                                  br_if 0 (;@15;)
                                  local.get 40
                                  local.get 18
                                  i32.eq
                                  br_if 1 (;@14;)
                                end
                                block  ;; label = @15
                                  block  ;; label = @16
                                    local.get 41
                                    i32.const 1
                                    i32.and
                                    br_if 0 (;@16;)
                                    f64.const 0x1p+53 (;=9.0072e+15;)
                                    local.set 33
                                    local.get 19
                                    i32.const 1000000000
                                    i32.ne
                                    br_if 1 (;@15;)
                                    local.get 27
                                    local.get 20
                                    i32.le_u
                                    br_if 1 (;@15;)
                                    local.get 27
                                    i32.const -4
                                    i32.add
                                    i32.load8_u
                                    i32.const 1
                                    i32.and
                                    i32.eqz
                                    br_if 1 (;@15;)
                                  end
                                  f64.const 0x1.0000000000001p+53 (;=9.0072e+15;)
                                  local.set 33
                                end
                                f64.const 0x1p-1 (;=0.5;)
                                f64.const 0x1p+0 (;=1;)
                                f64.const 0x1.8p+0 (;=1.5;)
                                local.get 40
                                local.get 18
                                i32.eq
                                select
                                f64.const 0x1.8p+0 (;=1.5;)
                                local.get 23
                                local.get 19
                                i32.const 1
                                i32.shr_u
                                local.tee 40
                                i32.eq
                                select
                                local.get 23
                                local.get 40
                                i32.lt_u
                                select
                                local.set 42
                                block  ;; label = @15
                                  local.get 35
                                  br_if 0 (;@15;)
                                  local.get 36
                                  i32.load8_u
                                  i32.const 45
                                  i32.ne
                                  br_if 0 (;@15;)
                                  local.get 42
                                  f64.neg
                                  local.set 42
                                  local.get 33
                                  f64.neg
                                  local.set 33
                                end
                                local.get 27
                                local.get 21
                                local.get 23
                                i32.sub
                                local.tee 21
                                i32.store
                                local.get 33
                                local.get 42
                                f64.add
                                local.get 33
                                f64.eq
                                br_if 0 (;@14;)
                                local.get 27
                                local.get 21
                                local.get 19
                                i32.add
                                local.tee 19
                                i32.store
                                block  ;; label = @15
                                  local.get 19
                                  i32.const 1000000000
                                  i32.lt_u
                                  br_if 0 (;@15;)
                                  local.get 7
                                  local.get 37
                                  local.get 38
                                  i32.add
                                  i32.add
                                  local.set 19
                                  loop  ;; label = @16
                                    local.get 19
                                    i32.const 4
                                    i32.add
                                    i32.const 0
                                    i32.store
                                    block  ;; label = @17
                                      local.get 19
                                      local.get 20
                                      i32.ge_u
                                      br_if 0 (;@17;)
                                      local.get 20
                                      i32.const -4
                                      i32.add
                                      local.tee 20
                                      i32.const 0
                                      i32.store
                                    end
                                    local.get 19
                                    local.get 19
                                    i32.load
                                    i32.const 1
                                    i32.add
                                    local.tee 21
                                    i32.store
                                    local.get 19
                                    i32.const -4
                                    i32.add
                                    local.set 19
                                    local.get 21
                                    i32.const 999999999
                                    i32.gt_u
                                    br_if 0 (;@16;)
                                  end
                                  local.get 19
                                  i32.const 4
                                  i32.add
                                  local.set 27
                                end
                                local.get 30
                                local.get 20
                                i32.sub
                                i32.const 2
                                i32.shr_s
                                i32.const 9
                                i32.mul
                                local.set 24
                                local.get 20
                                i32.load
                                local.tee 21
                                i32.const 10
                                i32.lt_u
                                br_if 0 (;@14;)
                                i32.const 10
                                local.set 19
                                loop  ;; label = @15
                                  local.get 24
                                  i32.const 1
                                  i32.add
                                  local.set 24
                                  local.get 21
                                  local.get 19
                                  i32.const 10
                                  i32.mul
                                  local.tee 19
                                  i32.ge_u
                                  br_if 0 (;@15;)
                                end
                              end
                              local.get 27
                              i32.const 4
                              i32.add
                              local.tee 19
                              local.get 18
                              local.get 18
                              local.get 19
                              i32.gt_u
                              select
                              local.set 18
                            end
                            local.get 18
                            local.get 30
                            i32.sub
                            local.set 19
                            block  ;; label = @13
                              loop  ;; label = @14
                                local.get 19
                                local.set 21
                                local.get 18
                                local.tee 27
                                local.get 20
                                i32.le_u
                                local.tee 23
                                br_if 1 (;@13;)
                                local.get 21
                                i32.const -4
                                i32.add
                                local.set 19
                                local.get 27
                                i32.const -4
                                i32.add
                                local.tee 18
                                i32.load
                                i32.eqz
                                br_if 0 (;@14;)
                              end
                            end
                            block  ;; label = @13
                              block  ;; label = @14
                                local.get 26
                                br_if 0 (;@14;)
                                local.get 28
                                i32.const 8
                                i32.and
                                local.set 40
                                br 1 (;@13;)
                              end
                              local.get 24
                              i32.const -1
                              i32.xor
                              i32.const -1
                              local.get 22
                              i32.const 1
                              local.get 22
                              select
                              local.tee 18
                              local.get 24
                              i32.gt_s
                              local.get 24
                              i32.const -5
                              i32.gt_s
                              i32.and
                              local.tee 19
                              select
                              local.get 18
                              i32.add
                              local.set 22
                              i32.const -1
                              i32.const -2
                              local.get 19
                              select
                              local.get 29
                              i32.add
                              local.set 29
                              local.get 28
                              i32.const 8
                              i32.and
                              local.tee 40
                              br_if 0 (;@13;)
                              i32.const -9
                              local.set 18
                              block  ;; label = @14
                                local.get 23
                                br_if 0 (;@14;)
                                local.get 27
                                i32.const -4
                                i32.add
                                i32.load
                                local.tee 23
                                i32.eqz
                                br_if 0 (;@14;)
                                i32.const 0
                                local.set 18
                                local.get 23
                                i32.const 10
                                i32.rem_u
                                br_if 0 (;@14;)
                                i32.const 10
                                local.set 19
                                i32.const 0
                                local.set 18
                                loop  ;; label = @15
                                  local.get 18
                                  i32.const -1
                                  i32.add
                                  local.set 18
                                  local.get 23
                                  local.get 19
                                  i32.const 10
                                  i32.mul
                                  local.tee 19
                                  i32.rem_u
                                  i32.eqz
                                  br_if 0 (;@15;)
                                end
                              end
                              local.get 21
                              i32.const 2
                              i32.shr_s
                              i32.const 9
                              i32.mul
                              local.set 19
                              block  ;; label = @14
                                local.get 29
                                i32.const -33
                                i32.and
                                i32.const 70
                                i32.ne
                                br_if 0 (;@14;)
                                i32.const 0
                                local.set 40
                                local.get 22
                                local.get 19
                                local.get 18
                                i32.add
                                i32.const -9
                                i32.add
                                local.tee 18
                                i32.const 0
                                local.get 18
                                i32.const 0
                                i32.gt_s
                                select
                                local.tee 18
                                local.get 22
                                local.get 18
                                i32.lt_s
                                select
                                local.set 22
                                br 1 (;@13;)
                              end
                              i32.const 0
                              local.set 40
                              local.get 22
                              local.get 24
                              local.get 19
                              i32.add
                              local.get 18
                              i32.add
                              i32.const -9
                              i32.add
                              local.tee 18
                              i32.const 0
                              local.get 18
                              i32.const 0
                              i32.gt_s
                              select
                              local.tee 18
                              local.get 22
                              local.get 18
                              i32.lt_s
                              select
                              local.set 22
                            end
                            local.get 22
                            i32.const 2147483645
                            i32.const 2147483646
                            local.get 22
                            local.get 40
                            i32.or
                            local.tee 37
                            select
                            i32.gt_s
                            br_if 8 (;@4;)
                            local.get 22
                            local.get 37
                            i32.const 0
                            i32.ne
                            i32.add
                            i32.const 1
                            i32.add
                            local.set 41
                            block  ;; label = @13
                              block  ;; label = @14
                                local.get 29
                                i32.const -33
                                i32.and
                                i32.const 70
                                i32.ne
                                local.tee 38
                                br_if 0 (;@14;)
                                local.get 24
                                local.get 41
                                i32.const 2147483647
                                i32.xor
                                i32.gt_s
                                br_if 10 (;@4;)
                                local.get 24
                                i32.const 0
                                local.get 24
                                i32.const 0
                                i32.gt_s
                                select
                                local.set 18
                                br 1 (;@13;)
                              end
                              block  ;; label = @14
                                block  ;; label = @15
                                  local.get 24
                                  br_if 0 (;@15;)
                                  local.get 6
                                  local.set 21
                                  local.get 6
                                  local.set 19
                                  br 1 (;@14;)
                                end
                                local.get 24
                                local.get 24
                                i32.const 31
                                i32.shr_s
                                local.tee 18
                                i32.xor
                                local.get 18
                                i32.sub
                                local.set 18
                                local.get 6
                                local.set 21
                                local.get 6
                                local.set 19
                                loop  ;; label = @15
                                  local.get 19
                                  i32.const -1
                                  i32.add
                                  local.tee 19
                                  local.get 18
                                  local.get 18
                                  i32.const 10
                                  i32.div_u
                                  local.tee 23
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
                                  local.get 18
                                  i32.const 9
                                  i32.gt_u
                                  local.set 26
                                  local.get 23
                                  local.set 18
                                  local.get 26
                                  br_if 0 (;@15;)
                                end
                              end
                              block  ;; label = @14
                                local.get 6
                                local.get 21
                                i32.sub
                                i32.const 1
                                i32.gt_s
                                br_if 0 (;@14;)
                                local.get 19
                                local.get 14
                                local.get 21
                                i32.sub
                                i32.add
                                local.set 19
                                local.get 21
                                local.get 5
                                i32.const 52
                                i32.add
                                i32.sub
                                i32.const -10
                                i32.add
                                local.tee 18
                                i32.eqz
                                br_if 0 (;@14;)
                                local.get 19
                                i32.const 48
                                local.get 18
                                memory.fill
                              end
                              local.get 19
                              i32.const -2
                              i32.add
                              local.tee 35
                              local.get 29
                              i32.store8
                              local.get 19
                              i32.const -1
                              i32.add
                              i32.const 45
                              i32.const 43
                              local.get 24
                              i32.const 0
                              i32.lt_s
                              select
                              i32.store8
                              local.get 6
                              local.get 35
                              i32.sub
                              local.tee 18
                              local.get 41
                              i32.const 2147483647
                              i32.xor
                              i32.gt_s
                              br_if 9 (;@4;)
                            end
                            local.get 18
                            local.get 41
                            i32.add
                            local.tee 18
                            local.get 34
                            i32.const 2147483647
                            i32.xor
                            i32.gt_s
                            br_if 8 (;@4;)
                            local.get 18
                            local.get 34
                            i32.add
                            local.set 26
                            block  ;; label = @13
                              local.get 28
                              i32.const 73728
                              i32.and
                              local.tee 28
                              br_if 0 (;@13;)
                              local.get 25
                              local.get 26
                              i32.le_s
                              br_if 0 (;@13;)
                              block  ;; label = @14
                                local.get 25
                                local.get 26
                                i32.sub
                                local.tee 18
                                i32.const 256
                                local.get 18
                                i32.const 256
                                i32.lt_u
                                local.tee 19
                                select
                                local.tee 21
                                i32.eqz
                                br_if 0 (;@14;)
                                local.get 5
                                i32.const 608
                                i32.add
                                i32.const 32
                                local.get 21
                                memory.fill
                              end
                              block  ;; label = @14
                                local.get 19
                                br_if 0 (;@14;)
                                loop  ;; label = @15
                                  block  ;; label = @16
                                    local.get 0
                                    i32.load8_u
                                    i32.const 32
                                    i32.and
                                    br_if 0 (;@16;)
                                    local.get 5
                                    i32.const 608
                                    i32.add
                                    i32.const 256
                                    local.get 0
                                    call $__fwritex
                                    drop
                                  end
                                  local.get 18
                                  i32.const -256
                                  i32.add
                                  local.tee 18
                                  i32.const 255
                                  i32.gt_u
                                  br_if 0 (;@15;)
                                end
                              end
                              local.get 0
                              i32.load8_u
                              i32.const 32
                              i32.and
                              br_if 0 (;@13;)
                              local.get 5
                              i32.const 608
                              i32.add
                              local.get 18
                              local.get 0
                              call $__fwritex
                              drop
                            end
                            block  ;; label = @13
                              local.get 0
                              i32.load8_u
                              i32.const 32
                              i32.and
                              br_if 0 (;@13;)
                              local.get 36
                              local.get 34
                              local.get 0
                              call $__fwritex
                              drop
                            end
                            block  ;; label = @13
                              local.get 28
                              i32.const 65536
                              i32.ne
                              br_if 0 (;@13;)
                              local.get 25
                              local.get 26
                              i32.le_s
                              br_if 0 (;@13;)
                              block  ;; label = @14
                                local.get 25
                                local.get 26
                                i32.sub
                                local.tee 18
                                i32.const 256
                                local.get 18
                                i32.const 256
                                i32.lt_u
                                local.tee 19
                                select
                                local.tee 21
                                i32.eqz
                                br_if 0 (;@14;)
                                local.get 5
                                i32.const 608
                                i32.add
                                i32.const 48
                                local.get 21
                                memory.fill
                              end
                              block  ;; label = @14
                                local.get 19
                                br_if 0 (;@14;)
                                loop  ;; label = @15
                                  block  ;; label = @16
                                    local.get 0
                                    i32.load8_u
                                    i32.const 32
                                    i32.and
                                    br_if 0 (;@16;)
                                    local.get 5
                                    i32.const 608
                                    i32.add
                                    i32.const 256
                                    local.get 0
                                    call $__fwritex
                                    drop
                                  end
                                  local.get 18
                                  i32.const -256
                                  i32.add
                                  local.tee 18
                                  i32.const 255
                                  i32.gt_u
                                  br_if 0 (;@15;)
                                end
                              end
                              local.get 0
                              i32.load8_u
                              i32.const 32
                              i32.and
                              br_if 0 (;@13;)
                              local.get 5
                              i32.const 608
                              i32.add
                              local.get 18
                              local.get 0
                              call $__fwritex
                              drop
                            end
                            local.get 38
                            br_if 2 (;@10;)
                            local.get 30
                            local.get 20
                            local.get 20
                            local.get 30
                            i32.gt_u
                            select
                            local.tee 24
                            local.set 23
                            loop  ;; label = @13
                              block  ;; label = @14
                                block  ;; label = @15
                                  block  ;; label = @16
                                    block  ;; label = @17
                                      local.get 23
                                      i32.load
                                      local.tee 18
                                      i32.eqz
                                      br_if 0 (;@17;)
                                      i32.const 8
                                      local.set 19
                                      loop  ;; label = @18
                                        local.get 5
                                        i32.const 64
                                        i32.add
                                        local.get 19
                                        i32.add
                                        local.get 18
                                        local.get 18
                                        i32.const 10
                                        i32.div_u
                                        local.tee 20
                                        i32.const 10
                                        i32.mul
                                        i32.sub
                                        i32.const 48
                                        i32.or
                                        i32.store8
                                        local.get 19
                                        i32.const -1
                                        i32.add
                                        local.set 19
                                        local.get 18
                                        i32.const 9
                                        i32.gt_u
                                        local.set 21
                                        local.get 20
                                        local.set 18
                                        local.get 21
                                        br_if 0 (;@18;)
                                      end
                                      local.get 19
                                      i32.const 1
                                      i32.add
                                      local.tee 20
                                      local.get 5
                                      i32.const 64
                                      i32.add
                                      i32.add
                                      local.set 18
                                      block  ;; label = @18
                                        local.get 23
                                        local.get 24
                                        i32.eq
                                        br_if 0 (;@18;)
                                        local.get 19
                                        i32.const 2
                                        i32.add
                                        i32.const 2
                                        i32.lt_s
                                        br_if 4 (;@14;)
                                        br 3 (;@15;)
                                      end
                                      local.get 19
                                      i32.const 8
                                      i32.ne
                                      br_if 3 (;@14;)
                                      br 1 (;@16;)
                                    end
                                    i32.const 9
                                    local.set 20
                                    local.get 23
                                    local.get 24
                                    i32.ne
                                    br_if 1 (;@15;)
                                  end
                                  local.get 5
                                  i32.const 48
                                  i32.store8 offset=72
                                  local.get 12
                                  local.set 18
                                  br 1 (;@14;)
                                end
                                local.get 20
                                local.get 5
                                i32.const 64
                                i32.add
                                i32.add
                                local.get 5
                                i32.const 64
                                i32.add
                                local.get 11
                                local.get 20
                                i32.add
                                local.tee 18
                                local.get 5
                                i32.const 64
                                i32.add
                                local.get 18
                                i32.lt_u
                                select
                                local.tee 18
                                i32.sub
                                local.tee 19
                                i32.eqz
                                br_if 0 (;@14;)
                                local.get 18
                                i32.const 48
                                local.get 19
                                memory.fill
                              end
                              block  ;; label = @14
                                local.get 0
                                i32.load8_u
                                i32.const 32
                                i32.and
                                br_if 0 (;@14;)
                                local.get 18
                                local.get 13
                                local.get 18
                                i32.sub
                                local.get 0
                                call $__fwritex
                                drop
                              end
                              local.get 23
                              i32.const 4
                              i32.add
                              local.tee 23
                              local.get 30
                              i32.le_u
                              br_if 0 (;@13;)
                            end
                            block  ;; label = @13
                              local.get 37
                              i32.eqz
                              br_if 0 (;@13;)
                              local.get 0
                              i32.load8_u
                              i32.const 32
                              i32.and
                              br_if 0 (;@13;)
                              i32.const 1095
                              i32.const 1
                              local.get 0
                              call $__fwritex
                              drop
                            end
                            block  ;; label = @13
                              block  ;; label = @14
                                local.get 22
                                i32.const 1
                                i32.ge_s
                                br_if 0 (;@14;)
                                local.get 22
                                local.set 18
                                br 1 (;@13;)
                              end
                              block  ;; label = @14
                                local.get 23
                                local.get 27
                                i32.lt_u
                                br_if 0 (;@14;)
                                local.get 22
                                local.set 18
                                br 1 (;@13;)
                              end
                              loop  ;; label = @14
                                block  ;; label = @15
                                  block  ;; label = @16
                                    block  ;; label = @17
                                      local.get 23
                                      i32.load
                                      local.tee 18
                                      br_if 0 (;@17;)
                                      local.get 13
                                      local.set 19
                                      local.get 13
                                      local.set 20
                                      br 1 (;@16;)
                                    end
                                    local.get 13
                                    local.set 20
                                    local.get 13
                                    local.set 19
                                    loop  ;; label = @17
                                      local.get 19
                                      i32.const -1
                                      i32.add
                                      local.tee 19
                                      local.get 18
                                      local.get 18
                                      i32.const 10
                                      i32.div_u
                                      local.tee 21
                                      i32.const 10
                                      i32.mul
                                      i32.sub
                                      i32.const 48
                                      i32.or
                                      i32.store8
                                      local.get 20
                                      i32.const -1
                                      i32.add
                                      local.set 20
                                      local.get 18
                                      i32.const 9
                                      i32.gt_u
                                      local.set 24
                                      local.get 21
                                      local.set 18
                                      local.get 24
                                      br_if 0 (;@17;)
                                    end
                                    local.get 19
                                    local.get 5
                                    i32.const 64
                                    i32.add
                                    i32.le_u
                                    br_if 1 (;@15;)
                                  end
                                  local.get 19
                                  local.get 5
                                  i32.const 64
                                  i32.add
                                  i32.add
                                  local.get 20
                                  i32.sub
                                  local.set 19
                                  local.get 20
                                  local.get 5
                                  i32.const 64
                                  i32.add
                                  i32.sub
                                  local.tee 18
                                  i32.eqz
                                  br_if 0 (;@15;)
                                  local.get 19
                                  i32.const 48
                                  local.get 18
                                  memory.fill
                                end
                                block  ;; label = @15
                                  local.get 0
                                  i32.load8_u
                                  i32.const 32
                                  i32.and
                                  br_if 0 (;@15;)
                                  local.get 19
                                  local.get 22
                                  i32.const 9
                                  local.get 22
                                  i32.const 9
                                  i32.lt_u
                                  select
                                  local.get 0
                                  call $__fwritex
                                  drop
                                end
                                local.get 22
                                i32.const -9
                                i32.add
                                local.set 18
                                local.get 23
                                i32.const 4
                                i32.add
                                local.tee 23
                                local.get 27
                                i32.ge_u
                                br_if 1 (;@13;)
                                local.get 22
                                i32.const 9
                                i32.gt_s
                                local.set 19
                                local.get 18
                                local.set 22
                                local.get 19
                                br_if 0 (;@14;)
                              end
                            end
                            local.get 0
                            i32.const 48
                            local.get 18
                            i32.const 9
                            i32.add
                            i32.const 9
                            i32.const 0
                            call $pad
                            br 3 (;@9;)
                          end
                          i32.const 0
                          i32.const 28
                          i32.store offset=4396
                          br 9 (;@2;)
                        end
                        i32.const 0
                        local.set 22
                        i32.const 1024
                        local.set 30
                        local.get 15
                        local.set 18
                        local.get 28
                        local.set 27
                        local.get 23
                        local.set 24
                        br 4 (;@6;)
                      end
                      block  ;; label = @10
                        local.get 22
                        i32.const 0
                        i32.lt_s
                        br_if 0 (;@10;)
                        local.get 27
                        local.get 20
                        i32.const 4
                        i32.add
                        local.get 27
                        local.get 20
                        i32.gt_u
                        select
                        local.set 27
                        local.get 20
                        local.set 23
                        loop  ;; label = @11
                          block  ;; label = @12
                            block  ;; label = @13
                              local.get 23
                              i32.load
                              local.tee 18
                              i32.eqz
                              br_if 0 (;@13;)
                              local.get 13
                              local.set 19
                              loop  ;; label = @14
                                local.get 19
                                i32.const -1
                                i32.add
                                local.tee 19
                                local.get 18
                                local.get 18
                                i32.const 10
                                i32.div_u
                                local.tee 21
                                i32.const 10
                                i32.mul
                                i32.sub
                                i32.const 48
                                i32.or
                                i32.store8
                                local.get 18
                                i32.const 10
                                i32.lt_u
                                local.set 24
                                local.get 21
                                local.set 18
                                local.get 24
                                i32.eqz
                                br_if 0 (;@14;)
                                br 2 (;@12;)
                              end
                            end
                            local.get 5
                            i32.const 48
                            i32.store8 offset=72
                            local.get 12
                            local.set 19
                          end
                          block  ;; label = @12
                            block  ;; label = @13
                              local.get 23
                              local.get 20
                              i32.eq
                              br_if 0 (;@13;)
                              local.get 19
                              local.get 5
                              i32.const 64
                              i32.add
                              i32.le_u
                              br_if 1 (;@12;)
                              block  ;; label = @14
                                local.get 19
                                local.get 5
                                i32.const 64
                                i32.add
                                i32.sub
                                local.tee 18
                                i32.eqz
                                br_if 0 (;@14;)
                                local.get 5
                                i32.const 64
                                i32.add
                                i32.const 48
                                local.get 18
                                memory.fill
                              end
                              local.get 5
                              i32.const 64
                              i32.add
                              local.set 19
                              br 1 (;@12;)
                            end
                            block  ;; label = @13
                              local.get 0
                              i32.load8_u
                              i32.const 32
                              i32.and
                              br_if 0 (;@13;)
                              local.get 19
                              i32.const 1
                              local.get 0
                              call $__fwritex
                              drop
                            end
                            local.get 19
                            i32.const 1
                            i32.add
                            local.set 19
                            local.get 22
                            local.get 40
                            i32.or
                            i32.eqz
                            br_if 0 (;@12;)
                            local.get 0
                            i32.load8_u
                            i32.const 32
                            i32.and
                            br_if 0 (;@12;)
                            i32.const 1095
                            i32.const 1
                            local.get 0
                            call $__fwritex
                            drop
                          end
                          local.get 13
                          local.get 19
                          i32.sub
                          local.set 18
                          block  ;; label = @12
                            local.get 0
                            i32.load8_u
                            i32.const 32
                            i32.and
                            br_if 0 (;@12;)
                            local.get 19
                            local.get 18
                            local.get 22
                            local.get 18
                            local.get 22
                            i32.lt_s
                            select
                            local.get 0
                            call $__fwritex
                            drop
                          end
                          local.get 22
                          local.get 18
                          i32.sub
                          local.set 22
                          local.get 23
                          i32.const 4
                          i32.add
                          local.tee 23
                          local.get 27
                          i32.ge_u
                          br_if 1 (;@10;)
                          local.get 22
                          i32.const -1
                          i32.gt_s
                          br_if 0 (;@11;)
                        end
                      end
                      local.get 0
                      i32.const 48
                      local.get 22
                      i32.const 18
                      i32.add
                      i32.const 18
                      i32.const 0
                      call $pad
                      local.get 0
                      i32.load8_u
                      i32.const 32
                      i32.and
                      br_if 0 (;@9;)
                      local.get 35
                      local.get 6
                      local.get 35
                      i32.sub
                      local.get 0
                      call $__fwritex
                      drop
                    end
                    block  ;; label = @9
                      local.get 28
                      i32.const 8192
                      i32.ne
                      br_if 0 (;@9;)
                      local.get 25
                      local.get 26
                      i32.le_s
                      br_if 0 (;@9;)
                      block  ;; label = @10
                        local.get 25
                        local.get 26
                        i32.sub
                        local.tee 18
                        i32.const 256
                        local.get 18
                        i32.const 256
                        i32.lt_u
                        local.tee 19
                        select
                        local.tee 20
                        i32.eqz
                        br_if 0 (;@10;)
                        local.get 5
                        i32.const 608
                        i32.add
                        i32.const 32
                        local.get 20
                        memory.fill
                      end
                      block  ;; label = @10
                        local.get 19
                        br_if 0 (;@10;)
                        loop  ;; label = @11
                          block  ;; label = @12
                            local.get 0
                            i32.load8_u
                            i32.const 32
                            i32.and
                            br_if 0 (;@12;)
                            local.get 5
                            i32.const 608
                            i32.add
                            i32.const 256
                            local.get 0
                            call $__fwritex
                            drop
                          end
                          local.get 18
                          i32.const -256
                          i32.add
                          local.tee 18
                          i32.const 255
                          i32.gt_u
                          br_if 0 (;@11;)
                        end
                      end
                      local.get 0
                      i32.load8_u
                      i32.const 32
                      i32.and
                      br_if 0 (;@9;)
                      local.get 5
                      i32.const 608
                      i32.add
                      local.get 18
                      local.get 0
                      call $__fwritex
                      drop
                    end
                    local.get 25
                    local.get 26
                    local.get 25
                    local.get 26
                    i32.gt_s
                    select
                    local.set 18
                    br 3 (;@5;)
                  end
                  local.get 36
                  local.get 29
                  i32.const 26
                  i32.shl
                  i32.const 31
                  i32.shr_s
                  i32.const 9
                  i32.and
                  i32.add
                  local.set 22
                  block  ;; label = @8
                    local.get 23
                    i32.const 11
                    i32.gt_u
                    br_if 0 (;@8;)
                    block  ;; label = @9
                      block  ;; label = @10
                        i32.const 12
                        local.get 23
                        i32.sub
                        local.tee 18
                        i32.const 7
                        i32.and
                        local.tee 19
                        br_if 0 (;@10;)
                        f64.const 0x1p+4 (;=16;)
                        local.set 42
                        br 1 (;@9;)
                      end
                      local.get 23
                      i32.const -12
                      i32.add
                      local.set 18
                      f64.const 0x1p+4 (;=16;)
                      local.set 42
                      loop  ;; label = @10
                        local.get 18
                        i32.const 1
                        i32.add
                        local.set 18
                        local.get 42
                        f64.const 0x1p+4 (;=16;)
                        f64.mul
                        local.set 42
                        local.get 19
                        i32.const -1
                        i32.add
                        local.tee 19
                        br_if 0 (;@10;)
                      end
                      i32.const 0
                      local.get 18
                      i32.sub
                      local.set 18
                    end
                    block  ;; label = @9
                      local.get 23
                      i32.const 4
                      i32.gt_u
                      br_if 0 (;@9;)
                      loop  ;; label = @10
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
                        local.get 18
                        i32.const -8
                        i32.add
                        local.tee 18
                        br_if 0 (;@10;)
                      end
                    end
                    block  ;; label = @9
                      local.get 22
                      i32.load8_u
                      i32.const 45
                      i32.ne
                      br_if 0 (;@9;)
                      local.get 42
                      local.get 33
                      f64.neg
                      local.get 42
                      f64.sub
                      f64.add
                      f64.neg
                      local.set 33
                      br 1 (;@8;)
                    end
                    local.get 33
                    local.get 42
                    f64.add
                    local.get 42
                    f64.sub
                    local.set 33
                  end
                  block  ;; label = @8
                    block  ;; label = @9
                      local.get 5
                      i32.load offset=92
                      local.tee 24
                      i32.eqz
                      br_if 0 (;@9;)
                      local.get 24
                      local.get 24
                      i32.const 31
                      i32.shr_s
                      local.tee 18
                      i32.xor
                      local.get 18
                      i32.sub
                      local.set 18
                      local.get 6
                      local.set 19
                      loop  ;; label = @10
                        local.get 19
                        i32.const -1
                        i32.add
                        local.tee 19
                        local.get 18
                        local.get 18
                        i32.const 10
                        i32.div_u
                        local.tee 20
                        i32.const 10
                        i32.mul
                        i32.sub
                        i32.const 48
                        i32.or
                        i32.store8
                        local.get 18
                        i32.const 10
                        i32.lt_u
                        local.set 21
                        local.get 20
                        local.set 18
                        local.get 21
                        i32.eqz
                        br_if 0 (;@10;)
                        br 2 (;@8;)
                      end
                    end
                    local.get 5
                    i32.const 48
                    i32.store8 offset=63
                    local.get 10
                    local.set 19
                  end
                  local.get 34
                  i32.const 2
                  i32.or
                  local.set 27
                  local.get 29
                  i32.const 32
                  i32.and
                  local.set 20
                  local.get 19
                  i32.const -2
                  i32.add
                  local.tee 26
                  local.get 29
                  i32.const 15
                  i32.add
                  i32.store8
                  local.get 19
                  i32.const -1
                  i32.add
                  i32.const 45
                  i32.const 43
                  local.get 24
                  i32.const 0
                  i32.lt_s
                  select
                  i32.store8
                  local.get 28
                  i32.const 8
                  i32.and
                  i32.eqz
                  local.get 23
                  i32.const 1
                  i32.lt_s
                  i32.and
                  local.set 21
                  local.get 5
                  i32.const 64
                  i32.add
                  local.set 19
                  loop  ;; label = @8
                    local.get 19
                    local.tee 18
                    local.get 33
                    i32.trunc_sat_f64_s
                    local.tee 19
                    i32.const 3520
                    i32.add
                    i32.load8_u
                    local.get 20
                    i32.or
                    i32.store8
                    local.get 33
                    local.get 19
                    f64.convert_i32_s
                    f64.sub
                    f64.const 0x1p+4 (;=16;)
                    f64.mul
                    local.set 33
                    block  ;; label = @9
                      local.get 18
                      i32.const 1
                      i32.add
                      local.tee 19
                      local.get 5
                      i32.const 64
                      i32.add
                      i32.sub
                      i32.const 1
                      i32.ne
                      br_if 0 (;@9;)
                      local.get 21
                      local.get 33
                      f64.const 0x0p+0 (;=0;)
                      f64.eq
                      i32.and
                      br_if 0 (;@9;)
                      local.get 18
                      i32.const 46
                      i32.store8 offset=1
                      local.get 18
                      i32.const 2
                      i32.add
                      local.set 19
                    end
                    local.get 33
                    f64.const 0x0p+0 (;=0;)
                    f64.ne
                    br_if 0 (;@8;)
                  end
                  local.get 23
                  i32.const 2147483645
                  local.get 6
                  local.get 26
                  i32.sub
                  local.tee 30
                  local.get 27
                  i32.add
                  local.tee 18
                  i32.sub
                  i32.gt_s
                  br_if 3 (;@4;)
                  local.get 23
                  i32.const 2
                  i32.add
                  local.get 19
                  local.get 5
                  i32.const 64
                  i32.add
                  i32.sub
                  local.tee 20
                  local.get 20
                  i32.const -2
                  i32.add
                  local.get 23
                  i32.lt_s
                  select
                  local.get 20
                  local.get 23
                  select
                  local.tee 24
                  local.get 18
                  i32.add
                  local.set 19
                  block  ;; label = @8
                    local.get 28
                    i32.const 73728
                    i32.and
                    local.tee 21
                    br_if 0 (;@8;)
                    local.get 25
                    local.get 19
                    i32.le_s
                    br_if 0 (;@8;)
                    block  ;; label = @9
                      local.get 25
                      local.get 19
                      i32.sub
                      local.tee 18
                      i32.const 256
                      local.get 18
                      i32.const 256
                      i32.lt_u
                      local.tee 23
                      select
                      local.tee 28
                      i32.eqz
                      br_if 0 (;@9;)
                      local.get 5
                      i32.const 608
                      i32.add
                      i32.const 32
                      local.get 28
                      memory.fill
                    end
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
                          i32.const 608
                          i32.add
                          i32.const 256
                          local.get 0
                          call $__fwritex
                          drop
                        end
                        local.get 18
                        i32.const -256
                        i32.add
                        local.tee 18
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
                    i32.const 608
                    i32.add
                    local.get 18
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
                    local.get 22
                    local.get 27
                    local.get 0
                    call $__fwritex
                    drop
                  end
                  block  ;; label = @8
                    local.get 21
                    i32.const 65536
                    i32.ne
                    br_if 0 (;@8;)
                    local.get 25
                    local.get 19
                    i32.le_s
                    br_if 0 (;@8;)
                    block  ;; label = @9
                      local.get 25
                      local.get 19
                      i32.sub
                      local.tee 18
                      i32.const 256
                      local.get 18
                      i32.const 256
                      i32.lt_u
                      local.tee 23
                      select
                      local.tee 27
                      i32.eqz
                      br_if 0 (;@9;)
                      local.get 5
                      i32.const 608
                      i32.add
                      i32.const 48
                      local.get 27
                      memory.fill
                    end
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
                          i32.const 608
                          i32.add
                          i32.const 256
                          local.get 0
                          call $__fwritex
                          drop
                        end
                        local.get 18
                        i32.const -256
                        i32.add
                        local.tee 18
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
                    i32.const 608
                    i32.add
                    local.get 18
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
                    local.get 5
                    i32.const 64
                    i32.add
                    local.get 20
                    local.get 0
                    call $__fwritex
                    drop
                  end
                  block  ;; label = @8
                    local.get 24
                    local.get 20
                    i32.sub
                    local.tee 18
                    i32.const 1
                    i32.lt_s
                    br_if 0 (;@8;)
                    block  ;; label = @9
                      local.get 18
                      i32.const 256
                      local.get 18
                      i32.const 256
                      i32.lt_u
                      local.tee 20
                      select
                      local.tee 24
                      i32.eqz
                      br_if 0 (;@9;)
                      local.get 5
                      i32.const 608
                      i32.add
                      i32.const 48
                      local.get 24
                      memory.fill
                    end
                    block  ;; label = @9
                      local.get 20
                      br_if 0 (;@9;)
                      loop  ;; label = @10
                        block  ;; label = @11
                          local.get 0
                          i32.load8_u
                          i32.const 32
                          i32.and
                          br_if 0 (;@11;)
                          local.get 5
                          i32.const 608
                          i32.add
                          i32.const 256
                          local.get 0
                          call $__fwritex
                          drop
                        end
                        local.get 18
                        i32.const -256
                        i32.add
                        local.tee 18
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
                    i32.const 608
                    i32.add
                    local.get 18
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
                    local.get 26
                    local.get 30
                    local.get 0
                    call $__fwritex
                    drop
                  end
                  block  ;; label = @8
                    local.get 21
                    i32.const 8192
                    i32.ne
                    br_if 0 (;@8;)
                    local.get 25
                    local.get 19
                    i32.le_s
                    br_if 0 (;@8;)
                    block  ;; label = @9
                      local.get 25
                      local.get 19
                      i32.sub
                      local.tee 18
                      i32.const 256
                      local.get 18
                      i32.const 256
                      i32.lt_u
                      local.tee 20
                      select
                      local.tee 21
                      i32.eqz
                      br_if 0 (;@9;)
                      local.get 5
                      i32.const 608
                      i32.add
                      i32.const 32
                      local.get 21
                      memory.fill
                    end
                    block  ;; label = @9
                      local.get 20
                      br_if 0 (;@9;)
                      loop  ;; label = @10
                        block  ;; label = @11
                          local.get 0
                          i32.load8_u
                          i32.const 32
                          i32.and
                          br_if 0 (;@11;)
                          local.get 5
                          i32.const 608
                          i32.add
                          i32.const 256
                          local.get 0
                          call $__fwritex
                          drop
                        end
                        local.get 18
                        i32.const -256
                        i32.add
                        local.tee 18
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
                    i32.const 608
                    i32.add
                    local.get 18
                    local.get 0
                    call $__fwritex
                    drop
                  end
                  local.get 25
                  local.get 19
                  local.get 25
                  local.get 19
                  i32.gt_s
                  select
                  local.set 18
                  br 2 (;@5;)
                end
                local.get 5
                local.get 18
                i32.store8 offset=39
                i32.const 0
                local.set 22
                i32.const 1024
                local.set 30
                i32.const 1
                local.set 24
                local.get 9
                local.set 19
                local.get 15
                local.set 18
              end
              local.get 24
              local.get 18
              local.get 19
              i32.sub
              local.tee 23
              local.get 24
              local.get 23
              i32.gt_s
              select
              local.tee 26
              local.get 22
              i32.const 2147483647
              i32.xor
              i32.gt_s
              br_if 1 (;@4;)
              local.get 25
              local.get 22
              local.get 26
              i32.add
              local.tee 21
              local.get 25
              local.get 21
              i32.gt_s
              select
              local.tee 18
              local.get 20
              i32.gt_u
              br_if 1 (;@4;)
              block  ;; label = @6
                local.get 27
                i32.const 73728
                i32.and
                local.tee 27
                br_if 0 (;@6;)
                local.get 25
                local.get 21
                i32.le_s
                br_if 0 (;@6;)
                block  ;; label = @7
                  local.get 18
                  local.get 21
                  i32.sub
                  local.tee 20
                  i32.const 256
                  local.get 20
                  i32.const 256
                  i32.lt_u
                  local.tee 28
                  select
                  local.tee 40
                  i32.eqz
                  br_if 0 (;@7;)
                  local.get 5
                  i32.const 96
                  i32.add
                  i32.const 32
                  local.get 40
                  memory.fill
                end
                block  ;; label = @7
                  local.get 28
                  br_if 0 (;@7;)
                  loop  ;; label = @8
                    block  ;; label = @9
                      local.get 0
                      i32.load8_u
                      i32.const 32
                      i32.and
                      br_if 0 (;@9;)
                      local.get 5
                      i32.const 96
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
                i32.const 96
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
                local.get 30
                local.get 22
                local.get 0
                call $__fwritex
                drop
              end
              block  ;; label = @6
                local.get 27
                i32.const 65536
                i32.ne
                br_if 0 (;@6;)
                local.get 25
                local.get 21
                i32.le_s
                br_if 0 (;@6;)
                block  ;; label = @7
                  local.get 18
                  local.get 21
                  i32.sub
                  local.tee 20
                  i32.const 256
                  local.get 20
                  i32.const 256
                  i32.lt_u
                  local.tee 22
                  select
                  local.tee 30
                  i32.eqz
                  br_if 0 (;@7;)
                  local.get 5
                  i32.const 96
                  i32.add
                  i32.const 48
                  local.get 30
                  memory.fill
                end
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
                      i32.const 96
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
                i32.const 96
                i32.add
                local.get 20
                local.get 0
                call $__fwritex
                drop
              end
              block  ;; label = @6
                local.get 24
                local.get 23
                i32.le_s
                br_if 0 (;@6;)
                block  ;; label = @7
                  local.get 26
                  local.get 23
                  i32.sub
                  local.tee 20
                  i32.const 256
                  local.get 20
                  i32.const 256
                  i32.lt_u
                  local.tee 24
                  select
                  local.tee 26
                  i32.eqz
                  br_if 0 (;@7;)
                  local.get 5
                  i32.const 96
                  i32.add
                  i32.const 48
                  local.get 26
                  memory.fill
                end
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
                      i32.const 96
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
                i32.const 96
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
                local.get 19
                local.get 23
                local.get 0
                call $__fwritex
                drop
              end
              local.get 27
              i32.const 8192
              i32.ne
              br_if 0 (;@5;)
              local.get 25
              local.get 21
              i32.le_s
              br_if 0 (;@5;)
              block  ;; label = @6
                local.get 18
                local.get 21
                i32.sub
                local.tee 19
                i32.const 256
                local.get 19
                i32.const 256
                i32.lt_u
                local.tee 20
                select
                local.tee 21
                i32.eqz
                br_if 0 (;@6;)
                local.get 5
                i32.const 96
                i32.add
                i32.const 32
                local.get 21
                memory.fill
              end
              block  ;; label = @6
                local.get 20
                br_if 0 (;@6;)
                loop  ;; label = @7
                  block  ;; label = @8
                    local.get 0
                    i32.load8_u
                    i32.const 32
                    i32.and
                    br_if 0 (;@8;)
                    local.get 5
                    i32.const 96
                    i32.add
                    i32.const 256
                    local.get 0
                    call $__fwritex
                    drop
                  end
                  local.get 19
                  i32.const -256
                  i32.add
                  local.tee 19
                  i32.const 255
                  i32.gt_u
                  br_if 0 (;@7;)
                end
              end
              local.get 0
              i32.load8_u
              i32.const 32
              i32.and
              br_if 0 (;@5;)
              local.get 5
              i32.const 96
              i32.add
              local.get 19
              local.get 0
              call $__fwritex
              drop
              br 0 (;@5;)
            end
          end
        end
        i32.const 0
        i32.const 61
        i32.store offset=4396
      end
      i32.const -1
      local.set 17
    end
    local.get 5
    i32.const 864
    i32.add
    global.set $__stack_pointer
    local.get 17)
  (func $pop_arg (type 14) (param i32 i32 i32)
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
  (func $pad (type 15) (param i32 i32 i32 i32 i32)
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
      block  ;; label = @2
        local.get 2
        local.get 3
        i32.sub
        local.tee 3
        i32.const 256
        local.get 3
        i32.const 256
        i32.lt_u
        local.tee 2
        select
        local.tee 4
        i32.eqz
        br_if 0 (;@2;)
        local.get 5
        local.get 1
        local.get 4
        memory.fill
      end
      block  ;; label = @2
        local.get 2
        br_if 0 (;@2;)
        loop  ;; label = @3
          block  ;; label = @4
            local.get 0
            i32.load8_u
            i32.const 32
            i32.and
            br_if 0 (;@4;)
            local.get 5
            i32.const 256
            local.get 0
            call $__fwritex
            drop
          end
          local.get 3
          i32.const -256
          i32.add
          local.tee 3
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
      local.get 5
      local.get 3
      local.get 0
      call $__fwritex
      drop
    end
    local.get 5
    i32.const 256
    i32.add
    global.set $__stack_pointer)
  (func $long_double_not_supported (type 8)
    i32.const 1207
    i32.const 3736
    call $fputs
    drop
    call $abort
    unreachable)
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
        block  ;; label = @3
          local.get 4
          i32.load8_u
          local.get 1
          i32.const 255
          i32.and
          i32.eq
          br_if 0 (;@3;)
          local.get 5
          i32.const 4
          i32.lt_u
          br_if 0 (;@3;)
          local.get 1
          i32.const 255
          i32.and
          i32.const 16843009
          i32.mul
          local.set 0
          loop  ;; label = @4
            i32.const 16843008
            local.get 4
            i32.load
            local.get 0
            i32.xor
            local.tee 2
            i32.sub
            local.get 2
            i32.or
            i32.const -2139062144
            i32.and
            i32.const -2139062144
            i32.ne
            br_if 2 (;@2;)
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
            br_if 0 (;@4;)
          end
        end
        local.get 5
        i32.eqz
        br_if 1 (;@1;)
      end
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
  (func $memcmp (type 1) (param i32 i32 i32) (result i32)
    (local i32 i32 i32)
    i32.const 0
    local.set 3
    block  ;; label = @1
      local.get 2
      i32.eqz
      br_if 0 (;@1;)
      block  ;; label = @2
        loop  ;; label = @3
          local.get 0
          i32.load8_u
          local.tee 4
          local.get 1
          i32.load8_u
          local.tee 5
          i32.ne
          br_if 1 (;@2;)
          local.get 1
          i32.const 1
          i32.add
          local.set 1
          local.get 0
          i32.const 1
          i32.add
          local.set 0
          local.get 2
          i32.const -1
          i32.add
          local.tee 2
          br_if 0 (;@3;)
          br 2 (;@1;)
        end
      end
      local.get 4
      local.get 5
      i32.sub
      local.set 3
    end
    local.get 3)
  (func $strchr (type 3) (param i32 i32) (result i32)
    local.get 0
    local.get 1
    call $__strchrnul
    local.tee 0
    i32.const 0
    local.get 0
    i32.load8_u
    local.get 1
    i32.const 255
    i32.and
    i32.eq
    select)
  (func $__strchrnul (type 3) (param i32 i32) (result i32)
    (local i32 i32 i32)
    block  ;; label = @1
      block  ;; label = @2
        block  ;; label = @3
          block  ;; label = @4
            local.get 1
            i32.const 255
            i32.and
            local.tee 2
            i32.eqz
            br_if 0 (;@4;)
            local.get 0
            i32.const 3
            i32.and
            i32.eqz
            br_if 2 (;@2;)
            block  ;; label = @5
              local.get 0
              i32.load8_u
              local.tee 3
              br_if 0 (;@5;)
              local.get 0
              return
            end
            local.get 3
            local.get 1
            i32.const 255
            i32.and
            i32.ne
            br_if 1 (;@3;)
            local.get 0
            return
          end
          local.get 0
          local.get 0
          call $strlen
          i32.add
          return
        end
        block  ;; label = @3
          local.get 0
          i32.const 1
          i32.add
          local.tee 3
          i32.const 3
          i32.and
          br_if 0 (;@3;)
          local.get 3
          local.set 0
          br 1 (;@2;)
        end
        local.get 3
        i32.load8_u
        local.tee 4
        i32.eqz
        br_if 1 (;@1;)
        local.get 4
        local.get 1
        i32.const 255
        i32.and
        i32.eq
        br_if 1 (;@1;)
        block  ;; label = @3
          local.get 0
          i32.const 2
          i32.add
          local.tee 3
          i32.const 3
          i32.and
          br_if 0 (;@3;)
          local.get 3
          local.set 0
          br 1 (;@2;)
        end
        local.get 3
        i32.load8_u
        local.tee 4
        i32.eqz
        br_if 1 (;@1;)
        local.get 4
        local.get 1
        i32.const 255
        i32.and
        i32.eq
        br_if 1 (;@1;)
        block  ;; label = @3
          local.get 0
          i32.const 3
          i32.add
          local.tee 3
          i32.const 3
          i32.and
          br_if 0 (;@3;)
          local.get 3
          local.set 0
          br 1 (;@2;)
        end
        local.get 3
        i32.load8_u
        local.tee 4
        i32.eqz
        br_if 1 (;@1;)
        local.get 4
        local.get 1
        i32.const 255
        i32.and
        i32.eq
        br_if 1 (;@1;)
        local.get 0
        i32.const 4
        i32.add
        local.set 0
      end
      block  ;; label = @2
        i32.const 16843008
        local.get 0
        i32.load
        local.tee 3
        i32.sub
        local.get 3
        i32.or
        i32.const -2139062144
        i32.and
        i32.const -2139062144
        i32.ne
        br_if 0 (;@2;)
        local.get 2
        i32.const 16843009
        i32.mul
        local.set 2
        loop  ;; label = @3
          i32.const 16843008
          local.get 3
          local.get 2
          i32.xor
          local.tee 3
          i32.sub
          local.get 3
          i32.or
          i32.const -2139062144
          i32.and
          i32.const -2139062144
          i32.ne
          br_if 1 (;@2;)
          i32.const 16843008
          local.get 0
          i32.const 4
          i32.add
          local.tee 0
          i32.load
          local.tee 3
          i32.sub
          local.get 3
          i32.or
          i32.const -2139062144
          i32.and
          i32.const -2139062144
          i32.eq
          br_if 0 (;@3;)
        end
      end
      local.get 0
      i32.const -1
      i32.add
      local.set 3
      loop  ;; label = @2
        local.get 3
        i32.const 1
        i32.add
        local.tee 3
        i32.load8_u
        local.tee 0
        i32.eqz
        br_if 1 (;@1;)
        local.get 0
        local.get 1
        i32.const 255
        i32.and
        i32.ne
        br_if 0 (;@2;)
      end
    end
    local.get 3)
  (func $strdup (type 0) (param i32) (result i32)
    (local i32 i32)
    block  ;; label = @1
      local.get 0
      call $strlen
      i32.const 1
      i32.add
      local.tee 1
      call $malloc
      local.tee 2
      i32.eqz
      br_if 0 (;@1;)
      local.get 1
      i32.eqz
      br_if 0 (;@1;)
      local.get 2
      local.get 0
      local.get 1
      memory.copy
    end
    local.get 2)
  (func $strlen (type 0) (param i32) (result i32)
    (local i32 i32 i32)
    local.get 0
    local.set 1
    block  ;; label = @1
      block  ;; label = @2
        local.get 0
        i32.const 3
        i32.and
        i32.eqz
        br_if 0 (;@2;)
        block  ;; label = @3
          local.get 0
          i32.load8_u
          br_if 0 (;@3;)
          local.get 0
          local.get 0
          i32.sub
          return
        end
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
        local.tee 1
        i32.const 3
        i32.and
        br_if 1 (;@1;)
      end
      local.get 1
      i32.const -4
      i32.add
      local.set 2
      local.get 1
      i32.const -5
      i32.add
      local.set 1
      loop  ;; label = @2
        local.get 1
        i32.const 4
        i32.add
        local.set 1
        i32.const 16843008
        local.get 2
        i32.const 4
        i32.add
        local.tee 2
        i32.load
        local.tee 3
        i32.sub
        local.get 3
        i32.or
        i32.const -2139062144
        i32.and
        i32.const -2139062144
        i32.eq
        br_if 0 (;@2;)
      end
      loop  ;; label = @2
        local.get 1
        i32.const 1
        i32.add
        local.set 1
        local.get 2
        i32.load8_u
        local.set 3
        local.get 2
        i32.const 1
        i32.add
        local.set 2
        local.get 3
        br_if 0 (;@2;)
      end
    end
    local.get 1
    local.get 0
    i32.sub)
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
  (table (;0;) 10 10 funcref)
  (memory (;0;) 2)
  (global $__stack_pointer (mut i32) (i32.const 71168))
  (global $GOT.data.internal.__memory_base i32 (i32.const 0))
  (export "memory" (memory 0))
  (export "_start" (func $_start))
  (export "OutlierRemovalAverageSummary" (func $OutlierRemovalAverageSummary))
  (export "OutlierRemovalAverageSummaryLowerThreshold" (func $OutlierRemovalAverageSummaryLowerThreshold))
  (export "OutlierRemovalAverageSummaryUpperThreshold" (func $OutlierRemovalAverageSummaryUpperThreshold))
  (export "setup" (func $setup))
  (export "run" (func $run))
  (elem (;0;) (i32.const 1) func $idlefn $workfn $handlerfn $devfn $__stdio_seek $__stdio_write $__stdio_read $__stdio_close $__stdout_write)
  (data $.rodata (i32.const 1024) "-+   0X0x\00-0X+0X 0X-0x+0x 0x\00w\00incorrect\00nan\00/dev/null\00inf\00rwa\00NAN\00INF\00.\00(null)\00These results are \00\0aend of run\0a\00Starting\0a\00\0afinished\0a\00\0aBad task id %d\0a\00qpkt count = %d  holdcount = %d\0a\00Support for formatting long double values is currently disabled.\0aTo enable it, add -lc-printscan-long-double to the link command.\0a\00\00\00 \0e\00\00Success\00Illegal byte sequence\00Domain error\00Result not representable\00Not a tty\00Permission denied\00Operation not permitted\00No such file or directory\00No such process\00File exists\00Value too large for data type\00No space left on device\00Out of memory\00Resource busy\00Interrupted system call\00Resource temporarily unavailable\00Invalid seek\00Cross-device link\00Read-only file system\00Directory not empty\00Connection reset by peer\00Operation timed out\00Connection refused\00Host is unreachable\00Address in use\00Broken pipe\00I/O error\00No such device or address\00No such device\00Not a directory\00Is a directory\00Text file busy\00Exec format error\00Invalid argument\00Argument list too long\00Symbolic link loop\00Filename too long\00Too many open files in system\00No file descriptors available\00Bad file descriptor\00No child process\00Bad address\00File too large\00Too many links\00No locks available\00Resource deadlock would occur\00State not recoverable\00Previous owner died\00Operation canceled\00Function not implemented\00No message of desired type\00Identifier removed\00Link has been severed\00Protocol error\00Bad message\00Not a socket\00Destination address required\00Message too large\00Protocol wrong type for socket\00Protocol not available\00Protocol not supported\00Not supported\00Address family not supported by protocol\00Address not available\00Network is down\00Network unreachable\00Connection reset by network\00Connection aborted\00No buffer space available\00Socket is connected\00Socket not connected\00Operation already in progress\00Operation in progress\00Stale file handle\00Quota exceeded\00Multihop attempted\00Capabilities insufficient\00\00\00u\02N\00\d6\01\e2\04\b9\04\18\01\8e\05\ed\02\16\04\f2\00\97\03\01\038\05\af\01\82\01O\03/\04\1e\00\d4\05\a2\00\12\03\1e\03\c2\01\de\03\08\00\ac\05\00\01d\02\f1\01e\054\02\8c\02\cf\02-\03L\04\e3\05\9f\02\f8\04\1c\05\08\05\b1\02K\05\15\02x\00R\02<\03\f1\03\e4\00\c3\03}\04\cc\00\aa\03y\05$\02n\01m\03\22\04\ab\04D\00\fb\01\ae\00\83\03`\00\e5\01\07\04\94\04^\04+\00X\019\01\92\00\c2\05\9b\01C\02F\01\f6\05\00\00\00\00\00\00\19\00\0b\00\19\19\19\00\00\00\00\05\00\00\00\00\00\00\09\00\00\00\00\0b\00\00\00\00\00\00\00\00\19\00\0a\0a\19\19\19\03\0a\07\00\01\1b\09\0b\18\00\00\09\06\0b\00\00\0b\00\06\19\00\00\00\19\19\19\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\0e\00\00\00\00\00\00\00\00\19\00\0b\0d\19\19\19\00\0d\00\00\02\00\09\0e\00\00\00\09\00\0e\00\00\0e\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\0c\00\00\00\00\00\00\00\00\00\00\00\13\00\00\00\00\13\00\00\00\00\09\0c\00\00\00\00\00\0c\00\00\0c\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\10\00\00\00\00\00\00\00\00\00\00\00\0f\00\00\00\04\0f\00\00\00\00\09\10\00\00\00\00\00\10\00\00\10\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\12\00\00\00\00\00\00\00\00\00\00\00\11\00\00\00\00\11\00\00\00\00\09\12\00\00\00\00\00\12\00\00\12\00\00\1a\00\00\00\1a\1a\1a\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\1a\00\00\00\1a\1a\1a\00\00\00\00\00\00\09\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\14\00\00\00\00\00\00\00\00\00\00\00\17\00\00\00\00\17\00\00\00\00\09\14\00\00\00\00\00\14\00\00\14\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\16\00\00\00\00\00\00\00\00\00\00\00\15\00\00\00\00\15\00\00\00\00\09\16\00\00\00\00\00\16\00\00\16\00\000123456789ABCDEF")
  (data $.data (i32.const 3536) "0ABCDEFGHIJKLMNOPQRSTUVWXYZ\00\00\00\00\00\0a\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\05\00\00\00\00\00\00\00\00\00\00\00\08\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\09\00\00\00\05\00\00\00h\11\00\00\00\04\00\00\00\00\00\00\00\00\00\00\01\00\00\00\00\00\00\00\0a\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00 \0e\00\00\00\00\00\00\05\00\00\00\00\00\00\00\00\00\00\00\08\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\06\00\00\00\05\00\00\00\8c\15\00\00\00\00\00\00\00\00\00\00\00\00\00\00\02\00\00\00\00\00\00\00\ff\ff\ff\ff\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\98\0e\00\00\00\00\02\00"))
