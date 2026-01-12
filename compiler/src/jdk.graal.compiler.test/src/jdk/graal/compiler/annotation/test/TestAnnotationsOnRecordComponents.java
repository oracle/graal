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
import java.lang.reflect.RecordComponent;
import java.util.List;

import org.junit.Test;

import jdk.graal.compiler.annotation.AnnotationValueSupport;
import jdk.graal.compiler.annotation.TypeAnnotationValue;
import jdk.graal.compiler.test.AddExports;
import jdk.vm.ci.meta.ResolvedJavaRecordComponent;
import sun.reflect.annotation.TypeAnnotation;

/**
 * Tests for annotations on {@link ResolvedJavaRecordComponent}s.
 */
@AddExports({"java.base/java.lang", "java.base/java.lang.reflect", "java.base/jdk.internal.reflect", "java.base/sun.reflect.annotation"})
public class TestAnnotationsOnRecordComponents extends TestAnnotationsBase {

    public TestAnnotationsOnRecordComponents() {
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
        for (RecordComponent rc : AnnotationTestInput.AnnotatedRecord.class.getRecordComponents()) {
            checkTypeAnnotationValues(rc);
        }
        for (RecordComponent rc : recordComponents.keySet()) {
            checkTypeAnnotationValues(rc);
        }
    }

    /**
     * Tests that {@link TypeAnnotation}s obtained from {@code rc} match
     * {@link TypeAnnotationValue}s for the corresponding {@link ResolvedJavaRecordComponent}.
     */
    private static void checkTypeAnnotationValues(RecordComponent rc) {
        ResolvedJavaRecordComponent resolvedRc = metaAccess.lookupJavaRecordComponent(rc);
        byte[] rawAnnotations = getFieldValue(recordComponentTypeAnnotations, rc);
        List<TypeAnnotation> typeAnnotations = getTypeAnnotations(rawAnnotations, rc.getDeclaringRecord());
        List<TypeAnnotationValue> typeAnnotationValues = AnnotationValueSupport.getTypeAnnotationValues(resolvedRc);
        assertTypeAnnotationsEquals(typeAnnotations, typeAnnotationValues);
    }

    private static final Field recordComponentTypeAnnotations = lookupField(RecordComponent.class, "typeAnnotations");

}
