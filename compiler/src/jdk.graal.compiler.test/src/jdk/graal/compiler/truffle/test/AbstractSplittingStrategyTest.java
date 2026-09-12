/*
 * Copyright (c) 2011, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.ReflectionUtils;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedDirectCallNode;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedTruffleRuntimeListener;

public class AbstractSplittingStrategyTest extends TestWithPolyglotOptions {

    protected static final OptimizedTruffleRuntime runtime = (OptimizedTruffleRuntime) Truffle.getRuntime();
    static final Object[] noArguments = {};
    protected SplitCountingListener listener;

    protected static void testSplitsDirectCallsHelper(OptimizedCallTarget callTarget, Object[] firstArgs, Object[] secondArgs) {
        // two callers for a target are needed
        runtime.createDirectCallNode(callTarget);
        CallsTargetRootNode callerRoot = new CallsTargetRootNode(callTarget);
        OptimizedCallTarget callerTarget = (OptimizedCallTarget) callerRoot.getCallTarget();
        Assert.assertFalse("Uninitialized root was ready for compilation", callerTarget.prepareForCompilation(true, 1, false));
        callerTarget.call(firstArgs);
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callTarget));
        callerTarget.call(firstArgs);
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callTarget));
        callerTarget.call(secondArgs);
        Assert.assertTrue("Target does not need split after the node went polymorphic", getNeedsSplit(callTarget));
        Assert.assertFalse("Target split before compilation preparation", callerRoot.callNode.isCallTargetCloned());
        Assert.assertTrue(callerTarget.prepareForCompilation(false, 1, false));
        Assert.assertFalse("Inline preparation split the call target", callerRoot.callNode.isCallTargetCloned());
        Assert.assertFalse("Compilation was not delayed after splitting", callerTarget.prepareForCompilation(true, 1, false));
        Assert.assertTrue("Target was not split during compilation preparation", callerRoot.callNode.isCallTargetCloned());
        Assert.assertTrue("Compilation was delayed more than once", callerTarget.prepareForCompilation(true, 1, false));
    }

    protected static void testDoesNotSplitDirectCallHelper(OptimizedCallTarget callTarget, Object[] firstArgs, Object[] secondArgs) {
        // two callers for a target are needed
        runtime.createDirectCallNode(callTarget);
        CallsTargetRootNode callerRoot = new CallsTargetRootNode(callTarget);
        OptimizedCallTarget callerTarget = (OptimizedCallTarget) callerRoot.getCallTarget();
        callerTarget.call(firstArgs);
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callTarget));
        callerTarget.call(firstArgs);
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callTarget));
        callerTarget.call(secondArgs);
        Assert.assertFalse("Target needs split without reporting", getNeedsSplit(callTarget));
        callerTarget.call(secondArgs);
        Assert.assertTrue(callerTarget.prepareForCompilation(true, 1, false));
        Assert.assertFalse("Target does not need split but is split", callerRoot.callNode.isCallTargetCloned());

        // A new call node to an unmarked target does not split.
        final DirectCallNode newCallNode = runtime.createDirectCallNode(callTarget);
        newCallNode.call(firstArgs);
        Assert.assertFalse("new call node to non \"needs split\" target is split", newCallNode.isCallTargetCloned());
    }

    protected static void testNeedsSplitButDoesNotSplitDirectCallHelper(OptimizedCallTarget callTarget, Object[] firstArgs, Object[] secondArgs) {
        // two callers for a target are needed
        runtime.createDirectCallNode(callTarget);
        CallsTargetRootNode callerRoot = new CallsTargetRootNode(callTarget);
        OptimizedCallTarget callerTarget = (OptimizedCallTarget) callerRoot.getCallTarget();
        callerTarget.call(firstArgs);
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callTarget));
        callerTarget.call(firstArgs);
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callTarget));
        callerTarget.call(secondArgs);
        Assert.assertTrue("Target does not need split after the node went polymorphic", getNeedsSplit(callTarget));
        callerTarget.call(secondArgs);
        Assert.assertTrue(callerTarget.prepareForCompilation(true, 1, false));
        Assert.assertFalse("Target shouldn't be split but is split", callerRoot.callNode.isCallTargetCloned());

        // A new call node is not split outside compilation preparation.
        final DirectCallNode newCallNode = runtime.createDirectCallNode(callTarget);
        newCallNode.call(firstArgs);
        Assert.assertFalse("new call node to non \"needs split\" target is split", newCallNode.isCallTargetCloned());
    }

    protected static Boolean getNeedsSplit(OptimizedCallTarget callTarget) {
        try {
            return (Boolean) reflectivelyGetField(callTarget, "needsSplit");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Assert.assertTrue("Cannot read \"needsSplit\" field from OptimizedCallTarget", false);
            return false;
        }
    }

    protected static Object reflectivelyGetField(Object o, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Field fallbackEngineDataField = null;
        Class<?> cls = o.getClass();
        while (fallbackEngineDataField == null) {
            try {
                fallbackEngineDataField = cls.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                if (cls.getSuperclass() != null) {
                    cls = cls.getSuperclass();
                } else {
                    throw e;
                }
            }
        }
        ReflectionUtils.setAccessible(fallbackEngineDataField, true);
        return fallbackEngineDataField.get(o);
    }

    protected static void reflectivelySetField(Object o, String fieldName, Object value) throws NoSuchFieldException, IllegalAccessException {
        Field fallbackEngineDataField = null;
        Class<?> cls = o.getClass();
        while (fallbackEngineDataField == null) {
            try {
                fallbackEngineDataField = cls.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                if (cls.getSuperclass() != null) {
                    cls = cls.getSuperclass();
                } else {
                    throw e;
                }
            }
        }
        ReflectionUtils.setAccessible(fallbackEngineDataField, true);
        fallbackEngineDataField.set(o, value);
    }

    protected static void createDummyTargetsToBoostGrowingSplitLimit() {
        for (int i = 0; i < 10; i++) {
            new DummyRootNode().getCallTarget();
        }
    }

    @Before
    public void addListener() {
        setupContext("engine.Compilation", "false",
                        "engine.SplittingGrowthLimit", "2.0");
        listener = new SplitCountingListener();
        runtime.addListener(listener);
    }

    @After
    public void removeListener() {
        runtime.removeListener(listener);
    }

    static class SplitCountingListener implements OptimizedTruffleRuntimeListener {

        int splitCount = 0;

        @Override
        public void onCompilationSplit(OptimizedDirectCallNode callNode) {
            splitCount++;
        }
    }

    static class DummyRootNode extends RootNode {

        @Child private Node polymorphic = new Node() {
        };

        protected DummyRootNode() {
            super(null);
        }

        @Override
        public boolean isCloningAllowed() {
            return true;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return 1;
        }

        @Override
        public String toString() {
            return "INNER";
        }
    }

    // Root node for all nodes in this test
    @ReportPolymorphism
    abstract static class SplittingTestNode extends Node {
        public abstract Object execute(VirtualFrame frame);
    }

    static class ReturnsFirstArgumentNode extends SplittingTestNode {
        @Override
        public Object execute(VirtualFrame frame) {
            return frame.getArguments()[0];
        }
    }

    static class ReturnsSecondArgumentNode extends SplittingTestNode {
        @Override
        public Object execute(VirtualFrame frame) {
            return frame.getArguments()[1];
        }
    }

    static final class CallsTargetRootNode extends RootNode {

        @Child OptimizedDirectCallNode callNode;

        CallsTargetRootNode(OptimizedCallTarget target) {
            super(null);
            callNode = (OptimizedDirectCallNode) runtime.createDirectCallNode(target);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return callNode.call(frame.getArguments());
        }
    }

    abstract static class SplittableRootNode extends RootNode {

        protected SplittableRootNode() {
            super(null);
        }

        @Override
        public boolean isCloningAllowed() {
            return true;
        }
    }

    static class SplittingTestRootNode extends SplittableRootNode {
        @Child private SplittingTestNode bodyNode;

        SplittingTestRootNode(SplittingTestNode bodyNode) {
            super();
            this.bodyNode = bodyNode;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return bodyNode.execute(frame);
        }
    }
}
