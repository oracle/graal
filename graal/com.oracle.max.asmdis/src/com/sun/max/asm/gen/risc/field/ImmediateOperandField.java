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

import java.lang.reflect.*;
import java.util.*;

import com.sun.max.asm.*;
import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.risc.bitRange.*;

/**
 * A field that contains an immediate value.
 */
public class ImmediateOperandField extends OperandField<ImmediateArgument> implements ImmediateParameter, WrappableSpecification, InstructionConstraint {

    public String asJavaExpression() {
        final String value = valueString();
        return minArgumentValue() + " <= " + value + " && " + value + " <= " + maxArgumentValue();
    }

    public boolean check(Template template, List<Argument> arguments) {
        final long value = evaluate(template, arguments);
        return minArgumentValue() <= value && value <= maxArgumentValue();
    }

    public Method predicateMethod() {
        return null;
    }

    public boolean referencesParameter(Parameter parameter) {
        return parameter == this;
    }

    public ImmediateOperandField(BitRange bitRange) {
        super(bitRange);
    }

    public static ImmediateOperandField create(BitRangeOrder order, int... bits) {
        final BitRange bitRange = BitRange.create(bits, order);
        return new ImmediateOperandField(bitRange);
    }

    public static ImmediateOperandField createDescending(int firstBitIndex, int lastBitIndex) {
        return new ImmediateOperandField(new DescendingBitRange(firstBitIndex, lastBitIndex));
    }

    public static ImmediateOperandField createAscending(int firstBitIndex, int lastBitIndex) {
        return new ImmediateOperandField(new AscendingBitRange(firstBitIndex, lastBitIndex));
    }

    public static ImmediateOperandField createAscending(int... bits) {
        return create(BitRangeOrder.ASCENDING, bits);
    }

    @Override
    public Class type() {
        return int.class;
    }

    public String valueString() {
        if (boundTo() != null) {
            return boundTo().valueString();
        }
        return variableName();
    }

    @Override
    public ImmediateOperandField beSigned() {
        return (ImmediateOperandField) super.beSigned();
    }

    @Override
    public ImmediateOperandField beSignedOrUnsigned() {
        return (ImmediateOperandField) super.beSignedOrUnsigned();
    }

    @Override
    public Immediate32Argument disassemble(int instruction) {
        return new Immediate32Argument(extract(instruction));
    }

    @Override
    public ImmediateOperandField setVariableName(String name) {
        super.setVariableName(name);
        return this;
    }

    private ArgumentRange argumentRange;

    public ArgumentRange argumentRange() {
        if (argumentRange == null) {
            argumentRange = new ArgumentRange(this, minArgumentValue(), maxArgumentValue());
        }
        return argumentRange;
    }

    private Iterable<? extends Argument> testArguments;
    private Iterable<? extends Argument> illegalTestArguments;

    public Iterable<? extends Argument> getLegalTestArguments() {
        if (testArguments == null) {
            final List<Integer> integers = signDependentOperations().legalTestArgumentValues(minArgumentValue(), maxArgumentValue(), grain());
            List<Argument> result = new ArrayList<Argument>(integers.size());
            for (Integer i : integers) {
                result.add(new Immediate32Argument(i));
            }
            testArguments = result;
        }
        return testArguments;
    }

    public Iterable<? extends Argument> getIllegalTestArguments() {
        if (this.illegalTestArguments == null) {
            final List<Immediate32Argument> illegalArguments = new LinkedList<Immediate32Argument>();
            final int min = minArgumentValue();
            if (min != Integer.MIN_VALUE) {
                illegalArguments.add(new Immediate32Argument(min - 1));
                illegalArguments.add(new Immediate32Argument(Integer.MIN_VALUE));
            }
            final int max = maxArgumentValue();
            if (max != Integer.MAX_VALUE) {
                illegalArguments.add(new Immediate32Argument(max + 1));
                illegalArguments.add(new Immediate32Argument(Integer.MAX_VALUE));
            }
            this.illegalTestArguments = illegalArguments;
        }
        return illegalTestArguments;
    }

    public TestArgumentExclusion excludeExternalTestArguments(Argument... arguments) {
        return new TestArgumentExclusion(AssemblyTestComponent.EXTERNAL_ASSEMBLER, this, new HashSet<Argument>(Arrays.asList(arguments)));
    }

    @Override
    public ImmediateOperandField bindTo(Expression expression) {
        return (ImmediateOperandField) super.bindTo(expression);
    }
}
