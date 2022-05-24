/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.buildtimeeval;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.oracle.svm.test.buildtimeeval.heap.HeapDataStructure;
import org.graalvm.nativeimage.ImageInfo;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.hosted.classinitialization.ClassInitializationOptions;
import com.oracle.svm.test.buildtimeeval.nativeimage.meta.Constant;

class ClassWithUnreachableMethods {
    public int foo() {
        return 42;
    };

    public int bar() {
        return 43;
    }

}

public class BuildTimeMethodEvaluationTest {

    private boolean testPredicate() {
        return ImageInfo.inImageCode() && ClassInitializationOptions.UseExperimentalBuildTimeEvaluation.hasBeenSet();
    }

    public static final String CLASS_WITH_UNREACHABLE_METHODS = "ClassWithUnreachableMethods";

    /**
     * We can guarantee that reflection abstractions get converted into a constant.
     */
    @Constant
    @NeverInline("Must be build-time evaluated")
    public static Method getMethod(String clazz, String methodName) {
        try {
            String className = "com.oracle.svm.test.buildtimeeval." + clazz;
            return Class.forName(className).getMethod(methodName);
        } catch (NoSuchMethodException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reflective elements can go into data structures, and they are still registered.
     */
    @Constant
    @NeverInline("Must be build-time evaluated")
    static HeapDataStructure getBoxedMethod(String clazz, String methodName) {
        return new HeapDataStructure(getMethod(clazz, methodName));
    }

    @Constant
    @NeverInline("Must be build-time evaluated")
    public static Integer getNullOrNatural(int input) {
        return input <= 0 ? null : Integer.valueOf(input);
    }

    /**
     * Reflective elements can go into arrays, and they are still registered.
     */
    @Constant
    @NeverInline("Must be build-time evaluated")
    static Method[] getMethodInArray(String clazz, String methodName) {
        return new Method[]{getMethod(clazz, methodName)};
    }

    @Test
    public void testMetadata() {
        if (testPredicate()) {
            try {
                Assert.assertEquals(getMethod(CLASS_WITH_UNREACHABLE_METHODS, "foo").invoke(new ClassWithUnreachableMethods()), 42);
                Assert.assertEquals(getMethod(CLASS_WITH_UNREACHABLE_METHODS, "bar").invoke(new ClassWithUnreachableMethods()), 43);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    public void testMetadataInDataStructures() {
        if (testPredicate()) {
            try {
                Assert.assertEquals(getBoxedMethod(CLASS_WITH_UNREACHABLE_METHODS, "foo").m.invoke(new ClassWithUnreachableMethods()), 42);
                Assert.assertEquals(getMethodInArray(CLASS_WITH_UNREACHABLE_METHODS, "bar")[0].invoke(new ClassWithUnreachableMethods()), 43);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Exceptions are thrown at run time.
     */
    @Test
    public void testException() {
        if (testPredicate()) {
            try {
                getMethod(CLASS_WITH_UNREACHABLE_METHODS, "noFooNoSmile");
                Assert.fail("Must throw!");
            } catch (RuntimeException e) {
                // here we would need a proper stack trace
            }
        }
    }

    @Test
    public void testConstructors() {
        if (testPredicate()) {
            try {
                // For now only factory methods work.
                Assert.assertEquals(HeapDataStructure.create(CLASS_WITH_UNREACHABLE_METHODS, "foo").m.invoke(new ClassWithUnreachableMethods()), 42);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    public void testNonStaticFinalMethods() {
        if (testPredicate()) {
            try {
                Assert.assertEquals(HeapDataStructure.create(CLASS_WITH_UNREACHABLE_METHODS, "foo").getM().invoke(new ClassWithUnreachableMethods()), 42);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    public void testComposition() {
        if (testPredicate()) {
            try {
                Assert.assertEquals(HeapDataStructure.create(getMethod(CLASS_WITH_UNREACHABLE_METHODS, "foo")).getM().invoke(new ClassWithUnreachableMethods()), 42);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    public void testNull() {
        if (testPredicate()) {
            Assert.assertEquals(getNullOrNatural(0), null);
            Assert.assertEquals(getNullOrNatural(1), Integer.valueOf(1));
        }
    }
}
