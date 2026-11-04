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

import org.junit.Test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeIndexMode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;

/**
 * Tests that SVM threaded bytecode-handler execution preserves the Java interpreter semantics for
 * exceptions. A handler tail-call chain must behave as if the generated Java switch called each
 * handler directly: an exception thrown by a tail-called handler is handled using the throwing
 * bytecode node and bytecode index, not using the original caller's handler state.
 */
public class TailCallHandlerExceptionTest {

    private static final BytecodeDSLTestLanguage LANGUAGE = null;

    private static TailCallExceptionRootNode parse(BytecodeParser<TailCallExceptionRootNodeGen.Builder> builder) {
        BytecodeRootNodes<TailCallExceptionRootNode> nodes = TailCallExceptionRootNodeGen.create(LANGUAGE, BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(0);
    }

    @Test
    public void testLocalCatchAfterTailCalledHandlerThrows() {
        assertEquals(42, parseThrowingChild().getCallTarget().call());
    }

    @Test
    public void testNestedCatchAfterTailCalledHandlerThrows() {
        TailCallExceptionRootNode child = parseThrowingChild();
        TailCallExceptionRootNode parent = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginInvokeTarget();
            b.emitLoadConstant(child.getCallTarget());
            b.endInvokeTarget();
            b.endReturn();
            b.endRoot();
        });

        assertEquals(42, parent.getCallTarget().call());
    }

    private static TailCallExceptionRootNode parseThrowingChild() {
        return parse(b -> {
            b.beginRoot();
            b.emitStep();
            b.beginTryCatch();

            b.beginBlock();
            b.emitStep();
            b.emitThrowMarker();
            b.endBlock();

            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();

            b.endTryCatch();

            b.beginReturn();
            b.emitLoadConstant(-1);
            b.endReturn();
            b.endRoot();
        });
    }

    @SuppressWarnings("serial")
    private static final class MarkerException extends AbstractTruffleException {
        MarkerException(Node node) {
            super("marker", node);
        }
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableTailCallHandlers = true, enableUncachedInterpreter = true, bytecodeIndexMode = BytecodeIndexMode.FRAME)
    abstract static class TailCallExceptionRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {
        protected TailCallExceptionRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation(storeBytecodeIndex = false)
        static final class Step {
            @Specialization
            @SuppressWarnings("static-method")
            static void perform() {
            }
        }

        @Operation(storeBytecodeIndex = false)
        static final class ThrowMarker {
            @Specialization
            static Object perform(@Bind Node node) {
                throw new MarkerException(node);
            }
        }

        @Operation(storeBytecodeIndex = false)
        static final class InvokeTarget {
            @Specialization
            static Object perform(RootCallTarget target,
                            @Cached IndirectCallNode callNode) {
                return callNode.call(target);
            }
        }
    }
}
