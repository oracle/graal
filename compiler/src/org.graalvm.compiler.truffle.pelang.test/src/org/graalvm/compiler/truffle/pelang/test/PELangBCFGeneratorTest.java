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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertThat;

import org.graalvm.compiler.truffle.pelang.PELangBCFGenerator;
import org.graalvm.compiler.truffle.pelang.PELangRootNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangBasicBlockDispatchNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangBasicBlockNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangDoubleSuccessorNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangSingleSuccessorNode;
import org.junit.Test;

public class PELangBCFGeneratorTest {

    @Test
    public void testSimpleAdd() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleAdd());

        assertThat(rootNode.getBodyNode(), instanceOf(PELangBasicBlockDispatchNode.class));

        PELangBasicBlockDispatchNode dispatchNode = (PELangBasicBlockDispatchNode) rootNode.getBodyNode();
        PELangBasicBlockNode[] basicBlocks = dispatchNode.getBlockNodes();

        assertThat(basicBlocks.length, equalTo(1));
        assertThat(basicBlocks[0], instanceOf(PELangSingleSuccessorNode.class));

        PELangSingleSuccessorNode b0 = (PELangSingleSuccessorNode) basicBlocks[0];
        assertThat(b0.getSuccessor(), equalTo(PELangBasicBlockNode.NO_SUCCESSOR));
    }

    @Test
    public void testSimpleBlock() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleBlock());

        assertThat(rootNode.getBodyNode(), instanceOf(PELangBasicBlockDispatchNode.class));

        PELangBasicBlockDispatchNode dispatchNode = (PELangBasicBlockDispatchNode) rootNode.getBodyNode();
        PELangBasicBlockNode[] basicBlocks = dispatchNode.getBlockNodes();

        assertThat(basicBlocks.length, equalTo(1));
        assertThat(basicBlocks[0], instanceOf(PELangSingleSuccessorNode.class));

        PELangSingleSuccessorNode b0 = (PELangSingleSuccessorNode) basicBlocks[0];
        assertThat(b0.getSuccessor(), equalTo(PELangBasicBlockNode.NO_SUCCESSOR));
    }

    @Test
    public void testSimpleLocalReadWrite() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleLocalReadWrite());

        assertThat(rootNode.getBodyNode(), instanceOf(PELangBasicBlockDispatchNode.class));

        PELangBasicBlockDispatchNode dispatchNode = (PELangBasicBlockDispatchNode) rootNode.getBodyNode();
        PELangBasicBlockNode[] basicBlocks = dispatchNode.getBlockNodes();

        assertThat(basicBlocks.length, equalTo(1));
        assertThat(basicBlocks[0], instanceOf(PELangSingleSuccessorNode.class));

        PELangSingleSuccessorNode b0 = (PELangSingleSuccessorNode) basicBlocks[0];
        assertThat(b0.getSuccessor(), equalTo(PELangBasicBlockNode.NO_SUCCESSOR));
    }

    @Test
    public void testSimpleGlobalReadWrite() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleGlobalReadWrite());

        assertThat(rootNode.getBodyNode(), instanceOf(PELangBasicBlockDispatchNode.class));

        PELangBasicBlockDispatchNode dispatchNode = (PELangBasicBlockDispatchNode) rootNode.getBodyNode();
        PELangBasicBlockNode[] basicBlocks = dispatchNode.getBlockNodes();

        assertThat(basicBlocks.length, equalTo(1));
        assertThat(basicBlocks[0], instanceOf(PELangSingleSuccessorNode.class));

        PELangSingleSuccessorNode b0 = (PELangSingleSuccessorNode) basicBlocks[0];
        assertThat(b0.getSuccessor(), equalTo(PELangBasicBlockNode.NO_SUCCESSOR));
    }

    @Test
    public void testSimpleBranch() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleBranch());

        assertThat(rootNode.getBodyNode(), instanceOf(PELangBasicBlockDispatchNode.class));

        PELangBasicBlockDispatchNode dispatchNode = (PELangBasicBlockDispatchNode) rootNode.getBodyNode();
        PELangBasicBlockNode[] basicBlocks = dispatchNode.getBlockNodes();

        assertThat(basicBlocks.length, equalTo(5));
        assertThat(basicBlocks[0], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[1], instanceOf(PELangDoubleSuccessorNode.class));
        assertThat(basicBlocks[2], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[3], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[4], instanceOf(PELangSingleSuccessorNode.class));

        PELangSingleSuccessorNode b0 = (PELangSingleSuccessorNode) basicBlocks[0];
        PELangDoubleSuccessorNode b1 = (PELangDoubleSuccessorNode) basicBlocks[1];
        PELangSingleSuccessorNode b2 = (PELangSingleSuccessorNode) basicBlocks[2];
        PELangSingleSuccessorNode b3 = (PELangSingleSuccessorNode) basicBlocks[3];
        PELangSingleSuccessorNode b4 = (PELangSingleSuccessorNode) basicBlocks[4];

        assertThat(b0.getSuccessor(), equalTo(1));
        assertThat(b1.getTrueSuccessor(), equalTo(2));
        assertThat(b1.getFalseSuccessor(), equalTo(3));
        assertThat(b2.getSuccessor(), equalTo(4));
        assertThat(b3.getSuccessor(), equalTo(4));
        assertThat(b4.getSuccessor(), equalTo(PELangBasicBlockNode.NO_SUCCESSOR));
    }

    @Test
    public void testSimpleLoop() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.simpleLoop());

        assertThat(rootNode.getBodyNode(), instanceOf(PELangBasicBlockDispatchNode.class));

        PELangBasicBlockDispatchNode dispatchNode = (PELangBasicBlockDispatchNode) rootNode.getBodyNode();
        PELangBasicBlockNode[] basicBlocks = dispatchNode.getBlockNodes();

        assertThat(basicBlocks.length, equalTo(4));
        assertThat(basicBlocks[0], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[1], instanceOf(PELangDoubleSuccessorNode.class));
        assertThat(basicBlocks[2], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[3], instanceOf(PELangSingleSuccessorNode.class));

        PELangSingleSuccessorNode b0 = (PELangSingleSuccessorNode) basicBlocks[0];
        PELangDoubleSuccessorNode b1 = (PELangDoubleSuccessorNode) basicBlocks[1];
        PELangSingleSuccessorNode b2 = (PELangSingleSuccessorNode) basicBlocks[2];
        PELangSingleSuccessorNode b3 = (PELangSingleSuccessorNode) basicBlocks[3];

        assertThat(b0.getSuccessor(), equalTo(1));
        assertThat(b1.getTrueSuccessor(), equalTo(2));
        assertThat(b1.getFalseSuccessor(), equalTo(3));
        assertThat(b2.getSuccessor(), equalTo(1));
        assertThat(b3.getSuccessor(), equalTo(PELangBasicBlockNode.NO_SUCCESSOR));
    }

    @Test
    public void testNestedAdd() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedAdds());

        assertThat(rootNode.getBodyNode(), instanceOf(PELangBasicBlockDispatchNode.class));

        PELangBasicBlockDispatchNode dispatchNode = (PELangBasicBlockDispatchNode) rootNode.getBodyNode();
        PELangBasicBlockNode[] basicBlocks = dispatchNode.getBlockNodes();

        assertThat(basicBlocks.length, equalTo(1));
        assertThat(basicBlocks[0], instanceOf(PELangSingleSuccessorNode.class));

        PELangSingleSuccessorNode b0 = (PELangSingleSuccessorNode) basicBlocks[0];
        assertThat(b0.getSuccessor(), equalTo(PELangBasicBlockNode.NO_SUCCESSOR));
    }

    @Test
    public void testNestedBlocks() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedBlocks());

        assertThat(rootNode.getBodyNode(), instanceOf(PELangBasicBlockDispatchNode.class));

        PELangBasicBlockDispatchNode dispatchNode = (PELangBasicBlockDispatchNode) rootNode.getBodyNode();
        PELangBasicBlockNode[] basicBlocks = dispatchNode.getBlockNodes();

        assertThat(basicBlocks.length, equalTo(1));
        assertThat(basicBlocks[0], instanceOf(PELangSingleSuccessorNode.class));

        PELangSingleSuccessorNode b0 = (PELangSingleSuccessorNode) basicBlocks[0];
        assertThat(b0.getSuccessor(), equalTo(PELangBasicBlockNode.NO_SUCCESSOR));
    }

    @Test
    public void testNestedLocalReadWrites() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedLocalReadWrites());

        assertThat(rootNode.getBodyNode(), instanceOf(PELangBasicBlockDispatchNode.class));

        PELangBasicBlockDispatchNode dispatchNode = (PELangBasicBlockDispatchNode) rootNode.getBodyNode();
        PELangBasicBlockNode[] basicBlocks = dispatchNode.getBlockNodes();

        assertThat(basicBlocks.length, equalTo(1));
        assertThat(basicBlocks[0], instanceOf(PELangSingleSuccessorNode.class));

        PELangSingleSuccessorNode b0 = (PELangSingleSuccessorNode) basicBlocks[0];
        assertThat(b0.getSuccessor(), equalTo(PELangBasicBlockNode.NO_SUCCESSOR));
    }

    @Test
    public void testNestedLoops() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedLoops());

        assertThat(rootNode.getBodyNode(), instanceOf(PELangBasicBlockDispatchNode.class));

        PELangBasicBlockDispatchNode dispatchNode = (PELangBasicBlockDispatchNode) rootNode.getBodyNode();
        PELangBasicBlockNode[] basicBlocks = dispatchNode.getBlockNodes();

        assertThat(basicBlocks.length, equalTo(6));
        assertThat(basicBlocks[0], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[1], instanceOf(PELangDoubleSuccessorNode.class));
        assertThat(basicBlocks[2], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[3], instanceOf(PELangDoubleSuccessorNode.class));
        assertThat(basicBlocks[4], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[5], instanceOf(PELangSingleSuccessorNode.class));

        PELangSingleSuccessorNode b0 = (PELangSingleSuccessorNode) basicBlocks[0];
        PELangDoubleSuccessorNode b1 = (PELangDoubleSuccessorNode) basicBlocks[1];
        PELangSingleSuccessorNode b2 = (PELangSingleSuccessorNode) basicBlocks[2];
        PELangDoubleSuccessorNode b3 = (PELangDoubleSuccessorNode) basicBlocks[3];
        PELangSingleSuccessorNode b4 = (PELangSingleSuccessorNode) basicBlocks[4];
        PELangSingleSuccessorNode b5 = (PELangSingleSuccessorNode) basicBlocks[5];

        assertThat(b0.getSuccessor(), equalTo(1));
        assertThat(b1.getTrueSuccessor(), equalTo(2));
        assertThat(b1.getFalseSuccessor(), equalTo(5));
        assertThat(b2.getSuccessor(), equalTo(3));
        assertThat(b3.getTrueSuccessor(), equalTo(4));
        assertThat(b3.getFalseSuccessor(), equalTo(1));
        assertThat(b4.getSuccessor(), equalTo(3));
        assertThat(b5.getSuccessor(), equalTo(PELangBasicBlockNode.NO_SUCCESSOR));
    }

    @Test
    public void testNestedBranches() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.nestedBranches());

        assertThat(rootNode.getBodyNode(), instanceOf(PELangBasicBlockDispatchNode.class));

        PELangBasicBlockDispatchNode dispatchNode = (PELangBasicBlockDispatchNode) rootNode.getBodyNode();
        PELangBasicBlockNode[] basicBlocks = dispatchNode.getBlockNodes();

        assertThat(basicBlocks.length, equalTo(8));
        assertThat(basicBlocks[0], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[1], instanceOf(PELangDoubleSuccessorNode.class));
        assertThat(basicBlocks[2], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[3], instanceOf(PELangDoubleSuccessorNode.class));
        assertThat(basicBlocks[4], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[5], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[6], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[7], instanceOf(PELangSingleSuccessorNode.class));

        PELangSingleSuccessorNode b0 = (PELangSingleSuccessorNode) basicBlocks[0];
        PELangDoubleSuccessorNode b1 = (PELangDoubleSuccessorNode) basicBlocks[1];
        PELangSingleSuccessorNode b2 = (PELangSingleSuccessorNode) basicBlocks[2];
        PELangDoubleSuccessorNode b3 = (PELangDoubleSuccessorNode) basicBlocks[3];
        PELangSingleSuccessorNode b4 = (PELangSingleSuccessorNode) basicBlocks[4];
        PELangSingleSuccessorNode b5 = (PELangSingleSuccessorNode) basicBlocks[5];
        PELangSingleSuccessorNode b6 = (PELangSingleSuccessorNode) basicBlocks[6];
        PELangSingleSuccessorNode b7 = (PELangSingleSuccessorNode) basicBlocks[7];

        assertThat(b0.getSuccessor(), equalTo(1));
        assertThat(b1.getTrueSuccessor(), equalTo(2));
        assertThat(b1.getFalseSuccessor(), equalTo(6));
        assertThat(b2.getSuccessor(), equalTo(3));
        assertThat(b3.getTrueSuccessor(), equalTo(4));
        assertThat(b3.getFalseSuccessor(), equalTo(5));
        assertThat(b4.getSuccessor(), equalTo(7));
        assertThat(b5.getSuccessor(), equalTo(7));
        assertThat(b6.getSuccessor(), equalTo(7));
        assertThat(b7.getSuccessor(), equalTo(PELangBasicBlockNode.NO_SUCCESSOR));
    }

    @Test
    public void testBranchWithGlobalReadWrite() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.branchWithGlobalReadWrite());

        assertThat(rootNode.getBodyNode(), instanceOf(PELangBasicBlockDispatchNode.class));

        PELangBasicBlockDispatchNode dispatchNode = (PELangBasicBlockDispatchNode) rootNode.getBodyNode();
        PELangBasicBlockNode[] basicBlocks = dispatchNode.getBlockNodes();

        assertThat(basicBlocks.length, equalTo(5));
        assertThat(basicBlocks[0], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[1], instanceOf(PELangDoubleSuccessorNode.class));
        assertThat(basicBlocks[2], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[3], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[4], instanceOf(PELangSingleSuccessorNode.class));

        PELangSingleSuccessorNode b0 = (PELangSingleSuccessorNode) basicBlocks[0];
        PELangDoubleSuccessorNode b1 = (PELangDoubleSuccessorNode) basicBlocks[1];
        PELangSingleSuccessorNode b2 = (PELangSingleSuccessorNode) basicBlocks[2];
        PELangSingleSuccessorNode b3 = (PELangSingleSuccessorNode) basicBlocks[3];
        PELangSingleSuccessorNode b4 = (PELangSingleSuccessorNode) basicBlocks[4];

        assertThat(b0.getSuccessor(), equalTo(1));
        assertThat(b1.getTrueSuccessor(), equalTo(2));
        assertThat(b1.getFalseSuccessor(), equalTo(3));
        assertThat(b2.getSuccessor(), equalTo(4));
        assertThat(b3.getSuccessor(), equalTo(4));
        assertThat(b4.getSuccessor(), equalTo(PELangBasicBlockNode.NO_SUCCESSOR));
    }

    @Test
    public void testLoopWithGlobalReadWrite() {
        PELangBCFGenerator g = new PELangBCFGenerator();
        PELangRootNode rootNode = g.generate(PELangSample.loopWithGlobalReadWrite());

        assertThat(rootNode.getBodyNode(), instanceOf(PELangBasicBlockDispatchNode.class));

        PELangBasicBlockDispatchNode dispatchNode = (PELangBasicBlockDispatchNode) rootNode.getBodyNode();
        PELangBasicBlockNode[] basicBlocks = dispatchNode.getBlockNodes();

        assertThat(basicBlocks.length, equalTo(4));
        assertThat(basicBlocks[0], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[1], instanceOf(PELangDoubleSuccessorNode.class));
        assertThat(basicBlocks[2], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[3], instanceOf(PELangSingleSuccessorNode.class));

        PELangSingleSuccessorNode b0 = (PELangSingleSuccessorNode) basicBlocks[0];
        PELangDoubleSuccessorNode b1 = (PELangDoubleSuccessorNode) basicBlocks[1];
        PELangSingleSuccessorNode b2 = (PELangSingleSuccessorNode) basicBlocks[2];
        PELangSingleSuccessorNode b3 = (PELangSingleSuccessorNode) basicBlocks[3];

        assertThat(b0.getSuccessor(), equalTo(1));
        assertThat(b1.getTrueSuccessor(), equalTo(2));
        assertThat(b1.getFalseSuccessor(), equalTo(3));
        assertThat(b2.getSuccessor(), equalTo(1));
        assertThat(b3.getSuccessor(), equalTo(PELangBasicBlockNode.NO_SUCCESSOR));
    }

}
