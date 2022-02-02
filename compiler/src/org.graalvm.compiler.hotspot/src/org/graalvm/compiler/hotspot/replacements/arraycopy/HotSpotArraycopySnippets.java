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

package org.graalvm.compiler.hotspot.replacements.arraycopy;

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.FREQUENT_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.LIKELY_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;

import org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import org.graalvm.compiler.hotspot.word.KlassPointer;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopyCallNode;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopySnippets;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import jdk.vm.ci.meta.JavaKind;

public class HotSpotArraycopySnippets extends ArrayCopySnippets {

    public Pointer loadHub(Object nonNullSrc) {
        return HotSpotReplacementsUtil.loadHub(nonNullSrc).asWord();
    }

    @Override
    public boolean hubsEqual(Object nonNullSrc, Object nonNullDest) {
        Pointer srcHub = loadHub(nonNullSrc);
        Pointer destHub = loadHub(nonNullDest);
        return srcHub == destHub;
    }

    public Word getSuperCheckOffset(Pointer destElemKlass) {
        return WordFactory.signed(destElemKlass.readInt(HotSpotReplacementsUtil.superCheckOffsetOffset(INJECTED_VMCONFIG), HotSpotReplacementsUtil.KLASS_SUPER_CHECK_OFFSET_LOCATION));
    }

    public int getReadLayoutHelper(Pointer srcHub) {
        return HotSpotReplacementsUtil.readLayoutHelper(KlassPointer.fromWord(srcHub));
    }

    @Override
    public boolean layoutHelpersEqual(Object nonNullSrc, Object nonNullDest) {
        Pointer srcHub = loadHub(nonNullSrc);
        Pointer destHub = loadHub(nonNullDest);
        return getReadLayoutHelper(srcHub) == getReadLayoutHelper(destHub);
    }

    public Pointer getDestElemClass(Pointer destKlassPointer) {
        KlassPointer destKlass = (KlassPointer.fromWord(destKlassPointer));
        return destKlass.readKlassPointer(HotSpotReplacementsUtil.arrayClassElementOffset(INJECTED_VMCONFIG),
                        HotSpotReplacementsUtil.OBJ_ARRAY_KLASS_ELEMENT_KLASS_LOCATION).asWord();
    }

    @Override
    protected int heapWordSize() {
        return HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG);
    }

    @Override
    @SuppressWarnings("unused")
    protected void doCheckcastArraycopySnippet(Object src, int srcPos, Object dest, int destPos, int length, JavaKind elementKind, LocationIdentity arrayLocation, Counters counters) {
        if (probability(FREQUENT_PROBABILITY, length > 0)) {
            Object nonNullSrc = PiNode.asNonNullObject(src);
            Object nonNullDest = PiNode.asNonNullObject(dest);
            Pointer srcKlass = loadHub(nonNullSrc);
            Pointer destKlass = loadHub(nonNullDest);
            if (probability(LIKELY_PROBABILITY, srcKlass == destKlass) || probability(LIKELY_PROBABILITY, nonNullDest.getClass() == Object[].class)) {
                // no storecheck required.
                counters.objectCheckcastSameTypeCounter.inc();
                counters.objectCheckcastSameTypeCopiedCounter.add(length);
                ArrayCopyCallNode.arraycopyObjectKillsAny(nonNullSrc, srcPos, nonNullDest, destPos, length, heapWordSize());
            } else {
                Pointer destElemKlass = getDestElemClass(destKlass);
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
