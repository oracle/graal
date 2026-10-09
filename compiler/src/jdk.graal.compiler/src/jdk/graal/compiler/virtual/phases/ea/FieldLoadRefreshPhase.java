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
package jdk.graal.compiler.virtual.phases.ea;

import java.util.List;
import java.util.Optional;

import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.virtual.FieldAliasNode;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;

/**
 * Optionally rematerializes immutable field values after invokes to replace a spill/reload pair
 * with a field load. The cached values remain semantically valid across calls; refreshing them is
 * a code-quality optimization, not a correctness requirement.
 *
 * <p>
 * This phase inserts reloads on normal invoke continuations after inlining, before subsequent
 * read elimination. It deliberately targets {@link Invoke} only: macro nodes may become call-free,
 * and foreign-call register effects are outside the scope of this optimization. Missing calls
 * introduced by later lowering is safe, but may leave spill/reload pairs in the generated code.
 *
 * <p>
 * Stores to an aliased field invalidate both mutable and immutable entries in read elimination,
 * regardless of the receiver. This may conservatively lose a cached value when another instance
 * is initialized, but does not restrict which graphs can be compiled.
 *
 * <p>
 * GVN must not process the reloads because their immutable cache identity survives generic memory
 * kills, while read elimination explicitly invalidates the cached values at invokes. Subsequent
 * field accesses can then reuse the inserted reads.
 */
public final class FieldLoadRefreshPhase extends BasePhase<CoreProviders> {
    private final CanonicalizerPhase canonicalizer;

    public FieldLoadRefreshPhase(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.unlessRunBefore(this, StageFlag.HIGH_TIER_LOWERING, graphState);
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        List<FieldAliasNode> aliases = graph.getNodes().filter(FieldAliasNode.class).filter(alias -> ((FieldAliasNode) alias).getLocationIdentity().isImmutable()).snapshot();
        if (aliases.isEmpty()) {
            return;
        }
        ControlFlowGraph cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeDominators(true).build();
        for (Node call : graph.getNodes().filter(node -> node instanceof Invoke).snapshot()) {
            // Native calls can require a thread-state transition before heap reads are safe.
            if (!(((Invoke) call).callTarget() instanceof MethodCallTargetNode)) {
                continue;
            }
            FixedWithNextNode position = call instanceof WithExceptionNode withException ? withException.next() : (FixedWithNextNode) call;
            for (FieldAliasNode alias : aliases) {
                if (dominates(cfg, alias, (FixedNode) call)) {
                    LoadFieldNode load = LoadFieldNode.createImmutableFieldLoad(StampPair.createSingle(alias.getAlias().stamp(NodeView.DEFAULT)), alias.getReceiver(), alias.getField());
                    graph.addAfterFixed(position, graph.add(load));
                }
            }
        }
        // Connect the new reads to their users before canonicalization can remove unused loads.
        new ReadEliminationPhase(canonicalizer).apply(graph, context);
        canonicalizer.apply(graph, context);
    }

    private static boolean dominates(ControlFlowGraph cfg, FieldAliasNode alias, FixedNode node) {
        HIRBlock aliasBlock = cfg.blockFor(alias);
        HIRBlock nodeBlock = cfg.blockFor(node);
        if (aliasBlock != nodeBlock) {
            return aliasBlock.dominates(nodeBlock);
        }
        for (Node predecessor = node; predecessor != nodeBlock.getBeginNode(); predecessor = predecessor.predecessor()) {
            if (predecessor == alias) {
                return true;
            }
        }
        return false;
    }

}
