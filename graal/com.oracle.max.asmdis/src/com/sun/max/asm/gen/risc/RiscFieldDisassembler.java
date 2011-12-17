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
/**
 *
 */
package com.sun.max.asm.gen.risc;

import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.risc.bitRange.*;
import com.sun.max.asm.gen.risc.field.*;
import com.sun.max.lang.*;

/**
 * Disassembles an assembled RISC instruction to show the value of each field in the instruction as
 * well as the bits occupied by the field.
 */
class RiscFieldDisassembler<Template_Type extends Template> implements RiscInstructionDescriptionVisitor {

    private final Template_Type template;
    private final int assembledInstruction;
    private String string;

    public RiscFieldDisassembler(Template_Type template, byte[] assembledInstruction) {
        assert assembledInstruction.length == 4;
        this.template = template;
        this.assembledInstruction = assembledInstruction[0] << 24 | ((assembledInstruction[1] & 0xFF) << 16) | ((assembledInstruction[2] & 0xFF) << 8) | (assembledInstruction[3] & 0xFF);
    }

    @Override
    public String toString() {
        if (string == null) {
            string = "";
            RiscInstructionDescriptionVisitor.Static.visitInstructionDescription(this, template.instructionDescription());
        }
        return string;
    }

    public void visitConstant(RiscConstant constant) {
        visitField(constant.field());
    }

    public void visitConstraint(InstructionConstraint constraint) {
    }

    public void visitField(RiscField field) {
        if (string.length() != 0) {
            string += ' ';
        }
        final int value;
        final BitRange bitRange = field.bitRange();
        final int width = bitRange.width();

        if (field instanceof OperandField) {
            value = ((OperandField) field).extract(assembledInstruction);
        } else {
            value = bitRange.extractUnsignedInt(assembledInstruction);
        }
        String binary = Integer.toBinaryString(value);
        if (binary.length() > width) {
            binary = binary.substring(binary.length() - width);
        } else if (binary.length() < width) {
            binary = Strings.times('0', width - binary.length()) + binary;
        }

        string += field.name() + "[" + bitRange + "]=" + value + "{" + binary + "}";
    }

    public void visitString(String s) {
    }

}
