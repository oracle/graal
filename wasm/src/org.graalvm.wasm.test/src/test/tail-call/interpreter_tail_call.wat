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
    (global $stack i32 (i32.const 40))
    (global $source i32 (i32.const 0))

    (type $t (func (param $stack i32) (param $sp i32) (param $source i32) (param $bci i32) (result i32)))

    (table $tab 4 funcref)
    (elem (i32.const 0) $lit)
    (elem (i32.const 1) $add)
    (elem (i32.const 2) $mul)
    (elem (i32.const 3) $ret)

    (memory $mem 1 1)
    (data (i32.const 0) "\00\05\00\02\00\03\01\02\03")

    (func $next (type $t) (param $stack i32) (param $sp i32) (param $source i32) (param $bci i32) (result i32)
        ;; load stack
        local.get $stack
        ;; load sp
        local.get $sp
        ;; load source
        local.get $source
        ;; load bci
        local.get $bci
        ;; load source[bci]
        local.get $source
        local.get $bci
        i32.add
        i32.load8_u
        ;; call tab[source[bci]](stack, sp, source, bci)
        return_call_indirect (type $t)
    )

    (func $lit (type $t) (param $stack i32) (param $sp i32) (param $source i32) (param $bci i32) (result i32) (local $val i32)
        ;; val = source[bci + 1]
        local.get $source
        local.get $bci
        i32.const 1
        i32.add
        i32.add
        i32.load8_u
        local.set $val
        ;; stack[sp] = val
        local.get $stack
        local.get $sp
        i32.add
        local.get $val
        i32.store8
        ;; load stack
        local.get $stack
        ;; load sp + 1
        local.get $sp
        i32.const 1
        i32.add
        ;; load source
        local.get $source
        ;; load bci + 2
        local.get $bci
        i32.const 2
        i32.add
        ;; call next(stack, sp + 1, source, bci + 2)
        return_call $next
    )

    (func $add (type $t) (param $stack i32) (param $sp i32) (param $source i32) (param $bci i32) (result i32) (local $val i32)
        ;; load stack[sp - 1]
        local.get $stack
        local.get $sp
        i32.const 1
        i32.sub
        i32.add
        i32.load8_u
        ;; load stack[sp - 2]
        local.get $stack
        local.get $sp
        i32.const 2
        i32.sub
        i32.add
        i32.load8_u
        ;; val = stack[sp - 2] + stack[sp - 1]
        i32.add
        local.set $val
        ;; stack[sp - 2] = val
        local.get $stack
        local.get $sp
        i32.const 2
        i32.sub
        i32.add
        local.get $val
        i32.store8
        ;; load stack
        local.get $stack
        ;; load sp - 1
        local.get $sp
        i32.const 1
        i32.sub
        ;; load source
        local.get $source
        ;; load bci + 1
        local.get $bci
        i32.const 1
        i32.add
        ;; call next(stack, sp - 1, source, bci + 2)
        return_call $next
    )

    (func $mul (type $t) (param $stack i32) (param $sp i32) (param $source i32) (param $bci i32) (result i32) (local $val i32)
        ;; load stack[sp - 1]
        local.get $stack
        local.get $sp
        i32.const 1
        i32.sub
        i32.add
        i32.load8_u
        ;; load stack[sp - 2]
        local.get $stack
        local.get $sp
        i32.const 2
        i32.sub
        i32.add
        i32.load8_u
        ;; val = stack[sp - 2] * stack[sp - 1]
        i32.mul
        local.set $val
        ;; stack[sp - 2] = val
        local.get $stack
        local.get $sp
        i32.const 2
        i32.sub
        i32.add
        local.get $val
        i32.store8
        ;; load stack
        local.get $stack
        ;; load sp - 1
        local.get $sp
        i32.const 1
        i32.sub
        ;; load source
        local.get $source
        ;; load bci + 1
        local.get $bci
        i32.const 1
        i32.add
        ;; call next(stack, sp - 1, source, bci + 2)
        return_call $next
    )

    (func $ret (type $t) (param $stack i32) (param $sp i32) (param $source i32) (param $bci i32) (result i32)
        ;; load stack[0]
        local.get $stack
        i32.load8_u
    )

    (func (export "_main") (result i32)
        ;; stack = mem[40]
        i32.const 40
        ;; sp = 0
        i32.const 0
        ;; source = mem[0]
        i32.const 0
        ;; bci = 0;
        i32.const 0
        return_call $next
    )
)
