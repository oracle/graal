/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements;

import static com.oracle.graal.api.code.MemoryBarriers.*;
import static com.oracle.graal.api.meta.DeoptimizationAction.*;
import static com.oracle.graal.api.meta.DeoptimizationReason.*;
import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.nodes.java.ArrayLengthNode.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.HeapAccess.BarrierType;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.phases.util.*;

/**
 * VM-independent lowerings for standard Java nodes. VM-specific methods are abstract and must be
 * implemented by VM-specific subclasses.
 */
public abstract class DefaultJavaLoweringProvider implements LoweringProvider {

    protected final MetaAccessProvider metaAccess;
    protected final TargetDescription target;

    private BoxingSnippets.Templates boxingSnippets;

    public DefaultJavaLoweringProvider(MetaAccessProvider metaAccess, TargetDescription target) {
        this.metaAccess = metaAccess;
        this.target = target;
    }

    public void initialize(Providers providers, SnippetReflectionProvider snippetReflection) {
        boxingSnippets = new BoxingSnippets.Templates(providers, snippetReflection, target);
    }

    @Override
    public void lower(Node n, LoweringTool tool) {
        if (n instanceof LoadFieldNode) {
            lowerLoadFieldNode((LoadFieldNode) n, tool);
        } else if (n instanceof StoreFieldNode) {
            lowerStoreFieldNode((StoreFieldNode) n, tool);
        } else if (n instanceof LoadIndexedNode) {
            lowerLoadIndexedNode((LoadIndexedNode) n, tool);
        } else if (n instanceof StoreIndexedNode) {
            lowerStoreIndexedNode((StoreIndexedNode) n, tool);
        } else if (n instanceof ArrayLengthNode) {
            lowerArrayLengthNode((ArrayLengthNode) n, tool);
        } else if (n instanceof LoadHubNode) {
            lowerLoadHubNode((LoadHubNode) n);
        } else if (n instanceof CompareAndSwapNode) {
            lowerCompareAndSwapNode((CompareAndSwapNode) n);
        } else if (n instanceof AtomicReadAndWriteNode) {
            lowerAtomicReadAndWriteNode((AtomicReadAndWriteNode) n);
        } else if (n instanceof UnsafeLoadNode) {
            lowerUnsafeLoadNode((UnsafeLoadNode) n, tool);
        } else if (n instanceof UnsafeStoreNode) {
            lowerUnsafeStoreNode((UnsafeStoreNode) n);
        } else if (n instanceof JavaReadNode) {
            lowerJavaReadNode((JavaReadNode) n);
        } else if (n instanceof JavaWriteNode) {
            lowerJavaWriteNode((JavaWriteNode) n);
        } else if (n instanceof CommitAllocationNode) {
            lowerCommitAllocationNode((CommitAllocationNode) n, tool);
        } else if (n instanceof BoxNode) {
            boxingSnippets.lower((BoxNode) n, tool);
        } else if (n instanceof UnboxNode) {
            boxingSnippets.lower((UnboxNode) n, tool);
        } else {
            throw GraalInternalError.shouldNotReachHere("Node implementing Lowerable not handled: " + n);
        }
    }

    protected void lowerLoadFieldNode(LoadFieldNode loadField, LoweringTool tool) {
        assert loadField.getKind() != Kind.Illegal;
        StructuredGraph graph = loadField.graph();
        ResolvedJavaField field = loadField.field();
        ValueNode object = loadField.isStatic() ? staticFieldBase(graph, field) : loadField.object();
        Stamp loadStamp = loadStamp(loadField.stamp(), field.getKind());
        ConstantLocationNode location = createFieldLocation(graph, field, false);
        assert location != null : "Field that is loaded must not be eliminated";

        ReadNode memoryRead = graph.add(new ReadNode(object, location, loadStamp, fieldLoadBarrierType(field)));
        ValueNode readValue = implicitLoadConvert(graph, field.getKind(), memoryRead);
        loadField.replaceAtUsages(readValue);
        graph.replaceFixed(loadField, memoryRead);

        memoryRead.setGuard(createNullCheck(object, memoryRead, tool));

        if (loadField.isVolatile()) {
            MembarNode preMembar = graph.add(new MembarNode(JMM_PRE_VOLATILE_READ));
            graph.addBeforeFixed(memoryRead, preMembar);
            MembarNode postMembar = graph.add(new MembarNode(JMM_POST_VOLATILE_READ));
            graph.addAfterFixed(memoryRead, postMembar);
        }
    }

    protected void lowerStoreFieldNode(StoreFieldNode storeField, LoweringTool tool) {
        StructuredGraph graph = storeField.graph();
        ResolvedJavaField field = storeField.field();
        ValueNode object = storeField.isStatic() ? staticFieldBase(graph, field) : storeField.object();
        ValueNode value = implicitStoreConvert(graph, storeField.field().getKind(), storeField.value());
        ConstantLocationNode location = createFieldLocation(graph, field, false);

        if (location == null) {
            /* Field has been eliminated, so no write necessary. */
            assert !storeField.isVolatile() : "missing memory barriers";
            graph.removeFixed(storeField);
            return;
        }
        WriteNode memoryWrite = graph.add(new WriteNode(object, value, location, fieldStoreBarrierType(storeField.field())));
        memoryWrite.setStateAfter(storeField.stateAfter());
        graph.replaceFixedWithFixed(storeField, memoryWrite);
        memoryWrite.setGuard(createNullCheck(object, memoryWrite, tool));

        if (storeField.isVolatile()) {
            MembarNode preMembar = graph.add(new MembarNode(JMM_PRE_VOLATILE_WRITE));
            graph.addBeforeFixed(memoryWrite, preMembar);
            MembarNode postMembar = graph.add(new MembarNode(JMM_POST_VOLATILE_WRITE));
            graph.addAfterFixed(memoryWrite, postMembar);
        }
    }

    protected void lowerLoadIndexedNode(LoadIndexedNode loadIndexed, LoweringTool tool) {
        StructuredGraph graph = loadIndexed.graph();
        Kind elementKind = loadIndexed.elementKind();
        LocationNode location = createArrayLocation(graph, elementKind, loadIndexed.index(), false);
        Stamp loadStamp = loadStamp(loadIndexed.stamp(), elementKind);

        ReadNode memoryRead = graph.add(new ReadNode(loadIndexed.array(), location, loadStamp, BarrierType.NONE));
        ValueNode readValue = implicitLoadConvert(graph, elementKind, memoryRead);

        memoryRead.setGuard(createBoundsCheck(loadIndexed, tool));

        loadIndexed.replaceAtUsages(readValue);
        graph.replaceFixed(loadIndexed, memoryRead);
    }

    protected void lowerStoreIndexedNode(StoreIndexedNode storeIndexed, LoweringTool tool) {
        StructuredGraph graph = storeIndexed.graph();
        GuardingNode boundsCheck = createBoundsCheck(storeIndexed, tool);
        Kind elementKind = storeIndexed.elementKind();
        LocationNode location = createArrayLocation(graph, elementKind, storeIndexed.index(), false);

        ValueNode value = storeIndexed.value();
        ValueNode array = storeIndexed.array();
        FixedWithNextNode checkCastNode = null;
        if (elementKind == Kind.Object && !StampTool.isObjectAlwaysNull(value)) {
            /* Array store check. */
            ResolvedJavaType arrayType = StampTool.typeOrNull(array);
            if (arrayType != null && StampTool.isExactType(array)) {
                ResolvedJavaType elementType = arrayType.getComponentType();
                if (!elementType.isJavaLangObject()) {
                    checkCastNode = graph.add(new CheckCastNode(elementType, value, null, true));
                    graph.addBeforeFixed(storeIndexed, checkCastNode);
                    value = checkCastNode;
                }
            } else {
                ValueNode arrayClass = createReadHub(graph, array, boundsCheck);
                ValueNode componentHub = createReadArrayComponentHub(graph, arrayClass, storeIndexed);
                checkCastNode = graph.add(new CheckCastDynamicNode(componentHub, value, true));
                graph.addBeforeFixed(storeIndexed, checkCastNode);
                value = checkCastNode;
            }
        }

        WriteNode memoryWrite = graph.add(new WriteNode(array, implicitStoreConvert(graph, elementKind, value), location, arrayStoreBarrierType(storeIndexed.elementKind())));
        memoryWrite.setGuard(boundsCheck);
        memoryWrite.setStateAfter(storeIndexed.stateAfter());
        graph.replaceFixedWithFixed(storeIndexed, memoryWrite);

        if (checkCastNode instanceof Lowerable) {
            /* Recursive lowering of the store check node. */
            ((Lowerable) checkCastNode).lower(tool);
        }
    }

    protected void lowerArrayLengthNode(ArrayLengthNode arrayLengthNode, LoweringTool tool) {
        StructuredGraph graph = arrayLengthNode.graph();
        ValueNode array = arrayLengthNode.array();
        ConstantLocationNode location = ConstantLocationNode.create(ARRAY_LENGTH_LOCATION, Kind.Int, arrayLengthOffset(), graph);

        ReadNode arrayLengthRead = graph.add(new ReadNode(array, location, StampFactory.positiveInt(), BarrierType.NONE));
        arrayLengthRead.setGuard(createNullCheck(array, arrayLengthNode, tool));
        graph.replaceFixedWithFixed(arrayLengthNode, arrayLengthRead);
    }

    protected void lowerLoadHubNode(LoadHubNode loadHub) {
        StructuredGraph graph = loadHub.graph();
        if (graph.getGuardsStage().ordinal() < StructuredGraph.GuardsStage.FIXED_DEOPTS.ordinal()) {
            return;
        }
        ValueNode hub = createReadHub(graph, loadHub.getValue(), loadHub.getGuard());
        graph.replaceFloating(loadHub, hub);
    }

    protected void lowerCompareAndSwapNode(CompareAndSwapNode cas) {
        StructuredGraph graph = cas.graph();
        Kind valueKind = cas.getValueKind();
        LocationNode location = createLocation(cas.offset(), cas.getLocationIdentity(), valueKind);

        ValueNode expectedValue = implicitStoreConvert(graph, valueKind, cas.expected());
        ValueNode newValue = implicitStoreConvert(graph, valueKind, cas.newValue());

        LoweredCompareAndSwapNode atomicNode = graph.add(new LoweredCompareAndSwapNode(cas.object(), location, expectedValue, newValue, compareAndSwapBarrierType(cas)));
        atomicNode.setStateAfter(cas.stateAfter());
        graph.replaceFixedWithFixed(cas, atomicNode);
    }

    protected void lowerAtomicReadAndWriteNode(AtomicReadAndWriteNode n) {
        StructuredGraph graph = n.graph();
        Kind valueKind = n.getValueKind();
        LocationNode location = IndexedLocationNode.create(n.getLocationIdentity(), valueKind, 0, n.offset(), graph, 1);

        ValueNode newValue = implicitStoreConvert(graph, valueKind, n.newValue());

        LoweredAtomicReadAndWriteNode memoryRead = graph.add(new LoweredAtomicReadAndWriteNode(n.object(), location, newValue, atomicReadAndWriteBarrierType(n)));
        memoryRead.setStateAfter(n.stateAfter());

        ValueNode readValue = implicitLoadConvert(graph, valueKind, memoryRead);
        n.replaceAtUsages(readValue);
        graph.replaceFixedWithFixed(n, memoryRead);
    }

    protected void lowerUnsafeLoadNode(UnsafeLoadNode load, @SuppressWarnings("unused") LoweringTool tool) {
        StructuredGraph graph = load.graph();
        if (load.getGuardingCondition() != null) {
            ConditionAnchorNode valueAnchorNode = graph.add(new ConditionAnchorNode(load.getGuardingCondition()));
            ReadNode memoryRead = createUnsafeRead(graph, load, valueAnchorNode);
            graph.replaceFixedWithFixed(load, valueAnchorNode);
            graph.addAfterFixed(valueAnchorNode, memoryRead);
        } else if (graph.getGuardsStage().ordinal() > StructuredGraph.GuardsStage.FLOATING_GUARDS.ordinal()) {
            assert load.getKind() != Kind.Illegal;
            ReadNode memoryRead = createUnsafeRead(graph, load, null);
            // An unsafe read must not float outside its block otherwise
            // it may float above an explicit null check on its object.
            memoryRead.setGuard(BeginNode.prevBegin(load));
            graph.replaceFixedWithFixed(load, memoryRead);
        }
    }

    protected ReadNode createUnsafeRead(StructuredGraph graph, UnsafeLoadNode load, GuardingNode guard) {
        boolean compressible = load.accessKind() == Kind.Object;
        Kind readKind = load.accessKind();
        ValueNode[] base = null;
        ValueNode object = load.object();
        if (object.isConstant() && object.asConstant().isDefaultForKind()) {
            base = new ValueNode[1];
        }
        LocationNode location = createLocation(load, base);
        if (base != null && base[0] != null) {
            object = base[0];
        }
        Stamp loadStamp = loadStamp(load.stamp(), readKind, compressible);
        ReadNode memoryRead = graph.add(new ReadNode(object, location, loadStamp, guard, BarrierType.NONE));
        ValueNode readValue = implicitLoadConvert(graph, readKind, memoryRead, compressible);
        load.replaceAtUsages(readValue);
        return memoryRead;
    }

    protected void lowerUnsafeStoreNode(UnsafeStoreNode store) {
        StructuredGraph graph = store.graph();
        ValueNode object = store.object();
        ValueNode[] base = null;
        if (object.isConstant() && object.asConstant().isDefaultForKind()) {
            base = new ValueNode[1];
        }
        LocationNode location = createLocation(store, base);
        if (base != null && base[0] != null) {
            object = base[0];
        }
        boolean compressible = store.value().getKind() == Kind.Object;
        Kind valueKind = store.accessKind();
        ValueNode value = implicitStoreConvert(graph, valueKind, store.value(), compressible);
        WriteNode write = graph.add(new WriteNode(object, value, location, unsafeStoreBarrierType(store)));
        write.setStateAfter(store.stateAfter());
        graph.replaceFixedWithFixed(store, write);
    }

    protected void lowerJavaReadNode(JavaReadNode read) {
        StructuredGraph graph = read.graph();
        Kind valueKind = read.location().getValueKind();
        Stamp loadStamp = loadStamp(read.stamp(), valueKind, read.isCompressible());

        ReadNode memoryRead = graph.add(new ReadNode(read.object(), read.location(), loadStamp, read.getBarrierType()));
        ValueNode readValue = implicitLoadConvert(graph, valueKind, memoryRead, read.isCompressible());
        memoryRead.setGuard(read.getGuard());
        read.replaceAtUsages(readValue);
        graph.replaceFixed(read, memoryRead);
    }

    protected void lowerJavaWriteNode(JavaWriteNode write) {
        StructuredGraph graph = write.graph();
        Kind valueKind = write.location().getValueKind();
        ValueNode value = implicitStoreConvert(graph, valueKind, write.value(), write.isCompressible());

        WriteNode memoryWrite = graph.add(new WriteNode(write.object(), value, write.location(), write.getBarrierType(), write.isInitialization()));
        memoryWrite.setStateAfter(write.stateAfter());
        graph.replaceFixedWithFixed(write, memoryWrite);
        memoryWrite.setGuard(write.getGuard());
    }

    protected void lowerCommitAllocationNode(CommitAllocationNode commit, LoweringTool tool) {
        StructuredGraph graph = commit.graph();
        if (graph.getGuardsStage() == StructuredGraph.GuardsStage.FIXED_DEOPTS) {
            List<AbstractNewObjectNode> recursiveLowerings = new ArrayList<>();

            ValueNode[] allocations = new ValueNode[commit.getVirtualObjects().size()];
            BitSet omittedValues = new BitSet();
            int valuePos = 0;
            for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
                VirtualObjectNode virtual = commit.getVirtualObjects().get(objIndex);
                int entryCount = virtual.entryCount();
                AbstractNewObjectNode newObject;
                if (virtual instanceof VirtualInstanceNode) {
                    newObject = graph.add(new NewInstanceNode(virtual.type(), true));
                } else {
                    newObject = graph.add(new NewArrayNode(((VirtualArrayNode) virtual).componentType(), ConstantNode.forInt(entryCount, graph), true));
                }
                recursiveLowerings.add(newObject);
                graph.addBeforeFixed(commit, newObject);
                allocations[objIndex] = newObject;
                for (int i = 0; i < entryCount; i++) {
                    ValueNode value = commit.getValues().get(valuePos);
                    if (value instanceof VirtualObjectNode) {
                        value = allocations[commit.getVirtualObjects().indexOf(value)];
                    }
                    if (value == null) {
                        omittedValues.set(valuePos);
                    } else if (!(value.isConstant() && value.asConstant().isDefaultForKind())) {
                        // Constant.illegal is always the defaultForKind, so it is skipped
                        Kind valueKind = value.getKind();
                        Kind entryKind = virtual.entryKind(i);

                        // Truffle requires some leniency in terms of what can be put where:
                        Kind accessKind = valueKind.getStackKind() == entryKind.getStackKind() ? entryKind : valueKind;
                        assert valueKind.getStackKind() == entryKind.getStackKind() ||
                                        (valueKind == Kind.Long || valueKind == Kind.Double || (valueKind == Kind.Int && virtual instanceof VirtualArrayNode));
                        ConstantLocationNode location = null;
                        BarrierType barrierType = null;
                        if (virtual instanceof VirtualInstanceNode) {
                            ResolvedJavaField field = ((VirtualInstanceNode) virtual).field(i);
                            long offset = fieldOffset(field);
                            if (offset >= 0) {
                                location = ConstantLocationNode.create(initLocationIdentity(), accessKind, offset, graph);
                                barrierType = fieldInitializationBarrier(entryKind);
                            }
                        } else {
                            location = ConstantLocationNode.create(initLocationIdentity(), accessKind, arrayBaseOffset(entryKind) + i * arrayScalingFactor(entryKind), graph);
                            barrierType = arrayInitializationBarrier(entryKind);
                        }
                        if (location != null) {
                            WriteNode write = new WriteNode(newObject, implicitStoreConvert(graph, entryKind, value), location, barrierType);
                            graph.addAfterFixed(newObject, graph.add(write));
                        }
                    }
                    valuePos++;

                }
            }
            valuePos = 0;

            for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
                VirtualObjectNode virtual = commit.getVirtualObjects().get(objIndex);
                int entryCount = virtual.entryCount();
                ValueNode newObject = allocations[objIndex];
                for (int i = 0; i < entryCount; i++) {
                    if (omittedValues.get(valuePos)) {
                        ValueNode value = commit.getValues().get(valuePos);
                        assert value instanceof VirtualObjectNode;
                        ValueNode allocValue = allocations[commit.getVirtualObjects().indexOf(value)];
                        if (!(allocValue.isConstant() && allocValue.asConstant().isDefaultForKind())) {
                            assert virtual.entryKind(i) == Kind.Object && allocValue.getKind() == Kind.Object;
                            LocationNode location;
                            BarrierType barrierType;
                            if (virtual instanceof VirtualInstanceNode) {
                                VirtualInstanceNode virtualInstance = (VirtualInstanceNode) virtual;
                                location = createFieldLocation(graph, virtualInstance.field(i), true);
                                barrierType = BarrierType.IMPRECISE;
                            } else {
                                location = createArrayLocation(graph, virtual.entryKind(i), ConstantNode.forInt(i, graph), true);
                                barrierType = BarrierType.PRECISE;
                            }
                            if (location != null) {
                                WriteNode write = new WriteNode(newObject, implicitStoreConvert(graph, Kind.Object, allocValue), location, barrierType);
                                graph.addBeforeFixed(commit, graph.add(write));
                            }
                        }
                    }
                    valuePos++;
                }
            }

            finishAllocatedObjects(tool, commit, allocations);
            graph.removeFixed(commit);

            for (AbstractNewObjectNode recursiveLowering : recursiveLowerings) {
                recursiveLowering.lower(tool);
            }
        }
    }

    public static void finishAllocatedObjects(LoweringTool tool, CommitAllocationNode commit, ValueNode[] allocations) {
        StructuredGraph graph = commit.graph();
        for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
            FixedValueAnchorNode anchor = graph.add(new FixedValueAnchorNode(allocations[objIndex]));
            allocations[objIndex] = anchor;
            graph.addBeforeFixed(commit, anchor);
        }
        for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
            for (MonitorIdNode monitorId : commit.getLocks(objIndex)) {
                MonitorEnterNode enter = graph.add(new MonitorEnterNode(allocations[objIndex], monitorId));
                graph.addBeforeFixed(commit, enter);
                enter.lower(tool);
            }
        }
        for (Node usage : commit.usages().snapshot()) {
            AllocatedObjectNode addObject = (AllocatedObjectNode) usage;
            int index = commit.getVirtualObjects().indexOf(addObject.getVirtualObject());
            graph.replaceFloating(addObject, allocations[index]);
        }
    }

    protected BarrierType fieldLoadBarrierType(@SuppressWarnings("unused") ResolvedJavaField field) {
        return BarrierType.NONE;
    }

    protected BarrierType fieldStoreBarrierType(ResolvedJavaField field) {
        if (field.getKind() == Kind.Object) {
            return BarrierType.IMPRECISE;
        }
        return BarrierType.NONE;
    }

    protected BarrierType arrayStoreBarrierType(Kind elementKind) {
        if (elementKind == Kind.Object) {
            return BarrierType.PRECISE;
        }
        return BarrierType.NONE;
    }

    protected BarrierType fieldInitializationBarrier(Kind entryKind) {
        return entryKind == Kind.Object ? BarrierType.IMPRECISE : BarrierType.NONE;
    }

    protected BarrierType arrayInitializationBarrier(Kind entryKind) {
        return entryKind == Kind.Object ? BarrierType.PRECISE : BarrierType.NONE;
    }

    protected BarrierType unsafeStoreBarrierType(UnsafeStoreNode store) {
        return storeBarrierType(store.object(), store.value());
    }

    protected BarrierType compareAndSwapBarrierType(CompareAndSwapNode cas) {
        return storeBarrierType(cas.object(), cas.expected());
    }

    protected BarrierType atomicReadAndWriteBarrierType(AtomicReadAndWriteNode n) {
        return storeBarrierType(n.object(), n.newValue());
    }

    protected BarrierType storeBarrierType(ValueNode object, ValueNode value) {
        if (value.getKind() == Kind.Object) {
            ResolvedJavaType type = StampTool.typeOrNull(object);
            if (type != null && !type.isArray()) {
                return BarrierType.IMPRECISE;
            } else {
                return BarrierType.PRECISE;
            }
        }
        return BarrierType.NONE;
    }

    protected abstract int fieldOffset(ResolvedJavaField field);

    protected abstract ValueNode staticFieldBase(StructuredGraph graph, ResolvedJavaField field);

    protected abstract int arrayLengthOffset();

    protected abstract int arrayBaseOffset(Kind elementKind);

    public int arrayScalingFactor(Kind elementKind) {
        return target.getSizeInBytes(elementKind);
    }

    protected abstract LocationIdentity initLocationIdentity();

    public Stamp loadStamp(Stamp stamp, Kind kind) {
        return loadStamp(stamp, kind, true);
    }

    protected Stamp loadStamp(Stamp stamp, Kind kind, @SuppressWarnings("unused") boolean compressible) {
        switch (kind) {
            case Boolean:
            case Byte:
                return StampTool.narrowingConversion(stamp, 8);
            case Char:
            case Short:
                return StampTool.narrowingConversion(stamp, 16);
        }
        return stamp;
    }

    public ValueNode implicitLoadConvert(StructuredGraph graph, Kind kind, ValueNode value) {
        return implicitLoadConvert(graph, kind, value, true);

    }

    protected ValueNode implicitLoadConvert(StructuredGraph graph, Kind kind, ValueNode value, @SuppressWarnings("unused") boolean compressible) {
        switch (kind) {
            case Byte:
            case Short:
                return graph.unique(new SignExtendNode(value, 32));
            case Boolean:
            case Char:
                return graph.unique(new ZeroExtendNode(value, 32));
        }
        return value;
    }

    public ValueNode implicitStoreConvert(StructuredGraph graph, Kind kind, ValueNode value) {
        return implicitStoreConvert(graph, kind, value, true);
    }

    protected ValueNode implicitStoreConvert(StructuredGraph graph, Kind kind, ValueNode value, @SuppressWarnings("unused") boolean compressible) {
        switch (kind) {
            case Boolean:
            case Byte:
                return graph.unique(new NarrowNode(value, 8));
            case Char:
            case Short:
                return graph.unique(new NarrowNode(value, 16));
        }
        return value;
    }

    protected abstract ValueNode createReadHub(StructuredGraph graph, ValueNode object, GuardingNode guard);

    protected abstract ValueNode createReadArrayComponentHub(StructuredGraph graph, ValueNode arrayHub, FixedNode anchor);

    protected ConstantLocationNode createFieldLocation(StructuredGraph graph, ResolvedJavaField field, boolean initialization) {
        int offset = fieldOffset(field);
        if (offset >= 0) {
            LocationIdentity loc = initialization ? initLocationIdentity() : field;
            return ConstantLocationNode.create(loc, field.getKind(), offset, graph);
        } else {
            return null;
        }
    }

    protected LocationNode createLocation(UnsafeAccessNode access, ValueNode[] base) {
        return createLocation(access.offset(), access.getLocationIdentity(), access.accessKind(), base);
    }

    protected LocationNode createLocation(ValueNode offsetNode, LocationIdentity locationIdentity, Kind accessKind) {
        return createLocation(offsetNode, locationIdentity, accessKind, null);
    }

    /**
     * Try to unpack the operations in offsetNode into a LocationNode, taking advantage of
     * addressing modes if possible.
     *
     * @param offsetNode the computed offset into the base of the memory operation
     * @param locationIdentity
     * @param accessKind
     * @param base if non-null try to find a value that can be used as the base of the memory
     *            operation and return it as base[0]
     * @return the newly created LocationNode
     */
    protected LocationNode createLocation(ValueNode offsetNode, LocationIdentity locationIdentity, Kind accessKind, ValueNode[] base) {
        ValueNode offset = offsetNode;
        if (offset.isConstant()) {
            long offsetValue = offset.asConstant().asLong();
            return ConstantLocationNode.create(locationIdentity, accessKind, offsetValue, offset.graph());
        }

        long displacement = 0;
        int indexScaling = 1;
        boolean signExtend = false;
        if (offset instanceof SignExtendNode) {
            SignExtendNode extend = (SignExtendNode) offset;
            if (extend.getResultBits() == 64) {
                signExtend = true;
                offset = extend.getValue();
            }
        }
        if (offset instanceof IntegerAddNode) {
            IntegerAddNode integerAddNode = (IntegerAddNode) offset;
            if (integerAddNode.getY() instanceof ConstantNode) {
                displacement = integerAddNode.getY().asConstant().asLong();
                offset = integerAddNode.getX();
            }
        }
        if (base != null && signExtend == false && offset instanceof IntegerAddNode) {
            /*
             * Try to decompose the operation into base plus offset so the base can go into a new
             * node. Prefer the unshifted side of an add as the base.
             */
            IntegerAddNode integerAddNode = (IntegerAddNode) offset;
            if (integerAddNode.getY() instanceof LeftShiftNode) {
                base[0] = integerAddNode.getX();
                offset = integerAddNode.getY();
            } else {
                base[0] = integerAddNode.getY();
                offset = integerAddNode.getX();
            }
            if (offset instanceof IntegerAddNode) {
                integerAddNode = (IntegerAddNode) offset;
                if (integerAddNode.getY() instanceof ConstantNode) {
                    displacement = integerAddNode.getY().asConstant().asLong();
                    offset = integerAddNode.getX();
                }
            }
        }
        if (offset instanceof LeftShiftNode) {
            LeftShiftNode leftShiftNode = (LeftShiftNode) offset;
            if (leftShiftNode.getY() instanceof ConstantNode) {
                long shift = leftShiftNode.getY().asConstant().asLong();
                if (shift >= 1 && shift <= 3) {
                    if (shift == 1) {
                        indexScaling = 2;
                    } else if (shift == 2) {
                        indexScaling = 4;
                    } else {
                        indexScaling = 8;
                    }
                    offset = leftShiftNode.getX();
                }
            }
        }
        if (signExtend) {
            // If we were using sign extended values before restore the sign extension.
            offset = offset.graph().addOrUnique(new SignExtendNode(offset, 64));
        }
        return IndexedLocationNode.create(locationIdentity, accessKind, displacement, offset, offset.graph(), indexScaling);
    }

    public IndexedLocationNode createArrayLocation(Graph graph, Kind elementKind, ValueNode index, boolean initialization) {
        LocationIdentity loc = initialization ? initLocationIdentity() : NamedLocationIdentity.getArrayLocation(elementKind);
        return IndexedLocationNode.create(loc, elementKind, arrayBaseOffset(elementKind), index, graph, arrayScalingFactor(elementKind));
    }

    protected GuardingNode createBoundsCheck(AccessIndexedNode n, LoweringTool tool) {
        StructuredGraph graph = n.graph();
        ValueNode array = n.array();
        ValueNode arrayLength = readArrayLength(array, tool.getConstantReflection());
        if (arrayLength == null) {
            Stamp stamp = StampFactory.positiveInt();
            ReadNode readArrayLength = graph.add(new ReadNode(array, ConstantLocationNode.create(ARRAY_LENGTH_LOCATION, Kind.Int, arrayLengthOffset(), graph), stamp, BarrierType.NONE));
            graph.addBeforeFixed(n, readArrayLength);
            readArrayLength.setGuard(createNullCheck(array, readArrayLength, tool));
            arrayLength = readArrayLength;
        } else {
            arrayLength = arrayLength.isAlive() ? arrayLength : graph.addOrUniqueWithInputs(arrayLength);
        }

        if (arrayLength.isConstant() && n.index().isConstant()) {
            int l = arrayLength.asConstant().asInt();
            int i = n.index().asConstant().asInt();
            if (i >= 0 && i < l) {
                // unneeded range check
                return null;
            }
        }

        return tool.createGuard(n, graph.unique(new IntegerBelowNode(n.index(), arrayLength)), BoundsCheckException, InvalidateReprofile);
    }

    protected GuardingNode createNullCheck(ValueNode object, FixedNode before, LoweringTool tool) {
        if (StampTool.isObjectNonNull(object)) {
            return null;
        }
        return tool.createGuard(before, before.graph().unique(new IsNullNode(object)), DeoptimizationReason.NullCheckException, DeoptimizationAction.InvalidateReprofile, true);
    }

    @Override
    public ValueNode reconstructArrayIndex(LocationNode location) {
        Kind elementKind = location.getValueKind();
        assert location.getLocationIdentity().equals(NamedLocationIdentity.getArrayLocation(elementKind));

        long base;
        ValueNode index;
        int scale = arrayScalingFactor(elementKind);

        if (location instanceof ConstantLocationNode) {
            base = ((ConstantLocationNode) location).getDisplacement();
            index = null;
        } else if (location instanceof IndexedLocationNode) {
            IndexedLocationNode indexedLocation = (IndexedLocationNode) location;
            assert indexedLocation.getIndexScaling() == scale;
            base = indexedLocation.getDisplacement();
            index = indexedLocation.getIndex();
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }

        base -= arrayBaseOffset(elementKind);
        assert base >= 0 && base % scale == 0;

        base /= scale;
        assert NumUtil.isInt(base);

        StructuredGraph graph = location.graph();
        if (index == null) {
            return ConstantNode.forInt((int) base, graph);
        } else {
            if (base == 0) {
                return index;
            } else {
                return IntegerArithmeticNode.add(graph, ConstantNode.forInt((int) base, graph), index);
            }
        }
    }
}
