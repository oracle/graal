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
package com.oracle.graal.api.meta.test;

import static com.oracle.graal.api.meta.MetaUtil.*;
import static org.junit.Assert.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import org.junit.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;


public class TestMetaAccessProvider {

    public TestMetaAccessProvider() {
    }

    public static final MetaAccessProvider runtime = Graal.getRequiredCapability(MetaAccessProvider.class);
    public static final List<Class<?>> classes = new ArrayList<>(Arrays.asList(
        void.class,
        boolean.class,
        byte.class,
        short.class,
        char.class,
        int.class,
        float.class,
        long.class,
        double.class,
        Object.class,
        Serializable.class,
        Cloneable.class,
        Test.class,
        TestMetaAccessProvider.class
    ));

    static {
        for (Class<?> c : new ArrayList<>(classes)) {
            if (c != void.class) {
                classes.add(Array.newInstance(c, 0).getClass());
            }
        }
    }

    @Test
    public void lookupJavaTypeTest() {
        for (Class c : classes) {
            ResolvedJavaType type = runtime.lookupJavaType(c);
            assertNotNull(type);
            assertTrue(type.isClass(c));
            assertEquals(c.getModifiers(), type.getModifiers());
            if (!type.isArrayClass()) {
                assertEquals(type.getName(), toInternalName(c.getName()));
                assertEquals(toJavaName(type), c.getName());
            }
        }
    }

    @Test
    public void lookupJavaMethodTest() {
        for (Class c : classes) {
            for (Method reflect : c.getDeclaredMethods()) {
                ResolvedJavaMethod method = runtime.lookupJavaMethod(reflect);
                assertNotNull(method);
                assertEquals(reflect.getModifiers(), method.getModifiers());
                assertTrue(method.getDeclaringClass().isClass(reflect.getDeclaringClass()));
            }
        }
    }

    @Test
    public void lookupJavaFieldTest() {
        for (Class c : classes) {
            for (Field reflect : c.getDeclaredFields()) {
                ResolvedJavaField field = runtime.lookupJavaField(reflect);
                assertNotNull(field);
                assertEquals(reflect.getModifiers(), field.getModifiers());
                assertTrue(field.getDeclaringClass().isClass(reflect.getDeclaringClass()));
            }
        }
    }

    public static final List<Constant> constants = new ArrayList<>();
    static {
        for (Field f : Constant.class.getDeclaredFields()) {
            int mods = f.getModifiers();
            if (f.getType() == Constant.class && Modifier.isPublic(mods) && Modifier.isStatic(mods) && Modifier.isFinal(mods)) {
                try {
                    Constant c = (Constant) f.get(null);
                    if (c != null) {
                        constants.add(c);
                    }
                } catch (Exception e) {
                }
            }
        }
        for (Class c : classes) {
            if (c != void.class) {
                constants.add(Constant.forObject(Array.newInstance(c, 42)));
            }
        }
    }

    @Test
    public void lookupJavaTypeConstantTest() {
        for (Constant c : constants) {
            if (c.getKind().isObject() && !c.isNull()) {
                Object o = c.asObject();
                ResolvedJavaType type = runtime.lookupJavaType(c);
                assertNotNull(type);
                assertTrue(type.isClass(o.getClass()));
            } else {
                assertEquals(runtime.lookupJavaType(c), null);
            }
        }
    }

    @Test
    public void constantEqualsTest() {
        for (Constant c1 : constants) {
            for (Constant c2 : constants) {
                // test symmetry
                assertEquals(runtime.constantEquals(c1, c2), runtime.constantEquals(c2, c1));
                if (!c1.getKind().isObject() && !c2.getKind().isObject()) {
                    assertEquals(c1.equals(c2), runtime.constantEquals(c2, c1));
                }
            }
        }
    }

    @Test
    public void lookupArrayLengthTest() {
        for (Constant c : constants) {
            if (!c.getKind().isObject() || c.isNull() || !c.asObject().getClass().isArray()) {
                try {
                    int length = runtime.lookupArrayLength(c);
                    fail("Expected " + IllegalArgumentException.class.getName() + " for " + c + ", not " + length);
                } catch (IllegalArgumentException e) {
                    // pass
                }
            } else {
                assertEquals(Array.getLength(c.asObject()), runtime.lookupArrayLength(c));
            }
        }
    }
}
