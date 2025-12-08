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
(module $sieve.wasm
  (type (;0;) (func (param i32)))
  (type (;1;) (func))
  (type (;2;) (func (result f64)))
  (type (;3;) (func (result i32)))
  (import "wasi_snapshot_preview1" "proc_exit" (func $__imported_wasi_snapshot_preview1_proc_exit (type 0)))
  (func $__wasm_call_ctors (type 1))
  (func $_start (type 1)
    (local i32)
    block  ;; label = @1
      block  ;; label = @2
        global.get $GOT.data.internal.__memory_base
        i32.const 1028
        i32.add
        i32.load
        br_if 0 (;@2;)
        global.get $GOT.data.internal.__memory_base
        i32.const 1028
        i32.add
        i32.const 1
        i32.store
        call $__wasi_init_tp
        call $__wasm_call_ctors
        call $__original_main
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
  (func $OutlierRemovalAverageSummary (type 1))
  (func $OutlierRemovalAverageSummaryLowerThreshold (type 2) (result f64)
    f64.const 0x0p+0 (;=0;))
  (func $OutlierRemovalAverageSummaryUpperThreshold (type 2) (result f64)
    f64.const 0x1p-1 (;=0.5;))
  (func $setup (type 1))
  (func $run (type 3) (result i32)
    (local i32 i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 2400016
    i32.sub
    local.tee 0
    global.set $__stack_pointer
    local.get 0
    local.set 1
    i32.const 2
    local.set 2
    block  ;; label = @1
      loop  ;; label = @2
        local.get 1
        i32.const 32
        i32.add
        local.tee 3
        local.get 2
        i32.const 6
        i32.add
        i32.store
        local.get 1
        i32.const 28
        i32.add
        local.get 2
        i32.const 5
        i32.add
        i32.store
        local.get 1
        i32.const 24
        i32.add
        local.get 2
        i32.const 4
        i32.add
        i32.store
        local.get 1
        i32.const 20
        i32.add
        local.get 2
        i32.const 3
        i32.add
        i32.store
        local.get 1
        i32.const 16
        i32.add
        local.get 2
        i32.const 2
        i32.add
        i32.store
        local.get 1
        i32.const 12
        i32.add
        local.get 2
        i32.const 1
        i32.add
        i32.store
        local.get 1
        i32.const 8
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
          local.set 4
          loop  ;; label = @4
            block  ;; label = @5
              local.get 0
              local.get 4
              i32.const 2
              i32.shl
              i32.add
              local.tee 1
              i32.load
              i32.eqz
              br_if 0 (;@5;)
              i32.const 3
              local.set 2
              loop  ;; label = @6
                local.get 1
                i32.load
                local.get 2
                i32.const -1
                i32.add
                i32.mul
                local.tee 3
                i32.const 600000
                i32.gt_s
                br_if 1 (;@5;)
                local.get 0
                local.get 3
                i32.const 2
                i32.shl
                i32.add
                i32.const 0
                i32.store
                local.get 1
                i32.load
                local.get 2
                i32.mul
                local.tee 3
                i32.const 600000
                i32.gt_s
                br_if 1 (;@5;)
                local.get 0
                local.get 3
                i32.const 2
                i32.shl
                i32.add
                i32.const 0
                i32.store
                local.get 2
                i32.const 2
                i32.add
                local.tee 2
                i32.const 600001
                i32.ne
                br_if 0 (;@6;)
              end
            end
            local.get 4
            i32.const 1
            i32.add
            local.tee 4
            i32.const 775
            i32.ne
            br_if 0 (;@4;)
          end
          i32.const 0
          local.set 2
          i32.const 0
          local.set 3
          loop  ;; label = @4
            local.get 3
            local.get 0
            local.get 2
            i32.add
            local.tee 1
            i32.const 8
            i32.add
            i32.load
            i32.const 0
            i32.ne
            i32.add
            local.get 1
            i32.const 12
            i32.add
            i32.load
            i32.const 0
            i32.ne
            i32.add
            local.get 1
            i32.const 16
            i32.add
            i32.load
            i32.const 0
            i32.ne
            i32.add
            local.set 3
            local.get 2
            i32.const 2399984
            i32.eq
            br_if 3 (;@1;)
            local.get 2
            i32.const 16
            i32.add
            local.set 2
            local.get 3
            local.get 1
            i32.const 20
            i32.add
            i32.load
            i32.const 0
            i32.ne
            i32.add
            local.set 3
            br 0 (;@4;)
          end
        end
        local.get 1
        i32.const 36
        i32.add
        local.get 2
        i32.store
        local.get 2
        i32.const 1
        i32.add
        local.set 2
        local.get 3
        local.set 1
        br 0 (;@2;)
      end
    end
    local.get 0
    i32.const 2400016
    i32.add
    global.set $__stack_pointer
    local.get 3)
  (func $__original_main (type 3) (result i32)
    (local i32 i32 i32 i32 i32)
    global.get $__stack_pointer
    i32.const 2400016
    i32.sub
    local.tee 0
    global.set $__stack_pointer
    i32.const 0
    local.set 1
    i32.const 2
    local.set 2
    loop (result i32)  ;; label = @1
      local.get 0
      local.get 1
      i32.add
      local.tee 3
      i32.const 32
      i32.add
      local.get 2
      i32.const 6
      i32.add
      i32.store
      local.get 3
      i32.const 28
      i32.add
      local.get 2
      i32.const 5
      i32.add
      i32.store
      local.get 3
      i32.const 24
      i32.add
      local.get 2
      i32.const 4
      i32.add
      i32.store
      local.get 3
      i32.const 20
      i32.add
      local.get 2
      i32.const 3
      i32.add
      i32.store
      local.get 3
      i32.const 16
      i32.add
      local.get 2
      i32.const 2
      i32.add
      i32.store
      local.get 3
      i32.const 12
      i32.add
      local.get 2
      i32.const 1
      i32.add
      i32.store
      local.get 3
      i32.const 8
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
        local.set 4
        loop  ;; label = @3
          block  ;; label = @4
            local.get 0
            local.get 4
            i32.const 2
            i32.shl
            i32.add
            local.tee 3
            i32.load
            i32.eqz
            br_if 0 (;@4;)
            i32.const 3
            local.set 2
            loop  ;; label = @5
              local.get 3
              i32.load
              local.get 2
              i32.const -1
              i32.add
              i32.mul
              local.tee 1
              i32.const 600000
              i32.gt_s
              br_if 1 (;@4;)
              local.get 0
              local.get 1
              i32.const 2
              i32.shl
              i32.add
              i32.const 0
              i32.store
              local.get 3
              i32.load
              local.get 2
              i32.mul
              local.tee 1
              i32.const 600000
              i32.gt_s
              br_if 1 (;@4;)
              local.get 0
              local.get 1
              i32.const 2
              i32.shl
              i32.add
              i32.const 0
              i32.store
              local.get 2
              i32.const 2
              i32.add
              local.tee 2
              i32.const 600001
              i32.ne
              br_if 0 (;@5;)
            end
          end
          local.get 4
          i32.const 1
          i32.add
          local.tee 4
          i32.const 775
          i32.ne
          br_if 0 (;@3;)
        end
        i32.const 0
        local.set 2
        i32.const 0
        local.set 1
        block  ;; label = @3
          loop  ;; label = @4
            local.get 1
            local.get 0
            local.get 2
            i32.add
            local.tee 3
            i32.const 8
            i32.add
            i32.load
            i32.const 0
            i32.ne
            i32.add
            local.get 3
            i32.const 12
            i32.add
            i32.load
            i32.const 0
            i32.ne
            i32.add
            local.get 3
            i32.const 16
            i32.add
            i32.load
            i32.const 0
            i32.ne
            i32.add
            local.set 1
            local.get 2
            i32.const 2399984
            i32.eq
            br_if 1 (;@3;)
            local.get 2
            i32.const 16
            i32.add
            local.set 2
            local.get 1
            local.get 3
            i32.const 20
            i32.add
            i32.load
            i32.const 0
            i32.ne
            i32.add
            local.set 1
            br 0 (;@4;)
          end
        end
        local.get 0
        i32.const 2400016
        i32.add
        global.set $__stack_pointer
        local.get 1
        return
      end
      local.get 3
      i32.const 36
      i32.add
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
  (func $__wasi_init_tp (type 1)
    (local i32 i32)
    i32.const 0
    i32.const 1036
    i32.store offset=1036
    i32.const 4195456
    local.set 0
    block  ;; label = @1
      block  ;; label = @2
        i32.const 4195456
        i32.eqz
        br_if 0 (;@2;)
        i32.const 4195456
        i32.const 1152
        i32.sub
        local.set 1
        br 1 (;@1;)
      end
      global.get $__stack_pointer
      local.set 1
      i32.const 4195456
      i32.const 1144
      i32.sub
      i32.const 1024
      local.get 1
      i32.const 1024
      i32.gt_u
      local.tee 0
      select
      local.set 1
      i32.const 4195456
      i32.const 1024
      local.get 0
      select
      local.set 0
    end
    i32.const 56
    i32.const 0
    i32.store offset=1036
    i32.const 52
    local.get 1
    i32.store offset=1036
    i32.const 48
    local.get 0
    i32.store offset=1036
    i32.const 8
    i32.const 1036
    i32.store offset=1036
    i32.const 4
    i32.const 1036
    i32.store offset=1036
    i32.const 12
    i32.const 0
    i32.load offset=1032
    i32.store offset=1036
    i32.const 0
    local.get 1
    i32.const 8388608
    local.get 1
    i32.const 8388608
    i32.lt_u
    select
    i32.store offset=1024)
  (func $dummy (type 1))
  (func $__wasm_call_dtors (type 1)
    call $dummy
    call $dummy)
  (table (;0;) 1 1 funcref)
  (memory (;0;) 65)
  (global $__stack_pointer (mut i32) (i32.const 4195456))
  (global $GOT.data.internal.__memory_base i32 (i32.const 0))
  (export "memory" (memory 0))
  (export "_start" (func $_start))
  (export "OutlierRemovalAverageSummary" (func $OutlierRemovalAverageSummary))
  (export "OutlierRemovalAverageSummaryLowerThreshold" (func $OutlierRemovalAverageSummaryLowerThreshold))
  (export "OutlierRemovalAverageSummaryUpperThreshold" (func $OutlierRemovalAverageSummaryUpperThreshold))
  (export "setup" (func $setup))
  (export "run" (func $run))
  (data $.data (i32.const 1024) "\00\00\02\00"))
