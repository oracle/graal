/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.automaton;

import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.ObjectArrayBuffer;
import com.oracle.truffle.regex.tregex.matchers.MatcherBuilder;
import org.graalvm.collections.EconomicMap;

import java.util.List;

public abstract class StateTransitionCanonicalizer<SS, TB extends TransitionBuilder<SS>> {

    private final MatcherBuilder[] intersectionResult = new MatcherBuilder[3];
    private final EconomicMap<SS, TB> mergeSameTargetsMap = EconomicMap.create();

    public TB[] run(List<TB> transitions, CompilationBuffer compilationBuffer) {
        ObjectArrayBuffer disjointTransitions = calcDisjointTransitions(transitions, compilationBuffer);
        return mergeSameTargets(compilationBuffer, disjointTransitions);
    }

    @SuppressWarnings("unchecked")
    private ObjectArrayBuffer calcDisjointTransitions(List<TB> transitions, CompilationBuffer compilationBuffer) {
        ObjectArrayBuffer disjointTransitions = compilationBuffer.getObjectBuffer1();
        for (TB t : transitions) {
            for (int i = 0; i < disjointTransitions.size(); i++) {
                TB dt = (TB) disjointTransitions.get(i);
                dt.getMatcherBuilder().intersectAndSubtract(t.getMatcherBuilder(), compilationBuffer, intersectionResult);
                MatcherBuilder dtSubtractedMatcher = intersectionResult[0];
                MatcherBuilder tSubtractedMatcher = intersectionResult[1];
                MatcherBuilder intersection = intersectionResult[2];
                if (intersection.matchesSomething()) {
                    if (dtSubtractedMatcher.matchesNothing()) {
                        dt.mergeInPlace(t, intersection);
                    } else {
                        dt.setMatcherBuilder(dtSubtractedMatcher);
                        disjointTransitions.add(dt.createMerged(t, intersection));
                    }
                    t.setMatcherBuilder(tSubtractedMatcher);
                    if (tSubtractedMatcher.matchesNothing()) {
                        break;
                    }
                }
            }
            if (t.getMatcherBuilder().matchesSomething()) {
                disjointTransitions.add(t);
            }
        }
        return disjointTransitions;
    }

    @SuppressWarnings("unchecked")
    private TB[] mergeSameTargets(CompilationBuffer compilationBuffer, ObjectArrayBuffer disjointTransitions) {
        int resultSize = 0;
        for (Object o : disjointTransitions) {
            TB tb = (TB) o;
            if (tb.getMatcherBuilder().matchesNothing()) {
                continue;
            }
            TB existingTransitions = mergeSameTargetsMap.get(tb.getTargetState());
            if (existingTransitions == null) {
                mergeSameTargetsMap.put(tb.getTargetState(), tb);
                resultSize++;
            } else {
                boolean merged = false;
                TB mergeCandidate = existingTransitions;
                do {
                    if (isSameTargetMergeAllowed(tb, mergeCandidate)) {
                        mergeCandidate.setMatcherBuilder(mergeCandidate.getMatcherBuilder().union(tb.getMatcherBuilder(), compilationBuffer));
                        merged = true;
                        break;
                    }
                    mergeCandidate = (TB) mergeCandidate.getNext();
                } while (mergeCandidate != null);
                if (!merged) {
                    tb.setNext(existingTransitions);
                    mergeSameTargetsMap.put(tb.getTargetState(), tb);
                    resultSize++;
                }
            }
        }
        TB[] resultArray = createResultArray(resultSize);
        int i = 0;
        for (TB list : mergeSameTargetsMap.getValues()) {
            TB tb = list;
            do {
                resultArray[i++] = tb;
                tb = (TB) tb.getNext();
            } while (tb != null);
        }
        mergeSameTargetsMap.clear();
        return resultArray;
    }

    protected abstract boolean isSameTargetMergeAllowed(TB a, TB b);

    protected abstract TB[] createResultArray(int size);
}
