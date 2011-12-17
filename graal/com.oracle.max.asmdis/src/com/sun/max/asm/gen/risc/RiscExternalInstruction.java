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

import com.sun.max.asm.*;
import com.sun.max.asm.dis.*;
import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.risc.field.*;
import com.sun.max.lang.*;

/**
 * Output of RISC instructions in external assembler format
 * We use exactly the same syntax as Sun's SPARC assembler "as"
 * and GNU's assembler "gas", except for branches to detected labels.
 * In the latter case, the label's name followed by ":"
 * is printed instead of ".".
 *
 * Examples of branch instructions without labels:
 *
 *     brz,a  . +20
 *     bne    . -200
 *
 * Examples of branch instructions with detected labels:
 *
 *     ba     L1: +112
 *     be,pt  L2: -50
 */
public class RiscExternalInstruction implements RiscInstructionDescriptionVisitor {

    protected final RiscTemplate template;
    protected final LinkedList<Argument> arguments;
    protected final ImmediateArgument address;
    protected final AddressMapper addressMapper;

    public RiscExternalInstruction(RiscTemplate template, List<Argument> arguments) {
        this.template = template;
        this.arguments = new LinkedList<Argument>(arguments);
        this.address = null;
        this.addressMapper = null;
    }

    public RiscExternalInstruction(RiscTemplate template, List<Argument> arguments, ImmediateArgument address, AddressMapper addressMapper) {
        this.template = template;
        this.arguments = new LinkedList<Argument>(arguments);
        this.address = address;
        this.addressMapper = addressMapper;
    }

    private String nameString;

    public String name() {
        if (nameString == null) {
            nameString = template.externalName();
            for (Argument argument : arguments) {
                if (argument instanceof ExternalMnemonicSuffixArgument) {
                    final String suffix = argument.externalValue();
                    nameString += suffix;
                }
            }
        }
        return nameString;
    }

    private String operandsString;

    public String operands() {
        if (operandsString == null) {
            operandsString = "";
            RiscInstructionDescriptionVisitor.Static.visitInstructionDescription(this, template.instructionDescription());
        }
        return operandsString;
    }

    @Override
    public String toString() {
        return Strings.padLengthWithSpaces(name(), 10) + "    " + operands();
    }

    private void print(String s) {
        operandsString += s;
    }

    private void printBranchDisplacement(ImmediateArgument immediateArgument) {
        final int delta = (int) immediateArgument.asLong();
        if (address != null) {
            final ImmediateArgument targetAddress = address.plus(delta);
            final DisassembledLabel label = addressMapper.labelAt(targetAddress);
            if (label != null) {
                print(label.name() + ": ");
            }
        } else {
            // (tw) No longer checked for absolute branch, always print "."
            print(". ");
        }
        if (delta >= 0) {
            print("+");
        }
        print(Integer.toString(delta));
    }

    private Object previousSpecification;

    public void visitField(RiscField field) {
        if (field instanceof OperandField) {
            final OperandField operandField = (OperandField) field;
            if (operandField.boundTo() != null) {
                return;
            }
            final Argument argument = arguments.remove();
            if (argument instanceof ExternalMnemonicSuffixArgument) {
                return;
            }
            if (previousSpecification != null && !(previousSpecification instanceof String)) {
                print(", ");
            }
            if (argument instanceof ImmediateArgument) {
                final ImmediateArgument immediateArgument = (ImmediateArgument) argument;
                if (field instanceof BranchDisplacementOperandField) {
                    printBranchDisplacement(immediateArgument);
                } else {
                    if (operandField.isSigned()) {
                        print(immediateArgument.signedExternalValue());
                    } else {
                        print(immediateArgument.externalValue());
                    }
                }
            } else {
                print(argument.externalValue());
            }
            previousSpecification = field;
        }
    }

    public void visitConstant(RiscConstant constant) {
    }

    private boolean writingStrings;

    public void visitString(String string) {
        if (writingStrings) {
            print(string);
            previousSpecification = string;
        }
        writingStrings = true;
    }

    public void visitConstraint(InstructionConstraint constraint) {
    }

}
