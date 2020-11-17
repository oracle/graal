;;
;; Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
  (type (;0;) (func (param i32) (result i32)))
  (type (;1;) (func (result i32)))
  (type (;2;) (func))
  (type (;3;) (func (param i32)))
  (type (;4;) (func (param i32 i32) (result i32)))
  (type (;5;) (func (param i32 i32 i32) (result i32)))
  (type (;6;) (func (param i32 i64 i32) (result i64)))
  (import "wasi_snapshot_preview1" "proc_exit" (func $__wasi_proc_exit (type 3)))
  (func $__wasm_call_ctors (type 2)
    nop)
  (func $fibonacci (type 0) (param i32) (result i32)
    (local i32 i32)
    local.get 0
    i32.const 1
    i32.lt_s
    if  ;; label = @1
      i32.const 0
      return
    end
    local.get 0
    i32.const 3
    i32.ge_s
    if  ;; label = @1
      loop  ;; label = @2
        local.get 0
        i32.const -1
        i32.add
        call $fibonacci
        local.get 1
        i32.add
        local.set 1
        local.get 0
        i32.const 4
        i32.gt_s
        local.set 2
        local.get 0
        i32.const -2
        i32.add
        local.set 0
        local.get 2
        br_if 0 (;@2;)
      end
    end
    local.get 1
    i32.const 1
    i32.add)
  (func $run (type 1) (result i32)
    i32.const 31
    call $fibonacci
    i32.const 1346269
    i32.ne)
  (func $main (type 4) (param i32 i32) (result i32)
    call $run)
  (func $_start (type 2)
    call $run
    call $__wasi_proc_exit
    unreachable)
  (func $__errno_location (type 1) (result i32)
    i32.const 1024)
  (func $stackSave (type 1) (result i32)
    global.get 0)
  (func $stackRestore (type 3) (param i32)
    local.get 0
    global.set 0)
  (func $stackAlloc (type 0) (param i32) (result i32)
    global.get 0
    local.get 0
    i32.sub
    i32.const -16
    i32.and
    local.tee 0
    global.set 0
    local.get 0)
  (func $fflush (type 0) (param i32) (result i32)
    (local i32)
    local.get 0
    if  ;; label = @1
      local.get 0
      i32.load offset=76
      i32.const -1
      i32.le_s
      if  ;; label = @2
        local.get 0
        call $__fflush_unlocked
        return
      end
      local.get 0
      call $__fflush_unlocked
      return
    end
    i32.const 1040
    i32.load
    if  ;; label = @1
      i32.const 1040
      i32.load
      call $fflush
      local.set 1
    end
    i32.const 1036
    i32.load
    local.tee 0
    if  ;; label = @1
      loop  ;; label = @2
        local.get 0
        i32.load offset=76
        i32.const 0
        i32.ge_s
        if (result i32)  ;; label = @3
          i32.const 1
        else
          i32.const 0
        end
        drop
        local.get 0
        i32.load offset=20
        local.get 0
        i32.load offset=28
        i32.gt_u
        if  ;; label = @3
          local.get 0
          call $__fflush_unlocked
          local.get 1
          i32.or
          local.set 1
        end
        local.get 0
        i32.load offset=56
        local.tee 0
        br_if 0 (;@2;)
      end
    end
    local.get 1)
  (func $__fflush_unlocked (type 0) (param i32) (result i32)
    (local i32 i32)
    block  ;; label = @1
      local.get 0
      i32.load offset=20
      local.get 0
      i32.load offset=28
      i32.le_u
      br_if 0 (;@1;)
      local.get 0
      i32.const 0
      i32.const 0
      local.get 0
      i32.load offset=36
      call_indirect (type 5)
      drop
      local.get 0
      i32.load offset=20
      br_if 0 (;@1;)
      i32.const -1
      return
    end
    local.get 0
    i32.load offset=4
    local.tee 1
    local.get 0
    i32.load offset=8
    local.tee 2
    i32.lt_u
    if  ;; label = @1
      local.get 0
      local.get 1
      local.get 2
      i32.sub
      i64.extend_i32_s
      i32.const 1
      local.get 0
      i32.load offset=40
      call_indirect (type 6)
      drop
    end
    local.get 0
    i32.const 0
    i32.store offset=28
    local.get 0
    i64.const 0
    i64.store offset=16
    local.get 0
    i64.const 0
    i64.store offset=4 align=4
    i32.const 0)
  (table (;0;) 2 2 funcref)
  (memory (;0;) 256 256)
  (global (;0;) (mut i32) (i32.const 5243936))
  (global (;1;) i32 (i32.const 1044))
  (export "memory" (memory 0))
  (export "__indirect_function_table" (table 0))
  (export "run" (func $run))
  (export "main" (func $main))
  (export "_start" (func $_start))
  (export "__errno_location" (func $__errno_location))
  (export "fflush" (func $fflush))
  (export "stackSave" (func $stackSave))
  (export "stackRestore" (func $stackRestore))
  (export "stackAlloc" (func $stackAlloc))
  (export "__data_end" (global 1))
  (elem (;0;) (i32.const 1) $__wasm_call_ctors))
