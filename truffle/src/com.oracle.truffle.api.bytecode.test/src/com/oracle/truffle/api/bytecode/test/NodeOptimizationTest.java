/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeIndexMode;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants.Variant;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

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

        assertEquals(42, node.getCallTarget().call(42));

        Instruction instruction = node.getBytecodeNode().getInstructionsAsList().get(1);
        assertEquals("c.SingleSpecialization", instruction.getName());
        Instruction.Argument nodeArgument = findNodeArgument(instruction);
        assertNotNull(nodeArgument);
        assertSingletonNode(nodeArgument);
    }

    @Test
    public void testBoundNode() {
        // return - (arg0)
        NodeOptimizationTestNode node = (NodeOptimizationTestNode) parse(b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginBoundNode();
            b.emitLoadArgument(0);
            b.endBoundNode();
            b.endReturn();
            b.endRoot();
        }).getRootNode();

        assertEquals(42, node.getCallTarget().call(42));

        Instruction instruction = node.getBytecodeNode().getInstructionsAsList().get(1);
        Instruction.Argument nodeArgument = findNodeArgument(instruction);
        assertEquals("c.BoundNode", instruction.getName());
        assertNotNull(nodeArgument);

        assertInstanceNode(nodeArgument);
        assertTrue(nodeArgument.getSpecializationInfo().get(0).isActive());
    }

    @Test
    public void testBoundBytecodeNode() {
        // return - (arg0)
        NodeOptimizationTestNode node = (NodeOptimizationTestNode) parse(b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginBoundBytecodeNode();
            b.emitLoadArgument(0);
            b.endBoundBytecodeNode();
            b.endReturn();
            b.endRoot();
        }).getRootNode();

        assertEquals(42, node.getCallTarget().call(42));

        Instruction instruction = node.getBytecodeNode().getInstructionsAsList().get(1);
        Instruction.Argument nodeArgument = findNodeArgument(instruction);
        assertEquals("c.BoundBytecodeNode", instruction.getName());
        assertNotNull(nodeArgument);

        assertSingletonNode(nodeArgument);
        assertTrue(nodeArgument.getSpecializationInfo().get(0).isActive());
    }

    @Test
    public void testSingletonForSpecialization() {
        // return - (arg0)
        NodeOptimizationTestNode node = (NodeOptimizationTestNode) parse(b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginSpecializationTest();
            b.emitLoadArgument(0);
            b.endSpecializationTest();
            b.endReturn();
            b.endRoot();
        }).getRootNode();

        assertEquals(42, node.getCallTarget().call(42));

        Instruction instruction = node.getBytecodeNode().getInstructionsAsList().get(1);
        assertEquals("c.SpecializationTest$Int", instruction.getName());
        Instruction.Argument nodeArgument = findNodeArgument(instruction);
        assertNotNull(nodeArgument);

        assertSingletonNode(nodeArgument);
        assertTrue(nodeArgument.getSpecializationInfo().get(0).isActive());
    }

    @Test
    public void testBoundNodeBytecodeIndexModes() {
        testBoundNodeBytecodeIndexModes(false);
    }

    @Test
    public void testBoundNodeWithCacheBytecodeIndexModes() {
        testBoundNodeBytecodeIndexModes(true);
    }

    private static void testBoundNodeBytecodeIndexModes(boolean withCache) {
        for (var variant : BoundNodeRootNodeBuilder.variants()) {
            boolean cachedNodeMode = variant == BoundNodeRootNodeCachedNode.BYTECODE;
            BoundNodeRootNode root = variant.create(null, BytecodeConfig.DEFAULT, b -> {
                b.beginRoot();
                b.beginReturn();
                if (withCache) {
                    b.emitBoundNodeWithCache();
                } else {
                    b.emitBoundNode();
                }
                b.endReturn();
                b.endRoot();
            }).getNode(0);

            assertSame(root.getBytecodeNode(), root.getCallTarget().call());
            root.getBytecodeNode().setUncachedThreshold(0);
            Object boundNode = root.getCallTarget().call();
            Instruction instruction = root.getBytecodeNode().getInstructionsAsList().get(0);
            Instruction.Argument nodeArgument = findNodeArgument(instruction);
            assertNotNull(nodeArgument);
            assertEquals(withCache || cachedNodeMode ? 4 : 0, nodeArgument.getDescriptor().getLength());
            if (withCache || cachedNodeMode) {
                assertInstanceNode(nodeArgument);
            } else {
                assertSingletonNode(nodeArgument);
            }
            assertSame(withCache || cachedNodeMode ? nodeArgument.asCachedNode() : root.getBytecodeNode(), boundNode);
            assertSame(boundNode, root.getCallTarget().call());
        }
    }

    @GenerateBytecodeTestVariants({
                    @Variant(suffix = "CachedNode", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true, bytecodeIndexMode = BytecodeIndexMode.CACHED_NODE)),
                    @Variant(suffix = "Frame", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true, bytecodeIndexMode = BytecodeIndexMode.FRAME)),
                    @Variant(suffix = "Manual", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true, bytecodeIndexMode = BytecodeIndexMode.MANUAL)),
                    @Variant(suffix = "None", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true, bytecodeIndexMode = BytecodeIndexMode.NONE))
    })
    public abstract static class BoundNodeRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected BoundNodeRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation(storeBytecodeIndex = false)
        static final class BoundNode {
            @Specialization
            @SuppressWarnings("truffle-unused") // Verify both implicit and explicit node bindings.
            public static Node doDefault(@Bind Node node, @Bind("$node") Node explicitNode) {
                assertSame(node, explicitNode);
                return node;
            }
        }

        @Operation(storeBytecodeIndex = false)
        static final class BoundNodeWithCache {
            @Specialization
            @SuppressWarnings("truffle-unused") // Verify both implicit and explicit node bindings.
            public static Node doDefault(@Bind Node node, @Bind("$node") Node explicitNode,
                            @Cached InlinedConditionProfile profile) {
                assertTrue(profile.profile(node, true));
                assertSame(node, explicitNode);
                return node;
            }
        }
    }

    private static NodeOptimizationTestNode parse(BytecodeParser<NodeOptimizationTestNodeGen.Builder> builder) {
        BytecodeRootNodes<NodeOptimizationTestNode> nodes = NodeOptimizationTestNodeGen.create(LANGUAGE, BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(nodes.count() - 1);
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                    enableYield = true, enableSerialization = true, //
                    enableSpecializationIntrospection = true, enableQuickening = true, //
                    boxingEliminationTypes = {long.class, int.class, boolean.class})
    public abstract static class NodeOptimizationTestNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected NodeOptimizationTestNode(BytecodeDSLTestLanguage language,
                        FrameDescriptor.Builder frameDescriptor) {
            super(language, frameDescriptor.build());
        }

        @Operation
        static final class SpecializationTest {
            @Specialization
            public static int doInt(int a) {
                return a;
            }

            @Specialization
            public static long doLong(long a) {
                return a;
            }

            @Specialization
            public static boolean doBoolean(boolean a) {
                return a;
            }
        }

        @Operation
        static final class SingleSpecialization {
            @Specialization
            public static Object doDefault(Object v) {
                return v;
            }
        }

        @Operation
        static final class BoundNode {
            @Specialization
            public static Object doDefault(Object v, @SuppressWarnings("unused") @Bind Node node) {
                return v;
            }
        }

        @Operation
        static final class BoundBytecodeNode {
            @Specialization
            public static Object doDefault(Object v, @SuppressWarnings("unused") @Bind BytecodeNode node) {
                return v;
            }
        }

    }

}
