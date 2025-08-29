/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.memory;

import static jdk.graal.compiler.nodeinfo.InputType.Memory;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.spi.ConstantFieldProvider;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GuardedValueNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * A floating read of a value from memory specified in terms of an object base and an object
 * relative location. This node does not null check the object.
 */
@NodeInfo(nameTemplate = "Read#{p#location/s}", cycles = CYCLES_2, size = SIZE_1)
public class FloatingReadNode extends FloatingAccessNode implements Canonicalizable {
    public static final NodeClass<FloatingReadNode> TYPE = NodeClass.create(FloatingReadNode.class);

    @OptionalInput(Memory) MemoryKill lastLocationAccess;

    /**
     * If this is not null, then it is the field from which this load reads.
     */
    private final ResolvedJavaField field;

    /**
     * Records the injection of the property that this is a load from a trusted final field.
     *
     * @see ConstantFieldProvider#isTrustedFinal(CanonicalizerTool, ResolvedJavaField)
     */
    private final boolean trustInjected;

    /**
     * @see #potentialAntiDependency()
     */
    private final boolean noAntiDependency;

    public FloatingReadNode(AddressNode address, LocationIdentity location, MemoryKill lastLocationAccess, Stamp stamp) {
        this(address, location, lastLocationAccess, stamp, null, BarrierType.NONE);
    }

    public FloatingReadNode(AddressNode address, LocationIdentity location, MemoryKill lastLocationAccess, Stamp stamp, GuardingNode guard) {
        this(address, location, lastLocationAccess, stamp, guard, BarrierType.NONE);
    }

    public FloatingReadNode(AddressNode address, LocationIdentity location, MemoryKill lastLocationAccess, Stamp stamp, GuardingNode guard, BarrierType barrierType) {
        this(TYPE, address, location, lastLocationAccess, stamp, guard, barrierType, null, false, false);
    }

    public FloatingReadNode(AddressNode address, LocationIdentity location, MemoryKill lastLocationAccess, Stamp stamp, GuardingNode guard, BarrierType barrierType, ResolvedJavaField field,
                    boolean trustInjected) {
        this(TYPE, address, location, lastLocationAccess, stamp, guard, barrierType, field, trustInjected, false);
    }

    protected FloatingReadNode(NodeClass<? extends FloatingAccessNode> c, AddressNode address, LocationIdentity location, MemoryKill lastLocationAccess, Stamp stamp, GuardingNode guard,
                    BarrierType barrierType, ResolvedJavaField field, boolean trustInjected, boolean noAntiDependency) {
        super(c, address, location, stamp, guard, barrierType);
        this.lastLocationAccess = lastLocationAccess;
        this.field = field;
        this.trustInjected = trustInjected;
        this.noAntiDependency = location.isImmutable() || noAntiDependency;

        // The input to floating reads must be always non-null or have at least a guard.
        assert guard != null || !(address.getBase().stamp(NodeView.DEFAULT) instanceof ObjectStamp) || address.getBase() instanceof ValuePhiNode ||
                        ((ObjectStamp) address.getBase().stamp(NodeView.DEFAULT)).nonNull() : address.getBase();

        assert barrierType == BarrierType.NONE || stamp.isObjectStamp() : "incorrect barrier on non-object type: " + location;
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryKill newlla) {
        updateUsagesInterface(lastLocationAccess, newlla);
        lastLocationAccess = newlla;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (!tool.canonicalizeReads()) {
            return this;
        }

        Node result = ReadNode.canonicalizeRead(this, getAddress(), getLocationIdentity(), tool);
        if (result != this) {
            return result;
        }
        if (getAddress().hasMoreThanOneUsage() && lastLocationAccess instanceof WriteNode) {
            WriteNode write = (WriteNode) lastLocationAccess;
            if (write.getAddress() == getAddress() && write.getAccessStamp(NodeView.DEFAULT).isCompatible(stamp(NodeView.DEFAULT))) {
                // Same memory location with no intervening write
                return write.value();
            }
        }

        ValueNode rewireMemory = tryRewireMemoryInputForTrustedFinalLoad(tool);
        return rewireMemory == null ? this : rewireMemory;
    }

    /**
     * Try to rewire a load to a better memory if the load is from a trusted final field. A memory
     * input is better if it dominates the current memory input since it will allow this load to
     * have more freedom in scheduling.
     * <p>
     * This is called from {@link #canonical(CanonicalizerTool)}, so it returns a clone of this node
     * with a new memory input. If no better memory node can be used, this method returns
     * {@code null}.
     *
     * @see ConstantFieldProvider#isTrustedFinal(CanonicalizerTool, ResolvedJavaField)
     */
    private ValueNode tryRewireMemoryInputForTrustedFinalLoad(CanonicalizerTool tool) {
        if (field == null) {
            // Not a field access
            return null;
        }
        if (!trustInjected && !tool.getConstantFieldProvider().isTrustedFinal(tool, field)) {
            // The field is not a trusted final field
            return null;
        }

        if (getClass() != FloatingReadNode.class) {
            // Only this exact node class
            return null;
        }

        StructuredGraph graph = this.graph();
        GraphState graphState = graph.getGraphState();
        if (!graphState.isAfterStage(GraphState.StageFlag.FLOATING_READS) || !graphState.isBeforeStage(GraphState.StageFlag.FIXED_READS)) {
            // We only have a memory graph after FloatingReads and before FixedReads
            return null;
        }

        // Look through Pi and GuardedValue, they don't affect the value of the node at runtime, so
        // they does not change whether the final fields are immutable or not. We don't look too
        // deep through the Pi chain, this also keeps the style gate happy by removing the endless
        // loop.
        ValueNode base = getAddress().getBase();
        int maximumDepth = 10;
        for (int i = 0; i < maximumDepth; i++) {
            if (base instanceof PiNode pi) {
                base = pi.object();
            } else if (base instanceof GuardedValueNode guardedValue) {
                base = guardedValue.object();
            } else {
                break;
            }
        }

        // A parameter should be fully initialized, except if it is the receiver of a constructor.
        // As a result, we can rewire the memory input to the memory state at the start of the
        // current graph.
        if (base instanceof ParameterNode param && (param.index() != 0 || !graph.method().isConstructor())) {
            MemoryKill newMemory = graph.start();
            if (getLastLocationAccess() == null || (getLastLocationAccess() == newMemory && noAntiDependency)) {
                return null;
            }

            // Setting noAntiDependency to true is a must, otherwise we can introduce incorrect
            // anti-dependencies with nodes between the old memory input and newMemory, leading to
            // an unschedulable graph
            FloatingReadNode res = new FloatingReadNode(TYPE, getAddress(), getLocationIdentity(), newMemory, stamp, getGuard(), getBarrierType(), field, trustInjected, true);
            return res;
        }

        return null;
    }

    @SuppressWarnings("try")
    @Override
    public FixedAccessNode asFixedNode() {
        try (DebugCloseable position = withNodeSourcePosition()) {
            ReadNode result = graph().add(new ReadNode(getAddress(), getLocationIdentity(), stamp(NodeView.DEFAULT), getBarrierType(), MemoryOrderMode.PLAIN, field, trustInjected));
            result.setGuard(getGuard());
            return result;
        }
    }

    @Override
    public boolean verifyNode() {
        MemoryKill lla = getLastLocationAccess();
        assert lla != null || getLocationIdentity().isImmutable() : "lastLocationAccess of " + this + " shouldn't be null for mutable location identity " + getLocationIdentity();
        return super.verifyNode();
    }

    /**
     * Indicate whether this load may have anti-dependencies on other stores. Anti-dependency means
     * that a store can overwrite the memory location which is read by this load. As a result, the
     * store must not be scheduled in a way that it can execute before this load in any execution
     * path. The result of {@code false} from this method means that the memory location from which
     * this load reads is immutable after the memory input of this node in the CFG. This is weaker
     * than an immutable load because the memory location can still be modified before the memory
     * input of this load. As a result, this load must still be scheduled under its memory input in
     * the dominator tree. An immutable load also means that it has no anti-dependency and this
     * method will return {@code false}.
     */
    public boolean potentialAntiDependency() {
        return !noAntiDependency;
    }

}
