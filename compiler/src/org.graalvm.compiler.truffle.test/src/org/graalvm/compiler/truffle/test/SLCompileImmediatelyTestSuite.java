/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleBackgroundCompilation;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleCompileImmediately;

import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.sl.test.SLSimpleTestSuite;
import com.oracle.truffle.sl.test.SLTestRunner;
import com.oracle.truffle.sl.test.SLTestSuite;

@RunWith(SLTestRunner.class)
@SLTestSuite(value = {"tests"}, testCaseDirectory = SLSimpleTestSuite.class)
public class SLCompileImmediatelyTestSuite {

    private static TruffleCompilerOptions.TruffleOptionsOverrideScope overrideScope;

    @BeforeClass
    public static void beforeClass() {
        assert overrideScope == null;
        /*
         * We turn on the flag to compile every Truffle function immediately, on its first execution
         * in the interpreter. And we wait until compilation finishes so that we really execute the
         * compiled method. This leads to a lot of compilation, but that is the purpose of this
         * test. It also leads to a lot of deoptimization, since the first time a method is compiled
         * it has all nodes in the uninitialized specialization. This means that most methods are
         * compiled multiple times, in different specialization states.
         */
        overrideScope = TruffleCompilerOptions.overrideOptions(TruffleCompileImmediately, true, TruffleBackgroundCompilation, false);

        Assume.assumeFalse("Crashes on AArch64 in C2 (GR-8733)", System.getProperty("os.arch").equalsIgnoreCase("aarch64"));

        TruffleTestUtil.assumeJavaDesktopModuleIsAvailable();
    }

    @AfterClass
    public static void afterClass() {
        assert overrideScope != null;
        overrideScope.close();
    }

    /*
     * Our "mx unittest" command looks for methods that are annotated with @Test. By just defining
     * an empty method, this class gets included and the test suite is properly executed.
     */
    @Test
    public void unittest() {
    }
}
