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

package com.oracle.svm.test.junit.nativeimage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.junit.Test;

import com.oracle.svm.core.feature.JVMCIFeatureAccess;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.test.api.NativeImageBuildArgs;

@NativeImageBuildArgs({
                "--features=com.oracle.svm.test.junit.nativeimage.MethodHandleStableNameTest$TestFeature"
})
public class MethodHandleStableNameTest {
    private static final String RENAMING_PROCESSOR = "com.oracle.svm.hosted.methodhandles.MethodHandleInvokerRenamingSubstitutionProcessor";
    private static final MethodType UNARY = MethodType.methodType(int.class, int.class);
    private static final MethodType BINARY = MethodType.methodType(int.class, int.class, int.class);
    private static final MethodType ARRAY_UNARY = MethodType.methodType(int.class, int[].class);
    private static final MethodType ARRAY_PREDICATE = MethodType.methodType(boolean.class, int[].class);

    @Platforms(Platform.HOSTED_ONLY.class)
    private static final class BoundNumber extends AtomicInteger {
        private static final long serialVersionUID = 1L;

        @Override
        public String toString() {
            throw new AssertionError("Invoker naming called application code");
        }
    }

    /** Initialized by the first invocation of {@link #twice}, which the test observes. */
    @Platforms(Platform.HOSTED_ONLY.class)
    static final class Lazy {
        static int twice(int value) {
            return 2 * value;
        }
    }

    static int add(int left, int right) {
        return left + right;
    }

    static int subtract(int left, int right) {
        return left - right;
    }

    static int select(int value) {
        return value;
    }

    static int sum(int[] values) {
        int result = 0;
        for (int value : values) {
            result += value;
        }
        return result;
    }

    static int product(int[] values) {
        int result = 1;
        for (int value : values) {
            result *= value;
        }
        return result;
    }

    static boolean nonEmpty(int[] values) {
        return values.length > 0;
    }

    public static class TestFeature implements Feature {
        private BeforeAnalysisAccess access;
        private Object processor;
        private Method isNameAlwaysStable;

        @Override
        public void beforeAnalysis(BeforeAnalysisAccess beforeAnalysisAccess) {
            access = beforeAnalysisAccess;
            try {
                Class<?> processorClass = Class.forName(RENAMING_PROCESSOR);
                processor = processorClass.getMethod("singleton").invoke(null);
                isNameAlwaysStable = processorClass.getMethod("isNameAlwaysStable", String.class);
                testBoundConstants();
                testOpaqueValues();
                testWarmUp();
                testDelegatingHandles();
                testIntrinsicData();
            } catch (AssertionError e) {
                throw e;
            } catch (Throwable e) {
                throw new AssertionError(e);
            }
        }

        /** Distinct bound constants give distinct names, and equal ones are reported as duplicates. */
        private void testBoundConstants() throws ReflectiveOperationException {
            MethodHandle add = MethodHandles.lookup().findStatic(MethodHandleStableNameTest.class, "add", BINARY);
            String eleven = invokerName(MethodHandles.insertArguments(add, 0, 11));
            String twentyTwo = invokerName(MethodHandles.insertArguments(add, 0, 22));
            assertNotEquals(baseName(eleven), baseName(twentyTwo));
            assertTrue(eleven, isNameAlwaysStable(eleven));
            assertTrue(twentyTwo, isNameAlwaysStable(twentyTwo));

            MethodHandle identity = MethodHandles.identity(Object.class);
            String first = invokerName(identity.bindTo("first"));
            String second = invokerName(identity.bindTo("second"));
            assertNotEquals(baseName(first), baseName(second));
            assertTrue(first, isNameAlwaysStable(first));
            assertTrue(second, isNameAlwaysStable(second));

            String high = invokerName(identity.bindTo(String.valueOf((char) 0xd800)));
            String low = invokerName(identity.bindTo(String.valueOf((char) 0xdc00)));
            assertNotEquals(baseName(high), baseName(low));
            assertTrue(low, isNameAlwaysStable(low));

            String duplicate = invokerName(identity.bindTo("duplicate"));
            String copy = invokerName(identity.bindTo("duplicate"));
            assertDuplicates(duplicate, copy);
            assertFalse(duplicate, isNameAlwaysStable(duplicate));
            assertFalse(copy, isNameAlwaysStable(copy));
        }

        /** Naming does not read values that cannot identify an invoker, and never reports them stable. */
        private void testOpaqueValues() throws ReflectiveOperationException {
            MethodHandle identity = MethodHandles.identity(Object.class);
            Object[] buffer = new Object[1];
            buffer[0] = buffer;
            String cyclic = invokerName(identity.bindTo(buffer));
            buffer[0] = new Object();
            String mutated = invokerName(identity.bindTo(buffer));
            assertDuplicates(cyclic, mutated);

            Runnable firstLambda = () -> {
            };
            Runnable secondLambda = () -> {
            };
            assertTrue(firstLambda.getClass().isHidden());
            assertTrue(secondLambda.getClass().isHidden());
            String firstHidden = invokerName(identity.bindTo(firstLambda.getClass()));
            String secondHidden = invokerName(identity.bindTo(secondLambda.getClass()));
            assertDuplicates(firstHidden, secondHidden);

            String number = invokerName(identity.bindTo(new BoundNumber()));
            assertFalse(number, isNameAlwaysStable(number));
        }

        /** The JDK replaces the forms of handles as they run, which must not change the names. */
        private void testWarmUp() throws Throwable {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandle add = lookup.findStatic(MethodHandleStableNameTest.class, "add", BINARY);

            // Customization gives a frequently invoked handle its own copy of a shared form.
            MethodHandle sharedForm = MethodHandles.filterArguments(add, 0, MethodHandles.insertArguments(add, 0, 11), MethodHandles.insertArguments(add, 0, 22));
            MethodHandle warmChild = MethodHandles.insertArguments(add, 0, 22);
            Object coldForm = form(warmChild);
            for (int i = 0; i < 1024; i++) {
                assertEquals(23, (int) warmChild.invokeExact(1));
            }
            assertNotSame("warm-up did not customize the handle", coldForm, form(warmChild));
            MethodHandle customizedForm = MethodHandles.filterArguments(add, 0, MethodHandles.insertArguments(add, 0, 11), warmChild);
            assertDuplicates(invokerName(sharedForm), invokerName(customizedForm));

            // A direct method handle drops its class initialization barrier on its first invocation.
            MethodHandle twice = lookup.findStatic(Lazy.class, "twice", UNARY);
            Object barrierForm = form(twice);
            String uninitialized = invokerName(MethodHandles.filterArguments(add, 0, twice));
            assertEquals(4, (int) twice.invokeExact(2));
            assertNotSame("invocation did not initialize the class", barrierForm, form(twice));
            assertDuplicates(uninitialized, invokerName(MethodHandles.filterArguments(add, 0, twice)));
        }

        /** A delegating handle is identified by its target, which its form re-invokes without naming. */
        private void testDelegatingHandles() throws ReflectiveOperationException {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandle sum = lookup.findStatic(MethodHandleStableNameTest.class, "sum", ARRAY_UNARY).asVarargsCollector(int[].class);
            MethodHandle product = lookup.findStatic(MethodHandleStableNameTest.class, "product", ARRAY_UNARY).asVarargsCollector(int[].class);
            MethodHandle nonEmpty = lookup.findStatic(MethodHandleStableNameTest.class, "nonEmpty", ARRAY_PREDICATE);
            assertSame("collectors share one form", form(sum), form(product));
            String sumFirst = invokerName(MethodHandles.guardWithTest(nonEmpty, sum, product));
            String productFirst = invokerName(MethodHandles.guardWithTest(nonEmpty, product, sum));
            assertNotEquals(baseName(sumFirst), baseName(productFirst));
        }

        /** The case count of a table switch selects the generated code, so it is part of the name. */
        private void testIntrinsicData() throws ReflectiveOperationException {
            MethodHandle select = MethodHandles.lookup().findStatic(MethodHandleStableNameTest.class, "select", UNARY);
            String twoCases = invokerName(MethodHandles.tableSwitch(select, select, select));
            String threeCases = invokerName(MethodHandles.tableSwitch(select, select, select, select));
            assertNotEquals(baseName(twoCases), baseName(threeCases));
        }

        private String invokerName(MethodHandle handle) throws ReflectiveOperationException {
            ReflectionUtil.lookupMethod(MethodHandle.class, "customize").invoke(handle);
            Object form = form(handle);
            ReflectionUtil.lookupMethod(form.getClass(), "compileToBytecode").invoke(form);
            Object entry = ReflectionUtil.readField(form.getClass(), "vmentry", form);
            Class<?> invoker = (Class<?>) ReflectionUtil.lookupMethod(entry.getClass(), "getDeclaringClass").invoke(entry);
            String name = ((JVMCIFeatureAccess.BeforeAnalysisAccess) access).getMetaAccess().lookupJavaType(invoker).getName();
            assertTrue(name, name.startsWith("Ljava/lang/invoke/LambdaForm$MH.s"));
            return name;
        }

        private boolean isNameAlwaysStable(String name) throws ReflectiveOperationException {
            return (boolean) isNameAlwaysStable.invoke(processor, name);
        }

        private static Object form(MethodHandle handle) {
            return ReflectionUtil.readField(MethodHandle.class, "form", handle);
        }

        /** Strips the discovery-order suffix, which depends on earlier lookups of the same structure. */
        private static String baseName(String name) {
            String withoutTerminator = name.substring(0, name.length() - 1);
            int suffixStart = withoutTerminator.lastIndexOf('_');
            String suffix = withoutTerminator.substring(suffixStart + 1);
            if (suffixStart >= 0 && !suffix.isEmpty() && suffix.chars().allMatch(Character::isDigit)) {
                return withoutTerminator.substring(0, suffixStart);
            }
            return withoutTerminator;
        }

        private static void assertDuplicates(String name, String other) {
            assertEquals(baseName(name), baseName(other));
            assertNotEquals(name, other);
        }
    }

    @Test
    public void testStableNameFeature() {
        assertTrue(true);
    }
}
