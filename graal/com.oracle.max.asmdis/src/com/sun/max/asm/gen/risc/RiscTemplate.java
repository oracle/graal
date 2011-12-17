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
public class RiscTemplate extends Template implements RiscInstructionDescriptionVisitor {

    private final List<RiscField> allFields = new LinkedList<RiscField>();
    private final List<OperandField> operandFields = new LinkedList<OperandField>();
    private final List<OptionField> optionFields = new LinkedList<OptionField>();
    private final List<OperandField> parameters = new ArrayList<OperandField>();
    private final List<Option> options = new LinkedList<Option>();

    private int opcode;
    private int opcodeMask;
    private RiscTemplate canonicalRepresentative;

    public RiscTemplate(InstructionDescription instructionDescription) {
        super(instructionDescription);
    }

    @Override
    public RiscInstructionDescription instructionDescription() {
        return (RiscInstructionDescription) super.instructionDescription();
    }

    private RiscTemplate synthesizedFrom;

    public void setSynthesizedFrom(RiscTemplate synthesizedFrom) {
        assert instructionDescription().isSynthetic();
        this.synthesizedFrom = synthesizedFrom;
    }

    public RiscTemplate synthesizedFrom() {
        return synthesizedFrom;
    }

    /**
     * Adds the value of a constant field to the opcode of the instruction and
     * updates the opcode mask to include the bits of the field.
     *
     * @param field a field containing a constant value
     * @param value the constant value
     */
    private void organizeConstant(RiscField field, int value) {
        try {
            opcode |= field.bitRange().assembleUnsignedInt(value);
            opcodeMask |= field.bitRange().instructionMask();
        } catch (IndexOutOfBoundsException indexOutOfBoundsException) {
            throw ProgramError.unexpected("operand for constant field " + field.name() + " does not fit: " + value);
        }
    }

    public void visitField(RiscField field) {
        allFields.add(field);
        if (field instanceof OperandField) {
            final OperandField operandField = (OperandField) field;
            if (field instanceof OffsetParameter) {
                setLabelParameterIndex();
            }
            if (operandField.boundTo() == null) {
                parameters.add(operandField);
            }
            operandFields.add(operandField);
        } else if (field instanceof OptionField) {
            optionFields.add((OptionField) field);
        } else if (field instanceof ReservedField) {
            organizeConstant(field, 0);
        } else {
            throw ProgramError.unexpected("unknown or unallowed type of field: " + field);
        }
    }

    public void visitConstant(RiscConstant constant) {
        organizeConstant(constant.field(), constant.value());
    }

    public void visitConstraint(InstructionConstraint constraint) {
    }

    /**
     * Sets the internal name of this template from a given string it is not already set.
     *
     * @param string  a string specified in the to consider
     */
    public void visitString(String string) {
        if (internalName() == null) {
            setInternalName(string);
        }
    }

    public List<OperandField> operandFields() {
        return operandFields;
    }

    public int opcode() {
        return opcode;
    }

    public int opcodeMask() {
        return opcodeMask;
    }

    public List<OptionField> optionFields() {
        return optionFields;
    }

    public void addOptionField(OptionField f) {
        allFields.add(f);
        optionFields.add(f);
    }

    public int specificity() {
        return Integer.bitCount(opcodeMask);
    }

    public void organizeOption(Option option, RiscTemplate canonicalRepresentative) {
        instructionDescription().setExternalName(externalName() + option.externalName());
        setInternalName(internalName() + option.name());
        try {
            opcode |= option.field().bitRange().assembleUnsignedInt(option.value());
            opcodeMask |= option.field().bitRange().instructionMask();
        } catch (IndexOutOfBoundsException e) {
            throw ProgramError.unexpected("Option: " + option.name() + " does not fit in field " + option.field().name());
        }

        options.add(option);
        if (option.isRedundant()) {
            this.canonicalRepresentative = canonicalRepresentative;
        }
    }

    @Override
    public Template canonicalRepresentative() {
        return canonicalRepresentative;
    }

    @Override
    public String assemblerMethodName() {
        return internalName();
    }

    @Override
    public List<Operand> operands() {
        throw ProgramError.unexpected("unimplemented");
    }

    @Override
    public List<OperandField> parameters() {
        return parameters;
    }

    @Override
    public String toString() {
        return "<" + getClass().getSimpleName() + " #" + serial() + ": " + internalName() + " " + Integer.toHexString(opcode()) + ", " + parameters() + ">";
    }

}
