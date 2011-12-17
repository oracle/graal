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
package com.sun.max.asm.gen;

import com.sun.max.asm.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;

/**
 */
public abstract class ImmediateArgument implements Argument {

    public static ImmediateArgument create(long value, WordWidth width) {
        switch (width) {
            case BITS_8:
                return new Immediate8Argument((byte) value);
            case BITS_16:
                return new Immediate16Argument((short) value);
            case BITS_32:
                return new Immediate32Argument((int) value);
            case BITS_64:
                return new Immediate64Argument(value);
            default:
                throw ProgramError.unknownCase();
        }
    }

    public ImmediateArgument plus(ImmediateArgument addend) {
        return create(asLong() + addend.asLong(), width());
    }

    public ImmediateArgument plus(long addend) {
        return create(asLong() + addend, width());
    }

    public ImmediateArgument minus(ImmediateArgument addend) {
        return create(asLong() - addend.asLong(), width());
    }

    public ImmediateArgument minus(long addend) {
        return create(asLong() - addend, width());
    }

    public abstract WordWidth width();

    public abstract String signedExternalValue();

    public abstract Object boxedJavaValue();

    @Override
    public final String toString() {
        return "<" + getClass().getSimpleName() + ": " + externalValue() + ">";
    }

}
