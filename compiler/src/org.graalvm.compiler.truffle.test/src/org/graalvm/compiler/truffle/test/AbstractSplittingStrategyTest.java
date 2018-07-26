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

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.ReflectionUtils;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntimeListener;
import org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.lang.reflect.Field;

public class AbstractSplittingStrategyTest {

    protected static final GraalTruffleRuntime runtime = (GraalTruffleRuntime) Truffle.getRuntime();

    private static TruffleCompilerOptions.TruffleOptionsOverrideScope doNotCompileScope;
    private static TruffleCompilerOptions.TruffleOptionsOverrideScope growthLimitScope;
    private static TruffleCompilerOptions.TruffleOptionsOverrideScope hardLimitScope;

    @BeforeClass
    public static void before() {
        doNotCompileScope = TruffleCompilerOptions.overrideOptions(TruffleCompilerOptions.TruffleCompilation, false);
        growthLimitScope = TruffleCompilerOptions.overrideOptions(TruffleCompilerOptions.TruffleSplittingGrowthLimit, 2.0);
        hardLimitScope = TruffleCompilerOptions.overrideOptions(TruffleCompilerOptions.TruffleSplittingMaxNumberOfSplitNodes, 1000);
    }

    @AfterClass
    public static void after() {
        hardLimitScope.close();
        growthLimitScope.close();
        doNotCompileScope.close();
    }

    protected SplitCountingListener listener;

    @Before
    public void addListener() {
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

    static final Object[] noArguments = {};

    protected static void createDummyTargetsToBoostGrowingSplitLimit() {
        for (int i = 0; i < 10; i++) {
            runtime.createCallTarget(new DummyRootNode());
        }
    }

    protected static int DUMMYROOTNODECOUNT = NodeUtil.countNodes(new DummyRootNode());

    static class DummyRootNode extends RootNode {

        @Child private Node polymorphic = new Node() {
            @Override
            public NodeCost getCost() {
                return NodeCost.POLYMORPHIC;
            }
        };

        @Override
        public boolean isCloningAllowed() {
            return true;
        }

        protected DummyRootNode() {
            super(null);
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

    abstract class SplittableRootNode extends RootNode {

        protected SplittableRootNode() {
            super(null);
        }

        @Override
        public boolean isCloningAllowed() {
            return true;
        }
    }
}
