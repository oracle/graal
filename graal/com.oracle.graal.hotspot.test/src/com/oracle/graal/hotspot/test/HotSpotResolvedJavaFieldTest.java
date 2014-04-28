/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.test;

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.hotspot.meta.HotSpotResolvedObjectType.*;

import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.meta.*;

/**
 * Tests {@link HotSpotResolvedJavaField} functionality.
 */
public class HotSpotResolvedJavaFieldTest {

    private static final Class<?>[] classesWithInternalFields = {Class.class, ClassLoader.class};

    /**
     * Tests that {@link HotSpotResolvedJavaField#getModifiers()} only includes the modifiers
     * returned by {@link Field#getModifiers()}. Namely, it must not include
     * {@code HotSpotResolvedJavaField#FIELD_INTERNAL_FLAG}.
     */
    @Test
    public void testModifiersForInternal() {
        for (Class<?> c : classesWithInternalFields) {
            ResolvedJavaType type = HotSpotResolvedObjectType.fromClass(c);
            for (ResolvedJavaField field : type.getInstanceFields(false)) {
                if (field.isInternal()) {
                    Assert.assertEquals(0, ~getReflectionFieldModifiers() & field.getModifiers());
                }
            }
        }
    }

    /**
     * Tests that {@link HotSpotResolvedObjectType#createField(String, JavaType, long, int)} always
     * returns the same object for an internal field.
     */
    @Test
    public void testCachingForInternalFields() {
        for (Class<?> c : classesWithInternalFields) {
            HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) HotSpotResolvedObjectType.fromClass(c);
            for (ResolvedJavaField field : type.getInstanceFields(false)) {
                if (field.isInternal()) {
                    HotSpotResolvedJavaField expected = (HotSpotResolvedJavaField) field;
                    ResolvedJavaField actual = type.createField(expected.getName(), expected.getType(), expected.offset(), expected.getModifiers());
                    Assert.assertEquals(expected, actual);
                }
            }
        }
    }

    @Test
    public void testIsInObject() {
        for (Field f : String.class.getDeclaredFields()) {
            HotSpotResolvedJavaField rf = (HotSpotResolvedJavaField) runtime().getHostProviders().getMetaAccess().lookupJavaField(f);
            Assert.assertEquals(rf.toString(), rf.isInObject("a string"), !rf.isStatic());
        }
    }
}
