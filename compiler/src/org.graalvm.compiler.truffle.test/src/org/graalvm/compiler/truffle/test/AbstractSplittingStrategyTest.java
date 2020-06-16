/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;

import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntimeListener;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.ReflectionUtils;

public class AbstractSplittingStrategyTest extends TestWithPolyglotOptions {

    protected static final GraalTruffleRuntime runtime = (GraalTruffleRuntime) Truffle.getRuntime();
    static final Object[] noArguments = {};
    protected SplitCountingListener listener;

    protected static void testSplitsDirectCallsHelper(OptimizedCallTarget callTarget, Object[] firstArgs, Object[] secondArgs) {
        // two callers for a target are needed
        runtime.createDirectCallNode(callTarget);
        final DirectCallNode directCallNode = runtime.createDirectCallNode(callTarget);
        directCallNode.call(firstArgs);
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callTarget));
        directCallNode.call(firstArgs);
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callTarget));
        directCallNode.call(secondArgs);
        Assert.assertTrue("Target does not need split after the node went polymorphic", getNeedsSplit(callTarget));
        directCallNode.call(secondArgs);
        Assert.assertTrue("Target needs split but not split", directCallNode.isCallTargetCloned());

        // Test new dirrectCallNode will split
        final DirectCallNode newCallNode = runtime.createDirectCallNode(callTarget);
        newCallNode.call(firstArgs);
        Assert.assertTrue("new call node to \"needs split\" target is not split", newCallNode.isCallTargetCloned());
    }

    protected static void testDoesNotSplitDirectCallHelper(OptimizedCallTarget callTarget, Object[] firstArgs, Object[] secondArgs) {
        // two callers for a target are needed
        runtime.createDirectCallNode(callTarget);
        final DirectCallNode directCallNode = runtime.createDirectCallNode(callTarget);
        directCallNode.call(firstArgs);
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callTarget));
        directCallNode.call(firstArgs);
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callTarget));
        directCallNode.call(secondArgs);
        Assert.assertFalse("Target needs split without reporting", getNeedsSplit(callTarget));
        directCallNode.call(secondArgs);
        Assert.assertFalse("Target does not need split but is split", directCallNode.isCallTargetCloned());

        // Test new dirrectCallNode will split
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
            runtime.createCallTarget(new DummyRootNode());
        }
    }

    @Before
    public void addListener() {
        setupContext("engine.Compilation", "false",
                        "engine.SplittingGrowthLimit", "2.0",
                        "engine.SplittingMaxNumberOfSplitNodes", "1000");
        listener = new SplitCountingListener();
        runtime.addListener(listener);
    }

    @After
    public void removeListener() {
        runtime.removeListener(listener);
    }

    static class SplitCountingListener implements GraalTruffleRuntimeListener {

        int splitCount = 0;

        @Override
        public void onCompilationSplit(OptimizedDirectCallNode callNode) {
            splitCount++;
        }
    }

    static class DummyRootNode extends RootNode {

        @Child private Node polymorphic = new Node() {
            @Override
            public NodeCost getCost() {
                return NodeCost.POLYMORPHIC;
            }
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

    abstract class SplittableRootNode extends RootNode {

        protected SplittableRootNode() {
            super(null);
        }

        @Override
        public boolean isCloningAllowed() {
            return true;
        }
    }

    class SplittingTestRootNode extends SplittableRootNode {
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
