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
package jdk.graal.compiler.hightiercodegen.reconstruction;

import java.util.SortedSet;

import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.CFStackifierSortPhase;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.CatchScopeContainer;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.IfScopeContainer;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.LoopScopeContainer;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.Scope;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.ScopeContainer;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.SwitchScopeContainer;
import org.graalvm.collections.EconomicMap;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.blocks.LabeledBlock;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.blocks.LabeledBlockGeneration;

/**
 * This class contains all the data that is computed by the stackifier algorithm.
 */
public class StackifierData implements ReconstructionData {

    /**
     * Mapping from a basic block to {@link LabeledBlock}s that start before the given basic block.
     */
    private EconomicMap<HIRBlock, SortedSet<LabeledBlock>> labeledBlockStarts;

    /**
     * Mapping from a basic block to a {@link LabeledBlock} that ends before the given basic block.
     *
     * Since labeled blocks are used only for forward jumps, at most one labeled block needs to end
     * before any given basic block.
     */
    private EconomicMap<HIRBlock, LabeledBlock> labeledBlockEnd;

    /**
     * Basic blocks sorted by {@link CFStackifierSortPhase}.
     */
    private HIRBlock[] blocks;

    /**
     * Mapping from a basic block to the innermost enclosing scope.
     */
    private EconomicMap<HIRBlock, Scope> enclosingScope;

    /**
     * Mapping from nodes that start a new scope to the corresponding scope container, e.g. a IfNode
     * maps to a {@link IfScopeContainer}
     */
    private EconomicMap<Node, ScopeContainer> scopes;

    private LabeledBlockGeneration labeledBlockGeneration;

    public SortedSet<LabeledBlock> labeledBlockStarts(HIRBlock start) {
        return labeledBlockStarts.get(start);
    }

    public LabeledBlock labeledBlockEnd(HIRBlock end) {
        return labeledBlockEnd.get(end);
    }

    public int getNrOfLabeledBlocks() {
        return labeledBlockEnd.size();
    }

    public HIRBlock[] getBlocks() {
        return blocks;
    }

    public EconomicMap<HIRBlock, Scope> getEnclosingScope() {
        return enclosingScope;
    }

    public void setEnclosingScope(EconomicMap<HIRBlock, Scope> enclosingScope) {
        this.enclosingScope = enclosingScope;
    }

    public void setLabeledBlockStarts(EconomicMap<HIRBlock, SortedSet<LabeledBlock>> labeledBlockStarts) {
        this.labeledBlockStarts = labeledBlockStarts;
    }

    public void setLabeledBlockEnd(EconomicMap<HIRBlock, LabeledBlock> labeledBlockEnds) {
        this.labeledBlockEnd = labeledBlockEnds;
    }

    public void setSortedBlocks(HIRBlock[] sortedBlocks) {
        this.blocks = sortedBlocks;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        final String separator = "-----------------------------\n";
        final String indent = " ";
        sb.append("Stackifier data\n");
        sb.append(separator);
        StringBuilder indentSb = new StringBuilder();

        sb.append(blocks.length).append(" basic blocks in total\n").append(separator);
        sb.append("Labeled blocks\n").append(separator);
        for (HIRBlock b : blocks) {
            if (labeledBlockEnd(b) != null) {
                indentSb.replace(0, 1, "");
                LabeledBlock labeledBlock = labeledBlockEnd(b);
                sb.append(indentSb).append("}").append(labeledBlock.getLabel()).append(" [").append(labeledBlock.getEnd()).append("]\n");
            }
            if (labeledBlockStarts(b) != null) {
                for (LabeledBlock labeledBlock : labeledBlockStarts(b)) {
                    sb.append(indentSb).append(labeledBlock.getLabel()).append(" [").append(labeledBlock.getStart()).append("] {\n");
                    indentSb.append(indent);
                }
            }
        }
        sb.append(separator).append("Scopes\n").append(separator);
        for (HIRBlock b : blocks) {
            if (b.isLoopHeader()) {
                sb.append("Loop ").append(((LoopScopeContainer) scopes.get(b.getBeginNode())).getLoopScope()).append("\n");
            }
            ScopeContainer scopeContainer = scopes.get(b.getEndNode());
            if (scopeContainer != null) {
                if (scopeContainer instanceof IfScopeContainer) {
                    IfScopeContainer ifScopeContainer = (IfScopeContainer) scopeContainer;
                    if (ifScopeContainer.getThenScope() != null) {
                        sb.append("Then ").append(ifScopeContainer.getThenScope()).append("\n");
                    }
                    if (ifScopeContainer.getElseScope() != null) {
                        sb.append("Else ").append(ifScopeContainer.getElseScope()).append("\n");
                    }
                } else if (scopeContainer instanceof CatchScopeContainer) {
                    CatchScopeContainer catchScopeContainer = (CatchScopeContainer) scopeContainer;
                    if (catchScopeContainer.getCatchScope() != null) {
                        sb.append("Catch ").append(((CatchScopeContainer) scopeContainer).getCatchScope()).append("\n");
                    }
                } else if (scopeContainer instanceof SwitchScopeContainer) {
                    SwitchScopeContainer switchScopeContainer = (SwitchScopeContainer) scopeContainer;
                    Scope[] caseScopes = switchScopeContainer.getCaseScopes();
                    for (Scope scope : caseScopes) {
                        if (scope != null) {
                            sb.append("Case ").append(scope).append("\n");
                        }
                    }
                }
            }
        }
        sb.append(separator).append("Innermost enclosing scopes\n").append(separator);
        for (HIRBlock b : enclosingScope.getKeys()) {
            sb.append(b).append(" -> ").append(enclosingScope.get(b)).append("\n");
        }
        sb.append(separator);
        return sb.toString();
    }

    public void setScopeEntries(EconomicMap<Node, ScopeContainer> scopes) {
        this.scopes = scopes;
    }

    public ScopeContainer getScopeEntry(Node node) {
        return scopes.get(node);
    }

    public int getNrThenScopes() {
        int sum = 0;
        for (ScopeContainer scopeContainer : scopes.getValues()) {
            if (scopeContainer instanceof IfScopeContainer && ((IfScopeContainer) scopeContainer).getThenScope() != null) {
                sum++;
            }
        }
        return sum;
    }

    public int getNrElseScopes() {
        int sum = 0;
        for (ScopeContainer scopeContainer : scopes.getValues()) {
            if (scopeContainer instanceof IfScopeContainer && ((IfScopeContainer) scopeContainer).getElseScope() != null) {
                sum++;
            }
        }
        return sum;
    }

    public int getNrLoopScopes() {
        int sum = 0;
        for (ScopeContainer scopeContainer : scopes.getValues()) {
            if (scopeContainer instanceof LoopScopeContainer) {
                sum++;
            }
        }
        return sum;
    }

    public int getNrCatchScopes() {
        int sum = 0;
        for (ScopeContainer scopeContainer : scopes.getValues()) {
            if (scopeContainer instanceof CatchScopeContainer && ((CatchScopeContainer) scopeContainer).getCatchScope() != null) {
                sum++;
            }
        }
        return sum;
    }

    public LabeledBlockGeneration getLabeledBlockGeneration() {
        return labeledBlockGeneration;
    }

    public void setLabeledBlockGeneration(LabeledBlockGeneration labeledBlockGeneration) {
        this.labeledBlockGeneration = labeledBlockGeneration;
    }
}
