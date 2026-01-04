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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.Test;

import jdk.graal.compiler.annotation.AnnotationValueSupport;
import jdk.graal.compiler.annotation.TypeAnnotationValue;
import jdk.graal.compiler.test.AddExports;
import jdk.vm.ci.meta.ResolvedJavaField;
import sun.reflect.annotation.TypeAnnotation;

/**
 * Tests for annotations on {@link ResolvedJavaField}s.
 */
@AddExports({"java.base/java.lang", "java.base/java.lang.reflect", "java.base/jdk.internal.reflect", "java.base/sun.reflect.annotation"})
public class TestAnnotationsOnFields extends TestAnnotationsBase {

    public TestAnnotationsOnFields() {
    }

    @Test
    public void getAnnotationValuesTest() {
        for (Field f : AnnotationTestInput.class.getDeclaredFields()) {
            checkAnnotationValues(f);
        }
        for (Field f : fields.keySet()) {
            checkAnnotationValues(f);
        }
    }

    @Test
    public void getTypeAnnotationValuesTest() {
        for (Field f : AnnotationTestInput.class.getDeclaredFields()) {
            checkTypeAnnotationValues(f);
        }
        for (Field f : fields.keySet()) {
            checkTypeAnnotationValues(f);
        }
    }

    /**
     * Tests that {@link TypeAnnotation}s obtained from {@code field} match
     * {@link TypeAnnotationValue}s for the corresponding {@link ResolvedJavaField}.
     */
    private static void checkTypeAnnotationValues(Field field) {
        ResolvedJavaField javaField = metaAccess.lookupJavaField(field);
        byte[] rawAnnotations = invokeMethod(fieldGetTypeAnnotationBytes, field);
        List<TypeAnnotation> typeAnnotations = getTypeAnnotations(rawAnnotations, field.getDeclaringClass());
        List<TypeAnnotationValue> typeAnnotationValues = AnnotationValueSupport.getTypeAnnotationValues(javaField);
        assertTypeAnnotationsEquals(typeAnnotations, typeAnnotationValues);
    }

    private static final Method fieldGetTypeAnnotationBytes = lookupMethod(Field.class, "getTypeAnnotationBytes0");

}
