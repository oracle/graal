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
package jdk.graal.compiler.hotspot.replacements;

import static jdk.graal.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static jdk.graal.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl.IDENTITY_HASHCODE;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.identityHashCode;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.identityHashCodeShift;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.loadWordFromObject;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.lockMaskInPlace;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.markOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.monitorValue;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.uninitializedIdentityHashCodeValue;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.unlockedValue;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.useLightweightLocking;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.useObjectMonitorTable;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.FAST_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;

import org.graalvm.word.WordFactory;

import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.replacements.IdentityHashCodeSnippets;
import jdk.graal.compiler.word.Word;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/7131f053b0d26b62cbf0d8376ec117d6e8d79f9e/src/hotspot/share/opto/library_call.cpp#L4675-L4809",
          sha1 = "c212d1dbff26d02d4d749e085263d4104895f1ba")
// @formatter:on
public class HotSpotHashCodeSnippets extends IdentityHashCodeSnippets {

    @Override
    protected int computeIdentityHashCode(final Object x) {
        Word mark = loadWordFromObject(x, markOffset(INJECTED_VMCONFIG));

        // In HotSpot, the upper bits (i.e., [63:2] in 64-bits VM) of the mark word in object header
        // are
        // 1) not used with lightweight locking;
        // 2) pointer to the displaced mark in a thread's stack with stack locking; or
        // 3) pointer to the monitor object with heavy monitor locking.
        //
        // When these upper bits are not used, i.e., when an object is either unlocked or locked
        // with lightweight locking, HotSpot reuses fraction of the upper bits (e.g., [38:8] in
        // 64-bits VM) for caching the identity hash code. Therefore,
        // 1) when lightweight locking is employed as fast locking scheme (-XX:LockingMode=2), we
        // only need to test if the object is NOT in a monitor-locked state, i.e., lock bits not
        // equals to 0b10;
        // 2) when stack locking is employed as fast locking scheme (-XX:LockingMode=1) or no fast
        // locking scheme is employed (-XX:LockingMode=0), we need to test if the object is
        // unlocked, i.e., lock bits equals to 0b01.
        //
        // See src/hotspot/share/oops/markWord.hpp for more details.
        final Word lockBits = mark.and(lockMaskInPlace(INJECTED_VMCONFIG));
        if (probability(FAST_PATH_PROBABILITY, useObjectMonitorTable(INJECTED_VMCONFIG) ||
                        useLightweightLocking(INJECTED_VMCONFIG) ? lockBits.notEqual(WordFactory.unsigned(monitorValue(INJECTED_VMCONFIG)))
                                        : lockBits.equal(WordFactory.unsigned(unlockedValue(INJECTED_VMCONFIG))))) {
            int hash = (int) mark.unsignedShiftRight(identityHashCodeShift(INJECTED_VMCONFIG)).rawValue();
            if (probability(FAST_PATH_PROBABILITY, hash != uninitializedIdentityHashCodeValue(INJECTED_VMCONFIG))) {
                return hash;
            }
        }
        return identityHashCode(IDENTITY_HASHCODE, x);
    }
}
