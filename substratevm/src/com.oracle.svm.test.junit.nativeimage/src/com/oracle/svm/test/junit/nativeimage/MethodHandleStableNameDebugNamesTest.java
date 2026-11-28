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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Map;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.junit.Test;

import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.test.api.NativeImageBuildArgs;

/**
 * Runs {@link MethodHandleStableNameTest} with the JDK's debug names, which number the entry points
 * of invokers in discovery order.
 */
@NativeImageBuildArgs({
                "-J-Djava.lang.invoke.MethodHandle.DEBUG_NAMES=true",
                "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
                "--features=com.oracle.svm.test.junit.nativeimage.MethodHandleStableNameDebugNamesTest$DebugCacheFeature"
})
public class MethodHandleStableNameDebugNamesTest extends MethodHandleStableNameTest {
    public static class DebugCacheFeature implements Feature {
        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            Class<?> lambdaForm = ReflectionUtil.lookupClass("java.lang.invoke.LambdaForm");
            Map<?, ?> debugNames = ReflectionUtil.readStaticField(lambdaForm, "DEBUG_NAMES");
            assertFalse("the builder must have debug names to discard", debugNames.isEmpty());
            RuntimeReflection.register(ReflectionUtil.lookupField(lambdaForm, "DEBUG_NAMES"));
            RuntimeReflection.register(ReflectionUtil.lookupField(lambdaForm, "DEBUG_NAME_COUNTERS"));
        }
    }

    /** Builder debug names must not reach the image, but runtime debug naming must still work. */
    @Test
    public void testRuntimeDebugNames() throws Throwable {
        Map<?, ?> debugNames = debugCache("DEBUG_NAMES");
        Map<?, ?> counters = debugCache("DEBUG_NAME_COUNTERS");
        assertTrue("debug cache contains builder forms", debugNames.isEmpty());
        assertTrue("debug counters contain builder history", counters.isEmpty());

        MethodHandle identity = MethodHandles.identity(int.class);
        String description = identity.toString();
        assertTrue(description, description.contains("=Lambda("));
        assertFalse("runtime formatting did not populate the debug cache", debugNames.isEmpty());
        assertFalse("runtime formatting did not populate the counters", counters.isEmpty());
        assertEquals(description, identity.toString());
        assertEquals(42, (int) identity.invokeExact(42));
    }

    private static Map<?, ?> debugCache(String name) throws ReflectiveOperationException {
        Field field = Class.forName("java.lang.invoke.LambdaForm").getDeclaredField(name);
        field.setAccessible(true);
        Map<?, ?> cache = (Map<?, ?>) field.get(null);
        assertNotNull("debug naming must remain enabled", cache);
        return cache;
    }

    /** Declared again so that the test harness, which looks for declared tests, finds this class. */
    @Override
    @Test
    public void testStableNameFeature() {
        super.testStableNameFeature();
    }
}
