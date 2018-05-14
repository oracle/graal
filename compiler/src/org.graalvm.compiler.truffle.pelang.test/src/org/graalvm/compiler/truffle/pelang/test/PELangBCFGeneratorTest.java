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
import org.graalvm.compiler.truffle.pelang.PELangBuilder;
import org.graalvm.compiler.truffle.pelang.PELangRootNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangBasicBlockDispatchNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangBasicBlockNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangDoubleSuccessorNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangSingleSuccessorNode;
import org.junit.Test;

public class PELangBCFGeneratorTest extends PELangTest {

    protected Object constant10() {
        return 10;
    }

    protected Object constant15() {
        return 15;
    }

    @Test
    public void testNestedBlocks() {
        PELangBuilder b = new PELangBuilder();
        PELangBCFGenerator g = new PELangBCFGenerator();

        // @formatter:off
        PELangRootNode rootNode = g.generate(
            b.root(
                b.block(
                    b.write(0, "counter"),
                    b.write(1, "counter"),
                    b.write(2, "counter"),
                    b.block(
                        b.write(3, "counter"),
                        b.write(4, "counter"),
                        b.write(10, "counter"),
                        b.block(
                            b.ret(b.read("counter"))
                        )
                    )
                )
            )
        );
        // @formatter:on

        assertCallResult(10L, rootNode);
        assertThat(rootNode.getBodyNode(), instanceOf(PELangBasicBlockDispatchNode.class));

        PELangBasicBlockDispatchNode dispatchNode = (PELangBasicBlockDispatchNode) rootNode.getBodyNode();
        PELangBasicBlockNode[] basicBlocks = dispatchNode.getBlockNodes();

        assertThat(basicBlocks.length, equalTo(1));
        assertThat(basicBlocks[0], instanceOf(PELangSingleSuccessorNode.class));

        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testLoop() {
        PELangBuilder b = new PELangBuilder();
        PELangBCFGenerator g = new PELangBCFGenerator();

        // @formatter:off
        PELangRootNode rootNode = g.generate(
            b.root(
                b.block(
                    b.write(0, "counter"),
                    b.loop(
                        b.lessThan(b.read("counter"), b.literal(10L)),
                        b.increment(1, "counter")),
                    b.ret(b.read("counter"))
                )
            )
        );
        // @formatter:on

        assertCallResult(10L, rootNode);
        assertThat(rootNode.getBodyNode(), instanceOf(PELangBasicBlockDispatchNode.class));

        PELangBasicBlockDispatchNode dispatchNode = (PELangBasicBlockDispatchNode) rootNode.getBodyNode();
        PELangBasicBlockNode[] basicBlocks = dispatchNode.getBlockNodes();

        assertThat(basicBlocks.length, equalTo(4));
        assertThat(basicBlocks[0], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[1], instanceOf(PELangDoubleSuccessorNode.class));
        assertThat(basicBlocks[2], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[3], instanceOf(PELangSingleSuccessorNode.class));

        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedLoop() {
        PELangBuilder b = new PELangBuilder();
        PELangBCFGenerator g = new PELangBCFGenerator();

        // @formatter:off
        PELangRootNode rootNode = g.generate(
            b.root(
                b.block(
                    b.write(0, "i"),
                    b.write(0, "j"),
                    b.loop(
                        b.lessThan(b.read("i"), b.literal(5L)),
                        b.block(
                            b.increment(1, "i"),
                            b.loop(
                                b.lessThan(b.read("j"), b.literal(10L)),
                                b.block(
                                    b.increment(1, "j")
                                )
                            )
                        )
                    ),
                    b.ret(
                        b.add(
                            b.read("i"),
                            b.read("j")
                        )
                    )
                )
            )
        );
        // @formatter:on

        assertCallResult(15L, rootNode);
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

        assertPartialEvalEquals("constant15", rootNode);
    }

    @Test
    public void testBranch() {
        PELangBuilder b = new PELangBuilder();
        PELangBCFGenerator g = new PELangBCFGenerator();

        // @formatter:off
        PELangRootNode rootNode = g.generate(
            b.root(
                b.block(
                    b.write(0, "i"),
                    b.branch(
                        b.lessThan(b.read("i"), b.literal(10L)),
                        b.write(10, "i"),
                        b.write(5L, "i")),
                    b.ret(b.read("i"))
                )
            )
        );
        // @formatter:on

        assertCallResult(10L, rootNode);
        assertThat(rootNode.getBodyNode(), instanceOf(PELangBasicBlockDispatchNode.class));

        PELangBasicBlockDispatchNode dispatchNode = (PELangBasicBlockDispatchNode) rootNode.getBodyNode();
        PELangBasicBlockNode[] basicBlocks = dispatchNode.getBlockNodes();

        assertThat(basicBlocks.length, equalTo(5));
        assertThat(basicBlocks[0], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[1], instanceOf(PELangDoubleSuccessorNode.class));
        assertThat(basicBlocks[2], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[3], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[4], instanceOf(PELangSingleSuccessorNode.class));

        assertPartialEvalEquals("constant10", rootNode);
    }

    @Test
    public void testNestedBranch() {
        PELangBuilder b = new PELangBuilder();
        PELangBCFGenerator g = new PELangBCFGenerator();

        // @formatter:off
        PELangRootNode rootNode = g.generate(
            b.root(
                b.block(
                    b.write(0L, "i"),
                    b.branch(
                        b.lessThan(b.read("i"), b.literal(10L)),
                        b.branch(
                            b.equals(b.read("i"), b.literal(0L)),
                            b.write(10L, "i"),
                            b.write(5L, "i")
                        ),
                        b.write(5L, "i")),
                    b.ret(b.read("i"))
                )
            )
        );
        // @formatter:on

        assertCallResult(10L, rootNode);
        assertThat(rootNode.getBodyNode(), instanceOf(PELangBasicBlockDispatchNode.class));

        PELangBasicBlockDispatchNode dispatchNode = (PELangBasicBlockDispatchNode) rootNode.getBodyNode();
        PELangBasicBlockNode[] basicBlocks = dispatchNode.getBlockNodes();

        assertThat(basicBlocks.length, equalTo(7));
        assertThat(basicBlocks[0], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[1], instanceOf(PELangDoubleSuccessorNode.class));
        assertThat(basicBlocks[2], instanceOf(PELangDoubleSuccessorNode.class));
        assertThat(basicBlocks[3], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[4], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[5], instanceOf(PELangSingleSuccessorNode.class));
        assertThat(basicBlocks[6], instanceOf(PELangSingleSuccessorNode.class));

        assertPartialEvalEquals("constant10", rootNode);
    }

}
