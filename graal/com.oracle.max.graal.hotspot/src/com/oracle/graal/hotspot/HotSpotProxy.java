/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.hotspot;

/**
 * Provides methods to classify the HotSpot-internal identifiers.
 */
public final class HotSpotProxy {

    private HotSpotProxy() {
    }

    private enum CompilerObjectType {
        // this enum needs to have the same values as the one in graal_Compiler.hpp
        STUB(0x100000000000000L),
        METHOD(0x200000000000000L),
        CLASS(0x300000000000000L),
        SYMBOL(0x400000000000000L),
        CONSTANT_POOL(0x500000000000000L),
        CONSTANT(0x600000000000000L),
        TYPE_MASK(0xf00000000000000L),
        DUMMY_CONSTANT(0x6ffffffffffffffL);

        public final long bits;

        CompilerObjectType(long bits) {
            this.bits = bits;
        }
    }

    public static final Long DUMMY_CONSTANT_OBJ = CompilerObjectType.DUMMY_CONSTANT.bits;

    private static boolean isType(long id, CompilerObjectType type) {
        return (id & CompilerObjectType.TYPE_MASK.bits) == type.bits;
    }

    public static boolean isStub(long id) {
        return isType(id, CompilerObjectType.STUB);
    }

    public static boolean isMethod(long id) {
        return isType(id, CompilerObjectType.METHOD);
    }

    public static boolean isClass(long id) {
        return isType(id, CompilerObjectType.CLASS);
    }

    public static boolean isSymbol(long id) {
        return isType(id, CompilerObjectType.SYMBOL);
    }

    public static boolean isConstantPool(long id) {
        return isType(id, CompilerObjectType.CONSTANT_POOL);
    }

    public static boolean isConstant(long id) {
        return isType(id, CompilerObjectType.CONSTANT_POOL);
    }

    public static String toString(long id) {
        CompilerObjectType type = null;
        for (CompilerObjectType t : CompilerObjectType.values()) {
            if ((id & CompilerObjectType.TYPE_MASK.bits) == t.bits) {
                type = t;
            }
        }
        long num = id & ~CompilerObjectType.TYPE_MASK.bits;
        return type + " " + num;
    }

}
