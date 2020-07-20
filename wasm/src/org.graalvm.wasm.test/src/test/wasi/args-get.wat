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
  (type (;0;) (func (result i32)))
  (type (;1;) (func (param i32 i32)))
  (import "wasi_snapshot_preview1" "args_get" (func $__wasi_args_get (type 1)))
  (import "wasi_snapshot_preview1" "args_sizes_get" (func $__wasi_args_sizes_get (type 1)))
  (memory (;0;) 4)
  (export "memory" (memory 0))
  (func (export "_main") (type 0)
    (local i32 i32 i32 i32)
    i32.const 0
    i32.const 4
    call $__wasi_args_sizes_get

    ;; Number of arguments.
    i32.const 0
    i32.load
    local.set 1
    local.get 1
    i32.const 3
    i32.ne
    if $B0
      ;; Return wrong value if the buffer size is incorrect.
      i32.const -1
      return
    end

    ;; Size of buffer is not required in this program, but should be 9.
    i32.const 4
    i32.load
    i32.const 9
    i32.ne
    if $B0
      ;; Return wrong value if the buffer size is incorrect.
      i32.const -2
      return
    end

    ;; Store the arguments in memory.
    i32.const 8
    i32.const 20
    call $__wasi_args_get

    ;; Iterate through the arguments, and sum-up their characters.
    ;; Declare the sum variable.
    i32.const 0
    local.set 2

    ;; Declare the iteration index.
    i32.const 0
    local.set 0
    loop $B0
      local.get 0
      local.get 1
      i32.lt_u
      if $B1
        ;; The argv start address.
        i32.const 8
        ;; Compute the offset from argv start.
        local.get 0
        i32.const 4
        i32.mul
        ;; Add these two together.
        i32.add

        ;; The previous address stores the address of the start of the argument string.
        ;; We load the address of the start of the string.
        i32.load
        local.set 3
        loop $B2
          local.get 3
          i32.load8_u
          if $B3
            local.get 3
            i32.load8_u
            local.get 2
            i32.add
            local.set 2
            local.get 3
            i32.const 1
            i32.add
            local.set 3
            br $B2
          end
        end
        local.get 0
        i32.const 1
        i32.add
        local.set 0
        br $B0
      end
    end
    local.get 2
  )
)
