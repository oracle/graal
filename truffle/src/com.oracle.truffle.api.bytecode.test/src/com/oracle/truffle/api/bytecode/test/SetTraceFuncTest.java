/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.polyglot.Context;
import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instrumentation;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.Prolog;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;

/**
 * Showcases how to implement the python set trace func feature.
 */
public class SetTraceFuncTest extends AbstractInstructionTest {

    private static SetTraceFuncRootNode parse(BytecodeParser<SetTraceFuncRootNodeGen.Builder> parser) {
        BytecodeRootNodes<SetTraceFuncRootNode> nodes = SetTraceFuncRootNodeGen.create(TraceFunLanguage.REF.get(null), BytecodeConfig.WITH_SOURCE, parser);
        return nodes.getNodes().get(0);
    }

    @Test
    public void test() {
        try (Context c = Context.create(TraceFunLanguage.ID)) {
            c.enter();
            c.initialize(TraceFunLanguage.ID);

            AtomicInteger firstCounter = new AtomicInteger();
            AtomicInteger secondCounter = new AtomicInteger();
            SetTraceFuncRootNode node = parse((b) -> {
                b.beginRoot();
                b.emitTraceFun();
                b.emitTraceFun();

                b.emitSetTraceFun(() -> {
                    firstCounter.incrementAndGet();
                });

                // already in the first execution these two
                // trace fun calls should increment the first counter
                b.emitTraceFun();
                b.emitTraceFun();

                b.emitSetTraceFun((Runnable) () -> {
                    secondCounter.incrementAndGet();
                });

                b.emitTraceFun();
                b.emitTraceFun();

                b.beginReturn();
                b.emitLoadConstant(42);
                b.endReturn();

                b.endRoot();
            });
            assertEquals(42, node.getCallTarget().call());

            assertEquals(2, firstCounter.get());
            assertEquals(2, secondCounter.get());

            assertEquals(42, node.getCallTarget().call());

            assertEquals(4, firstCounter.get());
            assertEquals(6, secondCounter.get());
        }
    }

    @Test
    public void testMultipleRoots() {
        try (Context c = Context.create(TraceFunLanguage.ID)) {
            c.enter();
            c.initialize(TraceFunLanguage.ID);

            AtomicInteger counter = new AtomicInteger();
            SetTraceFuncRootNode foo = parse((b) -> {
                b.beginRoot();
                b.emitTraceFun();
                b.endRoot();
            });

            SetTraceFuncRootNode bar = parse((b) -> {
                b.beginRoot();
                b.emitTraceFun();
                b.emitInvoke(foo);

                b.emitSetTraceFun(() -> {
                    counter.incrementAndGet();
                });

                b.emitTraceFun();
                b.emitInvoke(foo);
                b.endRoot();
            });

            SetTraceFuncRootNode baz = parse((b) -> {
                b.beginRoot();
                b.emitTraceFun();

                b.emitInvoke(bar);

                b.emitTraceFun();
                b.endRoot();
            });

            // Nothing should happen until the trace function is set.
            foo.getCallTarget().call();
            assertEquals(0, counter.get());

            // When we call baz, it calls bar to enable tracing. All roots on the stack (bar, baz)
            // should transition, and every other root (foo) should transition on function entry.
            // We expect one increment from each root.
            baz.getCallTarget().call();
            assertEquals(3, counter.get());

            counter.set(0);

            // For the subsequent call, we expect 2 increments from each root.
            baz.getCallTarget().call();
            assertEquals(6, counter.get());
        }
    }

    @GenerateBytecode(languageClass = TraceFunLanguage.class, //
                    enableYield = true, enableSerialization = true, //
                    enableQuickening = true, //
                    enableUncachedInterpreter = true,  //
                    boxingEliminationTypes = {long.class, int.class, boolean.class})
    public abstract static class SetTraceFuncRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        private static final BytecodeConfig TRACE_FUN = SetTraceFuncRootNodeGen.newConfigBuilder().addInstrumentation(TraceFun.class).build();

        protected SetTraceFuncRootNode(TraceFunLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Prolog
        static final class CheckTraceFunOnEnter {
            @Specialization
            public static void doProlog(@Bind SetTraceFuncRootNode root) {
                if (root.getLanguage(TraceFunLanguage.class).noTraceFun.isValid()) {
                    return;
                }
                root.enableTraceFun();
            }
        }

        private void enableTraceFun() {
            getRootNodes().update(TRACE_FUN);
        }

        @Operation
        @ConstantOperand(type = SetTraceFuncRootNode.class)
        static final class Invoke {
            @Specialization(excludeForUncached = true)
            public static void doInvoke(@SuppressWarnings("unused") SetTraceFuncRootNode callee,
                            @Cached("create(callee.getCallTarget())") DirectCallNode callNode) {
                callNode.call();
            }

            @Specialization(replaces = "doInvoke")
            public static void doInvokeUncached(@SuppressWarnings("unused") SetTraceFuncRootNode callee,
                            @Cached IndirectCallNode callNode) {
                callNode.call(callee.getCallTarget());
            }
        }

        @Operation
        @ConstantOperand(type = Runnable.class)
        static final class SetTraceFun {

            @Specialization
            @TruffleBoundary
            public static void doDefault(Runnable run,
                            @Bind SetTraceFuncRootNode root) {
                TraceFunLanguage language = root.getLanguage(TraceFunLanguage.class);
                language.threadLocal.get().traceFun = run;
                language.noTraceFun.invalidate();
                Truffle.getRuntime().iterateFrames((frameInstance) -> {
                    if (frameInstance.getCallTarget() instanceof RootCallTarget c && c.getRootNode() instanceof SetTraceFuncRootNode r) {
                        r.enableTraceFun();
                    }
                    return null;
                });
            }

        }

        @Instrumentation
        static final class TraceFun {

            @Specialization
            public static void doDefault(@Bind SetTraceFuncRootNode node) {
                Runnable fun = node.getLanguage(TraceFunLanguage.class).threadLocal.get().traceFun;
                if (fun != null) {
                    invokeSetTraceFunc(fun);
                }
            }

            @TruffleBoundary
            private static void invokeSetTraceFunc(Runnable fun) {
                fun.run();
            }
        }

    }

    static class ThreadLocalData {

        private Runnable traceFun;

    }

    @TruffleLanguage.Registration(id = TraceFunLanguage.ID)
    @ProvidedTags(StandardTags.ExpressionTag.class)
    public static class TraceFunLanguage extends TruffleLanguage<Object> {
        public static final String ID = "TraceLineLanguage";

        final ContextThreadLocal<ThreadLocalData> threadLocal = this.locals.createContextThreadLocal((c, t) -> new ThreadLocalData());
        final Assumption noTraceFun = Truffle.getRuntime().createAssumption();

        @Override
        protected Object createContext(Env env) {
            return new Object();
        }

        static final LanguageReference<TraceFunLanguage> REF = LanguageReference.create(TraceFunLanguage.class);
    }

}
