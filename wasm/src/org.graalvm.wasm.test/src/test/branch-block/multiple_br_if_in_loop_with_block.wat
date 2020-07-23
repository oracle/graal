;;
;; Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
    (func (export "_main") (result i32)
        (local $i i32) (local $div_3_count  i32) (local $div_12_count i32) (local $div_60_count i32)
        i32.const 0
        local.set $i
        block $wrapper
            loop $loop
                block ;; useless block to check profile offsets computation
                    local.get $i
                    i32.const 1200
                    i32.eq
                    br_if $wrapper
                    local.get $i
                    i32.const 1
                    i32.add
                    local.tee $i

                    local.get $i
                    i32.const 3
                    i32.rem_u
                    i32.const 0
                    i32.ne
                    br_if $loop
                    local.get $div_3_count
                    i32.const 1
                    i32.add
                    local.set $div_3_count
                end

                local.get $i
                i32.const 4
                i32.rem_u
                i32.const 0
                i32.ne
                br_if $loop
                local.get $div_12_count
                i32.const 1
                i32.add
                local.set $div_12_count

                local.get $i
                i32.const 5
                i32.rem_u
                i32.const 0
                i32.ne
                br_if $loop
                local.get $div_60_count
                i32.const 1
                i32.add
                local.set $div_60_count

                br $loop
            end
            unreachable
        end
        local.get $div_60_count ;; == 20
        local.get $div_12_count ;; == 100
        local.get $div_3_count ;; == 400
        i32.add
        i32.add
    )
)