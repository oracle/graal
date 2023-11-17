/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.meta;

import static jdk.graal.compiler.core.common.GraalOptions.AlwaysInlineVTableStubs;
import static jdk.graal.compiler.core.common.GraalOptions.InlineVTableStubs;
import static jdk.graal.compiler.core.common.GraalOptions.OmitHotExceptionStacktrace;
import static jdk.graal.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl.OSR_MIGRATION_END;
import static jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.GENERIC_ARRAYCOPY;
import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;
import static org.graalvm.word.LocationIdentity.any;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugHandlersFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntimeProvider;
import jdk.graal.compiler.hotspot.nodes.BeginLockScopeNode;
import jdk.graal.compiler.hotspot.nodes.HotSpotCompressionNode;
import jdk.graal.compiler.hotspot.nodes.HotSpotDirectCallTargetNode;
import jdk.graal.compiler.hotspot.nodes.HotSpotIndirectCallTargetNode;
import jdk.graal.compiler.hotspot.nodes.KlassBeingInitializedCheckNode;
import jdk.graal.compiler.hotspot.nodes.VMErrorNode;
import jdk.graal.compiler.hotspot.nodes.VirtualThreadUpdateJFRNode;
import jdk.graal.compiler.hotspot.nodes.type.HotSpotNarrowOopStamp;
import jdk.graal.compiler.hotspot.nodes.type.KlassPointerStamp;
import jdk.graal.compiler.hotspot.nodes.type.MethodPointerStamp;
import jdk.graal.compiler.hotspot.replacements.AssertionSnippets;
import jdk.graal.compiler.hotspot.replacements.ClassGetHubNode;
import jdk.graal.compiler.hotspot.replacements.DigestBaseSnippets;
import jdk.graal.compiler.hotspot.replacements.FastNotifyNode;
import jdk.graal.compiler.hotspot.replacements.HotSpotAllocationSnippets;
import jdk.graal.compiler.hotspot.replacements.HotSpotG1WriteBarrierSnippets;
import jdk.graal.compiler.hotspot.replacements.HotSpotHashCodeSnippets;
import jdk.graal.compiler.hotspot.replacements.HotSpotIsArraySnippets;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.hotspot.replacements.HotSpotSerialWriteBarrierSnippets;
import jdk.graal.compiler.hotspot.replacements.HubGetClassNode;
import jdk.graal.compiler.hotspot.replacements.InstanceOfSnippets;
import jdk.graal.compiler.hotspot.replacements.KlassLayoutHelperNode;
import jdk.graal.compiler.hotspot.replacements.LoadExceptionObjectSnippets;
import jdk.graal.compiler.hotspot.replacements.LogSnippets;
import jdk.graal.compiler.hotspot.replacements.MonitorSnippets;
import jdk.graal.compiler.hotspot.replacements.ObjectCloneSnippets;
import jdk.graal.compiler.hotspot.replacements.ObjectSnippets;
import jdk.graal.compiler.hotspot.replacements.RegisterFinalizerSnippets;
import jdk.graal.compiler.hotspot.replacements.StringToBytesSnippets;
import jdk.graal.compiler.hotspot.replacements.UnsafeCopyMemoryNode;
import jdk.graal.compiler.hotspot.replacements.UnsafeSnippets;
import jdk.graal.compiler.hotspot.replacements.VirtualThreadUpdateJFRSnippets;
import jdk.graal.compiler.hotspot.replacements.arraycopy.CheckcastArrayCopyCallNode;
import jdk.graal.compiler.hotspot.replacements.arraycopy.GenericArrayCopyCallNode;
import jdk.graal.compiler.hotspot.replacements.arraycopy.HotSpotArraycopySnippets;
import jdk.graal.compiler.hotspot.stubs.ForeignCallSnippets;
import jdk.graal.compiler.hotspot.word.KlassPointer;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractDeoptimizeNode;
import jdk.graal.compiler.nodes.CompressionNode.CompressionOp;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeadEndNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GetObjectAddressNode;
import jdk.graal.compiler.nodes.GraphState.GuardsStage;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoweredCallTargetNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.FloatingIntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.IntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.RemNode;
import jdk.graal.compiler.nodes.calc.SignedDivNode;
import jdk.graal.compiler.nodes.calc.SignedFloatingIntegerDivNode;
import jdk.graal.compiler.nodes.calc.SignedFloatingIntegerRemNode;
import jdk.graal.compiler.nodes.calc.SignedRemNode;
import jdk.graal.compiler.nodes.debug.StringToBytesNode;
import jdk.graal.compiler.nodes.debug.VerifyHeapNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode.BytecodeExceptionKind;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.extended.GetClassNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.LoadHubNode;
import jdk.graal.compiler.nodes.extended.LoadMethodNode;
import jdk.graal.compiler.nodes.extended.OSRLocalNode;
import jdk.graal.compiler.nodes.extended.OSRLockNode;
import jdk.graal.compiler.nodes.extended.OSRMonitorEnterNode;
import jdk.graal.compiler.nodes.extended.OSRStartNode;
import jdk.graal.compiler.nodes.extended.StoreHubNode;
import jdk.graal.compiler.nodes.gc.G1ArrayRangePostWriteBarrier;
import jdk.graal.compiler.nodes.gc.G1ArrayRangePreWriteBarrier;
import jdk.graal.compiler.nodes.gc.G1PostWriteBarrier;
import jdk.graal.compiler.nodes.gc.G1PreWriteBarrier;
import jdk.graal.compiler.nodes.gc.G1ReferentFieldReadBarrier;
import jdk.graal.compiler.nodes.gc.SerialArrayRangeWriteBarrier;
import jdk.graal.compiler.nodes.gc.SerialWriteBarrier;
import jdk.graal.compiler.nodes.java.ClassIsAssignableFromNode;
import jdk.graal.compiler.nodes.java.DynamicNewArrayNode;
import jdk.graal.compiler.nodes.java.DynamicNewInstanceNode;
import jdk.graal.compiler.nodes.java.InstanceOfDynamicNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.LoadExceptionObjectNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.java.MonitorEnterNode;
import jdk.graal.compiler.nodes.java.MonitorExitNode;
import jdk.graal.compiler.nodes.java.MonitorIdNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.java.NewMultiArrayNode;
import jdk.graal.compiler.nodes.java.RegisterFinalizerNode;
import jdk.graal.compiler.nodes.java.ValidateNewInstanceClassNode;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringProvider;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.spi.PlatformConfigurationProvider;
import jdk.graal.compiler.nodes.spi.StampProvider;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.DefaultJavaLoweringProvider;
import jdk.graal.compiler.replacements.IdentityHashCodeSnippets;
import jdk.graal.compiler.replacements.IsArraySnippets;
import jdk.graal.compiler.replacements.arraycopy.ArrayCopyNode;
import jdk.graal.compiler.replacements.arraycopy.ArrayCopySnippets;
import jdk.graal.compiler.replacements.arraycopy.ArrayCopyWithDelayedLoweringNode;
import jdk.graal.compiler.replacements.nodes.AssertionNode;
import jdk.graal.compiler.replacements.nodes.CStringConstant;
import jdk.graal.compiler.replacements.nodes.LogNode;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaField;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * HotSpot implementation of {@link LoweringProvider}.
 */
public abstract class DefaultHotSpotLoweringProvider extends DefaultJavaLoweringProvider implements HotSpotLoweringProvider {

    /**
     * Extension API for lowering a node outside the set of core HotSpot compiler nodes.
     */
    public interface Extension {
        Class<? extends Node> getNodeType();

        /**
         * Lowers {@code n} whose type is guaranteed to be {@link #getNodeType()}.
         */
        void lower(Node n, LoweringTool tool);

        /**
         * Initializes this extension.
         */
        void initialize(HotSpotProviders providers,
                        OptionValues options,
                        GraalHotSpotVMConfig config,
                        HotSpotHostForeignCallsProvider foreignCalls,
                        Iterable<DebugHandlersFactory> factories);
    }

    /**
     * Service provider interface for discovering {@link Extension}s.
     */
    public interface Extensions {
        /**
         * Gets the extensions provided by this object.
         *
         * In the context of service caching done when building a libgraal image, implementations of
         * this method must return a new value each time to avoid sharing extensions between
         * different {@link DefaultHotSpotLoweringProvider}s.
         */
        List<Extension> createExtensions();
    }

    protected final HotSpotGraalRuntimeProvider runtime;
    protected final HotSpotRegistersProvider registers;
    protected final HotSpotConstantReflectionProvider constantReflection;

    protected InstanceOfSnippets.Templates instanceofSnippets;
    protected HotSpotAllocationSnippets.Templates allocationSnippets;
    protected MonitorSnippets.Templates monitorSnippets;
    protected HotSpotSerialWriteBarrierSnippets.Templates serialWriteBarrierSnippets;
    protected HotSpotG1WriteBarrierSnippets.Templates g1WriteBarrierSnippets;
    protected LoadExceptionObjectSnippets.Templates exceptionObjectSnippets;
    protected AssertionSnippets.Templates assertionSnippets;
    protected LogSnippets.Templates logSnippets;
    protected ArrayCopySnippets.Templates arraycopySnippets;
    protected StringToBytesSnippets.Templates stringToBytesSnippets;
    protected ObjectSnippets.Templates objectSnippets;
    protected UnsafeSnippets.Templates unsafeSnippets;
    protected ObjectCloneSnippets.Templates objectCloneSnippets;
    protected ForeignCallSnippets.Templates foreignCallSnippets;
    protected RegisterFinalizerSnippets.Templates registerFinalizerSnippets;
    protected VirtualThreadUpdateJFRSnippets.Templates virtualThreadUpdateJFRSnippets;

    protected final Map<Class<? extends Node>, Extension> extensions = new HashMap<>();

    public DefaultHotSpotLoweringProvider(HotSpotGraalRuntimeProvider runtime, MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, HotSpotRegistersProvider registers,
                    HotSpotConstantReflectionProvider constantReflection, PlatformConfigurationProvider platformConfig, MetaAccessExtensionProvider metaAccessExtensionProvider,
                    TargetDescription target) {
        super(metaAccess, foreignCalls, platformConfig, metaAccessExtensionProvider, target, runtime.getVMConfig().useCompressedOops);
        this.runtime = runtime;
        this.registers = registers;
        this.constantReflection = constantReflection;
    }

    public HotSpotGraalRuntimeProvider getRuntime() {
        return runtime;
    }

    public HotSpotRegistersProvider getRegisters() {
        return registers;
    }

    public HotSpotConstantReflectionProvider getConstantReflection() {
        return constantReflection;
    }

    @Override
    public void initialize(OptionValues options, Iterable<DebugHandlersFactory> factories, HotSpotProviders providers, GraalHotSpotVMConfig config) {
        initialize(options, factories, providers, config,
                        new HotSpotArraycopySnippets.Templates(new HotSpotArraycopySnippets(), runtime, options, providers),
                        new HotSpotAllocationSnippets.Templates(new HotSpotAllocationSnippets(config, providers.getRegisters()), options, runtime, providers, config));
    }

    public void initialize(OptionValues options, Iterable<DebugHandlersFactory> factories, HotSpotProviders providers, GraalHotSpotVMConfig config,
                    HotSpotArraycopySnippets.Templates arraycopySnippetTemplates,
                    HotSpotAllocationSnippets.Templates allocationSnippetTemplates) {
        super.initialize(options, runtime, providers);

        assert target == providers.getCodeCache().getTarget() : Assertions.errorMessage(target, providers.getCodeCache().getTarget());
        instanceofSnippets = new InstanceOfSnippets.Templates(options, runtime, providers);
        allocationSnippets = allocationSnippetTemplates;
        monitorSnippets = new MonitorSnippets.Templates(options, runtime, providers, config);
        g1WriteBarrierSnippets = new HotSpotG1WriteBarrierSnippets.Templates(options, runtime, providers, config);
        serialWriteBarrierSnippets = new HotSpotSerialWriteBarrierSnippets.Templates(options, runtime, providers);
        exceptionObjectSnippets = new LoadExceptionObjectSnippets.Templates(options, providers);
        assertionSnippets = new AssertionSnippets.Templates(options, providers);
        logSnippets = new LogSnippets.Templates(options, providers);
        arraycopySnippets = arraycopySnippetTemplates;
        stringToBytesSnippets = new StringToBytesSnippets.Templates(options, providers);
        identityHashCodeSnippets = new IdentityHashCodeSnippets.Templates(new HotSpotHashCodeSnippets(), options, providers, HotSpotReplacementsUtil.MARK_WORD_LOCATION);
        isArraySnippets = new IsArraySnippets.Templates(new HotSpotIsArraySnippets(), options, providers);
        objectCloneSnippets = new ObjectCloneSnippets.Templates(options, providers);
        foreignCallSnippets = new ForeignCallSnippets.Templates(options, providers);
        registerFinalizerSnippets = new RegisterFinalizerSnippets.Templates(options, providers);
        objectSnippets = new ObjectSnippets.Templates(options, providers);
        unsafeSnippets = new UnsafeSnippets.Templates(options, providers);
        virtualThreadUpdateJFRSnippets = new VirtualThreadUpdateJFRSnippets.Templates(options, providers);

        replacements.registerSnippetTemplateCache(new DigestBaseSnippets.Templates(options, providers));

        initializeExtensions(options, factories, providers, config, GraalServices.load(Extensions.class));
    }

    @Override
    public final void initializeExtensions(OptionValues options, Iterable<DebugHandlersFactory> factories, HotSpotProviders providers, GraalHotSpotVMConfig config,
                    Iterable<Extensions> iterableExtensions) throws GraalError {
        for (Extensions ep : iterableExtensions) {
            for (Extension ext : ep.createExtensions()) {
                Class<? extends Node> nodeType = ext.getNodeType();
                Extension old = extensions.put(nodeType, ext);
                if (old != null) {
                    throw new GraalError("Two lowering extensions conflict on the handling of %s: %s and %s", nodeType.getName(), old, ext);
                }
                ext.initialize(providers, options, config, (HotSpotHostForeignCallsProvider) foreignCalls, factories);
            }
        }
    }

    public HotSpotAllocationSnippets.Templates getAllocationSnippets() {
        return allocationSnippets;
    }

    public ArrayCopySnippets.Templates getArraycopySnippets() {
        return arraycopySnippets;
    }

    public MonitorSnippets.Templates getMonitorSnippets() {
        return monitorSnippets;
    }

    /**
     * Handles the lowering of {@code n} without delegating to plugins or super.
     *
     * @return {@code true} if this method handles lowering of {@code n}
     */
    private boolean lowerWithoutDelegation(Node n, LoweringTool tool) {
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
            if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.MID_TIER) {
                lowerBytecodeExceptionNode((BytecodeExceptionNode) n);
            }
        } else if (n instanceof InstanceOfNode) {
            InstanceOfNode instanceOfNode = (InstanceOfNode) n;
            if (graph.getGuardsStage().areDeoptsFixed()) {
                instanceofSnippets.lower(instanceOfNode, tool);
            } else {
                if (instanceOfNode.allowsNull()) {
                    ValueNode object = instanceOfNode.getValue();
                    LogicNode newTypeCheck = graph.addOrUniqueWithInputs(InstanceOfNode.create(instanceOfNode.type(), object, instanceOfNode.profile(), instanceOfNode.getAnchor()));
                    LogicNode newNode = LogicNode.or(graph.unique(IsNullNode.create(object)), newTypeCheck, BranchProbabilityNode.NOT_LIKELY_PROFILE);
                    instanceOfNode.replaceAndDelete(newNode);
                }
            }
        } else if (n instanceof InstanceOfDynamicNode) {
            InstanceOfDynamicNode instanceOfDynamicNode = (InstanceOfDynamicNode) n;
            if (graph.getGuardsStage().areDeoptsFixed()) {
                instanceofSnippets.lower(instanceOfDynamicNode, tool);
            } else {
                ValueNode mirror = instanceOfDynamicNode.getMirrorOrHub();
                if (mirror.stamp(NodeView.DEFAULT).getStackKind() == JavaKind.Object) {
                    ClassGetHubNode classGetHub = graph.unique(new ClassGetHubNode(mirror));
                    instanceOfDynamicNode.setMirror(classGetHub);
                }

                if (instanceOfDynamicNode.allowsNull()) {
                    ValueNode object = instanceOfDynamicNode.getObject();
                    LogicNode newTypeCheck = graph.addOrUniqueWithInputs(
                                    InstanceOfDynamicNode.create(graph.getAssumptions(), tool.getConstantReflection(), instanceOfDynamicNode.getMirrorOrHub(), object,
                                                    false/* null checked below */, instanceOfDynamicNode.isExact()));
                    LogicNode newNode = LogicNode.or(graph.unique(IsNullNode.create(object)), newTypeCheck, BranchProbabilityNode.NOT_LIKELY_PROFILE);
                    instanceOfDynamicNode.replaceAndDelete(newNode);
                }
            }
        } else if (n instanceof ClassIsAssignableFromNode) {
            if (graph.getGuardsStage().areDeoptsFixed()) {
                instanceofSnippets.lower((ClassIsAssignableFromNode) n, tool);
            }
        } else if (n instanceof NewInstanceNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                getAllocationSnippets().lower((NewInstanceNode) n, tool);
            }
        } else if (n instanceof DynamicNewInstanceNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                getAllocationSnippets().lower((DynamicNewInstanceNode) n, tool);
            }
        } else if (n instanceof ValidateNewInstanceClassNode) {
            ValidateNewInstanceClassNode validateNewInstance = (ValidateNewInstanceClassNode) n;
            if (validateNewInstance.getClassClass() == null) {
                JavaConstant classClassMirror = constantReflection.asJavaClass(metaAccess.lookupJavaType(Class.class));
                ConstantNode classClass = ConstantNode.forConstant(classClassMirror, tool.getMetaAccess(), graph);
                validateNewInstance.setClassClass(classClass);
            }
            getAllocationSnippets().lower(validateNewInstance, tool);
        } else if (n instanceof NewArrayNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                getAllocationSnippets().lower((NewArrayNode) n, tool);
            }
        } else if (n instanceof DynamicNewArrayNode) {
            DynamicNewArrayNode dynamicNewArrayNode = (DynamicNewArrayNode) n;
            if (dynamicNewArrayNode.getVoidClass() == null) {
                JavaConstant voidClassMirror = constantReflection.asJavaClass(metaAccess.lookupJavaType(void.class));
                ConstantNode voidClass = ConstantNode.forConstant(voidClassMirror, tool.getMetaAccess(), graph);
                dynamicNewArrayNode.setVoidClass(voidClass);
            }
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                getAllocationSnippets().lower(dynamicNewArrayNode, tool);
            }
        } else if (n instanceof VerifyHeapNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                getAllocationSnippets().lower((VerifyHeapNode) n, tool);
            }
        } else if (n instanceof MonitorEnterNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                monitorSnippets.lower((MonitorEnterNode) n, registers, tool);
            } else {
                loadHubForMonitorEnterNode((MonitorEnterNode) n, tool, graph);
            }
        } else if (n instanceof MonitorExitNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                monitorSnippets.lower((MonitorExitNode) n, registers, tool);
            }
        } else if (n instanceof ArrayCopyNode) {
            arraycopySnippets.lower((ArrayCopyNode) n, tool);
        } else if (n instanceof GenericArrayCopyCallNode arraycopy) {
            lowerGenericArrayCopyCallNode(arraycopy);
        } else if (n instanceof CheckcastArrayCopyCallNode) {
            lowerCheckcastArrayCopyCallNode((CheckcastArrayCopyCallNode) n, tool);
        } else if (n instanceof ArrayCopyWithDelayedLoweringNode) {
            arraycopySnippets.lower((ArrayCopyWithDelayedLoweringNode) n, tool);
        } else if (n instanceof G1PreWriteBarrier) {
            g1WriteBarrierSnippets.lower((G1PreWriteBarrier) n, tool);
        } else if (n instanceof G1PostWriteBarrier) {
            g1WriteBarrierSnippets.lower((G1PostWriteBarrier) n, tool);
        } else if (n instanceof G1ReferentFieldReadBarrier) {
            g1WriteBarrierSnippets.lower((G1ReferentFieldReadBarrier) n, tool);
        } else if (n instanceof SerialWriteBarrier) {
            serialWriteBarrierSnippets.lower((SerialWriteBarrier) n, tool);
        } else if (n instanceof SerialArrayRangeWriteBarrier) {
            serialWriteBarrierSnippets.lower((SerialArrayRangeWriteBarrier) n, tool);
        } else if (n instanceof G1ArrayRangePreWriteBarrier) {
            g1WriteBarrierSnippets.lower((G1ArrayRangePreWriteBarrier) n, tool);
        } else if (n instanceof G1ArrayRangePostWriteBarrier) {
            g1WriteBarrierSnippets.lower((G1ArrayRangePostWriteBarrier) n, tool);
        } else if (n instanceof NewMultiArrayNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                getAllocationSnippets().lower((NewMultiArrayNode) n, tool);
            }
        } else if (n instanceof LoadExceptionObjectNode) {
            exceptionObjectSnippets.lower((LoadExceptionObjectNode) n, registers, tool);
        } else if (n instanceof AssertionNode) {
            assertionSnippets.lower((AssertionNode) n, tool);
        } else if (n instanceof LogNode) {
            logSnippets.lower((LogNode) n, tool);
        } else if (n instanceof StringToBytesNode) {
            if (graph.getGuardsStage().areDeoptsFixed()) {
                stringToBytesSnippets.lower((StringToBytesNode) n, tool);
            }
        } else if (n instanceof AbstractDeoptimizeNode || n instanceof UnwindNode || n instanceof RemNode || n instanceof SafepointNode) {
            /* No lowering, we generate LIR directly for these nodes. */
        } else if (n instanceof ClassGetHubNode) {
            lowerClassGetHubNode((ClassGetHubNode) n, tool);
        } else if (n instanceof HubGetClassNode) {
            lowerHubGetClassNode((HubGetClassNode) n, tool);
        } else if (n instanceof KlassLayoutHelperNode) {
            if (graph.getGuardsStage() == GuardsStage.AFTER_FSA) {
                lowerKlassLayoutHelperNode((KlassLayoutHelperNode) n, tool);
            }
        } else if (n instanceof KlassBeingInitializedCheckNode) {
            if (graph.getGuardsStage() == GuardsStage.AFTER_FSA) {
                getAllocationSnippets().lower((KlassBeingInitializedCheckNode) n, tool);
            }
        } else if (n instanceof FastNotifyNode) {
            if (graph.getGuardsStage() == GuardsStage.AFTER_FSA) {
                objectSnippets.lower(n, tool);
            }
        } else if (n instanceof UnsafeCopyMemoryNode) {
            if (graph.getGuardsStage() == GuardsStage.AFTER_FSA) {
                unsafeSnippets.lower((UnsafeCopyMemoryNode) n, tool);
            }
        } else if (n instanceof RegisterFinalizerNode) {
            lowerRegisterFinalizer((RegisterFinalizerNode) n, tool);
        } else if (n instanceof DeadEndNode) {
            lowerDeadEnd((DeadEndNode) n);
        } else if (n instanceof IntegerDivRemNode) {
            if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
                lowerIntegerDivRem((IntegerDivRemNode) n, tool);
            }
        } else if (n instanceof VirtualThreadUpdateJFRNode) {
            if (graph.getGuardsStage() == GuardsStage.AFTER_FSA) {
                virtualThreadUpdateJFRSnippets.lower((VirtualThreadUpdateJFRNode) n, registers, tool);
            }
        } else {
            return false;
        }
        return true;
    }

    private void lowerGenericArrayCopyCallNode(GenericArrayCopyCallNode arraycopy) {
        StructuredGraph graph = arraycopy.graph();
        if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
            GetObjectAddressNode srcAddr = graph.add(new GetObjectAddressNode(arraycopy.getSource()));
            graph.addBeforeFixed(arraycopy, srcAddr);
            GetObjectAddressNode destAddr = graph.add(new GetObjectAddressNode(arraycopy.getDestination()));
            graph.addBeforeFixed(arraycopy, destAddr);
            ForeignCallNode call = graph.add(new ForeignCallNode(foreignCalls, GENERIC_ARRAYCOPY, srcAddr, arraycopy.getSrcPos(), destAddr, arraycopy.getDestPos(), arraycopy.getLength()));
            call.setStateAfter(arraycopy.stateAfter());
            graph.replaceFixedWithFixed(arraycopy, call);
        }
    }

    private ValueNode computeBase(LoweringTool tool, CheckcastArrayCopyCallNode n, ValueNode base, ValueNode pos) {
        FixedWithNextNode basePtr = base.graph().add(new GetObjectAddressNode(base));
        base.graph().addBeforeFixed(n, basePtr);

        int shift = CodeUtil.log2(tool.getMetaAccess().getArrayIndexScale(JavaKind.Object));
        ValueNode extendedPos = IntegerConvertNode.convert(pos, StampFactory.forKind(target.wordJavaKind), base.graph(), NodeView.DEFAULT);
        ValueNode scaledIndex = base.graph().unique(new LeftShiftNode(extendedPos, ConstantNode.forInt(shift, base.graph())));
        ValueNode offset = base.graph().unique(
                        new AddNode(scaledIndex,
                                        ConstantNode.forIntegerBits(PrimitiveStamp.getBits(scaledIndex.stamp(NodeView.DEFAULT)), tool.getMetaAccess().getArrayBaseOffset(JavaKind.Object),
                                                        base.graph())));
        return base.graph().unique(new OffsetAddressNode(basePtr, offset));
    }

    protected void lowerCheckcastArrayCopyCallNode(CheckcastArrayCopyCallNode n, LoweringTool tool) {
        StructuredGraph graph = n.graph();
        if (n.graph().getGuardsStage().areFrameStatesAtDeopts()) {
            ForeignCallDescriptor desc = ((HotSpotHostForeignCallsProvider) foreignCalls).lookupCheckcastArraycopyDescriptor(n.isUninit());
            ValueNode srcAddr = computeBase(tool, n, n.getSource(), n.getSourcePosition());
            ValueNode destAddr = computeBase(tool, n, n.getDestination(), n.getDestinationPosition());
            ValueNode len = n.getLength();
            if (len.stamp(NodeView.DEFAULT).getStackKind() != target.wordJavaKind) {
                len = IntegerConvertNode.convert(len, StampFactory.forKind(target.wordJavaKind), graph, NodeView.DEFAULT);
            }
            ForeignCallNode call = graph.add(new ForeignCallNode(desc, srcAddr, destAddr, len, n.getSuperCheckOffset(), n.getDestElemKlass()));
            call.setStateAfter(n.stateAfter());
            graph.replaceFixedWithFixed(n, call);
        }
    }

    /**
     * Lower ({@link FixedNode}) {@link IntegerDivRemNode} nodes to a {@link GuardingNode}
     * (potentially 2 guards if an overflow is possible) and a floating division
     * {@link FloatingIntegerDivRemNode}. This enabled global value numbering for non-constant
     * division operations. Later on in the backend we can combine certain divs again with their
     * checks to avoid explicit 0 and overflow checks.
     */
    protected void lowerIntegerDivRem(IntegerDivRemNode n, LoweringTool tool) {
        if (!n.canFloat()) {
            return;
        }
        ValueNode dividend = n.getX();
        ValueNode divisor = n.getY();
        final IntegerStamp dividendStamp = (IntegerStamp) dividend.stamp(NodeView.DEFAULT);
        final IntegerStamp divisorStamp = (IntegerStamp) divisor.stamp(NodeView.DEFAULT);
        final StructuredGraph graph = n.graph();
        if (!(n instanceof SignedDivNode || n instanceof SignedRemNode)) {
            // Floating integer division is only supported for signed division at the moment
            return;
        }
        if (n.getY().isConstant() && n.getY().asJavaConstant().asLong() == 0) {
            // always div by zero
            return;
        }
        if (!GraalOptions.FloatingDivNodes.getValue(n.getOptions())) {
            return;
        }

        boolean divisionOverflowIsJVMSCompliant = tool.getLowerer().divisionOverflowIsJVMSCompliant();
        if (!divisionOverflowIsJVMSCompliant) {
            long minValue = NumUtil.minValue(dividendStamp.getBits());
            if (dividendStamp.contains(minValue)) {
                /*
                 * The dividend may contain NumUtil.minValue(dividendStamp.getBits()) which can lead
                 * to an overflow of the division. Thus, also check if the divisor contains -1, in
                 * such case we can only start to float if we actually create 2 guards (one min
                 * check for the dividend and the 0 check for the divisor), this is only beneficial
                 * if we actually have at least 2 equivalent divisions. Thus, we do not perform this
                 * optimization at the moment, this may change in the future.
                 */
                if (divisorStamp.contains(-1)) {
                    return;

                }
            }
        }

        GuardingNode guard = null;
        if (n.getZeroGuard() == null) {
            LogicNode conditionDivisor = graph.addOrUniqueWithInputs(
                            CompareNode.createAnyCompareNode(Condition.EQ, n.getY(), ConstantNode.forIntegerBits(divisorStamp.getBits(), 0), tool.getConstantReflection()));
            if (conditionDivisor instanceof LogicConstantNode) {
                boolean val = ((LogicConstantNode) conditionDivisor).getValue();
                if (val) {
                    // stamp always is zero
                    return;
                } else {
                    // stamp never is zero, let canon later handle it
                }
            }
            guard = tool.createGuard(n, conditionDivisor, DeoptimizationReason.ArithmeticException, DeoptimizationAction.InvalidateReprofile, SpeculationLog.NO_SPECULATION, true, null);
            IntegerStamp stampWithout0 = IntegerStamp.create(divisorStamp.getBits(), divisorStamp.lowerBound(), divisorStamp.upperBound(), divisorStamp.mustBeSet(), divisorStamp.mayBeSet(), false);
            divisor = graph.addOrUnique(PiNode.create(n.getY(), stampWithout0, guard.asNode()));
        } else {
            guard = n.getZeroGuard();
            /*
             * We have a zero guard but its possible we are still missing a proper pi node for the
             * guard (which ensures the div never floats to far also based on the value input,
             * construct one if necessary.
             */
            if (divisorStamp.contains(0)) {
                IntegerStamp stampWithout0 = IntegerStamp.create(divisorStamp.getBits(), divisorStamp.lowerBound(), divisorStamp.upperBound(), divisorStamp.mustBeSet(), divisorStamp.mayBeSet(),
                                false);
                divisor = graph.addOrUnique(PiNode.create(n.getY(), stampWithout0, guard.asNode()));
            }
        }
        GraalError.guarantee(SignedDivNode.divisionIsJVMSCompliant(dividend, divisor, divisionOverflowIsJVMSCompliant), "Division must be allowed to float at this point. Dividend %s divisor %s",
                        dividend, divisor);
        ValueNode divRem = null;
        if (n instanceof SignedDivNode) {
            divRem = graph.addOrUnique(SignedFloatingIntegerDivNode.create(dividend, divisor, NodeView.DEFAULT, guard, divisionOverflowIsJVMSCompliant));

        } else if (n instanceof SignedRemNode) {
            divRem = graph.addOrUnique(SignedFloatingIntegerRemNode.create(dividend, divisor, NodeView.DEFAULT, guard, divisionOverflowIsJVMSCompliant));
        } else {
            throw GraalError.shouldNotReachHere("Unkown division node " + n); // ExcludeFromJacocoGeneratedReport
        }
        n.replaceAtUsages(divRem);
        graph.replaceFixedWithFloating(n, divRem);
    }

    @Override
    @SuppressWarnings("try")
    public void lower(Node n, LoweringTool tool) {
        try (DebugCloseable context = n.withNodeSourcePosition()) {
            Class<? extends Node> nodeType = n.getClass();
            if (!lowerWithoutDelegation(n, tool)) {
                Extension ext = extensions.get(nodeType);
                if (ext != null) {
                    ext.lower(ext.getNodeType().cast(n), tool);
                } else {
                    super.lower(n, tool);
                }
            } else {
                Extension ext = extensions.get(nodeType);
                if (ext != null) {
                    // This prevents an extension silently being ignored
                    throw new GraalError("Extension %s is redundant - %s directly handles lowering of %s nodes", ext, getClass().getName(), nodeType.getName());
                }
            }
        }
    }

    protected void loadHubForMonitorEnterNode(MonitorEnterNode monitor, LoweringTool tool, StructuredGraph graph) {
        if (monitor.getObjectData() == null) {
            ValueNode objectNonNull = createNullCheckedValue(monitor.object(), monitor, tool);
            monitor.setObject(objectNonNull);
            monitor.setObjectData(graph.addOrUnique(LoadHubNode.create(objectNonNull, tool.getStampProvider(), tool.getMetaAccess(), tool.getConstantReflection())));
        }
    }

    private void lowerKlassLayoutHelperNode(KlassLayoutHelperNode n, LoweringTool tool) {
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
            return;
        }
        StructuredGraph graph = n.graph();
        assert !n.getHub().isConstant();
        AddressNode address = createOffsetAddress(graph, n.getHub(), runtime.getVMConfig().klassLayoutHelperOffset);
        n.replaceAtUsagesAndDelete(graph.unique(new FloatingReadNode(address, HotSpotReplacementsUtil.KLASS_LAYOUT_HELPER_LOCATION, null, n.stamp(NodeView.DEFAULT), null, BarrierType.NONE)));
    }

    private void lowerHubGetClassNode(HubGetClassNode n, LoweringTool tool) {
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
            return;
        }

        ValueNode hub = n.getHub();
        GraalHotSpotVMConfig vmConfig = runtime.getVMConfig();
        StructuredGraph graph = n.graph();
        assert !hub.isConstant();
        AddressNode mirrorAddress = createOffsetAddress(graph, hub, vmConfig.classMirrorOffset);
        Stamp loadStamp = n.stamp(NodeView.DEFAULT);
        FloatingReadNode read = graph.unique(
                        new FloatingReadNode(mirrorAddress, HotSpotReplacementsUtil.CLASS_MIRROR_LOCATION, null, StampFactory.forKind(target.wordJavaKind),
                                        null, BarrierType.NONE));
        // Read the Object from the OopHandle
        AddressNode address = createOffsetAddress(graph, read, 0);
        read = graph.unique(new FloatingReadNode(address, HotSpotReplacementsUtil.HOTSPOT_OOP_HANDLE_LOCATION, null, loadStamp, null,
                        barrierSet.readBarrierType(HotSpotReplacementsUtil.HOTSPOT_OOP_HANDLE_LOCATION, address, loadStamp)));
        n.replaceAtUsagesAndDelete(read);
    }

    private void lowerClassGetHubNode(ClassGetHubNode n, LoweringTool tool) {
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
            return;
        }

        StructuredGraph graph = n.graph();
        assert !n.getValue().isConstant();
        AddressNode address = createOffsetAddress(graph, n.getValue(), runtime.getVMConfig().klassOffset);
        FloatingReadNode read = graph.unique(new FloatingReadNode(address, HotSpotReplacementsUtil.CLASS_KLASS_LOCATION, null, n.stamp(NodeView.DEFAULT), null, BarrierType.NONE));
        n.replaceAtUsagesAndDelete(read);
    }

    private void lowerInvoke(Invoke invoke, LoweringTool tool, StructuredGraph graph) {
        if (invoke.callTarget() instanceof Lowerable) {
            ((Lowerable) invoke.callTarget()).lower(tool);
        }
        if (invoke.callTarget() instanceof MethodCallTargetNode) {
            MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
            assert callTarget.getClass() == MethodCallTargetNode.class : "unexpected subclass of MethodCallTargetNode";
            NodeInputList<ValueNode> parameters = callTarget.arguments();
            ValueNode receiver = parameters.isEmpty() ? null : parameters.get(0);

            if (!callTarget.isStatic()) {
                assert receiver != null : "non-static call must have a receiver";
                if (receiver.stamp(NodeView.DEFAULT) instanceof ObjectStamp && !StampTool.isPointerNonNull(receiver)) {
                    ValueNode nonNullReceiver = createNullCheckedValue(receiver, invoke.asFixedNode(), tool);
                    parameters.set(0, nonNullReceiver);
                    receiver = nonNullReceiver;
                }
            }
            JavaType[] signature = callTarget.targetMethod().getSignature().toParameterTypes(callTarget.isStatic() ? null : callTarget.targetMethod().getDeclaringClass());

            LoweredCallTargetNode loweredCallTarget = null;
            OptionValues options = graph.getOptions();
            if (InlineVTableStubs.getValue(options) && callTarget.invokeKind().isIndirect() && (AlwaysInlineVTableStubs.getValue(options) || invoke.isPolymorphic())) {
                HotSpotResolvedJavaMethod hsMethod = (HotSpotResolvedJavaMethod) callTarget.targetMethod();
                ResolvedJavaType receiverType = invoke.getReceiverType();
                if (hsMethod.isInVirtualMethodTable(receiverType)) {
                    JavaKind wordKind = runtime.getTarget().wordJavaKind;
                    ValueNode hub = createReadHub(graph, receiver, tool);

                    ReadNode metaspaceMethod = createReadVirtualMethod(graph, hub, hsMethod, receiverType);
                    // We use LocationNode.ANY_LOCATION for the reads that access the
                    // compiled code entry as HotSpot does not guarantee they are final
                    // values.
                    int methodCompiledEntryOffset = runtime.getVMConfig().methodCompiledEntryOffset;
                    AddressNode address = createOffsetAddress(graph, metaspaceMethod, methodCompiledEntryOffset);
                    ReadNode compiledEntry = graph.add(new ReadNode(address, any(), StampFactory.forKind(wordKind), BarrierType.NONE, MemoryOrderMode.PLAIN));

                    loweredCallTarget = graph.add(new HotSpotIndirectCallTargetNode(metaspaceMethod, compiledEntry, parameters.toArray(new ValueNode[parameters.size()]), callTarget.returnStamp(),
                                    signature, callTarget.targetMethod(),
                                    HotSpotCallingConventionType.JavaCall, callTarget.invokeKind()));

                    graph.addBeforeFixed(invoke.asFixedNode(), metaspaceMethod);
                    graph.addAfterFixed(metaspaceMethod, compiledEntry);
                }
            }

            if (loweredCallTarget == null) {
                loweredCallTarget = graph.add(new HotSpotDirectCallTargetNode(parameters.toArray(new ValueNode[parameters.size()]), callTarget.returnStamp(),
                                signature, callTarget.targetMethod(),
                                HotSpotCallingConventionType.JavaCall,
                                callTarget.invokeKind()));
            }
            callTarget.replaceAndDelete(loweredCallTarget);
        }
    }

    private CompressEncoding getOopEncoding() {
        return runtime.getVMConfig().getOopEncoding();
    }

    @Override
    protected Stamp loadCompressedStamp(ObjectStamp stamp) {
        return HotSpotNarrowOopStamp.compressed(stamp, getOopEncoding());
    }

    @Override
    protected ValueNode newCompressionNode(CompressionOp op, ValueNode value) {
        return new HotSpotCompressionNode(op, value, getOopEncoding());
    }

    @Override
    public ValueNode staticFieldBase(StructuredGraph graph, ResolvedJavaField f) {
        HotSpotResolvedJavaField field = (HotSpotResolvedJavaField) f;
        JavaConstant base = constantReflection.asJavaClass(field.getDeclaringClass());
        return ConstantNode.forConstant(base, metaAccess, graph);
    }

    @Override
    protected ValueNode createReadArrayComponentHub(StructuredGraph graph, ValueNode arrayHub, boolean isKnownObjectArray, FixedNode anchor) {
        GuardingNode guard = null;
        if (!isKnownObjectArray) {
            /*
             * Anchor the read of the element klass to the cfg, because it is only valid when
             * arrayClass is an object class, which might not be the case in other parts of the
             * compiled method.
             */
            guard = AbstractBeginNode.prevBegin(anchor);
        }
        AddressNode address = createOffsetAddress(graph, arrayHub, runtime.getVMConfig().arrayClassElementOffset);
        return graph.unique(new FloatingReadNode(address, HotSpotReplacementsUtil.OBJ_ARRAY_KLASS_ELEMENT_KLASS_LOCATION, null, KlassPointerStamp.klassNonNull(), guard));
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
        getClass.replaceAtUsagesAndDelete(hubGetClass);
        hub.lower(tool);
        hubGetClass.lower(tool);
    }

    private void lowerStoreHubNode(StoreHubNode storeHub, StructuredGraph graph) {
        WriteNode hub = createWriteHub(graph, storeHub.getObject(), storeHub.getValue());
        hub.setStateAfter(storeHub.stateAfter());
        graph.replaceFixed(storeHub, hub);
    }

    private void lowerOSRStartNode(OSRStartNode osrStart) {
        StructuredGraph graph = osrStart.graph();
        if (graph.getGuardsStage() == GuardsStage.FIXED_DEOPTS) {
            StartNode newStart = graph.add(new StartNode());
            ParameterNode buffer = graph.addWithoutUnique(new ParameterNode(0, StampPair.createSingle(StampFactory.forKind(runtime.getTarget().wordJavaKind))));
            ForeignCallNode migrationEnd = graph.add(new ForeignCallNode(OSR_MIGRATION_END, buffer));
            migrationEnd.setStateAfter(osrStart.stateAfter());
            newStart.setNext(migrationEnd);
            FixedNode next = osrStart.next();
            osrStart.setNext(null);
            migrationEnd.setNext(next);
            graph.setStart(newStart);

            final int wordSize = target.wordSize;

            // @formatter:off
            // taken from c2 locals_addr = osr_buf + (max_locals-1)*wordSize)
            // @formatter:on
            int localsOffset = (graph.method().getMaxLocals() - 1) * wordSize;
            for (OSRLocalNode osrLocal : graph.getNodes(OSRLocalNode.TYPE)) {
                int size = osrLocal.getStackKind().getSlotCount();
                int offset = localsOffset - (osrLocal.index() + size - 1) * wordSize;
                AddressNode address = createOffsetAddress(graph, buffer, offset);
                ReadNode load = graph.add(new ReadNode(address, any(), osrLocal.stamp(NodeView.DEFAULT), BarrierType.NONE, MemoryOrderMode.PLAIN));
                osrLocal.replaceAndDelete(load);
                graph.addBeforeFixed(migrationEnd, load);
            }

            // @formatter:off
            // taken from c2 monitors_addr = osr_buf + (max_locals+mcnt*2-1)*wordSize);
            // @formatter:on
            final int lockCount = osrStart.stateAfter().locksSize();
            final int locksOffset = (graph.method().getMaxLocals() + lockCount * 2 - 1) * wordSize;

            // first initialize the lock slots for all enters with the displaced marks read from the
            // buffer
            for (OSRMonitorEnterNode osrMonitorEnter : graph.getNodes(OSRMonitorEnterNode.TYPE)) {
                MonitorIdNode monitorID = osrMonitorEnter.getMonitorId();
                OSRLockNode lock = (OSRLockNode) osrMonitorEnter.object();
                final int index = lock.index();

                final int offsetDisplacedHeader = locksOffset - ((index * 2) + 1) * wordSize;
                final int offsetLockObject = locksOffset - index * 2 * wordSize;

                // load the displaced mark from the osr buffer
                AddressNode addressDisplacedHeader = createOffsetAddress(graph, buffer, offsetDisplacedHeader);
                ReadNode loadDisplacedHeader = graph.add(new ReadNode(addressDisplacedHeader, any(), lock.stamp(NodeView.DEFAULT), BarrierType.NONE, MemoryOrderMode.PLAIN));
                graph.addBeforeFixed(migrationEnd, loadDisplacedHeader);

                // we need to initialize the stack slot for the lock
                BeginLockScopeNode beginLockScope = graph.add(new BeginLockScopeNode(lock.getStackKind(), monitorID.getLockDepth()));
                graph.addBeforeFixed(migrationEnd, beginLockScope);

                // write the displaced mark to the correct stack slot
                AddressNode addressDisplacedMark = createOffsetAddress(graph, beginLockScope, runtime.getVMConfig().basicLockDisplacedHeaderOffset);
                WriteNode writeStackSlot = graph.add(
                                new WriteNode(addressDisplacedMark, HotSpotReplacementsUtil.DISPLACED_MARK_WORD_LOCATION, loadDisplacedHeader, BarrierType.NONE, MemoryOrderMode.PLAIN));
                graph.addBeforeFixed(migrationEnd, writeStackSlot);

                // load the lock object from the osr buffer
                AddressNode addressLockObject = createOffsetAddress(graph, buffer, offsetLockObject);
                ReadNode loadObject = graph.add(new ReadNode(addressLockObject, any(), lock.stamp(NodeView.DEFAULT), BarrierType.NONE, MemoryOrderMode.PLAIN));
                lock.replaceAndDelete(loadObject);
                graph.addBeforeFixed(migrationEnd, loadObject);
            }

            osrStart.replaceAtUsagesAndDelete(newStart);
        }
    }

    static final class Exceptions {
        protected static final EnumMap<BytecodeExceptionKind, RuntimeException> cachedExceptions;

        static {
            cachedExceptions = new EnumMap<>(BytecodeExceptionKind.class);
            cachedExceptions.put(BytecodeExceptionKind.NULL_POINTER, clearStackTrace(new NullPointerException()));
            cachedExceptions.put(BytecodeExceptionKind.OUT_OF_BOUNDS, clearStackTrace(new ArrayIndexOutOfBoundsException()));
            cachedExceptions.put(BytecodeExceptionKind.CLASS_CAST, clearStackTrace(new ClassCastException()));
            cachedExceptions.put(BytecodeExceptionKind.ARRAY_STORE, clearStackTrace(new ArrayStoreException()));
            cachedExceptions.put(BytecodeExceptionKind.NEGATIVE_ARRAY_SIZE, clearStackTrace(new NegativeArraySizeException()));
            cachedExceptions.put(BytecodeExceptionKind.DIVISION_BY_ZERO, clearStackTrace(new ArithmeticException()));
            cachedExceptions.put(BytecodeExceptionKind.ILLEGAL_ARGUMENT_EXCEPTION_ARGUMENT_IS_NOT_AN_ARRAY,
                            clearStackTrace(new IllegalArgumentException(BytecodeExceptionKind.ILLEGAL_ARGUMENT_EXCEPTION_ARGUMENT_IS_NOT_AN_ARRAY.getExceptionMessage())));
        }

        private static RuntimeException clearStackTrace(RuntimeException ex) {
            ex.setStackTrace(new StackTraceElement[0]);
            return ex;
        }
    }

    public static final class RuntimeCalls {
        public static final EnumMap<BytecodeExceptionKind, ForeignCallSignature> runtimeCalls;

        static {
            runtimeCalls = new EnumMap<>(BytecodeExceptionKind.class);
            runtimeCalls.put(BytecodeExceptionKind.ARRAY_STORE, new ForeignCallSignature("createArrayStoreException", ArrayStoreException.class, Object.class));
            runtimeCalls.put(BytecodeExceptionKind.CLASS_CAST, new ForeignCallSignature("createClassCastException", ClassCastException.class, Object.class, KlassPointer.class));
            runtimeCalls.put(BytecodeExceptionKind.NULL_POINTER, new ForeignCallSignature("createNullPointerException", NullPointerException.class));
            runtimeCalls.put(BytecodeExceptionKind.OUT_OF_BOUNDS, new ForeignCallSignature("createOutOfBoundsException", ArrayIndexOutOfBoundsException.class, int.class, int.class));
            runtimeCalls.put(BytecodeExceptionKind.NEGATIVE_ARRAY_SIZE, new ForeignCallSignature("createNegativeArraySizeException", NegativeArraySizeException.class, int.class));
            runtimeCalls.put(BytecodeExceptionKind.DIVISION_BY_ZERO, new ForeignCallSignature("createDivisionByZeroException", ArithmeticException.class));
            runtimeCalls.put(BytecodeExceptionKind.INTEGER_EXACT_OVERFLOW, new ForeignCallSignature("createIntegerExactOverflowException", ArithmeticException.class));
            runtimeCalls.put(BytecodeExceptionKind.LONG_EXACT_OVERFLOW, new ForeignCallSignature("createLongExactOverflowException", ArithmeticException.class));
            runtimeCalls.put(BytecodeExceptionKind.ILLEGAL_ARGUMENT_EXCEPTION_ARGUMENT_IS_NOT_AN_ARRAY,
                            new ForeignCallSignature("createIllegalArgumentExceptionArgumentIsNotAnArray", IllegalArgumentException.class));
        }
    }

    private void throwCachedException(BytecodeExceptionNode node) {
        if (IS_IN_NATIVE_IMAGE) {
            throw new InternalError("Can't throw exception from SVM object");
        }
        Throwable exception = Exceptions.cachedExceptions.get(node.getExceptionKind());
        assert exception != null;

        StructuredGraph graph = node.graph();
        FloatingNode exceptionNode = ConstantNode.forConstant(constantReflection.forObject(exception), metaAccess, graph);
        graph.replaceFixedWithFloating(node, exceptionNode);
    }

    private void lowerBytecodeExceptionNode(BytecodeExceptionNode node) {
        if (OmitHotExceptionStacktrace.getValue(node.getOptions())) {
            throwCachedException(node);
            return;
        }

        ForeignCallSignature signature = RuntimeCalls.runtimeCalls.get(node.getExceptionKind());
        if (signature == null) {
            throw new GraalError("No runtime call available to lower BytecodeExceptionKind " + node.getExceptionKind());
        }
        ForeignCallDescriptor descriptor = foreignCalls.getDescriptor(signature);
        StructuredGraph graph = node.graph();
        List<ValueNode> arguments = node.getArguments();

        if (node.getExceptionKind() == BytecodeExceptionKind.CLASS_CAST) {
            assert arguments.size() == 2 : Assertions.errorMessage(node, arguments);
            /*
             * The foreign call expects the second argument to be the hub of the failing type check.
             * But when creating the BytecodeExceptionNode for dynamic type checks, it is difficult
             * to get the hub for the java.lang.Class instance in a VM-independent way. So we
             * convert the Class to the hub at this late stage.
             *
             * Note that the hub is null for primitive types. The ClassCastExceptionStub handles the
             * null value and uses a less verbose exception message in that case.
             */
            arguments = Arrays.asList(
                            arguments.get(0),
                            graph.addOrUniqueWithInputs(ClassGetHubNode.create(arguments.get(1), metaAccess, constantReflection)));
        }

        assert descriptor.getArgumentTypes().length == arguments.size() : Assertions.errorMessage(node, descriptor, arguments);
        ForeignCallNode foreignCallNode = graph.add(new ForeignCallNode(descriptor, node.stamp(NodeView.DEFAULT), arguments));
        /*
         * If a deoptimization is necessary then the stub itself will initiate the deoptimization
         * for this frame. See CreateExceptionStub#handleExceptionReturn.
         */
        foreignCallNode.setValidateDeoptFrameStates(false);
        /*
         * The original BytecodeExceptionNode is a StateState.Rethrow FrameState which isn't
         * suitable for deopt because the exception to be thrown comes from this call so it's not
         * available in the debug info. The foreign call cannot be a StateState.AfterPop because
         * that runs into assertions about which bytecodes must be reexecuted. The actual setting of
         * reexecute doesn't matter here because the call will simply rethrows the exception
         * instead.
         */
        FrameState stateDuring = node.stateAfter();
        stateDuring = stateDuring.duplicateModified(graph, stateDuring.bci, FrameState.StackState.BeforePop, JavaKind.Object, null, null, null);
        foreignCallNode.setStateDuring(stateDuring);
        // Keep the original rethrowException stateAfter for use by FSA
        foreignCallNode.setStateAfter(node.stateAfter());
        graph.replaceFixedWithFixed(node, foreignCallNode);
    }

    protected void lowerDeadEnd(DeadEndNode deadEnd) {
        StructuredGraph graph = deadEnd.graph();
        VMErrorNode vmErrorNode = graph.add(new VMErrorNode(new CStringConstant("DeadEnd"), graph.unique(ConstantNode.forLong(0))));
        DeoptimizeNode deopt = graph.add(new DeoptimizeNode(DeoptimizationAction.None, DeoptimizationReason.UnreachedCode));
        vmErrorNode.setNext(deopt);
        deadEnd.replaceAndDelete(vmErrorNode);
    }

    private ReadNode createReadVirtualMethod(StructuredGraph graph, ValueNode hub, HotSpotResolvedJavaMethod method, ResolvedJavaType receiverType) {
        return createReadVirtualMethod(graph, hub, method.vtableEntryOffset(receiverType));
    }

    private ReadNode createReadVirtualMethod(StructuredGraph graph, ValueNode hub, int vtableEntryOffset) {
        assert vtableEntryOffset > 0 : Assertions.errorMessage(hub, vtableEntryOffset);
        // We use LocationNode.ANY_LOCATION for the reads that access the vtable
        // entry as HotSpot does not guarantee that this is a final value.
        Stamp methodStamp = MethodPointerStamp.methodNonNull();
        AddressNode address = createOffsetAddress(graph, hub, vtableEntryOffset);
        ReadNode metaspaceMethod = graph.add(new ReadNode(address, any(), methodStamp, BarrierType.NONE, MemoryOrderMode.PLAIN));
        return metaspaceMethod;
    }

    @Override
    protected ValueNode createReadHub(StructuredGraph graph, ValueNode object, LoweringTool tool) {
        if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
            return graph.unique(new LoadHubNode(tool.getStampProvider(), object));
        }
        assert !object.isConstant() || object.isNullConstant();

        KlassPointerStamp hubStamp = KlassPointerStamp.klassNonNull();
        if (runtime.getVMConfig().useCompressedClassPointers) {
            hubStamp = hubStamp.compressed(runtime.getVMConfig().getKlassEncoding());
        }

        AddressNode address = createOffsetAddress(graph, object, runtime.getVMConfig().hubOffset);
        LocationIdentity hubLocation = runtime.getVMConfig().useCompressedClassPointers ? HotSpotReplacementsUtil.COMPRESSED_HUB_LOCATION : HotSpotReplacementsUtil.HUB_LOCATION;
        FloatingReadNode memoryRead = graph.unique(new FloatingReadNode(address, hubLocation, null, hubStamp, null, BarrierType.NONE));
        if (runtime.getVMConfig().useCompressedClassPointers) {
            return HotSpotCompressionNode.uncompress(graph, memoryRead, runtime.getVMConfig().getKlassEncoding());
        } else {
            return memoryRead;
        }
    }

    private WriteNode createWriteHub(StructuredGraph graph, ValueNode object, ValueNode value) {
        assert !object.isConstant() || object.asConstant().isDefaultForKind();

        ValueNode writeValue = value;
        if (runtime.getVMConfig().useCompressedClassPointers) {
            writeValue = HotSpotCompressionNode.compress(graph, value, runtime.getVMConfig().getKlassEncoding());
        }

        AddressNode address = createOffsetAddress(graph, object, runtime.getVMConfig().hubOffset);
        return graph.add(new WriteNode(address, HotSpotReplacementsUtil.HUB_WRITE_LOCATION, writeValue, BarrierType.NONE, MemoryOrderMode.PLAIN));
    }

    @Override
    public int fieldOffset(ResolvedJavaField f) {
        return f.getOffset();
    }

    @Override
    public int arrayLengthOffset() {
        return runtime.getVMConfig().arrayOopDescLengthOffset();
    }

    @Override
    public ObjectCloneSnippets.Templates getObjectCloneSnippets() {
        return objectCloneSnippets;
    }

    @Override
    public ForeignCallSnippets.Templates getForeignCallSnippets() {
        return foreignCallSnippets;
    }

    private void lowerRegisterFinalizer(RegisterFinalizerNode n, LoweringTool tool) {
        registerFinalizerSnippets.lower(n, tool);
    }
}
