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
 * Addressing methods for operands. Refer to "Appendix A.1 Opcode-Syntax Notation".
 */
public enum AddressingMethodCode  {

    A, // Far pointer is encoded in the instruction
    C, // Control register specified by the ModRM reg field
    D, // Debug register specified by the ModRM reg field
    E, // General purpose register or memory operand specified by the ModRM byte. Memory addresses can be computed from a segment register, SIB byte, and/or displacement.
    F, // rFLAGS register
    G, // General purpose register specified by the ModRM reg field.
    I, // Immediate value
    IC, // we made this one up, it's like I, but with parameter type AMD64XMMComparison
    J, // The instruction includes a relative offset that is added to the rIP.
    M, // A memory operand specified by the ModRM byte.
    N, // we made this one up, it's like G, but with ParameterPlace.OPCODE1/2{_REXB} instead of a ModRM field
    O, // The offset of an operand is encoded in the instruction. There is no ModRM byte in the instruction. Complex addressing using the SIB byte cannot be done.
    P, // 64-bit MMX register specified by the ModRM reg field.
    PR, // 64-bit MMX register specified by the ModRM r/m field. The ModRM mod field must be 11b.
    Q, // 64-bit MMX-register or memory operand specified by the ModRM byte. Memory addresses can be computed from a segment register, SIB byte, and/or displacement.
    R, // General purpose register specified by the ModRM r/m field. The ModRM mod field must be 11b.
    S, // Segment register specified by the ModRM reg field.
    T,
    V, // 128-bit XMM register specified by the ModRM reg field.
    VR, // 128-bit XMM register specified by the ModRM r/m field. The ModRM mod field must be 11b.
    W, // A 128-bit XMM register or memory operand specified by the ModRM byte. Memory addresses can be computed from a segment register, SIB byte, and/or displacement.
    X, // A memory operand addressed by the DS.rSI registers. Used in string instructions.
    Y; // A memory operand addressed by the ES.rDI registers. Used in string instructions.
}
