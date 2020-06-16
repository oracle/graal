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
  (type (;1;) (func (param i32) (result i32)))
  (import "man-in-the-middle" "functiontable" (table (;0;) 6 6 funcref))
  (func (export "_main") (type 0)
    (local i32)
    ;; Multiply 1 by 2.
    i32.const 1
    i32.const 0
    call_indirect (type 1)
    local.set 0

    ;; Multiply by 3.
    local.get 0
    i32.const 1
    call_indirect (type 1)
    local.set 0

    ;; Multiply by 4.
    local.get 0
    i32.const 2
    call_indirect (type 1)
    local.set 0

    ;; Add 1.
    ;; We will then have 25.
    local.get 0
    i32.const 3
    call_indirect (type 1)
    local.set 0

    ;; Indirectly call multiplication by 3.
    ;; We will then have 75.
    local.get 0
    i32.const 4
    call_indirect (type 1)
    local.set 0

    ;; Subtract 7.
    ;; Result should be 68.
    local.get 0
    i32.const 5
    call_indirect (type 1)
  )
  (func (type 1)
    local.get 0
    i32.const 7
    i32.sub
  )
  (elem (;0;) (i32.const 5) 1)
)
