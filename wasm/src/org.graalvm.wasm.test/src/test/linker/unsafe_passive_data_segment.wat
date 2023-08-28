;;
;; Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
    (memory 1 1)
    (func (export "_main") (result i32)
        i32.const 0
        i32.const 0
        i32.const 256
        memory.init 0
        i32.const 0
        i32.load8_u
        i32.const 2
        i32.ne
        if
            i32.const 1
            return
        end
        i32.const 255
        i32.load8_u
        i32.const 1
        i32.ne
        if
            i32.const 1
            return
        end
        i32.const 0
        return
    )
    (data "\02\de\d5\cf\88\72\2e\13\f0\66\36\50\ef\6c\4f\b6\70\86\e3\e8\45\db\a9\c6\6c\ad\25\22\25\5b\74\12\a4\25\39\f0\43\b0\8c\1e\f9\21\f4\f0\a5\60\33\c9\d5\19\26\06\6d\df\7d\dd\a2\c9\d5\59\c4\ec\31\bc\a0\74\e5\6e\ad\8b\55\06\38\b7\a4\ef\e5\0e\a3\b5\98\e0\d6\dd\df\6d\1d\78\50\ec\48\e2\9e\1a\ac\e1\37\45\a0\c5\6b\3c\7c\06\36\31\a0\fd\a1\a5\fe\7b\d8\6a\6c\0f\88\dc\bb\3d\11\a5\9a\e1\b9\d3\63\fe\a2\22\f2\e8\0d\4f\49\0a\0d\fa\31\5d\e2\38\7e\4e\1c\3e\c3\cf\68\03\f6\48\8a\39\36\2b\b8\dc\af\ca\f3\86\53\29\68\95\f8\3d\3d\75\09\4a\92\84\98\3d\09\79\70\51\ba\4c\98\98\a7\5a\5f\65\e0\67\ef\a6\67\63\58\f4\66\9a\bf\96\fe\d9\cc\96\1a\ab\5f\f8\86\d7\58\d5\d2\c9\95\f0\64\23\7a\37\7c\42\9f\6b\8b\63\56\ca\63\13\d0\ca\a6\85\4e\ec\c3\e0\5a\65\39\ec\38\0f\69\81\f8\57\d2\48\6c\52\bc\e7\ea\01")
)
