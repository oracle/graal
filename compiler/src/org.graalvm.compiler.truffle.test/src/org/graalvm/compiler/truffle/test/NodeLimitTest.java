/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.core.common.GraalBailoutException;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode;
import org.graalvm.compiler.truffle.runtime.SharedTruffleRuntimeOptions;
import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.compiler.truffle.compiler.SharedTruffleCompilerOptions;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions;

public class NodeLimitTest extends PartialEvaluationTest {

    public NodeLimitTest() {
        runtime = Truffle.getRuntime();
    }

    static TruffleCompilerOptions.TruffleOptionsOverrideScope performanceWarningsAreFatalScope;

    @BeforeClass
    public static void beforeClass() {
        Assume.assumeFalse(TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleCompileImmediately));
        performanceWarningsAreFatalScope = TruffleCompilerOptions.overrideOptions(SharedTruffleCompilerOptions.TrufflePerformanceWarningsAreFatal, false);
    }

    @AfterClass
    public static void afterClass() {
        performanceWarningsAreFatalScope.close();
    }

    @Test
    public void oneRootNodeTest() {
        fullTest(createRootNodeFillerOnly(), createRootNodeFillerAndTest());
    }

    @Test
    public void oneRootNodeTestEscapingFrame() {
        fullTest(createRootNodeFillerThatUsedFrame(), createRootNodeFillerAndTestThatUsedFrame());
    }

    @Test
    public void testWithTruffleInlining() {
        fullTest(createRootNodeWithCall(createRootNodeFillerOnly()), createRootNodeWithCall(createRootNodeFillerAndTest()));
    }

    @Test
    public void testDefaultLimit() {
        // NOTE: the following code is intentionally written to explode during partial evaluation!
        // It is wrong in almost every way possible.
        final RootNode rootNode = new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                recurse();
                assertNotInCompilation();
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

            private void assertNotInCompilation() {
                for (; globalI < 100_000; globalI++) {
                    global++;
                }
                CompilerAsserts.neverPartOfCompilation();
            }
        };
        partialEval((OptimizedCallTarget) runtime.createCallTarget(rootNode), new Object[]{}, StructuredGraph.AllowAssumptions.YES, CompilationIdentifier.INVALID_COMPILATION_ID);
    }

    private static TruffleRuntime runtime;

    // Used as a black hole for filler code
    @SuppressWarnings("unused") private static int global;
    @SuppressWarnings("unused") private static int globalI;

    private static RootNode createRootNodeFillerOnly() {
        return new RootNode(null) {

            @Override
            public Object execute(VirtualFrame frame) {
                foo();
                return null;
            }

            private void foo() {
                for (; globalI < 1000; globalI++) {
                    global += globalI;
                }

            }
        };
    }

    private static RootNode createRootNodeFillerAndTest() {
        return new RootNode(null) {

            @Override
            public Object execute(VirtualFrame frame) {
                foo();
                return null;
            }

            private void foo() {
                for (; globalI < 1000; globalI++) {
                    global += globalI;
                }
                testMethod();
            }

            void testMethod() {
                CompilerAsserts.neverPartOfCompilation();
            }
        };
    }

    private static RootNode createRootNodeFillerThatUsedFrame() {
        FrameDescriptor descriptor = new FrameDescriptor();
        final FrameSlot slot = descriptor.addFrameSlot("test");
        return new RootNode(null, descriptor) {

            @Override
            public Object execute(VirtualFrame frame) {
                frame.setInt(slot, foo());
                return null;
            }

            private int foo() {
                for (; globalI < 1000; globalI++) {
                    global += globalI;
                }
                return global;
            }
        };
    }

    private static RootNode createRootNodeFillerAndTestThatUsedFrame() {
        FrameDescriptor descriptor = new FrameDescriptor();
        FrameSlot slot = (descriptor.getSlots().isEmpty()) ? descriptor.addFrameSlot("test", null, FrameSlotKind.Int) : descriptor.findFrameSlot("test");
        return new RootNode(null, descriptor) {

            @Override
            public Object execute(VirtualFrame frame) {
                frame.setInt(slot, global);
                foo();
                testMethod(frame);
                return null;
            }

            private int foo() {
                for (; globalI < 1000; globalI++) {
                    global += globalI;
                }
                return global;
            }

            void testMethod(VirtualFrame frame) {
                global += (int) frame.getArguments()[0];
                global += FrameUtil.getIntSafe(frame, slot);
                CompilerAsserts.neverPartOfCompilation();
            }
        };
    }

    private void fullTest(RootNode rootNodeFillerOnly, RootNode rootNodeFillerAndTest) {
        expectNeverPartOfCompilation(rootNodeFillerOnly, rootNodeFillerAndTest);
        expectAllOK(rootNodeFillerOnly, rootNodeFillerAndTest);
    }

    private void expectNeverPartOfCompilation(RootNode fillerOnly, RootNode fillerAndTest) {
        try {
            peRootNodeWithFillerAndTest(getBaselineGraphNodeCount(fillerOnly) + 50, fillerAndTest);
            throw new AssertionError("Expected to throw but did not.");
        } catch (GraalBailoutException e) {
            Assert.assertEquals("CompilerAsserts.neverPartOfCompilation()", e.getMessage());
        }
    }

    private void expectAllOK(RootNode fillerOnly, RootNode fillerAndTest) {
        peRootNodeWithFillerAndTest(getBaselineGraphNodeCount(fillerOnly) - 10, fillerAndTest);
    }

    private int getBaselineGraphNodeCount(RootNode rootNode) {
        final OptimizedCallTarget baselineGraphTarget = (OptimizedCallTarget) runtime.createCallTarget(rootNode);
        final StructuredGraph baselineGraph = partialEval(baselineGraphTarget, new Object[]{}, StructuredGraph.AllowAssumptions.YES, CompilationIdentifier.INVALID_COMPILATION_ID);
        return baselineGraph.getNodeCount();
    }

    @SuppressWarnings("try")
    private void peRootNodeWithFillerAndTest(int nodeLimit, RootNode rootNode) {
        try (TruffleCompilerOptions.TruffleOptionsOverrideScope scope = TruffleCompilerOptions.overrideOptions(SharedTruffleCompilerOptions.TruffleMaximumGraalNodeCount, nodeLimit)) {
            RootCallTarget target = runtime.createCallTarget(rootNode);
            final Object[] arguments = {1};
            globalI = 0;
            partialEval((OptimizedCallTarget) target, arguments, StructuredGraph.AllowAssumptions.YES, CompilationIdentifier.INVALID_COMPILATION_ID);
        }
    }

    private static RootNode createRootNodeWithCall(final RootNode rootNode) {
        return new RootNode(null) {

            @Child OptimizedDirectCallNode call = (OptimizedDirectCallNode) runtime.createDirectCallNode(runtime.createCallTarget(rootNode));

            @Override
            public Object execute(VirtualFrame frame) {
                foo();
                call.call(new Object[0]);
                return null;
            }

            private void foo() {
                for (; globalI < 1000; globalI++) {
                    global += globalI;
                }

            }
        };
    }
}
