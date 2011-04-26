/*
 * Copyright (c) 2010 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.oracle.graal.runtime;

/**
 * Provides methods to classify the HotSpot-internal identifiers.
 *
 * @author Lukas Stadler
 */
public final class HotSpotProxy {

    private HotSpotProxy() {
    }

    private enum CompilerObjectType {
        // this enum needs to have the same values as the one in c1x_Compiler.hpp
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
