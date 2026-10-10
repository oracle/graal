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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.junit.Test;

import com.oracle.svm.core.logging.HasXlogSupport;
import com.oracle.svm.core.logging.LogTagSet;
import com.oracle.svm.guest.staging.jdk.RuntimeSupport;

/// Verifies that image defaults install the GC fallback before isolate initialization hooks.
@NativeImageBuildArgs({"--add-exports=org.graalvm.nativeimage.guest.staging/com.oracle.svm.guest.staging.jdk=ALL-UNNAMED", "-R:+PrintGC",
                "--features=com.oracle.svm.test.LegacyGCLoggingInitializationTest$InitializationFeature"})
public class LegacyGCLoggingInitializationTest {
    /// Captures whether the image default was applied before initialization hooks.
    private static boolean gcLoggingInstalled;

    /// Registers a check at the public initialization hook boundary.
    @Platforms(Platform.HOSTED_ONLY.class)
    public static class InitializationFeature implements Feature {
        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            RuntimeSupport.getRuntimeSupport().addInitializationHook(initializationHook());
        }
    }

    /// Captures the route at the initialization hook boundary.
    private static RuntimeSupport.Hook initializationHook() {
        return _ -> gcLoggingInstalled = LogTagSet.gc.isInfo();
    }

    /// Checks the result captured before later initialization could install fallback logging.
    @Test
    public void installsImageDefaultEarly() {
        assertFalse(HasXlogSupport.get());
        assertTrue(gcLoggingInstalled);
    }
}
