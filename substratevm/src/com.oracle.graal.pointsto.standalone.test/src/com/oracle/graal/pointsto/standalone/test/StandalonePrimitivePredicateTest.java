/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;

import org.junit.Test;

import com.oracle.graal.pointsto.standalone.test.classes.StandalonePrimitivePredicateCase;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;

/**
 * Covers primitive-value tracking and predicate-driven reachability in standalone analysis.
 *
 * Context-sensitive analysis does not support either option yet. When the analysis options given
 * on the command line select a context-sensitive policy, the test asserts that the analysis is
 * rejected instead of skipping, so that the rejection stays covered.
 */
public class StandalonePrimitivePredicateTest extends StandaloneAnalysisTest {

    private static final String TRACK_PRIMITIVE_VALUES = "-H:+TrackPrimitiveValues";
    private static final String USE_PREDICATES = "-H:+UsePredicates";

    @Override
    protected String[] extraOptions() {
        return new String[]{
                        TRACK_PRIMITIVE_VALUES,
                        USE_PREDICATES
        };
    }

    /**
     * Verifies exact primitive propagation, merged primitive propagation, and basic predicate-based
     * branch elimination. Equal uncached primitive states must also obey the {@code hashCode}
     * contract.
     */
    @Test
    public void testPrimitiveAndPredicateAnalysis() {
        if (selectsContextSensitivePolicy()) {
            String message = assertThrows(AnalysisError.UserError.class, () -> createAnalysis(StandalonePrimitivePredicateCase.class)).getMessage();
            assertTrue(message, message.contains(TRACK_PRIMITIVE_VALUES) && message.contains(USE_PREDICATES));
            return;
        }

        runAnalysis(StandalonePrimitivePredicateCase.class);

        assertParameterState(findMethod(StandalonePrimitivePredicateCase.class, "consumeExact", int.class), 0,
                        state -> assertEquals(TypeState.forPrimitiveConstant(analysis(), 42), state));
        var choose = findMethod(StandalonePrimitivePredicateCase.class, "choose", boolean.class);
        assertImplementationInvoked(choose);
        assertResultSaturated(choose);

        assertImplementationInvoked(findMethod(StandalonePrimitivePredicateCase.class, "reachable1"));
        assertNotImplementationInvoked(findMethod(StandalonePrimitivePredicateCase.class, "unreachable1"));
        assertImplementationInvoked(findMethod(StandalonePrimitivePredicateCase.class, "reachable2"));
        assertNotImplementationInvoked(findMethod(StandalonePrimitivePredicateCase.class, "unreachable2"));

        TypeState firstUncachedConstant = TypeState.forPrimitiveConstant(analysis(), -1);
        TypeState secondUncachedConstant = TypeState.forPrimitiveConstant(analysis(), -1);
        assertEquals(firstUncachedConstant, secondUncachedConstant);
        assertEquals(firstUncachedConstant.hashCode(), secondUncachedConstant.hashCode());

        var uniqueConstants = new HashSet<TypeState>();
        uniqueConstants.add(firstUncachedConstant);
        uniqueConstants.add(secondUncachedConstant);
        assertEquals(1, uniqueConstants.size());
    }

    /**
     * Returns whether the analyzer builds a context-sensitive policy for this run. Turning both
     * options off last never changes the policy, and it keeps the probe from being rejected.
     */
    private boolean selectsContextSensitivePolicy() {
        return createAnalysis(StandalonePrimitivePredicateCase.class, "-H:-TrackPrimitiveValues", "-H:-UsePredicates").analysisPolicy().isContextSensitiveAnalysis();
    }
}
