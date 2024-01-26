/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeNodes;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public class NodeBindingTest extends AbstractQuickeningTest {

    protected static final BytecodeDSLTestLanguage LANGUAGE = null;

    @Test
    public void test() {
        // return - (arg0)
        BindingTestRootNode node = (BindingTestRootNode) parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.emitBindNodes();
            b.endReturn();

            b.endRoot();
        }).getRootNode();

        node.getBytecodeNode().setUncachedThreshold(1);
        assertEquals(BytecodeTier.UNCACHED, node.getBytecodeNode().getTier());
        assertEquals(42, node.getCallTarget().call());
        assertEquals(BytecodeTier.CACHED, node.getBytecodeNode().getTier());
        assertEquals(42, node.getCallTarget().call());
    }

    private static BindingTestRootNode parse(BytecodeParser<BindingTestRootNodeGen.Builder> builder) {
        BytecodeNodes<BindingTestRootNode> nodes = BindingTestRootNodeGen.create(BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(nodes.count() - 1);
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                    enableYield = true, enableUncachedInterpreter = true, enableSerialization = true, //
                    enableQuickening = true, //
                    boxingEliminationTypes = {long.class, int.class, boolean.class})
    public abstract static class BindingTestRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected BindingTestRootNode(TruffleLanguage<?> language,
                        FrameDescriptor.Builder frameDescriptor) {
            super(language, customize(frameDescriptor).build());
        }

        private static FrameDescriptor.Builder customize(FrameDescriptor.Builder b) {
            b.defaultValue("Nil");
            return b;
        }

        @Operation
        static final class BindNodes {

            @Specialization
            public static int s1(
                            VirtualFrame frame,
                            @Bind("$node") Node node1,
                            @Bind("this") Node node2,
                            @Bind("$bytecode") BytecodeNode bytecodeNode1,
                            @Bind("$root") BindingTestRootNode root,
                            @Bind("$location") BytecodeLocation location,
                            @Bind("$bci") int bci,
                            @Cached(value = "true", uncached = "false", neverDefault = false) boolean cached) {
                assertNodes(frame.materialize(), bci, node1, node2, bytecodeNode1, root, location, cached);
                return 42;
            }

            @TruffleBoundary
            private static void assertNodes(MaterializedFrame frame, int bci, Node node1, Node node2, BytecodeNode bytecodeNode1, BindingTestRootNode root, BytecodeLocation location, boolean cached) {
                assertSame(node1, node2);
                assertEquals(location, bytecodeNode1.getBytecodeLocation(frame, node1));
                assertEquals(location, BytecodeLocation.get(node1, bci));
                assertFails(() -> bytecodeNode1.getBytecodeLocation(frame, null), NullPointerException.class);
                assertFails(() -> bytecodeNode1.getBytecodeLocation(null, node2), NullPointerException.class);

                if (cached) {
                    assertSame(node1.getParent(), bytecodeNode1);
                    assertSame(bytecodeNode1.getParent(), root);
                } else {
                    assertSame(node1, bytecodeNode1);
                    assertSame(bytecodeNode1.getParent(), root);
                }
            }

        }

    }

}
