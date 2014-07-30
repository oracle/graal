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
package com.oracle.graal.phases.common;

import com.oracle.graal.graph.Graph.NodeEventScope;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.util.*;
import com.oracle.graal.phases.tiers.*;

public class CleanTypeProfileProxyPhase extends BasePhase<PhaseContext> {

    private CanonicalizerPhase canonicalizer;

    public CleanTypeProfileProxyPhase(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    @Override
    protected void run(StructuredGraph graph, PhaseContext context) {
        HashSetNodeEventListener listener = new HashSetNodeEventListener();
        try (NodeEventScope s = graph.trackNodeEvents(listener)) {
            for (TypeProfileProxyNode proxy : graph.getNodes(TypeProfileProxyNode.class)) {
                graph.replaceFloating(proxy, proxy.getValue());
            }
        }
        if (!listener.getNodes().isEmpty()) {
            canonicalizer.applyIncremental(graph, context, listener.getNodes());
        }
        assert graph.getNodes(TypeProfileProxyNode.class).count() == 0;
    }
}
