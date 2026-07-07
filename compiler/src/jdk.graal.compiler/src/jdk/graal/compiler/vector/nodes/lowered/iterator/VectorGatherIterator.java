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
package jdk.graal.compiler.vector.nodes.lowered.iterator;

import java.util.ArrayList;

import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.op.VectorGatherNode;
import jdk.graal.compiler.vector.nodes.op.VectorTransformation;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ConstantReflectionProvider;

public class VectorGatherIterator extends VectorTransformationIterator {

    public static VectorGatherIterator createInitialIterator(VectorTransformation seq, AnchoringNode anchor, TargetDescription target) {
        return new VectorGatherIterator(createInitialIterators(seq, anchor, target));
    }

    public static VectorGatherIterator createPhiIterator(VectorTransformation seq, AbstractMergeNode merge, AnchoringNode anchor, TargetDescription target) {
        return new VectorGatherIterator(createPhiIterators(seq, merge, anchor, target));
    }

    protected VectorGatherIterator(ArrayList<VectorIterator> inputs) {
        super(inputs);
    }

    @Override
    public VectorIterator next(VectorNode vector, ValueNode stepLength) {
        return new VectorGatherIterator(nextInputs(vector, stepLength));
    }

    @Override
    public VectorNode getVector(VectorNode source, VectorIterationState state, FixedNode position, ConstantReflectionProvider constantReflection, int stepLength) {
        VectorGatherNode original = (VectorGatherNode) source;
        VectorGatherNode gather = (VectorGatherNode) super.getVector(source, state, position, constantReflection, stepLength);

        MemoryKill lastLocationAccess = state.getLastLocationAccess(((VectorGatherNode) source).getLocationIdentity());
        if (lastLocationAccess == null) {
            lastLocationAccess = ((VectorGatherNode) source).getLastLocationAccess();
        }
        gather.setLastLocationAccess(lastLocationAccess);

        GuardingNode newGuard = state.getCachedVectorGuard(original.getGuard(), position);
        if (original.getGuard() != null) {
            GraalError.guarantee(newGuard != null, "must have cached guard for gather %s with original guard %s, got: %s", original, original.getGuard(), newGuard);
        }
        gather.setGuard(newGuard);

        return gather;
    }
}
