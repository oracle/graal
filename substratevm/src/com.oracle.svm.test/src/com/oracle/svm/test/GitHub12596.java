/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodHandles;

import org.junit.Assert;
import org.junit.Test;

/**
 * Regression test for:
 * - https://github.com/oracle/graal/issues/12596
 *
 * This intentionally exercises REF_putField/REF_putStatic through
 * MethodHandle.invokeWithArguments(...) with boxed arguments. Primitive targets require
 * argument adaptation before reaching FieldAccessor#set(...).
 */
public class GitHub12596 {

    static final class PrimitiveFields {
        public boolean z;
        public byte b;
        public short s;
        public char c;

        public static boolean Z;
        public static byte B;
        public static short S;
        public static char C;
    }

    @Test
    public void invokePrimitiveSettersWithBoxedArguments() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        PrimitiveFields receiver = new PrimitiveFields();

        MethodHandle setZ = lookup.findSetter(PrimitiveFields.class, "z", boolean.class);
        MethodHandle setB = lookup.findSetter(PrimitiveFields.class, "b", byte.class);
        MethodHandle setS = lookup.findSetter(PrimitiveFields.class, "s", short.class);
        MethodHandle setC = lookup.findSetter(PrimitiveFields.class, "c", char.class);

        MethodHandle setZStatic = lookup.findStaticSetter(PrimitiveFields.class, "Z", boolean.class);
        MethodHandle setBStatic = lookup.findStaticSetter(PrimitiveFields.class, "B", byte.class);
        MethodHandle setSStatic = lookup.findStaticSetter(PrimitiveFields.class, "S", short.class);
        MethodHandle setCStatic = lookup.findStaticSetter(PrimitiveFields.class, "C", char.class);

        assertRefKind(lookup, setZ, MethodHandleInfo.REF_putField);
        assertRefKind(lookup, setB, MethodHandleInfo.REF_putField);
        assertRefKind(lookup, setS, MethodHandleInfo.REF_putField);
        assertRefKind(lookup, setC, MethodHandleInfo.REF_putField);
        assertRefKind(lookup, setZStatic, MethodHandleInfo.REF_putStatic);
        assertRefKind(lookup, setBStatic, MethodHandleInfo.REF_putStatic);
        assertRefKind(lookup, setSStatic, MethodHandleInfo.REF_putStatic);
        assertRefKind(lookup, setCStatic, MethodHandleInfo.REF_putStatic);

        setZ.invokeWithArguments(receiver, Boolean.TRUE);
        setB.invokeWithArguments(receiver, Byte.valueOf((byte) 7));
        setS.invokeWithArguments(receiver, Short.valueOf((short) 8));
        setC.invokeWithArguments(receiver, Character.valueOf('A'));

        setZStatic.invokeWithArguments(Boolean.TRUE);
        setBStatic.invokeWithArguments(Byte.valueOf((byte) 17));
        setSStatic.invokeWithArguments(Short.valueOf((short) 18));
        setCStatic.invokeWithArguments(Character.valueOf('B'));

        Assert.assertTrue(receiver.z);
        Assert.assertEquals((byte) 7, receiver.b);
        Assert.assertEquals((short) 8, receiver.s);
        Assert.assertEquals('A', receiver.c);
        Assert.assertTrue(PrimitiveFields.Z);
        Assert.assertEquals((byte) 17, PrimitiveFields.B);
        Assert.assertEquals((short) 18, PrimitiveFields.S);
        Assert.assertEquals('B', PrimitiveFields.C);
    }

    private static void assertRefKind(MethodHandles.Lookup lookup, MethodHandle setter, int expectedKind) {
        MethodHandleInfo info = lookup.revealDirect(setter);
        Assert.assertEquals(expectedKind, info.getReferenceKind());
    }
}
