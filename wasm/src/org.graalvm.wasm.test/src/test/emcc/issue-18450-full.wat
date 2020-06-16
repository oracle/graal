;;
;; Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
  (type (;1;) (func (param i32 i32 i32) (result i32)))
  (type (;2;) (func (param i32 f64 i32 i32 i32 i32) (result i32)))
  (type (;3;) (func (param i32 i32)))
  (type (;4;) (func (param i32 i64 i32) (result i64)))
  (type (;5;) (func (param i32)))
  (type (;6;) (func (param i32 i32 i32 i32) (result i32)))
  (type (;7;) (func (result i32)))
  (type (;8;) (func (param i32 i32) (result i32)))
  (type (;9;) (func (param i32 i32 i32 i32 i32 i32 i32)))
  (type (;10;) (func))
  (type (;11;) (func (param i32 i32 i32)))
  (type (;12;) (func (param i32 i32 i32 i32 i32) (result i32)))
  (type (;13;) (func (param i64 i32 i32) (result i32)))
  (type (;14;) (func (param i64 i32) (result i32)))
  (type (;15;) (func (param i32 i32 i32 i32 i32)))
  (type (;16;) (func (param f64 i32) (result f64)))
  (global (;0;) i32 (i32.const 0))
  (global (;1;) i32 (i32.const 5024))
  (global (;2;) (mut i32) (i32.const 5056))
  (func (;0;) (type 7) (result i32)
    i32.const 814
    call 1)
  (func (;1;) (type 0) (param i32) (result i32)
    (local i32)
    i32.const 3244
    i32.const 3244
    i32.load
    i32.const -1
    i32.add
    local.tee 0
    i32.store
    local.get 0
    if (result i32)  ;; label = @1
      i32.const 3240
      i32.load
      local.tee 1
      i32.const 1
      i32.shr_u
      i32.const 32767
      i32.and
      local.set 0
      local.get 1
      i32.const 1
      i32.and
      if (result i32)  ;; label = @2
        i32.const 3240
        local.get 0
        i32.const 53256
        i32.xor
        i32.store
        i32.const 6
        call 2
      else
        i32.const 3240
        local.get 0
        i32.store
        i32.const 5
        call 2
      end
    else
      call 3
    end)
  (func (;2;) (type 0) (param i32) (result i32)
    (local i32)
    local.get 0
    call 4
    local.tee 0
    if (result i32)  ;; label = @1
      local.get 0
      local.get 0
      i32.load offset=16
      i32.const 65531
      i32.and
      i32.store offset=16
      local.get 0
      i32.const 3232
      i32.load
      local.tee 1
      local.get 0
      i32.load offset=8
      local.get 1
      i32.load offset=8
      i32.gt_s
      select
    else
      i32.const 0
    end)
  (func (;3;) (type 7) (result i32)
    (local i32)
    i32.const 3224
    i32.const 3224
    i32.load
    i32.const 1
    i32.add
    i32.store
    i32.const 3232
    i32.load
    local.tee 0
    local.get 0
    i32.load offset=16
    i32.const 4
    i32.or
    i32.store offset=16
    local.get 0
    i32.load)
  (func (;4;) (type 0) (param i32) (result i32)
    (local i32 i32 i32 i32)
    global.get 2
    local.set 1
    global.get 2
    i32.const 16
    i32.add
    global.set 2
    local.get 1
    local.set 2
    block (result i32)  ;; label = @1
      block  ;; label = @2
        local.get 0
        i32.const 1
        i32.lt_s
        i32.const 1056
        i32.load
        local.get 0
        i32.lt_s
        i32.or
        br_if 0 (;@2;)
        local.get 0
        i32.const 2
        i32.shl
        i32.const 1056
        i32.add
        i32.load
        local.tee 3
        i32.eqz
        br_if 0 (;@2;)
        local.get 3
        br 1 (;@1;)
      end
      local.get 2
      local.get 0
      i32.store
      i32.const 2082
      local.get 2
      call 5
      i32.const 0
    end
    local.set 4
    local.get 1
    global.set 2
    local.get 4)
  (func (;5;) (type 3) (param i32 i32)
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
    local.get 2
    global.set 2)
  (export "_main" (func 0))
  (memory (;0;) 4)
  (export "memory" (memory 0))
  (data (;0;) (i32.const 1024) "0ABCDEFGHIJKLMNOPQRSTUVWXYZ\00\00\00\00\00\0a")
  (data (;1;) (i32.const 1104) "\11\00\0a\00\11\11\11\00\00\00\00\05\00\00\00\00\00\00\09\00\00\00\00\0b")
  (data (;2;) (i32.const 1136) "\11\00\0f\0a\11\11\11\03\0a\07\00\01\13\09\0b\0b\00\00\09\06\0b\00\00\0b\00\06\11\00\00\00\11\11\11")
  (data (;3;) (i32.const 1185) "\0b")
  (data (;4;) (i32.const 1194) "\11\00\0a\0a\11\11\11\00\0a\00\00\02\00\09\0b\00\00\00\09\00\0b\00\00\0b")
  (data (;5;) (i32.const 1243) "\0c")
  (data (;6;) (i32.const 1255) "\0c\00\00\00\00\0c\00\00\00\00\09\0c\00\00\00\00\00\0c\00\00\0c")
  (data (;7;) (i32.const 1301) "\0e")
  (data (;8;) (i32.const 1313) "\0d\00\00\00\04\0d\00\00\00\00\09\0e\00\00\00\00\00\0e\00\00\0e")
  (data (;9;) (i32.const 1359) "\10")
  (data (;10;) (i32.const 1371) "\0f\00\00\00\00\0f\00\00\00\00\09\10\00\00\00\00\00\10\00\00\10\00\00\12\00\00\00\12\12\12")
  (data (;11;) (i32.const 1426) "\12\00\00\00\12\12\12\00\00\00\00\00\00\09")
  (data (;12;) (i32.const 1475) "\0b")
  (data (;13;) (i32.const 1487) "\0a\00\00\00\00\0a\00\00\00\00\09\0b\00\00\00\00\00\0b\00\00\0b")
  (data (;14;) (i32.const 1533) "\0c")
  (data (;15;) (i32.const 1545) "\0c\00\00\00\00\0c\00\00\00\00\09\0c\00\00\00\00\00\0c\00\00\0c\00\000123456789ABCDEF\05")
  (data (;16;) (i32.const 1596) "\01")
  (data (;17;) (i32.const 1620) "\01\00\00\00\01\00\00\00\88\08\00\00\00\04")
  (data (;18;) (i32.const 1644) "\01")
  (data (;19;) (i32.const 1659) "\0a\ff\ff\ff\ff")
  (data (;20;) (i32.const 1728) "0\06\00\000\06")
  (data (;21;) (i32.const 1924) "\d8\0c")
  (data (;22;) (i32.const 1980) "iteration %d of %s: \09%ld\0a\00first %d warmup iterations %s: \00, \00%ld\00last %d iterations %s: \00### %s: %ld\0a\00\0aBad task id %d\0a\00incorrect\00richards\00-+   0X0x\00(null)\00-0X+0X 0X-0x+0x 0x\00inf\00INF\00nan\00NAN\00."))
