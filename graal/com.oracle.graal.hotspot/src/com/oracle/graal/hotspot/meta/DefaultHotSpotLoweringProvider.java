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

import static com.oracle.graal.compiler.common.GraalOptions.AlwaysInlineVTableStubs;
import static com.oracle.graal.compiler.common.GraalOptions.InlineVTableStubs;
import static com.oracle.graal.compiler.common.GraalOptions.OmitHotExceptionStacktrace;
import static com.oracle.graal.hotspot.meta.HotSpotForeignCallsProviderImpl.OSR_MIGRATION_END;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.CLASS_KLASS_LOCATION;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.CLASS_MIRROR_LOCATION;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.HUB_LOCATION;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.HUB_WRITE_LOCATION;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.KLASS_LAYOUT_HELPER_LOCATION;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.OBJ_ARRAY_KLASS_ELEMENT_KLASS_LOCATION;
import static com.oracle.graal.hotspot.replacements.NewObjectSnippets.INIT_LOCATION;
import static jdk.internal.jvmci.hotspot.HotSpotJVMCIRuntimeProvider.getArrayBaseOffset;
import static jdk.internal.jvmci.hotspot.HotSpotVMConfig.config;
import static jdk.internal.jvmci.meta.LocationIdentity.any;

import java.lang.ref.Reference;

import jdk.internal.jvmci.code.CallingConvention;
import jdk.internal.jvmci.code.TargetDescription;
import jdk.internal.jvmci.common.JVMCIError;
import jdk.internal.jvmci.hotspot.HotSpotResolvedJavaField;
import jdk.internal.jvmci.hotspot.HotSpotResolvedJavaMethod;
import jdk.internal.jvmci.hotspot.HotSpotVMConfig;
import jdk.internal.jvmci.meta.ConstantReflectionProvider;
import jdk.internal.jvmci.meta.JavaConstant;
import jdk.internal.jvmci.meta.JavaKind;
import jdk.internal.jvmci.meta.JavaType;
import jdk.internal.jvmci.meta.LocationIdentity;
import jdk.internal.jvmci.meta.MetaAccessProvider;
import jdk.internal.jvmci.meta.ResolvedJavaField;
import jdk.internal.jvmci.meta.ResolvedJavaType;

import com.oracle.graal.compiler.common.spi.ForeignCallDescriptor;
import com.oracle.graal.compiler.common.spi.ForeignCallsProvider;
import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeInputList;
import com.oracle.graal.hotspot.HotSpotGraalRuntimeProvider;
import com.oracle.graal.hotspot.nodes.CompressionNode;
import com.oracle.graal.hotspot.nodes.ComputeObjectAddressNode;
import com.oracle.graal.hotspot.nodes.G1ArrayRangePostWriteBarrier;
import com.oracle.graal.hotspot.nodes.G1ArrayRangePreWriteBarrier;
import com.oracle.graal.hotspot.nodes.G1PostWriteBarrier;
import com.oracle.graal.hotspot.nodes.G1PreWriteBarrier;
import com.oracle.graal.hotspot.nodes.G1ReferentFieldReadBarrier;
import com.oracle.graal.hotspot.nodes.GetObjectAddressNode;
import com.oracle.graal.hotspot.nodes.HotSpotDirectCallTargetNode;
import com.oracle.graal.hotspot.nodes.HotSpotIndirectCallTargetNode;
import com.oracle.graal.hotspot.nodes.SerialArrayRangeWriteBarrier;
import com.oracle.graal.hotspot.nodes.SerialWriteBarrier;
import com.oracle.graal.hotspot.nodes.type.KlassPointerStamp;
import com.oracle.graal.hotspot.nodes.type.MethodPointerStamp;
import com.oracle.graal.hotspot.nodes.type.NarrowOopStamp;
import com.oracle.graal.hotspot.replacements.AssertionSnippets;
import com.oracle.graal.hotspot.replacements.CheckCastDynamicSnippets;
import com.oracle.graal.hotspot.replacements.ClassGetHubNode;
import com.oracle.graal.hotspot.replacements.HubGetClassNode;
import com.oracle.graal.hotspot.replacements.InstanceOfSnippets;
import com.oracle.graal.hotspot.replacements.KlassLayoutHelperNode;
import com.oracle.graal.hotspot.replacements.LoadExceptionObjectSnippets;
import com.oracle.graal.hotspot.replacements.MonitorSnippets;
import com.oracle.graal.hotspot.replacements.NewObjectSnippets;
import com.oracle.graal.hotspot.replacements.RuntimeStringSnippets;
import com.oracle.graal.hotspot.replacements.UnsafeLoadSnippets;
import com.oracle.graal.hotspot.replacements.WriteBarrierSnippets;
import com.oracle.graal.hotspot.replacements.arraycopy.ArrayCopyNode;
import com.oracle.graal.hotspot.replacements.arraycopy.ArrayCopySlowPathNode;
import com.oracle.graal.hotspot.replacements.arraycopy.ArrayCopySnippets;
import com.oracle.graal.hotspot.replacements.arraycopy.ArrayCopyUnrollNode;
import com.oracle.graal.hotspot.replacements.arraycopy.UnsafeArrayCopySnippets;
import com.oracle.graal.nodes.AbstractBeginNode;
import com.oracle.graal.nodes.AbstractDeoptimizeNode;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.Invoke;
import com.oracle.graal.nodes.LoweredCallTargetNode;
import com.oracle.graal.nodes.ParameterNode;
import com.oracle.graal.nodes.StartNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.UnwindNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.AddNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.calc.IntegerDivNode;
import com.oracle.graal.nodes.calc.IntegerRemNode;
import com.oracle.graal.nodes.calc.RemNode;
import com.oracle.graal.nodes.calc.UnsignedDivNode;
import com.oracle.graal.nodes.calc.UnsignedRemNode;
import com.oracle.graal.nodes.debug.RuntimeStringNode;
import com.oracle.graal.nodes.debug.VerifyHeapNode;
import com.oracle.graal.nodes.extended.BytecodeExceptionNode;
import com.oracle.graal.nodes.extended.ForeignCallNode;
import com.oracle.graal.nodes.extended.GetClassNode;
import com.oracle.graal.nodes.extended.GuardingNode;
import com.oracle.graal.nodes.extended.LoadHubNode;
import com.oracle.graal.nodes.extended.LoadMethodNode;
import com.oracle.graal.nodes.extended.OSRLocalNode;
import com.oracle.graal.nodes.extended.OSRStartNode;
import com.oracle.graal.nodes.extended.StoreHubNode;
import com.oracle.graal.nodes.extended.UnsafeLoadNode;
import com.oracle.graal.nodes.java.CheckCastDynamicNode;
import com.oracle.graal.nodes.java.ClassIsAssignableFromNode;
import com.oracle.graal.nodes.java.DynamicNewArrayNode;
import com.oracle.graal.nodes.java.DynamicNewInstanceNode;
import com.oracle.graal.nodes.java.InstanceOfDynamicNode;
import com.oracle.graal.nodes.java.InstanceOfNode;
import com.oracle.graal.nodes.java.LoadExceptionObjectNode;
import com.oracle.graal.nodes.java.MethodCallTargetNode;
import com.oracle.graal.nodes.java.MonitorExitNode;
import com.oracle.graal.nodes.java.NewArrayNode;
import com.oracle.graal.nodes.java.NewInstanceNode;
import com.oracle.graal.nodes.java.NewMultiArrayNode;
import com.oracle.graal.nodes.java.RawMonitorEnterNode;
import com.oracle.graal.nodes.memory.FloatingReadNode;
import com.oracle.graal.nodes.memory.HeapAccess.BarrierType;
import com.oracle.graal.nodes.memory.ReadNode;
import com.oracle.graal.nodes.memory.WriteNode;
import com.oracle.graal.nodes.memory.address.AddressNode;
import com.oracle.graal.nodes.spi.LoweringProvider;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.nodes.spi.StampProvider;
import com.oracle.graal.nodes.type.StampTool;
import com.oracle.graal.replacements.DefaultJavaLoweringProvider;
import com.oracle.graal.replacements.nodes.AssertionNode;

/**
 * HotSpot implementation of {@link LoweringProvider}.
 */
public class DefaultHotSpotLoweringProvider extends DefaultJavaLoweringProvider implements HotSpotLoweringProvider {

    protected final HotSpotGraalRuntimeProvider runtime;
    protected final ForeignCallsProvider foreignCalls;
    protected final HotSpotRegistersProvider registers;
    protected final ConstantReflectionProvider constantReflection;

    protected CheckCastDynamicSnippets.Templates checkcastDynamicSnippets;
    protected InstanceOfSnippets.Templates instanceofSnippets;
    protected NewObjectSnippets.Templates newObjectSnippets;
    protected MonitorSnippets.Templates monitorSnippets;
    protected WriteBarrierSnippets.Templates writeBarrierSnippets;
    protected LoadExceptionObjectSnippets.Templates exceptionObjectSnippets;
    protected UnsafeLoadSnippets.Templates unsafeLoadSnippets;
    protected AssertionSnippets.Templates assertionSnippets;
    protected ArrayCopySnippets.Templates arraycopySnippets;
    protected RuntimeStringSnippets.Templates runtimeStringSnippets;

    public DefaultHotSpotLoweringProvider(HotSpotGraalRuntimeProvider runtime, MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, HotSpotRegistersProvider registers,
                    ConstantReflectionProvider constantReflection, TargetDescription target) {
        super(metaAccess, target);
        this.runtime = runtime;
        this.foreignCalls = foreignCalls;
        this.registers = registers;
        this.constantReflection = constantReflection;
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
        runtimeStringSnippets = new RuntimeStringSnippets.Templates(providers, target);
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
                newObjectSnippets.lower((NewArrayNode) n, registers, tool);
            }
        } else if (n instanceof DynamicNewArrayNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                newObjectSnippets.lower((DynamicNewArrayNode) n, registers, tool);
            }
        } else if (n instanceof VerifyHeapNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                newObjectSnippets.lower((VerifyHeapNode) n, registers, tool);
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
            arraycopySnippets.lower((ArrayCopySlowPathNode) n, tool);
        } else if (n instanceof ArrayCopyUnrollNode) {
            arraycopySnippets.lower((ArrayCopyUnrollNode) n, tool);
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
        } else if (n instanceof RuntimeStringNode) {
            runtimeStringSnippets.lower((RuntimeStringNode) n, tool);
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
        } else if (n instanceof ComputeObjectAddressNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                lowerComputeObjectAddressNode((ComputeObjectAddressNode) n);
            }
        } else {
            super.lower(n, tool);
        }
    }

    private static void lowerComputeObjectAddressNode(ComputeObjectAddressNode n) {
        /*
         * Lower the node into a ComputeObjectAddress node and an Add but ensure that it's below any
         * potential safepoints and above it's uses.
         */
        for (Node use : n.usages().snapshot()) {
            if (use instanceof FixedNode) {
                FixedNode fixed = (FixedNode) use;
                StructuredGraph graph = n.graph();
                GetObjectAddressNode address = graph.add(new GetObjectAddressNode(n.getObject()));
                graph.addBeforeFixed(fixed, address);
                AddNode add = graph.addOrUnique(new AddNode(address, n.getOffset()));
                graph.replaceFixedWithFloating(n, add);
            } else {
                throw JVMCIError.shouldNotReachHere("Unexpected floating use of ComputeObjectAddressNode");
            }
        }
    }

    private void lowerKlassLayoutHelperNode(KlassLayoutHelperNode n, LoweringTool tool) {
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
            return;
        }
        StructuredGraph graph = n.graph();
        assert !n.getHub().isConstant();
        AddressNode address = createOffsetAddress(graph, n.getHub(), config().klassLayoutHelperOffset);
        graph.replaceFloating(n, graph.unique(new FloatingReadNode(address, KLASS_LAYOUT_HELPER_LOCATION, null, n.stamp(), n.getGuard(), BarrierType.NONE)));
    }

    private void lowerHubGetClassNode(HubGetClassNode n, LoweringTool tool) {
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
            return;
        }

        StructuredGraph graph = n.graph();
        assert !n.getHub().isConstant();
        AddressNode address = createOffsetAddress(graph, n.getHub(), config().classMirrorOffset);
        FloatingReadNode read = graph.unique(new FloatingReadNode(address, CLASS_MIRROR_LOCATION, null, n.stamp(), n.getGuard(), BarrierType.NONE));
        graph.replaceFloating(n, read);
    }

    private void lowerClassGetHubNode(ClassGetHubNode n, LoweringTool tool) {
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
            return;
        }

        StructuredGraph graph = n.graph();
        assert !n.getValue().isConstant();
        AddressNode address = createOffsetAddress(graph, n.getValue(), config().klassOffset);
        FloatingReadNode read = graph.unique(new FloatingReadNode(address, CLASS_KLASS_LOCATION, null, n.stamp(), n.getGuard(), BarrierType.NONE));
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
                    JavaKind wordKind = runtime.getTarget().wordJavaKind;
                    ValueNode hub = createReadHub(graph, receiver, receiverNullCheck, tool);

                    ReadNode metaspaceMethod = createReadVirtualMethod(graph, hub, hsMethod, receiverType);
                    // We use LocationNode.ANY_LOCATION for the reads that access the
                    // compiled code entry as HotSpot does not guarantee they are final
                    // values.
                    int methodCompiledEntryOffset = config().methodCompiledEntryOffset;
                    AddressNode address = createOffsetAddress(graph, metaspaceMethod, methodCompiledEntryOffset);
                    ReadNode compiledEntry = graph.add(new ReadNode(address, any(), StampFactory.forKind(wordKind), BarrierType.NONE));

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
    protected Stamp loadStamp(Stamp stamp, JavaKind kind, boolean compressible) {
        if (kind == JavaKind.Object && compressible && config().useCompressedOops) {
            return NarrowOopStamp.compressed((ObjectStamp) stamp, config().getOopEncoding());
        }
        return super.loadStamp(stamp, kind, compressible);
    }

    @Override
    protected ValueNode implicitLoadConvert(StructuredGraph graph, JavaKind kind, ValueNode value, boolean compressible) {
        if (kind == JavaKind.Object && compressible && config().useCompressedOops) {
            return CompressionNode.uncompress(value, config().getOopEncoding());
        }
        return super.implicitLoadConvert(graph, kind, value, compressible);
    }

    @Override
    public ValueNode staticFieldBase(StructuredGraph graph, ResolvedJavaField f) {
        HotSpotResolvedJavaField field = (HotSpotResolvedJavaField) f;
        JavaConstant base = field.getDeclaringClass().getJavaClass();
        return ConstantNode.forConstant(base, metaAccess, graph);
    }

    @Override
    protected ValueNode implicitStoreConvert(StructuredGraph graph, JavaKind kind, ValueNode value, boolean compressible) {
        if (kind == JavaKind.Object && compressible && config().useCompressedOops) {
            return CompressionNode.compress(value, config().getOopEncoding());
        }
        return super.implicitStoreConvert(graph, kind, value, compressible);
    }

    @Override
    protected ValueNode createReadArrayComponentHub(StructuredGraph graph, ValueNode arrayHub, FixedNode anchor) {
        /*
         * Anchor the read of the element klass to the cfg, because it is only valid when arrayClass
         * is an object class, which might not be the case in other parts of the compiled method.
         */
        AddressNode address = createOffsetAddress(graph, arrayHub, config().arrayClassElementOffset);
        return graph.unique(new FloatingReadNode(address, OBJ_ARRAY_KLASS_ELEMENT_KLASS_LOCATION, null, KlassPointerStamp.klassNonNull(), AbstractBeginNode.prevBegin(anchor)));
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

    private void lowerLoadMethodNode(LoadMethodNode loadMethodNode) {
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
    public BarrierType fieldInitializationBarrier(JavaKind entryKind) {
        return (entryKind == JavaKind.Object && !config().useDeferredInitBarriers) ? BarrierType.IMPRECISE : BarrierType.NONE;
    }

    @Override
    public BarrierType arrayInitializationBarrier(JavaKind entryKind) {
        return (entryKind == JavaKind.Object && !config().useDeferredInitBarriers) ? BarrierType.PRECISE : BarrierType.NONE;
    }

    private void lowerOSRStartNode(OSRStartNode osrStart) {
        StructuredGraph graph = osrStart.graph();
        if (graph.getGuardsStage() == StructuredGraph.GuardsStage.FIXED_DEOPTS) {
            StartNode newStart = graph.add(new StartNode());
            ParameterNode buffer = graph.unique(new ParameterNode(0, StampFactory.forKind(runtime.getTarget().wordJavaKind)));
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
                int size = osrLocal.getStackKind().getSlotCount();
                int offset = localsOffset - (osrLocal.index() + size - 1) * 8;
                AddressNode address = createOffsetAddress(graph, buffer, offset);
                ReadNode load = graph.add(new ReadNode(address, any(), osrLocal.stamp(), BarrierType.NONE));
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
                FloatingNode exceptionNode = ConstantNode.forConstant(constantReflection.forObject(exception), metaAccess, graph);
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

    private static boolean addReadBarrier(UnsafeLoadNode load) {
        if (config().useG1GC && load.graph().getGuardsStage() == StructuredGraph.GuardsStage.FIXED_DEOPTS && load.object().getStackKind() == JavaKind.Object && load.accessKind() == JavaKind.Object &&
                        !StampTool.isPointerAlwaysNull(load.object())) {
            ResolvedJavaType type = StampTool.typeOrNull(load.object());
            if (type != null && !type.isArray()) {
                return true;
            }
        }
        return false;
    }

    private ReadNode createReadVirtualMethod(StructuredGraph graph, ValueNode hub, HotSpotResolvedJavaMethod method, ResolvedJavaType receiverType) {
        return createReadVirtualMethod(graph, hub, method.vtableEntryOffset(receiverType));
    }

    private ReadNode createReadVirtualMethod(StructuredGraph graph, ValueNode hub, int vtableEntryOffset) {
        assert vtableEntryOffset > 0;
        // We use LocationNode.ANY_LOCATION for the reads that access the vtable
        // entry as HotSpot does not guarantee that this is a final value.
        Stamp methodStamp = MethodPointerStamp.method();
        AddressNode address = createOffsetAddress(graph, hub, vtableEntryOffset);
        ReadNode metaspaceMethod = graph.add(new ReadNode(address, any(), methodStamp, BarrierType.NONE));
        return metaspaceMethod;
    }

    @Override
    protected ValueNode createReadHub(StructuredGraph graph, ValueNode object, GuardingNode guard, LoweringTool tool) {
        if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
            return graph.unique(new LoadHubNode(tool.getStampProvider(), object, guard != null ? guard.asNode() : null));
        }
        assert !object.isConstant() || object.isNullConstant();

        KlassPointerStamp hubStamp = KlassPointerStamp.klassNonNull();
        if (config().useCompressedClassPointers) {
            hubStamp = hubStamp.compressed(config().getKlassEncoding());
        }

        AddressNode address = createOffsetAddress(graph, object, config().hubOffset);
        FloatingReadNode memoryRead = graph.unique(new FloatingReadNode(address, HUB_LOCATION, null, hubStamp, guard, BarrierType.NONE));
        if (config().useCompressedClassPointers) {
            return CompressionNode.uncompress(memoryRead, config().getKlassEncoding());
        } else {
            return memoryRead;
        }
    }

    private WriteNode createWriteHub(StructuredGraph graph, ValueNode object, ValueNode value) {
        assert !object.isConstant() || object.asConstant().isDefaultForKind();

        ValueNode writeValue = value;
        if (config().useCompressedClassPointers) {
            writeValue = CompressionNode.compress(value, config().getKlassEncoding());
        }

        AddressNode address = createOffsetAddress(graph, object, config().hubOffset);
        return graph.add(new WriteNode(address, HUB_WRITE_LOCATION, writeValue, BarrierType.NONE));
    }

    @Override
    protected BarrierType fieldLoadBarrierType(ResolvedJavaField f) {
        HotSpotResolvedJavaField loadField = (HotSpotResolvedJavaField) f;
        BarrierType barrierType = BarrierType.NONE;
        if (config().useG1GC && loadField.getJavaKind() == JavaKind.Object && metaAccess.lookupJavaType(Reference.class).equals(loadField.getDeclaringClass()) &&
                        loadField.getName().equals("referent")) {
            barrierType = BarrierType.PRECISE;
        }
        return barrierType;
    }

    @Override
    public int fieldOffset(ResolvedJavaField f) {
        HotSpotResolvedJavaField field = (HotSpotResolvedJavaField) f;
        return field.offset();
    }

    @Override
    public int arrayScalingFactor(JavaKind kind) {
        if (config().useCompressedOops && kind == JavaKind.Object) {
            return super.arrayScalingFactor(JavaKind.Int);
        } else {
            return super.arrayScalingFactor(kind);
        }
    }

    @Override
    public int arrayBaseOffset(JavaKind kind) {
        return getArrayBaseOffset(kind);
    }

    @Override
    public int arrayLengthOffset() {
        return config().arrayOopDescLengthOffset();
    }

    @Override
    public LocationIdentity initLocationIdentity() {
        return INIT_LOCATION;
    }
}
