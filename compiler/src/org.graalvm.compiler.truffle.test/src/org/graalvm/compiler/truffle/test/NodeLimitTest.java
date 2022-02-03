/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;

public class NodeLimitTest extends PartialEvaluationTest {

    @Before
    public void before() {
        setupContext();
        Assume.assumeFalse(dummyTarget().getOptionValue(PolyglotCompilerOptions.CompileImmediately));
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

    @Test(expected = PermanentBailoutException.class)
    public void testDefaultLimit() {
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
                for (int i = 0; i < 100; i++) {
                    getF().apply(0);
                }
            }

            private Function<Integer, Integer> getF() {
                return new Function<Integer, Integer>() {
                    @Override
                    public Integer apply(Integer integer) {
                        return integer < 500 ? getF().apply(integer + 1) : 0;
                    }
                };
            }
        };
        partialEval((OptimizedCallTarget) rootNode.getCallTarget(), new Object[]{}, CompilationIdentifier.INVALID_COMPILATION_ID);
    }

    private static class TestRootNode extends RootNode {
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
                global += globalI;
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
        final StructuredGraph baselineGraph = partialEval(baselineGraphTarget, new Object[]{}, getCompilationId(baselineGraphTarget));
        return baselineGraph.getNodeCount();
    }

    @SuppressWarnings("try")
    private void peRootNode(int nodeLimit, Supplier<RootNode> rootNodeFactory) {
        setupContext(Context.newBuilder().allowAllAccess(true).allowExperimentalOptions(true).option("engine.MaximumGraalNodeCount", Integer.toString(nodeLimit)).build());
        RootCallTarget target = rootNodeFactory.get().getCallTarget();
        final Object[] arguments = {1};
        partialEval((OptimizedCallTarget) target, arguments, getCompilationId(target));
    }

}
