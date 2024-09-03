/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.memory.ExtendableMemoryAccess;

/**
 * Goal: minimize the number of {@link ZeroExtendNode}s and {@link SignExtendNode}s needed. This is
 * done by folding extends into memory operations and also by "overextending" extends to cover all
 * given use scenarios. In the case of overextension, an original extend may be replaced with an
 * "extend+narrow" combo. Note that within the backend code generation narrows are free (via
 * {@link CastValue}), so it is inconsequential to add more of them. Note that this optimization
 * will always result in the same or fewer number of extends along all paths.
 *
 * The idea case is a situation such as
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
 * However, even a case such as the below would be beneficial since the number of extends on any
 * path will be no more than the original graph.
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
    private static final int UNSET = -1;

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        // This phase can cause reads to be non-canonicalizable.
        return NotApplicable.unlessRunAfter(this, StageFlag.FINAL_CANONICALIZATION, graphState);
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

            if (!(def instanceof ExtendableMemoryAccess) && !hasRedundantExtends) {
                // return: nothing to optimize for this def
                continue;
            }

            /*
             * After this phase is complete, for a given def there will be at most one sign and zero
             * extend.
             */
            ValueNode newZeroExtend = null;
            ValueNode newSignExtend = null;

            // If def is an extendable access, try to fold some extends into def
            ValueNode extendInput = def;
            if (def instanceof ExtendableMemoryAccess) {
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

                    // replace original access with new extended memory access
                    graph.replaceFixedWithFixed(access.asFixedWithNextNode(), extendedDef);
                }
            }

            if (extendInput == def && !hasRedundantExtends) {
                // nothing to optimize
                continue;
            }

            // create needed new extends
            if (maxZeroExtend != UNSET && newZeroExtend == null) {
                newZeroExtend = graph.addOrUnique(new ZeroExtendNode(extendInput, inputBitsSize, maxZeroExtend));
            }
            if (maxSignExtend != UNSET && newSignExtend == null) {
                newSignExtend = graph.addOrUnique(new SignExtendNode(extendInput, inputBitsSize, maxSignExtend));
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
                    assert newZeroExtend != null;
                    replacementBits = maxZeroExtend;
                    replacement = newZeroExtend;
                } else {
                    assert newSignExtend != null;
                    replacementBits = maxSignExtend;
                    replacement = newSignExtend;
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
}
