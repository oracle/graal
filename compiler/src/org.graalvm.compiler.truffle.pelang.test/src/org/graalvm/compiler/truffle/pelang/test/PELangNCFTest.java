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
import org.graalvm.compiler.truffle.pelang.util.PELangSample;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.junit.Assert;
import org.junit.Test;

public class PELangNCFTest extends PELangTest {

    @Test
    public void testSimpleAdd() {
        PELangRootNode rootNode = PELangSample.simpleAdd();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testSimpleBlock() {
        PELangRootNode rootNode = PELangSample.simpleBlock();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testSimpleLocalReadWrite() {
        PELangRootNode rootNode = PELangSample.simpleLocalReadWrite();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testSimpleGlobalReadWrite() {
        PELangRootNode rootNode = PELangSample.simpleGlobalReadWrite();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        // TODO: add partial evaluation asserts
    }

    @Test
    public void testSimpleBranch() {
        PELangRootNode rootNode = PELangSample.simpleBranch();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testSimpleLoop() {
        PELangRootNode rootNode = PELangSample.simpleLoop();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);
        warmupCallTarget(callTarget);

        try {
            // do a first run and swallow code install exceptions
            StructuredGraph graph = partiallyEvaluate(callTarget);
            compileGraph(graph, callTarget);
        } catch (Exception e) {
            // swallow exception
        }
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testSimpleSwitch() {
        PELangRootNode rootNode = PELangSample.simpleSwitch();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testSimpleInvoke() {
        PELangRootNode rootNode = PELangSample.simpleInvoke();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testSimpleObject() {
        PELangRootNode rootNode = PELangSample.simpleObject();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        // TODO: add partial evaluation asserts
    }

    @Test
    public void testSimpleArrayRead() {
        PELangRootNode rootNode = PELangSample.simpleArrayRead();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        Assert.assertTrue(graph.isTrivial());
    }

    @Test
    public void testSimpleMultiArrayRead() {
        PELangRootNode rootNode = PELangSample.simpleMultiArrayRead();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        Assert.assertTrue(graph.isTrivial());
    }

    @Test
    public void testSimpleArrayWrite() {
        PELangRootNode rootNode = PELangSample.simpleArrayWrite();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        Assert.assertTrue(graph.isTrivial());
    }

    @Test
    public void testSimpleMultiArrayWrite() {
        PELangRootNode rootNode = PELangSample.simpleMultiArrayWrite();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        Assert.assertTrue(graph.isTrivial());
    }

    @Test
    public void testComplexStringArray() {
        PELangRootNode rootNode = PELangSample.complexStringArray();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals("Foo", callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        Assert.assertTrue(graph.isTrivial());
    }

    @Test(expected = PELangException.class)
    public void testInvalidBranch() {
        PELangRootNode rootNode = PELangSample.invalidBranch();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
    }

    @Test(expected = PELangException.class)
    public void testInvalidLoop() {
        PELangRootNode rootNode = PELangSample.invalidLoop();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
    }

    @Test
    public void testNestedAdds() {
        PELangRootNode rootNode = PELangSample.nestedAdds();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testNestedBlocks() {
        PELangRootNode rootNode = PELangSample.nestedBlocks();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testNestedLocalReadWrites() {
        PELangRootNode rootNode = PELangSample.nestedLocalReadWrites();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testNestedBranches() {
        PELangRootNode rootNode = PELangSample.nestedBranches();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testNestedLoops() {
        PELangRootNode rootNode = PELangSample.nestedLoops();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);
        warmupCallTarget(callTarget);

        try {
            // do a first run and swallow code install exceptions
            StructuredGraph graph = partiallyEvaluate(callTarget);
            compileGraph(graph, callTarget);
        } catch (Exception e) {
            // swallow exception
        }
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testNestedSwitches() {
        PELangRootNode rootNode = PELangSample.nestedSwitches();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        assertGraphEquals("constant10", graph);
    }

    @Test
    public void testBranchWithGlobalReadWrite() {
        PELangRootNode rootNode = PELangSample.branchWithGlobalReadWrite();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        // TODO: add partial evaluation asserts
    }

    @Test
    public void testLoopWithGlobalReadWrite() {
        PELangRootNode rootNode = PELangSample.loopWithGlobalReadWrite();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);
        warmupCallTarget(callTarget);

        try {
            // do a first run and swallow code install exceptions
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
        PELangRootNode rootNode = PELangSample.nestedLoopsWithMultipleBackEdges();
        OptimizedCallTarget callTarget = createCallTarget(rootNode);
        assertCallResultEquals(10L, callTarget);

        warmupCallTarget(callTarget);
        StructuredGraph graph = partiallyEvaluate(callTarget);
        compileGraph(graph, callTarget);
        // TODO: add partial evaluation asserts
    }

    @Test
    public void testInvokeObjectFunctionProperty() {
        PELangRootNode rootNode = PELangSample.invokeObjectFunctionProperty();
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
