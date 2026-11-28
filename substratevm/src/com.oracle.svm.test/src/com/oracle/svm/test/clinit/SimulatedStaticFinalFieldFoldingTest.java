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
package com.oracle.svm.test.clinit;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.test.NativeImageBuildArgs;

/**
 * Checks that the static analysis sees the folded value of a static final field whose declaring
 * class initializer is simulated, independent of when the simulation runs.
 *
 * Each class in {@link SimulatedStaticFinalFieldHolders} is initialized at run time, has a class
 * initializer that can be simulated, and reads its own static final field in a method that is
 * reached only through reflection. Static final field folding turns the field read into a check
 * whether the field is initialized, which selects either the folded value or the uninitialized
 * value {@code null}. Inlining before analysis folds this check if the class initializer is
 * simulated. The read is in a method of the declaring class, so no class initialization check
 * before it starts the simulation, and the simulation otherwise runs in a separate analysis task
 * when the class becomes reachable. If inlining before analysis only used a simulation result that
 * is already published, the static analysis would see {@code null} whenever that task has not
 * finished yet, and {@code nullValueObserved()} would become reachable. Since this depends on task
 * scheduling, the test uses several classes.
 */
@NativeImageBuildArgs("--features=com.oracle.svm.test.clinit.SimulatedStaticFinalFieldFoldingTest$TestFeature")
public class SimulatedStaticFinalFieldFoldingTest {

    private static final int HOLDER_COUNT = 8;
    private static final String HOLDER_PREFIX = "com.oracle.svm.test.clinit.SimulatedStaticFinalFieldHolders$Holder";

    public static class TestFeature implements Feature {
        private final List<Class<?>> holders = List.of(
                        SimulatedStaticFinalFieldHolders.Holder0.class, SimulatedStaticFinalFieldHolders.Holder1.class,
                        SimulatedStaticFinalFieldHolders.Holder2.class, SimulatedStaticFinalFieldHolders.Holder3.class,
                        SimulatedStaticFinalFieldHolders.Holder4.class, SimulatedStaticFinalFieldHolders.Holder5.class,
                        SimulatedStaticFinalFieldHolders.Holder6.class, SimulatedStaticFinalFieldHolders.Holder7.class);
        private boolean classesRegistered;

        private static Method method(Class<?> holder, String name) {
            try {
                return holder.getDeclaredMethod(name);
            } catch (NoSuchMethodException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            for (Class<?> holder : holders) {
                RuntimeReflection.register(method(holder, "read"));
            }
        }

        @Override
        public void duringAnalysis(DuringAnalysisAccess access) {
            if (!classesRegistered) {
                /*
                 * The test looks up the classes by name at run time. Registering a class for
                 * reflection makes it reachable right away, which would start the simulation of its
                 * class initializer earlier than the registration of the read method does.
                 * Therefore, register the classes only after the first analysis iteration.
                 */
                classesRegistered = true;
                RuntimeReflection.register(holders.toArray(new Class<?>[0]));
                access.requireAnalysisIteration();
            }
        }

        @Override
        public void afterAnalysis(AfterAnalysisAccess access) {
            List<String> failures = new ArrayList<>();
            for (Class<?> holder : holders) {
                if (access.isReachable(method(holder, "nullValueObserved"))) {
                    failures.add(holder.getName());
                }
            }
            if (!failures.isEmpty()) {
                throw new Error("The static analysis saw the uninitialized value of a static final field whose class initializer is simulated: " + failures);
            }
        }
    }

    @Test
    public void simulatedValuesAreReturned() throws ReflectiveOperationException {
        for (int i = 0; i < HOLDER_COUNT; i++) {
            /* The class name must not be a constant, so the class is not reachable early. */
            Class<?> holder = Class.forName(HOLDER_PREFIX + i);
            Assert.assertEquals("value" + i, holder.getDeclaredMethod("read").invoke(null));
        }
    }
}

/**
 * The classes are not nested in the test class: the test class is registered for reflection, which
 * makes its nest members reachable early.
 */
final class SimulatedStaticFinalFieldHolders {

    static final class Holder0 {
        /* Not a compile-time constant, so the value is assigned by the class initializer. */
        static final Object VALUE = "value0";

        static Object read() {
            Object value = VALUE;
            if (value == null) {
                nullValueObserved();
            }
            return value;
        }

        /** Reachable only if the static analysis sees the uninitialized value of VALUE. */
        static void nullValueObserved() {
        }
    }

    static final class Holder1 {
        static final Object VALUE = "value1";

        static Object read() {
            Object value = VALUE;
            if (value == null) {
                nullValueObserved();
            }
            return value;
        }

        static void nullValueObserved() {
        }
    }

    static final class Holder2 {
        static final Object VALUE = "value2";

        static Object read() {
            Object value = VALUE;
            if (value == null) {
                nullValueObserved();
            }
            return value;
        }

        static void nullValueObserved() {
        }
    }

    static final class Holder3 {
        static final Object VALUE = "value3";

        static Object read() {
            Object value = VALUE;
            if (value == null) {
                nullValueObserved();
            }
            return value;
        }

        static void nullValueObserved() {
        }
    }

    static final class Holder4 {
        static final Object VALUE = "value4";

        static Object read() {
            Object value = VALUE;
            if (value == null) {
                nullValueObserved();
            }
            return value;
        }

        static void nullValueObserved() {
        }
    }

    static final class Holder5 {
        static final Object VALUE = "value5";

        static Object read() {
            Object value = VALUE;
            if (value == null) {
                nullValueObserved();
            }
            return value;
        }

        static void nullValueObserved() {
        }
    }

    static final class Holder6 {
        static final Object VALUE = "value6";

        static Object read() {
            Object value = VALUE;
            if (value == null) {
                nullValueObserved();
            }
            return value;
        }

        static void nullValueObserved() {
        }
    }

    static final class Holder7 {
        static final Object VALUE = "value7";

        static Object read() {
            Object value = VALUE;
            if (value == null) {
                nullValueObserved();
            }
            return value;
        }

        static void nullValueObserved() {
        }
    }
}
