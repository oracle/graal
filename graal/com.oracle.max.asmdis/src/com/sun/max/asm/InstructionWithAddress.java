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
package com.sun.max.asm;

import com.sun.max.program.*;

/**
 * An instruction that addresses some data with an absolute address.
 */
public abstract class InstructionWithAddress extends InstructionWithLabel {

    protected InstructionWithAddress(Assembler assembler, int startPosition, int endPosition, Label label) {
        super(assembler, startPosition, endPosition, label);
        assembler.addFixedSizeAssembledObject(this);
    }

    public int addressAsInt() throws AssemblyException {
        final Assembler32 assembler = (Assembler32) assembler();
        switch (label().state()) {
            case BOUND: {
                return assembler.startAddress() + label().position();
            }
            case FIXED_32: {
                return assembler.address(label());
            }
            case FIXED_64: {
                throw ProgramError.unexpected("64-bit address requested for 32-bit assembler");
            }
            default: {
                throw new AssemblyException("unassigned label");
            }
        }
    }

    public long addressAsLong() throws AssemblyException {
        final Assembler64 assembler = (Assembler64) assembler();
        switch (label().state()) {
            case BOUND: {
                return assembler.startAddress() + label().position();
            }
            case FIXED_64: {
                return assembler.address(label());
            }
            case FIXED_32: {
                throw ProgramError.unexpected("32-bit address requested for 64-bit assembler");
            }
            default: {
                throw new AssemblyException("unassigned label");
            }
        }
    }
}
