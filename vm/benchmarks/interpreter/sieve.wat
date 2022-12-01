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
  (type (;1;) (func))
  (type (;2;) (func (result i32)))
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
  (func $run (type 2) (result i32)
    (local i32 i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 2400016
    i32.sub
    local.tee 0
    global.set $__stack_pointer
    local.get 0
    i32.const 36
    i32.add
    local.set 1
    i32.const 2
    local.set 2
    block  ;; label = @1
      loop  ;; label = @2
        local.get 1
        i32.const -4
        i32.add
        local.get 2
        i32.const 6
        i32.add
        i32.store
        local.get 1
        i32.const -8
        i32.add
        local.get 2
        i32.const 5
        i32.add
        i32.store
        local.get 1
        i32.const -12
        i32.add
        local.get 2
        i32.const 4
        i32.add
        i32.store
        local.get 1
        i32.const -16
        i32.add
        local.get 2
        i32.const 3
        i32.add
        i32.store
        local.get 1
        i32.const -20
        i32.add
        local.get 2
        i32.const 2
        i32.add
        i32.store
        local.get 1
        i32.const -24
        i32.add
        local.get 2
        i32.const 1
        i32.add
        i32.store
        local.get 1
        i32.const -28
        i32.add
        local.get 2
        i32.store
        block  ;; label = @3
          local.get 2
          i32.const 7
          i32.add
          local.tee 2
          i32.const 600001
          i32.ne
          br_if 0 (;@3;)
          i32.const 2
          local.set 3
          loop  ;; label = @4
            block  ;; label = @5
              local.get 0
              local.get 3
              i32.const 2
              i32.shl
              i32.add
              local.tee 4
              i32.load
              local.tee 1
              i32.eqz
              br_if 0 (;@5;)
              local.get 1
              i32.const 300000
              i32.gt_s
              br_if 0 (;@5;)
              local.get 1
              i32.const 1
              i32.shl
              local.set 2
              i32.const 4
              local.set 1
              loop  ;; label = @6
                local.get 0
                local.get 2
                i32.const 2
                i32.shl
                i32.add
                i32.const 0
                i32.store
                local.get 4
                i32.load
                local.get 1
                i32.const -1
                i32.add
                i32.mul
                local.tee 2
                i32.const 600000
                i32.gt_s
                br_if 1 (;@5;)
                local.get 0
                local.get 2
                i32.const 2
                i32.shl
                i32.add
                i32.const 0
                i32.store
                local.get 1
                i32.const 600000
                i32.eq
                br_if 1 (;@5;)
                local.get 4
                i32.load
                local.get 1
                i32.mul
                local.set 2
                local.get 1
                i32.const 2
                i32.add
                local.set 1
                local.get 2
                i32.const 600001
                i32.lt_s
                br_if 0 (;@6;)
              end
            end
            local.get 3
            i32.const 1
            i32.add
            local.tee 3
            i32.const 775
            i32.ne
            br_if 0 (;@4;)
          end
          i32.const 0
          local.set 1
          i32.const 0
          local.set 4
          loop  ;; label = @4
            local.get 4
            local.get 0
            local.get 1
            i32.add
            local.tee 2
            i32.const 8
            i32.add
            i32.load
            i32.const 0
            i32.ne
            i32.add
            local.get 2
            i32.const 12
            i32.add
            i32.load
            i32.const 0
            i32.ne
            i32.add
            local.get 2
            i32.const 16
            i32.add
            i32.load
            i32.const 0
            i32.ne
            i32.add
            local.set 4
            local.get 1
            i32.const 2399984
            i32.eq
            br_if 3 (;@1;)
            local.get 1
            i32.const 16
            i32.add
            local.set 1
            local.get 4
            local.get 2
            i32.const 20
            i32.add
            i32.load
            i32.const 0
            i32.ne
            i32.add
            local.set 4
            br 0 (;@4;)
          end
        end
        local.get 1
        local.get 2
        i32.store
        local.get 2
        i32.const 1
        i32.add
        local.set 2
        local.get 1
        i32.const 32
        i32.add
        local.set 1
        br 0 (;@2;)
      end
    end
    local.get 0
    i32.const 2400016
    i32.add
    global.set $__stack_pointer
    local.get 4)
  (func $__original_main (type 2) (result i32)
    (local i32 i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 2400016
    i32.sub
    local.tee 0
    global.set $__stack_pointer
    local.get 0
    i32.const 36
    i32.add
    local.set 1
    i32.const 2
    local.set 2
    loop (result i32)  ;; label = @1
      local.get 1
      i32.const -4
      i32.add
      local.get 2
      i32.const 6
      i32.add
      i32.store
      local.get 1
      i32.const -8
      i32.add
      local.get 2
      i32.const 5
      i32.add
      i32.store
      local.get 1
      i32.const -12
      i32.add
      local.get 2
      i32.const 4
      i32.add
      i32.store
      local.get 1
      i32.const -16
      i32.add
      local.get 2
      i32.const 3
      i32.add
      i32.store
      local.get 1
      i32.const -20
      i32.add
      local.get 2
      i32.const 2
      i32.add
      i32.store
      local.get 1
      i32.const -24
      i32.add
      local.get 2
      i32.const 1
      i32.add
      i32.store
      local.get 1
      i32.const -28
      i32.add
      local.get 2
      i32.store
      block  ;; label = @2
        local.get 2
        i32.const 7
        i32.add
        local.tee 2
        i32.const 600001
        i32.ne
        br_if 0 (;@2;)
        i32.const 2
        local.set 3
        loop  ;; label = @3
          block  ;; label = @4
            local.get 0
            local.get 3
            i32.const 2
            i32.shl
            i32.add
            local.tee 4
            i32.load
            local.tee 1
            i32.eqz
            br_if 0 (;@4;)
            local.get 1
            i32.const 300000
            i32.gt_s
            br_if 0 (;@4;)
            local.get 1
            i32.const 1
            i32.shl
            local.set 2
            i32.const 4
            local.set 1
            loop  ;; label = @5
              local.get 0
              local.get 2
              i32.const 2
              i32.shl
              i32.add
              i32.const 0
              i32.store
              local.get 1
              i32.const -1
              i32.add
              local.get 4
              i32.load
              i32.mul
              local.tee 2
              i32.const 600000
              i32.gt_s
              br_if 1 (;@4;)
              local.get 0
              local.get 2
              i32.const 2
              i32.shl
              i32.add
              i32.const 0
              i32.store
              local.get 1
              i32.const 600000
              i32.eq
              br_if 1 (;@4;)
              local.get 1
              local.get 4
              i32.load
              i32.mul
              local.set 2
              local.get 1
              i32.const 2
              i32.add
              local.set 1
              local.get 2
              i32.const 600001
              i32.lt_s
              br_if 0 (;@5;)
            end
          end
          local.get 3
          i32.const 1
          i32.add
          local.tee 3
          i32.const 775
          i32.ne
          br_if 0 (;@3;)
        end
        i32.const 0
        local.set 1
        i32.const 0
        local.set 4
        block  ;; label = @3
          loop  ;; label = @4
            local.get 4
            local.get 0
            local.get 1
            i32.add
            local.tee 2
            i32.const 8
            i32.add
            i32.load
            i32.const 0
            i32.ne
            i32.add
            local.get 2
            i32.const 12
            i32.add
            i32.load
            i32.const 0
            i32.ne
            i32.add
            local.get 2
            i32.const 16
            i32.add
            i32.load
            i32.const 0
            i32.ne
            i32.add
            local.set 4
            local.get 1
            i32.const 2399984
            i32.eq
            br_if 1 (;@3;)
            local.get 1
            i32.const 16
            i32.add
            local.set 1
            local.get 4
            local.get 2
            i32.const 20
            i32.add
            i32.load
            i32.const 0
            i32.ne
            i32.add
            local.set 4
            br 0 (;@4;)
          end
        end
        local.get 0
        i32.const 2400016
        i32.add
        global.set $__stack_pointer
        local.get 4
        return
      end
      local.get 1
      local.get 2
      i32.store
      local.get 2
      i32.const 1
      i32.add
      local.set 2
      local.get 1
      i32.const 32
      i32.add
      local.set 1
      br 0 (;@1;)
    end)
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
  (func $run.command_export (type 2) (result i32)
    call $run
    call $__wasm_call_dtors)
  (table (;0;) 1 1 funcref)
  (memory (;0;) 65)
  (global $__stack_pointer (mut i32) (i32.const 4195328))
  (export "memory" (memory 0))
  (export "_start" (func $_start.command_export))
  (export "run" (func $run.command_export)))
