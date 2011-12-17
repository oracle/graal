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

import com.sun.max.*;
import com.sun.max.asm.*;
import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.risc.*;
import com.sun.max.asm.gen.risc.bitRange.*;
import com.sun.max.util.*;

/**
 */
public class SymbolicOperandField<Argument_Type extends SymbolicArgument> extends OperandField<Argument_Type> implements WrappableSpecification {

    private final Symbolizer<Argument_Type> symbolizer;

    public SymbolicOperandField(BitRange bitRange, Symbolizer<Argument_Type> symbolizer) {
        super(bitRange);
        assert symbolizer != null;
        this.symbolizer = symbolizer;
    }

    public static <Argument_Type extends SymbolicArgument> SymbolicOperandField<Argument_Type> createAscending(Symbolizer<Argument_Type> symbolizer, int... bits) {
        final BitRange bitRange = BitRange.create(bits, BitRangeOrder.ASCENDING);
        return new SymbolicOperandField<Argument_Type>(bitRange, symbolizer);
    }

    public static <Argument_Type extends SymbolicArgument> SymbolicOperandField<Argument_Type> createDescending(String variableName,
                    final Symbolizer<Argument_Type> symbolizer, int... bits) {
        final BitRange bitRange = BitRange.create(bits, BitRangeOrder.DESCENDING);
        final SymbolicOperandField<Argument_Type> field = new SymbolicOperandField<Argument_Type>(bitRange, symbolizer);
        if (variableName != null) {
            field.setVariableName(variableName);
        }
        return field;
    }

    public static <Argument_Type extends SymbolicArgument> SymbolicOperandField<Argument_Type> createDescending(Symbolizer<Argument_Type> symbolizer, int... bits) {
        return createDescending(null, symbolizer, bits);
    }

    public RiscConstant constant(Argument_Type argument) {
        return new RiscConstant(new ConstantField(name(), bitRange()), argument);
    }

    @Override
    public Class type() {
        return symbolizer.type();
    }

    public String valueString() {
        if (boundTo() != null) {
            return boundTo().valueString();
        }
        return variableName() + ".value()";
    }

    public int assemble(Argument_Type argument) throws AssemblyException {
        return bitRange().assembleUncheckedUnsignedInt(argument.value());
    }

    @Override
    public Argument_Type disassemble(int instruction) {
        return symbolizer.fromValue(extract(instruction));
    }

    @Override
    public SymbolicOperandField<Argument_Type> setVariableName(String name) {
        super.setVariableName(name);
        return this;
    }

    public ArgumentRange argumentRange() {
        return null;
    }

    public Iterable<? extends Argument> getLegalTestArguments() {
        return symbolizer;
    }

    public Iterable<? extends Argument> getIllegalTestArguments() {
        return Collections.emptyList();
    }

    @Override
    public SymbolicOperandField<Argument_Type> withExcludedExternalTestArguments(Argument... arguments) {
        final Class<SymbolicOperandField<Argument_Type>> type = null;
        return Utils.cast(type, super.withExcludedExternalTestArguments(arguments));
    }

    public TestArgumentExclusion excludeExternalTestArguments(Argument... arguments) {
        return new TestArgumentExclusion(AssemblyTestComponent.EXTERNAL_ASSEMBLER, this, new HashSet<Argument>(Arrays.asList(arguments)));
    }

    @Override
    public SymbolicOperandField<Argument_Type> bindTo(Expression expression) {
        final Class<SymbolicOperandField<Argument_Type>> type = null;
        return Utils.cast(type, super.bindTo(expression));
    }
}
