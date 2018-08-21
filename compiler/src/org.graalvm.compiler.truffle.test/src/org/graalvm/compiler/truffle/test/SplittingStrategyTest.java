/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.ReflectionUtils;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class SplittingStrategyTest extends AbstractSplittingStrategyTest {

    private final FallbackSplitInfo fallbackSplitInfo = new FallbackSplitInfo();

    @Test
    @SuppressWarnings("try")
    public void testDefaultStrategyStabilises() {
        try (TruffleCompilerOptions.TruffleOptionsOverrideScope s = TruffleCompilerOptions.overrideOptions(TruffleCompilerOptions.TruffleSplittingMaxNumberOfSplitNodes,
                        fallbackSplitInfo.getSplitLimit() + 1000)) {
            createDummyTargetsToBoostGrowingSplitLimit();
            class InnerRootNode extends SplittableRootNode {
                OptimizedCallTarget target;
                @Child private DirectCallNode callNode1;

                @Child private Node polymorphic = new Node() {
                    @Override
                    public NodeCost getCost() {
                        return NodeCost.POLYMORPHIC;
                    }
                };

                protected InnerRootNode() {
                    super();
                }

                @Override
                public Object execute(VirtualFrame frame) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    if (callNode1 == null) {
                        callNode1 = runtime.createDirectCallNode(target);
                        adoptChildren();
                    }
                    if (frame.getArguments().length > 0) {
                        if ((Integer) frame.getArguments()[0] < 100) {
                            callNode1.call(frame.getArguments());
                        }
                    }
                    return null;
                }

                @Override
                public String toString() {
                    return "INNER";
                }
            }
            final InnerRootNode innerRootNode = new InnerRootNode();
            final OptimizedCallTarget inner = (OptimizedCallTarget) runtime.createCallTarget(innerRootNode);

            final OptimizedCallTarget mid = (OptimizedCallTarget) runtime.createCallTarget(new SplittableRootNode() {

                @Child private DirectCallNode callNode = null;

                @Child private Node polymorphic = new Node() {
                    @Override
                    public NodeCost getCost() {
                        return NodeCost.POLYMORPHIC;
                    }
                };

                @Override
                public Object execute(VirtualFrame frame) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    if (callNode == null) {
                        callNode = runtime.createDirectCallNode(inner);
                        adoptChildren();
                    }
                    Object[] arguments = frame.getArguments();
                    if ((Integer) arguments[0] < 100) {
                        callNode.call(new Object[]{((Integer) arguments[0]) + 1});
                    }
                    return null;
                }

                @Override
                public String toString() {
                    return "MID";
                }
            });

            OptimizedCallTarget outside = (OptimizedCallTarget) runtime.createCallTarget(new SplittableRootNode() {

                @Child private DirectCallNode outsideCallNode = null; // runtime.createDirectCallNode(mid);

                @Override
                public Object execute(VirtualFrame frame) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    // Emulates builtin i.e. Split immediately
                    if (outsideCallNode == null) {
                        outsideCallNode = runtime.createDirectCallNode(mid);
                        outsideCallNode.cloneCallTarget();
                        adoptChildren();
                    }
                    return outsideCallNode.call(frame.getArguments());
                }

                @Override
                public String toString() {
                    return "OUTSIDE";
                }
            });
            innerRootNode.target = outside;
            createDummyTargetsToBoostGrowingSplitLimit();
            final int baseSplitCount = listener.splitCount;
            outside.call(1);

            // Expected 14
            // OUTSIDE MID
            // MID <split> INNER
            // INNER <split> OUTSIDE
            // OUTSIDE <split> MID
            // INNER OUTSIDE
            // OUTSIDE <split> MID
            // MID <split> INNER
            // MID <split> INNER
            // INNER <split> OUTSIDE
            // OUTSIDE <split> MID
            // INNER <split> OUTSIDE
            // OUTSIDE <split> MID
            // MID <split> INNER
            Assert.assertEquals("Not the right number of splits.", baseSplitCount + 13, listener.splitCount);
        }
    }

    static class CallsInnerAndSwapsCallNode extends RootNode {

        private final RootCallTarget toCall;

        protected CallsInnerAndSwapsCallNode(RootCallTarget toCall) {
            super(null);
            this.toCall = toCall;
        }

        @Child private OptimizedDirectCallNode callNode = null;

        @Override
        public Object execute(VirtualFrame frame) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (callNode == null || callNode.isCallTargetCloned() || callNode.getCallCount() > 2) {
                callNode = (OptimizedDirectCallNode) runtime.createDirectCallNode(toCall);
                adoptChildren();
            }
            return callNode.call(noArguments);
        }
    }

    static class FallbackSplitInfo {
        final Object fallbackEngineData;

        FallbackSplitInfo() {
            this.fallbackEngineData = reflectivelyGetSplittingLimitFromRuntime(runtime, new DummyRootNode());
        }

        int getSplitCount() {
            try {
                return (int) reflectivelyGetField(fallbackEngineData, "splitCount");
            } catch (NoSuchFieldException | IllegalAccessException e) {
                Assert.assertTrue("Exception while reading from engine data", false);
                return 0;
            }
        }

        int getSplitLimit() {
            try {
                return (int) reflectivelyGetField(fallbackEngineData, "splitLimit");
            } catch (NoSuchFieldException | IllegalAccessException e) {
                Assert.assertTrue("Exception while reading from engine data", false);
                return 0;
            }
        }

        void setLimitToCount() {
            try {
                reflectivelySetField(fallbackEngineData, "splitLimit", reflectivelyGetField(fallbackEngineData, "splitCount"));
            } catch (NoSuchFieldException | IllegalAccessException e) {
                Assert.assertTrue("Exception while reading from engine data", false);
            }
        }

        private static Object reflectivelyGetSplittingLimitFromRuntime(GraalTruffleRuntime graalTruffleRuntime, RootNode rootNode) {
            try {
                final Object tvmci = reflectivelyGetField(graalTruffleRuntime, "tvmci");
                final Method getEngineDataMethod = tvmci.getClass().getDeclaredMethod("getEngineData", new Class<?>[]{RootNode.class});
                ReflectionUtils.setAccessible(getEngineDataMethod, true);
                final Object fallbackEngineData = getEngineDataMethod.invoke(tvmci, rootNode);
                return fallbackEngineData;
            } catch (NoSuchFieldException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                Assert.assertTrue("Exception while getting engine data", false);
                return null;
            }
        }

    }

    @Test
    @SuppressWarnings("try")
    public void testMaxLimitForTargetsOutsideEngine() {
        final int expectedIncreaseInNodes = 10;
        try (TruffleCompilerOptions.TruffleOptionsOverrideScope s = TruffleCompilerOptions.overrideOptions(TruffleCompilerOptions.TruffleSplittingMaxNumberOfSplitNodes,
                        fallbackSplitInfo.getSplitCount() + expectedIncreaseInNodes)) {

            final OptimizedCallTarget inner = (OptimizedCallTarget) runtime.createCallTarget(new DummyRootNode());
            final OptimizedCallTarget outer = (OptimizedCallTarget) runtime.createCallTarget(new CallsInnerAndSwapsCallNode(inner));

            createDummyTargetsToBoostGrowingSplitLimit();

            SplitCountingListener localListener = new SplitCountingListener();
            runtime.addListener(localListener);

            for (int i = 0; i < 100; i++) {
                outer.call();
            }
            Assert.assertEquals("Too many of too few splits.", expectedIncreaseInNodes, localListener.splitCount * DUMMYROOTNODECOUNT);
            runtime.removeListener(localListener);
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testGrowingLimitForTargetsOutsideEngine() {
        final int expectedGrowingSplits = (int) (2 * TruffleCompilerOptions.getValue(TruffleCompilerOptions.TruffleSplittingGrowthLimit));
        final OptimizedCallTarget inner = (OptimizedCallTarget) runtime.createCallTarget(new DummyRootNode());
        final OptimizedCallTarget outer = (OptimizedCallTarget) runtime.createCallTarget(new CallsInnerAndSwapsCallNode(inner));
        fallbackSplitInfo.setLimitToCount();
        try (TruffleCompilerOptions.TruffleOptionsOverrideScope s = TruffleCompilerOptions.overrideOptions(TruffleCompilerOptions.TruffleSplittingMaxNumberOfSplitNodes,
                        fallbackSplitInfo.getSplitCount() + 2 * expectedGrowingSplits)) {
            // Create 2 targets to boost the growing limit
            runtime.createCallTarget(new DummyRootNode());
            runtime.createCallTarget(new DummyRootNode());

            SplitCountingListener localListener = new SplitCountingListener();
            runtime.addListener(localListener);

            for (int i = 0; i < 100; i++) {
                outer.call();
            }

            Assert.assertEquals("Too many of too few splits.", expectedGrowingSplits, localListener.splitCount);
            runtime.removeListener(localListener);
        }
    }

    @TruffleLanguage.Registration(id = "SplitTestLanguage", name = "SplitTestLanguage")
    public static class SplitTestLanguage extends TruffleLanguage<TruffleLanguage.Env> {
        static final String ID = "SplitTestLanguage";

        private final RootCallTarget callTarget = runtime.createCallTarget(new CallsInnerAndSwapsCallNode(runtime.createCallTarget(new DummyRootNode())));

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            if (request.getSource().getCharacters().equals("exec")) {
                return callTarget;
            } else if (request.getSource().getCharacters().toString().startsWith("new")) {
                return runtime.createCallTarget(new DummyRootNode());
            } else {
                throw new IllegalArgumentException();
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testHardSplitLimitInContext() {
        final int expectedSplittingIncrease = 20;
        try (TruffleCompilerOptions.TruffleOptionsOverrideScope s = TruffleCompilerOptions.overrideOptions(TruffleCompilerOptions.TruffleSplittingMaxNumberOfSplitNodes, expectedSplittingIncrease)) {
            Context c = Context.create();
            for (int i = 0; i < 100; i++) {
                eval(c, "exec");
            }
            Assert.assertEquals("Wrong number of splits: ", expectedSplittingIncrease, listener.splitCount * DUMMYROOTNODECOUNT);
        }
    }

    @Test
    public void testGrowingSplitLimitInContext() {
        Context c = Context.create();
        // Eval a lot to fill out budget
        useUpTheBudget(c);
        final int baseSplitCount = listener.splitCount;
        for (int i = 0; i < 10; i++) {
            eval(c, "exec");
        }
        Assert.assertEquals("Split count growing without new call targets", baseSplitCount, listener.splitCount);

        eval(c, "new");
        for (int i = 0; i < 10; i++) {
            eval(c, "exec");
        }
        Assert.assertEquals("Split count not correct after one new target", (int) (baseSplitCount + TruffleCompilerOptions.getValue(TruffleCompilerOptions.TruffleSplittingGrowthLimit)),
                        listener.splitCount);

        eval(c, "new2");
        for (int i = 0; i < 10; i++) {
            eval(c, "exec");
        }
        Assert.assertEquals("Split count not correct after one new target", (int) (baseSplitCount + 2 * TruffleCompilerOptions.getValue(TruffleCompilerOptions.TruffleSplittingGrowthLimit)),
                        listener.splitCount);
    }

    @Test
    public void testSplitLimitIsContextSpecific() {
        Context c1 = Context.create();
        Context c2 = Context.create();
        // Use up the c1 budget
        useUpTheBudget(c1);
        final int c1BaseSplitCount = listener.splitCount;
        // Try to split some more in c1
        for (int i = 0; i < 10; i++) {
            eval(c1, "exec");
        }
        Assert.assertEquals("Splitting over budget!", c1BaseSplitCount, listener.splitCount);
        // Try to split in c2
        for (int i = 0; i < 10; i++) {
            eval(c2, "exec");
        }
        Assert.assertTrue("No splitting in different context", c1BaseSplitCount < listener.splitCount);
    }

    private static void useUpTheBudget(Context context) {
        for (int i = 0; i < 10_000; i++) {
            eval(context, "exec");
        }
    }

    private static void eval(Context context, String s) {
        context.eval(SplitTestLanguage.ID, s);
    }
}
