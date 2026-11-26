/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeVersion;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants.Variant;
import com.oracle.truffle.api.bytecode.LocalAccessor;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.bytecode.test.error_tests.ExpectWarning;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public class BytecodeVersionTest {
    @Test
    public void testLocalRepresentation() {
        for (var variant : LocalValueRepresentationRootBuilder.variants()) {
            boolean legacy = variant.getGeneratedClass() == LocalValueRepresentationRootV1.class || variant.getGeneratedClass() == LocalValueRepresentationRootV1BE.class;
            for (boolean cached : new boolean[]{false, true}) {
                LocalValueRepresentationRoot root = variant.create(null, BytecodeConfig.DEFAULT, b -> {
                    b.beginRoot();
                    BytecodeLocal local = b.createLocal();
                    b.beginYield();
                    b.emitLoadNull();
                    b.endYield();
                    b.beginReturn();
                    b.emitIsCleared(local);
                    b.endReturn();
                    b.endRoot();
                }).getNode(0);
                root.getBytecodeNode().setUncachedThreshold(cached ? 0 : Integer.MAX_VALUE);
                ContinuationResult result = (ContinuationResult) root.getCallTarget().call();
                BytecodeNode node = root.getBytecodeNode();
                int bci = result.getBytecodeLocation().getBytecodeIndex();
                Frame frame = result.getFrame();
                assertSame(legacy ? null : BytecodeNode.INVALID, node.getLocalValue(bci, frame, 0));
                // A get/set round trip preserves Illegal only with V2.
                node.setLocalValue(bci, frame, 0, node.getLocalValue(bci, frame, 0));
                assertSame(legacy ? null : BytecodeNode.INVALID, node.getLocalValue(bci, frame, 0));
                assertEquals(!legacy, result.continueWith(null));
                result = (ContinuationResult) root.getCallTarget().call();
                frame = result.getFrame();
                node.setLocalValue(bci, frame, 0, 42L);
                assertEquals(42L, node.getLocalValue(bci, frame, 0));
                node.setLocalValue(bci, frame, 0, BytecodeNode.INVALID);
                assertSame(BytecodeNode.INVALID, node.getLocalValue(bci, frame, 0));
                // In V1 the marker is an ordinary object; in V2 it clears the slot.
                node.copyLocalValues(bci, frame, frame);
                assertEquals(!legacy, result.continueWith(null));
                result = (ContinuationResult) root.getCallTarget().call();
                frame = result.getFrame();
                node.setLocalValue(bci, frame, 0, null);
                assertNull(node.getLocalValue(bci, frame, 0));
                assertEquals(false, result.continueWith(null));
            }
        }
    }

    @SuppressWarnings("truffle-inlining")
    @OperationProxy.Proxyable(allowUncached = true)
    public abstract static class ConstantOperation extends Node {
        public abstract long execute();

        @Specialization
        public static long doLong() {
            return 42L;
        }
    }

}

@ExpectWarning({"Bytecode DSL behaviour version 1 is older than the newest version 2.%",
                "Bytecode DSL behaviour version 1 is older than the newest version 2.%"})
@GenerateBytecodeTestVariants({
                @Variant(suffix = "V1", configuration = @GenerateBytecode(//
                                version = BytecodeVersion.V1, languageClass = BytecodeDSLTestLanguage.class, enableYield = true, enableUncachedInterpreter = true)),
                @Variant(suffix = "V1BE", configuration = @GenerateBytecode(//
                                version = BytecodeVersion.V1, languageClass = BytecodeDSLTestLanguage.class, enableYield = true, enableUncachedInterpreter = true, enableQuickening = true, boxingEliminationTypes = long.class)),
                @Variant(suffix = "V2", configuration = @GenerateBytecode(//
                                version = BytecodeVersion.V2, languageClass = BytecodeDSLTestLanguage.class, enableYield = true, enableUncachedInterpreter = true)),
                @Variant(suffix = "V2BE", configuration = @GenerateBytecode(//
                                version = BytecodeVersion.V2, languageClass = BytecodeDSLTestLanguage.class, enableYield = true, enableUncachedInterpreter = true, enableQuickening = true, boxingEliminationTypes = long.class)),
                @Variant(suffix = "Latest", configuration = @GenerateBytecode(//
                                version = BytecodeVersion.LATEST, languageClass = BytecodeDSLTestLanguage.class, enableYield = true, enableUncachedInterpreter = true, enableQuickening = true, boxingEliminationTypes = long.class))})
abstract class LocalValueRepresentationRoot extends RootNode implements BytecodeRootNode {
    protected LocalValueRepresentationRoot(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Operation
    @ConstantOperand(type = LocalAccessor.class)
    static final class IsCleared {
        @Specialization
        static boolean isCleared(VirtualFrame frame, LocalAccessor local, @Bind BytecodeNode node) {
            return local.isCleared(node, frame);
        }
    }
}

@ExpectWarning({"A version should be specified explicitly. Defaulting to version 1. In the future, an explicit version will be required. See BytecodeVersion#V1 for the selected behaviour.",
                """
                                Bytecode DSL behaviour version 1 is older than the newest version 2.
                                Please migrate to the newest version. See the BytecodeVersion Javadoc for migration instructions.
                                Support will be removed in release 25.8.\
                                """})
@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
@OperationProxy(BytecodeVersionTest.ConstantOperation.class)
abstract class Omitted extends RootNode implements BytecodeRootNode {
    protected Omitted(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }
}

@ExpectWarning("""
                Bytecode DSL behaviour version 1 is older than the newest version 2.
                Please migrate to the newest version. See the BytecodeVersion Javadoc for migration instructions.
                Support will be removed in release 25.8.\
                """)
@GenerateBytecode(version = BytecodeVersion.V1, languageClass = BytecodeDSLTestLanguage.class)
@OperationProxy(BytecodeVersionTest.ConstantOperation.class)
abstract class Older extends RootNode implements BytecodeRootNode {
    protected Older(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }
}

@GenerateBytecode(version = BytecodeVersion.LATEST, languageClass = BytecodeDSLTestLanguage.class)
@OperationProxy(BytecodeVersionTest.ConstantOperation.class)
abstract class Latest extends RootNode implements BytecodeRootNode {
    protected Latest(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }
}
