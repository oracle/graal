/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.hotspot.replacements.arraycopy;

import static jdk.graal.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.FREQUENT_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.LIKELY_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;
import static jdk.graal.compiler.replacements.SnippetTemplate.AbstractTemplates.findMethod;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.hotspot.nodes.HotSpotDirectCallTargetNode;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.hotspot.word.KlassPointer;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.SnippetAnchorNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.replacements.arraycopy.ArrayCopyCallNode;
import jdk.graal.compiler.replacements.arraycopy.ArrayCopySnippets;
import jdk.graal.compiler.replacements.nodes.BasicArrayCopyNode;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.meta.JavaKind;

public class HotSpotArraycopySnippets extends ArrayCopySnippets {

    @Override
    public boolean hubsEqual(Object nonNullSrc, Object nonNullDest) {
        KlassPointer srcHub = HotSpotReplacementsUtil.loadHub(nonNullSrc);
        KlassPointer destHub = HotSpotReplacementsUtil.loadHub(nonNullDest);
        return srcHub.equal(destHub);
    }

    Word getSuperCheckOffset(KlassPointer destElemKlass) {
        return Word.signed(destElemKlass.readInt(HotSpotReplacementsUtil.superCheckOffsetOffset(INJECTED_VMCONFIG), HotSpotReplacementsUtil.KLASS_SUPER_CHECK_OFFSET_LOCATION));
    }

    @Override
    public boolean layoutHelpersEqual(Object nonNullSrc, Object nonNullDest) {
        KlassPointer srcHub = HotSpotReplacementsUtil.loadHub(nonNullSrc);
        KlassPointer destHub = HotSpotReplacementsUtil.loadHub(nonNullDest);
        return HotSpotReplacementsUtil.readLayoutHelper(srcHub) == HotSpotReplacementsUtil.readLayoutHelper(destHub);
    }

    KlassPointer getDestElemClass(KlassPointer destKlass) {
        return destKlass.readKlassPointer(HotSpotReplacementsUtil.arrayClassElementOffset(INJECTED_VMCONFIG),
                        HotSpotReplacementsUtil.OBJ_ARRAY_KLASS_ELEMENT_KLASS_LOCATION);
    }

    @Override
    protected int heapWordSize() {
        return HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG);
    }

    @Override
    @SuppressWarnings("unused")
    protected void doCheckcastArraycopySnippet(Object src, int srcPos, Object dest, int destPos, int length, JavaKind elementKind, LocationIdentity arrayLocation, Counters counters) {
        if (probability(FREQUENT_PROBABILITY, length > 0)) {
            Object nonNullSrc = PiNode.piCastNonNull(src, SnippetAnchorNode.anchor());
            Object nonNullDest = PiNode.piCastNonNull(dest, SnippetAnchorNode.anchor());
            KlassPointer srcKlass = HotSpotReplacementsUtil.loadHub(nonNullSrc);
            KlassPointer destKlass = HotSpotReplacementsUtil.loadHub(nonNullDest);
            if (probability(LIKELY_PROBABILITY, srcKlass.equal(destKlass)) || probability(LIKELY_PROBABILITY, nonNullDest.getClass() == Object[].class)) {
                // no storecheck required.
                counters.objectCheckcastSameTypeCounter.inc();
                counters.objectCheckcastSameTypeCopiedCounter.add(length);
                ArrayCopyCallNode.arraycopyObjectKillsAny(nonNullSrc, srcPos, nonNullDest, destPos, length, heapWordSize());
            } else {
                KlassPointer destElemKlass = getDestElemClass(destKlass);
                Word superCheckOffset = getSuperCheckOffset(destElemKlass);

                counters.objectCheckcastDifferentTypeCounter.inc();
                counters.objectCheckcastDifferentTypeCopiedCounter.add(length);

                int copiedElements = CheckcastArrayCopyCallNode.checkcastArraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length, superCheckOffset, destElemKlass, false);
                if (probability(SLOW_PATH_PROBABILITY, copiedElements != 0)) {
                    /*
                     * the stub doesn't throw the ArrayStoreException, but returns the number of
                     * copied elements (xor'd with -1).
                     */
                    copiedElements ^= -1;
                    System.arraycopy(nonNullSrc, srcPos + copiedElements, nonNullDest, destPos + copiedElements, length - copiedElements);
                }
            }
        }
    }

    @Override
    protected void doGenericArraycopySnippet(Object src, int srcPos, Object dest, int destPos, int length, JavaKind elementKind, LocationIdentity arrayLocation, Counters counters,
                    boolean exceptionSeen) {
        counters.genericArraycopyDifferentTypeCounter.inc();
        counters.genericArraycopyDifferentTypeCopiedCounter.add(length);
        int copiedElements = GenericArrayCopyCallNode.genericArraycopy(src, srcPos, dest, destPos, length);
        if (probability(SLOW_PATH_PROBABILITY, copiedElements != 0)) {
            /*
             * the stub doesn't throw the ArrayStoreException, but returns the number of copied
             * elements (xor'd with -1).
             */
            copiedElements ^= -1;
            if (exceptionSeen) {
                HotSpotArrayCopyCallWithExceptionNode.arraycopyWithException(src, srcPos + copiedElements, dest, destPos + copiedElements, length - copiedElements, elementKind);
            } else {
                System.arraycopy(src, srcPos + copiedElements, dest, destPos + copiedElements, length - copiedElements);
            }
        }
    }

    @Override
    protected void doFailingArraycopySnippet(Object src, int srcPos, Object dest, int destPos, int length, JavaKind elementKind, Counters counters) {
        // Call System.arraycopy but have an exception edge for the call.
        HotSpotArrayCopyCallWithExceptionNode.arraycopyWithException(src, srcPos, dest, destPos, length, elementKind);
    }

    @NodeInfo(allowedUsageTypes = {InputType.Memory, InputType.Value}, cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
    public static final class HotSpotArrayCopyCallWithExceptionNode extends BasicArrayCopyNode implements Lowerable {
        public static final NodeClass<HotSpotArrayCopyCallWithExceptionNode> TYPE = NodeClass.create(HotSpotArrayCopyCallWithExceptionNode.class);

        public HotSpotArrayCopyCallWithExceptionNode(ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, JavaKind elementKind) {
            super(TYPE, src, srcPos, dest, destPos, length, elementKind);
        }

        @Override
        public void lower(LoweringTool tool) {
            // Based on SubstrateGenericArrayCopyCallNode.
            if (graph().getGuardsStage().areFrameStatesAtDeopts()) {
                StructuredGraph graph = graph();
                ValueNode[] args = new ValueNode[]{getSource(), getSourcePosition(), getDestination(), getDestinationPosition(), getLength()};
                var returnStamp = StampPair.create(StampFactory.forVoid(), StampFactory.forVoid());
                var target = findMethod(tool.getMetaAccess(), System.class, "arraycopy");
                var signature = target.getSignature().toParameterTypes(null);
                var callType = HotSpotCallingConventionType.JavaCall;
                var invokeKind = CallTargetNode.InvokeKind.Static;
                CallTargetNode ct = graph.add(new HotSpotDirectCallTargetNode(args, returnStamp, signature, target, callType, invokeKind));

                InvokeWithExceptionNode call = graph.add(new InvokeWithExceptionNode(ct, null, bci()));
                call.setStateAfter(stateAfter());
                call.setStateDuring(stateDuring());
                graph.replaceWithExceptionSplit(this, call);
            }
        }

        @NodeIntrinsic
        public static native int arraycopyWithException(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter JavaKind elementKind);
    }
}
