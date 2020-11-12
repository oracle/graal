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
  (type (;0;) (func (param i32)))
  (type (;1;) (func))
  (type (;2;) (func (result i32)))
  (type (;3;) (func (param i32) (result i32)))
  (type (;4;) (func (param i32 i32) (result i32)))
  (type (;5;) (func (param i32 i32 i32) (result i32)))
  (type (;6;) (func (param i32 i64 i32) (result i64)))
  (import "wasi_snapshot_preview1" "proc_exit" (func (;0;) (type 0)))
  (func (;1;) (type 1))
  (func (;2;) (type 3) (param i32) (result i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32)
    global.get 0
    local.set 1
    i32.const 16
    local.set 2
    local.get 1
    local.get 2
    i32.sub
    local.set 3
    local.get 3
    global.set 0
    i32.const 1
    local.set 4
    local.get 3
    local.get 0
    i32.store offset=8
    local.get 3
    i32.load offset=8
    local.set 5
    local.get 5
    local.set 6
    local.get 4
    local.set 7
    local.get 6
    local.get 7
    i32.lt_s
    local.set 8
    i32.const 1
    local.set 9
    local.get 8
    local.get 9
    i32.and
    local.set 10
    block  ;; label = @1
      block  ;; label = @2
        local.get 10
        i32.eqz
        br_if 0 (;@2;)
        i32.const 0
        local.set 11
        local.get 3
        local.get 11
        i32.store offset=12
        br 1 (;@1;)
      end
      i32.const 2
      local.set 12
      local.get 3
      i32.load offset=8
      local.set 13
      local.get 13
      local.set 14
      local.get 12
      local.set 15
      local.get 14
      local.get 15
      i32.le_s
      local.set 16
      i32.const 1
      local.set 17
      local.get 16
      local.get 17
      i32.and
      local.set 18
      block  ;; label = @2
        local.get 18
        i32.eqz
        br_if 0 (;@2;)
        i32.const 1
        local.set 19
        local.get 3
        local.get 19
        i32.store offset=12
        br 1 (;@1;)
      end
      local.get 3
      i32.load offset=8
      local.set 20
      i32.const 1
      local.set 21
      local.get 20
      local.get 21
      i32.sub
      local.set 22
      local.get 22
      call 2
      local.set 23
      local.get 3
      i32.load offset=8
      local.set 24
      i32.const 2
      local.set 25
      local.get 24
      local.get 25
      i32.sub
      local.set 26
      local.get 26
      call 2
      local.set 27
      local.get 23
      local.get 27
      i32.add
      local.set 28
      local.get 3
      local.get 28
      i32.store offset=12
    end
    local.get 3
    i32.load offset=12
    local.set 29
    i32.const 16
    local.set 30
    local.get 3
    local.get 30
    i32.add
    local.set 31
    local.get 31
    global.set 0
    local.get 29
    return)
  (func (;3;) (type 2) (result i32)
    (local i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32)
    global.get 0
    local.set 0
    i32.const 16
    local.set 1
    local.get 0
    local.get 1
    i32.sub
    local.set 2
    local.get 2
    global.set 0
    i32.const 24157817
    local.set 3
    i32.const 37
    local.set 4
    local.get 2
    local.get 4
    i32.store offset=8
    local.get 2
    local.get 3
    i32.store offset=4
    local.get 2
    i32.load offset=8
    local.set 5
    local.get 5
    call 2
    local.set 6
    local.get 2
    local.get 6
    i32.store
    local.get 2
    i32.load
    local.set 7
    local.get 2
    i32.load offset=4
    local.set 8
    local.get 7
    local.set 9
    local.get 8
    local.set 10
    local.get 9
    local.get 10
    i32.ne
    local.set 11
    i32.const 1
    local.set 12
    local.get 11
    local.get 12
    i32.and
    local.set 13
    block  ;; label = @1
      block  ;; label = @2
        local.get 13
        i32.eqz
        br_if 0 (;@2;)
        i32.const 1
        local.set 14
        local.get 2
        local.get 14
        i32.store offset=12
        br 1 (;@1;)
      end
      i32.const 0
      local.set 15
      local.get 2
      local.get 15
      i32.store offset=12
    end
    local.get 2
    i32.load offset=12
    local.set 16
    i32.const 16
    local.set 17
    local.get 2
    local.get 17
    i32.add
    local.set 18
    local.get 18
    global.set 0
    local.get 16
    return)
  (func (;4;) (type 2) (result i32)
    (local i32 i32 i32 i32 i32 i32 i32)
    global.get 0
    local.set 0
    i32.const 16
    local.set 1
    local.get 0
    local.get 1
    i32.sub
    local.set 2
    local.get 2
    global.set 0
    i32.const 0
    local.set 3
    local.get 2
    local.get 3
    i32.store offset=12
    call 3
    local.set 4
    i32.const 16
    local.set 5
    local.get 2
    local.get 5
    i32.add
    local.set 6
    local.get 6
    global.set 0
    local.get 4
    return)
  (func (;5;) (type 4) (param i32 i32) (result i32)
    (local i32)
    call 4
    local.set 2
    local.get 2
    return)
  (func (;6;) (type 1)
    block  ;; label = @1
      i32.const 1
      i32.eqz
      br_if 0 (;@1;)
      call 1
    end
    call 4
    call 10
    unreachable)
  (func (;7;) (type 2) (result i32)
    i32.const 1024)
  (func (;8;) (type 0) (param i32)
    local.get 0
    call 0
    unreachable)
  (func (;9;) (type 1)
    (local i32)
    i32.const 0
    local.set 0
    block  ;; label = @1
      i32.const 0
      i32.const 0
      i32.le_u
      br_if 0 (;@1;)
      loop  ;; label = @2
        local.get 0
        i32.const -4
        i32.add
        local.tee 0
        i32.load
        call_indirect (type 1)
        local.get 0
        i32.const 0
        i32.gt_u
        br_if 0 (;@2;)
      end
    end
    call 11)
  (func (;10;) (type 0) (param i32)
    call 11
    call 9
    call 11
    local.get 0
    call 8
    unreachable)
  (func (;11;) (type 1))
  (func (;12;) (type 2) (result i32)
    global.get 0)
  (func (;13;) (type 0) (param i32)
    local.get 0
    global.set 0)
  (func (;14;) (type 3) (param i32) (result i32)
    (local i32 i32)
    global.get 0
    local.get 0
    i32.sub
    i32.const -16
    i32.and
    local.tee 1
    global.set 0
    local.get 1)
  (func (;15;) (type 3) (param i32) (result i32)
    i32.const 1)
  (func (;16;) (type 0) (param i32))
  (func (;17;) (type 0) (param i32))
  (func (;18;) (type 0) (param i32))
  (func (;19;) (type 2) (result i32)
    i32.const 1028
    call 17
    i32.const 1036)
  (func (;20;) (type 1)
    i32.const 1028
    call 18)
  (func (;21;) (type 3) (param i32) (result i32)
    (local i32 i32)
    block  ;; label = @1
      block  ;; label = @2
        local.get 0
        i32.eqz
        br_if 0 (;@2;)
        block  ;; label = @3
          local.get 0
          i32.load offset=76
          i32.const -1
          i32.gt_s
          br_if 0 (;@3;)
          local.get 0
          call 22
          return
        end
        local.get 0
        call 15
        local.set 1
        local.get 0
        call 22
        local.set 2
        local.get 1
        i32.eqz
        br_if 1 (;@1;)
        local.get 0
        call 16
        local.get 2
        return
      end
      i32.const 0
      local.set 2
      block  ;; label = @2
        i32.const 0
        i32.load offset=1040
        i32.eqz
        br_if 0 (;@2;)
        i32.const 0
        i32.load offset=1040
        call 21
        local.set 2
      end
      block  ;; label = @2
        call 19
        i32.load
        local.tee 0
        i32.eqz
        br_if 0 (;@2;)
        loop  ;; label = @3
          i32.const 0
          local.set 1
          block  ;; label = @4
            local.get 0
            i32.load offset=76
            i32.const 0
            i32.lt_s
            br_if 0 (;@4;)
            local.get 0
            call 15
            local.set 1
          end
          block  ;; label = @4
            local.get 0
            i32.load offset=20
            local.get 0
            i32.load offset=28
            i32.le_u
            br_if 0 (;@4;)
            local.get 0
            call 22
            local.get 2
            i32.or
            local.set 2
          end
          block  ;; label = @4
            local.get 1
            i32.eqz
            br_if 0 (;@4;)
            local.get 0
            call 16
          end
          local.get 0
          i32.load offset=56
          local.tee 0
          br_if 0 (;@3;)
        end
      end
      call 20
    end
    local.get 2)
  (func (;22;) (type 3) (param i32) (result i32)
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
    block  ;; label = @1
      local.get 0
      i32.load offset=4
      local.tee 1
      local.get 0
      i32.load offset=8
      local.tee 2
      i32.ge_u
      br_if 0 (;@1;)
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
  (export "run" (func 3))
  (export "main" (func 5))
  (export "_start" (func 6))
  (export "__errno_location" (func 7))
  (export "fflush" (func 21))
  (export "stackSave" (func 12))
  (export "stackRestore" (func 13))
  (export "stackAlloc" (func 14))
  (export "__data_end" (global 1))
  (elem (;0;) (i32.const 1) func 1))
