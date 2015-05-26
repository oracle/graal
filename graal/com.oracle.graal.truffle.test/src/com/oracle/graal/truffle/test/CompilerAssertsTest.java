/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.jvmci.code.BailoutException;
import org.junit.*;

import com.oracle.graal.truffle.test.nodes.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;

public class CompilerAssertsTest extends PartialEvaluationTest {

    public static class NeverPartOfCompilationTestNode extends AbstractTestNode {

        @Override
        public int execute(VirtualFrame frame) {
            CompilerAsserts.neverPartOfCompilation();
            return 0;
        }

    }

    public static class CompilationConstantTestNode extends AbstractTestNode {
        @Child private AbstractTestNode child;

        public CompilationConstantTestNode(AbstractTestNode child) {
            this.child = child;
        }

        @Override
        public int execute(VirtualFrame frame) {
            CompilerAsserts.compilationConstant(child.execute(frame));
            return 0;
        }

    }

    @Test
    public void neverPartOfCompilationTest() {
        NeverPartOfCompilationTestNode result = new NeverPartOfCompilationTestNode();
        RootTestNode rootNode = new RootTestNode(new FrameDescriptor(), "neverPartOfCompilation", result);
        try {
            compileHelper("neverPartOfCompilation", rootNode, new Object[0]);
            Assert.fail("Expected bailout exception due to never part of compilation");
        } catch (BailoutException e) {
            // Bailout exception expected.
        }
    }

    @Test
    public void compilationNonConstantTest() {
        FrameDescriptor descriptor = new FrameDescriptor();
        CompilationConstantTestNode result = new CompilationConstantTestNode(new NonConstantTestNode(5));
        RootTestNode rootNode = new RootTestNode(descriptor, "compilationConstant", result);
        try {
            compileHelper("compilationConstant", rootNode, new Object[0]);
            Assert.fail("Expected bailout exception because expression is not compilation constant");
        } catch (BailoutException e) {
            // Bailout exception expected.
        }
    }

    @Test
    public void compilationConstantTest() {
        FrameDescriptor descriptor = new FrameDescriptor();
        CompilationConstantTestNode result = new CompilationConstantTestNode(new ConstantTestNode(5));
        RootTestNode rootNode = new RootTestNode(descriptor, "compilationConstant", result);
        compileHelper("compilationConstant", rootNode, new Object[0]);
    }
}
