/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.api.directives.test;

import static jdk.graal.compiler.api.directives.GraalDirectives.injectBranchProbability;
import static jdk.graal.compiler.api.directives.GraalDirectives.sideEffect;
import static jdk.graal.compiler.debug.DebugOptions.DumpOnError;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.ProfileData.ProfileSource;
import jdk.graal.compiler.nodes.ShortCircuitOrNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import org.junit.Assert;
import org.junit.Test;

public class ProbabilityDirectiveShortCircuitTest extends GraalCompilerTest {

    private void checkProfiles(String snippetName) {
        OptionValues noDumpOnError = new OptionValues(getInitialOptions(), DumpOnError, false);
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod(snippetName), noDumpOnError);
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        createInliningPhase(canonicalizer).apply(graph, getDefaultHighTierContext());
        canonicalizer.apply(graph, getDefaultHighTierContext());
        for (IfNode ifNode : graph.getNodes(IfNode.TYPE)) {
            Assert.assertEquals(ifNode + " profile source", ProfileSource.INJECTED, ifNode.profileSource());
        }
        for (ShortCircuitOrNode shortCircuit : graph.getNodes(ShortCircuitOrNode.TYPE)) {
            Assert.assertEquals(shortCircuit + " profile source", ProfileSource.INJECTED, shortCircuit.getShortCircuitProbability().getProfileSource());
        }
    }

    @Test
    public void andIf() {
        checkProfiles("andIfSnippet");
    }

    public static boolean andIfSnippet(boolean a, int b, double c) {
        if (injectBranchProbability(0.125, a) && injectBranchProbability(0.125, b == 42) && injectBranchProbability(0.125, c > 0.0)) {
            sideEffect();  // prevent folding to a conditional
            return true;
        } else {
            return false;
        }
    }

    @Test
    public void andConditional() {
        checkProfiles("andConditionalSnippet");
    }

    public static boolean andConditionalSnippet(boolean a, int b, double c) {
        // This will fold to a conditional.
        if (injectBranchProbability(0.125, a) && injectBranchProbability(0.125, b == 42) && injectBranchProbability(0.125, c > 0.0)) {
            return true;
        } else {
            return false;
        }
    }

    @Test(expected = GraalError.class)
    public void andReturn() {
        checkProfiles("andReturnSnippet");
    }

    @BytecodeParserForceInline
    public static boolean andReturnSnippet(boolean a, int b, double c) {
        /*
         * This builds a graph shape with a BranchProbabilityNode used by a ValuePhi used by a
         * Return. That is not accepted by the simplification in BranchProbabilityNode that
         * propagates injected probabilities to the correct usage. Top-level snippets wanting to
         * inject probabilities this way have to use an explicit if statement (which will then be
         * folded correctly). However, inlining this pattern into a snippet works fine as long as
         * the call site also injects a probability.
         */
        return injectBranchProbability(0.125, a) && injectBranchProbability(0.125, b == 42) && injectBranchProbability(0.125, c > 0.0);
    }

    @Test
    public void andInlined() {
        checkProfiles("andInlinedSnippet");
    }

    public static boolean andInlinedSnippet(boolean a, int b, double c) {
        if (injectBranchProbability(0.25, andReturnSnippet(a, b, c))) {
            sideEffect();
            return true;
        } else {
            return false;
        }
    }

    @Test
    public void orInlined() {
        checkProfiles("orInlinedSnippet");
    }

    @BytecodeParserForceInline
    public static boolean orHelper(Integer i) {
        if (injectBranchProbability(0.25, i == null) || injectBranchProbability(0.25, i < 42)) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean orInlinedSnippet(Integer i) {
        if (injectBranchProbability(0.25, orHelper(i))) {
            sideEffect();
            return true;
        } else {
            return false;
        }
    }
}
