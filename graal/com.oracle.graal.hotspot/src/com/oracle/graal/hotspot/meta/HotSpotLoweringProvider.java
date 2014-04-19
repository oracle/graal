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
package com.oracle.graal.hotspot.meta;

import static com.oracle.graal.api.code.MemoryBarriers.*;
import static com.oracle.graal.api.meta.DeoptimizationAction.*;
import static com.oracle.graal.api.meta.DeoptimizationReason.*;
import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.hotspot.meta.HotSpotForeignCallsProviderImpl.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.hotspot.replacements.NewObjectSnippets.*;
import static com.oracle.graal.nodes.java.ArrayLengthNode.*;
import static com.oracle.graal.phases.GraalOptions.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.debug.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.nodes.type.*;
import com.oracle.graal.hotspot.replacements.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.HeapAccess.BarrierType;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.debug.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.replacements.*;

/**
 * HotSpot implementation of {@link LoweringProvider}.
 */
public class HotSpotLoweringProvider implements LoweringProvider {

    protected final HotSpotGraalRuntime runtime;
    protected final MetaAccessProvider metaAccess;
    protected final ForeignCallsProvider foreignCalls;
    protected final HotSpotRegistersProvider registers;

    protected CheckCastDynamicSnippets.Templates checkcastDynamicSnippets;
    protected InstanceOfSnippets.Templates instanceofSnippets;
    protected NewObjectSnippets.Templates newObjectSnippets;
    protected MonitorSnippets.Templates monitorSnippets;
    protected WriteBarrierSnippets.Templates writeBarrierSnippets;
    protected BoxingSnippets.Templates boxingSnippets;
    protected LoadExceptionObjectSnippets.Templates exceptionObjectSnippets;
    protected UnsafeLoadSnippets.Templates unsafeLoadSnippets;

    public HotSpotLoweringProvider(HotSpotGraalRuntime runtime, MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, HotSpotRegistersProvider registers) {
        this.runtime = runtime;
        this.metaAccess = metaAccess;
        this.foreignCalls = foreignCalls;
        this.registers = registers;
    }

    public void initialize(HotSpotProviders providers, HotSpotVMConfig config) {
        TargetDescription target = providers.getCodeCache().getTarget();
        checkcastDynamicSnippets = new CheckCastDynamicSnippets.Templates(providers, target);
        instanceofSnippets = new InstanceOfSnippets.Templates(providers, target);
        newObjectSnippets = new NewObjectSnippets.Templates(providers, target);
        monitorSnippets = new MonitorSnippets.Templates(providers, target, config.useFastLocking);
        writeBarrierSnippets = new WriteBarrierSnippets.Templates(providers, target, config.useCompressedOops ? config.getOopEncoding() : null);
        boxingSnippets = new BoxingSnippets.Templates(providers, providers.getSnippetReflection(), target);
        exceptionObjectSnippets = new LoadExceptionObjectSnippets.Templates(providers, target);
        unsafeLoadSnippets = new UnsafeLoadSnippets.Templates(providers, target);
        providers.getReplacements().registerSnippetTemplateCache(new UnsafeArrayCopySnippets.Templates(providers, target));
    }

    @Override
    public void lower(Node n, LoweringTool tool) {
        StructuredGraph graph = (StructuredGraph) n.graph();

        if (n instanceof ArrayLengthNode) {
            lowerArrayLengthNode((ArrayLengthNode) n, tool);
        } else if (n instanceof Invoke) {
            lowerInvoke((Invoke) n, tool, graph);
        } else if (n instanceof LoadFieldNode) {
            lowerLoadFieldNode((LoadFieldNode) n, tool);
        } else if (n instanceof StoreFieldNode) {
            lowerStoreFieldNode((StoreFieldNode) n, tool);
        } else if (n instanceof CompareAndSwapNode) {
            lowerCompareAndSwapNode((CompareAndSwapNode) n);
        } else if (n instanceof AtomicReadAndWriteNode) {
            lowerAtomicReadAndWriteNode((AtomicReadAndWriteNode) n);
        } else if (n instanceof LoadIndexedNode) {
            lowerLoadIndexedNode((LoadIndexedNode) n, tool);
        } else if (n instanceof StoreIndexedNode) {
            lowerStoreIndexedNode((StoreIndexedNode) n, tool);
        } else if (n instanceof UnsafeLoadNode) {
            lowerUnsafeLoadNode((UnsafeLoadNode) n, tool);
        } else if (n instanceof UnsafeStoreNode) {
            lowerUnsafeStoreNode((UnsafeStoreNode) n);
        } else if (n instanceof JavaReadNode) {
            lowerJavaReadNode((JavaReadNode) n);
        } else if (n instanceof JavaWriteNode) {
            lowerJavaWriteNode((JavaWriteNode) n);
        } else if (n instanceof LoadHubNode) {
            lowerLoadHubNode((LoadHubNode) n);
        } else if (n instanceof LoadMethodNode) {
            lowerLoadMethodNode((LoadMethodNode) n);
        } else if (n instanceof StoreHubNode) {
            lowerStoreHubNode((StoreHubNode) n, graph);
        } else if (n instanceof CommitAllocationNode) {
            lowerCommitAllocationNode((CommitAllocationNode) n, tool);
        } else if (n instanceof OSRStartNode) {
            lowerOSRStartNode((OSRStartNode) n);
        } else if (n instanceof DynamicCounterNode) {
            lowerDynamicCounterNode((DynamicCounterNode) n);
        } else if (n instanceof BytecodeExceptionNode) {
            lowerBytecodeExceptionNode((BytecodeExceptionNode) n);
        } else if (n instanceof CheckCastDynamicNode) {
            checkcastDynamicSnippets.lower((CheckCastDynamicNode) n, tool);
        } else if (n instanceof InstanceOfNode) {
            if (graph.getGuardsStage() == StructuredGraph.GuardsStage.FIXED_DEOPTS) {
                instanceofSnippets.lower((InstanceOfNode) n, tool);
            }
        } else if (n instanceof InstanceOfDynamicNode) {
            if (graph.getGuardsStage() == StructuredGraph.GuardsStage.FIXED_DEOPTS) {
                instanceofSnippets.lower((InstanceOfDynamicNode) n, tool);
            }
        } else if (n instanceof NewInstanceNode) {
            if (graph.getGuardsStage() == StructuredGraph.GuardsStage.AFTER_FSA) {
                newObjectSnippets.lower((NewInstanceNode) n, registers, tool);
            }
        } else if (n instanceof DynamicNewInstanceNode) {
            if (graph.getGuardsStage() == StructuredGraph.GuardsStage.AFTER_FSA) {
                newObjectSnippets.lower((DynamicNewInstanceNode) n, registers, tool);
            }
        } else if (n instanceof NewArrayNode) {
            if (graph.getGuardsStage() == StructuredGraph.GuardsStage.AFTER_FSA) {
                newObjectSnippets.lower((NewArrayNode) n, registers, tool);
            }
        } else if (n instanceof DynamicNewArrayNode) {
            if (graph.getGuardsStage() == StructuredGraph.GuardsStage.AFTER_FSA) {
                newObjectSnippets.lower((DynamicNewArrayNode) n, registers, tool);
            }
        } else if (n instanceof MonitorEnterNode) {
            if (graph.getGuardsStage() == StructuredGraph.GuardsStage.AFTER_FSA) {
                monitorSnippets.lower((MonitorEnterNode) n, registers, tool);
            }
        } else if (n instanceof MonitorExitNode) {
            if (graph.getGuardsStage() == StructuredGraph.GuardsStage.AFTER_FSA) {
                monitorSnippets.lower((MonitorExitNode) n, tool);
            }
        } else if (n instanceof G1PreWriteBarrier) {
            writeBarrierSnippets.lower((G1PreWriteBarrier) n, registers, tool);
        } else if (n instanceof G1PostWriteBarrier) {
            writeBarrierSnippets.lower((G1PostWriteBarrier) n, registers, tool);
        } else if (n instanceof G1ReferentFieldReadBarrier) {
            writeBarrierSnippets.lower((G1ReferentFieldReadBarrier) n, registers, tool);
        } else if (n instanceof SerialWriteBarrier) {
            writeBarrierSnippets.lower((SerialWriteBarrier) n, tool);
        } else if (n instanceof SerialArrayRangeWriteBarrier) {
            writeBarrierSnippets.lower((SerialArrayRangeWriteBarrier) n, tool);
        } else if (n instanceof G1ArrayRangePreWriteBarrier) {
            writeBarrierSnippets.lower((G1ArrayRangePreWriteBarrier) n, registers, tool);
        } else if (n instanceof G1ArrayRangePostWriteBarrier) {
            writeBarrierSnippets.lower((G1ArrayRangePostWriteBarrier) n, registers, tool);
        } else if (n instanceof NewMultiArrayNode) {
            if (graph.getGuardsStage() == StructuredGraph.GuardsStage.AFTER_FSA) {
                newObjectSnippets.lower((NewMultiArrayNode) n, tool);
            }
        } else if (n instanceof LoadExceptionObjectNode) {
            exceptionObjectSnippets.lower((LoadExceptionObjectNode) n, registers, tool);
        } else if (n instanceof IntegerDivNode || n instanceof IntegerRemNode || n instanceof UnsignedDivNode || n instanceof UnsignedRemNode) {
            // Nothing to do for division nodes. The HotSpot signal handler catches divisions by
            // zero and the MIN_VALUE / -1 cases.
        } else if (n instanceof BoxNode) {
            boxingSnippets.lower((BoxNode) n, tool);
        } else if (n instanceof UnboxNode) {
            boxingSnippets.lower((UnboxNode) n, tool);
        } else if (n instanceof DeoptimizeNode || n instanceof UnwindNode) {
            /* No lowering, we generate LIR directly for these nodes. */
        } else {
            throw GraalInternalError.shouldNotReachHere("Node implementing Lowerable not handled: " + n);
        }
    }

    private void lowerArrayLengthNode(ArrayLengthNode arrayLengthNode, LoweringTool tool) {
        StructuredGraph graph = arrayLengthNode.graph();
        ValueNode array = arrayLengthNode.array();
        ReadNode arrayLengthRead = graph.add(new ReadNode(array, ConstantLocationNode.create(ARRAY_LENGTH_LOCATION, Kind.Int, runtime.getConfig().arrayLengthOffset, graph),
                        StampFactory.positiveInt(), BarrierType.NONE, false));
        arrayLengthRead.setGuard(createNullCheck(array, arrayLengthNode, tool));
        graph.replaceFixedWithFixed(arrayLengthNode, arrayLengthRead);
    }

    private void lowerInvoke(Invoke invoke, LoweringTool tool, StructuredGraph graph) {
        if (invoke.callTarget() instanceof MethodCallTargetNode) {
            MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
            NodeInputList<ValueNode> parameters = callTarget.arguments();
            ValueNode receiver = parameters.size() <= 0 ? null : parameters.get(0);
            GuardingNode receiverNullCheck = null;
            if (!callTarget.isStatic() && receiver.stamp() instanceof ObjectStamp && !ObjectStamp.isObjectNonNull(receiver)) {
                receiverNullCheck = createNullCheck(receiver, invoke.asNode(), tool);
                invoke.setGuard(receiverNullCheck);
            }
            JavaType[] signature = MetaUtil.signatureToTypes(callTarget.targetMethod().getSignature(), callTarget.isStatic() ? null : callTarget.targetMethod().getDeclaringClass());

            LoweredCallTargetNode loweredCallTarget = null;
            if (callTarget.invokeKind() == InvokeKind.Virtual && InlineVTableStubs.getValue() && (AlwaysInlineVTableStubs.getValue() || invoke.isPolymorphic())) {

                HotSpotResolvedJavaMethod hsMethod = (HotSpotResolvedJavaMethod) callTarget.targetMethod();
                if (!hsMethod.getDeclaringClass().isInterface()) {
                    if (hsMethod.isInVirtualMethodTable()) {
                        int vtableEntryOffset = hsMethod.vtableEntryOffset();
                        assert vtableEntryOffset > 0;
                        Kind wordKind = runtime.getTarget().wordKind;
                        ValueNode hub = createReadHub(graph, wordKind, receiver, receiverNullCheck);

                        ReadNode metaspaceMethod = createReadVirtualMethod(graph, wordKind, hub, hsMethod);
                        // We use LocationNode.ANY_LOCATION for the reads that access the
                        // compiled code entry as HotSpot does not guarantee they are final
                        // values.
                        ReadNode compiledEntry = graph.add(new ReadNode(metaspaceMethod, ConstantLocationNode.create(ANY_LOCATION, wordKind, runtime.getConfig().methodCompiledEntryOffset, graph),
                                        StampFactory.forKind(wordKind), BarrierType.NONE, false));

                        loweredCallTarget = graph.add(new HotSpotIndirectCallTargetNode(metaspaceMethod, compiledEntry, parameters, invoke.asNode().stamp(), signature, callTarget.targetMethod(),
                                        CallingConvention.Type.JavaCall));

                        graph.addBeforeFixed(invoke.asNode(), metaspaceMethod);
                        graph.addAfterFixed(metaspaceMethod, compiledEntry);
                    }
                }
            }

            if (loweredCallTarget == null) {
                loweredCallTarget = graph.add(new HotSpotDirectCallTargetNode(parameters, invoke.asNode().stamp(), signature, callTarget.targetMethod(), CallingConvention.Type.JavaCall,
                                callTarget.invokeKind()));
            }
            callTarget.replaceAndDelete(loweredCallTarget);
        }
    }

    private Stamp loadStamp(Stamp stamp, Kind kind) {
        return loadStamp(stamp, kind, true);
    }

    private Stamp loadStamp(Stamp stamp, Kind kind, boolean compressible) {
        switch (kind) {
            case Boolean:
            case Byte:
                return StampTool.narrowingConversion(stamp, 8);

            case Char:
            case Short:
                return StampTool.narrowingConversion(stamp, 16);

            case Object:
                if (compressible && runtime.getConfig().useCompressedOops) {
                    return new NarrowOopStamp((ObjectStamp) stamp, runtime.getConfig().getOopEncoding());
                }
        }
        return stamp;
    }

    private ValueNode implicitLoadConvert(StructuredGraph graph, Kind kind, ValueNode value) {
        return implicitLoadConvert(graph, kind, value, true);
    }

    private ValueNode implicitLoadConvert(StructuredGraph graph, Kind kind, ValueNode value, boolean compressible) {
        switch (kind) {
            case Byte:
            case Short:
                return graph.unique(new SignExtendNode(value, 32));

            case Boolean:
            case Char:
                return graph.unique(new ZeroExtendNode(value, 32));

            case Object:
                if (compressible && runtime.getConfig().useCompressedOops) {
                    return CompressionNode.uncompress(value, runtime.getConfig().getOopEncoding());
                }
        }
        return value;
    }

    private void lowerLoadFieldNode(LoadFieldNode loadField, LoweringTool tool) {
        StructuredGraph graph = loadField.graph();
        HotSpotResolvedJavaField field = (HotSpotResolvedJavaField) loadField.field();
        ValueNode object = loadField.isStatic() ? ConstantNode.forConstant(HotSpotObjectConstant.forObject(field.getDeclaringClass().mirror()), metaAccess, graph) : loadField.object();
        assert loadField.getKind() != Kind.Illegal;
        BarrierType barrierType = getFieldLoadBarrierType(field);

        Stamp loadStamp = loadStamp(loadField.stamp(), field.getKind());
        ReadNode memoryRead = graph.add(new ReadNode(object, createFieldLocation(graph, field, false), loadStamp, barrierType, false));
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

    private ValueNode implicitStoreConvert(StructuredGraph graph, Kind kind, ValueNode value) {
        return implicitStoreConvert(graph, kind, value, true);
    }

    private ValueNode implicitStoreConvert(StructuredGraph graph, Kind kind, ValueNode value, boolean compressible) {
        switch (kind) {
            case Boolean:
            case Byte:
                return graph.unique(new NarrowNode(value, 8));
            case Char:
            case Short:
                return graph.unique(new NarrowNode(value, 16));
            case Object:
                if (compressible && runtime.getConfig().useCompressedOops) {
                    return CompressionNode.compress(value, runtime.getConfig().getOopEncoding());
                }
        }
        return value;
    }

    private void lowerStoreFieldNode(StoreFieldNode storeField, LoweringTool tool) {
        StructuredGraph graph = storeField.graph();
        HotSpotResolvedJavaField field = (HotSpotResolvedJavaField) storeField.field();
        ValueNode object = storeField.isStatic() ? ConstantNode.forConstant(HotSpotObjectConstant.forObject(field.getDeclaringClass().mirror()), metaAccess, graph) : storeField.object();
        BarrierType barrierType = getFieldStoreBarrierType(storeField);

        ValueNode value = implicitStoreConvert(graph, storeField.field().getKind(), storeField.value());
        WriteNode memoryWrite = graph.add(new WriteNode(object, value, createFieldLocation(graph, field, false), barrierType, false));
        memoryWrite.setStateAfter(storeField.stateAfter());
        graph.replaceFixedWithFixed(storeField, memoryWrite);
        memoryWrite.setGuard(createNullCheck(object, memoryWrite, tool));
        FixedWithNextNode last = memoryWrite;
        FixedWithNextNode first = memoryWrite;

        if (storeField.isVolatile()) {
            MembarNode preMembar = graph.add(new MembarNode(JMM_PRE_VOLATILE_WRITE));
            graph.addBeforeFixed(first, preMembar);
            MembarNode postMembar = graph.add(new MembarNode(JMM_POST_VOLATILE_WRITE));
            graph.addAfterFixed(last, postMembar);
        }
    }

    private void lowerCompareAndSwapNode(CompareAndSwapNode cas) {
        StructuredGraph graph = cas.graph();
        Kind valueKind = cas.getValueKind();
        LocationNode location = IndexedLocationNode.create(cas.getLocationIdentity(), valueKind, cas.displacement(), cas.offset(), graph, 1);

        ValueNode expectedValue = implicitStoreConvert(graph, valueKind, cas.expected(), true);
        ValueNode newValue = implicitStoreConvert(graph, valueKind, cas.newValue(), true);

        LoweredCompareAndSwapNode atomicNode = graph.add(new LoweredCompareAndSwapNode(cas.object(), location, expectedValue, newValue, getCompareAndSwapBarrierType(cas), false));
        atomicNode.setStateAfter(cas.stateAfter());
        graph.replaceFixedWithFixed(cas, atomicNode);
    }

    private void lowerAtomicReadAndWriteNode(AtomicReadAndWriteNode n) {
        StructuredGraph graph = n.graph();
        Kind valueKind = n.getValueKind();
        LocationNode location = IndexedLocationNode.create(n.getLocationIdentity(), valueKind, 0, n.offset(), graph, 1);

        ValueNode newValue = implicitStoreConvert(graph, valueKind, n.newValue());

        LoweredAtomicReadAndWriteNode memoryRead = graph.add(new LoweredAtomicReadAndWriteNode(n.object(), location, newValue, getAtomicReadAndWriteBarrierType(n), false));
        memoryRead.setStateAfter(n.stateAfter());

        ValueNode readValue = implicitLoadConvert(graph, valueKind, memoryRead);

        n.replaceAtUsages(readValue);
        graph.replaceFixedWithFixed(n, memoryRead);
    }

    private void lowerLoadIndexedNode(LoadIndexedNode loadIndexed, LoweringTool tool) {
        StructuredGraph graph = loadIndexed.graph();
        Kind elementKind = loadIndexed.elementKind();
        LocationNode arrayLocation = createArrayLocation(graph, elementKind, loadIndexed.index(), false);

        Stamp loadStamp = loadStamp(loadIndexed.stamp(), elementKind);
        ReadNode memoryRead = graph.add(new ReadNode(loadIndexed.array(), arrayLocation, loadStamp, BarrierType.NONE, false));
        ValueNode readValue = implicitLoadConvert(graph, elementKind, memoryRead);

        memoryRead.setGuard(createBoundsCheck(loadIndexed, tool));

        loadIndexed.replaceAtUsages(readValue);
        graph.replaceFixed(loadIndexed, memoryRead);
    }

    private void lowerStoreIndexedNode(StoreIndexedNode storeIndexed, LoweringTool tool) {
        StructuredGraph graph = storeIndexed.graph();
        GuardingNode boundsCheck = createBoundsCheck(storeIndexed, tool);
        Kind elementKind = storeIndexed.elementKind();
        LocationNode arrayLocation = createArrayLocation(graph, elementKind, storeIndexed.index(), false);

        ValueNode value = storeIndexed.value();
        ValueNode array = storeIndexed.array();

        CheckCastNode checkcastNode = null;
        CheckCastDynamicNode checkcastDynamicNode = null;
        if (elementKind == Kind.Object && !ObjectStamp.isObjectAlwaysNull(value)) {
            // Store check!
            ResolvedJavaType arrayType = ObjectStamp.typeOrNull(array);
            if (arrayType != null && ObjectStamp.isExactType(array)) {
                ResolvedJavaType elementType = arrayType.getComponentType();
                if (!MetaUtil.isJavaLangObject(elementType)) {
                    checkcastNode = graph.add(new CheckCastNode(elementType, value, null, true));
                    graph.addBeforeFixed(storeIndexed, checkcastNode);
                    value = checkcastNode;
                }
            } else {
                Kind wordKind = runtime.getTarget().wordKind;
                ValueNode arrayClass = createReadHub(graph, wordKind, array, boundsCheck);
                LocationNode location = ConstantLocationNode.create(FINAL_LOCATION, wordKind, runtime.getConfig().arrayClassElementOffset, graph);
                /*
                 * Anchor the read of the element klass to the cfg, because it is only valid when
                 * arrayClass is an object class, which might not be the case in other parts of the
                 * compiled method.
                 */
                FloatingReadNode arrayElementKlass = graph.unique(new FloatingReadNode(arrayClass, location, null, StampFactory.forKind(wordKind), BeginNode.prevBegin(storeIndexed)));
                checkcastDynamicNode = graph.add(new CheckCastDynamicNode(arrayElementKlass, value, true));
                graph.addBeforeFixed(storeIndexed, checkcastDynamicNode);
                value = checkcastDynamicNode;
            }
        }
        BarrierType barrierType = getArrayStoreBarrierType(storeIndexed);
        WriteNode memoryWrite = graph.add(new WriteNode(array, implicitStoreConvert(graph, elementKind, value), arrayLocation, barrierType, false));
        memoryWrite.setGuard(boundsCheck);
        memoryWrite.setStateAfter(storeIndexed.stateAfter());
        graph.replaceFixedWithFixed(storeIndexed, memoryWrite);

        // Lower the associated checkcast node.
        if (checkcastNode != null) {
            checkcastNode.lower(tool);
        } else if (checkcastDynamicNode != null) {
            checkcastDynamicSnippets.lower(checkcastDynamicNode, tool);
        }
    }

    private ReadNode createUnsafeRead(StructuredGraph graph, UnsafeLoadNode load, GuardingNode guard) {
        boolean compressible = (!load.object().isNullConstant() && load.accessKind() == Kind.Object);
        Kind readKind = load.accessKind();
        LocationNode location = createLocation(load);
        Stamp loadStamp = loadStamp(load.stamp(), readKind, compressible);
        ReadNode memoryRead = graph.add(new ReadNode(load.object(), location, loadStamp, guard, BarrierType.NONE, false));
        ValueNode readValue = implicitLoadConvert(graph, readKind, memoryRead, compressible);
        load.replaceAtUsages(readValue);
        return memoryRead;
    }

    private void lowerUnsafeLoadNode(UnsafeLoadNode load, LoweringTool tool) {
        StructuredGraph graph = load.graph();
        if (load.getGuardingCondition() != null) {
            ConditionAnchorNode valueAnchorNode = graph.add(new ConditionAnchorNode(load.getGuardingCondition()));
            ReadNode memoryRead = createUnsafeRead(graph, load, valueAnchorNode);
            graph.replaceFixedWithFixed(load, valueAnchorNode);
            graph.addAfterFixed(valueAnchorNode, memoryRead);
        } else if (graph.getGuardsStage().ordinal() > StructuredGraph.GuardsStage.FLOATING_GUARDS.ordinal()) {
            assert load.getKind() != Kind.Illegal;
            if (addReadBarrier(load)) {
                unsafeLoadSnippets.lower(load, tool);
            } else {
                ReadNode memoryRead = createUnsafeRead(graph, load, null);
                // An unsafe read must not float outside its block otherwise
                // it may float above an explicit null check on its object.
                memoryRead.setGuard(BeginNode.prevBegin(load));
                graph.replaceFixedWithFixed(load, memoryRead);
            }
        }
    }

    private void lowerUnsafeStoreNode(UnsafeStoreNode store) {
        StructuredGraph graph = store.graph();
        LocationNode location = createLocation(store);
        ValueNode object = store.object();
        BarrierType barrierType = getUnsafeStoreBarrierType(store);
        boolean compressible = store.value().getKind() == Kind.Object;
        Kind valueKind = store.accessKind();
        ValueNode value = implicitStoreConvert(graph, valueKind, store.value(), compressible);
        WriteNode write = graph.add(new WriteNode(object, value, location, barrierType, false));
        write.setStateAfter(store.stateAfter());
        graph.replaceFixedWithFixed(store, write);
    }

    private void lowerJavaReadNode(JavaReadNode read) {
        StructuredGraph graph = read.graph();

        Kind valueKind = read.location().getValueKind();
        Stamp loadStamp = loadStamp(read.stamp(), valueKind, read.isCompressible());
        ReadNode memoryRead = graph.add(new ReadNode(read.object(), read.location(), loadStamp, read.getBarrierType(), false));
        ValueNode readValue = implicitLoadConvert(graph, valueKind, memoryRead, read.isCompressible());

        memoryRead.setGuard(read.getGuard());

        read.replaceAtUsages(readValue);
        graph.replaceFixed(read, memoryRead);
    }

    private void lowerJavaWriteNode(JavaWriteNode write) {
        StructuredGraph graph = write.graph();

        Kind valueKind = write.location().getValueKind();
        ValueNode value = implicitStoreConvert(graph, valueKind, write.value(), write.isCompressible());

        WriteNode memoryWrite = graph.add(new WriteNode(write.object(), value, write.location(), write.getBarrierType(), false, write.isInitialization()));
        memoryWrite.setStateAfter(write.stateAfter());
        graph.replaceFixedWithFixed(write, memoryWrite);

        memoryWrite.setGuard(write.getGuard());
    }

    private void lowerLoadHubNode(LoadHubNode loadHub) {
        StructuredGraph graph = loadHub.graph();
        if (graph.getGuardsStage().ordinal() >= StructuredGraph.GuardsStage.FIXED_DEOPTS.ordinal()) {
            Kind wordKind = runtime.getTarget().wordKind;
            assert loadHub.getKind() == wordKind;
            ValueNode object = loadHub.object();
            GuardingNode guard = loadHub.getGuard();
            ValueNode hub = createReadHub(graph, wordKind, object, guard);
            graph.replaceFloating(loadHub, hub);
        }
    }

    private void lowerLoadMethodNode(LoadMethodNode loadMethodNode) {
        StructuredGraph graph = loadMethodNode.graph();
        ResolvedJavaMethod method = loadMethodNode.getMethod();
        ReadNode metaspaceMethod = createReadVirtualMethod(graph, runtime.getTarget().wordKind, loadMethodNode.getHub(), method);
        graph.replaceFixed(loadMethodNode, metaspaceMethod);
    }

    private void lowerStoreHubNode(StoreHubNode storeHub, StructuredGraph graph) {
        WriteNode hub = createWriteHub(graph, runtime.getTarget().wordKind, storeHub.getObject(), storeHub.getValue());
        graph.replaceFixed(storeHub, hub);
    }

    private void lowerCommitAllocationNode(CommitAllocationNode commit, LoweringTool tool) {
        StructuredGraph graph = commit.graph();
        if (graph.getGuardsStage() == StructuredGraph.GuardsStage.FIXED_DEOPTS) {
            ValueNode[] allocations = new ValueNode[commit.getVirtualObjects().size()];
            BitSet omittedValues = new BitSet();
            int valuePos = 0;
            for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
                VirtualObjectNode virtual = commit.getVirtualObjects().get(objIndex);
                int entryCount = virtual.entryCount();
                FixedWithNextNode newObject;
                if (virtual instanceof VirtualInstanceNode) {
                    newObject = graph.add(new NewInstanceNode(virtual.type(), true));
                } else {
                    newObject = graph.add(new NewArrayNode(((VirtualArrayNode) virtual).componentType(), ConstantNode.forInt(entryCount, graph), true));
                }
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
                        ConstantLocationNode location;
                        BarrierType barrierType;
                        if (virtual instanceof VirtualInstanceNode) {
                            ResolvedJavaField field = ((VirtualInstanceNode) virtual).field(i);
                            location = ConstantLocationNode.create(INIT_LOCATION, accessKind, ((HotSpotResolvedJavaField) field).offset(), graph);
                            barrierType = (entryKind == Kind.Object && !useDeferredInitBarriers()) ? BarrierType.IMPRECISE : BarrierType.NONE;
                        } else {
                            location = ConstantLocationNode.create(INIT_LOCATION, accessKind, getArrayBaseOffset(entryKind) + i * getScalingFactor(entryKind), graph);
                            barrierType = (entryKind == Kind.Object && !useDeferredInitBarriers()) ? BarrierType.PRECISE : BarrierType.NONE;
                        }
                        WriteNode write = new WriteNode(newObject, implicitStoreConvert(graph, entryKind, value), location, barrierType, false);
                        graph.addAfterFixed(newObject, graph.add(write));
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
                            WriteNode write;
                            if (virtual instanceof VirtualInstanceNode) {
                                VirtualInstanceNode virtualInstance = (VirtualInstanceNode) virtual;
                                write = new WriteNode(newObject, implicitStoreConvert(graph, Kind.Object, allocValue), createFieldLocation(graph, (HotSpotResolvedJavaField) virtualInstance.field(i),
                                                true), BarrierType.IMPRECISE, false);
                            } else {
                                write = new WriteNode(newObject, implicitStoreConvert(graph, Kind.Object, allocValue), createArrayLocation(graph, virtual.entryKind(i), ConstantNode.forInt(i, graph),
                                                true), BarrierType.PRECISE, false);
                            }
                            graph.addBeforeFixed(commit, graph.add(write));
                        }
                    }
                    valuePos++;
                }
            }

            finishAllocatedObjects(tool, commit, allocations);
            graph.removeFixed(commit);
        }
    }

    private void lowerOSRStartNode(OSRStartNode osrStart) {
        StructuredGraph graph = osrStart.graph();
        if (graph.getGuardsStage() == StructuredGraph.GuardsStage.FIXED_DEOPTS) {
            StartNode newStart = graph.add(new StartNode());
            ParameterNode buffer = graph.unique(new ParameterNode(0, StampFactory.forKind(runtime.getTarget().wordKind)));
            ForeignCallNode migrationEnd = graph.add(new ForeignCallNode(foreignCalls, OSR_MIGRATION_END, buffer));
            migrationEnd.setStateAfter(osrStart.stateAfter());

            newStart.setNext(migrationEnd);
            FixedNode next = osrStart.next();
            osrStart.setNext(null);
            migrationEnd.setNext(next);
            graph.setStart(newStart);

            // mirroring the calculations in c1_GraphBuilder.cpp (setup_osr_entry_block)
            int localsOffset = (graph.method().getMaxLocals() - 1) * 8;
            for (OSRLocalNode osrLocal : graph.getNodes(OSRLocalNode.class)) {
                int size = HIRFrameStateBuilder.stackSlots(osrLocal.getKind());
                int offset = localsOffset - (osrLocal.index() + size - 1) * 8;
                IndexedLocationNode location = IndexedLocationNode.create(ANY_LOCATION, osrLocal.getKind(), offset, ConstantNode.forLong(0, graph), graph, 1);
                ReadNode load = graph.add(new ReadNode(buffer, location, osrLocal.stamp(), BarrierType.NONE, false));
                osrLocal.replaceAndDelete(load);
                graph.addBeforeFixed(migrationEnd, load);
            }
            osrStart.replaceAtUsages(newStart);
            osrStart.safeDelete();
        }
    }

    private void lowerDynamicCounterNode(DynamicCounterNode n) {
        StructuredGraph graph = n.graph();
        if (graph.getGuardsStage() == StructuredGraph.GuardsStage.AFTER_FSA) {
            BenchmarkCounters.lower(n, registers, runtime.getConfig(), runtime.getTarget().wordKind);
        }
    }

    static final class Exceptions {
        protected static final ArrayIndexOutOfBoundsException cachedArrayIndexOutOfBoundsException;
        protected static final NullPointerException cachedNullPointerException;

        static {
            cachedArrayIndexOutOfBoundsException = new ArrayIndexOutOfBoundsException();
            cachedArrayIndexOutOfBoundsException.setStackTrace(new StackTraceElement[0]);
            cachedNullPointerException = new NullPointerException();
            cachedNullPointerException.setStackTrace(new StackTraceElement[0]);
        }
    }

    public static final class RuntimeCalls {
        public static final ForeignCallDescriptor CREATE_NULL_POINTER_EXCEPTION = new ForeignCallDescriptor("createNullPointerException", NullPointerException.class);
        public static final ForeignCallDescriptor CREATE_OUT_OF_BOUNDS_EXCEPTION = new ForeignCallDescriptor("createOutOfBoundsException", ArrayIndexOutOfBoundsException.class, int.class);
    }

    private void lowerBytecodeExceptionNode(BytecodeExceptionNode node) {
        StructuredGraph graph = node.graph();
        if (graph.getGuardsStage() == StructuredGraph.GuardsStage.FLOATING_GUARDS) {
            if (OmitHotExceptionStacktrace.getValue()) {
                Throwable exception;
                if (node.getExceptionClass() == NullPointerException.class) {
                    exception = Exceptions.cachedNullPointerException;
                } else if (node.getExceptionClass() == ArrayIndexOutOfBoundsException.class) {
                    exception = Exceptions.cachedArrayIndexOutOfBoundsException;
                } else {
                    throw GraalInternalError.shouldNotReachHere();
                }
                FloatingNode exceptionNode = ConstantNode.forConstant(HotSpotObjectConstant.forObject(exception), metaAccess, graph);
                graph.replaceFixedWithFloating(node, exceptionNode);

            } else {
                ForeignCallDescriptor descriptor;
                if (node.getExceptionClass() == NullPointerException.class) {
                    descriptor = RuntimeCalls.CREATE_NULL_POINTER_EXCEPTION;
                } else if (node.getExceptionClass() == ArrayIndexOutOfBoundsException.class) {
                    descriptor = RuntimeCalls.CREATE_OUT_OF_BOUNDS_EXCEPTION;
                } else {
                    throw GraalInternalError.shouldNotReachHere();
                }

                ForeignCallNode foreignCallNode = graph.add(new ForeignCallNode(foreignCalls, descriptor, node.stamp(), node.getArguments()));
                graph.replaceFixedWithFixed(node, foreignCallNode);
            }
        }
    }

    protected static void finishAllocatedObjects(LoweringTool tool, CommitAllocationNode commit, ValueNode[] allocations) {
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

    private static LocationNode createLocation(UnsafeAccessNode access) {
        return createLocation(access.offset(), access.getLocationIdentity(), access.accessKind());
    }

    private static LocationNode createLocation(ValueNode offsetNode, LocationIdentity locationIdentity, Kind accessKind) {
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
                offset = extend.getInput();
            }
        }
        if (offset instanceof IntegerAddNode) {
            IntegerAddNode integerAddNode = (IntegerAddNode) offset;
            if (integerAddNode.y() instanceof ConstantNode) {
                displacement = integerAddNode.y().asConstant().asLong();
                offset = integerAddNode.x();
            }
        }

        if (offset instanceof LeftShiftNode) {
            LeftShiftNode leftShiftNode = (LeftShiftNode) offset;
            if (leftShiftNode.y() instanceof ConstantNode) {
                long shift = leftShiftNode.y().asConstant().asLong();
                if (shift >= 1 && shift <= 3) {
                    if (shift == 1) {
                        indexScaling = 2;
                    } else if (shift == 2) {
                        indexScaling = 4;
                    } else {
                        indexScaling = 8;
                    }
                    offset = leftShiftNode.x();
                }
            }
        }
        if (signExtend) {
            // If we were using sign extended values before restore the sign extension.
            offset = offset.graph().addOrUnique(new SignExtendNode(offset, 64));
        }
        return IndexedLocationNode.create(locationIdentity, accessKind, displacement, offset, offset.graph(), indexScaling);
    }

    private static boolean addReadBarrier(UnsafeLoadNode load) {
        if (useG1GC() && load.graph().getGuardsStage() == StructuredGraph.GuardsStage.FIXED_DEOPTS && load.object().getKind() == Kind.Object && load.accessKind() == Kind.Object &&
                        !ObjectStamp.isObjectAlwaysNull(load.object())) {
            ResolvedJavaType type = ObjectStamp.typeOrNull(load.object());
            if (type != null && !type.isArray()) {
                return true;
            }
        }
        return false;
    }

    private static ReadNode createReadVirtualMethod(StructuredGraph graph, Kind wordKind, ValueNode hub, ResolvedJavaMethod method) {
        HotSpotResolvedJavaMethod hsMethod = (HotSpotResolvedJavaMethod) method;
        assert !hsMethod.getDeclaringClass().isInterface();
        assert hsMethod.isInVirtualMethodTable();

        int vtableEntryOffset = hsMethod.vtableEntryOffset();
        assert vtableEntryOffset > 0;
        // We use LocationNode.ANY_LOCATION for the reads that access the vtable
        // entry as HotSpot does not guarantee that this is a final value.
        ReadNode metaspaceMethod = graph.add(new ReadNode(hub, ConstantLocationNode.create(ANY_LOCATION, wordKind, vtableEntryOffset, graph), StampFactory.forKind(wordKind), BarrierType.NONE, false));
        return metaspaceMethod;
    }

    private ValueNode createReadHub(StructuredGraph graph, Kind wordKind, ValueNode object, GuardingNode guard) {
        HotSpotVMConfig config = runtime.getConfig();
        LocationNode location = ConstantLocationNode.create(FINAL_LOCATION, wordKind, config.hubOffset, graph);
        assert !object.isConstant() || object.asConstant().isNull();

        Stamp hubStamp;
        if (config.useCompressedClassPointers) {
            hubStamp = StampFactory.forInteger(32);
        } else {
            hubStamp = StampFactory.forKind(wordKind);
        }

        FloatingReadNode memoryRead = graph.unique(new FloatingReadNode(object, location, null, hubStamp, guard, BarrierType.NONE, false));
        if (config.useCompressedClassPointers) {
            return CompressionNode.uncompress(memoryRead, config.getKlassEncoding());
        } else {
            return memoryRead;
        }
    }

    private WriteNode createWriteHub(StructuredGraph graph, Kind wordKind, ValueNode object, ValueNode value) {
        HotSpotVMConfig config = runtime.getConfig();
        LocationNode location = ConstantLocationNode.create(HUB_LOCATION, wordKind, config.hubOffset, graph);
        assert !object.isConstant() || object.asConstant().isNull();

        ValueNode writeValue = value;
        if (config.useCompressedClassPointers) {
            writeValue = CompressionNode.compress(value, config.getKlassEncoding());
        }

        return graph.add(new WriteNode(object, writeValue, location, BarrierType.NONE, false));
    }

    private static BarrierType getFieldLoadBarrierType(HotSpotResolvedJavaField loadField) {
        BarrierType barrierType = BarrierType.NONE;
        if (config().useG1GC && loadField.getKind() == Kind.Object && loadField.getDeclaringClass().mirror() == java.lang.ref.Reference.class && loadField.getName().equals("referent")) {
            barrierType = BarrierType.PRECISE;
        }
        return barrierType;
    }

    private static BarrierType getFieldStoreBarrierType(StoreFieldNode storeField) {
        if (storeField.field().getKind() == Kind.Object) {
            return BarrierType.IMPRECISE;
        }
        return BarrierType.NONE;
    }

    private static BarrierType getArrayStoreBarrierType(StoreIndexedNode store) {
        if (store.elementKind() == Kind.Object) {
            return BarrierType.PRECISE;
        }
        return BarrierType.NONE;
    }

    private static BarrierType getUnsafeStoreBarrierType(UnsafeStoreNode store) {
        if (store.value().getKind() == Kind.Object) {
            ResolvedJavaType type = ObjectStamp.typeOrNull(store.object());
            if (type != null && !type.isArray()) {
                return BarrierType.IMPRECISE;
            } else {
                return BarrierType.PRECISE;
            }
        }
        return BarrierType.NONE;
    }

    private static BarrierType getCompareAndSwapBarrierType(CompareAndSwapNode cas) {
        if (cas.expected().getKind() == Kind.Object) {
            ResolvedJavaType type = ObjectStamp.typeOrNull(cas.object());
            if (type != null && !type.isArray()) {
                return BarrierType.IMPRECISE;
            } else {
                return BarrierType.PRECISE;
            }
        }
        return BarrierType.NONE;
    }

    private static BarrierType getAtomicReadAndWriteBarrierType(AtomicReadAndWriteNode n) {
        if (n.newValue().getKind() == Kind.Object) {
            ResolvedJavaType type = ObjectStamp.typeOrNull(n.object());
            if (type != null && !type.isArray()) {
                return BarrierType.IMPRECISE;
            } else {
                return BarrierType.PRECISE;
            }
        }
        return BarrierType.NONE;
    }

    protected static ConstantLocationNode createFieldLocation(StructuredGraph graph, HotSpotResolvedJavaField field, boolean initialization) {
        LocationIdentity loc = initialization ? INIT_LOCATION : field;
        return ConstantLocationNode.create(loc, field.getKind(), field.offset(), graph);
    }

    public int getScalingFactor(Kind kind) {
        if (useCompressedOops() && kind == Kind.Object) {
            return this.runtime.getTarget().getSizeInBytes(Kind.Int);
        } else {
            return this.runtime.getTarget().getSizeInBytes(kind);
        }
    }

    protected IndexedLocationNode createArrayLocation(Graph graph, Kind elementKind, ValueNode index, boolean initialization) {
        LocationIdentity loc = initialization ? INIT_LOCATION : NamedLocationIdentity.getArrayLocation(elementKind);
        int scale = getScalingFactor(elementKind);
        return IndexedLocationNode.create(loc, elementKind, getArrayBaseOffset(elementKind), index, graph, scale);
    }

    @Override
    public ValueNode reconstructArrayIndex(LocationNode location) {
        Kind elementKind = location.getValueKind();
        assert location.getLocationIdentity().equals(NamedLocationIdentity.getArrayLocation(elementKind));

        long base;
        ValueNode index;
        int scale = getScalingFactor(elementKind);

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

        base -= getArrayBaseOffset(elementKind);
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

    private GuardingNode createBoundsCheck(AccessIndexedNode n, LoweringTool tool) {
        StructuredGraph g = n.graph();
        ValueNode array = n.array();
        ValueNode arrayLength = readArrayLength(n.graph(), array, tool.getConstantReflection());
        if (arrayLength == null) {
            Stamp stamp = StampFactory.positiveInt();
            ReadNode readArrayLength = g.add(new ReadNode(array, ConstantLocationNode.create(ARRAY_LENGTH_LOCATION, Kind.Int, runtime.getConfig().arrayLengthOffset, g), stamp, BarrierType.NONE, false));
            g.addBeforeFixed(n, readArrayLength);
            readArrayLength.setGuard(createNullCheck(array, readArrayLength, tool));
            arrayLength = readArrayLength;
        }

        if (arrayLength.isConstant() && n.index().isConstant()) {
            int l = arrayLength.asConstant().asInt();
            int i = n.index().asConstant().asInt();
            if (i >= 0 && i < l) {
                // unneeded range check
                return null;
            }
        }

        return tool.createGuard(n, g.unique(new IntegerBelowThanNode(n.index(), arrayLength)), BoundsCheckException, InvalidateReprofile);
    }

    private static GuardingNode createNullCheck(ValueNode object, FixedNode before, LoweringTool tool) {
        if (ObjectStamp.isObjectNonNull(object)) {
            return null;
        }
        return tool.createGuard(before, before.graph().unique(new IsNullNode(object)), DeoptimizationReason.NullCheckException, DeoptimizationAction.InvalidateReprofile, true);
    }

}
