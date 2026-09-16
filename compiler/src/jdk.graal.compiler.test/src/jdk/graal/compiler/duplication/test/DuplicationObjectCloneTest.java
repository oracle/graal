/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.duplication.test;

import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.duplication.phases.simulation.DuplicationOptions;
import jdk.graal.compiler.duplication.phases.simulation.DuplicationPhase;
import jdk.graal.compiler.duplication.phases.simulation.FixedDuplicationSimulationConfig;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DisableOverflownCountedLoopsPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Exercises interactions between duplication, conditional elimination, and intrinsified array
 * clones.
 */
public class DuplicationObjectCloneTest extends GraalCompilerTest {

    public static Object preparedCloneHelper(Object array) {
        GraalDirectives.guardingNonNull(array);
        if (array instanceof byte[]) {
            return ((byte[]) array).clone();
        }
        return ((Object[]) array).clone();
    }

    public static Object callerWithTwoByteArrayPredecessors(Object left, Object right, Object fallback, int select) {
        Object array;
        boolean useClone;
        if (select == 0) {
            GraalDirectives.controlFlowAnchor();
            byte[] checked = (byte[]) left;
            GraalDirectives.blackhole(checked);
            array = left;
            useClone = true;
        } else if (select == 1) {
            GraalDirectives.controlFlowAnchor();
            byte[] checked = (byte[]) right;
            GraalDirectives.blackhole(checked);
            array = right;
            useClone = true;
        } else {
            GraalDirectives.controlFlowAnchor();
            array = fallback;
            useClone = false;
        }
        if (!useClone) {
            return array;
        }
        return preparedCloneHelper(array);
    }

    /**
     * Prepares the helper independently before inlining it into the caller, matching Truffle's
     * separation between partial evaluation of an inlinee and agnostic inlining into the root.
     */
    @Test
    public void testPreparedCloneInlinee() {
        OptionValues options = DuplicationTestOptions.initial();
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();

        StructuredGraph inlinee = parseEager("preparedCloneHelper", AllowAssumptions.YES, options);
        canonicalizer.apply(inlinee, getProviders());
        StructuredGraph caller = parseEager("callerWithTwoByteArrayPredecessors", AllowAssumptions.YES, options);
        canonicalizer.apply(caller, getProviders());
        ResolvedJavaMethod helperMethod = getResolvedJavaMethod("preparedCloneHelper");
        InvokeNode helperInvoke = caller.getNodes().filter(InvokeNode.class).first();
        InliningUtil.inline(helperInvoke, inlinee, true, helperMethod, "test", getClass().getName());

        new DisableOverflownCountedLoopsPhase().apply(caller);
        new DuplicationPhase(FixedDuplicationSimulationConfig.defaultForDepth(DuplicationOptions.EarlySimulationDepth), true, true,
                        DuplicationPhase.FACTORS_INCLUDING_PEA, canonicalizer, options).apply(caller, getDefaultHighTierContext());
        new HighTierLoweringPhase(canonicalizer).apply(caller, getDefaultHighTierContext());
    }
}
