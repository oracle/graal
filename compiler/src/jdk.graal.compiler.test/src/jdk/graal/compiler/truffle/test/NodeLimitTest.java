/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedRuntimeOptions;

import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.contract.NodeCostUtil;

public class NodeLimitTest extends PartialEvaluationTest {

    @Before
    public void before() {
        setupContext();
        Assume.assumeFalse(dummyTarget().getOptionValue(OptimizedRuntimeOptions.CompileImmediately));
    }

    private static OptimizedCallTarget dummyTarget() {
        return (OptimizedCallTarget) RootNode.createConstantNode(42).getCallTarget();
    }

    @Test
    public void oneRootNodeTestSmallGraalNodeCount() {
        expectBailout(NodeLimitTest::createRootNode);
    }

    @Test
    public void oneRootNodeTestEnoughGraalNodeCount() {
        expectAllOK(NodeLimitTest::createRootNode);
    }

    // test that the timeout function during PE works
    @Test(expected = PermanentBailoutException.class)
    public void testTimeOutLimit() {
        // note the timeout may be subject to scaling (up) because we are running with assertions
        final int secondTimeout = 2;
        setupContext(Context.newBuilder().allowAllAccess(true).allowExperimentalOptions(true).option("compiler.CompilationTimeout", String.valueOf(secondTimeout)).build());

        // NOTE: the following code is intentionally written to explode during partial evaluation!
        // It is wrong in almost every way possible.
        final RootNode rootNode = new TestRootNode() {
            @Override
            public Object execute(VirtualFrame frame) {
                recurse();
                foo();
                return null;
            }

            @ExplodeLoop
            private void recurse() {
                for (int i = 0; i < 1000; i++) {
                    getF().apply(0);
                }
            }

            private Function<Integer, Integer> getF() {
                return new Function<>() {
                    @Override
                    public Integer apply(Integer integer) {
                        return integer < 500 ? getF().apply(integer + 1) : 0;
                    }
                };
            }
        };
        partialEval((OptimizedCallTarget) rootNode.getCallTarget(), new Object[]{});
    }

    // test the limit by setting a small one, normally the limit is not enabled
    @Test(expected = PermanentBailoutException.class)
    public void testSetLimit() {
        // a small resonable default to ensure if a user sets it the functionality works
        setupContext(Context.newBuilder().allowAllAccess(true).allowExperimentalOptions(true).option("compiler.MaximumGraalGraphSize", String.valueOf(5000)).build());

        // NOTE: the following code is intentionally written to explode during partial evaluation!
        // It is wrong in almost every way possible.
        final RootNode rootNode = new TestRootNode() {
            @Override
            public Object execute(VirtualFrame frame) {
                recurse();
                foo();
                return null;
            }

            @ExplodeLoop
            private void recurse() {
                for (int i = 0; i < 1000; i++) {
                    getF().apply(0);
                }
            }

            private Function<Integer, Integer> getF() {
                return new Function<>() {
                    @Override
                    public Integer apply(Integer integer) {
                        return integer < 500 ? getF().apply(integer + 1) : 0;
                    }
                };
            }
        };
        partialEval((OptimizedCallTarget) rootNode.getCallTarget(), new Object[]{});
    }

    private static class TestRootNode extends RootNode {
        // Used to introduce allocation during PE to increase the size of the graph
        private static class MyClass {
            int[] array = new int[100];

            MyClass() {
                for (int i = 0; i < array.length; i++) {
                    array[i] = value();
                }
            }

            @CompilerDirectives.TruffleBoundary
            private static int value() {
                return 1;
            }

            public int sum() {
                int sum = 0;
                for (int i : array) {
                    sum += i;
                }
                return sum;
            }
        }

        // Used as a black hole for filler code
        @SuppressWarnings("unused") private int global;
        @SuppressWarnings("unused") private int globalI;

        protected TestRootNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            foo();
            return null;
        }

        protected void foo() {
            for (; globalI < 1000; globalI++) {
                global += new MyClass().sum();
            }

        }
    }

    private static RootNode createRootNode() {
        return new TestRootNode();
    }

    private void expectBailout(Supplier<RootNode> rootNodeFactory) {
        try {
            peRootNode(getBaselineGraphNodeCount(rootNodeFactory.get()) / 2, rootNodeFactory);
        } catch (PermanentBailoutException ignored) {
            // Expected, intentionally ignored
            return;
        } catch (Throwable e) {
            Assert.fail("Unexpected exception caught.");
        }
        Assert.fail("Expected permanent bailout that never happened.");
    }

    private void expectAllOK(Supplier<RootNode> rootNodeFactory) {
        int adjust = 3; // account for reduction in base graph size by GR-34551
        peRootNode((getBaselineGraphNodeCount(rootNodeFactory.get()) + adjust) * 2, rootNodeFactory);
    }

    private int getBaselineGraphNodeCount(RootNode rootNode) {
        final OptimizedCallTarget baselineGraphTarget = (OptimizedCallTarget) rootNode.getCallTarget();
        final StructuredGraph baselineGraph = partialEval(baselineGraphTarget, new Object[]{});
        return NodeCostUtil.computeGraphSize(baselineGraph);
    }

    private void peRootNode(int nodeLimit, Supplier<RootNode> rootNodeFactory) {
        setupContext(Context.newBuilder().allowAllAccess(true).allowExperimentalOptions(true).option("compiler.MaximumGraalGraphSize", Integer.toString(nodeLimit)).build());
        RootCallTarget target = rootNodeFactory.get().getCallTarget();
        final Object[] arguments = {1};
        partialEval((OptimizedCallTarget) target, arguments);
    }

}
