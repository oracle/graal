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
import org.graalvm.compiler.truffle.pelang.util.PELangSample;
import org.junit.Test;

public class PELangNCFTest extends PELangTest {

    @Test
    public void testSimpleAdd() {
        PELangRootNode rootNode = PELangSample.simpleAdd();
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleBlock() {
        PELangRootNode rootNode = PELangSample.simpleBlock();
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleLocalReadWrite() {
        PELangRootNode rootNode = PELangSample.simpleLocalReadWrite();
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleGlobalReadWrite() {
        PELangRootNode rootNode = PELangSample.simpleGlobalReadWrite();
        assertCallResultEquals(10L, rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test
    public void testSimpleBranch() {
        PELangRootNode rootNode = PELangSample.simpleBranch();
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleLoop() {
        PELangRootNode rootNode = PELangSample.simpleLoop();
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleSwitch() {
        PELangRootNode rootNode = PELangSample.simpleSwitch();
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleInvoke() {
        PELangRootNode rootNode = PELangSample.simpleInvoke();
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleObject() {
        PELangRootNode rootNode = PELangSample.simpleObject();
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testSimpleArrayRead() {
        PELangRootNode rootNode = PELangSample.simpleArrayRead();
        assertCallResultEquals(10L, rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test
    public void testSimpleMultiArrayRead() {
        PELangRootNode rootNode = PELangSample.simpleMultiArrayRead();
        assertCallResultEquals(10L, rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test
    public void testSimpleArrayWrite() {
        PELangRootNode rootNode = PELangSample.simpleArrayWrite();
        assertCallResultEquals(10L, rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test
    public void testSimpleMultiArrayWrite() {
        PELangRootNode rootNode = PELangSample.simpleMultiArrayWrite();
        assertCallResultEquals(10L, rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test
    public void testComplexStringArray() {
        PELangRootNode rootNode = PELangSample.complexStringArray();
        assertCallResultEquals("Foo", rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test(expected = PELangException.class)
    public void testInvalidBranch() {
        PELangRootNode rootNode = PELangSample.invalidBranch();

        // compilation should work, but execution should throw an exception
        compileHelper(rootNode.getName(), rootNode);
        assertCallResultEquals("anything", rootNode);
    }

    @Test(expected = PELangException.class)
    public void testInvalidLoop() {
        PELangRootNode rootNode = PELangSample.invalidLoop();

        // compilation should work, but execution should throw an exception
        compileHelper(rootNode.getName(), rootNode);
        assertCallResultEquals("anything", rootNode);
    }

    @Test
    public void testNestedAdds() {
        PELangRootNode rootNode = PELangSample.nestedAdds();
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedBlocks() {
        PELangRootNode rootNode = PELangSample.nestedBlocks();
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedLocalReadWrites() {
        PELangRootNode rootNode = PELangSample.nestedLocalReadWrites();
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedBranches() {
        PELangRootNode rootNode = PELangSample.nestedBranches();
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedLoops() {
        PELangRootNode rootNode = PELangSample.nestedLoops();
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedSwitches() {
        PELangRootNode rootNode = PELangSample.nestedSwitches();
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedObjectProperties() {
        PELangRootNode rootNode = PELangSample.nestedObjectProperties();
        assertCallResultEquals(10L, rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test
    public void testBranchWithGlobalReadWrite() {
        PELangRootNode rootNode = PELangSample.branchWithGlobalReadWrite();
        assertCallResultEquals(10L, rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test
    public void testLoopWithGlobalReadWrite() {
        PELangRootNode rootNode = PELangSample.loopWithGlobalReadWrite();
        assertCallResultEquals(10L, rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test
    public void testNestedLoopsWithMultipleBackEdges() {
        PELangRootNode rootNode = PELangSample.nestedLoopsWithMultipleBackEdges();
        assertCallResultEquals(10L, rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test
    public void testInvokeObjectFunctionProperty() {
        PELangRootNode rootNode = PELangSample.invokeObjectFunctionProperty();
        assertCallResultEquals(10L, rootNode);
        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testBinaryTrees() {
        PELangRootNode rootNode = PELangSample.binaryTrees();
        compileHelper(rootNode.getName(), rootNode, new Object[]{10L});
    }

    @Test
    public void testArraySum() {
        PELangRootNode rootNode = PELangSample.arraySum();
        assertCallResultEquals(10L, rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test
    public void testArrayCompare() {
        PELangRootNode rootNode = PELangSample.arrayCompare();
        assertCallResultEquals(1L, rootNode);
        compileHelper(rootNode.getName(), rootNode);
    }

    @Test
    public void testPow() {
        PELangRootNode rootNode = PELangSample.pow();
        assertCallResultEquals(9L, rootNode, new Object[]{3L, 2L});
        compileHelper(rootNode.getName(), rootNode, new Object[]{3L, 2L});
    }

    protected Object constant10() {
        return 10L;
    }

}
