/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import java.io.ByteArrayOutputStream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.TestAPIAccessor;
import com.oracle.truffle.api.test.common.TestUtils;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

/**
 * Similar to jdk.graal.compiler.truffle.test.GR42688test, but this one also runs on SVM and in
 * Polyglot Isolate (but can't do additional checks only available in the compiler package, because
 * that can access classes from the optimized runtime).
 */
public class GR42688LanguageTest {

    @SuppressWarnings("truffle-inlining")
    public abstract static class TypeIndexNode extends Node {

        public abstract int execute(Object value);

        @SuppressWarnings("unused")
        @Specialization
        public int doInt(int n) {
            return 1;
        }

        @SuppressWarnings("unused")
        @Specialization
        public int doDouble(double n) {
            return 2;
        }
    }

    public abstract static class CalleeNode extends RootNode {
        protected CalleeNode() {
            super(null);
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            return execute(frame.getArguments()[0], frame.getArguments()[1]);
        }

        public abstract Object execute(Object value, Object klass);

        @SuppressWarnings("unused")
        @Specialization(guards = "typeIndexNode.execute(value) == cachedTypeIndex", limit = "2")
        public boolean doCached(Object value, int checkedAgainst,
                        @Cached TypeIndexNode typeIndexNode,
                        @Cached("typeIndexNode.execute(value)") int cachedTypeIndex) {
            return cachedTypeIndex == checkedAgainst;
        }

        @Override
        public String getName() {
            return "callee";
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    private static class CallerNode extends RootNode {
        @Child private DirectCallNode callNode;
        private final String name;

        CallerNode(CallTarget target, String name) {
            super(null);
            this.name = name;
            this.callNode = Truffle.getRuntime().createDirectCallNode(target);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return callNode.call(frame.getArguments());
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

    @TruffleLanguage.Registration
    static class DeoptLoopLanguage extends TruffleLanguage<DeoptLoopLanguage.DLLangContext> {
        static final String ID = TestUtils.getDefaultLanguageId(DeoptLoopLanguage.class);

        static class DLLangContext {
            private final CallTarget intCaller1;
            private final CallTarget intCaller2;
            private final CallTarget doubleCaller;

            {
                CallTarget callee = GR42688LanguageTestFactory.CalleeNodeGen.create().getCallTarget();
                intCaller1 = new CallerNode(callee, "intCaller1").getCallTarget();
                intCaller2 = new CallerNode(intCaller1, "intCaller2").getCallTarget();
                doubleCaller = new CallerNode(callee, "doubleCaller").getCallTarget();
            }
        }

        @Override
        protected DLLangContext createContext(Env env) {
            return new DLLangContext();
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            String src = request.getSource().getCharacters().toString();
            DLLangContext langContext = TestAPIAccessor.engineAccess().getCurrentContext(getClass());
            return switch (src) {
                case "intCaller1" -> langContext.intCaller1;
                case "intCaller2" -> langContext.intCaller2;
                case "doubleCaller" -> langContext.doubleCaller;
                default -> throw new AssertionError("Unknown source!");
            };
        }
    }

    @Test
    public void testDeoptLoopDetected() {
        TruffleTestAssumptions.assumeOptimizingRuntime();
        TruffleTestAssumptions.assumeDeoptLoopDetectionAvailable();
        int compilationThreshold = 10;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder() //
                        .allowExperimentalOptions(true) //
                        .option("engine.CompileImmediately", "false") //
                        .option("engine.BackgroundCompilation", "false") //
                        .option("engine.DynamicCompilationThresholds", "false") //
                        .option("engine.MultiTier", "false") //
                        .option("engine.Splitting", "false") //
                        .option("engine.SingleTierCompilationThreshold", String.valueOf(compilationThreshold)) //
                        .option("engine.CompilationFailureAction", "Silent") //
                        .option("engine.CompileOnly", "callee,intCaller1,intCaller2") //
                        .option("engine.TraceCompilation", "true") //
                        .out(outputStream) //
                        .err(outputStream) //
                        .build()) {
            Value intCaller1 = context.parse(DeoptLoopLanguage.ID, "intCaller1");
            Value intCaller2 = context.parse(DeoptLoopLanguage.ID, "intCaller2");
            Value doubleCaller = context.parse(DeoptLoopLanguage.ID, "doubleCaller");

            /*
             * We have 2 direct callers of callee, one which uses int (intCaller1), another using
             * double (doubleCaller). intCaller2 calls callee indirectly via intCaller1. The call
             * graph is the following.
             */
            //@formatter:off
             /*
              * intCaller2
              *     |
              * intCaller1      doubleCaller
              *        \        /
              *         \      /
              *          callee
              */
            //@formatter:on
            /*
             * We are using Tier 2 compilation only and so for in each compilation, all callees are
             * inlined. intCaller2 inlines intCaller1 and callee, and both intCaller1 and
             * doubleCaller inline callee. This test demonstrates that the inline caches in callee
             * never generalize to see both int and double, for the two instances of TypeIndexNode.
             * This state generalization is needed for the compilation of intCaller2 to succeed and
             * remain valid.
             */
            for (int i = 0; i < compilationThreshold; i++) {
                intCaller1.execute(42, 1);
            }

            /*
             * So intCaller1 is compiled first, and it will not be deoptimized after doubleCaller is
             * called, because they are independent callers of callee.
             */

            // At this point callee has a single
            // TypeIndexNode with:
            // TypeIndexNode 1 state=1=int

            // Let's call with a double now
            doubleCaller.execute(3.14, 2);
            // What happens to the inline cache in callee is now it looks like:
            // TypeIndexNode 2 state=2=double (executed first)
            // TypeIndexNode 1 state=3=int+double (executed second)

            /*
             * Now let's see how intCaller2 causes a deopt loop. Default value for
             * DeoptCycleDetectionThreshold is 15, so "+20" should be enough to detect the deopt
             * loop.
             */
            for (int i = 0; i < compilationThreshold + 20; i++) {
                intCaller2.execute(42, 1);
            }
            /*
             * As long as intCaller2 runs in the interpreter it calls the compiled code for
             * intCaller1, because that handles int just fine, but after intCaller2 gets compiled,
             * it immediately deoptimizes, because TypeIndexNode 2 which is executed first in the
             * inline cache didn't see int since it didn't run in interpreter and the intCaller1
             * compilation was used instead (which handles int just fine). The deoptimization is not
             * precise and ends up in the AST of intCaller2, not intCaller1 or callee, which means
             * intCaller2 in the interpreter still uses the compiled code for intCaller1 and the
             * inline cache of the callee never gets to update to handle int. This is repeated until
             * a deopt loop is detected.
             */

        }
        Assert.assertTrue(outputStream.toString().contains("Deopt taken too many times"));
    }
}
