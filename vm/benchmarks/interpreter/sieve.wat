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
    local.tee 2
    global.set 0
    i32.const 2
    local.set 3
    i32.const 2
    local.set 0
    loop  ;; label = @1
      local.get 2
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
        local.get 2
        local.get 3
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
        local.set 1
        i32.const 2
        local.set 0
        loop  ;; label = @3
          local.get 2
          local.get 1
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
          local.tee 1
          i32.const 600001
          i32.lt_s
          br_if 0 (;@3;)
        end
      end
      local.get 3
      i32.const 1
      i32.add
      local.tee 3
      i32.const 775
      i32.ne
      br_if 0 (;@1;)
    end
    i32.const 0
    local.set 1
    i32.const 2
    local.set 0
    loop  ;; label = @1
      local.get 1
      local.get 2
      local.get 0
      i32.const 2
      i32.shl
      i32.add
      i32.load
      i32.const 0
      i32.ne
      i32.add
      local.set 1
      local.get 0
      i32.const 1
      i32.add
      local.tee 0
      i32.const 600001
      i32.ne
      br_if 0 (;@1;)
    end
    local.get 2
    i32.const 2400016
    i32.add
    global.set 0
    local.get 1)
  (func $main (type 3) (param i32 i32) (result i32)
    (local i32 i32 i32)
    global.get 0
    i32.const 2400016
    i32.sub
    local.tee 2
    global.set 0
    i32.const 2
    local.set 3
    i32.const 2
    local.set 0
    loop  ;; label = @1
      local.get 2
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
        local.get 2
        local.get 3
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
        local.set 1
        i32.const 2
        local.set 0
        loop  ;; label = @3
          local.get 2
          local.get 1
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
          local.tee 1
          i32.const 600001
          i32.lt_s
          br_if 0 (;@3;)
        end
      end
      local.get 3
      i32.const 1
      i32.add
      local.tee 3
      i32.const 775
      i32.ne
      br_if 0 (;@1;)
    end
    i32.const 0
    local.set 1
    i32.const 2
    local.set 0
    loop  ;; label = @1
      local.get 1
      local.get 2
      local.get 0
      i32.const 2
      i32.shl
      i32.add
      i32.load
      i32.const 0
      i32.ne
      i32.add
      local.set 1
      local.get 0
      i32.const 1
      i32.add
      local.tee 0
      i32.const 600001
      i32.ne
      br_if 0 (;@1;)
    end
    local.get 2
    i32.const 2400016
    i32.add
    global.set 0
    local.get 1)
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
    local.tee 2
    global.set 0
    i32.const 2
    local.set 3
    i32.const 2
    local.set 0
    loop  ;; label = @1
      local.get 2
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
        local.get 2
        local.get 3
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
        local.set 1
        i32.const 2
        local.set 0
        loop  ;; label = @3
          local.get 2
          local.get 1
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
          local.tee 1
          i32.const 600001
          i32.lt_s
          br_if 0 (;@3;)
        end
      end
      local.get 3
      i32.const 1
      i32.add
      local.tee 3
      i32.const 775
      i32.ne
      br_if 0 (;@1;)
    end
    i32.const 0
    local.set 1
    i32.const 2
    local.set 0
    loop  ;; label = @1
      local.get 1
      local.get 2
      local.get 0
      i32.const 2
      i32.shl
      i32.add
      i32.load
      i32.const 0
      i32.ne
      i32.add
      local.set 1
      local.get 0
      i32.const 1
      i32.add
      local.tee 0
      i32.const 600001
      i32.ne
      br_if 0 (;@1;)
    end
    local.get 2
    i32.const 2400016
    i32.add
    global.set 0
    local.get 1)
  (func $__wasm_call_ctors (type 0)
    nop)
  (memory (;0;) 256 256)
  (global (;0;) (mut i32) (i32.const 5244432))
  (export "memory" (memory 0))
  (export "run" (func $run))
  (export "main" (func $main))
  (export "_start" (func $_start))
  (data (;0;) (i32.const 1552) "\b0\06P"))
