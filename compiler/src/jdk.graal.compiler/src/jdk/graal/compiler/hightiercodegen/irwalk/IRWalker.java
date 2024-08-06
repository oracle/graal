/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hightiercodegen.irwalk;

import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Scope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.hightiercodegen.CodeGenTool;
import jdk.graal.compiler.hightiercodegen.lowerer.PhiResolveLowerer;
import jdk.graal.compiler.hightiercodegen.reconstruction.ReconstructionData;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

/**
 * Abstract interface for generating textual control flow in code from a {@link ControlFlowGraph}s.
 */
@SuppressWarnings("try")
public abstract class IRWalker {

    protected final CodeGenTool codeGenTool;
    protected final ControlFlowGraph cfg;
    protected final BlockMap<List<Node>> blockToNodeMap;
    protected final NodeMap<HIRBlock> nodeToBlockMap;
    protected final IRWalkVerifier verifier;
    protected final BlockVisitHistory blockHistory;
    protected final ReconstructionData reconstructionData;

    public IRWalker(CodeGenTool codeGenTool, ControlFlowGraph cfg, BlockMap<List<Node>> blockToNodeMap, NodeMap<HIRBlock> nodeToBlockMap, ReconstructionData reconstructionData) {
        this.codeGenTool = codeGenTool;
        this.cfg = cfg;
        this.reconstructionData = reconstructionData;
        this.blockHistory = new BlockVisitHistory();
        this.blockToNodeMap = blockToNodeMap;
        this.nodeToBlockMap = nodeToBlockMap;
        // used for verification
        List<Node> nodes = new ArrayList<>();
        for (HIRBlock b : cfg.getBlocks()) {
            nodes.addAll(blockToNodeMap.get(b));
        }
        verifier = new IRWalkVerifier(nodes);
    }

    public void lowerFunction(DebugContext debugContext) {
        try (Scope s = debugContext.scope("Code Flow Verification")) {
            lower(debugContext);
            verifier.verify(cfg.graph, codeGenTool, reconstructionData);
        } catch (Throwable t) {
            throw debugContext.handle(t);
        }
    }

    protected abstract void lower(DebugContext debugContext);

    protected void lowerLoopEndResolver(LoopEndNode node) {
        new PhiResolveLowerer(node).lower(codeGenTool);
    }

    /**
     * Lowers a node if it should be issued.
     *
     * @return true iff node was lowered
     */
    protected boolean lowerNode(Node n) {
        if (codeGenTool.nodeLowerer().isTopLevelStatement(n)) {
            codeGenTool.lowerStatement(n);
            return true;
        } else {
            return false;
        }
    }
}
