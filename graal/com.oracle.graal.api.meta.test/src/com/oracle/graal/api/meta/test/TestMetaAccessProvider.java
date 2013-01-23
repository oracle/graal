/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import sun.misc.Unsafe;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;

/**
 * Tests for {@link MetaAccessProvider}.
 */
public class TestMetaAccessProvider {

    public static final Unsafe unsafe;
    static {
        Unsafe theUnsafe = null;
        try {
            theUnsafe = Unsafe.getUnsafe();
        } catch (Exception e) {
            try {
                Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafeField.setAccessible(true);
                theUnsafe = (Unsafe) theUnsafeField.get(null);
            } catch (Exception e1) {
                throw (InternalError) new InternalError("unable to initialize unsafe").initCause(e1);
            }
        }
        unsafe = theUnsafe;
    }

    public TestMetaAccessProvider() {
    }

    public static final MetaAccessProvider runtime = Graal.getRequiredCapability(MetaAccessProvider.class);
    public static final Collection<Class<?>> classes = new HashSet<>();
    public static final Map<Class<?>, Class<?>> arrayClasses = new HashMap<>();

    public static synchronized Class<?> getArrayClass(Class componentType) {
        Class<?> arrayClass = arrayClasses.get(componentType);
        if (arrayClass == null) {
            arrayClass = Array.newInstance(componentType, 0).getClass();
            arrayClasses.put(componentType, arrayClass);
        }
        return arrayClass;
    }

    public static int dimensions(Class c) {
        if (c.getComponentType() != null) {
            return 1 + dimensions(c.getComponentType());
        }
        return 0;
    }

    private static void addClass(Class c) {
        if (classes.add(c)) {
            if (c.getSuperclass() != null) {
                addClass(c.getSuperclass());
            }
            for (Class sc : c.getInterfaces()) {
                addClass(sc);
            }
            for (Class dc : c.getDeclaredClasses()) {
                addClass(dc);
            }
            for (Method m : c.getDeclaredMethods()) {
                addClass(m.getReturnType());
                for (Class p : m.getParameterTypes()) {
                    addClass(p);
                }
            }

            if (c != void.class && dimensions(c) < 2) {
                Class<?> arrayClass = Array.newInstance(c, 0).getClass();
                arrayClasses.put(c, arrayClass);
                addClass(arrayClass);
            }
        }
    }

    static {
        Class[] initialClasses = {void.class, boolean.class, byte.class, short.class, char.class, int.class, float.class, long.class, double.class, Object.class, Class.class, ClassLoader.class,
                        String.class, Serializable.class, Cloneable.class, Test.class, TestMetaAccessProvider.class, List.class, Collection.class, Map.class, Queue.class, HashMap.class,
                        LinkedHashMap.class, IdentityHashMap.class, AbstractCollection.class, AbstractList.class, ArrayList.class};
        for (Class c : initialClasses) {
            addClass(c);
        }
    }

    static {
        System.out.println(classes.size() + " classes");
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
            if (c != void.class && !c.isArray()) {
                constants.add(Constant.forObject(Array.newInstance(c, 42)));
            }
        }
        constants.add(Constant.forObject(new ArrayList<>()));
        constants.add(Constant.forObject(new IdentityHashMap<>()));
        constants.add(Constant.forObject(new LinkedHashMap<>()));
        constants.add(Constant.forObject(new TreeMap<>()));
        constants.add(Constant.forObject(new ArrayDeque<>()));
        constants.add(Constant.forObject(new LinkedList<>()));
        constants.add(Constant.forObject("a string"));
        constants.add(Constant.forObject(42));
    }

    static {
        System.out.println(constants.size() + " constants");
    }

    @Test
    public void lookupJavaTypeTest() {
        for (Class c : classes) {
            ResolvedJavaType type = runtime.lookupJavaType(c);
            assertNotNull(type);
            assertEquals(c.getModifiers(), type.getModifiers());
            if (!type.isArray()) {
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
                int expected = reflect.getModifiers() & Modifier.methodModifiers();
                int actual = method.getModifiers();
                assertEquals(String.format("%s: 0x%x != 0x%x", reflect, expected, actual), expected, actual);
                assertTrue(method.getDeclaringClass().equals(runtime.lookupJavaType(reflect.getDeclaringClass())));
            }
        }
    }

    @Test
    public void lookupJavaFieldTest() {
        for (Class c : classes) {
            for (Field reflect : c.getDeclaredFields()) {
                ResolvedJavaField field = runtime.lookupJavaField(reflect);
                assertNotNull(field);
                int expected = reflect.getModifiers() & Modifier.fieldModifiers();
                int actual = field.getModifiers();
                assertEquals(String.format("%s: 0x%x != 0x%x", reflect, expected, actual), expected, actual);
                assertTrue(field.getDeclaringClass().equals(runtime.lookupJavaType(reflect.getDeclaringClass())));
            }
        }
    }

    @Test
    public void lookupJavaTypeConstantTest() {
        for (Constant c : constants) {
            if (c.getKind() == Kind.Object && !c.isNull()) {
                Object o = c.asObject();
                ResolvedJavaType type = runtime.lookupJavaType(c);
                assertNotNull(type);
                assertTrue(type.equals(runtime.lookupJavaType(o.getClass())));
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
                if (c1.getKind() != Kind.Object && c2.getKind() != Kind.Object) {
                    assertEquals(c1.equals(c2), runtime.constantEquals(c2, c1));
                }
            }
        }
    }

    @Test
    public void lookupArrayLengthTest() {
        for (Constant c : constants) {
            if (c.getKind() != Kind.Object || c.isNull() || !c.asObject().getClass().isArray()) {
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
