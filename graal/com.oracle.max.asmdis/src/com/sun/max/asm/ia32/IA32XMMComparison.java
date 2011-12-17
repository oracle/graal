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
package com.sun.max.asm.ia32;

import com.sun.max.asm.*;
import com.sun.max.util.*;

/**
 */
public enum IA32XMMComparison implements EnumerableArgument<IA32XMMComparison> {

    EQUAL,
    LESS_THAN,
    GREATER_THAN,
    LESS_THAN_OR_EQUAL,
    UNORDERED,
    NOT_EQUAL,
    NOT_LESS_THAN,
    NOT_LESS_THAN_OR_EQUAL,
    ORDERED;

    public int value() {
        return ordinal();
    }

    public long asLong() {
        return value();
    }

    public String externalValue() {
        return "$" + Integer.toString(value());
    }

    public String disassembledValue() {
        return name().toLowerCase();
    }

    public Enumerator<IA32XMMComparison> enumerator() {
        return ENUMERATOR;
    }

    public IA32XMMComparison exampleValue() {
        return LESS_THAN_OR_EQUAL;
    }

    public static final Enumerator<IA32XMMComparison> ENUMERATOR = new Enumerator<IA32XMMComparison>(IA32XMMComparison.class);

}
