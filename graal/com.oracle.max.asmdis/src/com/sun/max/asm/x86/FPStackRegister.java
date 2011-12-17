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
package com.sun.max.asm.x86;

import com.sun.max.asm.*;
import com.sun.max.util.*;

/**
 */
public enum FPStackRegister implements EnumerableArgument<FPStackRegister> {

    ST_0(0),
    ST_1(1),
    ST_2(2),
    ST_3(3),
    ST_4(4),
    ST_5(5),
    ST_6(6),
    ST_7(7),
    ST(0) {
        @Override
        public String externalValue() {
            return "%st";
        }
        @Override
        public String disassembledValue() {
            return "st";
        }
    };

    private final int value;

    private FPStackRegister(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public long asLong() {
        return value();
    }

    public String externalValue() {
        return "%st(" + value() + ")";
    }

    public String disassembledValue() {
        return "st(" + value() + ")";
    }

    public Enumerator<FPStackRegister> enumerator() {
        return ENUMERATOR;
    }

    public FPStackRegister exampleValue() {
        return ST_0;
    }

    public static final Enumerator<FPStackRegister> ENUMERATOR = new Enumerator<FPStackRegister>(FPStackRegister.class);

}
