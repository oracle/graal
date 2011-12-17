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
import com.sun.max.program.*;
import com.sun.max.util.*;

/**
 */
public class X86EnumerableParameter<EnumerableArgument_Type extends Enum<EnumerableArgument_Type> & EnumerableArgument<EnumerableArgument_Type>> extends X86Parameter implements EnumerableParameter {

    private final Enumerator<EnumerableArgument_Type> enumerator;
    private final Argument exampleArgument;

    public X86EnumerableParameter(X86Operand.Designation designation, ParameterPlace place, Enumerator<EnumerableArgument_Type> enumerator) {
        super(designation, place);
        this.enumerator = enumerator;
        final Iterator<EnumerableArgument_Type> it = enumerator.iterator();
        exampleArgument = it.hasNext() ? it.next().exampleValue() : null;
        switch (place) {
            case MOD_REG:
            case MOD_REG_REXR:
            case MOD_RM:
            case MOD_RM_REXB:
                setVariableName(designation.name().toLowerCase());
                break;
            case SIB_SCALE:
                setVariableName("scale");
                break;
            case SIB_INDEX:
            case SIB_INDEX_REXX:
                setVariableName("index");
                break;
            case SIB_BASE:
            case SIB_BASE_REXB:
                setVariableName("base");
                break;
            case APPEND:
                setVariableName(enumerator.type().getSimpleName().toLowerCase());
                break;
            case OPCODE1:
            case OPCODE1_REXB:
            case OPCODE2_REXB:
                setVariableName("register");
                break;
            case OPCODE2:
                setVariableName("st_i");
                break;
            default:
                throw ProgramError.unexpected();
        }
    }

    public Enumerator<EnumerableArgument_Type> enumerator() {
        return enumerator;
    }

    public Class type() {
        return enumerator.type();
    }

    public String valueString() {
        return variableName() + ".value()";
    }

    public Iterable<EnumerableArgument_Type> getLegalTestArguments() {
        return enumerator;
    }

    public Iterable<? extends Argument> getIllegalTestArguments() {
        return Collections.emptyList();
    }

    public Argument getExampleArgument() {
        return exampleArgument;
    }

    @Override
    public String toString() {
        return type().getSimpleName();
    }

}
