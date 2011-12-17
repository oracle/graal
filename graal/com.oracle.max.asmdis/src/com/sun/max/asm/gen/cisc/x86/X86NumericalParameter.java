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
package com.sun.max.asm.gen.cisc.x86;

import java.util.*;

import com.sun.max.asm.*;
import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.cisc.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;

/**
 */
public abstract class X86NumericalParameter extends X86Parameter implements AppendedParameter, ImmediateParameter {

    private final WordWidth width;

    public X86NumericalParameter(X86Operand.Designation designation, WordWidth width) {
        super(designation, ParameterPlace.APPEND);
        this.width = width;
    }

    public WordWidth width() {
        return width;
    }

    public String valueString() {
        return variableName();
    }

    public Class type() {
        return width().canonicalPrimitiveType;
    }

    public Iterable< ? extends ImmediateArgument> getLegalTestArguments() {
        try {
            switch (width) {
                case BITS_8:
                    return Static.createSequence(Immediate8Argument.class, byte.class, Byte.MIN_VALUE, (byte) -1, (byte) 2, Byte.MAX_VALUE);
                case BITS_16:
                    return Static.createSequence(Immediate16Argument.class, short.class, Short.MIN_VALUE, (short) (Byte.MIN_VALUE - 1), (short) (Byte.MAX_VALUE + 1), Short.MAX_VALUE);
                case BITS_32:
                    return Static.createSequence(Immediate32Argument.class, int.class, Integer.MIN_VALUE, Short.MIN_VALUE - 1, Short.MAX_VALUE + 1, Integer.MAX_VALUE);
                case BITS_64:
                    return Static.createSequence(Immediate64Argument.class, long.class, Long.MIN_VALUE, Integer.MIN_VALUE - 1L, Integer.MAX_VALUE + 1L, Long.MAX_VALUE);
                default:
                    throw ProgramError.unexpected();
            }
        } catch (Throwable throwable) {
            throw ProgramError.unexpected("could not generate test argument for: " + this, throwable);
        }
    }

    public Iterable<? extends Argument> getIllegalTestArguments() {
        return Collections.emptyList();
    }

    public Argument getExampleArgument() {
        switch (width) {
            case BITS_8:
                return new Immediate8Argument((byte) 0x12);
            case BITS_16:
                return new Immediate16Argument((short) 0x1234);
            case BITS_32:
                return new Immediate32Argument(0x12345678);
            case BITS_64:
                return new Immediate64Argument(0x123456789ABCDEL);
            default:
                throw ProgramError.unexpected();
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
