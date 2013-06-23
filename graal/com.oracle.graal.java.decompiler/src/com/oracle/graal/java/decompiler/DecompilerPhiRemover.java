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
package com.oracle.graal.java.decompiler;

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.java.decompiler.block.*;
import com.oracle.graal.java.decompiler.lines.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;

public class DecompilerPhiRemover {

    public static void apply(ControlFlowGraph cfg, List<DecompilerBlock> decompilerBlocks) {
        List<DecompilerBasicBlock> blocks = collectAllBasicBlocks(decompilerBlocks);
        for (DecompilerBasicBlock b : blocks) {
            List<DecompilerSyntaxLine> removedLines = new ArrayList<>();
            Map<DecompilerSyntaxLine, DecompilerBasicBlock> addedLines = new HashMap<>();
            for (DecompilerSyntaxLine l : b.getCode()) {
                if (l instanceof DecompilerPhiLine) {
                    DecompilerPhiLine phi = (DecompilerPhiLine) l;
                    removedLines.add(phi);
                    PhiNode phiNode = (PhiNode) phi.getNode();
                    for (int i = 0; i < phiNode.merge().phiPredecessorCount(); i++) {
                        Node n = phiNode.merge().phiPredecessorAt(i);
                        DecompilerBasicBlock targetBlock = getBlock(n, cfg, blocks);
                        DecompilerPhiResolveLine assignment = new DecompilerPhiResolveLine(targetBlock, phiNode, n);
                        addedLines.put(assignment, targetBlock);
                    }
                }
            }
            for (DecompilerSyntaxLine l : addedLines.keySet()) {
                addAssignment(addedLines.get(l), l);
            }
            b.getCode().removeAll(removedLines);
        }
    }

    private static List<DecompilerBasicBlock> collectAllBasicBlocks(List<DecompilerBlock> blocks) {
        List<DecompilerBasicBlock> allBasicBlocks = new ArrayList<>();
        for (DecompilerBlock b : blocks) {
            allBasicBlocks.addAll(b.getAllBasicBlocks());
        }
        return allBasicBlocks;
    }

    private static DecompilerBasicBlock getBlock(Node n, ControlFlowGraph cfg, List<DecompilerBasicBlock> blocks) {
        Block b = cfg.blockFor(n);
        for (DecompilerBasicBlock basicBlock : blocks) {
            if (basicBlock.getBlock() == b) {
                return basicBlock;
            }
        }
        throw new IllegalStateException("Block not found");
    }

    private static void addAssignment(DecompilerBasicBlock block, DecompilerSyntaxLine line) {
        if (block.getCode().isEmpty()) {
            block.getCode().add(line);
            return;
        }
        DecompilerSyntaxLine lastLine = block.getCode().get(block.getCode().size() - 1);
        if (lastLine instanceof DecompilerIfLine || lastLine instanceof DecompilerControlSplitLine) {
            block.getCode().remove(lastLine);
            block.getCode().add(line);
            block.getCode().add(lastLine);
        } else {
            block.getCode().add(line);
        }
    }
}
