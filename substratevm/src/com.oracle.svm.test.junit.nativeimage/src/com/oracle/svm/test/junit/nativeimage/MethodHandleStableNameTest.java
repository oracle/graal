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
import static org.junit.Assert.assertTrue;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
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
    @Platforms(Platform.HOSTED_ONLY.class)
    private static final class BoundNumber extends AtomicInteger {
        private static final long serialVersionUID = 1L;

        @Override
        public String toString() {
            throw new AssertionError("Invoker naming called application code");
        }
    }

    public static class TestFeature implements Feature {
        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            try {
                MethodHandle identity = MethodHandles.identity(Object.class);
                String first = invokerName(access, identity.bindTo("first"));
                String second = invokerName(access, identity.bindTo("second"));
                assertFalse(hasDiscoveryOrderSuffix(first));
                assertFalse(hasDiscoveryOrderSuffix(second));
                assertNotEquals(first, second);

                String high = invokerName(access, identity.bindTo(String.valueOf((char) 0xd800)));
                String low = invokerName(access, identity.bindTo(String.valueOf((char) 0xdc00)));
                assertNotEquals(high, low);
                assertFalse(hasDiscoveryOrderSuffix(low));

                Object[] buffer = new Object[1];
                buffer[0] = buffer;
                String cyclic = invokerName(access, identity.bindTo(buffer));
                buffer[0] = new Object();
                String mutated = invokerName(access, identity.bindTo(buffer));
                assertEquals(cyclic.substring(0, cyclic.length() - 1) + "_1;", mutated);

                Runnable firstLambda = () -> {
                };
                Runnable secondLambda = () -> {
                };
                assertTrue(firstLambda.getClass().isHidden());
                assertTrue(secondLambda.getClass().isHidden());
                String firstHidden = invokerName(access, identity.bindTo(firstLambda.getClass()));
                String secondHidden = invokerName(access, identity.bindTo(secondLambda.getClass()));
                assertEquals(firstHidden.substring(0, firstHidden.length() - 1) + "_1;", secondHidden);

                invokerName(access, identity.bindTo(new BoundNumber()));
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }

        private static String invokerName(BeforeAnalysisAccess access, MethodHandle handle) throws ReflectiveOperationException {
            ReflectionUtil.lookupMethod(MethodHandle.class, "customize").invoke(handle);
            Object form = ReflectionUtil.readField(MethodHandle.class, "form", handle);
            ReflectionUtil.lookupMethod(form.getClass(), "compileToBytecode").invoke(form);
            Object entry = ReflectionUtil.readField(form.getClass(), "vmentry", form);
            Class<?> invoker = (Class<?>) ReflectionUtil.lookupMethod(entry.getClass(), "getDeclaringClass").invoke(entry);
            String name = ((JVMCIFeatureAccess.BeforeAnalysisAccess) access).getMetaAccess().lookupJavaType(invoker).getName();
            assertTrue(name, name.startsWith("Ljava/lang/invoke/LambdaForm$MH.s"));
            return name;
        }

        private static boolean hasDiscoveryOrderSuffix(String stableName) {
            int suffixStart = stableName.lastIndexOf('_');
            return suffixStart >= 0 && stableName.endsWith(";") && stableName.substring(suffixStart + 1, stableName.length() - 1).chars().allMatch(Character::isDigit);
        }
    }

    @Test
    public void testStableNameFeature() {
        assertTrue(true);
    }
}
