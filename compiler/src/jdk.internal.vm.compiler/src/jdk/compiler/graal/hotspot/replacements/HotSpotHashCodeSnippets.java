/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.hotspot.replacements;

import static jdk.compiler.graal.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static jdk.compiler.graal.hotspot.meta.HotSpotForeignCallsProviderImpl.IDENTITY_HASHCODE;
import static jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil.identityHashCode;
import static jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil.identityHashCodeShift;
import static jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil.loadWordFromObject;
import static jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil.lockMaskInPlace;
import static jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil.markOffset;
import static jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil.uninitializedIdentityHashCodeValue;
import static jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil.unlockedMask;
import static jdk.compiler.graal.nodes.extended.BranchProbabilityNode.FAST_PATH_PROBABILITY;
import static jdk.compiler.graal.nodes.extended.BranchProbabilityNode.probability;

import jdk.compiler.graal.lir.SyncPort;
import jdk.compiler.graal.replacements.IdentityHashCodeSnippets;
import jdk.compiler.graal.word.Word;
import org.graalvm.word.WordFactory;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/d4c904d81970bbe5b0afe1029eae705366779839/src/hotspot/share/opto/library_call.cpp#L4480-L4604",
          sha1 = "34281fb78c4f0657a704dbda3e3cc85ed56dd2ad")
// @formatter:on
public class HotSpotHashCodeSnippets extends IdentityHashCodeSnippets {

    @Override
    protected int computeIdentityHashCode(final Object x) {
        Word mark = loadWordFromObject(x, markOffset(INJECTED_VMCONFIG));

        // When the object is unlocked, HotSpot reuses upper bits (e.g., [38:8] in 64-bits VM) of
        // the mark word in object header for caching identity hash code. The same bits are part of
        // the thread pointer in the case of biased locking, pointer to the displaced mark in a
        // thread's stack in the case of stack locking, or pointer to the monitor object in the case
        // of inflated lock.
        //
        // To test if an object is unlocked, we simply need to compare the lock bits against the
        // constant unlocked value. The lock bits are the least significant 3 bits prior to Java 18
        // (1 bit for biased locking and 2 bits for stack locking or heavy locking), and 2 bits
        // afterwards due to elimination of the biased locking. The unlocked values are 001 and 01
        // respectively. See src/hotspot/share/oops/markWord.hpp for more details.
        final Word lockBits = mark.and(lockMaskInPlace(INJECTED_VMCONFIG));
        if (probability(FAST_PATH_PROBABILITY, lockBits.equal(WordFactory.unsigned(unlockedMask(INJECTED_VMCONFIG))))) {
            int hash = (int) mark.unsignedShiftRight(identityHashCodeShift(INJECTED_VMCONFIG)).rawValue();
            if (probability(FAST_PATH_PROBABILITY, hash != uninitializedIdentityHashCodeValue(INJECTED_VMCONFIG))) {
                return hash;
            }
        }
        return identityHashCode(IDENTITY_HASHCODE, x);
    }
}
