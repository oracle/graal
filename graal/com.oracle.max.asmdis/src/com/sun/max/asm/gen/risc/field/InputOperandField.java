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
package com.sun.max.asm.gen.risc.field;

import com.sun.max.asm.*;
import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.risc.bitRange.*;
import com.sun.max.program.*;

/**
 * An input operand is a parameter to an assembler method that does not correspond directly
 * to a set of bits in the instruction but is a term in an expression that gives the value
 * for another operand that does represent a set of bits in the instruction.
 */
public class InputOperandField extends OperandField<ImmediateArgument> {

    private final Iterable< ? extends Argument> testArguments;
    private final ArgumentRange argumentRange;
    private final Iterable< ? extends Argument> illegalTestArguments;

    public InputOperandField(Iterable< ? extends Argument> testArguments, Iterable< ? extends Argument> illegalTestArguments, ArgumentRange argumentRange) {
        super(BitRange.create(new int[]{-1}, BitRangeOrder.DESCENDING));
        this.testArguments = testArguments;
        this.argumentRange = argumentRange;
        this.illegalTestArguments = illegalTestArguments;
    }

    public static InputOperandField create(OperandField valueRangeProvider) {
        return new InputOperandField(valueRangeProvider.getLegalTestArguments(), valueRangeProvider.getIllegalTestArguments(), valueRangeProvider.argumentRange());
    }

    @Override
    public ImmediateArgument disassemble(int instruction) {
        throw ProgramError.unexpected();
    }

    @Override
    public Class type() {
        return int.class;
    }

    public String valueString() {
        return variableName();
    }

    @Override
    public InputOperandField setVariableName(String name) {
        super.setVariableName(name);
        return this;
    }

    public Iterable< ? extends Argument> getLegalTestArguments() {
        return testArguments;
    }

    public Iterable<? extends Argument> getIllegalTestArguments() {
        //return Iterables.empty();
        return illegalTestArguments;
    }

    public ArgumentRange argumentRange() {
        return argumentRange;
    }

}
