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
package org.graalvm.compiler.nodes.java;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import java.lang.ref.Reference;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.iterators.NodePredicates;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.CompressionNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.meta.JavaKind;

/**
 * Implements the semantics of {@link Reference#reachabilityFence} without relying on a
 * {@link FrameState} to keep values alive.
 *
 * This node does not prevent any escape analysis. When an object is virtualized, then this node
 * keeps all non-primitive elements of the virtual object alive too, i.e., it ensures that there is
 * no observable difference even for objects that are transitively reachable. This is an important
 * part of the semantics of {@link Reference#reachabilityFence}.
 *
 * When an object is virtualized, also the virtualized object itself remains alive (i.e., it remains
 * part of the {@link #values} list. While this is not required for the semantics of
 * {@link Reference#reachabilityFence} from a GC point of view, it is useful when
 * {@link Reference#reachabilityFence} is used (or actually mis-used) to keep a value alive for the
 * purpose of stack frame inspection.
 */
@NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
public final class ReachabilityFenceNode extends FixedWithNextNode implements Virtualizable, Canonicalizable, LIRLowerable {
    public static final NodeClass<ReachabilityFenceNode> TYPE = NodeClass.create(ReachabilityFenceNode.class);

    @Input NodeInputList<ValueNode> values;

    public static ValueNode create(ValueNode value) {
        return new ReachabilityFenceNode(new ValueNode[]{value});
    }

    public static ReachabilityFenceNode create(ValueNode[] values) {
        return new ReachabilityFenceNode(values);
    }

    protected ReachabilityFenceNode(ValueNode[] values) {
        super(TYPE, StampFactory.forVoid());
        this.values = new NodeInputList<>(this, values);
    }

    public NodeInputList<ValueNode> getValues() {
        return values;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        /*
         * The set can contain nodes that have not been added to the graph, i.e., nodes without an
         * id. Such nodes do not have a hashCode(), so we need to use System.identityHashCode().
         */
        EconomicSet<ValueNode> newValues = EconomicSet.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE, values.size());
        boolean modified = false;
        for (ValueNode originalValue : values) {
            if (originalValue instanceof VirtualObjectNode) {
                /*
                 * Virtualized by a previous run of escape analysis. No need to process it again.
                 * Also, getAlias can fail for it because this run of escape analysis might not
                 * track that allocation anymore.
                 */
                newValues.add(originalValue);
                /*
                 * We need to force the creation of a new ReachabilityFenceNode to work around
                 * restrictions of escape analysis. If we leave the old node in place, then the
                 * usage of a VirtualObjectNode forces materialization of that virtual object.
                 */
                modified = true;

            } else {
                ValueNode aliasValue = tool.getAlias(originalValue);
                if (originalValue == aliasValue) {
                    newValues.add(originalValue);
                } else {
                    processValue(aliasValue, newValues, tool);
                    modified = true;
                }
            }
        }
        if (modified) {
            tool.replaceWith(new ReachabilityFenceNode(newValues.toArray(new ValueNode[newValues.size()])));
        }
    }

    private void processValue(ValueNode value, EconomicSet<ValueNode> newValues, VirtualizerTool tool) {
        if (value.isConstant()) {
            /* Constant values do not need to be tracked. */
        } else if (value.getStackKind() != JavaKind.Object) {
            /* Primitive values do not need to be tracked. */
        } else if (value instanceof VirtualObjectNode) {
            VirtualObjectNode virtualObject = (VirtualObjectNode) value;
            if (newValues.add(virtualObject)) {
                /* When a virtual object is seen the first time, recursively add all elements. */
                for (int i = 0; i < virtualObject.entryCount(); i++) {
                    processValue(tool.getEntry(virtualObject, i), newValues, tool);
                }
            }
        } else {
            newValues.add(value);
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        /*
         * See if we want to build a new version of this node. Canonicalization must not have side
         * effects, including modifying the current node in place.
         */
        int droppedInputs = 0;
        int compressionInputs = 0;
        for (ValueNode value : values) {
            if (value.isConstant()) {
                /* Constant values do not need to be tracked. */
                droppedInputs++;
            } else if (tool.allUsagesAvailable()) {
                if ((value instanceof CompressionNode || value instanceof VirtualObjectNode) && hasOnlyReachabilityFenceUsages(value)) {
                    if (value instanceof CompressionNode) {
                        /* References do not need to be uncompressed just to be kept alive. */
                        compressionInputs++;
                    } else {
                        assert value instanceof VirtualObjectNode;
                        /* Virtual objects that have no other usages do not need to be tracked. */
                        droppedInputs++;
                    }
                }
            }
        }

        if (values.size() == 0 || values.size() == droppedInputs) {
            /* No more values to track, delete ourselves. */
            return null;
        } else if (droppedInputs > 0 || compressionInputs > 0) {
            /* We can drop or simplify some inputs, so build a new node. */
            int newInputSize = values.size() - droppedInputs;
            ValueNode[] newInputs = new ValueNode[newInputSize];
            int i = 0;
            for (ValueNode value : values) {
                ValueNode v = value;
                if (v.isConstant()) {
                    continue;
                } else if (tool.allUsagesAvailable()) {
                    if ((v instanceof CompressionNode || v instanceof VirtualObjectNode) && hasOnlyReachabilityFenceUsages(v)) {
                        if (v instanceof CompressionNode) {
                            v = ((CompressionNode) v).getValue();
                        } else {
                            assert v instanceof VirtualObjectNode;
                            continue;
                        }
                    }
                }
                newInputs[i++] = v;
            }
            assert i == newInputSize;
            return new ReachabilityFenceNode(newInputs);
        } else {
            return this;
        }
    }

    private static boolean hasOnlyReachabilityFenceUsages(ValueNode value) {
        if (value.hasExactlyOneUsage()) {
            assert value.singleUsage() instanceof ReachabilityFenceNode;
            return true;
        }
        return value.usages().filter(NodePredicates.isNotA(ReachabilityFenceNode.class)).isEmpty();
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        for (ValueNode value : values) {
            if (!(value instanceof VirtualObjectNode)) {
                gen.getLIRGeneratorTool().emitBlackhole(gen.operand(value));
            }
        }
    }
}
