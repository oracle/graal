/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.jdk20.test;

import java.io.IOException;

import org.graalvm.compiler.api.test.ModuleSupport;
import org.graalvm.compiler.core.test.SubprocessTest;
import org.graalvm.compiler.hotspot.test.HotSpotGraalCompilerTest;
import org.graalvm.compiler.test.AddExports;
import org.graalvm.compiler.test.SubprocessUtil;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;

@AddExports("java.base/jdk.internal.misc")
public class PreviewEnabledTest extends HotSpotGraalCompilerTest {

    @Before
    public void checkJavaAgent() {
        Assume.assumeFalse("Java Agent found -> skipping", SubprocessUtil.isJavaAgentAttached());
    }

    public void testGetCarrierThread() {
        compileAndInstallSubstitution(Thread.class, "currentCarrierThread");
        CarrierThreadTest.test();
    }

    public void testScopedValue() {
        compileAndInstallSubstitution(Thread.class, "setScopedValueCache");
        ScopedValueCacheTest.testScopedValue();

        compileAndInstallSubstitution(Thread.class, "scopedValueCache");
        ScopedValueCacheTest.testScopedValue();
    }

    public void testJFR() {
        try {
            // resolve class, profile exceptions
            VirtualThreadsJFRTest.test();
            Class<?> clazz = Class.forName("java.lang.VirtualThread");
            InstalledCode code = getCode(getResolvedJavaMethod(clazz, "mount"), null, true, true, getInitialOptions());
            assertTrue(code.isValid());
            VirtualThreadsJFRTest.test();
            assertTrue(code.isValid());
        } catch (ClassNotFoundException e) {
            fail(e.getMessage());
        }
    }

    public void testBody() {
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("java.base");
        testGetCarrierThread();
        testScopedValue();
        testJFR();
    }

    @Test
    public void testInSubprocess() throws IOException, InterruptedException {
        SubprocessTest.launchSubprocess(getClass(), this::testBody, "--enable-preview", "--add-modules=jdk.incubator.concurrent",
                        "--add-opens=jdk.incubator.concurrent/jdk.incubator.concurrent=ALL-UNNAMED");
    }
}
