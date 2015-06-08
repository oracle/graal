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
package com.oracle.graal.hotspot.meta;

import com.oracle.jvmci.code.ForeignCallsProvider;
import com.oracle.jvmci.code.CallingConvention;
import com.oracle.jvmci.code.TargetDescription;
import com.oracle.jvmci.meta.ResolvedJavaField;
import com.oracle.jvmci.meta.JavaType;
import com.oracle.jvmci.meta.MetaAccessProvider;
import com.oracle.jvmci.meta.JavaConstant;
import com.oracle.jvmci.meta.LocationIdentity;
import com.oracle.jvmci.meta.ResolvedJavaType;
import com.oracle.jvmci.meta.ForeignCallDescriptor;
import com.oracle.jvmci.meta.Kind;

import static com.oracle.jvmci.meta.LocationIdentity.*;
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.hotspot.meta.HotSpotForeignCallsProviderImpl.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.hotspot.replacements.NewObjectSnippets.*;

import java.lang.ref.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.nodes.type.*;
import com.oracle.graal.hotspot.replacements.*;
import com.oracle.graal.hotspot.replacements.arraycopy.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.debug.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.memory.*;
import com.oracle.graal.nodes.memory.HeapAccess.BarrierType;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.jvmci.common.*;
import com.oracle.jvmci.hotspot.*;

/**
 * HotSpot implementation of {@link LoweringProvider}.
 */
public class DefaultHotSpotLoweringProvider extends DefaultJavaLoweringProvider implements HotSpotLoweringProvider {

    protected final HotSpotGraalRuntimeProvider runtime;
    protected final ForeignCallsProvider foreignCalls;
    protected final HotSpotRegistersProvider registers;

    protected CheckCastDynamicSnippets.Templates checkcastDynamicSnippets;
    protected InstanceOfSnippets.Templates instanceofSnippets;
    protected NewObjectSnippets.Templates newObjectSnippets;
    protected MonitorSnippets.Templates monitorSnippets;
    protected WriteBarrierSnippets.Templates writeBarrierSnippets;
    protected LoadExceptionObjectSnippets.Templates exceptionObjectSnippets;
    protected UnsafeLoadSnippets.Templates unsafeLoadSnippets;
    protected AssertionSnippets.Templates assertionSnippets;
    protected ArrayCopySnippets.Templates arraycopySnippets;

    public DefaultHotSpotLoweringProvider(HotSpotGraalRuntimeProvider runtime, MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, HotSpotRegistersProvider registers,
                    TargetDescription target) {
        super(metaAccess, target);
        this.runtime = runtime;
        this.foreignCalls = foreignCalls;
        this.registers = registers;
    }

    public void initialize(HotSpotProviders providers, HotSpotVMConfig config) {
        super.initialize(providers, providers.getSnippetReflection());

        assert target == providers.getCodeCache().getTarget();
        checkcastDynamicSnippets = new CheckCastDynamicSnippets.Templates(providers, target);
        instanceofSnippets = new InstanceOfSnippets.Templates(providers, target);
        newObjectSnippets = new NewObjectSnippets.Templates(providers, target);
        monitorSnippets = new MonitorSnippets.Templates(providers, target, config.useFastLocking);
        writeBarrierSnippets = new WriteBarrierSnippets.Templates(providers, target, config.useCompressedOops ? config.getOopEncoding() : null);
        exceptionObjectSnippets = new LoadExceptionObjectSnippets.Templates(providers, target);
        unsafeLoadSnippets = new UnsafeLoadSnippets.Templates(providers, target);
        assertionSnippets = new AssertionSnippets.Templates(providers, target);
        arraycopySnippets = new ArrayCopySnippets.Templates(providers, target);
        providers.getReplacements().registerSnippetTemplateCache(new UnsafeArrayCopySnippets.Templates(providers, target));
    }

    @Override
    public void lower(Node n, LoweringTool tool) {
        StructuredGraph graph = (StructuredGraph) n.graph();
        if (n instanceof Invoke) {
            lowerInvoke((Invoke) n, tool, graph);
        } else if (n instanceof LoadMethodNode) {
            lowerLoadMethodNode((LoadMethodNode) n);
        } else if (n instanceof GetClassNode) {
            lowerGetClassNode((GetClassNode) n, tool, graph);
        } else if (n instanceof StoreHubNode) {
            lowerStoreHubNode((StoreHubNode) n, graph);
        } else if (n instanceof OSRStartNode) {
            lowerOSRStartNode((OSRStartNode) n);
        } else if (n instanceof BytecodeExceptionNode) {
            lowerBytecodeExceptionNode((BytecodeExceptionNode) n);
        } else if (n instanceof CheckCastDynamicNode) {
            checkcastDynamicSnippets.lower((CheckCastDynamicNode) n, tool);
        } else if (n instanceof InstanceOfNode) {
            if (graph.getGuardsStage().areDeoptsFixed()) {
                instanceofSnippets.lower((InstanceOfNode) n, tool);
            }
        } else if (n instanceof InstanceOfDynamicNode) {
            if (graph.getGuardsStage().areDeoptsFixed()) {
                instanceofSnippets.lower((InstanceOfDynamicNode) n, tool);
            }
        } else if (n instanceof ClassIsAssignableFromNode) {
            if (graph.getGuardsStage().areDeoptsFixed()) {
                instanceofSnippets.lower((ClassIsAssignableFromNode) n, tool);
            }
        } else if (n instanceof NewInstanceNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                newObjectSnippets.lower((NewInstanceNode) n, registers, tool);
            }
        } else if (n instanceof DynamicNewInstanceNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                newObjectSnippets.lower((DynamicNewInstanceNode) n, registers, tool);
            }
        } else if (n instanceof NewArrayNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                newObjectSnippets.lower((NewArrayNode) n, registers, runtime, tool);
            }
        } else if (n instanceof DynamicNewArrayNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                newObjectSnippets.lower((DynamicNewArrayNode) n, registers, tool);
            }
        } else if (n instanceof VerifyHeapNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                newObjectSnippets.lower((VerifyHeapNode) n, registers, runtime, tool);
            }
        } else if (n instanceof RawMonitorEnterNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                monitorSnippets.lower((RawMonitorEnterNode) n, registers, tool);
            }
        } else if (n instanceof MonitorExitNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                monitorSnippets.lower((MonitorExitNode) n, tool);
            }
        } else if (n instanceof ArrayCopyNode) {
            arraycopySnippets.lower((ArrayCopyNode) n, tool);
        } else if (n instanceof ArrayCopySlowPathNode) {
            ArrayCopySlowPathNode slowpath = (ArrayCopySlowPathNode) n;
            // FrameState stateAfter = slowpath.stateAfter();
            arraycopySnippets.lower(slowpath, tool);
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
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                newObjectSnippets.lower((NewMultiArrayNode) n, tool);
            }
        } else if (n instanceof LoadExceptionObjectNode) {
            exceptionObjectSnippets.lower((LoadExceptionObjectNode) n, registers, tool);
        } else if (n instanceof AssertionNode) {
            assertionSnippets.lower((AssertionNode) n, tool);
        } else if (n instanceof IntegerDivNode || n instanceof IntegerRemNode || n instanceof UnsignedDivNode || n instanceof UnsignedRemNode) {
            // Nothing to do for division nodes. The HotSpot signal handler catches divisions by
            // zero and the MIN_VALUE / -1 cases.
        } else if (n instanceof AbstractDeoptimizeNode || n instanceof UnwindNode || n instanceof RemNode) {
            /* No lowering, we generate LIR directly for these nodes. */
        } else if (n instanceof ClassGetHubNode) {
            lowerClassGetHubNode((ClassGetHubNode) n, tool);
        } else if (n instanceof HubGetClassNode) {
            lowerHubGetClassNode((HubGetClassNode) n, tool);
        } else if (n instanceof KlassLayoutHelperNode) {
            lowerKlassLayoutHelperNode((KlassLayoutHelperNode) n, tool);
        } else {
            super.lower(n, tool);
        }
    }

    private void lowerKlassLayoutHelperNode(KlassLayoutHelperNode n, LoweringTool tool) {
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
            return;
        }
        StructuredGraph graph = n.graph();
        LocationNode location = graph.unique(new ConstantLocationNode(KLASS_LAYOUT_HELPER_LOCATION, runtime.getConfig().klassLayoutHelperOffset));
        assert !n.getHub().isConstant();
        graph.replaceFloating(n, graph.unique(new FloatingReadNode(n.getHub(), location, null, n.stamp(), n.getGuard(), BarrierType.NONE)));
    }

    private void lowerHubGetClassNode(HubGetClassNode n, LoweringTool tool) {
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
            return;
        }

        StructuredGraph graph = n.graph();
        LocationNode location = graph.unique(new ConstantLocationNode(CLASS_MIRROR_LOCATION, runtime.getConfig().classMirrorOffset));
        assert !n.getHub().isConstant();
        FloatingReadNode read = graph.unique(new FloatingReadNode(n.getHub(), location, null, n.stamp(), n.getGuard(), BarrierType.NONE));
        graph.replaceFloating(n, read);
    }

    private void lowerClassGetHubNode(ClassGetHubNode n, LoweringTool tool) {
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
            return;
        }

        StructuredGraph graph = n.graph();
        LocationNode location = graph.unique(new ConstantLocationNode(CLASS_KLASS_LOCATION, runtime.getConfig().klassOffset));
        assert !n.getValue().isConstant();
        FloatingReadNode read = graph.unique(new FloatingReadNode(n.getValue(), location, null, n.stamp(), n.getGuard(), BarrierType.NONE));
        graph.replaceFloating(n, read);
    }

    private void lowerInvoke(Invoke invoke, LoweringTool tool, StructuredGraph graph) {
        if (invoke.callTarget() instanceof MethodCallTargetNode) {
            MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
            NodeInputList<ValueNode> parameters = callTarget.arguments();
            ValueNode receiver = parameters.size() <= 0 ? null : parameters.get(0);
            GuardingNode receiverNullCheck = null;
            if (!callTarget.isStatic() && receiver.stamp() instanceof ObjectStamp && !StampTool.isPointerNonNull(receiver)) {
                receiverNullCheck = createNullCheck(receiver, invoke.asNode(), tool);
                invoke.setGuard(receiverNullCheck);
            }
            JavaType[] signature = callTarget.targetMethod().getSignature().toParameterTypes(callTarget.isStatic() ? null : callTarget.targetMethod().getDeclaringClass());

            LoweredCallTargetNode loweredCallTarget = null;
            if (InlineVTableStubs.getValue() && callTarget.invokeKind().isIndirect() && (AlwaysInlineVTableStubs.getValue() || invoke.isPolymorphic())) {
                HotSpotResolvedJavaMethod hsMethod = (HotSpotResolvedJavaMethod) callTarget.targetMethod();
                ResolvedJavaType receiverType = invoke.getReceiverType();
                if (hsMethod.isInVirtualMethodTable(receiverType)) {
                    Kind wordKind = runtime.getTarget().wordKind;
                    ValueNode hub = createReadHub(graph, receiver, receiverNullCheck);

                    ReadNode metaspaceMethod = createReadVirtualMethod(graph, hub, hsMethod, receiverType);
                    // We use LocationNode.ANY_LOCATION for the reads that access the
                    // compiled code entry as HotSpot does not guarantee they are final
                    // values.
                    int methodCompiledEntryOffset = runtime.getConfig().methodCompiledEntryOffset;
                    ReadNode compiledEntry = graph.add(new ReadNode(metaspaceMethod, graph.unique(new ConstantLocationNode(any(), methodCompiledEntryOffset)), StampFactory.forKind(wordKind),
                                    BarrierType.NONE));

                    loweredCallTarget = graph.add(new HotSpotIndirectCallTargetNode(metaspaceMethod, compiledEntry, parameters, invoke.asNode().stamp(), signature, callTarget.targetMethod(),
                                    CallingConvention.Type.JavaCall, callTarget.invokeKind()));

                    graph.addBeforeFixed(invoke.asNode(), metaspaceMethod);
                    graph.addAfterFixed(metaspaceMethod, compiledEntry);
                }
            }

            if (loweredCallTarget == null) {
                loweredCallTarget = graph.add(new HotSpotDirectCallTargetNode(parameters, invoke.asNode().stamp(), signature, callTarget.targetMethod(), CallingConvention.Type.JavaCall,
                                callTarget.invokeKind()));
            }
            callTarget.replaceAndDelete(loweredCallTarget);
        }
    }

    @Override
    protected Stamp loadStamp(Stamp stamp, Kind kind, boolean compressible) {
        if (kind == Kind.Object && compressible && runtime.getConfig().useCompressedOops) {
            return NarrowOopStamp.compressed((ObjectStamp) stamp, runtime.getConfig().getOopEncoding());
        }
        return super.loadStamp(stamp, kind, compressible);
    }

    @Override
    protected ValueNode implicitLoadConvert(StructuredGraph graph, Kind kind, ValueNode value, boolean compressible) {
        if (kind == Kind.Object && compressible && runtime.getConfig().useCompressedOops) {
            return CompressionNode.uncompress(value, runtime.getConfig().getOopEncoding());
        }
        return super.implicitLoadConvert(graph, kind, value, compressible);
    }

    @Override
    protected ValueNode staticFieldBase(StructuredGraph graph, ResolvedJavaField f) {
        HotSpotResolvedJavaField field = (HotSpotResolvedJavaField) f;
        JavaConstant base = field.getDeclaringClass().getJavaClass();
        return ConstantNode.forConstant(base, metaAccess, graph);
    }

    @Override
    protected ValueNode implicitStoreConvert(StructuredGraph graph, Kind kind, ValueNode value, boolean compressible) {
        if (kind == Kind.Object && compressible && runtime.getConfig().useCompressedOops) {
            return CompressionNode.compress(value, runtime.getConfig().getOopEncoding());
        }
        return super.implicitStoreConvert(graph, kind, value, compressible);
    }

    @Override
    protected ValueNode createReadArrayComponentHub(StructuredGraph graph, ValueNode arrayHub, FixedNode anchor) {
        LocationNode location = graph.unique(new ConstantLocationNode(OBJ_ARRAY_KLASS_ELEMENT_KLASS_LOCATION, runtime.getConfig().arrayClassElementOffset));
        /*
         * Anchor the read of the element klass to the cfg, because it is only valid when arrayClass
         * is an object class, which might not be the case in other parts of the compiled method.
         */
        return graph.unique(new FloatingReadNode(arrayHub, location, null, KlassPointerStamp.klassNonNull(), AbstractBeginNode.prevBegin(anchor)));
    }

    @Override
    protected void lowerUnsafeLoadNode(UnsafeLoadNode load, LoweringTool tool) {
        StructuredGraph graph = load.graph();
        if (load.getGuardingCondition() == null && !graph.getGuardsStage().allowsFloatingGuards() && addReadBarrier(load)) {
            unsafeLoadSnippets.lower(load, tool);
        } else {
            super.lowerUnsafeLoadNode(load, tool);
        }
    }

    private static void lowerLoadMethodNode(LoadMethodNode loadMethodNode) {
        StructuredGraph graph = loadMethodNode.graph();
        HotSpotResolvedJavaMethod method = (HotSpotResolvedJavaMethod) loadMethodNode.getMethod();
        ReadNode metaspaceMethod = createReadVirtualMethod(graph, loadMethodNode.getHub(), method, loadMethodNode.getReceiverType());
        graph.replaceFixed(loadMethodNode, metaspaceMethod);
    }

    private static void lowerGetClassNode(GetClassNode getClass, LoweringTool tool, StructuredGraph graph) {
        StampProvider stampProvider = tool.getStampProvider();
        LoadHubNode hub = graph.unique(new LoadHubNode(stampProvider, getClass.getObject()));
        HubGetClassNode hubGetClass = graph.unique(new HubGetClassNode(tool.getMetaAccess(), hub));
        graph.replaceFloating(getClass, hubGetClass);
        hub.lower(tool);
        hubGetClass.lower(tool);
    }

    private void lowerStoreHubNode(StoreHubNode storeHub, StructuredGraph graph) {
        WriteNode hub = createWriteHub(graph, storeHub.getObject(), storeHub.getValue());
        graph.replaceFixed(storeHub, hub);
    }

    @Override
    protected BarrierType fieldInitializationBarrier(Kind entryKind) {
        return (entryKind == Kind.Object && !runtime.getConfig().useDeferredInitBarriers) ? BarrierType.IMPRECISE : BarrierType.NONE;
    }

    @Override
    protected BarrierType arrayInitializationBarrier(Kind entryKind) {
        return (entryKind == Kind.Object && !runtime.getConfig().useDeferredInitBarriers) ? BarrierType.PRECISE : BarrierType.NONE;
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
            for (OSRLocalNode osrLocal : graph.getNodes(OSRLocalNode.TYPE)) {
                int size = osrLocal.getKind().getSlotCount();
                int offset = localsOffset - (osrLocal.index() + size - 1) * 8;
                IndexedLocationNode location = graph.unique(new IndexedLocationNode(any(), offset, ConstantNode.forLong(0, graph), 1));
                ReadNode load = graph.add(new ReadNode(buffer, location, osrLocal.stamp(), BarrierType.NONE));
                osrLocal.replaceAndDelete(load);
                graph.addBeforeFixed(migrationEnd, load);
            }
            osrStart.replaceAtUsages(newStart);
            osrStart.safeDelete();
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
        if (graph.getGuardsStage().allowsFloatingGuards()) {
            if (OmitHotExceptionStacktrace.getValue()) {
                Throwable exception;
                if (node.getExceptionClass() == NullPointerException.class) {
                    exception = Exceptions.cachedNullPointerException;
                } else if (node.getExceptionClass() == ArrayIndexOutOfBoundsException.class) {
                    exception = Exceptions.cachedArrayIndexOutOfBoundsException;
                } else {
                    throw JVMCIError.shouldNotReachHere();
                }
                FloatingNode exceptionNode = ConstantNode.forConstant(HotSpotObjectConstantImpl.forObject(exception), metaAccess, graph);
                graph.replaceFixedWithFloating(node, exceptionNode);

            } else {
                ForeignCallDescriptor descriptor;
                if (node.getExceptionClass() == NullPointerException.class) {
                    descriptor = RuntimeCalls.CREATE_NULL_POINTER_EXCEPTION;
                } else if (node.getExceptionClass() == ArrayIndexOutOfBoundsException.class) {
                    descriptor = RuntimeCalls.CREATE_OUT_OF_BOUNDS_EXCEPTION;
                } else {
                    throw JVMCIError.shouldNotReachHere();
                }

                ForeignCallNode foreignCallNode = graph.add(new ForeignCallNode(foreignCalls, descriptor, node.stamp(), node.getArguments()));
                graph.replaceFixedWithFixed(node, foreignCallNode);
            }
        }
    }

    private boolean addReadBarrier(UnsafeLoadNode load) {
        if (runtime.getConfig().useG1GC && load.graph().getGuardsStage() == StructuredGraph.GuardsStage.FIXED_DEOPTS && load.object().getKind() == Kind.Object && load.accessKind() == Kind.Object &&
                        !StampTool.isPointerAlwaysNull(load.object())) {
            ResolvedJavaType type = StampTool.typeOrNull(load.object());
            if (type != null && !type.isArray()) {
                return true;
            }
        }
        return false;
    }

    private static ReadNode createReadVirtualMethod(StructuredGraph graph, ValueNode hub, HotSpotResolvedJavaMethod method, ResolvedJavaType receiverType) {
        return createReadVirtualMethod(graph, hub, method.vtableEntryOffset(receiverType));
    }

    private static ReadNode createReadVirtualMethod(StructuredGraph graph, ValueNode hub, int vtableEntryOffset) {
        assert vtableEntryOffset > 0;
        // We use LocationNode.ANY_LOCATION for the reads that access the vtable
        // entry as HotSpot does not guarantee that this is a final value.
        Stamp methodStamp = MethodPointerStamp.method();
        ReadNode metaspaceMethod = graph.add(new ReadNode(hub, graph.unique(new ConstantLocationNode(any(), vtableEntryOffset)), methodStamp, BarrierType.NONE));
        return metaspaceMethod;
    }

    @Override
    protected ValueNode createReadHub(StructuredGraph graph, ValueNode object, GuardingNode guard) {
        HotSpotVMConfig config = runtime.getConfig();
        LocationNode location = graph.unique(new ConstantLocationNode(HUB_LOCATION, config.hubOffset));
        assert !object.isConstant() || object.isNullConstant();

        KlassPointerStamp hubStamp = KlassPointerStamp.klassNonNull();
        if (config.useCompressedClassPointers) {
            hubStamp = hubStamp.compressed(config.getKlassEncoding());
        }

        FloatingReadNode memoryRead = graph.unique(new FloatingReadNode(object, location, null, hubStamp, guard, BarrierType.NONE));
        if (config.useCompressedClassPointers) {
            return CompressionNode.uncompress(memoryRead, config.getKlassEncoding());
        } else {
            return memoryRead;
        }
    }

    private WriteNode createWriteHub(StructuredGraph graph, ValueNode object, ValueNode value) {
        HotSpotVMConfig config = runtime.getConfig();
        LocationNode location = graph.unique(new ConstantLocationNode(HUB_WRITE_LOCATION, config.hubOffset));
        assert !object.isConstant() || object.asConstant().isDefaultForKind();

        ValueNode writeValue = value;
        if (config.useCompressedClassPointers) {
            writeValue = CompressionNode.compress(value, config.getKlassEncoding());
        }

        return graph.add(new WriteNode(object, writeValue, location, BarrierType.NONE));
    }

    @Override
    protected BarrierType fieldLoadBarrierType(ResolvedJavaField f) {
        HotSpotResolvedJavaField loadField = (HotSpotResolvedJavaField) f;
        BarrierType barrierType = BarrierType.NONE;
        if (runtime.getConfig().useG1GC && loadField.getKind() == Kind.Object && metaAccess.lookupJavaType(Reference.class).equals(loadField.getDeclaringClass()) &&
                        loadField.getName().equals("referent")) {
            barrierType = BarrierType.PRECISE;
        }
        return barrierType;
    }

    @Override
    protected int fieldOffset(ResolvedJavaField f) {
        HotSpotResolvedJavaField field = (HotSpotResolvedJavaField) f;
        return field.offset();
    }

    @Override
    public int arrayScalingFactor(Kind kind) {
        if (runtime.getConfig().useCompressedOops && kind == Kind.Object) {
            return this.runtime.getTarget().getSizeInBytes(Kind.Int);
        }
        return super.arrayScalingFactor(kind);
    }

    @Override
    protected int arrayBaseOffset(Kind kind) {
        return runtime.getJVMCIRuntime().getArrayBaseOffset(kind);
    }

    @Override
    protected int arrayLengthOffset() {
        return runtime.getConfig().arrayLengthOffset;
    }

    @Override
    protected LocationIdentity initLocationIdentity() {
        return INIT_LOCATION;
    }

    @Override
    public int getSizeInBytes(Stamp stamp) {
        if (stamp instanceof NarrowOopStamp || (stamp instanceof KlassPointerStamp && ((KlassPointerStamp) stamp).isCompressed())) {
            return Kind.Int.getByteCount();
        } else {
            return super.getSizeInBytes(stamp);
        }
    }
}
