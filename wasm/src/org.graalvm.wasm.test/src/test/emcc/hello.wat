;;
;; Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
  (type (;0;) (func (param i32 i32 i32) (result i32)))
  (type (;1;) (func (param i32) (result i32)))
  (type (;2;) (func (param i32 i64 i32) (result i64)))
  (type (;3;) (func (param i32)))
  (type (;4;) (func (param i32 i32 i32 i32) (result i32)))
  (type (;5;) (func (result i32)))
  (type (;6;) (func (param i32 i32) (result i32)))
  (type (;7;) (func))
  (import "env" "abort" (func (;0;) (type 3)))
  (import "env" "_emscripten_memcpy_big" (func (;1;) (type 0)))
  (import "env" "___wasi_fd_write" (func (;2;) (type 4)))
  (import "env" "memory" (memory (;0;) 256 256))
  (import "env" "table" (table (;0;) 6 6 funcref))
  (func (;3;) (type 1) (param i32) (result i32)
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
  (func (;4;) (type 6) (param i32 i32) (result i32)
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
        call 3
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
        i32.const 1
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
          i32.const 1
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
      call 12
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
  (func (;5;) (type 2) (param i32 i64 i32) (result i64)
    i64.const 0)
  (func (;6;) (type 0) (param i32 i32 i32) (result i32)
    (local i32 i32 i32 i32 i32 i32 i32)
    global.get 1
    local.set 6
    global.get 1
    i32.const 32
    i32.add
    global.set 1
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
          call 2
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
    global.set 1
    local.get 2)
  (func (;7;) (type 1) (param i32) (result i32)
    i32.const 0)
  (func (;8;) (type 5) (result i32)

    ;; Initialization code -- handwritten
    i32.const 3952
    i32.const 5246864
    i32.store
    ;; Initialization code -- handwritten

    call 16
    i32.const 0)
  (func (;9;) (type 2) (param i32 i64 i32) (result i64)
    i32.const 2
    call 0
    i64.const 0)
  (func (;10;) (type 0) (param i32 i32 i32) (result i32)
    i32.const 1
    call 0
    i32.const 0)
  (func (;11;) (type 1) (param i32) (result i32)
    i32.const 0
    call 0
    i32.const 0)
  (func (;12;) (type 0) (param i32 i32 i32) (result i32)
    (local i32 i32 i32)
    local.get 2
    i32.const 8192
    i32.ge_s
    if  ;; label = @1
      local.get 0
      local.get 1
      local.get 2
      call 1
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
  (func (;13;) (type 6) (param i32 i32) (result i32)
    (local i32)
    local.get 0
    local.set 2
    local.get 1
    i32.load offset=76
    drop
    local.get 2
    local.get 1
    call 4
    local.tee 1
    local.get 0
    local.get 1
    local.get 2
    i32.ne
    select)
  (func (;14;) (type 5) (result i32)
    (local i32 i32 i32)
    i32.const 1172
    local.set 0
    loop  ;; label = @1
      local.get 0
      i32.const 4
      i32.add
      local.set 1
      local.get 0
      i32.load
      local.tee 2
      i32.const -2139062144  ;; binary: 01111111 01111111 01111111 10000000
      i32.and
      i32.const -2139062144  ;; binary: 01111111 01111111 01111111 10000000
      i32.xor
      local.get 2
      i32.const -16843009  ;; binary: 00000001 00000001 00000001 00000001
      i32.add
      i32.and
      i32.eqz
      if  ;; label = @2
        local.get 1
        local.set 0
        br 1 (;@1;)
      end
    end
    local.get 2
    i32.const 255
    i32.and
    if  ;; label = @1
      loop  ;; label = @2
        local.get 0
        i32.const 1
        i32.add
        local.tee 0
        i32.load8_s
        br_if 0 (;@2;)
      end
    end
    local.get 0
    i32.const 1172
    i32.sub)
  (func (;15;) (type 1) (param i32) (result i32)
    (local i32 i32 i32 i32)
    global.get 1
    local.set 2
    global.get 1
    i32.const 16
    i32.add
    global.set 1
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
        call 3
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
      i32.const 1
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
    global.set 1
    local.get 1)
  (func (;16;) (type 7)
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
    call 14
    local.tee 1
    local.get 1
    local.get 0
    call 13
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
        call 15
      end
    end
    drop)
  (func (;17;) (type 1) (param i32) (result i32)
    (local i32 i32)
    global.get 1
    local.set 2
    local.get 0
    global.get 1
    i32.add
    global.set 1
    global.get 1
    i32.const 15
    i32.add
    i32.const -16
    i32.and
    global.set 1
    local.get 2)
  (global (;0;) i32 (i32.const 0))
  (global (;1;) (mut i32) (i32.const 3984))
  (export "_main" (func 8))
  (export "stackAlloc" (func 17))
  (elem (;0;) (i32.const 0) 11 7 10 6 9 5)
  (data (;0;) (i32.const 1024) "\05")
  (data (;1;) (i32.const 1036) "\01")
  (data (;2;) (i32.const 1060) "\01\00\00\00\01\00\00\00\b8\04\00\00\00\04")
  (data (;3;) (i32.const 1084) "\01")
  (data (;4;) (i32.const 1099) "\0a\ff\ff\ff\ff")
  (data (;5;) (i32.const 1169) "\04\00\00Hello world!"))
