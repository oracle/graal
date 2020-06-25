/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import java.util.function.Supplier;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.polyglot.Context;

public class NodeLimitTest extends PartialEvaluationTest {

    @Before
    public void before() {
        setupContext();
        Assume.assumeFalse(dummyTarget().getOptionValue(PolyglotCompilerOptions.CompileImmediately));
    }

    private static OptimizedCallTarget dummyTarget() {
        return (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(42));
    }

    @Test
    public void oneRootNodeTestSmallGraalNodeCount() {
        expectBailout(NodeLimitTest::createRootNode);
    }

    @Test
    public void oneRootNodeTestEnoughGraalNodeCount() {
        expectAllOK(NodeLimitTest::createRootNode);
    }

    @Test
    @SuppressWarnings("try")
    public void testWithTruffleInlining() {
        Assume.assumeFalse(dummyTarget().getOptionValue(PolyglotCompilerOptions.LanguageAgnosticInlining));
        setupContext(Context.newBuilder().allowAllAccess(true).allowExperimentalOptions(true).option("engine.MaximumInlineNodeCount", "10").build());
        RootNode rootNode = createRootNodeWithCall(new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                CompilerAsserts.neverPartOfCompilation();
                return null;
            }
        });
        RootCallTarget target = Truffle.getRuntime().createCallTarget(rootNode);
        final Object[] arguments = {1};
        partialEval((OptimizedCallTarget) target, arguments, CompilationIdentifier.INVALID_COMPILATION_ID);
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
        partialEval((OptimizedCallTarget) Truffle.getRuntime().createCallTarget(rootNode), new Object[]{}, CompilationIdentifier.INVALID_COMPILATION_ID);
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
        peRootNode(getBaselineGraphNodeCount(rootNodeFactory.get()) * 2, rootNodeFactory);
    }

    private int getBaselineGraphNodeCount(RootNode rootNode) {
        final OptimizedCallTarget baselineGraphTarget = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(rootNode);
        final StructuredGraph baselineGraph = partialEval(baselineGraphTarget, new Object[]{}, CompilationIdentifier.INVALID_COMPILATION_ID);
        return baselineGraph.getNodeCount();
    }

    @SuppressWarnings("try")
    private void peRootNode(int nodeLimit, Supplier<RootNode> rootNodeFactory) {
        setupContext(Context.newBuilder().allowAllAccess(true).allowExperimentalOptions(true).option("engine.MaximumGraalNodeCount", Integer.toString(nodeLimit)).build());
        RootCallTarget target = Truffle.getRuntime().createCallTarget(rootNodeFactory.get());
        final Object[] arguments = {1};
        partialEval((OptimizedCallTarget) target, arguments, CompilationIdentifier.INVALID_COMPILATION_ID);
    }

    private static RootNode createRootNodeWithCall(final RootNode rootNode) {
        return new TestRootNode() {

            @Child DirectCallNode call = Truffle.getRuntime().createDirectCallNode(Truffle.getRuntime().createCallTarget(rootNode));

            @Override
            public Object execute(VirtualFrame frame) {
                foo();
                call.call(new Object[0]);
                return null;
            }
        };
    }
}
