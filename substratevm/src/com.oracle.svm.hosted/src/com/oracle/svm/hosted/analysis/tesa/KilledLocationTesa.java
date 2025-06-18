/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.analysis.tesa;

import org.graalvm.word.LocationIdentity;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.graal.nodes.SubstrateFieldLocationIdentity;
import com.oracle.svm.hosted.analysis.tesa.effect.LocationEffect;
import com.oracle.svm.hosted.code.AnalysisToHostedGraphTransplanter;
import com.oracle.svm.hosted.meta.HostedUniverse;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MultiMemoryKill;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;

/**
 * TESA instance that computes information about killed locations.
 */
public class KilledLocationTesa extends AbstractTesa<LocationEffect> {

    @Override
    protected LocationEffect noEffect() {
        return LocationEffect.noEffect();
    }

    @Override
    protected LocationEffect anyEffect() {
        return LocationEffect.anyEffect();
    }

    @Override
    protected LocationEffect computeInitialState(AnalysisMethod method, StructuredGraph graph) {
        LocationEffect location = noEffect();
        for (Node node : graph.getNodes()) {
            assert !(node instanceof SafepointNode) : KilledLocationTesa.class.getSimpleName() + " does not support safepoints.";
            if (!isSupportedNode(node)) {
                return anyEffect();
            }
            if (shouldSkipNode(node)) {
                continue;
            }
            if (MemoryKill.isMemoryKill(node)) {
                LocationIdentity[] identities = extractLocationIdentities(node);
                for (LocationIdentity identity : identities) {
                    if (identity.equals(MemoryKill.NO_LOCATION) || identity.isInit()) {
                        continue;
                    }
                    if (identity.isAny()) {
                        return LocationEffect.anyEffect();
                    }
                    location = location.combineEffects(LocationEffect.singleLocation(identity));
                    if (location.isAnyEffect()) {
                        return LocationEffect.anyEffect();
                    }
                }
            }
        }
        return location;
    }

    /**
     * Returns all memory locations that may be killed by the given node.
     */
    public static LocationIdentity[] extractLocationIdentities(Node node) {
        return switch (node) {
            case SingleMemoryKill single -> new LocationIdentity[]{single.getKilledLocationIdentity()};
            case MultiMemoryKill multi -> multi.getKilledLocationIdentities();
            default -> new LocationIdentity[]{LocationIdentity.any()};
        };
    }

    @Override
    protected void optimizeInvoke(HostedUniverse universe, StructuredGraph graph, Invoke invoke, LocationEffect targetState) {
        switch (targetState) {
            case LocationEffect.Empty _ -> setKilledLocationIdentity(invoke, MemoryKill.NO_LOCATION);
            case LocationEffect.Single single -> setKilledLocationIdentity(invoke, transplantIdentity(universe, single.location));
            default -> AnalysisError.shouldNotReachHere(targetState + " is not actionable.");
        }
    }

    /**
     * The effects computed by the analysis may still contain <i>analysis</i> references that have
     * to be transplanted to <i>hosted</i>.
     * 
     * @see AnalysisToHostedGraphTransplanter
     */
    private static LocationIdentity transplantIdentity(HostedUniverse universe, LocationIdentity location) {
        return switch (location) {
            case SubstrateFieldLocationIdentity substrateFieldLocationIdentity -> {
                var field = substrateFieldLocationIdentity.getField();
                assert field instanceof AnalysisField : "The field computed by the TESA should be still an analysis field: " + field;
                yield new SubstrateFieldLocationIdentity(universe.lookup(field), substrateFieldLocationIdentity.isImmutable());
            }
            case FieldLocationIdentity fieldLocationIdentity -> {
                var field = fieldLocationIdentity.getField();
                assert field instanceof AnalysisField : "The field computed by the TESA should be still an analysis field: " + field;
                yield new FieldLocationIdentity(universe.lookup(field), fieldLocationIdentity.isImmutable());
            }
            default -> location;
        };
    }

    private static void setKilledLocationIdentity(Invoke invoke, LocationIdentity locationIdentity) {
        switch (invoke) {
            case InvokeNode invokeNode -> invokeNode.setKilledLocationIdentity(locationIdentity);
            case InvokeWithExceptionNode invokeWithExceptionNode -> invokeWithExceptionNode.setKilledLocationIdentity(locationIdentity);
            default -> AnalysisError.shouldNotReachHere("Unsupported invoke type: " + invoke.getClass() + " " + invoke);
        }
    }
}
