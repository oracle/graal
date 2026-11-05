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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.junit.Test;

public class MethodHandleFieldSetterTest {
    static class InstanceFields {
        boolean booleanField;
        byte byteField;
        short shortField;
        char charField;
    }

    static class StaticFields {
        static boolean booleanField;
        static byte byteField;
        static short shortField;
        static char charField;
    }

    public static class TestFeature implements Feature {
        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            RuntimeReflection.register(InstanceFields.class, StaticFields.class);
            RuntimeReflection.register(InstanceFields.class.getDeclaredFields());
            RuntimeReflection.register(StaticFields.class.getDeclaredFields());
        }
    }

    @Test
    public void testUnreflectPrimitiveInstanceFieldSetters() throws Throwable {
        InstanceFields instance = new InstanceFields();

        invokeInstanceSetter("booleanField", instance, Boolean.TRUE);
        invokeInstanceSetter("byteField", instance, Byte.valueOf((byte) 1));
        invokeInstanceSetter("shortField", instance, Short.valueOf((short) 2));
        invokeInstanceSetter("charField", instance, Character.valueOf('3'));

        assertTrue(instance.booleanField);
        assertEquals(1, instance.byteField);
        assertEquals(2, instance.shortField);
        assertEquals('3', instance.charField);
    }

    @Test
    public void testUnreflectPrimitiveStaticFieldSetters() throws Throwable {
        StaticFields.booleanField = false;
        StaticFields.byteField = 0;
        StaticFields.shortField = 0;
        StaticFields.charField = 0;

        invokeStaticSetter("booleanField", Boolean.TRUE);
        invokeStaticSetter("byteField", Byte.valueOf((byte) 4));
        invokeStaticSetter("shortField", Short.valueOf((short) 5));
        invokeStaticSetter("charField", Character.valueOf('6'));

        assertTrue(StaticFields.booleanField);
        assertEquals(4, StaticFields.byteField);
        assertEquals(5, StaticFields.shortField);
        assertEquals('6', StaticFields.charField);
    }

    @Test
    public void testFindPrimitiveFieldSetters() throws Throwable {
        InstanceFields instance = new InstanceFields();
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        lookup.findSetter(InstanceFields.class, "booleanField", boolean.class).invoke(instance, Boolean.FALSE);
        lookup.findSetter(InstanceFields.class, "byteField", byte.class).invoke(instance, Byte.valueOf((byte) 7));
        lookup.findSetter(InstanceFields.class, "shortField", short.class).invoke(instance, Short.valueOf((short) 8));
        lookup.findSetter(InstanceFields.class, "charField", char.class).invoke(instance, Character.valueOf('9'));

        assertFalse(instance.booleanField);
        assertEquals(7, instance.byteField);
        assertEquals(8, instance.shortField);
        assertEquals('9', instance.charField);
    }

    private static void invokeInstanceSetter(String fieldName, InstanceFields instance, Object value) throws Throwable {
        Field field = InstanceFields.class.getDeclaredField(fieldName);
        MethodHandle setter = MethodHandles.lookup().unreflectSetter(field);
        setter.invoke(instance, value);
    }

    private static void invokeStaticSetter(String fieldName, Object value) throws Throwable {
        Field field = StaticFields.class.getDeclaredField(fieldName);
        MethodHandle setter = MethodHandles.lookup().unreflectSetter(field);
        setter.invoke(value);
    }
}
