/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.annotation.test;

import static jdk.graal.compiler.annotation.AnnotationValueSupport.getDeclaredAnnotationValue;
import static jdk.graal.compiler.annotation.AnnotationValueSupport.getDeclaredAnnotationValues;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.AnnotationValueSupport;
import jdk.graal.compiler.annotation.TypeAnnotationValue;
import jdk.graal.compiler.test.AddExports;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.reflect.annotation.TypeAnnotation;

/**
 * Tests for annotations on {@link ResolvedJavaType}s.
 */
@AddExports({"java.base/java.lang", "java.base/java.lang.reflect", "java.base/jdk.internal.reflect", "java.base/sun.reflect.annotation"})
public class TestAnnotationsOnTypes extends TestAnnotationsBase {

    @SuppressWarnings("deprecation")
    @Test
    public void getTypeAnnotationValuesTest() {
        checkTypeAnnotationValues(AnnotationTestInput.AnnotatedClass.class);
        checkTypeAnnotationValues(AnnotationTestInput.AnnotatedClass2.class);
        checkTypeAnnotationValues(int.class);
        checkTypeAnnotationValues(void.class);
        for (Class<?> c : classes) {
            checkTypeAnnotationValues(c);
        }
    }

    /**
     * Tests that {@link TypeAnnotation}s obtained from {@code cls} match
     * {@link TypeAnnotationValue}s for the corresponding {@link ResolvedJavaType}.
     */
    private static void checkTypeAnnotationValues(Class<?> cls) {
        ResolvedJavaType rt = metaAccess.lookupJavaType(cls);
        assertTypeAnnotationsEquals(getTypeAnnotations(cls), AnnotationValueSupport.getTypeAnnotationValues(rt));
    }

    private static final Method classGetRawTypeAnnotations = lookupMethod(Class.class, "getRawTypeAnnotations");

    private static List<TypeAnnotation> getTypeAnnotations(Class<?> c) {
        byte[] rawAnnotations = invokeMethod(classGetRawTypeAnnotations, c);
        return getTypeAnnotations(rawAnnotations, c);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void getAnnotationValuesTest() {
        checkAnnotationValues(AnnotationTestInput.AnnotatedClass.class);
        checkAnnotationValues(AnnotationTestInput.AnnotatedClass2.class);
        checkAnnotationValues(int.class);
        checkAnnotationValues(void.class);
        for (Class<?> c : classes) {
            checkAnnotationValues(c);
        }

        // Primitive classes have no annotations but we cannot directly
        // test absence of annotations. Instead, just ensure empty answers
        // are returned when looking up an arbitrary annotation type.
        Class<?>[] prims = {void.class, byte.class, int.class, double.class, float.class, short.class, char.class, long.class};
        ResolvedJavaType overrideType = metaAccess.lookupJavaType(Override.class);
        for (Class<?> c : prims) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            AnnotationValue av = getDeclaredAnnotationValue(overrideType, type);
            Assert.assertNull(String.valueOf(av), av);
            Map<ResolvedJavaType, AnnotationValue> avMap = getDeclaredAnnotationValues(type);
            Assert.assertEquals(0, avMap.size());
        }

        // Test that inherited annotations are handled properly.
        ResolvedJavaType namedType = metaAccess.lookupJavaType(AnnotationTestInput.Named.class);
        AnnotationValue av = AnnotationValueSupport.getDeclaredAnnotationValue(namedType, metaAccess.lookupJavaType(AnnotationTestInput.OwnName.class));
        Assert.assertEquals("NonInheritedValue", av.get("value", String.class));
        av = getDeclaredAnnotationValue(namedType, metaAccess.lookupJavaType(AnnotationTestInput.InheritedName1.class));
        Assert.assertNull(av);
        av = getDeclaredAnnotationValue(namedType, metaAccess.lookupJavaType(AnnotationTestInput.InheritedName2.class));
        Assert.assertNull(av);
        av = getDeclaredAnnotationValue(namedType, metaAccess.lookupJavaType(AnnotationTestInput.InheritedName3.class));
        Assert.assertNull(av);
    }
}
