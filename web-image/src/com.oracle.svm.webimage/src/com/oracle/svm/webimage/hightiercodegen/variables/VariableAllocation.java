/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.hightiercodegen.variables;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.hightiercodegen.CodeGenTool;
import jdk.graal.compiler.hightiercodegen.NodeLowerer;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

/**
 * Performs variable allocation on SSA form.
 * <p>
 * It stores the allocation result in a {@link VariableMap}. The SSA graph stays unchanged. The
 * lowering takes advantage of the variable map to generate code.
 * <p>
 * We need to abstract when we want to keep several allocation strategies.
 * <p>
 * Whether to create a variable or to inline is tightly coupled to how code is generated for a node,
 * especially when it comes to correctness.
 */
public abstract class VariableAllocation {

    /**
     * Encodes when it is safe to inline a node.
     * <p>
     * Inlining means that the node is materialized at all its usages instead of where it was
     * scheduled. If a node is not inlined, it is first stored in a variable and its usages read
     * that variable.
     */
    protected enum SafetyPolicy {
        /**
         * It is always safe to inline the node.
         */
        Always,
        /**
         * It is safe to inline the node if it has a single usage.
         */
        SingleUsage,
        /**
         * It is safe to inline the node if it is a {@link FixedWithNextNode} and its only usage is
         * at its (only) successor.
         *
         * @see #getEffectiveUsages(ValueNode, CodeGenTool)
         */
        SingleUsageAtNext,
        /**
         * It is safe to inline the node if all its usages are in the same block as the node and the
         * block does not jump to a merge node.
         * <p>
         * This guarantees that no phi-scheduling happens in the block.
         *
         * @see #getBlockUsage(ValueNode, Node, NodeMap)
         */
        InBlockNoMerge,
        /**
         * It is never safe to inline the node.
         */
        Never
    }

    /**
     * Uses a simple algorithm for variable allocation.
     */
    public VariableMap compute(StructuredGraph g, CodeGenTool codeGenTool) {
        VariableMap varMap = new VariableMap();
        for (ValueNode node : g.getNodes().filter(ValueNode.class)) {
            if (needsVariable(node, codeGenTool)) {
                varMap.allocate(node);
            }
        }

        return varMap;
    }

    /**
     * Determines whether the give node's usages satisfy the given {@link SafetyPolicy}.
     */
    protected boolean isSafeToInline(ValueNode node, SafetyPolicy policy, CodeGenTool codeGenTool) {
        List<Node> effectiveUsages = getEffectiveUsages(node, codeGenTool);
        int numUsages = effectiveUsages.size();
        switch (policy) {
            case Always:
                return true;
            case SingleUsage:
                return numUsages <= 1;
            case SingleUsageAtNext:
                if (numUsages == 1 && node instanceof FixedWithNextNode fixed) {
                    FixedNode next = fixed.next();
                    return effectiveUsages.get(0) == next;
                } else {
                    return false;
                }
            case InBlockNoMerge:
                NodeMap<HIRBlock> nodeToBlockMap = node.graph().getLastSchedule().getNodeToBlockMap();
                HIRBlock thisNodeBlock = nodeToBlockMap.get(node);
                // Not safe if any of the usages are in a different block.
                if (!effectiveUsages.stream().allMatch(usage -> getBlockUsage(node, usage, nodeToBlockMap) == thisNodeBlock)) {
                    return false;
                }

                /*
                 * Unscheduled nodes such as ValueProxies are not in the nodeToBlockMap, so for
                 * safety in this case we disable inlining.
                 */
                if (thisNodeBlock == null) {
                    return false;
                }

                // Only safe if none of the block successors are merges.
                for (int i = 0; i < thisNodeBlock.getSuccessorCount(); i++) {
                    AbstractBeginNode b = thisNodeBlock.getSuccessorAt(i).getBeginNode();
                    if (b instanceof AbstractMergeNode) {
                        return false;
                    }
                }
                return true;
            case Never:
                return false;
            default:
                throw GraalError.shouldNotReachHere(policy.toString()); // ExcludeFromJacocoGeneratedReport
        }
    }

    /**
     * Determines in which block the usage happens if a node is used by the given usage.
     * <p>
     * In general this is the block the usage is in, but for {@link ValuePhiNode} it is the block
     * where the node is written into the phi node (where the end node jumps to the phi node's
     * merge).
     */
    protected HIRBlock getBlockUsage(ValueNode node, Node usage, NodeMap<HIRBlock> nodeToBlockMap) {
        HIRBlock block = nodeToBlockMap.get(usage);
        if (usage instanceof ValuePhiNode) {
            ValuePhiNode phi = (ValuePhiNode) usage;
            AbstractMergeNode merge = phi.merge();
            NodeInputList<ValueNode> values = phi.values();
            for (int i = 0; i < values.size(); i++) {
                if (values.get(i) == node) {
                    return nodeToBlockMap.get(merge.phiPredecessorAt(i));
                }
            }
            throw GraalError.shouldNotReachHere("Could not find end node for " + node + " and " + usage); // ExcludeFromJacocoGeneratedReport
        } else {
            return block;
        }
    }

    /**
     * Return a list of {@linkplain SafetyPolicy policies} under which a node can be safely inlined
     * at all usages.
     * <p>
     * All policies must be fulfilled for it to be safe.
     * <p>
     * Nodes that are lowered to more than a value but also to some extra initialization code must
     * never be inlined. Nodes with side effects are only safe to inline if they have a single
     * usage.
     */
    public abstract Collection<SafetyPolicy> getSafeInliningPolicies(ValueNode node, CodeGenTool codeGenTool);

    /**
     * Given a node that is safe to inline at all its usages, determine whether it should be inlined
     * at all usages.
     */
    protected abstract boolean shouldInline(ValueNode node, int numUsages, CodeGenTool codeGenTool);

    /**
     * Determines whether the given node should be stored in a variable based on whether it can be
     * safely inlined and whether it should be inlined.
     */
    public boolean needsVariable(ValueNode node, CodeGenTool codeGenTool) {
        if (!codeGenTool.nodeLowerer().isActiveValueNode(node)) {
            return false;
        }

        Collection<SafetyPolicy> policies = getSafeInliningPolicies(node, codeGenTool);

        assert !policies.isEmpty();

        // A node must satisfy all policies to not need a variable.
        if (!policies.stream().allMatch(policy -> isSafeToInline(node, policy, codeGenTool))) {
            return true;
        }

        return !shouldInline(node, getEffectiveUsages(node, codeGenTool).size(), codeGenTool);
    }

    /**
     * Returns the list of nodes where the given node is effectively used.
     * <p>
     * Uses {@link NodeLowerer#actualUsages(ValueNode)} to determine usages. In addition, if the
     * usage is a {@link CallTargetNode}, the effective usages are the {@linkplain Invoke invokes}
     * where the {@link CallTargetNode} is used to allow inlining arguments into {@linkplain Invoke
     * invokes}.
     */
    public static List<Node> getEffectiveUsages(ValueNode node, CodeGenTool codeGenTool) {
        List<Node> usages = new ArrayList<>(node.getUsageCount());

        for (Node usage : codeGenTool.nodeLowerer().actualUsages(node)) {
            /*
             * If the node is used by a call target node, the effective usage are the invokes that
             * use that call target node.
             */
            if (usage instanceof CallTargetNode) {
                CallTargetNode callTarget = (CallTargetNode) usage;

                for (Node callTargetUsage : codeGenTool.nodeLowerer().actualUsages(callTarget)) {
                    if (callTargetUsage instanceof Invoke) {
                        usages.add(callTargetUsage);
                    }
                }
            } else {
                usages.add(usage);
            }
        }

        return usages;
    }
}
