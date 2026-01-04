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
package jdk.graal.compiler.replacements.test;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.core.test.SubprocessTest;
import jdk.graal.compiler.test.SubprocessUtil;
import jdk.vm.ci.amd64.AMD64;

/** Runs selected String intrinsic tests with {@code -XX:AVX3Threshold=0}. */
public class StringIntrinsicAVX3ThresholdTest extends SubprocessTest {

    @Before
    public void before() {
        Assume.assumeTrue("AMD64-specific test", getArchitecture() instanceof AMD64);
        /*
         * We do not check the CPU flags for AVX-512 support. This is because the CPU flags we get
         * from HotSpot do not tell us what the CPU really supports: Some older CPUs that are
         * considered to have slow AVX-512 support are treated by HotSpot as not having AVX-512 at
         * all, unless the user specifically passes -XX:UseAVX=3. We pass this flag to the
         * subprocess, which will just ignore it with a warning if the target doesn't actually
         * support AVX-512.
         */
    }

    public void testWithAVX3Threshold(Class<? extends GraalCompilerTest> testClass) {
        Runnable nopRunnable = () -> {
            /*
             * The runnable is only relevant when running a test in the same class as the parent
             * process.
             */
        };
        SubprocessUtil.Subprocess subprocess = null;
        try {
            subprocess = launchSubprocess(testClass, ALL_TESTS, nopRunnable, "-XX:UseAVX=3", "-XX:+UnlockDiagnosticVMOptions", "-XX:AVX3Threshold=0");
        } catch (IOException | InterruptedException e) {
            Assert.fail("subprocess exception: " + e);
        }
        Assert.assertEquals("subprocess exit code", 0, subprocess.exitCode);
    }

    @Test
    public void compressInflate() {
        testWithAVX3Threshold(StringCompressInflateTest.class);
    }

    @Test
    public void compareTo1() {
        testWithAVX3Threshold(StringCompareToTest.class);
    }

    @Test
    public void compareTo2() {
        testWithAVX3Threshold(StringCompareToAVX512Test.class);
    }

    @Test
    public void countPositives() {
        testWithAVX3Threshold(CountPositivesTest.class);
    }
}
