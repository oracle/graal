/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.junit.nativeimage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.MissingReflectionRegistrationError;
import org.graalvm.nativeimage.dynamicaccess.AccessCondition;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;
import org.junit.Test;

import com.oracle.svm.hosted.reflect.ReflectionHostedSupport;
import com.oracle.svm.hosted.substitute.SubstitutionReflectivityFilter;
import com.oracle.svm.test.api.NativeImageBuildArgs;

import jdk.graal.compiler.api.directives.GraalDirectives;
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

    public interface SuperclassInterface extends GrandSuperclassSuperinterface {
        int SUPERCLASS_INTERFACE_FIELD = 1;

        static void staticInterfaceMethod() {
        }

        default void superclassInterfaceMethod() {
        }
    }

    public interface GrandSuperclassSuperinterface {
        int GRAND_SUPERCLASS_SUPERINTERFACE_FIELD = 2;

        default void grandSuperclassSuperinterfaceMethod() {
        }
    }

    public interface GrandSuperclassInterface extends GrandSuperclassSuperinterface {
        int GRAND_SUPERCLASS_INTERFACE_FIELD = 3;

        default void grandSuperclassInterfaceMethod() {
        }
    }

    public static class InheritanceGrandSuperclass implements GrandSuperclassInterface {
    }

    public static class InheritanceSuperclass extends InheritanceGrandSuperclass implements SuperclassInterface {
    }

    /**
     * Implements no interface itself: it inherits every interface member through its superclasses.
     */
    public static class InheritanceTarget extends InheritanceSuperclass {
        public int ownField;

        public void ownMethod() {
        }
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

            RuntimeReflection.register(FieldLookupTarget.class);
            RuntimeReflection.registerFieldLookup(FieldLookupTarget.class, "value");
            RuntimeReflection.register(InheritanceTarget.class);
        }

        @Override
        public void afterAnalysis(AfterAnalysisAccess access) {
            Set<String> knownClassNames = ImageSingletons.lookup(ReflectionHostedSupport.class).getKnownClassNames();
            if (!knownClassNames.contains(FieldLookupTarget.class.getName())) {
                throw new AssertionError(FieldLookupTarget.class.getName() + " is missing from reflection metadata");
            }
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

    /**
     * Registering a type for reflection exposes the public fields and methods it inherits through
     * the interfaces of its superclasses, as {@link Class#getFields()} and {@link Class#getMethods()}
     * return them on HotSpot.
     */
    @Test
    public void testMembersInheritedThroughSuperclassInterfaces() {
        // With exact metadata, a constant receiver would make the build register the queries itself.
        Class<?> target = GraalDirectives.opaque(InheritanceTarget.class);

        Set<String> fieldNames = Arrays.stream(target.getFields()).map(Field::getName).collect(Collectors.toSet());
        assertEquals(Set.of("ownField", "SUPERCLASS_INTERFACE_FIELD", "GRAND_SUPERCLASS_INTERFACE_FIELD", "GRAND_SUPERCLASS_SUPERINTERFACE_FIELD"), fieldNames);

        Set<String> methodNames = Arrays.stream(target.getMethods()).map(Method::getName).collect(Collectors.toSet());
        Set<String> expectedMethodNames = Set.of("ownMethod", "superclassInterfaceMethod", "grandSuperclassInterfaceMethod", "grandSuperclassSuperinterfaceMethod");
        assertTrue("getMethods() returned " + methodNames, methodNames.containsAll(expectedMethodNames));
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

    static class LookupCondition {
        static void reach() {
        }
    }

    static class AccessConditionType {
        static void reach() {
        }
    }

    public interface ConditionalInterface {
        int VALUE = 42;

        default int value() {
            return VALUE;
        }
    }

    public static class ConditionalParent implements ConditionalInterface {
    }

    public static class ConditionalChild extends ConditionalParent {
        public int own;
    }

    @NativeImageBuildArgs({
                    "--exact-reachability-metadata=com.oracle.svm.test.junit.nativeimage",
                    "--initialize-at-run-time=com.oracle.svm.test.junit.nativeimage.ReflectionRegistrationTest$LookupCondition,com.oracle.svm.test.junit.nativeimage.ReflectionRegistrationTest$AccessConditionType",
                    "--features=com.oracle.svm.test.junit.nativeimage.ReflectionRegistrationTest$ExactReachabilityTest$TestFeature"
    })
    public static class ExactReachabilityTest {
        public static class TestFeature implements Feature {
            @Override
            public void afterRegistration(AfterRegistrationAccess access) {
                var reflection = access.getReflectiveAccess();
                reflection.register(AccessCondition.typeReached(LookupCondition.class), ConditionalChild.class);
                var accessCondition = AccessCondition.typeReached(AccessConditionType.class);
                try {
                    reflection.register(accessCondition, ConditionalInterface.class.getDeclaredMethod("value"));
                    reflection.register(accessCondition, ConditionalInterface.class.getDeclaredField("VALUE"));
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            }

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

        @Test
        public void testConditionalMemberQueries() throws ReflectiveOperationException {
            Class<?> child = GraalDirectives.opaque(ConditionalChild.class);
            assertThrows(MissingReflectionRegistrationError.class, () -> child.getMethod("value"));
            assertThrows(MissingReflectionRegistrationError.class, () -> child.getField("VALUE"));
            assertThrows(MissingReflectionRegistrationError.class, () -> child.getDeclaredField("own"));
            assertThrows(MissingReflectionRegistrationError.class, () -> child.getMethod("absent"));
            LookupCondition.reach();
            Field field = child.getField("VALUE");
            Method method = child.getMethod("value");
            assertEquals(ConditionalChild.class, child.getDeclaredField("own").getDeclaringClass());
            assertThrows(NoSuchMethodException.class, () -> child.getMethod("absent"));
            assertThrows(MissingReflectionRegistrationError.class, () -> field.get(null));
            ConditionalChild instance = new ConditionalChild();
            assertThrows(MissingReflectionRegistrationError.class, () -> method.invoke(instance));
            AccessConditionType.reach();
            assertEquals(42, field.get(null));
            assertEquals(42, method.invoke(instance));
        }

        @Test
        public void testStaticInterfaceMethodRequiresOwnRegistration() {
            Class<?> declaringInterface = GraalDirectives.opaque(SuperclassInterface.class);
            assertThrows(MissingReflectionRegistrationError.class, () -> declaringInterface.getDeclaredMethod("staticInterfaceMethod"));
        }

        private static String fieldName() {
            return GraalDirectives.opaque("value");
        }
    }
}
