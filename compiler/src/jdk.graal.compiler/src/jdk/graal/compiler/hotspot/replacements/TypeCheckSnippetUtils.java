/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.graal.compiler.hotspot.stubs.LookUpSecondarySupersTableStub.SECONDARY_SUPERS_TABLE_MASK;
import static jdk.graal.compiler.hotspot.stubs.LookUpSecondarySupersTableStub.loadSecondarySupersElement;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.FREQUENT_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.NOT_LIKELY_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;

import java.util.Arrays;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.hotspot.nodes.type.KlassPointerStamp;
import jdk.graal.compiler.hotspot.stubs.LookUpSecondarySupersTableStub;
import jdk.graal.compiler.hotspot.word.KlassPointer;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.TypeCheckHints;
import jdk.graal.compiler.replacements.SnippetCounter;
import jdk.graal.compiler.replacements.SnippetCounter.Group;
import jdk.graal.compiler.serviceprovider.JavaVersionUtil;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.MetaAccessProvider;

//JaCoCo Exclude

/**
 * Utilities and common code paths used by the type check snippets.
 */
public class TypeCheckSnippetUtils {

    @Fold
    static boolean isJDK21() {
        return JavaVersionUtil.JAVA_SPEC == 21;
    }

    static boolean checkSecondarySubType(KlassPointer t, KlassPointer sNonNull, Counters counters) {
        // if (S.cache == T) return true
        if (isJDK21() && sNonNull.readKlassPointer(HotSpotReplacementsUtil.secondarySuperCacheOffset(INJECTED_VMCONFIG), HotSpotReplacementsUtil.SECONDARY_SUPER_CACHE_LOCATION).equal(t)) {
            counters.cacheHit.inc();
            return true;
        }

        return checkSelfAndSupers(t, sNonNull, counters);
    }

    static boolean checkUnknownSubType(KlassPointer t, KlassPointer sNonNull, Counters counters) {
        // int off = T.offset
        int superCheckOffset = t.readInt(HotSpotReplacementsUtil.superCheckOffsetOffset(INJECTED_VMCONFIG), HotSpotReplacementsUtil.KLASS_SUPER_CHECK_OFFSET_LOCATION);
        boolean primary = superCheckOffset != HotSpotReplacementsUtil.secondarySuperCacheOffset(INJECTED_VMCONFIG);

        // if (T = S[off]) return true
        if (sNonNull.readKlassPointer(superCheckOffset, HotSpotReplacementsUtil.PRIMARY_SUPERS_LOCATION).equal(t)) {
            if (primary) {
                counters.cacheHit.inc();
            } else {
                counters.displayHit.inc();
            }
            return true;
        }

        // if (off != &cache) return false
        if (primary) {
            counters.displayMiss.inc();
            return false;
        }

        return checkSelfAndSupers(t, sNonNull, counters);
    }

    // @formatter:off
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/8032d640c0d34fe507392a1d4faa4ff2005c771d/src/hotspot/cpu/x86/macroAssembler_x86.cpp#L4786-L4881",
              sha1 = "c0e2fdd973dc975757d58080ba94efe628d6a380")
    // @formatter:on
    static boolean checkSelfAndSupers(KlassPointer t, KlassPointer s, Counters counters) {
        // if (T == S) return true
        if (s.equal(t)) {
            counters.equalsSecondary.inc();
            return true;
        }

        Word secondarySupers = s.readWord(HotSpotReplacementsUtil.secondarySupersOffset(INJECTED_VMCONFIG), HotSpotReplacementsUtil.SECONDARY_SUPERS_LOCATION);

        if (isJDK21()) {
            int length = secondarySupers.readInt(HotSpotReplacementsUtil.metaspaceArrayLengthOffset(INJECTED_VMCONFIG), HotSpotReplacementsUtil.METASPACE_ARRAY_LENGTH_LOCATION);
            for (int i = 0; i < length; i++) {
                if (probability(NOT_LIKELY_PROBABILITY, t.equal(loadSecondarySupersElement(secondarySupers, i)))) {
                    s.writeKlassPointer(HotSpotReplacementsUtil.secondarySuperCacheOffset(INJECTED_VMCONFIG), t, HotSpotReplacementsUtil.SECONDARY_SUPER_CACHE_LOCATION);
                    counters.secondariesHit.inc();
                    return true;
                }
            }
            counters.secondariesMiss.inc();
            return false;
        }

        // Since JDK-8180450, HotSpot uses a hashed lookup of secondary supers. It maintains a 6-bit
        // hash and a 64-bit bitmap for each class. A negative lookup is simply testing if T.hash-th
        // bit is not present in the S.bitmap. A positive lookup contains a fast path that tests the
        // corresponding element in secondary supers table of S is T, and a slow path that calls
        // into StubRoutines::lookup_secondary_supers_table_slow_path_stub to handle collision
        // cases.
        //
        // When populating the secondary supers table, HotSpot first uses the hash as index to a
        // temporary array. In the case of collision, HotSpot stores the secondary super in the next
        // empty slot, with the possibility of swapping out existing secondary super to balance the
        // search distance. E.g.:
        //
        // @formatter:off
        // table  [_, _, _, A, _, _, _, B, C, _, _, _, _, _, _, _, ...]
        // bitmap  0  0  0  1  0  0  0  1  1  0  0  0  0  0  0  0  ...
        //        LSB
        // where A.hash == 3, B.hash == 7, C.hash == 7
        // The temporary secondary supers table is then compressed:
        //
        // table  [A, B, C, _, _, _, _, _, _, _, _, _, _, _, _, _, ...]
        // bitmap  0  0  0  1  0  0  0  1  1  0  0  0  0  0  0  0  ...
        // @formatter:on
        //
        // The index to the compressed secondary supers table is calculated via
        // Long.bitCount(S.bitmap << (63 - T.hash)) - 1

        // bit will be folded when T is constant.
        int bit = t.readByte(HotSpotReplacementsUtil.klassHashSlotOffset(INJECTED_VMCONFIG), HotSpotReplacementsUtil.KLASS_HASH_SLOT_LOCATION);
        long bitmap = s.readLong(HotSpotReplacementsUtil.klassBitmapOffset(INJECTED_VMCONFIG), HotSpotReplacementsUtil.KLASS_BITMAP_LOCATION);

        long bitmapShifted = bitmap << (SECONDARY_SUPERS_TABLE_MASK - bit);
        if (probability(NOT_LIKELY_PROBABILITY, bitmapShifted >= 0)) {
            // This is equivalent to bitmap & (1L << bit) == 0
            counters.bitmapMiss.inc();
            return false;
        }

        // Note that Array<Klass*>::_data[0] is offsetted by 8 in an Array<Klass*>. HotSpot assumes
        // Array<Klass*>::base_offset_in_bytes() == wordSize, and uses Long.bitCount(bitmapShifted)
        // as index to the hashed secondary supers table without adjusting the array base.
        // We instead rely on the compiler for constant folding.
        // Use long to avoid unnecessary zero extension.
        long index = Long.bitCount(bitmapShifted) - 1L;
        KlassPointer hashed = loadSecondarySupersElement(secondarySupers, index);

        if (probability(FREQUENT_PROBABILITY, t.equal(hashed))) {
            counters.hashHit.inc();
            return true;
        }

        if (probability(NOT_LIKELY_PROBABILITY, (bitmap & (1L << ((bit + 1) & SECONDARY_SUPERS_TABLE_MASK))) == 0)) {
            // Next slot is empty -- there is no collision.
            counters.bitmapMiss.inc();
            return false;
        }

        bitmap = Long.rotateRight(bitmap, bit);
        counters.hashMissStub.inc();
        return LookUpSecondarySupersTableStub.lookupSecondarySupersTableStub(t, secondarySupers, bitmap, index + 1);
    }

    static class Counters {
        final SnippetCounter hintsHit;
        final SnippetCounter hintsMiss;
        final SnippetCounter exactHit;
        final SnippetCounter exactMiss;
        final SnippetCounter isNull;
        final SnippetCounter cacheHit;
        final SnippetCounter secondariesHit;
        final SnippetCounter secondariesMiss;
        final SnippetCounter bitmapMiss;
        final SnippetCounter hashHit;
        final SnippetCounter hashMissStub;
        final SnippetCounter displayHit;
        final SnippetCounter displayMiss;
        final SnippetCounter equalsSecondary;

        Counters(SnippetCounter.Group.Factory factory) {
            Group group = factory.createSnippetCounterGroup("TypeCheck");
            hintsHit = new SnippetCounter(group, "hintsHit", "hit a hint type");
            hintsMiss = new SnippetCounter(group, "hintsMiss", "missed a hint type");
            exactHit = new SnippetCounter(group, "exactHit", "exact type test succeeded");
            exactMiss = new SnippetCounter(group, "exactMiss", "exact type test failed");
            isNull = new SnippetCounter(group, "isNull", "object tested was null");
            cacheHit = new SnippetCounter(group, "cacheHit", "secondary type cache hit");
            secondariesHit = new SnippetCounter(group, "secondariesHit", "secondaries scan succeeded");
            secondariesMiss = new SnippetCounter(group, "secondariesMiss", "secondaries scan failed");
            bitmapMiss = new SnippetCounter(group, "bitmapMiss", "secondary type not present in bitmap");
            hashHit = new SnippetCounter(group, "hashHit", "secondary type found in hashed table");
            hashMissStub = new SnippetCounter(group, "hashMissStub", "secondary type not found in first attempt, call into runtime");
            displayHit = new SnippetCounter(group, "displayHit", "primary type test succeeded");
            displayMiss = new SnippetCounter(group, "displayMiss", "primary type test failed");
            equalsSecondary = new SnippetCounter(group, "T_equals_S", "object type was equal to secondary type");
        }
    }

    /**
     * A set of type check hints ordered by decreasing probabilities.
     */
    public static class Hints {

        /**
         * The hubs of the hint types.
         */
        public final ConstantNode[] hubs;

        /**
         * A predicate over {@link #hubs} specifying whether the corresponding hint type is a
         * sub-type of the checked type.
         */
        public final boolean[] isPositive;

        Hints(ConstantNode[] hints, boolean[] hintIsPositive) {
            this.hubs = hints;
            this.isPositive = hintIsPositive;
        }
    }

    static Hints createHints(TypeCheckHints hints, MetaAccessProvider metaAccess, boolean positiveOnly, StructuredGraph graph) {
        ConstantNode[] hubs = new ConstantNode[hints.hints.length];
        boolean[] isPositive = new boolean[hints.hints.length];
        int index = 0;
        for (int i = 0; i < hubs.length; i++) {
            if (!positiveOnly || hints.hints[i].positive) {
                hubs[index] = ConstantNode.forConstant(KlassPointerStamp.klassNonNull(), ((HotSpotResolvedObjectType) hints.hints[i].type).klass(), metaAccess, graph);
                isPositive[index] = hints.hints[i].positive;
                index++;
            }
        }
        if (positiveOnly && index != hubs.length) {
            assert index < hubs.length : Assertions.errorMessage(index, hubs);
            hubs = Arrays.copyOf(hubs, index);
            isPositive = Arrays.copyOf(isPositive, index);
        }
        return new Hints(hubs, isPositive);
    }

}
