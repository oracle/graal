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

import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.api.meta.*;

/**
 * Tests for {@link MetaAccessProvider}.
 */
public class TestMetaAccessProvider extends TypeUniverse {

    @Test
    public void lookupJavaTypeTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            assertNotNull(type);
            assertEquals(c.getModifiers(), type.getModifiers());
            if (!type.isArray()) {
                assertEquals(type.getName(), toInternalName(c.getName()));
                assertEquals(type.toJavaName(), c.getName());
            }
        }
    }

    @Test
    public void lookupJavaMethodTest() {
        for (Class<?> c : classes) {
            for (Method reflect : c.getDeclaredMethods()) {
                ResolvedJavaMethod method = metaAccess.lookupJavaMethod(reflect);
                assertNotNull(method);
                int expected = reflect.getModifiers() & Modifier.methodModifiers();
                int actual = method.getModifiers();
                assertEquals(String.format("%s: 0x%x != 0x%x", reflect, expected, actual), expected, actual);
                assertTrue(method.getDeclaringClass().equals(metaAccess.lookupJavaType(reflect.getDeclaringClass())));
            }
        }
    }

    @Test
    public void lookupJavaFieldTest() {
        for (Class<?> c : classes) {
            for (Field reflect : c.getDeclaredFields()) {
                ResolvedJavaField field = metaAccess.lookupJavaField(reflect);
                assertNotNull(field);
                int expected = reflect.getModifiers();
                int actual = field.getModifiers();
                assertEquals(String.format("%s: 0x%x != 0x%x", reflect, expected, actual), expected, actual);
                assertTrue(field.getDeclaringClass().equals(metaAccess.lookupJavaType(reflect.getDeclaringClass())));
            }
        }
    }

    @Test
    public void lookupJavaTypeConstantTest() {
        for (Constant c : constants) {
            if (c.getKind() == Kind.Object && !c.isNull()) {
                Object o = snippetReflection.asObject(c);
                ResolvedJavaType type = metaAccess.lookupJavaType(c);
                assertNotNull(type);
                assertTrue(type.equals(metaAccess.lookupJavaType(o.getClass())));
            } else {
                assertEquals(metaAccess.lookupJavaType(c), null);
            }
        }
    }
}
