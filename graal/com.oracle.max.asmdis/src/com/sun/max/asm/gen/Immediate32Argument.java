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

import com.sun.max.lang.*;

/**
 */
public class Immediate32Argument extends ImmediateArgument {

    private final int value;

    public Immediate32Argument(int value) {
        this.value = value;
    }

    @Override
    public WordWidth width() {
        return WordWidth.BITS_32;
    }

    public int value() {
        return value;
    }

    public long asLong() {
        return value();
    }

    public String externalValue() {
        return "0x" + Integer.toHexString(value);
    }

    public String disassembledValue() {
        return "0x" + String.format("%X", value);
    }

    @Override
    public String signedExternalValue() {
        return Integer.toString(value);
    }

    @Override
    public Object boxedJavaValue() {
        return new Integer(value);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Immediate32Argument) {
            final Immediate32Argument argument = (Immediate32Argument) other;
            return value == argument.value;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return value;
    }

}
