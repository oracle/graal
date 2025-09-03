/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.graal.compiler.truffle.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.common.NullObject;
import com.oracle.truffle.compiler.TruffleCompilerListener;
import com.oracle.truffle.runtime.AbstractCompilationTask;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedTruffleRuntimeListener;

import jdk.graal.compiler.truffle.TruffleCompilerOptions;
import jdk.graal.compiler.util.CollectionsUtil;

public class DeoptLoopDetectionTest {

    private static final Map<String, String> ENGINE_OPTIONS = CollectionsUtil.mapOf(
                    "engine.CompilationFailureAction", "Silent", //
                    "engine.BackgroundCompilation", "false", //
                    "engine.CompileImmediately", "true");
    private final AtomicReference<CallTarget> callTargetFilter = new AtomicReference<>();
    private final AtomicReference<Boolean> compilationResult = new AtomicReference<>();
    private final AtomicReference<String> compilationFailedReason = new AtomicReference<>();
    private Context context;
    private final OptimizedTruffleRuntimeListener listener = new OptimizedTruffleRuntimeListener() {
        @Override
        public void onCompilationSuccess(OptimizedCallTarget target, AbstractCompilationTask task, TruffleCompilerListener.GraphInfo graph, TruffleCompilerListener.CompilationResultInfo result) {
            OptimizedTruffleRuntimeListener.super.onCompilationSuccess(target, task, graph, result);
            if (target == callTargetFilter.get()) {
                compilationResult.set(Boolean.TRUE);
            }
        }

        @Override
        public void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout, int tier, Supplier<String> lazyStackTrace) {
            if (target == callTargetFilter.get()) {
                compilationResult.set(Boolean.FALSE);
                compilationFailedReason.set(reason);
            }
        }
    };

    @Before
    public void setup() {
        context = Context.newBuilder().options(ENGINE_OPTIONS).build();
        context.enter();
        Assume.assumeTrue(Truffle.getRuntime() instanceof OptimizedTruffleRuntime);
        ((OptimizedTruffleRuntime) Truffle.getRuntime()).addListener(listener);

    }

    @After
    public void tearDown() {
        context.close();
        ((OptimizedTruffleRuntime) Truffle.getRuntime()).removeListener(listener);
    }

    @Test
    public void testAlwaysDeopt() {
        assertDeoptLoop(new BaseRootNode() {
            @Override
            public Object execute(VirtualFrame frame) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return null;
            }
        }, "alwaysDeopt", CallTarget::call, 0, 1);
    }

    @Test
    public void testAlwaysDeoptNoInvalidate() {
        AssertionError expectedError = Assert.assertThrows(AssertionError.class, () -> assertDeoptLoop(new BaseRootNode() {
            @CompilerDirectives.TruffleBoundary
            static void boundaryMethod() {

            }

            @Override
            public Object execute(VirtualFrame frame) {
                int arg = (int) frame.getArguments()[0];
                int threshold = TruffleCompilerOptions.DeoptCycleDetectionThreshold.getDefaultValue();
                if (arg < threshold) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                // call boundary method to prevent compiler from moving the following deoptimization
                // up
                boundaryMethod();
                CompilerDirectives.transferToInterpreter();
                return null;
            }
        }, "alwaysDeoptNoInvalidate", new Consumer<CallTarget>() {
            int i;

            @Override
            public void accept(CallTarget callTarget) {
                callTarget.call(i++);
                if (i == TruffleCompilerOptions.DeoptCycleDetectionThreshold.getDefaultValue() + 1) {
                    /*
                     * Invalidate the target that was just deoptimized (but not invalidated) by
                     * transferToInterpreter. The exact same compilation is then repeated in the
                     * next iteration, but because deoptimize nodes with deoptimization action
                     * "None" (like the one used for transferToInterpreter) don't trigger deopt loop
                     * detection, no deopt loop should be detected.
                     */
                    ((OptimizedCallTarget) callTarget).invalidate("Force one more recompile");
                }
            }
        }, 0, 1));
        Assert.assertEquals("No deopt loop detected after " + MAX_EXECUTIONS + " executions", expectedError.getMessage());
    }

    @Test
    public void testLocalDeopt() {
        assertDeoptLoop(new BaseRootNode() {

            @CompilationFinal int cachedValue;

            @Override
            public Object execute(VirtualFrame frame) {
                int arg = (int) frame.getArguments()[0];
                if (this.cachedValue != arg) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    this.cachedValue = arg;
                }
                return this.cachedValue;
            }

        }, "localDeopt", (target) -> {
            target.call(0);
            target.call(1);
        }, 0, 2);
    }

    @Test
    public void testGlobalDeopt() {
        assertDeoptLoop(new BaseRootNode() {

            @CompilationFinal Assumption assumption = Assumption.create();

            @Override
            public Object execute(VirtualFrame frame) {
                boolean arg = (boolean) frame.getArguments()[0];
                if (arg && assumption.isValid()) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    assumption.invalidate();
                    assumption = Assumption.create();
                }
                return arg;
            }

        }, "globalDeopt", (target) -> {
            target.call(true);
        }, 0, 1);
    }

    static class StaticAssumptionRootNode extends BaseRootNode {
        @CompilationFinal static volatile Assumption assumption = Assumption.create();

        @Override
        public Object execute(VirtualFrame frame) {
            if (!assumption.isValid()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            return 0;
        }
    }

    @Test
    public void testStaticAssumptionNoDeopt() {
        boolean[] firstExecution = new boolean[]{true};
        AssertionError expectedError = Assert.assertThrows(AssertionError.class, () -> assertDeoptLoop(new StaticAssumptionRootNode(), "staticAssumptionNoDeopt", (target) -> {
            target.call();
            if (firstExecution[0]) {
                StaticAssumptionRootNode.assumption.invalidate();
                StaticAssumptionRootNode.assumption = Assumption.create();
                firstExecution[0] = false;
            }
        }, 0, 1));
        Assert.assertEquals("No deopt loop detected after " + MAX_EXECUTIONS + " executions", expectedError.getMessage());
    }

    @Test
    public void testLocalDeoptWithChangedCode() {
        assertDeoptLoop(new BaseRootNode() {

            @CompilationFinal boolean cachedValue;

            @Override
            public Object execute(VirtualFrame frame) {
                int arg = (int) frame.getArguments()[0];
                if (arg > 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    this.cachedValue = !this.cachedValue;
                }
                int result = arg;
                if (cachedValue) {
                    result--;
                } else {
                    result++;
                }
                return result;
            }

        }, "localLoopDeoptwithChangedCode", (target) -> {
            target.call(1);
        }, 0, 1);
    }

    @Test
    public void testStabilizeLate() {
        assertDeoptLoop(new BaseRootNode() {

            static final int GENERIC = Integer.MIN_VALUE;

            @CompilationFinal int cachedValue;
            int seenCount = 0;

            @Override
            public Object execute(VirtualFrame frame) {
                int arg = (int) frame.getArguments()[0];
                // we assume Integer.MIN_VALUE is never used in value
                assert arg != Integer.MIN_VALUE;

                int result;
                if (cachedValue == GENERIC) {
                    result = arg;
                } else {
                    if (cachedValue != arg) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        if (seenCount < 20) {
                            this.cachedValue = arg;
                        } else {
                            cachedValue = GENERIC;
                        }
                        seenCount++;
                        result = arg;
                    } else {
                        result = cachedValue;
                    }
                }
                return result;
            }

        }, "stabilizeLate", new Consumer<>() {

            int input = 1;

            @Override
            public void accept(CallTarget target) {
                target.call(input++);
            }
        }, 0, 1);
    }

    @TruffleLanguage.Registration(id = NonConstantLanguageContextTestLanguage.ID, contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
    static class NonConstantLanguageContextTestLanguage extends TruffleLanguage<NonConstantLanguageContextTestLanguage.LanguageContext> {
        static final String ID = "NonConstantLanguageContextTestLanguage";
        static final String ROOT_NODE_NAME = "languageContextFallacy";

        static class LanguageContext {
            private final Env env;

            @CompilationFinal boolean initialized;

            LanguageContext(Env env) {
                this.env = env;
            }

            void ensureInitialized() {
                if (!initialized) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    initialize();
                }
            }

            private void initialize() {
                // perform initialization
                initialized = true;
            }

            static final ContextReference<LanguageContext> REF = ContextReference.create(NonConstantLanguageContextTestLanguage.class);
        }

        @Override
        protected LanguageContext createContext(Env env) {
            return new LanguageContext(env);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            BaseRootNode rootNode = new BaseRootNode() {
                @Override
                public Object execute(VirtualFrame frame) {
                    LanguageContext languageContext = LanguageContext.REF.get(this);
                    languageContext.ensureInitialized();
                    // ...
                    return NullObject.SINGLETON;
                }
            };
            rootNode.name = ROOT_NODE_NAME;
            return rootNode.getCallTarget();
        }
    }

    @Test
    public void testNonConstantLanguageContext() throws IOException {
        try (Engine engine = Engine.newBuilder().options(ENGINE_OPTIONS).build()) {
            Source source = Source.newBuilder(NonConstantLanguageContextTestLanguage.ID, "", "TestSource").build();
            AtomicReference<OptimizedCallTarget> captureTargetReference = new AtomicReference<>();
            final OptimizedTruffleRuntimeListener listener2 = new OptimizedTruffleRuntimeListener() {
                @Override
                public void onCompilationSuccess(OptimizedCallTarget target, AbstractCompilationTask task, TruffleCompilerListener.GraphInfo graph,
                                TruffleCompilerListener.CompilationResultInfo result) {
                    OptimizedTruffleRuntimeListener.super.onCompilationSuccess(target, task, graph, result);
                    captureTargetReference.set(target);
                }
            };
            ((OptimizedTruffleRuntime) Truffle.getRuntime()).addListener(listener2);
            try {
                try (Context context1 = Context.newBuilder().engine(engine).build()) {
                    context1.eval(source);
                }
            } finally {
                ((OptimizedTruffleRuntime) Truffle.getRuntime()).removeListener(listener2);
            }
            assertDeoptLoop((BaseRootNode) captureTargetReference.get().getRootNode(), NonConstantLanguageContextTestLanguage.ROOT_NODE_NAME, new Consumer<>() {

                int calleeIndex = 0;

                @Override
                public void accept(CallTarget target) {
                    try (Context context2 = Context.newBuilder().engine(engine).build()) {
                        context2.eval(source);
                    }
                }
            }, 1, 1);
        }
    }

    static class MyFunction {
        private final RootNode root;

        MyFunction(int id) {
            this.root = new RootNode(null) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return NullObject.SINGLETON;
                }

                @Override
                public String getName() {
                    return "callee" + id;
                }

                @Override
                public String toString() {
                    return getName();
                }
            };
        }
    }

    @Test
    public void testLookupAndDispatch() {
        assertDeoptLoop(new BaseRootNode() {

            @Child IndirectCallNode callNode = IndirectCallNode.create();

            @Override
            public Object execute(VirtualFrame frame) {
                MyFunction function = (MyFunction) frame.getArguments()[0];
                CallTarget target = function.root.getCallTarget();
                return callNode.call(target);
            }

        }, "tooBroadDispatch", new Consumer<>() {

            int calleeIndex = 0;

            @Override
            public void accept(CallTarget target) {
                target.call(new MyFunction(calleeIndex++));
            }
        }, 0, 1);
    }

    @Test
    public void testSkippedException() {
        assertDeoptLoop(new BaseRootNode() {

            @Override
            public Object execute(VirtualFrame frame) {
                try {
                    throw new IndexOutOfBoundsException();
                } catch (RuntimeException e) {
                    //
                }
                return null;
            }

        }, "skippedException", new Consumer<>() {
            @Override
            public void accept(CallTarget target) {
                target.call();
            }
        }, 0, 1);
    }

    private static final int MAX_EXECUTIONS = 1024;

    private void assertDeoptLoop(BaseRootNode root, String name, Consumer<CallTarget> callStrategy, int previousCompilations, int compilationsPerIteration) {
        root.name = name;
        CallTarget callTarget = root.getCallTarget();
        callTargetFilter.set(callTarget);
        compilationResult.set(null);
        compilationFailedReason.set(null);

        callStrategy.accept(callTarget);

        assertEquals(Boolean.TRUE, compilationResult.get());
        int iterationCounter = 0;
        while (compilationResult.get()) {
            if (iterationCounter >= MAX_EXECUTIONS) {
                throw new AssertionError("No deopt loop detected after " + MAX_EXECUTIONS + " executions");
            }
            callStrategy.accept(callTarget);
            iterationCounter++;
        }

        assertTrue(previousCompilations + iterationCounter * compilationsPerIteration > TruffleCompilerOptions.DeoptCycleDetectionThreshold.getDefaultValue());
        assertEquals(Boolean.FALSE, compilationResult.get());
        String failedReason = compilationFailedReason.get();
        assertNotNull(failedReason);
        assertTrue(failedReason, failedReason.contains("Deopt taken too many times"));
    }

    abstract static class BaseRootNode extends RootNode {

        private String name = this.getClass().getSimpleName();

        BaseRootNode() {
            super(null);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return getName();
        }
    }
}
