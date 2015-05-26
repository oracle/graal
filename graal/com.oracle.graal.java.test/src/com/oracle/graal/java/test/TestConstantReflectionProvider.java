/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.java.test;

import static org.junit.Assert.*;

import java.lang.reflect.*;

import org.junit.*;

import com.oracle.jvmci.meta.*;

/**
 * Tests for {@link ConstantReflectionProvider}. It assumes an implementation of the interface that
 * actually returns non-null results for access operations that are possible, i.e., the tests will
 * fail for an implementation that spuriously returns null (which is allowed by the specification).
 */
public class TestConstantReflectionProvider extends TypeUniverse {

    @Test
    public void constantEqualsTest() {
        for (JavaConstant c1 : constants) {
            for (JavaConstant c2 : constants) {
                // test symmetry
                assertEquals(constantReflection.constantEquals(c1, c2), constantReflection.constantEquals(c2, c1));
                if (c1.getKind() != Kind.Object && c2.getKind() != Kind.Object) {
                    assertEquals(c1.equals(c2), constantReflection.constantEquals(c2, c1));
                }
            }
        }
    }

    @Test
    public void readArrayLengthTest() {
        for (JavaConstant c : constants) {
            Integer actual = constantReflection.readArrayLength(c);
            if (c.getKind() != Kind.Object || c.isNull() || !snippetReflection.asObject(Object.class, c).getClass().isArray()) {
                assertNull(actual);
            } else {
                assertNotNull(actual);
                int actualInt = actual;
                assertEquals(Array.getLength(snippetReflection.asObject(Object.class, c)), actualInt);
            }
        }
    }

    @Test
    public void boxTest() {
        for (JavaConstant c : constants) {
            JavaConstant boxed = constantReflection.boxPrimitive(c);
            if (c.getKind().isPrimitive()) {
                assertTrue(boxed.getKind().isObject());
                assertFalse(boxed.isNull());
            }
        }

        assertEquals(Long.valueOf(42), snippetReflection.asObject(Long.class, constantReflection.boxPrimitive(JavaConstant.forLong(42))));
        assertEquals(Integer.valueOf(66), snippetReflection.asObject(Integer.class, constantReflection.boxPrimitive(JavaConstant.forInt(66))));
        assertEquals(Byte.valueOf((byte) 123), snippetReflection.asObject(Byte.class, constantReflection.boxPrimitive(JavaConstant.forByte((byte) 123))));
        assertSame(Boolean.TRUE, snippetReflection.asObject(Boolean.class, constantReflection.boxPrimitive(JavaConstant.forBoolean(true))));

        assertNull(constantReflection.boxPrimitive(JavaConstant.NULL_POINTER));
        assertNull(constantReflection.boxPrimitive(snippetReflection.forObject("abc")));
    }

    @Test
    public void unboxTest() {
        for (JavaConstant c : constants) {
            JavaConstant unboxed = c.isNull() ? null : constantReflection.unboxPrimitive(c);
            if (unboxed != null) {
                assertFalse(unboxed.getKind().isObject());
            }
        }

        assertEquals(JavaConstant.forLong(42), constantReflection.unboxPrimitive(snippetReflection.forObject(Long.valueOf(42))));
        assertEquals(JavaConstant.forInt(66), constantReflection.unboxPrimitive(snippetReflection.forObject(Integer.valueOf(66))));
        assertEquals(JavaConstant.forByte((byte) 123), constantReflection.unboxPrimitive(snippetReflection.forObject(Byte.valueOf((byte) 123))));
        assertSame(JavaConstant.forBoolean(true), constantReflection.unboxPrimitive(snippetReflection.forObject(Boolean.TRUE)));

        assertNull(constantReflection.unboxPrimitive(JavaConstant.NULL_POINTER));
        assertNull(constantReflection.unboxPrimitive(snippetReflection.forObject("abc")));
    }
}
