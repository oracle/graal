/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.compiler.phases.CanonicalizerPhase.IsImmutablePredicate;
import com.oracle.graal.compiler.util.*;
import com.oracle.graal.cri.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.ri.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;

public class LowerCheckCastPhase extends Phase {
    private final GraalRuntime runtime;
    private final RiResolvedMethod checkcast;

    public LowerCheckCastPhase(GraalRuntime runtime) {
        this.runtime = runtime;
        try {
            checkcast = runtime.getRiMethod(CheckCastSnippets.class.getDeclaredMethod("checkcast", Object.class, Object.class, Object[].class, boolean.class));
        } catch (NoSuchMethodException e) {
            throw new GraalInternalError(e);
        }
    }

    private static HotSpotKlassOop klassOop(RiResolvedType resolvedType) {
        return ((HotSpotType) resolvedType).klassOop();
    }

    @Override
    protected void run(StructuredGraph graph) {
        // TODO (dnsimon) remove this once lowering works in general
        if (graph.method() == null || !graph.method().holder().name().contains("LowerCheckCastTest")) {
            return;
        }

        int hits = 0;
        graph.mark();
        IsImmutablePredicate immutabilityPredicate = null;
        for (CheckCastNode ccn : graph.getNodes(CheckCastNode.class)) {
            ValueNode hub = ccn.targetClassInstruction();
            ValueNode object = ccn.object();
            RiResolvedType[] hints = ccn.hints();
            StructuredGraph snippetGraph = (StructuredGraph) checkcast.compilerStorage().get(Graph.class);
            hits++;
            HotSpotKlassOop[] hintHubs = new HotSpotKlassOop[hints.length];
            for (int i = 0; i < hintHubs.length; i++) {
                hintHubs[i] = klassOop(hints[i]);
            }
            final CiConstant hintHubsConst = CiConstant.forObject(hintHubs);
            immutabilityPredicate = new IsImmutablePredicate() {
                public boolean apply(CiConstant constant) {
                    return constant == hintHubsConst;
                }
            };
            Debug.log("Lowering checkcast in %s: ccn=%s, hintsHubs=%s, exact=%b", graph, ccn, Arrays.toString(hints), ccn.hintsExact());
            InliningUtil.snippetInline(runtime, ccn, ccn.anchor(), snippetGraph, true, hub, object, hintHubsConst, CiConstant.forBoolean(ccn.hintsExact()));
        }
        if (hits != 0) {
            Debug.log("Lowered %d checkcasts in %s ", hits, graph);
            new DeadCodeEliminationPhase().apply(graph);
            new CanonicalizerPhase(null, runtime, null, false, immutabilityPredicate).apply(graph);
        }
    }
}
