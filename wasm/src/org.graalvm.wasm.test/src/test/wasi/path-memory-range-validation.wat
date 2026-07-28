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
    (import "wasi_snapshot_preview1" "fd_prestat_dir_name" (func $fd_prestat_dir_name (param i32 i32 i32) (result i32)))
    (import "wasi_snapshot_preview1" "fd_readdir" (func $fd_readdir (param i32 i32 i32 i64 i32) (result i32)))
    (import "wasi_snapshot_preview1" "path_symlink" (func $path_symlink (param i32 i32 i32 i32 i32) (result i32)))
    (import "wasi_snapshot_preview1" "path_readlink" (func $path_readlink (param i32 i32 i32 i32 i32 i32) (result i32)))
    (import "wasi_snapshot_preview1" "path_unlink_file" (func $path_unlink_file (param i32 i32 i32) (result i32)))
    (memory 1)
    (data (i32.const 0) "file.txt")
    (data (i32.const 16) "link")
    (export "memory" (memory 0))
    (func (export "_main") (result i32) (local $ret i32)
        ;; 0xffffffff is a WASI u32 length, not a negative Java length. These
        ;; ranges are invalid for one-page memory and must not return success.

        (local.set $ret
            (call $fd_prestat_dir_name
                (i32.const 3)     ;; pre-opened "test" directory fd
                (i32.const 65535) ;; output buffer
                (i32.const -1)    ;; output buffer length: u32::MAX
            )
        )
        (if (i32.eq (local.get $ret) (i32.const 0)) (then (return (i32.const -1))))

        (local.set $ret
            (call $fd_readdir
                (i32.const 3)     ;; pre-opened "test" directory fd
                (i32.const 65535) ;; output buffer
                (i32.const -1)    ;; output buffer length: u32::MAX
                (i64.const 0)     ;; cookie
                (i32.const 32)    ;; output size address
            )
        )
        (if (i32.eq (local.get $ret) (i32.const 0)) (then (return (i32.const -2))))

        ;; Create a symbolic link so path_readlink reaches its output buffer handling.
        (local.set $ret
            (call $path_symlink
                (i32.const 0)  ;; pointer to "file.txt"
                (i32.const 8)  ;; path length
                (i32.const 3)  ;; pre-opened "test" directory fd
                (i32.const 16) ;; pointer to "link"
                (i32.const 4)  ;; path length
            )
        )
        (if (i32.ne (local.get $ret) (i32.const 0)) (then (return (local.get $ret))))

        (local.set $ret
            (call $path_readlink
                (i32.const 3)     ;; pre-opened "test" directory fd
                (i32.const 16)    ;; pointer to "link"
                (i32.const 4)     ;; path length
                (i32.const 65535) ;; output buffer
                (i32.const -1)    ;; output buffer length: u32::MAX
                (i32.const 32)    ;; output size address
            )
        )
        (if (i32.eq (local.get $ret) (i32.const 0)) (then (return (i32.const -3))))

        ;; Remove symbolic link.
        (local.set $ret
            (call $path_unlink_file
                (i32.const 3)  ;; pre-opened "test" directory fd
                (i32.const 16) ;; pointer to "link"
                (i32.const 4)  ;; path length
            )
        )
        (if (i32.ne (local.get $ret) (i32.const 0)) (then (return (local.get $ret))))

        (i32.const 0)
    )
)
