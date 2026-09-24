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

package jdk.graal.compiler.phases.common.writesinking.writesink;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_4;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_8;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.ProfileData;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;

/**
 * Temporary low-tier write used when write sinking moves a loop write to an exit that is reachable
 * even when the loop executes zero iterations.
 * <p>
 * The node represents "perform this write only if the loop body executed". It is created after
 * reads are fixed and before write barriers are inserted, then the following canonicalizer lowers it
 * to an ordinary if diamond. A cascade of conditional writes sharing the same condition is lowered
 * with a single diamond, preserving the order of the writes on the taken branch.
 *
 * <pre>
 *          |
 *     ConditionalWrite
 *          |
 *     ConditionalWrite
 *          |
 * </pre>
 *
 * gets transformed into
 *
 * <pre>
 *     |
 *    If
 *   /  \
 *   |  Write
 *   |   |
 *   |  Write
 *   \  /
 *   Merge
 * </pre>
 */
@NodeInfo(allowedUsageTypes = {InputType.Memory, InputType.Guard}, cycles = CYCLES_4, size = SIZE_8, nameTemplate = "ConditionalWrite#{p#location/s}")
public class ConditionalWriteNode extends FixedAccessNode implements Simplifiable, MemoryAccess, SingleMemoryKill {
    /**
     * Node type metadata for conditional writes.
     */
    public static final NodeClass<ConditionalWriteNode> TYPE = NodeClass.create(ConditionalWriteNode.class);

    @Input(InputType.Condition) LogicNode condition;
    @Input(InputType.Value) ValueNode value;

    final ProfileData.BranchProbabilityData profile;

    /**
     * Creates a temporary write guarded by {@code condition}.
     */
    public ConditionalWriteNode(LogicNode condition, AddressNode address, ValueNode value, LocationIdentity location, BarrierType barrier, ProfileData.BranchProbabilityData profile) {
        super(TYPE, address, location, StampFactory.forVoid(), barrier);
        this.condition = condition;
        this.value = value;
        this.profile = profile;
    }

    /**
     * Returns the location killed when this conditional write executes.
     */
    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return getLocationIdentity();
    }

    /**
     * Conditional writes preserve the null-check capability of the underlying write.
     */
    @Override
    public boolean canNullCheck() {
        return true;
    }

    /**
     * Lowers one or more adjacent conditional writes with the same condition into an if diamond.
     */
    @Override
    public void simplify(SimplifierTool tool) {
        List<ConditionalWriteNode> conditionals = getConditionalsCascade();

        // Create the write cascade, and link them together
        List<WriteNode> writes = buildWritesCascade(conditionals);
        // Obtain where to insert the diamond
        FixedWithNextNode highNode = (FixedWithNextNode) conditionals.get(0).predecessor();
        ConditionalWriteNode lastConditional = conditionals.get(conditionals.size() - 1);
        FixedNode lowNode = lastConditional.next();
        // Unlink conditionals cascade.
        highNode.setNext(null);
        lastConditional.setNext(null);
        // Spawn the control-flow and insert into the graph.
        buildAndInsertDiamond(writes, highNode, lowNode);
        deleteConditionals(conditionals);
    }

    /**
     * Collects the maximal adjacent chain of conditional writes guarded by this node's condition.
     * The returned list is ordered from the first conditional write in the fixed chain to the last.
     */
    private List<ConditionalWriteNode> getConditionalsCascade() {
        Node current;
        ConditionalWriteNode prev = this;
        ConditionalWriteNode topMost = prev;
        LogicNode commonCondition = condition;

        while (true) { // TERMINATION ARGUMENT: processing prev nodes
            CompilationAlarm.checkProgress(graph());
            current = prev.predecessor();
            if (!(current instanceof ConditionalWriteNode conditionalWrite)) {
                break;
            }
            prev = conditionalWrite;
            if (prev.condition != commonCondition) {
                break;
            }
            topMost = prev;
        }

        var cascade = new ArrayList<ConditionalWriteNode>();
        cascade.add(topMost);

        prev = topMost;
        while (true) { // TERMINATION ARGUMENT: processing prev nodes
            CompilationAlarm.checkProgress(graph());
            current = prev.next();
            if (!(current instanceof ConditionalWriteNode conditionalWrite)) {
                break;
            }
            prev = conditionalWrite;
            if (prev.condition != commonCondition) {
                break;
            }
            cascade.add(prev);
        }
        return cascade;
    }

    /**
     * Replaces each conditional write in {@code conditionals} with a plain write and links those
     * writes in the same order.
     */
    private List<WriteNode> buildWritesCascade(List<ConditionalWriteNode> conditionals) {
        var writes = new ArrayList<WriteNode>();
        WriteNode current = null;
        for (ConditionalWriteNode condi : conditionals) {
            WriteNode write = graph().add(new WriteNode(condi.getAddress(), condi.getLocationIdentity(), condi.value, condi.getBarrierType(), MemoryOrderMode.PLAIN));
            if (current != null) {
                current.setNext(write);
            }
            writes.add(write);
            current = write;
        }
        return writes;
    }

    /**
     * Inserts an if diamond whose true branch executes the write cascade and whose false branch
     * skips it.
     */
    private AbstractMergeNode buildAndInsertDiamond(List<WriteNode> writes, FixedWithNextNode highNode, FixedNode lowNode) {
        MergeNode mergeNode = graph().add(new MergeNode());
        EndNode regularEnd = graph().add(new EndNode());
        EndNode writeEnd = graph().add(new EndNode());
        WriteNode topWrite = writes.get(0);
        WriteNode bottomWrite = writes.get(writes.size() - 1);
        bottomWrite.setNext(writeEnd);
        mergeNode.addForwardEnd(writeEnd);
        mergeNode.addForwardEnd(regularEnd);
        IfNode ifNode = graph().add(new IfNode(condition, topWrite, regularEnd, profile));
        highNode.setNext(ifNode);
        mergeNode.setNext(lowNode);
        return mergeNode;
    }

    /**
     * Deletes the transient conditional writes after their replacement diamond has been inserted.
     */
    private static void deleteConditionals(List<ConditionalWriteNode> conditionals) {
        for (ConditionalWriteNode condi : conditionals) {
            condi.setNext(null);
            condi.safeDelete();
        }
    }
}
