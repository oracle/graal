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

import java.util.*;

import com.sun.max.asm.*;
import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.risc.bitRange.*;

/**
 * An instruction field whose encoded value does not include bits for
 * the low-order 0 bits of the aligned values that the field represents.
 * This class can convert between the field's <i>argument</i> (i.e.
 * the represented value) and it's <i>operand</i> (i.e. the encoded value).
 */
public class AlignedImmediateOperandField extends ImmediateOperandField {

    protected int zeroes;

    public AlignedImmediateOperandField(BitRange bitRange, int zeroes) {
        super(bitRange);
        this.zeroes = zeroes;
    }

    @Override
    public String asJavaExpression() {
        final String value = valueString();
        return "(" + super.asJavaExpression() + ") && ((" + value + " % " + grain() + ") == 0)";
    }

    @Override
    public boolean check(Template template, List<Argument> arguments) {
        if (!super.check(template, arguments)) {
            return false;
        }
        final long value = template.bindingFor(this, arguments).asLong();
        return (value % grain()) == 0;
    }

    @Override
    public int maxArgumentValue() {
        return super.maxArgumentValue() << zeroes();
    }

    @Override
    public int minArgumentValue() {
        return super.minArgumentValue() << zeroes();
    }

    @Override
    public int zeroes() {
        return zeroes;
    }

    /**
     * Converts an argument value to the operand value that does not include bits for the
     * implied low-order 0 bits that the aligned argument value is guaranteed to contain.
     * For example, if this field represents a 4-byte aligned value, then {@code argumentToOperand(536) == 134}.
     */
    private int argumentToOperand(int value) throws AssemblyException {
        final int p = grain();
        if (value % p != 0) {
            throw new AssemblyException("unaligned immediate operand: " + value);
        }
        return value / p;
    }

    /**
     * Converts an operand value to the argument value that includes
     * low-order 0 bits for the alignment of this field.
     * For example, if this field represents a 4-byte aligned value,
     * then {@code operandToArgument(134) == 536}.
     */
    private int operandToArgument(int operand) {
        return operand << zeroes();
    }

    @Override
    public int assemble(int value) throws IndexOutOfBoundsException, AssemblyException {
        return super.assemble(argumentToOperand(value));
    }

    @Override
    public Immediate32Argument disassemble(int instruction) {
        return new Immediate32Argument(operandToArgument(extract(instruction)));
    }
}
