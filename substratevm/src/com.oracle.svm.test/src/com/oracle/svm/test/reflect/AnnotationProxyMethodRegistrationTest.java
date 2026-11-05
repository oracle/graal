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
package com.oracle.svm.test.reflect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.test.NativeImageBuildArgs;

/**
 * Checks that the annotation proxy method follows an upgrade of the annotation method from
 * {@code QUERIED} to {@code ACCESSED}.
 *
 * In the first analysis iteration, only the annotation type is registered, which registers the
 * annotation methods and the proxy methods for lookup. In the second iteration, the feature
 * registers the annotation method for invocation, and the annotated class. Registering the
 * annotated class creates the proxy class, which also registers the annotation methods for
 * invocation. The proxy method must end up invocable, as it does when the registrations are
 * processed in the opposite order.
 *
 * The annotated class must not be reachable in the first iteration: the image heap then contains
 * its hub, so its annotations are registered and the proxy class is created right away, racing with
 * the lookup registration. It is therefore a top-level class, since member classes of a test class
 * are reachable as nest members of the test class, and the test looks it up by name instead of
 * using a class literal.
 */
@NativeImageBuildArgs("--features=com.oracle.svm.test.reflect.AnnotationProxyMethodRegistrationTest$TestFeature")
public class AnnotationProxyMethodRegistrationTest {

    static final String VALUE = "upgraded";

    /** Not final, so that {@link Class#forName} is not folded to a class literal at build time. */
    private static String annotatedClassName = "com.oracle.svm.test.reflect.AnnotationProxyMethodRegistrationTarget";

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface UpgradedAnnotation {
        String value();
    }

    public static class TestFeature implements Feature {
        private boolean accessRegistered;

        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            RuntimeReflection.register(UpgradedAnnotation.class);
        }

        @Override
        public void duringAnalysis(DuringAnalysisAccess access) {
            if (!accessRegistered) {
                accessRegistered = true;
                try {
                    RuntimeReflection.register(UpgradedAnnotation.class.getDeclaredMethod("value"));
                } catch (NoSuchMethodException e) {
                    throw new AssertionError(e);
                }
                RuntimeReflection.register(AnnotationProxyMethodRegistrationTarget.class);
                access.requireAnalysisIteration();
            }
        }
    }

    @Test
    public void proxyMethodIsInvocableAfterUpgrade() throws ReflectiveOperationException {
        Class<?> annotatedClass = Class.forName(annotatedClassName);
        // Checkstyle: allow direct annotation access
        UpgradedAnnotation annotation = annotatedClass.getAnnotation(UpgradedAnnotation.class);
        // Checkstyle: disallow direct annotation access
        Assert.assertNotNull(annotation);
        Assert.assertTrue(Proxy.isProxyClass(annotation.getClass()));

        Method proxyMethod = annotation.getClass().getDeclaredMethod("value");
        Assert.assertEquals(VALUE, proxyMethod.invoke(annotation));
    }
}
