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

import com.oracle.truffle.api.CompilerDirectives;
import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.LocalAccessor;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

/**
 * Regression tests to validate that cached tags are correctly updated when a continuation
 * transitions from uncached to cached.
 */
public class UncachedYieldTest {
    @Test
    public void testUncachedYieldTransitionOnEntry() {
        BytecodeRootNodes<UncachedYieldTestRootNode> nodes = UncachedYieldTestRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            // @formatter:off
            b.beginRoot();
                BytecodeLocal argLocal = b.createLocal();
                b.beginStoreLocal(argLocal);
                    b.emitLoadArgument(0);
                b.endStoreLocal();

                b.beginWhile();
                    b.emitLoadConstant(true);
                    b.beginYield();
                        b.emitMyLoadLocal(argLocal);
                    b.endYield();
                b.endWhile();
            b.endRoot();
            // @formatter:on
        });
        UncachedYieldTestRootNode root = nodes.getNode(0);
        Object argument = new Object();
        ContinuationResult result = (ContinuationResult) root.getCallTarget().call(argument);
        assertEquals(argument, result.getResult());
        for (int i = 0; i < 16; i++) {
            // trigger transition on continuation entry
            result = (ContinuationResult) result.continueWith(null);
            assertEquals(argument, result.getResult());
        }
    }

    @Test
    public void testUncachedYieldTransitionOnBackedge() {
        BytecodeRootNodes<UncachedYieldTestRootNode> nodes = UncachedYieldTestRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            // @formatter:off
            b.beginRoot();

            b.beginYield();
            b.emitLoadNull();
            b.endYield();

            BytecodeLocal counter = b.createLocal();
            b.beginStoreLocal(counter);
            b.emitLoadConstant(16);
            b.endStoreLocal();

            // trigger transition inside resumed loop
            b.beginWhile();
            b.emitCounterDecrement(counter);
            b.emitLoadNull();
            b.endWhile();

            b.beginReturn();
            b.emitMyLoadLocal(counter);
            b.endReturn();
            b.endRoot();
            // @formatter:on
        });
        UncachedYieldTestRootNode root = nodes.getNode(0);
        ContinuationResult cont = (ContinuationResult) root.getCallTarget().call();
        Object result = cont.continueWith(null);
        assertEquals(0, result);
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableYield = true, enableUncachedInterpreter = true, defaultUncachedThreshold = "4", boxingEliminationTypes = {int.class})
    public abstract static class UncachedYieldTestRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected UncachedYieldTestRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        @ConstantOperand(type = LocalAccessor.class)
        public static final class MyLoadLocal {
            @Specialization(rewriteOn = {FrameSlotTypeException.class, UnexpectedResultException.class})
            public static int doInt(VirtualFrame frame, LocalAccessor accessor,
                            @Bind BytecodeNode bytecodeNode) throws UnexpectedResultException {
                return accessor.getInt(bytecodeNode, frame);
            }

            @Specialization(replaces = "doInt")
            public static Object doObject(VirtualFrame frame, LocalAccessor accessor,
                            @Bind BytecodeNode bytecodeNode) {
                return accessor.getObject(bytecodeNode, frame);
            }
        }

        @Operation
        @ConstantOperand(type = LocalAccessor.class)
        public static final class CounterDecrement {
            @Specialization
            public static boolean perform(VirtualFrame frame, LocalAccessor accessor,
                            @Bind BytecodeNode bytecodeNode) {
                try {
                    int next = accessor.getInt(bytecodeNode, frame) - 1;
                    accessor.setInt(bytecodeNode, frame, next);
                    return next != 0;
                } catch (UnexpectedResultException ex) {
                    throw CompilerDirectives.shouldNotReachHere("counter should be an int");
                }
            }
        }
    }
}
