/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage.test.spec;

import java.util.Collection;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.svm.hosted.webimage.test.util.JTTParameterizedTestSuite;
import com.oracle.svm.webimage.jtt.testdispatcher.BytecodeTest;

/**
 * Tests all bytecodes.
 */
@RunWith(Parameterized.class)
public class JS_JTT_Bytecodes extends JTTParameterizedTestSuite {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<String> data() {
        return getClassNames(BytecodeTest.findTestClasses());
    }

    @Parameterized.Parameter public String className;

    /**
     * Compile our bytecode test class only once. Each test invokes it with different arguments.
     */
    @BeforeClass
    public static void setupClass() {
        compileToJS(BytecodeTest.class);
    }

    @Test
    @Override
    public void test() {
        runTest(className, BytecodeTest.class);
    }
}
