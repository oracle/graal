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
 * unmodified Software as contributed by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
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
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
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

import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeFrame;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;

public class GR77540Test {

    private static final BytecodeDSLTestLanguage LANGUAGE = null;

    private static GR77540RootNode parse(BytecodeParser<GR77540RootNodeGen.Builder> parser) {
        BytecodeRootNodes<GR77540RootNode> nodes = GR77540RootNodeGen.create(LANGUAGE, BytecodeConfig.DEFAULT, parser);
        return nodes.getNode(0);
    }

    @Test
    public void testMaterializedFrameAcrossUncachedToCachedTransition() {
        GR77540RootNode root = parse(b -> {
            b.beginRoot();
            BytecodeLocal local = b.createLocal();

            b.beginStoreLocal(local);
            b.emitLoadConstant(42L);
            b.endStoreLocal();

            b.beginYield();
            b.emitCreateMaterializedBytecodeFrame();
            b.endYield();

            b.beginReturn();
            b.emitLoadLocal(local);
            b.endReturn();
            b.endRoot();
        });

        ContinuationResult continuation = (ContinuationResult) root.getCallTarget().call();
        BytecodeFrame frame = (BytecodeFrame) continuation.getResult();
        assertEquals(BytecodeTier.UNCACHED, frame.getBytecodeNode().getTier());
        assertEquals(42L, frame.getLocalValue(0));

        root.getBytecodeNode().setUncachedThreshold(0);
        assertEquals(42L, continuation.continueWith(null));
        assertEquals(BytecodeTier.CACHED, root.getBytecodeNode().getTier());
        assertEquals(42L, frame.getLocalValue(0));
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true, enableQuickening = true, boxingEliminationTypes = long.class, enableYield = true,
                    enableBlockScoping = false)
    abstract static class GR77540RootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected GR77540RootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class CreateMaterializedBytecodeFrame {

            @Specialization
            static BytecodeFrame perform(VirtualFrame frame,
                            @Bind BytecodeNode bytecode,
                            @Bind("$bytecodeIndex") int bytecodeIndex) {
                return bytecode.createMaterializedFrame(bytecodeIndex, frame.materialize());
            }
        }
    }
}
