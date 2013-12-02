/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.*;
import static com.oracle.graal.phases.GraalOptions.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.word.*;

//JaCoCo Exclude

/**
 * Utilities and common code paths used by the type check snippets.
 */
public class TypeCheckSnippetUtils {

    public static final LocationIdentity TYPE_DISPLAY_LOCATION = new NamedLocationIdentity("TypeDisplay");

    static boolean checkSecondarySubType(Word t, Word s) {
        // if (S.cache == T) return true
        if (s.readWord(secondarySuperCacheOffset(), SECONDARY_SUPER_CACHE_LOCATION).equal(t)) {
            cacheHit.inc();
            return true;
        }

        return checkSelfAndSupers(t, s);
    }

    static boolean checkUnknownSubType(Word t, Word s) {
        // int off = T.offset
        int superCheckOffset = t.readInt(superCheckOffsetOffset(), LocationIdentity.FINAL_LOCATION);
        boolean primary = superCheckOffset != secondarySuperCacheOffset();

        // if (T = S[off]) return true
        if (s.readWord(superCheckOffset, TYPE_DISPLAY_LOCATION).equal(t)) {
            if (primary) {
                cacheHit.inc();
            } else {
                displayHit.inc();
            }
            return true;
        }

        // if (off != &cache) return false
        if (primary) {
            displayMiss.inc();
            return false;
        }

        return checkSelfAndSupers(t, s);
    }

    private static boolean checkSelfAndSupers(Word t, Word s) {
        // if (T == S) return true
        if (s.equal(t)) {
            T_equals_S.inc();
            return true;
        }

        // if (S.scan_s_s_array(T)) { S.cache = T; return true; }
        Word secondarySupers = s.readWord(secondarySupersOffset(), SECONDARY_SUPERS_LOCATION);
        int length = secondarySupers.readInt(metaspaceArrayLengthOffset(), LocationIdentity.FINAL_LOCATION);
        for (int i = 0; i < length; i++) {
            if (probability(NOT_LIKELY_PROBABILITY, t.equal(loadSecondarySupersElement(secondarySupers, i)))) {
                s.writeWord(secondarySuperCacheOffset(), t, SECONDARY_SUPER_CACHE_LOCATION);
                secondariesHit.inc();
                return true;
            }
        }
        secondariesMiss.inc();
        return false;
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
                hubs[index] = ConstantNode.forConstant(((HotSpotResolvedObjectType) hints.hints[i].type).klass(), metaAccess, graph);
                isPositive[index] = hints.hints[i].positive;
                index++;
            }
        }
        if (positiveOnly && index != hubs.length) {
            assert index < hubs.length;
            hubs = Arrays.copyOf(hubs, index);
            isPositive = Arrays.copyOf(isPositive, index);
        }
        return new Hints(hubs, isPositive);
    }

    static Word loadSecondarySupersElement(Word metaspaceArray, int index) {
        return metaspaceArray.readWord(metaspaceArrayBaseOffset() + index * wordSize(), LocationIdentity.FINAL_LOCATION);
    }

    private static final SnippetCounter.Group counters = SnippetCounters.getValue() ? new SnippetCounter.Group("TypeCheck") : null;
    static final SnippetCounter hintsHit = new SnippetCounter(counters, "hintsHit", "hit a hint type");
    static final SnippetCounter exactHit = new SnippetCounter(counters, "exactHit", "exact type test succeeded");
    static final SnippetCounter exactMiss = new SnippetCounter(counters, "exactMiss", "exact type test failed");
    static final SnippetCounter isNull = new SnippetCounter(counters, "isNull", "object tested was null");
    static final SnippetCounter cacheHit = new SnippetCounter(counters, "cacheHit", "secondary type cache hit");
    static final SnippetCounter secondariesHit = new SnippetCounter(counters, "secondariesHit", "secondaries scan succeeded");
    static final SnippetCounter secondariesMiss = new SnippetCounter(counters, "secondariesMiss", "secondaries scan failed");
    static final SnippetCounter displayHit = new SnippetCounter(counters, "displayHit", "primary type test succeeded");
    static final SnippetCounter displayMiss = new SnippetCounter(counters, "displayMiss", "primary type test failed");
    static final SnippetCounter T_equals_S = new SnippetCounter(counters, "T_equals_S", "object type was equal to secondary type");

}
