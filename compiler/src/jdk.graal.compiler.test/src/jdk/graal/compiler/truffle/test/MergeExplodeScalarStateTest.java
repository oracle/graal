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

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.extended.OpaqueValueNode;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;

/** Unmarked merge explosion with scalar BCI/SP locals, without early escape analysis. */
public class MergeExplodeScalarStateTest extends PartialEvaluationTest {
    // Bytecode local slots in dispatchSnippet, after its control parameter.
    private static final int BCI_LOCAL = 1;
    private static final int SP_LOCAL = 2;

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    private static long dispatchSnippet(int[] control) {
        int bci = 0;
        int stackPointer = 64;
        while (true) {
            if (!CompilerDirectives.inCompiledCode()) {
                control[3] = 1;
                return 100L * bci + stackPointer;
            }
            if (--control[1] == 0) {
                return 100L * bci + stackPointer;
            }
            // Keep each transition on a distinct backedge during parsing.
            switch (bci) {
                case 0:
                    if (control[0] == 0) {
                        GraalDirectives.controlFlowAnchor();
                        bci = 101;
                        stackPointer = 65;
                    } else {
                        GraalDirectives.controlFlowAnchor();
                        bci = 202;
                        stackPointer = 66;
                    }
                    break;
                case 101:
                    if (control[2] == 0) {
                        GraalDirectives.controlFlowAnchor();
                        bci = 202;
                        stackPointer = 66;
                    } else {
                        GraalDirectives.controlFlowAnchor();
                        bci = 303;
                        stackPointer = 65;
                    }
                    break;
                case 202:
                    if (control[2] == 0) {
                        GraalDirectives.controlFlowAnchor();
                        bci = 303;
                        stackPointer = 65;
                    } else {
                        GraalDirectives.controlFlowAnchor();
                        bci = 101;
                        stackPointer = 65;
                    }
                    break;
                case 303:
                    if (control[0] == 0) {
                        GraalDirectives.controlFlowAnchor();
                        bci = 101;
                        stackPointer = 65;
                    } else {
                        GraalDirectives.controlFlowAnchor();
                        bci = 202;
                        stackPointer = 66;
                    }
                    break;
                default:
                    throw shouldNotReachHere("Unexpected BCI");
            }
        }
    }

    @Test
    public void unmarkedScalarLocalsDispatch() {
        assertScalarState(-1);
    }

    @Test
    public void unmarkedScalarLocalsDeoptimization() {
        for (int bci : new int[]{0, 101, 202}) {
            assertScalarState(bci);
        }
    }

    private void assertScalarState(int deoptBci) {
        RootNode root = new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return dispatchSnippet((int[]) frame.getArguments()[0]);
            }
        };
        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        StructuredGraph graph = partialEval(root, new int[]{0, 2, 0, 0});
        IntegerSwitchNode dispatcher = null;
        for (IntegerSwitchNode candidate : graph.getNodes().filter(IntegerSwitchNode.class)) {
            if (candidate.value() instanceof ValuePhiNode phi && phi.merge() instanceof LoopBeginNode) {
                Assert.assertNull("Expected one reconstructed dispatcher", dispatcher);
                dispatcher = candidate;
            }
        }
        Assert.assertNotNull(dispatcher);
        Assert.assertTrue("Exercise repeated dispatcher extension", dispatcher.keyCount() >= 3);
        for (int i = 0; i < dispatcher.keyCount(); i++) {
            Assert.assertEquals("Synthetic IDs must be dense, not interpreter BCIs", i, dispatcher.keyAt(i).asInt());
        }
        ValuePhiNode selector = (ValuePhiNode) dispatcher.value();
        FrameState state = selector.merge().stateAfter();
        ValuePhiNode bci = (ValuePhiNode) state.values().get(BCI_LOCAL);
        ValuePhiNode sp = (ValuePhiNode) state.values().get(SP_LOCAL);
        Assert.assertNotSame(selector, bci);
        Assert.assertNotSame(selector, sp);
        Assert.assertSame(selector.merge(), bci.merge());
        Assert.assertSame(selector.merge(), sp.merge());
        Assert.assertEquals(selector.valueCount(), bci.valueCount());
        Assert.assertEquals(selector.valueCount(), sp.valueCount());
        Assert.assertTrue("Exercise multiple predecessors for an entry", selector.valueCount() > dispatcher.keyCount());
        for (int i = 0; i < bci.valueCount(); i++) {
            int entryBci = bci.valueAt(i).asJavaConstant().asInt();
            Assert.assertEquals("BCI and SP must follow the same predecessor ordering", stackPointerAt(entryBci), sp.valueAt(i).asJavaConstant().asInt());
        }
        if (deoptBci >= 0) {
            OpaqueValueNode opaqueBci = graph.addWithoutUnique(new OpaqueValueNode(bci));
            FixedGuardNode guard = graph.add(new FixedGuardNode(graph.addOrUnique(new IntegerEqualsNode(opaqueBci, ConstantNode.forInt(deoptBci, graph))),
                            DeoptimizationReason.TransferToInterpreter, DeoptimizationAction.None, true));
            // Deoptimize with the shared dispatcher state rather than a specialized entry state.
            guard.setStateBefore(state);
            graph.addBeforeFixed(dispatcher, guard);
        }
        compile(target, graph);
        Assert.assertTrue(target.isValid());
        int deoptimizations = 0;
        for (int entry = 0; entry < 2; entry++) {
            for (int branch = 0; branch < 2; branch++) {
                for (int iterations = 1; iterations <= 7; iterations++) {
                    int[] control = {entry, iterations, branch, 0};
                    long result = (long) target.call(control);
                    if (control[3] != 0) {
                        Assert.assertTrue("Unexpected interpreter fallback", deoptBci >= 0);
                        Assert.assertEquals(100L * deoptBci + stackPointerAt(deoptBci), result);
                        deoptimizations++;
                    } else {
                        int expectedBci = 0;
                        for (int i = 1; i < iterations; i++) {
                            expectedBci = switch (expectedBci) {
                                case 0, 303 -> entry == 0 ? 101 : 202;
                                case 101 -> branch == 0 ? 202 : 303;
                                case 202 -> branch == 0 ? 303 : 101;
                                default -> throw shouldNotReachHere("Unexpected BCI");
                            };
                        }
                        Assert.assertEquals(100L * expectedBci + stackPointerAt(expectedBci), result);
                    }
                    Assert.assertTrue(target.isValid());
                }
            }
        }
        if (deoptBci >= 0) {
            Assert.assertTrue("Expected deoptimization at BCI " + deoptBci, deoptimizations > 0);
        }
    }

    private static int stackPointerAt(int bci) {
        return switch (bci) {
            case 0 -> 64;
            case 101, 303 -> 65;
            case 202 -> 66;
            default -> throw shouldNotReachHere("Unexpected BCI");
        };
    }
}
