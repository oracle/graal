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

import com.sun.max.asm.*;
import com.sun.max.asm.gen.*;

/**
 * An operand that is already implicit in the machine instruction,
 * without requiring an assembler method parameter.
 */
public class X86ImplicitOperand extends X86Operand implements ImplicitOperand {

    private final ImplicitOperand.ExternalPresence externalPresence;
    private final Argument argument;

    public X86ImplicitOperand(X86Operand.Designation designation, ImplicitOperand.ExternalPresence externalPresence, Argument argument) {
        super(designation);
        this.externalPresence = externalPresence;
        this.argument = argument;
    }

    public ImplicitOperand.ExternalPresence externalPresence() {
        return externalPresence;
    }

    public Class type() {
        return argument.getClass();
    }

    public Argument argument() {
        return argument;
    }

    public String name() {
        if (argument instanceof Enum) {
            final Enum enumerable = (Enum) argument;
            return enumerable.name();
        }
        final Immediate8Argument immediate8Argument = (Immediate8Argument) argument;
        assert immediate8Argument.value() > 0;
        return immediate8Argument.signedExternalValue();
    }

    @Override
    public String toString() {
        return "<ImplicitOperand: " + argument.externalValue() + ">";
    }
}
