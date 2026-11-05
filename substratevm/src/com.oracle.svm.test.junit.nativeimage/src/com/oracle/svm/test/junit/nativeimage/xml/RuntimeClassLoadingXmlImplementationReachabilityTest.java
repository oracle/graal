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
package com.oracle.svm.test.xml;

import java.lang.reflect.Constructor;

import org.graalvm.nativeimage.hosted.Feature;
import org.junit.Test;

import com.oracle.svm.test.NativeImageBuildArgs;

@NativeImageBuildArgs({
                "--add-modules=java.xml",
                "-H:+UnlockExperimentalVMOptions",
                "-H:+AllowJRTFileSystem",
                "-H:+RuntimeClassLoading",
                "--initialize-at-run-time=jdk.internal.loader.ClassLoaders",
                "--features=com.oracle.svm.test.xml.RuntimeClassLoadingXmlImplementationReachabilityTest$TestFeature"
})
public class RuntimeClassLoadingXmlImplementationReachabilityTest {
    private static final String TRANSFORMER_FACTORY_IMPL = "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl";

    public static class TestFeature implements Feature {
        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            Class<?> clazz = access.findClassByName(TRANSFORMER_FACTORY_IMPL);
            if (clazz == null) {
                throw new AssertionError(TRANSFORMER_FACTORY_IMPL + " is not available to the image build");
            }
            access.registerAsUsed(clazz);
        }

        @Override
        public void afterAnalysis(AfterAnalysisAccess access) {
            Class<?> clazz = access.findClassByName(TRANSFORMER_FACTORY_IMPL);
            if (clazz == null || !access.isReachable(clazz)) {
                throw new AssertionError(TRANSFORMER_FACTORY_IMPL + " was not made AOT-reachable by the test");
            }
            try {
                Constructor<?> constructor = clazz.getDeclaredConstructor();
                if (!access.isReachable(constructor)) {
                    throw new AssertionError(TRANSFORMER_FACTORY_IMPL + " constructor was not made reachable by XML metadata registration");
                }
            } catch (NoSuchMethodException e) {
                throw new AssertionError(e);
            }
        }
    }

    @Test
    public void testAotReachableXmlImplementationGetsMetadata() {
    }
}
