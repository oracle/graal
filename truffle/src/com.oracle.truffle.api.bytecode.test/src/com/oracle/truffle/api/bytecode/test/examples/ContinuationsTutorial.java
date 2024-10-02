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
package com.oracle.truffle.api.bytecode.test.examples;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.LocalVariable;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Bytecode DSL interpreters can suspend and resume execution of single methods using continuations.
 * With continuations, a guest method can suspend itself (using a {@code Yield} operation),
 * persisting its execution state in a continuation object. Later, a caller can resume the
 * continuation to continue execution of the guest method. Both yield and resume pass a value,
 * allowing the caller and callee to communicate.
 * <p>
 * Continuations can be used to implement generators and stackless coroutines. This tutorial will
 * explain how to use continuations in your Bytecode DSL interpreter.
 */
public class ContinuationsTutorial {
    /**
     * The first step to using continuations is to enable them in the generated code. Modifying your
     * {@link GenerateBytecode} specification, set {@code enableYield = true}. Then, rebuild your
     * project to update the generated interpreter.
     */
    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableYield = true)
    public abstract static class YieldingBytecodeNode extends RootNode implements BytecodeRootNode {
        protected YieldingBytecodeNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        public static final class Add {
            @Specialization
            public static int doInt(int x, int y) {
                return x + y;
            }
        }
    }

    /**
     * When continuations are enabled, the Bytecode DSL generates a special {@code Yield} operation
     * that can be used to suspend the current execution.
     * <p>
     * The test below implements the following pseudocode:
     *
     * <pre>
     * def f():
     *   yield 42
     *   return 123
     * </pre>
     */
    @Test
    public void testSimpleContinuation() {
        // @formatter:off
        BytecodeRootNodes<YieldingBytecodeNode> nodes = YieldingBytecodeNodeGen.create(null /* TruffleLanguage */, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
                b.beginYield();
                    b.emitLoadConstant(42);
                b.endYield();
                b.beginReturn();
                    b.emitLoadConstant(123);
                b.endReturn();
            b.endRoot();
        });
        // @formatter:on
        YieldingBytecodeNode f = nodes.getNode(0);

        // When the root node is invoked, Yield suspends, producing a ContinuationResult.
        Object result = f.getCallTarget().call();
        assertTrue(result instanceof ContinuationResult);
        ContinuationResult continuation = (ContinuationResult) result;
        // The ContinuationResult contains the operand value as the "yielded" result.
        assertEquals(42, continuation.getResult());
        // The ContinuationResult can be resumed. The root node continues execution after the Yield.
        assertEquals(123, continuation.continueWith(null));
    }

    /**
     * The caller can supply a value when it resumes the continuation. This value becomes the value
     * produced by the {@code Yield} operation.
     * <p>
     * The test below implements the following pseudocode:
     *
     * <pre>
     * def f():
     *   x = yield 42
     *   return x + 1
     * </pre>
     */
    @Test
    public void testContinuationWithResumeValue() {
        // @formatter:off
        BytecodeRootNodes<YieldingBytecodeNode> nodes = YieldingBytecodeNodeGen.create(null /* TruffleLanguage */, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
                BytecodeLocal x = b.createLocal();
                b.beginStoreLocal(x);
                    b.beginYield();
                        b.emitLoadConstant(42);
                    b.endYield();
                b.endStoreLocal();

                b.beginReturn();
                    b.beginAdd();
                        b.emitLoadLocal(x);
                        b.emitLoadConstant(1);
                    b.endAdd();
                b.endReturn();
            b.endRoot();
        });
        // @formatter:on
        YieldingBytecodeNode f = nodes.getNode(0);

        ContinuationResult continuation = (ContinuationResult) f.getCallTarget().call();
        assertEquals(42, continuation.getResult());
        // The argument to continueWith becomes the value produced by the Yield.
        assertEquals(124, continuation.continueWith(123));
    }

    /**
     * The state of the program (local variables, stack operands, etc.) is also persisted by the
     * continuation.
     * <p>
     * The test below implements the following pseudocode:
     *
     * <pre>
     * def f():
     *   x = 0
     *   while (yield x+x):
     *     x = x + 1
     *   return "done"
     * </pre>
     */
    @Test
    public void testContinuationWithState() {
        // @formatter:off
        BytecodeRootNodes<YieldingBytecodeNode> nodes = YieldingBytecodeNodeGen.create(null /* TruffleLanguage */, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
                BytecodeLocal x = b.createLocal("x", null);
                b.beginStoreLocal(x);
                    b.emitLoadConstant(0);
                b.endStoreLocal();

                b.beginWhile();
                    b.beginYield();
                        b.beginAdd();
                            b.emitLoadLocal(x);
                            b.emitLoadLocal(x);
                        b.endAdd();
                    b.endYield();

                    b.beginStoreLocal(x);
                        b.beginAdd();
                            b.emitLoadLocal(x);
                            b.emitLoadConstant(1);
                        b.endAdd();
                    b.endStoreLocal();
                b.endWhile();

                b.beginReturn();
                    b.emitLoadConstant("done");
                b.endReturn();
            b.endRoot();
        });
        // @formatter:on
        YieldingBytecodeNode f = nodes.getNode(0);

        ContinuationResult continuation = (ContinuationResult) f.getCallTarget().call();
        for (int i = 0; i < 10; i++) {
            assertEquals(i + i, continuation.getResult());
            // Continue executing. Observe how the value of local x is updated in each iteration.
            continuation = (ContinuationResult) continuation.continueWith(true);
        }
        assertEquals(20, continuation.getResult());
        // The continuation frame contains the suspended state. We can also use introspection to
        // read value of x from the frame.
        BytecodeLocation location = continuation.getBytecodeLocation();
        BytecodeNode bytecode = location.getBytecodeNode();
        LocalVariable x = bytecode.getLocals().stream().filter(localVariable -> "x".equals(localVariable.getName())).findFirst().get();
        Object xValue = bytecode.getLocalValue(location.getBytecodeIndex(), continuation.getFrame(), x.getLocalOffset());
        assertEquals(10, xValue);

        // Break out of the loop by resuming with false. The root node finishes execution.
        assertEquals("done", continuation.continueWith(false));
    }

    /**
     * {@link ContinuationResult#continueWith} is a convenient API to test continuations, but for
     * performance reasons it should be avoided in real implementations. Continuations are dynamic
     * values: a new {@link ContinuationResult} is created every time a given {@code Yield}
     * executes. In other words, continuations are not partial evaluation (PE) constants, and calls
     * to {@link ContinuationResult#continueWith} cannot be devirtualized by PE.
     * <p>
     * However, the {@link ContinuationRootNode} <i>is</i> always the same for a given
     * {@code Yield}, so it can be used in inline caches. The {@link ContinuationRootNode} can be
     * accessed with {@link ContinuationResult#getContinuationRootNode()}. You can also cache the
     * call target obtained by {@link ContinuationResult#getContinuationCallTarget()}.
     * <p>
     * These root nodes have a precise calling convention. They take two parameters: the
     * materialized frame containing the continuation state ({@link ContinuationResult#getFrame()}),
     * and the value to resume with.
     * <p>
     * Below we define a new root node with a {@code Resume} operation that defines an inline cache
     * over continuation roots.
     */
    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableYield = true, enableSpecializationIntrospection = true)
    public abstract static class YieldingBytecodeNodeWithResume extends RootNode implements BytecodeRootNode {
        protected YieldingBytecodeNodeWithResume(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        public static final class Add {
            @Specialization
            public static int doInt(int x, int y) {
                return x + y;
            }
        }

        @Operation
        public static final class NotEquals {
            @Specialization
            public static boolean doInt(int x, int y) {
                return x != y;
            }
        }

        @Operation
        public static final class Resume {
            public static final int LIMIT = 3;

            /**
             * This specialization caches up to 3 continuation root nodes, which gives PE the
             * opportunity to inline their resume calls.
             */
            @SuppressWarnings("unused")
            @Specialization(guards = {"result.getContinuationRootNode() == cachedRootNode"}, limit = "LIMIT")
            public static Object resumeDirect(ContinuationResult result, Object value,
                            @Cached("result.getContinuationRootNode()") ContinuationRootNode cachedRootNode,
                            @Cached("create(cachedRootNode.getCallTarget())") DirectCallNode callNode) {
                // The continuation root's calling convention expects the continuation frame and the
                // resume value.
                return callNode.call(result.getFrame(), value);
            }

            /**
             * If too many different root nodes are seen, fall back on an indirect resume call.
             */
            @Specialization(replaces = "resumeDirect")
            public static Object resumeIndirect(ContinuationResult result, Object value,
                            @Cached IndirectCallNode callNode) {
                return callNode.call(result.getContinuationCallTarget(), result.getFrame(), value);
            }
        }

        @Operation
        public static final class IsContinuation {

            @Specialization
            public static boolean doCheck(Object result) {
                return result instanceof ContinuationResult;
            }
        }

    }

    /**
     * Below, we test the resume operation. The test implements the following pseudocode:
     *
     * <pre>
     * def consume(gen):
     *   while(isContinuation(gen)):
     *     gen = resume(gen, null)
     *   return gen
     *
     * def countToN(n):
     *   i = 0
     *   while(i != n):
     *     yield i
     *     i = i + 1
     *   return i
     * </pre>
     */
    @Test
    public void testResume() {
        // @formatter:off
        BytecodeRootNodes<YieldingBytecodeNodeWithResume> nodes = YieldingBytecodeNodeWithResumeGen.create(null /* TruffleLanguage */, BytecodeConfig.DEFAULT, b -> {
            // def consume(gen)
            b.beginRoot();
                BytecodeLocal gen = b.createLocal();

                b.beginStoreLocal(gen);
                    b.emitLoadArgument(0);
                b.endStoreLocal();

                b.beginWhile();
                    b.beginIsContinuation();
                      b.emitLoadLocal(gen);
                    b.endIsContinuation();

                    b.beginStoreLocal(gen);
                        b.beginResume();
                            b.emitLoadLocal(gen);
                            b.emitLoadNull();
                        b.endResume();
                    b.endStoreLocal();
                b.endWhile();

                b.beginReturn();
                    b.emitLoadLocal(gen);
                b.endReturn();
            b.endRoot();

            // def countToN(n)
            b.beginRoot();
                BytecodeLocal i = b.createLocal();
                b.beginStoreLocal(i);
                    b.emitLoadConstant(0);
                b.endStoreLocal();

                b.beginWhile();
                    b.beginNotEquals();
                        b.emitLoadLocal(i);
                        b.emitLoadArgument(0);
                    b.endNotEquals();

                    b.beginBlock();
                        b.beginYield();
                            b.emitLoadLocal(i);
                        b.endYield();

                        b.beginStoreLocal(i);
                            b.beginAdd();
                                b.emitLoadLocal(i);
                                b.emitLoadConstant(1);
                            b.endAdd();
                        b.endStoreLocal();
                    b.endBlock();
                b.endWhile();

                b.beginReturn();
                    b.emitLoadLocal(i);
                b.endReturn();
            b.endRoot();
        });
        // @formatter:on
        YieldingBytecodeNodeWithResume consume = nodes.getNode(0);
        YieldingBytecodeNodeWithResume countToN = nodes.getNode(1);

        ContinuationResult cont = (ContinuationResult) countToN.getCallTarget().call(10);
        // Pass the continuation to consume, which repeatedly resumes it until it stops yielding.
        // If the resume operation receives continuations for the same location, the resume
        // operation can be monomorphized by PE.
        assertEquals(10, consume.getCallTarget().call(cont));
    }
}
