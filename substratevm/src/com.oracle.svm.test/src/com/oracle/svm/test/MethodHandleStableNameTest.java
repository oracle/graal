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

package com.oracle.svm.test;

import static org.junit.Assert.assertTrue;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

import org.graalvm.nativeimage.hosted.Feature;
import org.junit.Test;

@NativeImageBuildArgs({
                "--features=com.oracle.svm.test.MethodHandleStableNameTest$TestFeature"
})
public class MethodHandleStableNameTest {
    private static final String METHOD_HANDLE_RENAMING_PROCESSOR = "com.oracle.svm.hosted.methodhandles.MethodHandleInvokerRenamingSubstitutionProcessor";

    public static class TestFeature implements Feature {
        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodHandle target = lookup.findStatic(MethodHandleStableNameTest.class, "add", MethodType.methodType(int.class, int.class, int.class));
                MethodHandle addEleven = MethodHandles.insertArguments(target, 0, 11);
                MethodHandle addTwentyTwo = MethodHandles.insertArguments(target, 0, 22);

                Class<?> processorClass = Class.forName(METHOD_HANDLE_RENAMING_PROCESSOR);
                Method stableNameForTesting = processorClass.getMethod("stableNameForTesting", MethodHandle.class, boolean.class, boolean.class);
                String addElevenStableName = (String) stableNameForTesting.invoke(null, addEleven, false, false);
                String addTwentyTwoStableName = (String) stableNameForTesting.invoke(null, addTwentyTwo, false, false);

                if (addElevenStableName.equals(addTwentyTwoStableName)) {
                    throw new AssertionError("Stable MethodHandle names must include bound values that distinguish otherwise identical LambdaForms.");
                }
                if (hasDiscoveryOrderSuffix(addElevenStableName) || hasDiscoveryOrderSuffix(addTwentyTwoStableName)) {
                    throw new AssertionError("Stable MethodHandle names must not rely on discovery-order suffixes: " + addElevenStableName + ", " + addTwentyTwoStableName);
                }
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }

        private static boolean hasDiscoveryOrderSuffix(String stableName) {
            int suffixStart = stableName.lastIndexOf('_');
            return suffixStart >= 0 && stableName.endsWith(";") && stableName.substring(suffixStart + 1, stableName.length() - 1).chars().allMatch(Character::isDigit);
        }
    }

    static int add(int left, int right) {
        return left + right;
    }

    @Test
    public void testStableNameFeature() {
        assertTrue(true);
    }
}
