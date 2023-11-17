/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.extended;

import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_16;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_8;

import java.util.Collections;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.IndirectCanonicalization;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.java.MonitorIdNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.nodes.spi.VirtualizableAllocation;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.virtual.VirtualBoxingNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This node represents the boxing of a primitive value. This corresponds to a call to the valueOf
 * methods in Integer, Long, etc.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_8, size = SIZE_16, allowedUsageTypes = {InputType.Value})
public abstract class BoxNode extends AbstractBoxingNode implements IterableNodeType, VirtualizableAllocation, Lowerable, Canonicalizable.Unary<ValueNode>, IndirectCanonicalization {

    public static final NodeClass<BoxNode> TYPE = NodeClass.create(BoxNode.class);

    private boolean hasIdentity;

    private BoxNode(NodeClass<? extends BoxNode> c, ValueNode value, ResolvedJavaType resultType, JavaKind boxingKind) {
        super(c, value, boxingKind, StampFactory.objectNonNull(TypeReference.createExactTrusted(resultType)), new FieldLocationIdentity(getValueField(resultType)));
        this.value = value;
    }

    private BoxNode(NodeClass<? extends BoxNode> c, ValueNode value, JavaKind boxingKind, Stamp s, LocationIdentity accessedLocation) {
        super(c, value, boxingKind, s, accessedLocation);
        this.value = value;
    }

    public static BoxNode create(ValueNode value, ResolvedJavaType resultType, JavaKind boxingKind) {
        if (boxingKind == JavaKind.Boolean) {
            return new PureBoxNode(value, resultType, boxingKind);
        }
        return new AllocatingBoxNode(value, resultType, boxingKind);
    }

    /**
     * @see #setHasIdentity()
     */
    public boolean hasIdentity() {
        return hasIdentity;
    }

    /**
     * Mark this boxing node as "identity preserving" such that it will not be escape analyzed or
     * commoned with another boxing node that shares the same {@linkplain #getValue() input value}.
     */
    public void setHasIdentity() {
        // A trusted box should never have identity
        assert !(getValue() instanceof TrustedBoxedValue) : this + ": " + getValue();
        this.hasIdentity = true;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        }
        if (forValue instanceof UnboxNode) {
            UnboxNode unbox = (UnboxNode) forValue;
            if (unbox.getBoxingKind() == getBoxingKind()) {
                ValueNode unboxInput = unbox.getValue();
                // box goes through valueOf path
                if (unboxInput instanceof BoxNode) {
                    if (((BoxNode) unboxInput).getBoxingKind() == getBoxingKind()) {
                        return unboxInput;
                    }
                }
                // trusted to have taken the valueOf path
                if (unboxInput instanceof TrustedBoxedValue) {
                    return ((TrustedBoxedValue) unboxInput).getValue();
                }
            }
        }
        return this;
    }

    protected VirtualBoxingNode createVirtualBoxingNode() {
        VirtualBoxingNode node = new VirtualBoxingNode(StampTool.typeOrNull(stamp(NodeView.DEFAULT)), boxingKind);
        return node;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        if (hasIdentity) {
            // Cannot virtualize a box node that preserves identity
            return;
        }
        ValueNode alias = tool.getAlias(getValue());

        VirtualBoxingNode newVirtual = createVirtualBoxingNode();
        assert newVirtual.getFields().length == 1 : Assertions.errorMessage(this, newVirtual);

        tool.createVirtualObject(newVirtual, new ValueNode[]{alias}, Collections.<MonitorIdNode> emptyList(), getNodeSourcePosition(), false);
        tool.replaceWithVirtual(newVirtual);
    }

    @NodeInfo(cycles = NodeCycles.CYCLES_8, size = SIZE_8, allowedUsageTypes = {InputType.Value})
    private static class PureBoxNode extends BoxNode {
        public static final NodeClass<PureBoxNode> TYPE = NodeClass.create(PureBoxNode.class);

        protected PureBoxNode(ValueNode value, ResolvedJavaType resultType, JavaKind boxingKind) {
            super(TYPE, value, resultType, boxingKind);
        }
    }

    @NodeInfo(cycles = NodeCycles.CYCLES_8, size = SIZE_8, allowedUsageTypes = {InputType.Memory, InputType.Value})
    private static class AllocatingBoxNode extends BoxNode implements SingleMemoryKill {
        public static final NodeClass<AllocatingBoxNode> TYPE = NodeClass.create(AllocatingBoxNode.class);

        protected AllocatingBoxNode(ValueNode value, ResolvedJavaType resultType, JavaKind boxingKind) {
            super(TYPE, value, resultType, boxingKind);
        }

        @Override
        public LocationIdentity getLocationIdentity() {
            return LocationIdentity.INIT_LOCATION;
        }

        @Override
        public LocationIdentity getKilledLocationIdentity() {
            return getLocationIdentity();
        }

    }

    /**
     * This nodes wraps value nodes representing objects that are known (due to some external
     * knowledge injected into the compiler) to have been created by a call to
     * Integer/Long/Short/...#valueOf methods. Thus, the wrapped value is subject to primitive box
     * caching.
     */
    @NodeInfo(cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED)
    public static class TrustedBoxedValue extends FloatingNode implements Canonicalizable, Virtualizable, Lowerable {
        public static final NodeClass<TrustedBoxedValue> TYPE = NodeClass.create(TrustedBoxedValue.class);

        @Input protected ValueNode value;

        public TrustedBoxedValue(ValueNode value) {
            super(TYPE, value.stamp(NodeView.DEFAULT));
            this.value = value;
        }

        public ValueNode getValue() {
            return value;
        }

        @Override
        public void lower(LoweringTool tool) {
            if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.MID_TIER) {
                replaceAtAllUsages(value, true);
            }
        }

        @Override
        public Node canonical(CanonicalizerTool tool) {
            if (tool.allUsagesAvailable()) {
                if (hasNoUsages()) {
                    return value;
                }
            }
            return this;
        }

        @Override
        public void virtualize(VirtualizerTool tool) {
            ValueNode alias = tool.getAlias(value);
            if (alias instanceof VirtualObjectNode) {
                tool.replaceWith(alias);
            }
        }

    }
}
