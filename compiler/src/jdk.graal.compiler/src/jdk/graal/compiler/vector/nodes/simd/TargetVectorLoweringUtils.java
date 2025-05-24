/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.simd;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.VirtualState;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.architecture.VectorLoweringProvider;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;

/**
 * Common utility methods for use by the target-specific vector lowering phases.
 */
public class TargetVectorLoweringUtils {

    /**
     * For SequenceVectorNodes used by vector frame states we sometimes optimistically generate cuts
     * on constants or broadcasts that are too long for the architecture. Cut these down to a legal
     * size.
     *
     * @return a legal version of the cut if it cuts a constant or broadcast, the unmodified cut
     *         otherwise; does not replace usages of the original
     */
    public static ValueNode legalizeSimdCutLength(SimdCutNode cut, LowTierContext context) {
        VectorArchitecture arch = ((VectorLoweringProvider) context.getLowerer()).getVectorArchitecture();
        SimdStamp simdStamp = (SimdStamp) cut.getValue().stamp(NodeView.DEFAULT);
        Stamp elementStamp = simdStamp.getComponent(0);
        if (simdStamp.getVectorLength() > arch.getMaxVectorLength(elementStamp)) {
            if (cut.getValue().isConstant() && cut.getValue().asConstant() instanceof SimdConstant) {
                SimdConstant simdConstant = (SimdConstant) cut.getValue().asConstant();
                return cut.canonicalConstant(simdConstant, context.getMetaAccess(), cut.graph());
            } else if (cut.getValue() instanceof SimdBroadcastNode) {
                SimdBroadcastNode broadcast = (SimdBroadcastNode) cut.getValue();
                return cut.graph().addOrUnique(new SimdBroadcastNode(broadcast.getValue(), cut.getLength()));
            } else {
                GraalError.shouldNotReachHere(cut + ": cut of illegal value " + cut.getValue() + ", stamp " + cut.getValue().stamp(NodeView.DEFAULT)); // ExcludeFromJacocoGeneratedReport
            }
        }
        return cut;
    }

    /**
     * If the SIMD constant is all ones or all zeros, replace it with a reinterpret operation on a
     * "canonical" all-ones or all-zeros constant node. This means that such SIMD constants of
     * different types will all share a single underlying constant value and a single underlying
     * register.
     *
     * Such a shared constant register may have a very long live range. However, all-ones and
     * all-zeros values are cheap to rematerialize, so we can split such a live range cheaply when
     * needed.
     */
    public static void uniqueSimdConstant(ConstantNode constantNode, SimdStamp stamp, LowTierContext context, VectorArchitecture vectorArch) {
        if (SimdStamp.isOpmask(stamp)) {
            return;
        }

        byte replacementValue;
        IntegerStamp replacementByteStamp = null;
        if (constantNode.asConstant().isDefaultForKind()) {
            replacementValue = 0;
            replacementByteStamp = IntegerStamp.create(8, replacementValue, replacementValue);
        } else if (SimdConstant.isAllOnes(constantNode.asConstant())) {
            replacementValue = -1;
            replacementByteStamp = IntegerStamp.create(8, replacementValue, replacementValue);
        } else {
            return;
        }

        Stamp elementStamp = stamp.getComponent(0);
        int totalBytes = stamp.getVectorLength() * vectorArch.getVectorStride(elementStamp);
        if (totalBytes > vectorArch.getMaxVectorLength()) {
            // Overly long "optimistic" constant generated for a vector frame state (see also
            // legalizeSimdCutLength).
            totalBytes = vectorArch.getMaxVectorLength();
        }

        Constant canonicalSimdConstant = SimdConstant.broadcast(JavaConstant.forByte(replacementValue), vectorArch.getMaxVectorLength());
        Stamp simdStamp = SimdStamp.broadcast(replacementByteStamp, vectorArch.getMaxVectorLength());
        ConstantNode canonicalSimdConstantNode = ConstantNode.forConstant(simdStamp, canonicalSimdConstant, context.getMetaAccess(), constantNode.graph());
        SimdStamp reinterpretStamp = SimdStamp.broadcast(elementStamp, totalBytes / vectorArch.getVectorStride(elementStamp));
        ValueNode reinterpret = constantNode.graph().addOrUnique(SimdNarrowingReinterpretNode.create(canonicalSimdConstantNode, reinterpretStamp));

        if (reinterpret != constantNode) {
            /* Don't replace usages in states, those want constants embedded directly. */
            constantNode.replaceAtUsages(reinterpret, usage -> usage != reinterpret && !(usage instanceof VirtualState));
        }
    }
}
