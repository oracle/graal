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

import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.hotspot.meta.HotSpotForeignCallsProviderImpl.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.hotspot.replacements.NewObjectSnippets.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
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
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.HeapAccess.BarrierType;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.debug.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.nodes.*;

/**
 * HotSpot implementation of {@link LoweringProvider}.
 */
public class DefaultHotSpotLoweringProvider extends DefaultJavaLoweringProvider implements HotSpotLoweringProvider {

    protected final HotSpotGraalRuntime runtime;
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

    public DefaultHotSpotLoweringProvider(HotSpotGraalRuntime runtime, MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, HotSpotRegistersProvider registers, TargetDescription target) {
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
        providers.getReplacements().registerSnippetTemplateCache(new UnsafeArrayCopySnippets.Templates(providers, target));
    }

    @Override
    public void lower(Node n, LoweringTool tool) {
        StructuredGraph graph = (StructuredGraph) n.graph();

        if (n instanceof Invoke) {
            lowerInvoke((Invoke) n, tool, graph);
        } else if (n instanceof LoadMethodNode) {
            lowerLoadMethodNode((LoadMethodNode) n);
        } else if (n instanceof StoreHubNode) {
            lowerStoreHubNode((StoreHubNode) n, graph);
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
        } else if (n instanceof AssertionNode) {
            assertionSnippets.lower((AssertionNode) n, tool);
        } else if (n instanceof IntegerDivNode || n instanceof IntegerRemNode || n instanceof UnsignedDivNode || n instanceof UnsignedRemNode) {
            // Nothing to do for division nodes. The HotSpot signal handler catches divisions by
            // zero and the MIN_VALUE / -1 cases.
        } else if (n instanceof DeoptimizeNode || n instanceof UnwindNode || n instanceof FloatRemNode) {
            /* No lowering, we generate LIR directly for these nodes. */
        } else {
            super.lower(n, tool);
        }
    }

    private void lowerInvoke(Invoke invoke, LoweringTool tool, StructuredGraph graph) {
        if (invoke.callTarget() instanceof MethodCallTargetNode) {
            MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
            NodeInputList<ValueNode> parameters = callTarget.arguments();
            ValueNode receiver = parameters.size() <= 0 ? null : parameters.get(0);
            GuardingNode receiverNullCheck = null;
            if (!callTarget.isStatic() && receiver.stamp() instanceof ObjectStamp && !StampTool.isObjectNonNull(receiver)) {
                receiverNullCheck = createNullCheck(receiver, invoke.asNode(), tool);
                invoke.setGuard(receiverNullCheck);
            }
            JavaType[] signature = callTarget.targetMethod().getSignature().toParameterTypes(callTarget.isStatic() ? null : callTarget.targetMethod().getDeclaringClass());

            LoweredCallTargetNode loweredCallTarget = null;
            boolean isVirtualOrInterface = callTarget.invokeKind() == InvokeKind.Virtual || callTarget.invokeKind() == InvokeKind.Interface;
            if (InlineVTableStubs.getValue() && isVirtualOrInterface && (AlwaysInlineVTableStubs.getValue() || invoke.isPolymorphic())) {
                HotSpotResolvedJavaMethod hsMethod = (HotSpotResolvedJavaMethod) callTarget.targetMethod();
                ResolvedJavaType receiverType = invoke.getReceiverType();
                if (hsMethod.isInVirtualMethodTable(receiverType)) {
                    Kind wordKind = runtime.getTarget().wordKind;
                    ValueNode hub = createReadHub(graph, receiver, receiverNullCheck);

                    ReadNode metaspaceMethod = createReadVirtualMethod(graph, wordKind, hub, hsMethod, receiverType);
                    // We use LocationNode.ANY_LOCATION for the reads that access the
                    // compiled code entry as HotSpot does not guarantee they are final
                    // values.
                    ReadNode compiledEntry = graph.add(new ReadNode(metaspaceMethod, ConstantLocationNode.create(ANY_LOCATION, wordKind, runtime.getConfig().methodCompiledEntryOffset, graph),
                                    StampFactory.forKind(wordKind), BarrierType.NONE));

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
        return ConstantNode.forConstant(HotSpotObjectConstant.forObject(field.getDeclaringClass().mirror()), metaAccess, graph);
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
        Kind wordKind = runtime.getTarget().wordKind;
        LocationNode location = ConstantLocationNode.create(FINAL_LOCATION, wordKind, runtime.getConfig().arrayClassElementOffset, graph);
        /*
         * Anchor the read of the element klass to the cfg, because it is only valid when arrayClass
         * is an object class, which might not be the case in other parts of the compiled method.
         */
        return graph.unique(new FloatingReadNode(arrayHub, location, null, StampFactory.forKind(wordKind), BeginNode.prevBegin(anchor)));
    }

    @Override
    protected void lowerUnsafeLoadNode(UnsafeLoadNode load, LoweringTool tool) {
        StructuredGraph graph = load.graph();
        if (load.getGuardingCondition() == null && graph.getGuardsStage().ordinal() > StructuredGraph.GuardsStage.FLOATING_GUARDS.ordinal() && addReadBarrier(load)) {
            unsafeLoadSnippets.lower(load, tool);
        } else {
            super.lowerUnsafeLoadNode(load, tool);
        }
    }

    private void lowerLoadMethodNode(LoadMethodNode loadMethodNode) {
        StructuredGraph graph = loadMethodNode.graph();
        HotSpotResolvedJavaMethod method = (HotSpotResolvedJavaMethod) loadMethodNode.getMethod();
        ReadNode metaspaceMethod = createReadVirtualMethod(graph, runtime.getTarget().wordKind, loadMethodNode.getHub(), method, loadMethodNode.getReceiverType());
        graph.replaceFixed(loadMethodNode, metaspaceMethod);
    }

    private void lowerStoreHubNode(StoreHubNode storeHub, StructuredGraph graph) {
        WriteNode hub = createWriteHub(graph, runtime.getTarget().wordKind, storeHub.getObject(), storeHub.getValue());
        graph.replaceFixed(storeHub, hub);
    }

    @Override
    protected BarrierType fieldInitializationBarrier(Kind entryKind) {
        return (entryKind == Kind.Object && !useDeferredInitBarriers()) ? BarrierType.IMPRECISE : BarrierType.NONE;
    }

    @Override
    protected BarrierType arrayInitializationBarrier(Kind entryKind) {
        return (entryKind == Kind.Object && !useDeferredInitBarriers()) ? BarrierType.PRECISE : BarrierType.NONE;
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
                ReadNode load = graph.add(new ReadNode(buffer, location, osrLocal.stamp(), BarrierType.NONE));
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

    private static boolean addReadBarrier(UnsafeLoadNode load) {
        if (useG1GC() && load.graph().getGuardsStage() == StructuredGraph.GuardsStage.FIXED_DEOPTS && load.object().getKind() == Kind.Object && load.accessKind() == Kind.Object &&
                        !StampTool.isObjectAlwaysNull(load.object())) {
            ResolvedJavaType type = StampTool.typeOrNull(load.object());
            if (type != null && !type.isArray()) {
                return true;
            }
        }
        return false;
    }

    private static ReadNode createReadVirtualMethod(StructuredGraph graph, Kind wordKind, ValueNode hub, HotSpotResolvedJavaMethod method, ResolvedJavaType receiverType) {
        return createReadVirtualMethod(graph, wordKind, hub, method.vtableEntryOffset(receiverType));
    }

    private static ReadNode createReadVirtualMethod(StructuredGraph graph, Kind wordKind, ValueNode hub, int vtableEntryOffset) {
        assert vtableEntryOffset > 0;
        // We use LocationNode.ANY_LOCATION for the reads that access the vtable
        // entry as HotSpot does not guarantee that this is a final value.
        ReadNode metaspaceMethod = graph.add(new ReadNode(hub, ConstantLocationNode.create(ANY_LOCATION, wordKind, vtableEntryOffset, graph), StampFactory.forKind(wordKind), BarrierType.NONE));
        return metaspaceMethod;
    }

    @Override
    protected ValueNode createReadHub(StructuredGraph graph, ValueNode object, GuardingNode guard) {
        Kind wordKind = target.wordKind;
        HotSpotVMConfig config = runtime.getConfig();
        LocationNode location = ConstantLocationNode.create(FINAL_LOCATION, wordKind, config.hubOffset, graph);
        assert !object.isConstant() || object.asConstant().isNull();

        Stamp hubStamp;
        if (config.useCompressedClassPointers) {
            hubStamp = StampFactory.forInteger(32);
        } else {
            hubStamp = StampFactory.forKind(wordKind);
        }

        FloatingReadNode memoryRead = graph.unique(new FloatingReadNode(object, location, null, hubStamp, guard, BarrierType.NONE));
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

        return graph.add(new WriteNode(object, writeValue, location, BarrierType.NONE));
    }

    @Override
    protected BarrierType fieldLoadBarrierType(ResolvedJavaField f) {
        HotSpotResolvedJavaField loadField = (HotSpotResolvedJavaField) f;
        BarrierType barrierType = BarrierType.NONE;
        if (config().useG1GC && loadField.getKind() == Kind.Object && loadField.getDeclaringClass().mirror() == java.lang.ref.Reference.class && loadField.getName().equals("referent")) {
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
        if (useCompressedOops() && kind == Kind.Object) {
            return this.runtime.getTarget().getSizeInBytes(Kind.Int);
        }
        return super.arrayScalingFactor(kind);
    }

    @Override
    protected int arrayBaseOffset(Kind kind) {
        return HotSpotGraalRuntime.getArrayBaseOffset(kind);
    }

    @Override
    protected int arrayLengthOffset() {
        return config().arrayLengthOffset;
    }

    @Override
    protected LocationIdentity initLocationIdentity() {
        return INIT_LOCATION;
    }
}
