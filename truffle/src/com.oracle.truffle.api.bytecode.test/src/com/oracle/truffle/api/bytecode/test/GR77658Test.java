/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Specialization;
import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;

public class GR77658Test extends AbstractInstructionTest {

    private static final BytecodeDSLTestLanguage LANGUAGE = null;

    @Test
    public void testDefaultLocalStabilizes() {
        GR77658RootNode node = GR77658RootNodeGen.create(LANGUAGE, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            BytecodeLocal local = b.createLocal();

            b.beginIfThen();
            b.emitLoadArgument(0);
            b.beginStoreLocal(local);
            b.emitLoadConstant(42);
            b.endStoreLocal();
            b.endIfThen();

            b.beginReturn();
            b.emitLoadLocal(local);
            b.endReturn();
            b.endRoot();
        }).getNode(0);

        assertEquals(42, node.getCallTarget().call(true));
        assertInstructions(node,
                        "load.argument",
                        "branch.false",
                        "load.constant$Int",
                        "store.local$Int$Int",
                        "load.local$Int",
                        "return");

        assertEquals(GR77658RootNode.DEFAULT_LOCAL_VALUE, node.getCallTarget().call(false));
        assertInstructions(node,
                        "load.argument",
                        "branch.false",
                        "load.constant$Int",
                        "store.local$Int$Int",
                        "load.local$generic",
                        "return");

        assertEquals(42, node.getCallTarget().call(true));
        assertInstructions(node,
                        "load.argument",
                        "branch.false",
                        "load.constant",
                        "store.local$generic",
                        "load.local$generic",
                        "return");

        var quickenings = assertQuickenings(node, 6, 5);
        assertStable(quickenings, node, true);
        assertStable(quickenings, node, false);
    }

    @Test
    public void testMaterializedDefaultLocalStabilizes() {
        GR77658RootNode node = GR77658RootNodeGen.create(LANGUAGE, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            BytecodeLocal local = b.createLocal();

            b.beginIfThen();
            b.emitLoadArgument(0);
            b.beginStoreLocal(local);
            b.emitLoadConstant(42);
            b.endStoreLocal();
            b.endIfThen();

            b.beginReturn();
            b.beginLoadLocalMaterialized(local);
            b.emitMaterializeFrame();
            b.endLoadLocalMaterialized();
            b.endReturn();
            b.endRoot();
        }).getNode(0);

        assertEquals(42, node.getCallTarget().call(true));
        assertEquals(GR77658RootNode.DEFAULT_LOCAL_VALUE, node.getCallTarget().call(false));
        assertEquals(42, node.getCallTarget().call(true));

        var quickenings = assertQuickenings(node, 6, 5);
        assertStable(quickenings, node, true);
        assertStable(quickenings, node, false);
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                    enableMaterializedLocalAccesses = true, //
                    defaultLocalValue = "DEFAULT_LOCAL_VALUE", //
                    boxingEliminationTypes = int.class)
    public abstract static class GR77658RootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        public static final Object DEFAULT_LOCAL_VALUE = new Object();

        protected GR77658RootNode(BytecodeDSLTestLanguage language, FrameDescriptor.Builder frameDescriptor) {
            super(language, frameDescriptor.build());
        }

        @Operation
        public static final class MaterializeFrame {
            @Specialization
            public static MaterializedFrame materialize(VirtualFrame frame) {
                return frame.materialize();
            }
        }
    }
}
