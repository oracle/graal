;;
;; Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
  (type (;1;) (func (param i32 i32) (result i32)))
  (type (;2;) (func (param i32 i32 i32 i32) (result i32)))
  (import "wasi_snapshot_preview1" "environ_get" (func $wasi_environ_get (type 1)))
  (import "wasi_snapshot_preview1" "fd_write" (func $wasi_fd_write (type 2)))
  (memory (;0;) 1)
  (export "memory" (memory 0))
  ;; Environment: A=42 B=FOO (set in environ-get.opts)
  (global (;0;) $iovarrayAddress i32 (i32.const 0))
  (global (;1;) $iovarrayLength i32 (i32.const 2))
  (global (;2;) $firstVarAddress i32 (i32.const 16))
  (global (;3;) $secondVarAddress i32 (i32.const 20))
  (global (;4;) $firstVarBufAddress i32 (i32.const 24))
  (global (;5;) $firstVarBufLength i32 (i32.const 4))
  (global (;6;) $secondVarBufAddress i32 (i32.const 29))
  (global (;7;) $secondVarBufLength i32 (i32.const 5))
  (global (;8;) $writtenBytesAddress i32 (i32.const 35))
  (global (;9;) $stdOutFd i32 (i32.const 1))

  (func (export "_main") (type 0) (local $errno i32)
    ;; This should write [24, 29] at $firstVarAddress and "A=42B=FOO" at $secondVarBufAddress.
    (local.set $errno (call $wasi_environ_get (global.get $firstVarAddress) (global.get $firstVarBufAddress)))
    (if (i32.ne (local.get $errno) (i32.const 0)) (then (return (i32.const -1))))
    (if (i32.ne (i32.load (global.get $firstVarAddress)) (global.get $firstVarBufAddress)) (then (return (i32.const -1))))
    (if (i32.ne (i32.load (global.get $secondVarAddress)) (global.get $secondVarBufAddress)) (then (return (i32.const -1))))

    ;; Store start index and length of each environment variable string (used as an argument of fd_write).
    (i32.store (i32.add (global.get $iovarrayAddress) (i32.const 0)) (global.get $firstVarBufAddress))
    (i32.store (i32.add (global.get $iovarrayAddress) (i32.const 4)) (global.get $firstVarBufLength))
    (i32.store (i32.add (global.get $iovarrayAddress) (i32.const 8)) (global.get $secondVarBufAddress))
    (i32.store (i32.add (global.get $iovarrayAddress) (i32.const 12)) (global.get $secondVarBufLength))

    ;; Print the two key=value pairs.
    (call $wasi_fd_write  (global.get $stdOutFd)  (global.get $iovarrayAddress) (global.get $iovarrayLength) (global.get $writtenBytesAddress))
    (return (i32.const 0))
  )
)
