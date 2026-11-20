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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.PointstoOptions.ContextSensitivity;
import com.oracle.graal.pointsto.standalone.test.classes.StandalonePrimitivePredicateCase;
import com.oracle.graal.pointsto.util.AnalysisError;

/**
 * Verifies that context-sensitive analysis rejects primitive-value tracking and predicates, which
 * it does not support yet, whatever route selects the context-sensitive policy. Each test passes
 * its policy after any analysis options given on the command line, so every context-sensitive
 * policy is covered whatever policy the suite runs under. The diagnostic names the mode in effect,
 * the option that selected it, and the remedy.
 */
public class StandaloneContextSensitivePredicateRejectionTest extends StandaloneAnalysisTest {

    private static final String TRACK_PRIMITIVE_VALUES = "-H:+TrackPrimitiveValues";
    private static final String USE_PREDICATES = "-H:+UsePredicates";
    private static final String NO_USE_PREDICATES = "-H:-UsePredicates";
    private static final String ALLOCATION_SITE_SENSITIVE_HEAP = "-H:+AllocationSiteSensitiveHeap";
    private static final String NO_ALLOCATION_SITE_SENSITIVE_HEAP = "-H:-AllocationSiteSensitiveHeap";
    private static final String CALLING_CONTEXT_MODE = "Calling-context-sensitive analysis (";
    private static final String HEAP_MODE = "Context-insensitive analysis with an allocation-site-sensitive heap (";
    private static final String TURN_OFF_BOTH = "Pass -H:-TrackPrimitiveValues -H:-UsePredicates";

    /**
     * Predicates enabled after the policy, so the policy's own reset does not turn them off.
     */
    @Test
    public void testPredicatesRejected() {
        for (ContextSensitivity policy : contextSensitivePolicies()) {
            String message = assertPolicyRejected(policy, USE_PREDICATES);
            // Enabling predicates also enables primitive-value tracking.
            assertContains(message, TRACK_PRIMITIVE_VALUES, USE_PREDICATES);
        }
    }

    /**
     * Primitive-value tracking and predicates enabled after the policy.
     */
    @Test
    public void testPrimitivesAndPredicatesRejected() {
        for (ContextSensitivity policy : contextSensitivePolicies()) {
            String message = assertPolicyRejected(policy, TRACK_PRIMITIVE_VALUES, USE_PREDICATES);
            assertContains(message, TRACK_PRIMITIVE_VALUES, USE_PREDICATES);
        }
    }

    /**
     * Primitive-value tracking alone is not supported either.
     */
    @Test
    public void testPrimitiveTrackingRejected() {
        for (ContextSensitivity policy : contextSensitivePolicies()) {
            String message = assertPolicyRejected(policy, TRACK_PRIMITIVE_VALUES, NO_USE_PREDICATES);
            assertContains(message, TRACK_PRIMITIVE_VALUES);
            assertNotContains(message, USE_PREDICATES);
        }
    }

    /**
     * {@code AllocationSiteSensitiveHeap} selects a context-sensitive policy on its own and does
     * not reset the two options. Calling contexts stay insensitive, so the diagnostic names the
     * allocation-site-sensitive heap and that option rather than the {@code insens} value of
     * {@code AnalysisContextSensitivity}.
     */
    @Test
    public void testHeapSensitivityOptionRejected() {
        String message = assertRejected(policyOption(ContextSensitivity.insens), ALLOCATION_SITE_SENSITIVE_HEAP, TRACK_PRIMITIVE_VALUES, USE_PREDICATES);
        assertContains(message, HEAP_MODE + ALLOCATION_SITE_SENSITIVE_HEAP + ")", TRACK_PRIMITIVE_VALUES, USE_PREDICATES, TURN_OFF_BOTH,
                        "turn off the allocation-site-sensitive heap with " + NO_ALLOCATION_SITE_SENSITIVE_HEAP + ".");
        assertNotContains(message, policyOption(ContextSensitivity.insens));
    }

    /**
     * {@code AllocationSiteSensitiveHeap} with a positive {@code MaxCallingContextDepth} makes
     * calling contexts sensitive, so the diagnostic names that mode and both options.
     */
    @Test
    public void testHeapSensitivityWithCallingContextsRejected() {
        String depth = "-H:MaxCallingContextDepth=1";
        String message = assertRejected(policyOption(ContextSensitivity.insens), ALLOCATION_SITE_SENSITIVE_HEAP, depth, USE_PREDICATES);
        assertContains(message, CALLING_CONTEXT_MODE + ALLOCATION_SITE_SENSITIVE_HEAP + " " + depth + ")", USE_PREDICATES, TURN_OFF_BOTH,
                        "select context-insensitive analysis with " + NO_ALLOCATION_SITE_SENSITIVE_HEAP + ".");
    }

    /**
     * Options given before the policy are still reset by it, so this configuration remains
     * accepted with both options off.
     */
    @Test
    public void testForcingOrderAccepted() {
        for (ContextSensitivity policy : contextSensitivePolicies()) {
            PointsToAnalysis analysis = createAnalysis(StandalonePrimitivePredicateCase.class, TRACK_PRIMITIVE_VALUES, USE_PREDICATES, policyOption(policy));
            assertTrue(policy + " should select a context-sensitive policy", analysis.analysisPolicy().isContextSensitiveAnalysis());
            assertFalse(policy + " should turn off primitive-value tracking", analysis.trackPrimitiveValues());
            assertFalse(policy + " should turn off predicates", analysis.usePredicates());
        }
    }

    /**
     * Context-insensitive analysis keeps both options and prunes a branch with a predicate.
     */
    @Test
    public void testInsensitivePredicatesAnalyzed() {
        runAnalysis(StandalonePrimitivePredicateCase.class, policyOption(ContextSensitivity.insens), TRACK_PRIMITIVE_VALUES, USE_PREDICATES);

        assertFalse(analysis().analysisPolicy().isContextSensitiveAnalysis());
        assertTrue(analysis().trackPrimitiveValues());
        assertTrue(analysis().usePredicates());
        assertImplementationInvoked(findMethod(StandalonePrimitivePredicateCase.class, "reachable1"));
        assertNotImplementationInvoked(findMethod(StandalonePrimitivePredicateCase.class, "unreachable1"));
    }

    private static List<ContextSensitivity> contextSensitivePolicies() {
        return Arrays.stream(ContextSensitivity.values()).filter(policy -> policy != ContextSensitivity.insens).toList();
    }

    private static String policyOption(ContextSensitivity policy) {
        return "-H:AnalysisContextSensitivity=" + policy.value();
    }

    /**
     * Asserts that the policy, followed by the enabled options, is rejected with a diagnostic that
     * names the policy's mode, the policy selector, and the remedy. Only {@code allocsens} keeps
     * calling contexts insensitive.
     */
    private String assertPolicyRejected(ContextSensitivity policy, String... enabledOptions) {
        String selector = policyOption(policy);
        String message = assertRejected(Stream.concat(Stream.of(selector), Arrays.stream(enabledOptions)).toArray(String[]::new));
        boolean callingContextSensitive = policy != ContextSensitivity.allocsens;
        String mode = callingContextSensitive ? CALLING_CONTEXT_MODE : HEAP_MODE;
        String remedy = callingContextSensitive ? "select context-insensitive analysis" : "turn off the allocation-site-sensitive heap";
        assertContains(message, mode + selector + ")", TURN_OFF_BOTH, remedy + " with " + policyOption(ContextSensitivity.insens) + ".");
        return message;
    }

    /**
     * Asserts that creating the analysis is rejected, and that the rejection comes from the
     * analysis constructor: before any root is registered, and without running the analysis.
     */
    private String assertRejected(String... options) {
        AnalysisError.UserError error = assertThrows("Expected rejection for " + Arrays.toString(options), AnalysisError.UserError.class,
                        () -> createAnalysis(StandalonePrimitivePredicateCase.class, options));
        assertTrue("Expected the rejection while the analysis is created: " + error,
                        Arrays.stream(error.getStackTrace()).anyMatch(frame -> frame.getClassName().equals(PointsToAnalysis.class.getName()) && frame.getMethodName().equals("<init>")));
        return error.getMessage();
    }

    private static void assertContains(String message, String... fragments) {
        for (String fragment : fragments) {
            assertTrue("Expected \"" + fragment + "\" in: " + message, message.contains(fragment));
        }
    }

    private static void assertNotContains(String message, String fragment) {
        assertFalse("Did not expect \"" + fragment + "\" in: " + message, message.contains(fragment));
    }
}
