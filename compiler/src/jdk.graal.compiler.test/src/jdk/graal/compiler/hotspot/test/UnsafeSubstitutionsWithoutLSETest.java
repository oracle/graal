/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.core.test.SubprocessTest;
import jdk.graal.compiler.replacements.test.UnsafeSubstitutionsTest;
import jdk.graal.compiler.test.SubprocessUtil;
import jdk.vm.ci.aarch64.AArch64;

public class UnsafeSubstitutionsWithoutLSETest extends SubprocessTest {
    @Before
    public void before() {
        Assume.assumeTrue("AArch64-specific test", getArchitecture() instanceof AArch64);
    }

    public void testWithoutLSE(Class<? extends GraalCompilerTest> testClass) {
        Runnable nopRunnable = () -> {
            /*
             * The runnable is only relevant when running a test in the same class as the parent
             * process.
             */
        };
        SubprocessUtil.Subprocess subprocess = null;
        try {
            subprocess = launchSubprocess(testClass, ALL_TESTS, nopRunnable, "-XX:-UseLSE");
        } catch (IOException | InterruptedException e) {
            Assert.fail("subprocess exception: " + e);
        }
        Assert.assertEquals("subprocess exit code", 0, subprocess.exitCode);
    }

    @Test
    public void unsafeSubstitutions() {
        testWithoutLSE(UnsafeSubstitutionsTest.class);
    }
}
