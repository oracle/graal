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
package org.graalvm.compiler.hotspot.replacements;

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl.IDENTITY_HASHCODE;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.identityHashCode;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.identityHashCodeShift;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.loadWordFromObject;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.lockMaskInPlace;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.markOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.uninitializedIdentityHashCodeValue;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.unlockedMask;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.FAST_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;

import org.graalvm.compiler.lir.StubPort;
import org.graalvm.compiler.replacements.IdentityHashCodeSnippets;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.WordFactory;

// @formatter:off
@StubPort(path      = "src/hotspot/share/opto/library_call.cpp",
          lineStart = 4311,
          lineEnd   = 4435,
          commit    = "540c706bbcbb809ae1304aac4f2a16a5e83cb458",
          sha1      = "bbf28398bfdef37afbb856160110d010c60e87e7")
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
