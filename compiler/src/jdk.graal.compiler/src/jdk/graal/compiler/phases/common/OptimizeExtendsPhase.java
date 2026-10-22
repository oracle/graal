/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.phases.common;

import java.util.List;
import java.util.Optional;

import jdk.graal.compiler.lir.CastValue;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import jdk.graal.compiler.core.common.memory.MemoryExtendKind;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeWorkList.SingletonNodeWorkList;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ArithmeticOperation;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.IntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.RoundFloatToIntegerNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.memory.ExtendableMemoryAccess;
import jdk.graal.compiler.nodes.ChainedPhiAdapterNode;
import jdk.graal.compiler.nodes.ChainedPhiValueSplitNode;
import jdk.graal.compiler.nodes.debug.SideEffectNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.spi.ValueProxy;
import jdk.graal.compiler.replacements.nodes.BitCountNode;
import jdk.graal.compiler.replacements.nodes.CountLeadingZerosNode;
import jdk.graal.compiler.replacements.nodes.CountTrailingZerosNode;
import jdk.graal.compiler.replacements.nodes.ReverseBitsNode;
import jdk.graal.compiler.replacements.nodes.ReverseBytesNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerExactArithmeticSplitNode;

/**
 * Goal: minimize the number of {@link ZeroExtendNode}s and {@link SignExtendNode}s needed. This is
 * done by folding extends into memory operations and also by "overextending" extends to cover all
 * given use scenarios. In the case of overextension, an original extend may be replaced with an
 * "extend+narrow" combo. Note that within the backend code generation narrows are free (via
 * {@link CastValue}), so it is inconsequential to add more of them.
 *
 * Each application of this phase produces the same or fewer scalar sign- and zero-extension nodes
 * than its input graph. Individual execution paths can gain extensions when combining moves them
 * onto paths that do not need their values. Partial redundancy scheduling can duplicate extension
 * nodes to avoid work on such paths.
 *
 * {@link Mode#COMBINE} combines extensions of a common input. Combining can hoist extensions onto
 * paths where their values are unused. This mode runs before the partial redundancy scheduling
 * phase, which can duplicate the resulting extensions onto paths where their values are needed.
 * {@link Mode#FOLD_READS} folds extensions into fixed reads after final canonicalization and computes
 * which zero-extensions require explicit instructions. The first example below shows read folding
 * alone. The other examples show the combined effect of both modes.
 *
 * The ideal case is a situation such as
 *
 * <pre>
 * Original:
 *         Load
 *           |
 *      [Zero Extend]
 *           |
 *          [op]
 *
 * New:
 *
 *     Load w/ Zero Extend
 *            |
 *           [op]
 * </pre>
 *
 * Or using narrows to cover all cases:
 *
 * <pre>
 * Original:
 *
 *                    Load
 *                   /    \
 *                  /      \
 *   [Zero Extend to 64]  [Zero Extend to 32]
 *           |                       |
 *          [op]                    [op]
 *
 * New:
 *
 *    Load w/ Zero Extend to 64
 *        /    \
 *       |     [Narrow to 32]
 *       |          |
 *      [op]       [op]
 * </pre>
 *
 * The following example combines both kinds of extension and folds zero-extension into the read.
 *
 * <pre>
 * Original:
 *
 *                                       Load Byte
 *                                     | |      |  \
 *                                     | |      |    \
 *                                     / |      |      \
 *                                  /    |      |        \
 *                               /       |      |          \
 *                            /          |      |            \
 *                         /             |      |              \
 *                      /                |      |                \
 *                   /                   |      |                  \
 *  [Zero extend to 32]  [Zero Extend to 64]   [Sign Extend to 32] [Sign Extend to 64]
 *         |                    |                    |                 |
 *        [op]                 [op]                 [op]              [op]
 *
 * New:
 *
 *                            Load Byte w/ Zero Extend to 64
 *                            / |                    |
 *                         /    |                    |
 *                      /       |                    |
 *                   /          |                    |
 *   [Narrow to 32]             |              [Narrow to 8]
 *         |                    |                     |
 *         |                    |            [Sign Extend to 64]
 *         |                    |             /             \
 *         |                    |       [Narrow to 32]       \
 *         |                    |            |                \
 *        [op]                 [op]         [op]             [op]
 * </pre>
 */
public class OptimizeExtendsPhase extends BasePhase<LowTierContext> {
    /**
     * The optimization to perform.
     */
    public enum Mode {
        /**
         * Extension combining before partial redundancy scheduling.
         */
        COMBINE,
        /**
         * Folds extensions into fixed reads after final canonicalization. Also computes where
         * explicit zero-extension is required based on whether inputs have zero upper register bits.
         */
        FOLD_READS
    }

    private static final int UNSET = -1;
    private final Mode mode;

    public OptimizeExtendsPhase(Mode mode) {
        this.mode = mode;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        // This phase can cause reads to be non-canonicalizable.
        return NotApplicable.unlessRunAfter(this, mode == Mode.COMBINE ? StageFlag.LOW_TIER_LOWERING : StageFlag.FINAL_CANONICALIZATION, graphState);
    }

    @Override
    protected void run(StructuredGraph graph, LowTierContext context) {
        if (!context.getLowerer().narrowsUseCastValue()) {
            // Nothing to be done
            return;
        }

        int origNumExtends = 0;
        EconomicSet<ValueNode> defsWithExtends = EconomicSet.create(Equivalence.DEFAULT);

        /* Step 1: Find defs with extend usages. */
        for (Node node : graph.getNodes().filter(OptimizeExtendsPhase::isExtendNode)) {
            IntegerConvertNode<?> extend = (IntegerConvertNode<?>) node;
            origNumExtends++;
            assert extend.getInputBits() < extend.getResultBits() : Assertions.errorMessage(extend);
            if (extend.getValue() instanceof ValuePhiNode phiNode && phiNode.merge() instanceof LoopBeginNode loopBeginNode && loopBeginNode.mayEmitThreadedCode()) {
                // Skip extend on loop phis when the loop is marked as threaded
                continue;
            }
            // record use of this node
            defsWithExtends.add(extend.getValue());
        }

        /*
         * If a def is an extend, then it is possible it will be rewritten when processing that
         * extend as a use. We resolve this via tracking when they change.
         */
        EconomicMap<ValueNode, ValueNode> extendReplacements = EconomicMap.create(Equivalence.DEFAULT);
        /* Step 2: try to optimize extends */
        for (ValueNode origDef : defsWithExtends) {

            // Size of all inputs
            int inputBitsSize = UNSET;

            /*
             * Track the maximum extend resultBit size seen.
             */
            int maxZeroExtend = UNSET;
            int maxSignExtend = UNSET;

            // Determine current def value
            ValueNode def = origDef;
            if (def instanceof IntegerConvertNode<?> && extendReplacements.containsKey(def)) {
                def = extendReplacements.get(def);
            }

            if (mode == Mode.FOLD_READS && !(def instanceof ExtendableMemoryAccess)) {
                continue;
            }

            // Determine maximum extend size for each input size

            // Get all extends
            List<Node> uses = def.usages().filter(OptimizeExtendsPhase::isExtendNode).snapshot();
            // Tracks whether a def has multiple extends with the same input size
            boolean hasRedundantExtends = false;
            for (Node n : uses) {
                IntegerConvertNode<?> use = (IntegerConvertNode<?>) n;
                int inputBits = use.getInputBits();
                int resultBits = use.getResultBits();
                if (inputBitsSize == UNSET) {
                    inputBitsSize = inputBits;
                } else {
                    GraalError.guarantee(inputBitsSize == inputBits, "Unexpected input bits size: %s. Expected size: %s", inputBits, inputBitsSize);
                }
                if (use instanceof ZeroExtendNode) {
                    hasRedundantExtends |= maxZeroExtend != UNSET;
                    maxZeroExtend = Integer.max(maxZeroExtend, resultBits);
                } else {
                    assert use instanceof SignExtendNode : Assertions.errorMessageContext("use", use);
                    hasRedundantExtends |= maxSignExtend != UNSET;
                    maxSignExtend = Integer.max(maxSignExtend, resultBits);
                }
            }

            ValueNode newZeroExtend = null;
            ValueNode newSignExtend = null;
            ValueNode extendInput = def;
            if (mode == Mode.COMBINE) {
                if (!hasRedundantExtends) {
                    continue;
                }

                /*
                 * After combining is complete, for a given def there will be at most one sign and zero
                 * extend.
                 */
                // create needed new extends
                if (maxZeroExtend != UNSET) {
                    newZeroExtend = graph.addOrUnique(new ZeroExtendNode(extendInput, inputBitsSize, maxZeroExtend));
                }
                if (maxSignExtend != UNSET) {
                    newSignExtend = graph.addOrUnique(new SignExtendNode(extendInput, inputBitsSize, maxSignExtend));
                }
            } else {
                // If def is an extendable access, try to fold some extends into def
                ExtendableMemoryAccess access = (ExtendableMemoryAccess) def;
                FixedWithNextNode extendedDef = null;
                MemoryExtendKind extendKind = maxZeroExtend == UNSET ? MemoryExtendKind.DEFAULT : MemoryExtendKind.getZeroExtendKind(maxZeroExtend);
                if (extendKind.isExtended() && context.getLowerer().supportsFoldingExtendIntoAccess(access, extendKind)) {
                    // creating the new zero extended mem access
                    extendedDef = graph.add(access.copyWithExtendKind(extendKind));
                    newZeroExtend = extendedDef;
                } else {
                    extendKind = maxSignExtend == UNSET ? MemoryExtendKind.DEFAULT : MemoryExtendKind.getSignExtendKind(maxSignExtend);
                    if (extendKind.isExtended() && context.getLowerer().supportsFoldingExtendIntoAccess(access, extendKind)) {
                        // creating the new sign extended mem access
                        extendedDef = graph.add(access.copyWithExtendKind(extendKind));
                        newSignExtend = extendedDef;
                    }
                }

                if (extendedDef != null) {
                    // pass narrow to original value uses to meet stamp expectations
                    extendInput = graph.addOrUnique(new NarrowNode(extendedDef, inputBitsSize));
                    def.replaceAtUsages(extendInput, InputType.Value);

                    /*
                     * The access result stamp has changed. Value usages were updated above. There may
                     * be guard usages that use the read as a null check. They do not depend on the
                     * result stamp. Replace without checking stamps.
                     */
                    graph.replaceFixedWithFixedWithoutCheckingInvariants(access.asFixedWithNextNode(), extendedDef);
                }

                if (extendInput == def) {
                    // nothing to optimize
                    continue;
                }
            }

            /*
             * Process original nodes according to the result bit size of the original and
             * replacement.
             *
             * 1. original == replacement: directly replace
             *
             * 2. original < replacement: append narrow to replacement
             */
            for (Node n : uses) {
                IntegerConvertNode<?> use = (IntegerConvertNode<?>) n;
                // find replacement
                ValueNode replacement;
                final int replacementBits;
                if (use instanceof ZeroExtendNode) {
                    replacementBits = maxZeroExtend;
                    replacement = newZeroExtend;
                } else {
                    replacementBits = maxSignExtend;
                    replacement = newSignExtend;
                }
                if (replacement == null) {
                    GraalError.guarantee(mode == Mode.FOLD_READS, "In COMBINE mode, every extension being processed must have a replacement");
                    continue;
                }

                // append a narrow to replacement if necessary
                int resultBits = use.getResultBits();
                if (resultBits != replacementBits) {
                    assert replacementBits > resultBits : replacementBits + " " + resultBits;
                    replacement = graph.addOrUnique(new NarrowNode(replacement, replacementBits, resultBits));
                }

                // replace original extend node
                if (use != replacement) {
                    use.replaceAtUsagesAndDelete(replacement);
                    // record replacement if the extend is also a def
                    if (defsWithExtends.contains(use)) {
                        extendReplacements.put(use, replacement);
                    }
                }
            }

            // remove extendInput if it is not used
            if (extendInput != def && extendInput.hasNoUsages()) {
                extendInput.safeDelete();
            }
        }

        if (mode == Mode.FOLD_READS) {
            ZeroExtendAnalysis.computeRequiresExplicitZeroExtend(graph);
        }

        if (Assertions.assertionsEnabled()) {
            /*
             * After this optimization is performed the number of extends in the graph should be
             * less than or equal to the number of extends in the original graph.
             */
            int numExtends = graph.getNodes().filter(OptimizeExtendsPhase::isExtendNode).count();
            assert numExtends <= origNumExtends : numExtends + " " + origNumExtends;
        }
    }

    private static boolean isExtendNode(Node node) {
        return (node instanceof ZeroExtendNode || node instanceof SignExtendNode) && ((IntegerConvertNode<?>) node).stamp(NodeView.DEFAULT) instanceof IntegerStamp;
    }

    /**
     * Sets {@code requiresExplicitZeroExtend} on 32-to-64 {@link ZeroExtendNode}s according to whether
     * their inputs may have nonzero upper register bits. Follows forwarding nodes and propagates
     * information about nonzero upper register bits through phis, including cycles. The worklist
     * visits each reached node once.
     *
     * Requires a graph after final canonicalization and read folding, with no further transformations
     * that could invalidate the guarantee of zero upper register bits before LIR generation.
     */
    public static final class ZeroExtendAnalysis {
        private final StructuredGraph graph;
        private SingletonNodeWorkList valuesRequiringZeroExtend;

        private ZeroExtendAnalysis(StructuredGraph graph) {
            this.graph = graph;
        }

        /**
         * Sets {@code requiresExplicitZeroExtend} to {@code false} for 32-to-64 zero extensions whose
         * inputs are guaranteed to have zero upper register bits. Other widths use the conservative
         * default, {@code true}. Creates the analysis only if a 32-to-64 zero extension exists.
         */
        public static void computeRequiresExplicitZeroExtend(StructuredGraph graph) {
            ZeroExtendAnalysis analysis = null;
            for (ZeroExtendNode extend : graph.getNodes().filter(ZeroExtendNode.class)) {
                if (extend.getInputBits() == 32 && extend.getResultBits() == 64) {
                    if (analysis == null) {
                        analysis = new ZeroExtendAnalysis(graph);
                    }
                    extend.setRequiresExplicitZeroExtend(analysis.requiresExplicitZeroExtend(extend.getValue()));
                }
            }
        }

        /**
         * Follows forwarding nodes and classifies phis by propagation. Other values require explicit
         * zero-extension unless their emission guarantees zero upper register bits.
         */
        private boolean requiresExplicitZeroExtend(ValueNode input) {
            ValueNode value = skipForwardingNodes(input);
            if (value instanceof ValuePhiNode phi) {
                if (valuesRequiringZeroExtend == null) {
                    findPhisRequiringZeroExtend();
                }
                return valuesRequiringZeroExtend.contains(phi);
            }
            return !hasZeroUpperBits(value);
        }

        /**
         * Skips nodes that forward their input value. Stops at phis, which are classified by
         * {@link #findPhisRequiringZeroExtend()}.
         */
        private ValueNode skipForwardingNodes(ValueNode input) {
            ValueNode value = input;
            int remaining = graph.getNodeCount();
            while (remaining-- > 0) {
                ValueNode proxiedValue = getProxiedValue(value);
                if (proxiedValue == null) {
                    break;
                }
                value = proxiedValue;
            }
            GraalError.guarantee(remaining >= 0, "Cycle in forwarding nodes");
            return value;
        }

        /** Recognizes values whose emission guarantees zero upper register bits. */
        private static boolean hasZeroUpperBits(ValueNode value) {
            if (!(value.stamp(NodeView.DEFAULT) instanceof IntegerStamp stamp && stamp.getBits() == 32)) {
                return false;
            }
            if (value instanceof NarrowNode) {
                /* CastValue narrowing can preserve upper register bits. */
                return false;
            }
            if (value instanceof ArithmeticOperation) {
                /* Computing an int arithmetic result clears the upper register bits. */
                return true;
            }
            if (value.isJavaConstant()) {
                /* Materializing an int constant clears the upper register bits. */
                return true;
            }
            if (value instanceof ReadNode) {
                /*
                 * The result stamp includes any folded extension, so this read produces 32 bits.
                 * Sign-extension from a byte or short affects only those 32 bits; the upper register
                 * bits are zero.
                 */
                return true;
            }
            if (value instanceof IntegerDivRemNode || value instanceof IntegerExactArithmeticSplitNode || value instanceof RoundFloatToIntegerNode) {
                /* These additional arithmetic nodes also clear the upper register bits. */
                return true;
            }
            if (value instanceof BitCountNode || value instanceof CountLeadingZerosNode || value instanceof CountTrailingZerosNode) {
                /* The entire register contains the count, which is at most 64. */
                return true;
            }
            if (value instanceof ReverseBitsNode || value instanceof ReverseBytesNode) {
                /* Byte and bit reversal of an int clear the upper register bits. */
                return true;
            }
            return false;
        }

        /**
         * Finds 32-bit phis that may have nonzero upper register bits. Starts with values requiring
         * explicit zero-extension and propagates information about nonzero upper register bits along
         * graph usages through forwarding nodes and 32-bit phis. Each reached node is visited once.
         */
        private void findPhisRequiringZeroExtend() {
            SingletonNodeWorkList worklist = new SingletonNodeWorkList(graph);
            for (ValueNode value : graph.getNodes().filter(ValueNode.class)) {
                if (value.stamp(NodeView.DEFAULT) instanceof IntegerStamp stamp && stamp.getBits() == 32 &&
                                !(value instanceof ValuePhiNode) && getProxiedValue(value) == null && !hasZeroUpperBits(value)) {
                    worklist.add(value);
                }
            }
            for (Node value : worklist) {
                for (Node usage : value.usages()) {
                    if (usage instanceof ValuePhiNode phi && phi.stamp(NodeView.DEFAULT) instanceof IntegerStamp stamp && stamp.getBits() == 32) {
                        worklist.add(phi);
                    } else if (getProxiedValue(usage) == value) {
                        worklist.add(usage);
                    }
                }
            }
            valuesRequiringZeroExtend = worklist;
        }

        /** Returns the value forwarded by a proxying node, or {@code null} for other nodes. */
        private static ValueNode getProxiedValue(Node node) {
            if (node instanceof ValueProxy proxy) {
                return proxy.getOriginalNode();
            } else if (node instanceof SideEffectNode sideEffect) {
                return sideEffect.getValue();
            } else if (node instanceof ChainedPhiValueSplitNode split) {
                return split.getValue();
            } else if (node instanceof ChainedPhiAdapterNode adapter) {
                return adapter.getValue();

            }
            return null;
        }
    }
}
