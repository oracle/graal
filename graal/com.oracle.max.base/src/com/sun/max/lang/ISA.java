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
package com.sun.max.lang;

/**
 * Instruction Set Architecture monikers.
 */
public enum ISA {

    AMD64(Category.CISC, 0, false, 0),
    ARM(Category.RISC, 4, false, 0),
    IA32(Category.CISC, 0, false, 0),
    PPC(Category.RISC, 4, true, 0),
    SPARC(Category.RISC, 4, true, 8);

    public enum Category {
        CISC, RISC;
    }

    public final Category category;

    /**
     * True if PC-relative control transfer instructions contain an offset relative to the start of the instruction.
     * False if PC-relative control transfer instructions contain an offset relative to the end of the instruction.
     */
    public final boolean relativeBranchFromStart;

    /**
     * Offset to the return address of the caller from the caller's saved PC.
     * For example, a SPARC call instruction saves its address in %o7. The return address is typically 2 instructions after
     * (to account for the call instruction itself and the delay slot).
     */
    public final int offsetToReturnPC;

    /**
     * Width of an instruction (in bytes).
     * If instructions are of variable width, this field is zero.
     */
    public final int instructionWidth;

    private ISA(Category category, int instructionWidth, boolean relativeBranchFromStart, int offsetToReturnPC) {
        this.category = category;
        this.instructionWidth = instructionWidth;
        this.relativeBranchFromStart = relativeBranchFromStart;
        this.offsetToReturnPC = offsetToReturnPC;
    }

    @Override
    public String toString() {
        return name();
    }

    /**
     * Determines if call instructions in this instruction set push the return address on the stack.
     */
    public boolean callsPushReturnAddressOnStack() {
        return category == Category.CISC;
    }

}
