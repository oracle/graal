/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.bytecode.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.Instruction.Argument.Kind;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;

public class NodeOptimizationTest extends AbstractInstructionTest {

    protected static final BytecodeDSLTestLanguage LANGUAGE = null;

    @Test
    public void testSingleSpecialization() {
        // return - (arg0)
        NodeOptimizationTestNode node = (NodeOptimizationTestNode) parse(b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginSingleSpecialization();
            b.emitLoadArgument(0);
            b.endSingleSpecialization();
            b.endReturn();
            b.endRoot();
        }).getRootNode();

        Instruction singleSpecializationNode = node.getBytecodeNode().getInstructionsAsList().get(1);
        assertEquals("c.SingleSpecialization", singleSpecializationNode.getName());
        assertNoNode(singleSpecializationNode);

        assertEquals(42, node.getCallTarget().call(42));
    }

    @Test
    public void testBoundNode() {
        // return - (arg0)
        NodeOptimizationTestNode node = (NodeOptimizationTestNode) parse(b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginBound();
            b.emitLoadArgument(0);
            b.endBound();
            b.endReturn();
            b.endRoot();
        }).getRootNode();

        Instruction singleSpecializationNode = node.getBytecodeNode().getInstructionsAsList().get(1);
        assertEquals("c.Bound", singleSpecializationNode.getName());
        assertNode(singleSpecializationNode);

        assertEquals(42, node.getCallTarget().call(42));
    }

    private static void assertNoNode(Instruction i) {
        for (Instruction.Argument arg : i.getArguments()) {
            if (arg.getKind() == Kind.NODE_PROFILE) {
                fail("No node profile expected but found in " + i);
            }
        }
    }

    private static void assertNode(Instruction i) {
        for (Instruction.Argument arg : i.getArguments()) {
            if (arg.getKind() == Kind.NODE_PROFILE) {
                return;
            }
        }
        fail("No node profile found but expected in " + i);
    }

    private static NodeOptimizationTestNode parse(BytecodeParser<NodeOptimizationTestNodeGen.Builder> builder) {
        BytecodeRootNodes<NodeOptimizationTestNode> nodes = NodeOptimizationTestNodeGen.create(LANGUAGE, BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(nodes.count() - 1);
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                    enableYield = true, enableSerialization = true, //
                    enableQuickening = true, //
                    boxingEliminationTypes = {long.class, int.class, boolean.class})
    public abstract static class NodeOptimizationTestNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected NodeOptimizationTestNode(BytecodeDSLTestLanguage language,
                        FrameDescriptor.Builder frameDescriptor) {
            super(language, frameDescriptor.build());
        }

        @Operation
        static final class SingleSpecialization {
            @Specialization
            public static Object doDefault(Object v) {
                return v;
            }
        }

        @Operation
        static final class Bound {
            @Specialization
            public static Object doDefault(Object v, @SuppressWarnings("unused") @Bind Node node) {
                return v;
            }
        }

    }

}
