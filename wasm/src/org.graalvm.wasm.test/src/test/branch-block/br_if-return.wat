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
  (type (;0;) (func (result i32)))
  (type (;1;) (func (param i32 i32 i32) (result i32)))
  (memory 1 1)
  (func (;0;) (type 1) (param i32 i32 i32) (result i32)
    (local i32 i32)
    local.get 2
    if (result i32)  ;; label = @1
      loop  ;; label = @2
        local.get 1
        i32.load8_s
        local.tee 4
        local.get 0
        i32.load8_s
        local.tee 3
        i32.eq
        if  ;; label = @3
          local.get 1
          i32.const 1
          i32.add
          local.set 1
          local.get 0
          i32.const 1
          i32.add
          local.set 0
          i32.const 0
          local.get 2
          i32.const -1
          i32.add
          local.tee 2
          i32.eqz
          br_if 3 (;@0;)
          drop
          br 1 (;@2;)
        end
      end
      local.get 3
      local.get 4
      i32.sub
    else
      i32.const 0
    end
    return
    unreachable)
  (func (;1;) (type 0) (result i32)
    i32.const 0
    i32.const 3
    i32.store
    i32.const 1
    i32.const 5
    i32.store
    i32.const 2
    i32.const 3
    i32.store
    i32.const 3
    i32.const 2
    i32.store
    i32.const 0
    i32.const 2
    i32.const 2
    call 0
  )
  (export "_main" (func 1))
)
