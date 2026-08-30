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
package com.oracle.svm.hosted.analysis.tesa;

import org.graalvm.word.LocationIdentity;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.tesa.effect.LocationEffect;
import com.oracle.svm.hosted.meta.HostedUniverse;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.ForeignCall;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;

/**
 * TESA instance that computes the locations accessed by methods. Since {@link MemoryAccess}
 * includes writes that depend on the previous memory state, the result conservatively
 * over-approximates the locations read by a method.
 */
public class AccessedLocationTesa extends AbstractTesa<LocationEffect> {

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
            assert !(node instanceof SafepointNode) : AccessedLocationTesa.class.getSimpleName() + " does not support safepoints.";
            if (!isSupportedNode(node)) {
                return anyEffect();
            }
            if (shouldSkipNode(node)) {
                continue;
            }
            if (node instanceof ForeignCall || node instanceof StateSplit stateSplit && stateSplit.hasSideEffect() && !(node instanceof MemoryAccess)) {
                return anyEffect();
            }
            if (node instanceof MemoryAccess access) {
                LocationIdentity identity = access.getLocationIdentity();
                if (identity.isImmutable()) {
                    continue;
                }
                if (identity.isAny()) {
                    return anyEffect();
                }
                location = location.combineEffects(LocationEffect.singleLocation(identity));
                if (location.isAnyEffect()) {
                    return anyEffect();
                }
            }
        }
        return location;
    }

    @Override
    protected void optimizeInvoke(HostedUniverse universe, StructuredGraph graph, Invoke invoke, LocationEffect targetState) {
        switch (targetState) {
            case LocationEffect.Empty _ -> setLocationIdentity(invoke, MemoryKill.NO_LOCATION);
            case LocationEffect.Single single -> setLocationIdentity(invoke, KilledLocationTesa.transplantIdentity(universe, single.location));
            default -> AnalysisError.shouldNotReachHere(targetState + " is not actionable.");
        }
    }

    /**
     * Sets the location accessed by the given invoke.
     */
    private static void setLocationIdentity(Invoke invoke, LocationIdentity locationIdentity) {
        switch (invoke) {
            case InvokeNode invokeNode -> invokeNode.setLocationIdentity(locationIdentity);
            case InvokeWithExceptionNode invokeWithExceptionNode -> invokeWithExceptionNode.setLocationIdentity(locationIdentity);
            default -> AnalysisError.shouldNotReachHere("Unsupported invoke type: " + invoke.getClass() + " " + invoke);
        }
    }
}
