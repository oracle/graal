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
    (import "wasi_snapshot_preview1" "path_open" (func $path_open (param i32 i32 i32 i32 i32 i64 i64 i32 i32) (result i32)))
    (import "wasi_snapshot_preview1" "fd_filestat_get" (func $fd_filestat_get (param i32 i32) (result i32)))
    (import "wasi_snapshot_preview1" "fd_close" (func $fd_close (param i32) (result i32)))
    (memory 1)
    (data (i32.const 0) "file.txt")
    (export "memory" (memory 0))
    (func (export "_main") (result i32) (local $ret i32)
        ;; Open file "file.txt" in pre-opened directory 3
        (local.set $ret
            (call $path_open
                (i32.const 3)       ;; pre-opened "test" directory fd
                (i32.const 0)       ;; dirflags
                (i32.const 0)       ;; pointer to path "file.txt"
                (i32.const 8)       ;; path length
                (i32.const 0)       ;; oflags
                (i64.const 2097152) ;; rights base (only fd_fdstat_get set)
                (i64.const 2097152) ;; rights inherting (only fd_fdstat_get set)
                (i32.const 1)       ;; fdflags (append flag set)
                (i32.const 8)       ;; fd address
            )
        )
        ;; Exit in case of error
        (if (i32.ne (local.get $ret) (i32.const 0)) (then (return (local.get $ret))))

        ;; Load filestat struct at address 12
        (local.set $ret (call $fd_filestat_get (i32.load (i32.const 8)) (i32.const 12)))
        ;; Exit in case of error
        (if (i32.ne (local.get $ret) (i32.const 0)) (then (return (local.get $ret))))

        ;; Check that filetype is regular_file
        (if (i32.ne (i32.load8_u (i32.const 28)) (i32.const 4)) (then (return (i32.const -1))))
        ;; Check that nlink equal 1
        ;; GR-29297: this attribute is currently not supported on non-Unix platforms
        ;; (if (i64.ne (i64.load (i32.const 36)) (i64.const 1)) (then (return (i32.const -2))))
        ;; Check that size equal 17
        (if (i64.ne (i64.load (i32.const 44)) (i64.const 17)) (then (return (i32.const -3))))
        ;; Check that timestamp fields (atim, mtime, ctime) are > 1612883662 (2021-02-09T15:14:22Z)
        (if (i64.le_u (i64.load (i32.const 52)) (i64.const 1612883662)) (then (return (i32.const -4))))
        (if (i64.le_u (i64.load (i32.const 60)) (i64.const 1612883662)) (then (return (i32.const -5))))
        ;; GR-29297: this attribute is currently not supported on non-Unix platforms
        ;; (if (i64.le_u (i64.load (i32.const 68)) (i64.const 1612883662)) (then (return (i32.const -6))))

        ;; Free opened file
        (local.set $ret (call $fd_close (i32.load (i32.const 8))))
        ;; Exit in case of error
        (if (i32.ne (local.get $ret) (i32.const 0)) (then (return (local.get $ret))))

        ;; Clear random fd number so that this test is deterministic
        (i32.store (i32.const 8) (i32.const 0))
        ;; Success
        (i32.const 0)
    )
)
