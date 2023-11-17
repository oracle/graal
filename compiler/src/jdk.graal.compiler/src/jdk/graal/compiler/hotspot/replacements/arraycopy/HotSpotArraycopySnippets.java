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
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.FREQUENT_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.LIKELY_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;

import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.hotspot.word.KlassPointer;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.SnippetAnchorNode;
import jdk.graal.compiler.replacements.arraycopy.ArrayCopyCallNode;
import jdk.graal.compiler.replacements.arraycopy.ArrayCopySnippets;
import jdk.graal.compiler.word.Word;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.WordFactory;

import jdk.vm.ci.meta.JavaKind;

public class HotSpotArraycopySnippets extends ArrayCopySnippets {

    @Override
    public boolean hubsEqual(Object nonNullSrc, Object nonNullDest) {
        KlassPointer srcHub = HotSpotReplacementsUtil.loadHub(nonNullSrc);
        KlassPointer destHub = HotSpotReplacementsUtil.loadHub(nonNullDest);
        return srcHub.equal(destHub);
    }

    Word getSuperCheckOffset(KlassPointer destElemKlass) {
        return WordFactory.signed(destElemKlass.readInt(HotSpotReplacementsUtil.superCheckOffsetOffset(INJECTED_VMCONFIG), HotSpotReplacementsUtil.KLASS_SUPER_CHECK_OFFSET_LOCATION));
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
    protected void doGenericArraycopySnippet(Object src, int srcPos, Object dest, int destPos, int length, JavaKind elementKind, LocationIdentity arrayLocation, Counters counters) {
        counters.genericArraycopyDifferentTypeCounter.inc();
        counters.genericArraycopyDifferentTypeCopiedCounter.add(length);
        int copiedElements = GenericArrayCopyCallNode.genericArraycopy(src, srcPos, dest, destPos, length);
        if (probability(SLOW_PATH_PROBABILITY, copiedElements != 0)) {
            /*
             * the stub doesn't throw the ArrayStoreException, but returns the number of copied
             * elements (xor'd with -1).
             */
            copiedElements ^= -1;
            System.arraycopy(src, srcPos + copiedElements, dest, destPos + copiedElements, length - copiedElements);
        }
    }
}
