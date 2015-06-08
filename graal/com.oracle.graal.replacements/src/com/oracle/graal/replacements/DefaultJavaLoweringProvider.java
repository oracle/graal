/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.nodes.java.ArrayLengthNode.*;
import static com.oracle.jvmci.code.MemoryBarriers.*;
import static com.oracle.jvmci.meta.DeoptimizationAction.*;
import static com.oracle.jvmci.meta.DeoptimizationReason.*;
import static com.oracle.jvmci.meta.LocationIdentity.*;

import java.util.*;

import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.debug.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.memory.HeapAccess.BarrierType;
import com.oracle.graal.nodes.memory.*;
import com.oracle.graal.nodes.memory.address.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.phases.util.*;
import com.oracle.jvmci.code.*;
import com.oracle.jvmci.common.*;
import com.oracle.jvmci.meta.*;

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
        providers.getReplacements().registerSnippetTemplateCache(new SnippetCounterNode.SnippetCounterSnippets.Templates(providers, snippetReflection, target));
    }

    @Override
    public void lower(Node n, LoweringTool tool) {
        assert n instanceof Lowerable;
        StructuredGraph graph = (StructuredGraph) n.graph();
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
        } else if (n instanceof MonitorEnterNode) {
            lowerMonitorEnterNode((MonitorEnterNode) n, tool, graph);
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
        } else if (n instanceof TypeCheckNode) {
            lowerTypeCheckNode((TypeCheckNode) n, tool, graph);
        } else if (n instanceof VerifyHeapNode) {
            lowerVerifyHeap((VerifyHeapNode) n);
        } else {
            throw JVMCIError.shouldNotReachHere("Node implementing Lowerable not handled: " + n);
        }
    }

    private void lowerTypeCheckNode(TypeCheckNode n, LoweringTool tool, StructuredGraph graph) {
        ValueNode hub = createReadHub(graph, n.getValue(), null);
        ValueNode clazz = graph.unique(ConstantNode.forConstant(tool.getStampProvider().createHubStamp((ObjectStamp) n.getValue().stamp()), n.type().getObjectHub(), tool.getMetaAccess()));
        LogicNode objectEquals = graph.unique(PointerEqualsNode.create(hub, clazz));
        n.replaceAndDelete(objectEquals);
    }

    protected void lowerVerifyHeap(VerifyHeapNode n) {
        GraphUtil.removeFixedWithUnusedInputs(n);
    }

    protected static AddressNode createOffsetAddress(StructuredGraph graph, ValueNode object, long offset) {
        ValueNode o = ConstantNode.forLong(offset, graph);
        return graph.unique(new OffsetAddressNode(object, o));
    }

    protected AddressNode createFieldAddress(StructuredGraph graph, ValueNode object, ResolvedJavaField field) {
        int offset = fieldOffset(field);
        if (offset >= 0) {
            return createOffsetAddress(graph, object, offset);
        } else {
            return null;
        }
    }

    protected void lowerLoadFieldNode(LoadFieldNode loadField, LoweringTool tool) {
        assert loadField.getKind() != Kind.Illegal;
        StructuredGraph graph = loadField.graph();
        ResolvedJavaField field = loadField.field();
        ValueNode object = loadField.isStatic() ? staticFieldBase(graph, field) : loadField.object();
        Stamp loadStamp = loadStamp(loadField.stamp(), field.getKind());

        AddressNode address = createFieldAddress(graph, object, field);
        assert address != null : "Field that is loaded must not be eliminated: " + field.getDeclaringClass().toJavaName(true) + "." + field.getName();

        ReadNode memoryRead = graph.add(new ReadNode(address, field.getLocationIdentity(), loadStamp, fieldLoadBarrierType(field)));
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
        AddressNode address = createFieldAddress(graph, object, field);
        assert address != null;

        WriteNode memoryWrite = graph.add(new WriteNode(address, field.getLocationIdentity(), value, fieldStoreBarrierType(storeField.field())));
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

    public AddressNode createArrayAddress(StructuredGraph graph, ValueNode array, Kind elementKind, ValueNode index) {
        ValueNode longIndex = graph.unique(new SignExtendNode(index, 64));

        int shift = CodeUtil.log2(arrayScalingFactor(elementKind));
        ValueNode scaledIndex = graph.unique(new LeftShiftNode(longIndex, ConstantNode.forInt(shift, graph)));

        int base = arrayBaseOffset(elementKind);
        ValueNode offset = graph.unique(new AddNode(scaledIndex, ConstantNode.forLong(base, graph)));

        return graph.unique(new OffsetAddressNode(array, offset));
    }

    protected void lowerLoadIndexedNode(LoadIndexedNode loadIndexed, LoweringTool tool) {
        StructuredGraph graph = loadIndexed.graph();
        Kind elementKind = loadIndexed.elementKind();
        Stamp loadStamp = loadStamp(loadIndexed.stamp(), elementKind);

        PiNode pi = getBoundsCheckedIndex(loadIndexed, tool);
        ValueNode checkedIndex = pi;
        if (checkedIndex == null) {
            checkedIndex = loadIndexed.index();
        }

        AddressNode address = createArrayAddress(graph, loadIndexed.array(), elementKind, checkedIndex);
        ReadNode memoryRead = graph.add(new ReadNode(address, NamedLocationIdentity.getArrayLocation(elementKind), loadStamp, BarrierType.NONE));
        ValueNode readValue = implicitLoadConvert(graph, elementKind, memoryRead);

        if (pi != null) {
            memoryRead.setGuard(pi.getGuard());
        }

        loadIndexed.replaceAtUsages(readValue);
        graph.replaceFixed(loadIndexed, memoryRead);
    }

    protected void lowerStoreIndexedNode(StoreIndexedNode storeIndexed, LoweringTool tool) {
        StructuredGraph graph = storeIndexed.graph();

        PiNode pi = getBoundsCheckedIndex(storeIndexed, tool);
        ValueNode checkedIndex;
        GuardingNode boundsCheck;
        if (pi == null) {
            checkedIndex = storeIndexed.index();
            boundsCheck = null;
        } else {
            checkedIndex = pi;
            boundsCheck = pi.getGuard();
        }

        Kind elementKind = storeIndexed.elementKind();

        ValueNode value = storeIndexed.value();
        ValueNode array = storeIndexed.array();
        FixedWithNextNode checkCastNode = null;
        if (elementKind == Kind.Object && !StampTool.isPointerAlwaysNull(value)) {
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

        AddressNode address = createArrayAddress(graph, array, elementKind, checkedIndex);
        WriteNode memoryWrite = graph.add(new WriteNode(address, NamedLocationIdentity.getArrayLocation(elementKind), implicitStoreConvert(graph, elementKind, value),
                        arrayStoreBarrierType(storeIndexed.elementKind())));
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

        AddressNode address = createOffsetAddress(graph, array, arrayLengthOffset());
        ReadNode arrayLengthRead = graph.add(new ReadNode(address, ARRAY_LENGTH_LOCATION, StampFactory.positiveInt(), BarrierType.NONE));
        arrayLengthRead.setGuard(createNullCheck(array, arrayLengthNode, tool));
        graph.replaceFixedWithFixed(arrayLengthNode, arrayLengthRead);
    }

    protected void lowerLoadHubNode(LoadHubNode loadHub) {
        StructuredGraph graph = loadHub.graph();
        if (graph.getGuardsStage().allowsFloatingGuards()) {
            return;
        }
        ValueNode hub = createReadHub(graph, loadHub.getValue(), loadHub.getGuard());
        graph.replaceFloating(loadHub, hub);
    }

    protected void lowerMonitorEnterNode(MonitorEnterNode monitorEnter, LoweringTool tool, StructuredGraph graph) {
        ValueNode object = monitorEnter.object();
        GuardingNode nullCheck = createNullCheck(object, monitorEnter, tool);
        if (nullCheck != null) {
            object = graph.unique(new PiNode(object, ((ObjectStamp) object.stamp()).improveWith(StampFactory.objectNonNull()), (ValueNode) nullCheck));
        }
        ValueNode hub = graph.addOrUnique(LoadHubNode.create(object, tool.getStampProvider(), tool.getMetaAccess()));
        RawMonitorEnterNode rawMonitorEnter = graph.add(new RawMonitorEnterNode(object, hub, monitorEnter.getMonitorId()));
        rawMonitorEnter.setStateBefore(monitorEnter.stateBefore());
        rawMonitorEnter.setStateAfter(monitorEnter.stateAfter());
        graph.replaceFixedWithFixed(monitorEnter, rawMonitorEnter);
    }

    protected void lowerCompareAndSwapNode(CompareAndSwapNode cas) {
        StructuredGraph graph = cas.graph();
        Kind valueKind = cas.getValueKind();

        ValueNode expectedValue = implicitStoreConvert(graph, valueKind, cas.expected());
        ValueNode newValue = implicitStoreConvert(graph, valueKind, cas.newValue());

        AddressNode address = graph.unique(new OffsetAddressNode(cas.object(), cas.offset()));
        LoweredCompareAndSwapNode atomicNode = graph.add(new LoweredCompareAndSwapNode(address, cas.getLocationIdentity(), expectedValue, newValue, compareAndSwapBarrierType(cas)));
        atomicNode.setStateAfter(cas.stateAfter());
        graph.replaceFixedWithFixed(cas, atomicNode);
    }

    protected void lowerAtomicReadAndWriteNode(AtomicReadAndWriteNode n) {
        StructuredGraph graph = n.graph();
        Kind valueKind = n.getValueKind();

        ValueNode newValue = implicitStoreConvert(graph, valueKind, n.newValue());

        AddressNode address = graph.unique(new OffsetAddressNode(n.object(), n.offset()));
        LoweredAtomicReadAndWriteNode memoryRead = graph.add(new LoweredAtomicReadAndWriteNode(address, n.getLocationIdentity(), newValue, atomicReadAndWriteBarrierType(n)));
        memoryRead.setStateAfter(n.stateAfter());

        ValueNode readValue = implicitLoadConvert(graph, valueKind, memoryRead);
        n.stateAfter().replaceFirstInput(n, memoryRead);
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
        } else {
            assert load.getKind() != Kind.Illegal;
            ReadNode memoryRead = createUnsafeRead(graph, load, null);
            graph.replaceFixedWithFixed(load, memoryRead);
        }
    }

    protected AddressNode createUnsafeAddress(StructuredGraph graph, ValueNode object, ValueNode offset) {
        if (object.isConstant() && object.asConstant().isDefaultForKind()) {
            return graph.unique(new RawAddressNode(offset));
        } else {
            return graph.unique(new OffsetAddressNode(object, offset));
        }
    }

    protected ReadNode createUnsafeRead(StructuredGraph graph, UnsafeLoadNode load, GuardingNode guard) {
        boolean compressible = load.accessKind() == Kind.Object;
        Kind readKind = load.accessKind();
        Stamp loadStamp = loadStamp(load.stamp(), readKind, compressible);
        AddressNode address = createUnsafeAddress(graph, load.object(), load.offset());
        ReadNode memoryRead = graph.add(new ReadNode(address, load.getLocationIdentity(), loadStamp, guard, BarrierType.NONE));
        if (guard == null) {
            // An unsafe read must not float otherwise it may float above
            // a test guaranteeing the read is safe.
            memoryRead.setForceFixed(true);
        }
        ValueNode readValue = implicitLoadConvert(graph, readKind, memoryRead, compressible);
        load.replaceAtUsages(readValue);
        return memoryRead;
    }

    protected void lowerUnsafeStoreNode(UnsafeStoreNode store) {
        StructuredGraph graph = store.graph();
        boolean compressible = store.value().getKind() == Kind.Object;
        Kind valueKind = store.accessKind();
        ValueNode value = implicitStoreConvert(graph, valueKind, store.value(), compressible);
        AddressNode address = createUnsafeAddress(graph, store.object(), store.offset());
        WriteNode write = graph.add(new WriteNode(address, store.getLocationIdentity(), value, unsafeStoreBarrierType(store)));
        write.setStateAfter(store.stateAfter());
        graph.replaceFixedWithFixed(store, write);
    }

    protected void lowerJavaReadNode(JavaReadNode read) {
        StructuredGraph graph = read.graph();
        Kind valueKind = read.getReadKind();
        Stamp loadStamp = loadStamp(read.stamp(), valueKind, read.isCompressible());

        ReadNode memoryRead = graph.add(new ReadNode(read.getAddress(), read.getLocationIdentity(), loadStamp, read.getBarrierType()));
        GuardingNode guard = read.getGuard();
        ValueNode readValue = implicitLoadConvert(graph, valueKind, memoryRead, read.isCompressible());
        if (guard == null) {
            // An unsafe read must not float otherwise it may float above
            // a test guaranteeing the read is safe.
            memoryRead.setForceFixed(true);
        } else {
            memoryRead.setGuard(guard);
        }
        read.replaceAtUsages(readValue);
        graph.replaceFixed(read, memoryRead);
    }

    protected void lowerJavaWriteNode(JavaWriteNode write) {
        StructuredGraph graph = write.graph();
        Kind valueKind = write.getWriteKind();
        ValueNode value = implicitStoreConvert(graph, valueKind, write.value(), write.isCompressible());

        WriteNode memoryWrite = graph.add(new WriteNode(write.getAddress(), write.getLocationIdentity(), value, write.getBarrierType(), write.isInitialization()));
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
                    newObject = graph.add(createNewInstanceFromVirtual(virtual));
                } else {
                    newObject = graph.add(createNewArrayFromVirtual(virtual, ConstantNode.forInt(entryCount, graph)));
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
                        assert valueKind.getStackKind() == entryKind.getStackKind() ||
                                        (valueKind == Kind.Long || valueKind == Kind.Double || (valueKind == Kind.Int && virtual instanceof VirtualArrayNode));
                        AddressNode address = null;
                        BarrierType barrierType = null;
                        if (virtual instanceof VirtualInstanceNode) {
                            ResolvedJavaField field = ((VirtualInstanceNode) virtual).field(i);
                            long offset = fieldOffset(field);
                            if (offset >= 0) {
                                address = createOffsetAddress(graph, newObject, offset);
                                barrierType = fieldInitializationBarrier(entryKind);
                            }
                        } else {
                            address = createOffsetAddress(graph, newObject, arrayBaseOffset(entryKind) + i * arrayScalingFactor(entryKind));
                            barrierType = arrayInitializationBarrier(entryKind);
                        }
                        if (address != null) {
                            WriteNode write = new WriteNode(address, initLocationIdentity(), implicitStoreConvert(graph, entryKind, value), barrierType);
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
                            AddressNode address;
                            BarrierType barrierType;
                            if (virtual instanceof VirtualInstanceNode) {
                                VirtualInstanceNode virtualInstance = (VirtualInstanceNode) virtual;
                                address = createFieldAddress(graph, newObject, virtualInstance.field(i));
                                barrierType = BarrierType.IMPRECISE;
                            } else {
                                address = createArrayAddress(graph, newObject, virtual.entryKind(i), ConstantNode.forInt(i, graph));
                                barrierType = BarrierType.PRECISE;
                            }
                            if (address != null) {
                                WriteNode write = new WriteNode(address, initLocationIdentity(), implicitStoreConvert(graph, Kind.Object, allocValue), barrierType);
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

    public NewInstanceNode createNewInstanceFromVirtual(VirtualObjectNode virtual) {
        return new NewInstanceNode(virtual.type(), true);
    }

    protected NewArrayNode createNewArrayFromVirtual(VirtualObjectNode virtual, ValueNode length) {
        return new NewArrayNode(((VirtualArrayNode) virtual).componentType(), length, true);
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
                return IntegerStamp.OPS.getNarrow().foldStamp(32, 8, stamp);
            case Char:
            case Short:
                return IntegerStamp.OPS.getNarrow().foldStamp(32, 16, stamp);
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

    protected PiNode getBoundsCheckedIndex(AccessIndexedNode n, LoweringTool tool) {
        StructuredGraph graph = n.graph();
        ValueNode array = n.array();
        ValueNode arrayLength = readArrayLength(array, tool.getConstantReflection());
        if (arrayLength == null) {
            Stamp stamp = StampFactory.positiveInt();
            AddressNode address = createOffsetAddress(graph, array, arrayLengthOffset());
            ReadNode readArrayLength = graph.add(new ReadNode(address, ARRAY_LENGTH_LOCATION, stamp, BarrierType.NONE));
            graph.addBeforeFixed(n, readArrayLength);
            readArrayLength.setGuard(createNullCheck(array, readArrayLength, tool));
            arrayLength = readArrayLength;
        } else {
            arrayLength = arrayLength.isAlive() ? arrayLength : graph.addOrUniqueWithInputs(arrayLength);
        }

        if (arrayLength.isConstant() && n.index().isConstant()) {
            int l = arrayLength.asJavaConstant().asInt();
            int i = n.index().asJavaConstant().asInt();
            if (i >= 0 && i < l) {
                // unneeded range check
                return null;
            }
        }

        GuardingNode guard = tool.createGuard(n, graph.unique(new IntegerBelowNode(n.index(), arrayLength)), BoundsCheckException, InvalidateReprofile);
        IntegerStamp lengthStamp = (IntegerStamp) arrayLength.stamp();
        IntegerStamp indexStamp = StampFactory.forInteger(32, 0, lengthStamp.upperBound());
        return graph.unique(new PiNode(n.index(), indexStamp, guard.asNode()));
    }

    protected GuardingNode createNullCheck(ValueNode object, FixedNode before, LoweringTool tool) {
        if (StampTool.isPointerNonNull(object)) {
            return null;
        }
        return tool.createGuard(before, before.graph().unique(new IsNullNode(object)), DeoptimizationReason.NullCheckException, DeoptimizationAction.InvalidateReprofile, true);
    }

    @Override
    public ValueNode reconstructArrayIndex(Kind elementKind, AddressNode address) {
        StructuredGraph graph = address.graph();
        ValueNode offset = ((OffsetAddressNode) address).getOffset();

        int base = arrayBaseOffset(elementKind);
        ValueNode scaledIndex = graph.unique(new SubNode(offset, ConstantNode.forIntegerStamp(offset.stamp(), base, graph)));

        int shift = CodeUtil.log2(arrayScalingFactor(elementKind));
        ValueNode ret = graph.unique(new RightShiftNode(scaledIndex, ConstantNode.forInt(shift, graph)));
        return IntegerConvertNode.convert(ret, StampFactory.forKind(Kind.Int), graph);
    }

    @Override
    public int getSizeInBytes(Stamp stamp) {
        if (stamp instanceof PrimitiveStamp) {
            return ((PrimitiveStamp) stamp).getBits() / 8;
        } else if (stamp instanceof AbstractPointerStamp) {
            return target.wordSize;
        } else {
            throw JVMCIError.shouldNotReachHere("stamp " + stamp + " has no size");
        }
    }
}
