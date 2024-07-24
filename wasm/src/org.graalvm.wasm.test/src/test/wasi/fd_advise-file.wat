;;
;; Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
    (import "wasi_snapshot_preview1" "fd_advise" (func $fd_advise (param i32 i64 i64 i32) (result i32)))
    (import "wasi_snapshot_preview1" "fd_close" (func $fd_close (param i32) (result i32)))

    (memory 1)

    (data (i32.const 0) "file.txt")

    (export "memory" (memory 0))

    ;; Pre-opened temporary directory fd
    (global $directory_fd i32 (i32.const 3))

    ;; Memory location of file path
    (global $file_path_address i32 (i32.const 0))
    (global $file_path_length i32 (i32.const 8))

    ;; Address of opened file fd
    (global $file_fd_address i32 (i32.const 12))

    ;; Rights for path_open
    (global $all_rights i64 (i64.const 536870911))
    (global $advise_right i64 (i64.const 128))

    ;; Advice for fd_advise
    (global $sequential_access_advice i32 (i32.const 1))

    ;; Errno codes
    (global $errno_inval i32 (i32.const 28))
    (global $errno_badf i32 (i32.const 8))
    (global $errno_notcapable i32 (i32.const 76))

    (func (export "_main") (result i32) (local $ret i32)
        ;; Open file "file.txt" in pre-opened directory
        (local.set $ret
            (call $path_open
                (global.get $directory_fd)      ;; pre-opened "test" directory fd
                (i32.const 0)                   ;; dirflags
                (global.get $file_path_address) ;; pointer to path "file.txt"
                (global.get $file_path_length)  ;; path length
                (i32.const 0)                   ;; oflags
                (global.get $all_rights)        ;; rights base (all rights set)
                (global.get $all_rights)        ;; rights inherting (all rights set)
                (i32.const 0)                   ;; fdflags
                (global.get $file_fd_address)   ;; fd address
            )
        )
        ;; Exit in case of error
        (if (i32.ne (local.get $ret) (i32.const 0)) (then (return (local.get $ret))))

        ;; Valid advice
        (local.set $ret
            (call $fd_advise
                (i32.load (global.get $file_fd_address)) ;; "file.txt" fd
                (i64.const 0)                            ;; offset of advised region
                (i64.const 0)                            ;; length of advised region
                (global.get $sequential_access_advice)   ;; sequential access advice
            )
        )
        ;; Exit in case of error
        (if (i32.ne (local.get $ret) (i32.const 0)) (then (return (local.get $ret))))

        ;; Invalid advice
        (local.set $ret
            (call $fd_advise
                (i32.load (global.get $file_fd_address)) ;; "file.txt" fd
                (i64.const 0)                            ;; offset of advised region
                (i64.const 0)                            ;; length of advised region
                (i32.const 42)                           ;; invalid advice
            )
        )
        ;; Check that error code is inval
        (if (i32.ne (local.get $ret) (global.get $errno_inval)) (then (return (i32.const -1))))

        ;; Invalid length of advised region
        (local.set $ret
            (call $fd_advise
                (i32.load (global.get $file_fd_address)) ;; "file.txt" fd
                (i64.const 1)                            ;; offset of advised region
                (i64.const -1)                           ;; length of advised region (must be non-negative)
                (global.get $sequential_access_advice)   ;; sequential access advice
            )
        )
        ;; Check that error code is inval
        (if (i32.ne (local.get $ret) (global.get $errno_inval)) (then (return (i32.const -2))))

        ;; Invalid file descriptor
        (local.set $ret
            (call $fd_advise
                (i32.add (i32.load (global.get $file_fd_address)) (i32.const 1)) ;; invalid fd
                (i64.const 0)                                                    ;; offset of advised region
                (i64.const 0)                                                    ;; length of advised region
                (global.get $sequential_access_advice)                           ;; sequential access advice
            )
        )
        ;; Check that error code is badf
        (if (i32.ne (local.get $ret) (global.get $errno_badf)) (then (return (i32.const -3))))

        ;; Free opened file
        (local.set $ret (call $fd_close (i32.load (global.get $file_fd_address))))
        ;; Exit in case of error
        (if (i32.ne (local.get $ret) (i32.const 0)) (then (return (local.get $ret))))

        ;; Open file "file.txt" in pre-opened directory 3 without advise capabilities
        (local.set $ret
            (call $path_open
                (global.get $directory_fd)                                    ;; pre-opened "test" directory fd
                (i32.const 0)                                                 ;; dirflags
                (global.get $file_path_address)                               ;; pointer to path "file.txt"
                (global.get $file_path_length)                                ;; path length
                (i32.const 0)                                                 ;; oflags
                (i64.sub (global.get $all_rights) (global.get $advise_right)) ;; rights base (all rights set except for fd_advise)
                (i64.sub (global.get $all_rights) (global.get $advise_right)) ;; rights inheriting (all rights set except for fd_advise)
                (i32.const 0)                                                 ;; fdflags
                (global.get $file_fd_address)                                 ;; fd address
            )
        )
        ;; Exit in case of error
        (if (i32.ne (local.get $ret) (i32.const 0)) (then (return (local.get $ret))))

        ;; Insufficient capabilities for advise
        (local.set $ret
            (call $fd_advise
                (i32.load (global.get $file_fd_address)) ;; "file.txt" fd without advise rights
                (i64.const 0)                            ;; offset of advised region
                (i64.const 0)                            ;; length of advised region
                (global.get $sequential_access_advice)   ;; sequential access advice
            )
        )
        ;; Check that error code is notcapable
        (if (i32.ne (local.get $ret) (global.get $errno_notcapable)) (then (return (i32.const -4))))

        ;; Free opened file
        (local.set $ret (call $fd_close (i32.load (global.get $file_fd_address))))
        ;; Exit in case of error
        (if (i32.ne (local.get $ret) (i32.const 0)) (then (return (local.get $ret))))

        ;; Clear random fd number so that this test is deterministic
        (i32.store (global.get $file_fd_address) (i32.const 0))
        ;; Success
        (i32.const 0)
    )
)