/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.truffle.test;

import org.junit.*;
import org.junit.runner.*;

import com.oracle.graal.truffle.test.builtins.*;
import com.oracle.truffle.sl.test.*;

@RunWith(SLTestRunner.class)
@SLTestSuite({"graal/com.oracle.graal.truffle.test/sl", "sl"})
public class SLTruffleGraalTestSuite {

    public static void main(String[] args) throws Exception {
        SLTestRunner.runInMain(SLTruffleGraalTestSuite.class, args);
    }

    @BeforeClass
    public static void setupTestRunner() {
        SLTestRunner.setRepeats(1);
        SLTestRunner.installBuiltin(SLGetOptionBuiltinFactory.getInstance());
        SLTestRunner.installBuiltin(SLSetOptionBuiltinFactory.getInstance());
        SLTestRunner.installBuiltin(SLIsOptimizedBuiltinFactory.getInstance());
        SLTestRunner.installBuiltin(SLWaitForOptimizationBuiltinFactory.getInstance());
        SLTestRunner.installBuiltin(SLDisableSplittingBuiltinFactory.getInstance());
        SLTestRunner.installBuiltin(SLCallUntilOptimizedBuiltinFactory.getInstance());
        SLTestRunner.installBuiltin(SLIsInlinedBuiltinFactory.getInstance());
        SLTestRunner.installBuiltin(SLGenerateDummyNodesBuiltinFactory.getInstance());
    }

    /*
     * Our "mx unittest" command looks for methods that are annotated with @Test. By just defining
     * an empty method, this class gets included and the test suite is properly executed.
     */
    @Test
    public void unittest() {
    }
}
