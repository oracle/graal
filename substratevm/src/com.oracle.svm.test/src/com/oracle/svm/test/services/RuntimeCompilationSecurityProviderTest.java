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
package com.oracle.svm.test.services;

import java.security.Provider;
import java.security.Security;
import java.util.List;

import org.graalvm.nativeimage.hosted.Feature;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.test.NativeImageBuildArgs;

@NativeImageBuildArgs({
                "--future-defaults=metadata-security-provider-registration",
                "--features=com.oracle.svm.test.services.RuntimeCompilationSecurityProviderTest$EnableRuntimeCompilationFeature"
})
public class RuntimeCompilationSecurityProviderTest {
    public static final class EnableRuntimeCompilationFeature implements Feature {
        @Override
        public List<Class<? extends Feature>> getRequiredFeatures() {
            return List.of(runtimeCompilationFeature());
        }

        @SuppressWarnings("unchecked")
        private static Class<? extends Feature> runtimeCompilationFeature() {
            try {
                return (Class<? extends Feature>) Class.forName(
                                "com.oracle.svm.graal.hosted.runtimecompilation.RuntimeCompilationFeature");
            } catch (ClassNotFoundException e) {
                throw new AssertionError("Runtime compilation feature is unavailable", e);
            }
        }
    }

    /** §FS-002-security-providers.2.4: Tests the internal-runtime-randomness branch. */
    @Test
    public void testRuntimeCompilationRandomnessRegistersSunProvider() {
        Provider provider = Security.getProvider("SUN");
        Assert.assertNotNull("Runtime compilation randomness must retain its SecureRandom provider.", provider);
        Assert.assertEquals("sun.security.provider.Sun", provider.getClass().getName());
        Assert.assertNotNull("Complete provider registration must retain unrelated services.",
                        provider.getService("MessageDigest", "SHA-256"));
    }
}
