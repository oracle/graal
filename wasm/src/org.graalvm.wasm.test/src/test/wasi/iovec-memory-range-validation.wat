;;
;; Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
    (import "wasi_snapshot_preview1" "fd_read" (func $fd_read (param i32 i32 i32 i32) (result i32)))
    (import "wasi_snapshot_preview1" "fd_write" (func $fd_write (param i32 i32 i32 i32) (result i32)))
    (memory 1)
    (export "memory" (memory 0))
    (func (export "_main") (result i32) (local $ret i32)
        ;; fd_read validates all iovecs before reading. An invalid second iovec
        ;; must not partially fill the first buffer.
        (i32.store (i32.const 64) (i32.const 128))
        (i32.store (i32.const 68) (i32.const 1))
        (i32.store (i32.const 72) (i32.const 0))
        (i32.store (i32.const 76) (i32.const -1))
        (i32.store8 (i32.const 128) (i32.const 0))

        (local.set $ret
            (call $fd_read
                (i32.const 0)  ;; stdin
                (i32.const 64) ;; iovs
                (i32.const 2)  ;; iovs_len
                (i32.const 32) ;; output size address
            )
        )
        (if (i32.eq (local.get $ret) (i32.const 0)) (then (return (i32.const -1))))
        (if (i32.ne (i32.load8_u (i32.const 128)) (i32.const 0)) (then (return (i32.const -2))))

        ;; Iovec with buf_len = u32::MAX. This is a WASI u32 length, not a
        ;; negative Java length, and it is invalid for one-page memory.
        (i32.store (i32.const 64) (i32.const 0))
        (i32.store (i32.const 68) (i32.const -1))

        (local.set $ret
            (call $fd_write
                (i32.const 1)  ;; stdout
                (i32.const 64) ;; iovs
                (i32.const 1)  ;; iovs_len
                (i32.const 32) ;; output size address
            )
        )
        (if (i32.eq (local.get $ret) (i32.const 0)) (then (return (i32.const -3))))

        (local.set $ret
            (call $fd_read
                (i32.const 0)  ;; stdin
                (i32.const 64) ;; iovs
                (i32.const 1)  ;; iovs_len
                (i32.const 32) ;; output size address
            )
        )
        (if (i32.eq (local.get $ret) (i32.const 0)) (then (return (i32.const -4))))

        ;; iovs_len is a WASI u32. 0xffffffff is a huge iovec array length,
        ;; not a negative Java count, and it is invalid for one-page memory.
        (local.set $ret
            (call $fd_write
                (i32.const 1)  ;; stdout
                (i32.const 64) ;; iovs
                (i32.const -1) ;; iovs_len: u32::MAX
                (i32.const 32) ;; output size address
            )
        )
        (if (i32.eq (local.get $ret) (i32.const 0)) (then (return (i32.const -5))))

        (i32.store8 (i32.const 128) (i32.const 0))
        (local.set $ret
            (call $fd_read
                (i32.const 0)  ;; stdin
                (i32.const 64) ;; iovs
                (i32.const -1) ;; iovs_len: u32::MAX
                (i32.const 32) ;; output size address
            )
        )
        (if (i32.eq (local.get $ret) (i32.const 0)) (then (return (i32.const -6))))
        (if (i32.ne (i32.load8_u (i32.const 128)) (i32.const 0)) (then (return (i32.const -7))))

        (i32.const 0)
    )
)
