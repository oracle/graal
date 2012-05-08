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

/**
 * Lowers a {@link CheckCastNode} by replacing it with the graph of a {@linkplain CheckCastSnippets checkcast snippet}.
 */
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

    @Override
    protected void run(StructuredGraph graph) {
        final Map<CiConstant, CiConstant> hintHubsSet = new IdentityHashMap<>();
        IsImmutablePredicate immutabilityPredicate = new IsImmutablePredicate() {
            public boolean apply(CiConstant constant) {
                return hintHubsSet.containsKey(constant);
            }
        };
        for (CheckCastNode node : graph.getNodes(CheckCastNode.class)) {
            ValueNode hub = node.targetClassInstruction();
            ValueNode object = node.object();
            RiResolvedType[] hints = node.hints();
            StructuredGraph snippetGraph = (StructuredGraph) checkcast.compilerStorage().get(Graph.class);
            assert snippetGraph != null : CheckCastSnippets.class.getSimpleName() + " should be installed";
            HotSpotKlassOop[] hintHubs = new HotSpotKlassOop[hints.length];
            for (int i = 0; i < hintHubs.length; i++) {
                hintHubs[i] = ((HotSpotType) hints[i]).klassOop();
            }
            assert !node.hintsExact() || hints.length > 0 : "cannot have 0 exact hints!";
            final CiConstant hintHubsConst = CiConstant.forObject(hintHubs);
            hintHubsSet.put(hintHubsConst, hintHubsConst);
            Debug.log("Lowering checkcast in %s: node=%s, hintsHubs=%s, exact=%b", graph, node, Arrays.toString(hints), node.hintsExact());

            InliningUtil.inlineSnippet(runtime, node, (FixedWithNextNode) node.anchor(), snippetGraph, true, immutabilityPredicate, hub, object, hintHubsConst, CiConstant.forBoolean(node.hintsExact()));
        }
        if (!hintHubsSet.isEmpty()) {
            Debug.log("Lowered %d checkcasts in %s ", hintHubsSet.size(), graph);
            new DeadCodeEliminationPhase().apply(graph);
            new CanonicalizerPhase(null, runtime, null, false, immutabilityPredicate).apply(graph);
        }
    }
}
