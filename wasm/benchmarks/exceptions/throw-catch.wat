;;
;; Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
  ;; A fixed iteration count for the benchmark loop.
  (global $iterations i32 (i32.const 1000000))

  ;; Exception tag with a single i32 payload.
  (tag $E (param i32))

  ;; maybe_throw(x): returns x normally for 1/8 of inputs (when x & 7 == 0),
  ;; otherwise throws tag $E carrying payload x.
  (func $maybe_throw (param $x i32) (result i32)
    ;; If (x & 7) == 0, return x; else throw $E with payload x.
    (i32.eqz (i32.and (local.get $x) (i32.const 7)))
    if (result i32)
      (local.get $x)
    else
      (throw $E (local.get $x))
    end
  )

  ;; work(x): calls maybe_throw(x) and catches $E locally.
  ;; - Normal path: returns x
  ;; - Exception path: returns (payload + 1)
  (func $work (param $x i32) (result i32)
    (block $h (result i32)
      (try_table (result i32) (catch $E $h)
        (local.get $x)
        (return (call $maybe_throw))
      )
    )
    ;; Stack has the payload from the thrown exception.
    (i32.const 1)
    i32.add
  )

  ;; mid(x): calls maybe_throw(x) inside a try; on exception, transforms the
  ;; payload by adding 2 and rethrows a new $E with the transformed payload.
  (func $mid (param $x i32) (result i32)
    (block $h (result i32)
      (try_table (result i32) (catch $E $h)
        (return (call $maybe_throw (local.get $x)))
      )
    )
    ;; Transform payload and throw again with the same tag.
    (i32.const 2)
    i32.add
    (throw $E)
  )

  ;; outer(x): calls mid(x) and catches $E at an outer boundary.
  ;; - Normal path: returns x
  ;; - Exception path: returns (payload + 3) where payload came from mid's rethrow.
  (func $outer (param $x i32) (result i32)
    (block $h (result i32)
      (try_table (result i32) (catch $E $h)
        (call $mid (local.get $x))
      )
    )
    (i32.const 3)
    i32.add
  )

  ;; run(): the exported benchmark entry.
  ;; Loops for $iterations and alternates between the simple local-catch path (work)
  ;; and the nested rethrow path (outer). Aggregates results into a checksum to
  ;; ensure the optimizer cannot dead-code the calls.
  (func $run (export "run") (result i32)
    (local $i i32)
    (local $sum i32)
    (local $x i32)
  
    (global.get $iterations)
    (local.set $i)
  
    (loop $L
      ;; Create a varying input argument from the loop index and the running sum.
      (local.set $x (i32.xor (local.get $i) (local.get $sum)))
  
      ;; Alternate between paths to exercise both local catch and nested rethrow.
      (i32.and (local.get $i) (i32.const 1))
      if (result i32)
        ;; Odd iteration: local catch.
        (call $work (local.get $x))
      else
        ;; Even iteration: nested rethrow then catch.
        (call $outer (local.get $x))
      end
  
      ;; Accumulate into checksum.
      (local.get $sum)
      i32.add
      (local.set $sum)
  
      ;; Decrement counter and loop until zero.
      (local.tee $i (i32.sub (local.get $i) (i32.const 1)))
      (br_if $L)
    )
  
    ;; Return checksum to prevent elimination.
    (local.get $sum)
  )
)