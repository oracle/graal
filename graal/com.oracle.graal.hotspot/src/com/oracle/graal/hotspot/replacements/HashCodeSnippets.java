/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static com.oracle.graal.hotspot.meta.HotSpotForeignCallsProviderImpl.IDENTITY_HASHCODE;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.biasedLockMaskInPlace;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.identityHashCode;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.identityHashCodeShift;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.loadWordFromObject;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.markOffset;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.uninitializedIdentityHashCodeValue;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.unlockedMask;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.FAST_PATH_PROBABILITY;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.probability;

import com.oracle.graal.replacements.Snippet;
import com.oracle.graal.replacements.Snippets;
import com.oracle.graal.word.Word;

public class HashCodeSnippets implements Snippets {

    @Snippet
    public static int identityHashCodeSnippet(final Object thisObj) {
        if (probability(NOT_FREQUENT_PROBABILITY, thisObj == null)) {
            return 0;
        }
        return computeHashCode(thisObj);
    }

    static int computeHashCode(final Object x) {
        Word mark = loadWordFromObject(x, markOffset(INJECTED_VMCONFIG));

        // this code is independent from biased locking (although it does not look that way)
        final Word biasedLock = mark.and(biasedLockMaskInPlace(INJECTED_VMCONFIG));
        if (probability(FAST_PATH_PROBABILITY, biasedLock.equal(Word.unsigned(unlockedMask(INJECTED_VMCONFIG))))) {
            int hash = (int) mark.unsignedShiftRight(identityHashCodeShift(INJECTED_VMCONFIG)).rawValue();
            if (probability(FAST_PATH_PROBABILITY, hash != uninitializedIdentityHashCodeValue(INJECTED_VMCONFIG))) {
                return hash;
            }
        }
        return identityHashCode(IDENTITY_HASHCODE, x);
    }
}
