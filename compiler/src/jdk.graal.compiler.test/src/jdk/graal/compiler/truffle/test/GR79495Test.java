/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.bytecode.test.DebugBytecodeRootNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;

/**
 * Regression tests for frame materializations in inlined Bytecode DSL continuations.
 */
public class GR79495Test extends PartialEvaluationTest {

    @Test
    public void testCopyToMaterializedContinuationFrame() {
        /*
         * Regression test to ensure frame copying on entry/exit gets virtualized.
         * @formatter:off
         * def yieldingRoot():
         *   yield 0L
         *   return 20L + (yield 1L)
         *
         * def wrapperRoot(arg):
         *   return resume(resume(arg, null), 22L);
         *
         * cont = yieldingRoot()
         * wrapperRoot(cont)
         * @formatter:on
         * During PE of wrapperRoot, the inner resume yields with 20L on the stack, which triggers a copy from virtual to materialized frame.
         * The outer resume re-enters with a live stack operand and should trigger a copy from materialized to virtual frame. Both should virtualize.
         */
        BytecodeRootNodes<TestInterpreter> nodes = TestInterpreterGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginYield();
            b.emitLoadConstant(0L);
            b.endYield();
            b.beginReturn();
            b.beginAdd();
            b.emitLoadConstant(20L);
            b.beginYield();
            b.emitLoadConstant(1L);
            b.endYield();
            b.endAdd();
            b.endReturn();
            b.endRoot();

            b.beginRoot();
            b.beginReturn();
            b.beginContinueInlined();
            b.beginContinueInlined();
            b.emitLoadArgument(0);
            b.emitLoadNull();
            b.endContinueInlined();
            b.emitLoadConstant(22L);
            b.endContinueInlined();
            b.endReturn();
            b.endRoot();
        });

        TestInterpreter yieldingRoot = nodes.getNode(0);
        TestInterpreter wrapperRoot = nodes.getNode(1);
        yieldingRoot.getBytecodeNode().setUncachedThreshold(0);
        wrapperRoot.getBytecodeNode().setUncachedThreshold(0);

        OptimizedCallTarget yieldingTarget = (OptimizedCallTarget) yieldingRoot.getCallTarget();
        OptimizedCallTarget wrapperTarget = (OptimizedCallTarget) wrapperRoot.getCallTarget();

        StructuredGraph graph;
        try {
            // Continuations are one-shot, so disable profiling in partialEval and do it manually.
            preventProfileCalls = true;
            for (int i = 0; i < 3; i++) {
                ContinuationResult first = (ContinuationResult) yieldingTarget.call();
                Assert.assertEquals(42L, wrapperTarget.call(first));
            }

            graph = partialEval(wrapperTarget, null);
        } finally {
            preventProfileCalls = false;
        }

        assertTrue("unexpected materialized allocations: " + graph.getNodes().filter(AllocatedObjectNode.class).snapshot(),
                        graph.getNodes().filter(AllocatedObjectNode.class).isEmpty());
        assertTrue("unexpected object allocations: " + graph.getNodes().filter(AbstractNewObjectNode.class).snapshot(),
                        graph.getNodes().filter(AbstractNewObjectNode.class).isEmpty());
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableYield = true)
    abstract static class TestInterpreter extends DebugBytecodeRootNode implements BytecodeRootNode {
        protected TestInterpreter(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Add {
            @Specialization
            static long doLong(long left, long right) {
                return left + right;
            }
        }

        @Operation(storeBytecodeIndex = true)
        static final class ContinueInlined {
            @SuppressWarnings("unused")
            @Specialization(guards = "result.getContinuationRootNode() == rootNode", limit = "3")
            static Object invokeDirect(ContinuationResult result, Object value,
                            @Cached("result.getContinuationRootNode()") ContinuationRootNode rootNode,
                            @Cached("createForcedInlineCall(rootNode.getCallTarget())") DirectCallNode callNode) {
                return callNode.call(result.getFrame(), value);
            }

            static DirectCallNode createForcedInlineCall(CallTarget target) {
                DirectCallNode callNode = DirectCallNode.create(target);
                callNode.forceInlining();
                return callNode;
            }
        }
    }
}
