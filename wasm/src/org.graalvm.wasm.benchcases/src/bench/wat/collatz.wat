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

(;
pub fn collatz_steps(mut n: u64) -> u64 {
  let mut counter = 0;
  while n != 1 {
    if n % 2 == 0 {
      n /= 2;
    } else {
      n = 3 * n + 1;
    }
    counter += 1;
  }
  counter
}
;)

(module
  (type $int_func (func (result i32)))
  (type $setup_func (func))
  (type $teardown_func (func (param i32)))

  (global $iterations i64 (i64.const 670617279))

  (memory $memory (export "memory") 0)

  (func (export "benchmarkSetupEach") (type $setup_func))

  (func (export "benchmarkTeardownEach") (type $teardown_func))

  (func (export "benchmarkRun") (type $int_func)
    global.get $iterations
    call $collatz_steps
    i32.wrap_i64
  )

  (func $collatz_steps (export "collatz_steps") (param i64) (result i64)
    (local i64)
    i64.const 0
    local.set 1
    block
      local.get 0
      i64.const 1
      i64.eq
      br_if 0
      i64.const 0
      local.set 1
      loop $continue
        local.get 1
        i64.const 1
        i64.add
        local.set 1
        local.get 0
        i64.const 1
        i64.shr_u
        local.get 0
        i64.const 3
        i64.mul
        i64.const 1
        i64.add
        local.get 0
        i64.const 1
        i64.and
        i64.eqz
        select
        local.tee 0
        i64.const 1
        i64.ne
        br_if $continue
      end
    end
    local.get 1
  )
)
