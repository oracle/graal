/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.sun.max.asm.gen.cisc.x86;

/**
 * Refer to "Appendix A.1 Opcode-Syntax Notation".
 */
public enum OperandTypeCode {

    /**
     * Two 16-bit or 32-bit memory operands, depending on the effective operand size. Used in the BOUND instruction.
     */
    a,

    /**
     * A byte, irrespective of the effective operand size.
     */
    b,

    /**
     * A doubleword (32 bits), irrespective of the effective operand size.
     */
    d,

    /**
     * A double-quadword (128 bits), irrespective of the effective operand size.
     */
    dq,

    /**
     * ???
     */
    d_q,

    /**
     * A 32-bit or 48-bit far pointer, depending on the effective operand size.
     */
    p,

    /**
     * A 128-bit double-precision floating-point vector operand (packed double).
     */
    pd,

    /**
     * A 128-bit single-precision floating-point vector operand (packed single).
     */
    ps,

    /**
     * A quadword, irrespective of the effective operand size.
     */
    q,

    /**
     * A 6-byte or 10-byte pseudo-descriptor.
     */
    s,

    /**
     * A scalar double-precision floating-point operand (scalar double).
     */
    sd,

    /**
     * A scalar single-precision floating-point operand (scalar single).
     */
    ss,

    /**
     * A word, doubleword, or quadword, depending on the effective operand size.
     */
    v,

    /**
     * A word, irrespective of the effective operand size.
     */
    w,

    /**
     * A double word if operand size 32, a quad word if 64, undefined if 16.
     */
    y,

    /**
     * A word if the effective operand size is 16 bits, or a doubleword if the effective operand size is 32 or 64 bits.
     */
    z;

}
