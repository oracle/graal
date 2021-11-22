/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common.util;

import java.util.EnumSet;

import org.graalvm.compiler.graph.Graph.NodeEvent;
import org.graalvm.compiler.graph.Graph.NodeEventScope;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.loop.LoopEx;
import org.graalvm.compiler.nodes.loop.LoopsData;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;

public class LoopUtility {

    /**
     * Remove loop proxies that became obsolete over time, i.e., they proxy a value that already
     * flowed out of a loop and dominates the loop now.
     *
     * @param canonicalizer must not be {@code null}, will be applied incrementally to nodes whose
     *            inputs changed
     */
    @SuppressWarnings("try")
    public static void removeObsoleteProxies(StructuredGraph graph, CoreProviders context, CanonicalizerPhase canonicalizer) {
        LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
        final EconomicSetNodeEventListener inputChanges = new EconomicSetNodeEventListener(EnumSet.of(NodeEvent.INPUT_CHANGED));
        try (NodeEventScope s = graph.trackNodeEvents(inputChanges)) {
            for (LoopEx loop : loopsData.loops()) {
                removeObsoleteProxiesForLoop(loop);
            }
        }
        canonicalizer.applyIncremental(graph, context, inputChanges.getNodes());
    }

    /**
     * Remove obsolete proxies from one loop only. Unlike
     * {@link #removeObsoleteProxies(StructuredGraph, CoreProviders, CanonicalizerPhase)}, this does
     * not apply canonicalization.
     */
    public static void removeObsoleteProxiesForLoop(LoopEx loop) {
        for (LoopExitNode lex : loop.loopBegin().loopExits()) {
            for (ProxyNode proxy : lex.proxies().snapshot()) {
                if (loop.isOutsideLoop(proxy.value())) {
                    proxy.replaceAtUsagesAndDelete(proxy.getOriginalNode());
                }
            }
        }
    }
}
