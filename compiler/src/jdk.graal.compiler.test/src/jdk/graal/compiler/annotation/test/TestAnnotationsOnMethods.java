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

import static jdk.graal.compiler.annotation.AnnotationValueSupport.getAnnotationDefaultValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.AnnotationValueSupport;
import jdk.graal.compiler.annotation.TypeAnnotationValue;
import jdk.graal.compiler.annotation.test.TestAnnotationsOnMethods.AnnotationValueTest.Annotation1;
import jdk.graal.compiler.annotation.test.TestAnnotationsOnMethods.AnnotationValueTest.Annotation2;
import jdk.graal.compiler.annotation.test.TestAnnotationsOnMethods.AnnotationValueTest.Annotation3;
import jdk.graal.compiler.annotation.test.TestAnnotationsOnMethods.AnnotationValueTest.NumbersDE;
import jdk.graal.compiler.test.AddExports;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.reflect.annotation.TypeAnnotation;

/**
 * Tests for annotations on {@link ResolvedJavaMethod}s.
 */
@AddExports({"java.base/java.lang", "java.base/java.lang.reflect", "java.base/jdk.internal.reflect", "java.base/sun.reflect.annotation"})
public class TestAnnotationsOnMethods extends TestAnnotationsBase {

    public TestAnnotationsOnMethods() {
    }

    /**
     * Input for {@link #getAnnotationValuesTest}.
     */
    static class AnnotationValueTest {

        public enum NumbersEN {
            One,
            Two
        }

        public enum NumbersDE {
            Eins,
            Zwei;

            static {
                Assert.fail("NumbersDE.<clinit> should not be called");
            }
        }

        public enum NumbersUA {
            Odyn,
            Dva
        }

        @Retention(RetentionPolicy.RUNTIME)
        public @interface Annotation1 {
            NumbersEN value() default NumbersEN.One;
        }

        @Retention(RetentionPolicy.RUNTIME)
        public @interface Annotation2 {
            NumbersDE value() default NumbersDE.Eins;
        }

        @Retention(RetentionPolicy.RUNTIME)
        public @interface Annotation3 {
            NumbersUA value() default NumbersUA.Odyn;
        }

        @Annotation1
        @Annotation2
        @Annotation3(NumbersUA.Dva)
        static void methodWithThreeAnnotations() {

        }
    }

    @Test
    public void getParameterAnnotationValuesTest() throws Exception {
        checkParameterAnnotationValues(AnnotationTestInput.class.getDeclaredMethod("annotatedMethod"));
        for (Method m : methods.keySet()) {
            checkParameterAnnotationValues(m);
        }
    }

    /**
     * Tests that parameter {@link Annotation}s obtained from {@code m} match parameter
     * {@link AnnotationValue}s for the corresponding {@link ResolvedJavaMethod}.
     */
    private static void checkParameterAnnotationValues(Method m) {
        ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
        Annotation[][] parameterAnnotations = m.getParameterAnnotations();
        List<List<AnnotationValue>> parameterAnnotationValues = AnnotationValueSupport.getParameterAnnotationValues(method);
        if (parameterAnnotationValues != null) {
            int parsedAnnotations = parameterAnnotationValues.size();
            if (parsedAnnotations != parameterAnnotations.length) {
                // Remove slots injected for implicit leading parameters
                parameterAnnotations = Arrays.copyOfRange(parameterAnnotations, parameterAnnotations.length - parsedAnnotations, parameterAnnotations.length);
            }
            assertParameterAnnotationsEquals(parameterAnnotations, parameterAnnotationValues);
        } else {
            for (Annotation[] annotations : parameterAnnotations) {
                Assert.assertEquals(0, annotations.length);
            }
        }
    }

    public static void assertParameterAnnotationsEquals(
                    Annotation[][] parameterAnnotations,
                    List<List<AnnotationValue>> parameterAnnotationValues) throws AssertionError {
        assertEquals(parameterAnnotations.length, parameterAnnotationValues.size());
        for (int i = 0; i < parameterAnnotations.length; i++) {
            Annotation[] annotations = parameterAnnotations[i];
            List<AnnotationValue> annotationValues = parameterAnnotationValues.get(i);
            assertEquals(annotations.length, annotationValues.size());
            for (int j = 0; j < annotations.length; j++) {
                assertAnnotationsEquals(annotations[j], annotationValues.get(j));
            }
        }
    }

    @Test
    public void getTypeAnnotationValuesTest() throws Exception {
        checkTypeAnnotationValues(AnnotationTestInput.class.getDeclaredMethod("annotatedMethod"));
        for (Method m : methods.keySet()) {
            checkTypeAnnotationValues(m);
        }
    }

    private static final Method executableGetTypeAnnotationBytes = lookupMethod(Executable.class, "getTypeAnnotationBytes");

    /**
     * Tests that {@link TypeAnnotation}s obtained from {@code executable} match
     * {@link TypeAnnotationValue}s for the corresponding {@link ResolvedJavaMethod}.
     */
    private static void checkTypeAnnotationValues(Executable executable) {
        ResolvedJavaMethod method = metaAccess.lookupJavaMethod(executable);
        byte[] rawAnnotations = invokeMethod(executableGetTypeAnnotationBytes, executable);
        List<TypeAnnotation> typeAnnotations = getTypeAnnotations(rawAnnotations, executable.getDeclaringClass());
        List<TypeAnnotationValue> typeAnnotationValues = AnnotationValueSupport.getTypeAnnotationValues(method);
        assertTypeAnnotationsEquals(typeAnnotations, typeAnnotationValues);
    }

    @Test
    public void getAnnotationValuesTest() throws Exception {
        checkAnnotationValues(AnnotationTestInput.class.getDeclaredMethod("annotatedMethod"));
        checkAnnotationValues(AnnotationTestInput.class.getDeclaredMethod("missingAnnotation"));
        try {
            checkAnnotationValues(AnnotationTestInput.class.getDeclaredMethod("missingElementTypeAnnotation"));
            throw new AssertionError("expected " + TypeNotPresentException.class.getName());
        } catch (TypeNotPresentException e) {
            Assert.assertEquals("Type jdk.graal.compiler.annotation.test.Missing not present", e.getMessage());
        }
        try {
            checkAnnotationValues(AnnotationTestInput.class.getDeclaredMethod("missingNestedAnnotation"));
            throw new AssertionError("expected " + NoClassDefFoundError.class.getName());
        } catch (NoClassDefFoundError e) {
            Assert.assertEquals("Ljdk/graal/compiler/annotation/test/Missing;", e.getMessage());
        }
        checkAnnotationValues(AnnotationTestInput.class.getDeclaredMethod("missingTypeOfClassMember"));
        checkAnnotationValues(AnnotationTestInput.class.getDeclaredMethod("changeTypeOfMember"));
        checkAnnotationValues(AnnotationTestInput.class.getDeclaredMethod("missingMember"));
        List<AnnotationValue> avList = checkAnnotationValues(AnnotationTestInput.class.getDeclaredMethod("addedMember"));
        try {
            avList.getFirst().get("addedElement", Integer.class);
            throw new AssertionError("expected " + IllegalArgumentException.class.getName());
        } catch (IllegalArgumentException e) {
            Assert.assertEquals(MemberAdded.class.getName() + " missing element addedElement", e.getMessage());
        }

        for (Method m : methods.keySet()) {
            checkAnnotationValues(m);
        }

        ResolvedJavaMethod m = metaAccess.lookupJavaMethod(AnnotationValueTest.class.getDeclaredMethod("methodWithThreeAnnotations"));
        ResolvedJavaType a1 = metaAccess.lookupJavaType(Annotation1.class);
        ResolvedJavaType a2 = metaAccess.lookupJavaType(Annotation2.class);
        ResolvedJavaType a3 = metaAccess.lookupJavaType(Annotation3.class);
        ResolvedJavaType numbersDEType = metaAccess.lookupJavaType(NumbersDE.class);

        // Ensure NumbersDE is not initialized before Annotation2 is requested
        Assert.assertFalse(numbersDEType.isInitialized());

        Map<ResolvedJavaType, AnnotationValue> declaredAnnotationValues = AnnotationValueSupport.getDeclaredAnnotationValues(m);
        Assert.assertEquals(3, declaredAnnotationValues.size());
        Assert.assertNotNull(declaredAnnotationValues.get(a1));
        Assert.assertNotNull(declaredAnnotationValues.get(a2));
        Assert.assertNotNull(declaredAnnotationValues.get(a3));

        // Ensure NumbersDE is not initialized after Annotation2 is requested
        Assert.assertNotNull(AnnotationValueSupport.getDeclaredAnnotationValue(a2, m));
        Assert.assertFalse(numbersDEType.isInitialized());
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface NonNull {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface Special {
        String elementWithDefault() default "NO_NAME";

        long elementWithoutDefault();
    }

    private static native void methodWithAnnotatedParameters(@NonNull HashMap<String, String> p1, @Special(elementWithoutDefault = 42) @NonNull Class<? extends Annotation> p2);

    @Test
    public void getAnnotationDefaultValueTest() throws NoSuchMethodException {
        checkAnnotationDefaultValue(getClass().getDeclaredMethod("methodWithAnnotatedParameters", HashMap.class, Class.class));
        checkAnnotationDefaultValue(AnnotationTestInput.class.getDeclaredMethod("annotatedMethod"));
        for (Method m : methods.keySet()) {
            checkAnnotationDefaultValue(m);
        }
    }

    /**
     * Tests that {@link TypeAnnotation}s obtained from {@code executable} match
     * {@link TypeAnnotationValue}s for the corresponding {@link ResolvedJavaMethod}.
     */
    private static void checkAnnotationDefaultValue(Method executable) {
        ResolvedJavaMethod method = metaAccess.lookupJavaMethod(executable);
        Object defaultValue = executable.getDefaultValue();
        Object annotationDefaultValue = getAnnotationDefaultValue(method);
        if (defaultValue == null) {
            assertNull(annotationDefaultValue);
        } else {
            assertAnnotationElementsEqual(defaultValue, annotationDefaultValue);
        }
    }
}
