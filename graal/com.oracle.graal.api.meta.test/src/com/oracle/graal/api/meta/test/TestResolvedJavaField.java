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

import static java.lang.reflect.Modifier.*;
import static org.junit.Assert.*;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

import org.junit.*;

import com.oracle.graal.api.meta.*;

/**
 * Tests for {@link ResolvedJavaField}.
 */
public class TestResolvedJavaField extends FieldUniverse {

    public TestResolvedJavaField() {
    }

    @Test
    public void getModifiersTest() {
        for (Map.Entry<Field, ResolvedJavaField> e : fields.entrySet()) {
            int expected = e.getKey().getModifiers();
            int actual = e.getValue().getModifiers();
            assertEquals(expected, actual);
        }
    }

    @Test
    public void isSyntheticTest() {
        for (Map.Entry<Field, ResolvedJavaField> e : fields.entrySet()) {
            boolean expected = e.getKey().isSynthetic();
            boolean actual = e.getValue().isSynthetic();
            assertEquals(expected, actual);
        }
    }

    @Test
    public void getAnnotationTest() {
        for (Map.Entry<Field, ResolvedJavaField> e : fields.entrySet()) {
            for (Annotation expected : e.getKey().getAnnotations()) {
                if (expected != null) {
                    Annotation actual = e.getValue().getAnnotation(expected.annotationType());
                    assertEquals(expected, actual);
                }
            }
        }
    }

    @Test
    public void readConstantValueTest() throws NoSuchFieldException {
        for (Map.Entry<Field, ResolvedJavaField> e : fields.entrySet()) {
            Field field = e.getKey();
            if (isStatic(field.getModifiers())) {
                try {
                    Object expected = field.get(null);
                    Object actual = snippetReflection.asBoxedValue(e.getValue().readConstantValue(null));
                    assertEquals(expected, actual);
                } catch (IllegalArgumentException | IllegalAccessException e1) {
                }
            } else {
                try {
                    Object receiver = field.getDeclaringClass().newInstance();
                    Object expected = field.get(receiver);
                    Object actual = snippetReflection.asBoxedValue(e.getValue().readConstantValue(snippetReflection.forObject(receiver)));
                    assertEquals(expected, actual);
                } catch (InstantiationException | IllegalArgumentException | IllegalAccessException e1) {
                }
            }
        }

        ResolvedJavaField field = metaAccess.lookupJavaField(getClass().getDeclaredField("stringField"));
        for (Object receiver : new Object[]{this, null, new String()}) {
            Constant value = field.readConstantValue(snippetReflection.forObject(receiver));
            assertNull(value);
        }

        ResolvedJavaField constField = metaAccess.lookupJavaField(getClass().getDeclaredField("constantStringField"));
        for (Object receiver : new Object[]{this, null, new String()}) {
            Constant value = constField.readConstantValue(snippetReflection.forObject(receiver));
            if (value != null) {
                Object expected = "constantField";
                assertTrue(snippetReflection.asObject(value) == expected);
            }
        }
    }

    @Test
    public void readValueTest() throws IllegalArgumentException, IllegalAccessException {
        for (Map.Entry<Field, ResolvedJavaField> e : fields.entrySet()) {
            Field field = e.getKey();
            field.setAccessible(true);
            if (isStatic(field.getModifiers())) {
                try {
                    Object expected = field.get(null);
                    Object actual = snippetReflection.asBoxedValue(e.getValue().readValue(null));
                    assertEquals(expected, actual);
                } catch (IllegalArgumentException | IllegalAccessException e1) {
                }
            }
        }

        String testString = "a test string";
        testString.hashCode(); // create hash
        for (Field f : String.class.getDeclaredFields()) {
            f.setAccessible(true);
            ResolvedJavaField rf = metaAccess.lookupJavaField(f);
            Object receiver = isStatic(f.getModifiers()) ? null : testString;
            Object expected = f.get(receiver);
            Object actual = snippetReflection.asBoxedValue(rf.readValue(receiver == null ? null : snippetReflection.forObject(receiver)));
            assertEquals(expected, actual);
        }
    }

    String stringField = "field";
    final String constantStringField = "constantField";

    private Method findTestMethod(Method apiMethod) {
        String testName = apiMethod.getName() + "Test";
        for (Method m : getClass().getDeclaredMethods()) {
            if (m.getName().equals(testName) && m.getAnnotation(Test.class) != null) {
                return m;
            }
        }
        return null;
    }

    // @formatter:off
    private static final String[] untestedApiMethods = {
        "getDeclaringClass",
        "isInternal"
    };
    // @formatter:on

    /**
     * Ensures that any new methods added to {@link ResolvedJavaMethod} either have a test written
     * for them or are added to {@link #untestedApiMethods}.
     */
    @Test
    public void testCoverage() {
        Set<String> known = new HashSet<>(Arrays.asList(untestedApiMethods));
        for (Method m : ResolvedJavaField.class.getDeclaredMethods()) {
            if (findTestMethod(m) == null) {
                assertTrue("test missing for " + m, known.contains(m.getName()));
            } else {
                assertFalse("test should be removed from untestedApiMethods" + m, known.contains(m.getName()));
            }
        }
    }
}
