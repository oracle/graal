/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.pelang.test;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.truffle.pelang.PELangException;
import org.graalvm.compiler.truffle.pelang.PELangRootNode;
import org.graalvm.compiler.truffle.pelang.util.PELangBCFGenerator;
import org.graalvm.compiler.truffle.pelang.util.PELangSample;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.junit.Assert;
import org.junit.Test;

public class PELangBCFTest extends PELangTest {

    @Test
    public void testSimpleAdd() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleAdd());
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testSimpleBlock() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleBlock());
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testSimpleLocalReadWrite() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleLocalReadWrite());
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testSimpleGlobalReadWrite() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleGlobalReadWrite());
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        // TODO: add partial evaluation asserts
    }

    @Test
    public void testSimpleBranch() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleBranch());
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testSimpleLoop() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleLoop());
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testSimpleSwitch() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleSwitch());
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testSimpleInvoke() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleInvoke());
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testSimpleObject() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleObject());
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        // TODO: add partial evaluation asserts
    }
    }

    @Test(expected = PELangException.class)
    public void testInvalidBranch() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.invalidBranch());
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
    }

    @Test(expected = PELangException.class)
    public void testInvalidLoop() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.invalidLoop());
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
    }

    @Test
    public void testNestedAdd() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedAdds());
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testNestedBlocks() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedBlocks());
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testNestedLocalReadWrites() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedLocalReadWrites());
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testNestedBranches() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedBranches());
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testNestedLoops() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedLoops());
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testNestedSwitches() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedSwitches());
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testBranchWithGlobalReadWrite() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.branchWithGlobalReadWrite());
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        // TODO: add partial evaluation asserts
    }

    @Test
    public void testLoopWithGlobalReadWrite() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.loopWithGlobalReadWrite());
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        try {
            // do a first run and swallow code install exceptions
            warmupCallTarget(callTarget);
            StructuredGraph graph = partiallyEvaluate(callTarget);
            compileGraph(graph, callTarget);
        } catch (Exception e) {
            // swallow exception
        }
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        // TODO: add partial evaluation asserts
    }

    @Test
    public void testNestedLoopsWithMultipleBackEdges() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedLoopsWithMultipleBackEdges());
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        // TODO: add partial evaluation asserts
    }

    @Test
    public void testInvokeObjectFunctionProperty() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.invokeObjectFunctionProperty());
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        // TODO: add partial evaluation asserts
    }

    @Test
    public void testIrreducibleLoop() {
        // no need for a generator as sample is directly built with basic blocks
        PELangRootNode rootNode = PELangSample.irreducibleLoop();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        // TODO: add partial evaluation asserts
    }

    protected Object constant10() {
        return 10L;
    }

}
