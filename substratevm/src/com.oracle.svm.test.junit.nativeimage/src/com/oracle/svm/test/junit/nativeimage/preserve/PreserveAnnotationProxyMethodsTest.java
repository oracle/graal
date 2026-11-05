/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.junit.nativeimage.preserve;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.junit.Test;

import com.oracle.svm.guest.staging.reflect.ReflectionModifiers;
import com.oracle.svm.guest.staging.reflect.RuntimeMetadataDecoder;
import com.oracle.svm.test.api.NativeImageBuildArgs;

/**
 * Preserving an annotation element for reflective invocation must also preserve the corresponding
 * method on its generated proxy. Janino looks up and invokes that method on the concrete proxy
 * class when compiling Flink SQL operators. A later invocation registration must also reach the
 * proxy if the element was already registered for lookup.
 */
@NativeImageBuildArgs({
                "--features=com.oracle.svm.test.junit.nativeimage.preserve.PreserveAnnotationProxyMethodsTest$RegisterAnnotationMethod",
                "--add-exports=org.graalvm.nativeimage.guest.staging/com.oracle.svm.guest.staging.reflect=ALL-UNNAMED",
                "-H:+UnlockExperimentalVMOptions",
                "-H:Preserve=package=java.lang.annotation",
                "-H:-UnlockExperimentalVMOptions",
                "--exact-reachability-metadata=com.oracle.svm.test.junit.nativeimage.preserve"
})
public class PreserveAnnotationProxyMethodsTest {
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Marker {
        String value();
    }

    @Marker("hello")
    static final class Annotated {
    }

    public static final class RegisterAnnotationMethod implements Feature {
        private boolean registered;

        @Override
        public void duringSetup(DuringSetupAccess access) {
            RuntimeReflection.register(Marker.class);
        }

        @Override
        public void duringAnalysis(DuringAnalysisAccess access) {
            if (!registered) {
                registered = true;
                // Register the annotated class only after the first analysis iteration. Its proxy
                // can itself cause the annotation methods to be registered for invocation.
                RuntimeReflection.register(Annotated.class);
                RuntimeReflection.register(Marker.class.getDeclaredMethods());
                access.requireAnalysisIteration();
            }
        }
    }

    @Test
    public void testInvocationRegisteredAfterLookup() throws ReflectiveOperationException {
        // Read the annotation at run time so the image heap cannot supply a precomputed proxy.
        Class<?> annotated = Class.forName(System.getProperty("annotation.test.class", Annotated.class.getName()));
        Marker marker = AnnotationAccess.getAnnotation(annotated, Marker.class);
        String name = elementName();
        assertEquals("hello", Marker.class.getDeclaredMethod(name).invoke(marker));
        assertEquals("hello", invokeValue(marker, name, false));
    }

    @Test
    public void testPreservedAnnotationProxyMethods() throws ReflectiveOperationException {
        assertEquals(RetentionPolicy.RUNTIME, invokeValue(AnnotationAccess.getAnnotation(Deprecated.class, Retention.class), elementName(), true));
        assertArrayEquals(new ElementType[]{ElementType.ANNOTATION_TYPE}, (ElementType[]) invokeValue(AnnotationAccess.getAnnotation(Target.class, Target.class), elementName(), true));
    }

    private static String elementName() {
        // Keep the name unknown at build time so constant reflection inference cannot satisfy the lookup.
        return System.getProperty("annotation.element.name", "value");
    }

    private static Object invokeValue(Annotation annotation, String name, boolean expectedPreserved) throws ReflectiveOperationException {
        Method method = annotation.getClass().getDeclaredMethod(name);
        Object value = method.invoke(annotation);
        if (ImageInfo.inImageRuntimeCode()) {
            assertEquals("Preservation metadata for " + method, expectedPreserved, RuntimeMetadataDecoder.singleton().isPreserved(ReflectionModifiers.getRawModifiers(method)));
        }
        return value;
    }
}
