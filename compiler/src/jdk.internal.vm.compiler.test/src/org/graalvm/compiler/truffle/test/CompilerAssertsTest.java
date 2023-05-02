/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import org.graalvm.compiler.truffle.test.nodes.ConstantTestNode;
import org.graalvm.compiler.truffle.test.nodes.NonConstantTestNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;

import jdk.vm.ci.code.BailoutException;
import org.graalvm.polyglot.Context;

public class CompilerAssertsTest extends PartialEvaluationTest {

    @Before
    public void setup() {
        setupContext(Context.newBuilder().allowExperimentalOptions(true).option("engine.CompileImmediately", "false").build());
    }

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
    @SuppressWarnings("try")
    public void neverPartOfCompilationTest() {
        NeverPartOfCompilationTestNode result = new NeverPartOfCompilationTestNode();
        RootTestNode rootNode = new RootTestNode(new FrameDescriptor(), "neverPartOfCompilation", result);
        try (PreventDumping noDump = new PreventDumping()) {
            compileHelper("neverPartOfCompilation", rootNode, new Object[0]);
            Assert.fail("Expected bailout exception due to never part of compilation");
        } catch (BailoutException e) {
            // Bailout exception expected.
        }
    }

    @Test
    @SuppressWarnings("try")
    public void compilationNonConstantTest() {
        FrameDescriptor descriptor = new FrameDescriptor();
        CompilationConstantTestNode result = new CompilationConstantTestNode(new NonConstantTestNode(5));
        RootTestNode rootNode = new RootTestNode(descriptor, "compilationConstant", result);
        try (PreventDumping noDump = new PreventDumping()) {
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

    public static int nonBoxedPrimitivePEConstantTest() {
        boolean bool = true;
        byte b = 1;
        short s = 2;
        char c = 3;
        int i = 4;
        long l = 5L;
        float f = 6.0f;
        double d = 7.0d;

        CompilerAsserts.partialEvaluationConstant(bool);
        CompilerAsserts.partialEvaluationConstant(b);
        CompilerAsserts.partialEvaluationConstant(s);
        CompilerAsserts.partialEvaluationConstant(c);
        CompilerAsserts.partialEvaluationConstant(i);
        CompilerAsserts.partialEvaluationConstant(l);
        CompilerAsserts.partialEvaluationConstant(f);
        CompilerAsserts.partialEvaluationConstant(d);

        return 1;
    }

    @Test
    public void nonBoxingPEConstantTest() {
        StructuredGraph graph = parseEager("nonBoxedPrimitivePEConstantTest", StructuredGraph.AllowAssumptions.NO);
        NodeIterable<Node> nodes = graph.getNodes();
        for (Node n : nodes) {
            assertFalse(n instanceof BoxNode);
        }
    }
}
