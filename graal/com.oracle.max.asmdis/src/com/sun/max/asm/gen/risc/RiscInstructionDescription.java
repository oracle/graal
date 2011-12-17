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
package com.sun.max.asm.gen.risc;

import java.util.*;

import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.risc.field.*;
import com.sun.max.program.*;

/**
 */
public final class RiscInstructionDescription extends InstructionDescription {

    public RiscInstructionDescription(List<Object> specifications) {
        super(specifications);

        int bits = 0;
        int mask = 0;
        for (Object specification : specifications) {
            final RiscField field;
            if (specification instanceof RiscField) {
                field = (RiscField) specification;
                if (field instanceof InputOperandField) {
                    // Cannot recover the value of these fields from an assembled instruction
                    // with support for a simultaneous equation solver
                    beNotDisassemblable();
                }
            } else if (specification instanceof RiscConstant) {
                field = ((RiscConstant) specification).field();
            } else {
                continue;
            }
            bits += field.bitRange().encodedWidth();
            final int fieldMask = field.bitRange().instructionMask();
            if ((fieldMask & mask) != 0) {
                throw ProgramError.unexpected("RISC instruction field defines bits also defined by another field: " + field.name() + "[" + field.bitRange() + "]");
            }
            mask |= fieldMask;
        }

        if (bits != 32) {
            throw ProgramError.unexpected("RISC instruction description describes " + bits + " instruction field bits: " + specifications);
        }
    }

    private boolean synthetic;

    public InstructionDescription beSynthetic() {
        synthetic = true;
        return this;
    }

    @Override
    public boolean isSynthetic() {
        return synthetic;
    }

}
