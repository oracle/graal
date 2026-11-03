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
package jdk.graal.compiler.lir.test;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.gen.NodeLIRBuilder;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.LabelRef;
import jdk.graal.compiler.lir.SwitchStrategy;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.extended.SwitchNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaConstant;

public class SwitchStrategyTest extends GraalCompilerTest {

    /// Normal switch lowering creates distinct label references per key. This node forces range
    /// dispatch with one reference per successor so that compiled execution exercises shared targets.
    @NodeInfo
    private static final class RangeSwitchNode extends SwitchNode implements LIRLowerable {
        public static final NodeClass<RangeSwitchNode> TYPE = NodeClass.create(RangeSwitchNode.class);

        /// Preserves the keys of the Java switch through LIR generation.
        private final JavaConstant[] keys;

        /// Copies the switch data while leaving its successors disconnected until they are transferred.
        protected RangeSwitchNode(IntegerSwitchNode original) {
            super(TYPE, original.value(), new AbstractBeginNode[original.blockSuccessorCount()],
                            IntStream.rangeClosed(0, original.keyCount()).map(original::keySuccessorIndex).toArray(), original.getProfileData());
            keys = IntStream.range(0, original.keyCount()).mapToObj(original::keyAt).toArray(JavaConstant[]::new);
        }

        @Override
        public boolean isSorted() {
            return true;
        }

        @Override
        public int keyCount() {
            return keys.length;
        }

        @Override
        public JavaConstant keyAt(int i) {
            return keys[i];
        }

        @Override
        public boolean equalKeys(SwitchNode other) {
            return other instanceof RangeSwitchNode rangeSwitch && Arrays.equals(keys, rangeSwitch.keys);
        }

        @Override
        public Stamp genericSuccessorStamp() {
            return value().stamp(NodeView.DEFAULT);
        }

        @Override
        protected Stamp stampAtKeySuccessor(int i) {
            return StampFactory.forConstant(keyAt(i));
        }

        /// Emits the range strategy through the normal backend with shared successor labels.
        @Override
        public void generate(NodeLIRBuilderTool tool) {
            NodeLIRBuilder builder = (NodeLIRBuilder) tool;
            LabelRef[] successorLabels = new LabelRef[blockSuccessorCount()];
            for (int i = 0; i < successorLabels.length; i++) {
                successorLabels[i] = builder.getLIRBlock(blockSuccessor(i));
            }
            LabelRef[] keyTargets = new LabelRef[keyCount()];
            for (int i = 0; i < keyTargets.length; i++) {
                keyTargets[i] = successorLabels[keySuccessorIndex(i)];
            }
            SwitchStrategy strategy = new SwitchStrategy.RangesStrategy(Arrays.copyOf(getKeyProbabilities(), keyCount()), keys);
            builder.getLIRGeneratorTool().emitStrategySwitch(strategy, builder.getLIRGeneratorTool().asAllocatable(builder.operand(value())), keyTargets,
                            successorLabels[defaultSuccessorIndex()]);
        }
    }

    /// Replaces the Java switch before final scheduling so that shared targets reach range dispatch.
    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        Assert.assertEquals(1, graph.getNodes().filter(IntegerSwitchNode.class).count());
        IntegerSwitchNode original = graph.getNodes().filter(IntegerSwitchNode.class).first();
        Assert.assertEquals(4, original.keyCount());
        RangeSwitchNode replacement = graph.add(new RangeSwitchNode(original));
        for (int i = 0; i < original.blockSuccessorCount(); i++) {
            AbstractBeginNode successor = original.blockSuccessor(i);
            original.setBlockSuccessor(i, null);
            replacement.setBlockSuccessor(i, successor);
        }
        original.replaceAtPredecessor(replacement);
        original.safeDelete();
    }

    /// Provides single keys followed by an adjacent range with a shared target.
    public static int sharedTargetSwitch(int value) {
        return switch (value) {
            case Integer.MIN_VALUE, -3 -> 0;
            case -2, -1 -> 1;
            default -> -1;
        };
    }

    /// Compiles the Java switch with shared range dispatch and executes keys and default cases.
    @Test
    public void compiledRangeAfterSingletons() {
        for (int value : new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -100, -4, -3, -2, -1, 0, Integer.MAX_VALUE}) {
            test("sharedTargetSwitch", value);
        }
    }

    /// Checks dispatch at every key and its neighbors, as well as at the integer limits.
    private static void assertRangeDispatch(int[] keys, int[] targets) {
        JavaConstant[] constants = new JavaConstant[keys.length];
        double[] probabilities = new double[keys.length];
        Arrays.fill(probabilities, 0.8 / keys.length);
        for (int i = 0; i < keys.length; i++) {
            constants[i] = JavaConstant.forInt(keys[i]);
        }
        SwitchStrategy strategy = new SwitchStrategy.RangesStrategy(probabilities, constants);
        assertDispatch(strategy, keys, targets, Integer.MIN_VALUE);
        assertDispatch(strategy, keys, targets, Integer.MAX_VALUE);
        for (int key : keys) {
            assertDispatch(strategy, keys, targets, key);
            if (key != Integer.MIN_VALUE) {
                assertDispatch(strategy, keys, targets, key - 1);
            }
            if (key != Integer.MAX_VALUE) {
                assertDispatch(strategy, keys, targets, key + 1);
            }
        }
    }

    /// Interprets comparisons directly so that keys can share targets independently of LIR labels.
    private static void assertDispatch(SwitchStrategy strategy, int[] keys, int[] targets, int value) {
        int keyIndex = Arrays.binarySearch(keys, value);
        int expected = keyIndex < 0 ? -1 : targets[keyIndex];
        // -2 means no jump was taken; -1 denotes the default target.
        int[] actual = {-2};
        strategy.run(new SwitchStrategy.BaseSwitchClosure(null, null, null, null) {
            @Override
            public void conditionalJump(int index, Condition condition, boolean defaultTarget) {
                if (actual[0] == -2 && (condition == null || condition.check(value, keys[index]))) {
                    actual[0] = defaultTarget ? -1 : targets[index];
                }
            }

            @Override
            public void conditionalJumpOrDefault(int index, Condition condition, boolean canFallThrough) {
                if (actual[0] == -2) {
                    actual[0] = condition.check(value, keys[index]) ? targets[index] : -1;
                }
            }

            @Override
            protected void conditionalJump(int index, Condition condition, Label target) {
                throw new AssertionError("RangesStrategy does not use internal labels");
            }

            @Override
            public boolean isSameTarget(int index1, int index2) {
                return targets[index1] == targets[index2];
            }
        });
        Assert.assertEquals("dispatch for " + value + " with keys " + Arrays.toString(keys), expected, actual[0]);
    }

    /// Covers a final shared range after single keys whose failed equality checks leave a gap.
    @Test
    public void finalRangeAfterSingletons() {
        assertRangeDispatch(new int[]{Integer.MIN_VALUE, -3, -2, -1}, new int[]{0, 0, 1, 1});
    }

    /// Covers the same missing lower bound when the shared range is followed by another key.
    @Test
    public void intermediateRangeAfterSingletons() {
        assertRangeDispatch(new int[]{Integer.MIN_VALUE, -3, -2, -1, 5}, new int[]{0, 0, 1, 1, 2});
    }

    /// Covers shared ranges at the start, across gaps, and next to other shared ranges.
    @Test
    public void rangeControls() {
        assertRangeDispatch(new int[]{-3, -2, -1}, new int[]{0, 0, 0});
        assertRangeDispatch(new int[]{-3, -2, -1, 0}, new int[]{0, 0, 1, 1});
        assertRangeDispatch(new int[]{-3, -2, 1, 2}, new int[]{0, 0, 1, 1});
        assertRangeDispatch(new int[]{-3, -2, 1}, new int[]{0, 0, 1});
        assertRangeDispatch(new int[]{-3, -2, -1}, new int[]{0, 1, 2});
        assertRangeDispatch(new int[]{Integer.MAX_VALUE - 1, Integer.MAX_VALUE}, new int[]{0, 0});
    }
}
