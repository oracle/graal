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

import org.graalvm.compiler.truffle.pelang.PELangException;
import org.graalvm.compiler.truffle.pelang.PELangRootNode;
import org.graalvm.compiler.truffle.pelang.util.PELangBCFGenerator;
import org.graalvm.compiler.truffle.pelang.util.PELangSample;
import org.junit.Test;

public class PELangBCFTest extends PELangTest {

    @Test
    public void testSimpleAdd() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleAdd());
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleBlock() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleBlock());
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleLocalReadWrite() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleLocalReadWrite());
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleGlobalReadWrite() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleGlobalReadWrite());
        assertCallResultEquals(10L, rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test
    public void testSimpleBranch() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleBranch());
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleLoop() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleLoop());
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleSwitch() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleSwitch());
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleInvoke() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleInvoke());
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleObject() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleObject());
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleArrayRead() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleArrayRead());
        assertCallResultEquals(10L, rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test
    public void testSimpleMultiArrayRead() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleMultiArrayRead());
        assertCallResultEquals(10L, rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test
    public void testSimpleArrayWrite() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleArrayWrite());
        assertCallResultEquals(10L, rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test
    public void testSimpleMultiArrayWrite() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleMultiArrayWrite());
        assertCallResultEquals(10L, rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test
    public void testComplexStringArray() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.complexStringArray());
        assertCallResultEquals("Foo", rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test(expected = PELangException.class)
    public void testInvalidBranch() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.invalidBranch());

        // compilation should work, but execution should throw an exception
        compileHelper(rootNode.getName(), rootNode);
        assertCallResultEquals("anything", rootNode);
    }

    @Test(expected = PELangException.class)
    public void testInvalidLoop() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.invalidLoop());

        // compilation should work, but execution should throw an exception
        compileHelper(rootNode.getName(), rootNode);
        assertCallResultEquals("anything", rootNode);
    }

    @Test
    public void testNestedAdds() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedAdds());
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedBlocks() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedBlocks());
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedLocalReadWrites() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedLocalReadWrites());
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedBranches() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedBranches());
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedLoops() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedLoops());
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedSwitches() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedSwitches());
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedObjectProperties() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedObjectProperties());
        assertCallResultEquals(10L, rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test
    public void testBranchWithGlobalReadWrite() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.branchWithGlobalReadWrite());
        assertCallResultEquals(10L, rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test
    public void testLoopWithGlobalReadWrite() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.loopWithGlobalReadWrite());
        assertCallResultEquals(10L, rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test
    public void testNestedLoopsWithMultipleBackEdges() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedLoopsWithMultipleBackEdges());
        assertCallResultEquals(10L, rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test
    public void testInvokeObjectFunctionProperty() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.invokeObjectFunctionProperty());
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testIrreducibleLoop() {
        // no need for a generator as sample is directly built with basic blocks
        PELangRootNode rootNode = PELangSample.irreducibleLoop();
        assertCallResultEquals(10L, rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test
    public void testBinaryTrees() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.binaryTrees());
        compileHelper(rootNode.getName(), rootNode, new Object[]{10L});
    }

    @Test
    public void testArraySum() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.arraySum());
        assertCallResultEquals(10L, rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test
    public void testArrayCompare() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.arrayCompare());
        assertCallResultEquals(1L, rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test
    public void testPow() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.pow());
        assertCallResultEquals(9L, rootNode, new Object[]{3L, 2L});
        compileHelper(rootNode.getName(), rootNode, new Object[]{3L, 2L});
    }

    protected Object constant10() {
        return 10L;
    }

}
