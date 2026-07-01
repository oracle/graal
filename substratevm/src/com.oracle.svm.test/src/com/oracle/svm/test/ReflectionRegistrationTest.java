/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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
package com.oracle.svm.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.MissingReflectionRegistrationError;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;
import org.junit.Test;

import com.oracle.svm.hosted.substitute.SubstitutionReflectivityFilter;

import jdk.internal.misc.Unsafe;

/**
 * Tests the {@link RuntimeReflection}.
 */
public class ReflectionRegistrationTest {

    private static final int FIELD_LOOKUP_TEST_VALUE = 42;

    public static class FieldLookupTarget {
        public int value = FIELD_LOOKUP_TEST_VALUE;
    }

    public static class UnsafeAllocationTarget {
    }

    public static class TestFeature implements Feature {

        @SuppressWarnings("unused")//
        int unusedVariableOne = 1;
        @SuppressWarnings("unused")//
        int unusedVariableTwo = 2;

        @Override
        public void beforeAnalysis(final BeforeAnalysisAccess access) {
            try {
                RuntimeReflection.register((Class<?>) null);
                assert false;
            } catch (NullPointerException e) {
                assert e.getMessage().startsWith("Cannot register null value");
            }
            try {
                RuntimeReflection.register((Executable) null);
                assert false;
            } catch (NullPointerException e) {
                assert e.getMessage().startsWith("Cannot register null value");
            }
            try {
                RuntimeReflection.register((Field) null);
                assert false;
            } catch (NullPointerException e) {
                assert e.getMessage().startsWith("Cannot register null value");
            }

            try {
                ImageSingletons.lookup(RuntimeReflectionSupport.class).register(null, this.getClass());
                assert false;
            } catch (NullPointerException e) {
                assert e.getMessage().startsWith("Cannot use null value");
            }

            try {
                ImageSingletons.lookup(RuntimeReflectionSupport.class).register(null, false, this.getClass().getMethods());
                assert false;
            } catch (NullPointerException e) {
                assert e.getMessage().startsWith("Cannot use null value");
            }

            try {
                ImageSingletons.lookup(RuntimeReflectionSupport.class).register(null, true, false, this.getClass().getDeclaredFields());
                assert false;
            } catch (NullPointerException e) {
                assert e.getMessage().startsWith("Cannot use null value");
            }

            var reflectivityFilter = SubstitutionReflectivityFilter.singleton();
            try {
                reflectivityFilter.shouldExclude((Class<?>) null);
                assert false;
            } catch (NullPointerException e) {
                // expected
            }
            try {
                reflectivityFilter.shouldExclude((Executable) null);
                assert false;
            } catch (NullPointerException e) {
                // expected
            }
            try {
                reflectivityFilter.shouldExclude((Field) null);
                assert false;
            } catch (NullPointerException e) {
                // expected
            }

            RuntimeReflection.registerFieldLookup(FieldLookupTarget.class, "value");
        }

    }

    @Test
    public void test() {
        // nothing to do
    }

    @Test
    public void testFieldLookupAllowsAccess() throws ReflectiveOperationException {
        Field field = FieldLookupTarget.class.getDeclaredField("value");
        assertEquals(FIELD_LOOKUP_TEST_VALUE, field.get(new FieldLookupTarget()));
    }

    private static Object unsafeAllocate(Class<?> clazz) throws ReflectiveOperationException {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return ((Unsafe) unsafeField.get(null)).allocateInstance(clazz);
    }

    @NativeImageBuildArgs({
                    "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"
    })
    public static class UnsafeAllocationLegacyTest {
        @Test
        public void testUnregisteredUnsafeAllocationKeepsLegacyExceptionType() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> unsafeAllocate(UnsafeAllocationTarget.class));
            assertTrue(exception.getMessage().contains("unsafeAllocated"));
        }
    }

    @NativeImageBuildArgs({
                    "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
                    "--future-defaults=exact-reflection"
    })
    public static class ExactReflectionFutureDefaultTest {
        @Test
        public void testUnregisteredUnsafeAllocationThrowsMissingRegistrationError() {
            assertThrows(MissingReflectionRegistrationError.class, () -> unsafeAllocate(UnsafeAllocationTarget.class));
        }
    }

    @NativeImageBuildArgs({
                    "--exact-reachability-metadata=com.oracle.svm.test",
                    "--features=com.oracle.svm.test.ReflectionRegistrationTest$ExactReachabilityTest$TestFeature"
    })
    public static class ExactReachabilityTest {
        public static class TestFeature implements Feature {
            @Override
            public void beforeAnalysis(BeforeAnalysisAccess access) {
                RuntimeReflection.registerFieldLookup(FieldLookupTarget.class, "value");
            }
        }

        @Test
        public void testFieldLookupAllowsQueryOnly() throws NoSuchFieldException {
            Field field = FieldLookupTarget.class.getDeclaredField(fieldName());
            assertEquals(fieldName(), field.getName());
            assertThrows(MissingReflectionRegistrationError.class, () -> field.get(new FieldLookupTarget()));
        }

        private static String fieldName() {
            return System.nanoTime() == Long.MIN_VALUE ? "missing" : "value";
        }
    }
}
