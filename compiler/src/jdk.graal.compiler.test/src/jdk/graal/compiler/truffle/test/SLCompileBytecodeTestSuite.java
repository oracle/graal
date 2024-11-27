/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.sl.test.SLTestRunner;
import com.oracle.truffle.sl.test.SLTestSuite;

@RunWith(SLTestRunner.class)
@SLTestSuite(value = {"sl"}, options = {//
                "engine.BackgroundCompilation", "false",
                "engine.SingleTierCompilationThreshold", "20", // enough to transition to cached
                "engine.MultiTier", "false",
                "engine.CompileImmediately", "false",
                "sl.UseBytecode", "true"
})
public class SLCompileBytecodeTestSuite {

    public static void main(String[] args) throws Exception {
        SLTestRunner.runInMain(SLCompileBytecodeTestSuite.class, args);
    }

    @BeforeClass
    public static void setupTestRunner() {
        SLCompileASTTestSuite.installBuiltins();
    }

    /*
     * Our "mx unittest" command looks for methods that are annotated with @Test. By just defining
     * an empty method, this class gets included and the test suite is properly executed.
     */
    @Test
    public void unittest() {
    }
}
