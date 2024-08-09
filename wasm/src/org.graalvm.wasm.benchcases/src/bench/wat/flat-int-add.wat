;;
;; Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
  (type $int_func (func (result i32)))
  (type $proc (func))
  (type $teardown_func (func (param i32)))

  (global $iterations i32 (i32.const 10000000))

  (memory $memory (export "memory") 0)

  (func (export "benchmarkSetupEach") (type $proc))

  (func (export "benchmarkTeardownEach") (type $teardown_func))

  (func (export "benchmarkRun") (type $int_func)
    (local $i i32)
    (local $u_x i32)
    (local $u_y i32)
    (local $u_z i32)
    (local $u_w i32)
    (local $v_x i32)
    (local $v_y i32)
    (local $v_z i32)
    (local $v_w i32)
    (local $tmp_x i32)
    (local $tmp_y i32)
    (local $tmp_z i32)
    (local $tmp_w i32)
    (local.set $u_x (i32.const 2))
    (local.set $u_y (i32.const 4))
    (local.set $u_z (i32.const 8))
    (local.set $u_w (i32.const 16))
    (local.set $v_x (i32.const 3))
    (local.set $v_y (i32.const 5))
    (local.set $v_z (i32.const 7))
    (local.set $v_w (i32.const 11))

    (loop $bench_loop
      (local.set $tmp_x (local.get $v_x))
      (local.set $tmp_y (local.get $v_y))
      (local.set $tmp_z (local.get $v_z))
      (local.set $tmp_w (local.get $v_w))

      ;; Perform four int additions
      (local.set $v_x (i32.add (local.get $u_x) (local.get $v_x)))
      (local.set $v_y (i32.add (local.get $u_y) (local.get $v_y)))
      (local.set $v_z (i32.add (local.get $u_z) (local.get $v_z)))
      (local.set $v_w (i32.add (local.get $u_w) (local.get $v_w)))

      (local.set $u_x (local.get $tmp_x))
      (local.set $u_y (local.get $tmp_y))
      (local.set $u_z (local.get $tmp_z))
      (local.set $u_w (local.get $tmp_w))

      ;; Increment loop counter and exit loop
      (local.set $i (i32.add (local.get $i) (i32.const 1)))
      (br_if $bench_loop (i32.lt_s (local.get $i) (global.get $iterations)))
    )

    (i32.and (i32.and (i32.ne (local.get $v_x) (i32.const 0))
                      (i32.ne (local.get $v_y) (i32.const 0)))
             (i32.and (i32.ne (local.get $v_z) (i32.const 0))
                      (i32.ne (local.get $v_w) (i32.const 0))))
  )
)
