;;
;; Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
    (import "wasi_snapshot_preview1" "fd_prestat_dir_name" (func $fd_prestat_dir_name (param i32 i32 i32) (result i32)))
    (import "wasi_snapshot_preview1" "fd_write" (func $fd_write (param i32 i32 i32 i32) (result i32)))
    (memory 1)
    (export "memory" (memory 0))
    ;; iovec { buf = 8, buf_len = 4}
    (data (i32.const 0) "\08\00\00\00\04\00\00\00")
    (func (export "_main") (result i32) (local $ret i32)
        ;; Open file "file.txt" in pre-opened directory 3
        (local.set $ret
            (call $fd_prestat_dir_name
                (i32.const 3) ;; pre-opened "test" directory fd
                (i32.const 8) ;; address at which to load the path
                (i32.const 4) ;; number of bytes to load
            )
        )
        ;; Exit in case of error
        (if (i32.ne (local.get $ret) (i32.const 0)) (then (return (local.get $ret))))

        ;; Write path to stdout
        (local.set $ret
            (call $fd_write
                (i32.const 1)  ;; fd 1 (stdout)
                (i32.const 0)  ;; iovec at address 0
                (i32.const 1)  ;; iovec size
                (i32.const 12) ;; address at which to write the number of bytes written
            )
        )
        ;; Exit in case of error
        (if (i32.ne (local.get $ret) (i32.const 0)) (then (return (local.get $ret))))
        ;; Number of bytes written should equal 4
        (if (i32.ne (i32.load (i32.const 12)) (i32.const 4)) (then (return (i32.const -1))))

        ;; Success
        (i32.const 0)
    )
)
