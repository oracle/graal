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
package com.oracle.max.graal.compiler.phases;

import java.text.*;
import java.util.*;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.graph.collections.*;


public class RematerializationPhase extends Phase {

    private NodeMap<Block> nodeToBlock;
    private HashMap<Node, Block> newNodesToBlock;
    private List<Block> blocks;
    private UsageProbability[] probablityCache;
    private boolean ignoreUsages;

    @Override
    protected void run(Graph graph) {
        Iterable<Node> modifiedNodes = graph.getModifiedNodes();
        graph.stopRecordModifications();
        NodeWorkList work = graph.createNodeWorkList();
        for (Node modified : modifiedNodes) {
            if (modified instanceof FloatingNode) {
                work.add(modified);
            }
        }

        if (work.isEmpty()) {
            return;
        }

        final IdentifyBlocksPhase s = new IdentifyBlocksPhase(true);
        s.apply(graph);

        newNodesToBlock = new HashMap<Node, Block>();
        nodeToBlock = s.getNodeToBlock();
        blocks = s.getBlocks();
        probablityCache = new UsageProbability[blocks.size()];

        for (Node node : work) {
            if (node instanceof Phi || node instanceof Local || node instanceof Constant || node instanceof LocationNode) {
                continue;
            }
            boolean delay = false;
            for (Node usage : node.usages()) {
                if (usage instanceof FloatingNode && !(usage instanceof Phi) && work.isInQueue(usage)) {
                    delay = true;
                    break;
                }
            }
            if (delay) {
                work.addAgain(node);
                continue;
            }
            Arrays.fill(probablityCache, null);
            ignoreUsages = true;
            Block block = nodeToBlock.get(node);
            if (block == null) {
                continue;
            }
            UsageProbability usageProbability = usageProbability(node, block);
            if (usageProbability.probability < GraalOptions.MinimumUsageProbability) {
                if (ignoreUsages) {
                    ignoreUsages = false;
                    Arrays.fill(probablityCache, null);
                    usageProbability = usageProbability(node, block); // recompute with usage maps
                }
                //TTY.println("going to remarterialize " + node + " at " + block + " : " + toString(usageProbability));
                boolean first = true;
                for (Block sux : block.getSuccessors()) {
                    if (first) {
                        first = false;
                        continue;
                    }
                    usageProbability = usageProbability(node, sux);
                    List<Node> usages = new LinkedList<Node>();
                    for (Node usage : usageProbability.usages) {
                        usages.add(usage);
                    }
                    if (!usages.isEmpty()) {
                        Node copy = node.copyWithEdges();
                        newNodesToBlock.put(copy, sux);
                        GraalMetrics.Rematerializations++;
                        //TTY.println("> Rematerialized " + node + " : " + toString(usageProbability));
                        for (Node usage : usages) {
                            usage.inputs().replace(node, copy);
                            if (usageProbability.phiUsages != null) {
                                Set<Phi> phis = usageProbability.phiUsages.get(usage);
                                if (phis != null) {
                                    for (Phi phi : phis) {
                                        int index = phi.merge().phiPredecessorIndex(usage);
                                        assert phi.valueAt(index) == node;
                                        phi.setValueAt(index, (Value) copy);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private UsageProbability usageProbability(Node n, Block b) {
        UsageProbability cached = probablityCache[b.blockID()];
        if (cached != null) {
            return cached;
        }
        if (ignoreUsages) {
            GraalMetrics.PartialUsageProbability++;
        } else {
            GraalMetrics.FullUsageProbability++;
        }
        for (Node usage : n.usages()) {
            if (usage instanceof Phi) {
                Phi phi = (Phi) usage;
                Merge merge = phi.merge();
                for (int i = 0; i < phi.valueCount(); i++) {
                    if (phi.valueAt(i) == n) {
                        insertUsageInCache(merge.phiPredecessorAt(i), phi);
                    }
                }
            } else {
                insertUsageInCache(usage);
            }
        }
        return usageProbability0(n, b);
    }

    private void insertUsageInCache(Node usage) {
        insertUsageInCache(usage, null);
    }

    private void insertUsageInCache(Node usage, Phi phi) {
        Block block = block(usage);
        if (block == null) {
            return;
        }
        int blockID = block.blockID();
        UsageProbability usageProbability = probablityCache[blockID];
        if (usageProbability == null) {
            usageProbability = new UsageProbability(usage);
            probablityCache[blockID] = usageProbability;
        } else if (!ignoreUsages) {
            usageProbability.usages.mark(usage);
        }
        if (phi != null) {
            usageProbability.addPhiUsage(phi, usage);
        }
    }

    private Block block(Node node) {
        Block block;
        if (!nodeToBlock.isNew(node)) {
            block = nodeToBlock.get(node);
        } else {
            block = newNodesToBlock.get(node);
            assert block != null;
        }
        return block;
    }

    private UsageProbability usageProbability0(Node n, Block b) {
        //System.out.println("usageProbability0(" + n.id() + ", " + b + ")");
        UsageProbability cached = probablityCache[b.blockID()];
        if (cached != null && (cached.computed || ignoreUsages)) {
            return cached;
        }
        UsageProbability result = cached;
        if (result == null) {
            result = new UsageProbability(n.graph());
        }
        if (b.getSuccessors().size() > 0) {
            if (b.isLoopEnd()) {
                Block loopHeader = b.getSuccessors().get(0);
                assert loopHeader.isLoopHeader();
                UsageProbability headerUsages = probablityCache[loopHeader.blockID()];
                if (headerUsages != null) {
                    result.merge(headerUsages, 1.0);
                }
            } else if (b.getSuccessors().size() == 1) {
                result.merge(usageProbability0(n, b.getSuccessors().get(0)), 1.0);
            } else {
                Node lastNode = b.lastNode();
                if (lastNode instanceof Invoke) {
                    result.merge(usageProbability0(n, nodeToBlock.get(((Invoke) lastNode).next())), 1.0);
                    result.merge(usageProbability0(n, nodeToBlock.get(((Invoke) lastNode).exceptionEdge())), 0.0);
                } else if (lastNode instanceof ControlSplit) {
                    ControlSplit split = (ControlSplit) lastNode;
                    for (int i = 0; i < split.blockSuccessorCount(); i++) {
                        result.merge(usageProbability0(n, nodeToBlock.get(split.blockSuccessor(i))), split.probability(i));
                    }
                } else {
                    throw Util.shouldNotReachHere();
                }
            }
        }
        probablityCache[b.blockID()] = result;
        result.computed = true;
        return result;
    }

    private class UsageProbability {
        double probability;
        NodeBitMap usages;
        NodeMap<Set<Phi>> phiUsages;
        boolean computed;

        public UsageProbability(Node usage) {
            if (!ignoreUsages) {
                usages = usage.graph().createNodeBitMap();
                usages.mark(usage);
            }
            probability = 1.0;
        }

        public UsageProbability(Graph graph) {
            if (!ignoreUsages) {
                usages = graph.createNodeBitMap();
            }
            probability = 0.0;
        }

        public void merge(UsageProbability sux, double suxProbability) {
            if (!ignoreUsages) {
                usages.setUnion(sux.usages);
            }
            probability += suxProbability * sux.probability;
        }

        public void addPhiUsage(Phi phi, Node usage) {
            if (phiUsages == null) {
                phiUsages = phi.graph().createNodeMap();
            }
            Set<Phi> phis = phiUsages.get(usage);
            if (phis == null) {
                phis = new HashSet<Phi>(2);
                phiUsages.set(usage, phis);
            }
            phis.add(phi);
        }
    }

    private String toString(UsageProbability up) {
        NumberFormat nf = NumberFormat.getPercentInstance();
        StringBuilder sb = new StringBuilder("p=");
        sb.append(nf.format(up.probability));
        if (up.usages != null) {
            sb.append(" U=[");
            for (Node n : up.usages) {
                sb.append(n);
                sb.append(", ");
            }
            sb.append("]");
        }
        return sb.toString();
    }
}

