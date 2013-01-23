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
package com.oracle.graal.hotspot.snippets;

import static com.oracle.graal.hotspot.snippets.HotSpotSnippetUtils.*;
import static com.oracle.graal.snippets.nodes.BranchProbabilityNode.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.snippets.*;
import com.oracle.graal.word.*;

/**
 * Utilities and common code paths used by the type check snippets.
 */
public class TypeCheckSnippetUtils {

    static boolean checkSecondarySubType(Word t, Word s) {
        // if (S.cache == T) return true
        if (s.readWord(secondarySuperCacheOffset()) == t) {
            cacheHit.inc();
            return true;
        }

        return checkSelfAndSupers(t, s);
    }

    static boolean checkUnknownSubType(Word t, Word s) {
        // int off = T.offset
        int superCheckOffset = t.readInt(superCheckOffsetOffset());
        boolean primary = superCheckOffset != secondarySuperCacheOffset();

        // if (T = S[off]) return true
        if (s.readWord(superCheckOffset) == t) {
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
        if (s == t) {
            T_equals_S.inc();
            return true;
        }

        // if (S.scan_s_s_array(T)) { S.cache = T; return true; }
        Word secondarySupers = s.readWord(secondarySupersOffset());
        int length = secondarySupers.readInt(metaspaceArrayLengthOffset());
        for (int i = 0; i < length; i++) {
            if (t == loadWordElement(secondarySupers, i)) {
                probability(0.01);
                s.writeWord(secondarySuperCacheOffset(), t);
                secondariesHit.inc();
                return true;
            }
        }
        secondariesMiss.inc();
        return false;
    }

    static ConstantNode[] createHints(TypeCheckHints hints, MetaAccessProvider runtime, Graph graph) {
        ConstantNode[] hintHubs = new ConstantNode[hints.types.length];
        for (int i = 0; i < hintHubs.length; i++) {
            hintHubs[i] = ConstantNode.forConstant(((HotSpotResolvedObjectType) hints.types[i]).klass(), runtime, graph);
        }
        return hintHubs;
    }

    static Word loadWordElement(Word metaspaceArray, int index) {
        return metaspaceArray.readWord(metaspaceArrayBaseOffset() + index * wordSize());
    }

    private static final SnippetCounter.Group counters = GraalOptions.SnippetCounters ? new SnippetCounter.Group("TypeCheck") : null;
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
