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
package com.oracle.graal.java.decompiler.block;

import java.io.*;
import java.util.*;

import com.oracle.graal.graph.Node.Verbosity;
import com.oracle.graal.java.decompiler.*;
import com.oracle.graal.java.decompiler.lines.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.phases.schedule.*;

public abstract class DecompilerBlock {

    protected final Block block;
    protected final Decompiler decompiler;
    protected final List<DecompilerSyntaxLine> code;

    public DecompilerBlock(Block block, Decompiler decompiler) {
        this.block = block;
        this.decompiler = decompiler;
        this.code = new ArrayList<>();
    }

    public Block getBlock() {
        return block;
    }

    public abstract void printBlock(PrintStream stream, Block codeSuccessor);

    public final int getPredecessorCount() {
        return block.getPredecessorCount();
    }

    public abstract int getSuccessorCount();

    public abstract boolean contains(Block b);

    public abstract List<DecompilerBasicBlock> getAllBasicBlocks();

    @Override
    public abstract String toString();

    protected void initCode(SchedulePhase schedule) {
        List<ScheduledNode> instructions = schedule.nodesFor(block);
        for (ScheduledNode n : instructions) {
            if (n instanceof MergeNode) {
                MergeNode merge = (MergeNode) n;
                for (PhiNode phi : merge.phis()) {
                    addLine(new DecompilerPhiLine(this, phi));
                }
            } else if (n instanceof StartNode || n instanceof BeginNode || n instanceof EndNode || n instanceof LoopEndNode || n instanceof LoopExitNode) {
                // do nothing
            } else if (n instanceof ConstantNode) {
                // do nothing
            } else if (n instanceof IfNode) {
                assert n.inputs().count() == 1;
                addLine(new DecompilerIfLine(this, n, ((IfNode) n).condition()));
            } else if (n instanceof ReturnNode) {
                addLine(new DecompilerReturnLine(this, n.inputs().first()));
            } else if (n instanceof ControlSplitNode) {
                addLine(new DecompilerControlSplitLine(this, n));
            } else if (n instanceof ProxyNode) {
                ProxyNode proxy = (ProxyNode) n;
                addLine(new DecompilerProxyLine(this, proxy, proxy.value()));
            } else if (n instanceof ValueNode) {
                addLine(new DecompilerAssignmentLine(this, n));
            } else {
                throw new IllegalStateException(n.toString(Verbosity.All) + " " + n.getClass());
            }
        }
        simplifyCode();
    }

    private void simplifyCode() {
        for (int i = 0; i < code.size(); i++) {
            if (code.get(i) instanceof DecompilerAssignmentLine) {
                if (i + 1 < code.size()) {
                    if (code.get(i + 1) instanceof DecompilerIfLine) {
                        if (code.get(i).getNode().usages().count() == 1) {
                            if (((DecompilerIfLine) code.get(i + 1)).getCondition() == code.get(i).getNode()) {
                                ((DecompilerIfLine) code.get(i + 1)).setMergedCondition((DecompilerAssignmentLine) code.get(i));
                                code.set(i, null);
                            }
                        }
                    }
                }
            }
        }
    }

    protected void addLine(DecompilerSyntaxLine line) {
        code.add(line);
    }
}
