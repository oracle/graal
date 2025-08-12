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
package jdk.graal.compiler.replacements;

import static jdk.graal.compiler.core.common.GraalOptions.EmitStringSubstitutions;
import static jdk.graal.compiler.core.common.GraalOptions.InlineGraalStubs;
import static jdk.graal.compiler.core.common.SpectrePHTMitigations.Options.SpectrePHTIndexMasking;
import static jdk.graal.compiler.nodes.NamedLocationIdentity.ARRAY_LENGTH_LOCATION;
import static jdk.graal.compiler.nodes.calc.BinaryArithmeticNode.branchlessMax;
import static jdk.graal.compiler.nodes.calc.BinaryArithmeticNode.branchlessMin;
import static jdk.graal.compiler.nodes.java.ArrayLengthNode.readArrayLength;
import static jdk.graal.compiler.phases.common.LockEliminationPhase.removeMonitorAccess;
import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateReprofile;
import static jdk.vm.ci.meta.DeoptimizationReason.BoundsCheckException;
import static jdk.vm.ci.meta.DeoptimizationReason.NullCheckException;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.CompressionNode.CompressionOp;
import jdk.graal.compiler.nodes.ComputeObjectAddressNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GetObjectAddressNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.AndNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.FloatingIntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.IntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.OrNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.nodes.calc.RightShiftNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.SignedDivNode;
import jdk.graal.compiler.nodes.calc.SignedFloatingIntegerDivNode;
import jdk.graal.compiler.nodes.calc.SignedFloatingIntegerRemNode;
import jdk.graal.compiler.nodes.calc.SignedRemNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.calc.UnpackEndianHalfNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.debug.VerifyHeapNode;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.ClassIsArrayNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.extended.GuardedUnsafeLoadNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.JavaReadNode;
import jdk.graal.compiler.nodes.extended.JavaWriteNode;
import jdk.graal.compiler.nodes.extended.LoadArrayComponentHubNode;
import jdk.graal.compiler.nodes.extended.LoadHubNode;
import jdk.graal.compiler.nodes.extended.LoadHubOrNullNode;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.nodes.extended.ObjectIsArrayNode;
import jdk.graal.compiler.nodes.extended.PublishWritesNode;
import jdk.graal.compiler.nodes.extended.RawLoadNode;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.extended.UnboxNode;
import jdk.graal.compiler.nodes.extended.UnsafeMemoryLoadNode;
import jdk.graal.compiler.nodes.extended.UnsafeMemoryStoreNode;
import jdk.graal.compiler.nodes.gc.BarrierSet;
import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.graal.compiler.nodes.java.AccessIndexedNode;
import jdk.graal.compiler.nodes.java.AccessMonitorNode;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.java.AtomicReadAndAddNode;
import jdk.graal.compiler.nodes.java.AtomicReadAndWriteNode;
import jdk.graal.compiler.nodes.java.InstanceOfDynamicNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.LogicCompareAndSwapNode;
import jdk.graal.compiler.nodes.java.LoweredAtomicReadAndAddNode;
import jdk.graal.compiler.nodes.java.LoweredAtomicReadAndWriteNode;
import jdk.graal.compiler.nodes.java.MonitorEnterNode;
import jdk.graal.compiler.nodes.java.MonitorIdNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.java.RegisterFinalizerNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.java.UnsafeCompareAndExchangeNode;
import jdk.graal.compiler.nodes.java.UnsafeCompareAndSwapNode;
import jdk.graal.compiler.nodes.java.ValueCompareAndSwapNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.SideEffectFreeWriteNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.IndexAddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringProvider;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.spi.PlatformConfigurationProvider;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.nodes.virtual.VirtualArrayNode;
import jdk.graal.compiler.nodes.virtual.VirtualInstanceNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import jdk.graal.compiler.replacements.nodes.IdentityHashCodeNode;
import jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.architecture.VectorLoweringProvider;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * VM-independent lowerings for standard Java nodes. VM-specific methods are abstract and must be
 * implemented by VM-specific subclasses.
 */
public abstract class DefaultJavaLoweringProvider implements LoweringProvider, VectorLoweringProvider {

    protected final MetaAccessProvider metaAccess;
    protected final ForeignCallsProvider foreignCalls;
    protected final BarrierSet barrierSet;
    protected final MetaAccessExtensionProvider metaAccessExtensionProvider;
    protected final TargetDescription target;
    private final boolean useCompressedOops;
    protected final VectorArchitecture vectorArchitecture;
    protected Replacements replacements;

    private BoxingSnippets.Templates boxingSnippets;
    private IdentityHashCodeSnippets.Templates identityHashCodeSnippets;
    protected IsArraySnippets.Templates isArraySnippets;
    protected StringLatin1Snippets.Templates latin1Templates;
    protected StringUTF16Snippets.Templates utf16templates;

    public DefaultJavaLoweringProvider(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, PlatformConfigurationProvider platformConfig,
                    MetaAccessExtensionProvider metaAccessExtensionProvider,
                    TargetDescription target, boolean useCompressedOops, VectorArchitecture vectorArchitecture) {
        this.metaAccess = metaAccess;
        this.foreignCalls = foreignCalls;
        this.barrierSet = platformConfig.getBarrierSet();
        this.metaAccessExtensionProvider = metaAccessExtensionProvider;
        this.target = target;
        this.useCompressedOops = useCompressedOops;
        this.vectorArchitecture = vectorArchitecture;
    }

    public void initialize(OptionValues options, SnippetCounter.Group.Factory factory, Providers providers) {
        replacements = providers.getReplacements();
        boxingSnippets = new BoxingSnippets.Templates(options, factory, providers);
        identityHashCodeSnippets = createIdentityHashCodeSnippets(options, providers);
        if (EmitStringSubstitutions.getValue(options)) {
            latin1Templates = new StringLatin1Snippets.Templates(options, providers);
            providers.getReplacements().registerSnippetTemplateCache(latin1Templates);
            utf16templates = new StringUTF16Snippets.Templates(options, providers);
            providers.getReplacements().registerSnippetTemplateCache(utf16templates);
        }
        providers.getReplacements().registerSnippetTemplateCache(new SnippetCounterNode.SnippetCounterSnippets.Templates(options, providers));
        providers.getReplacements().registerSnippetTemplateCache(new BigIntegerSnippets.Templates(options, providers));
    }

    protected abstract IdentityHashCodeSnippets.Templates createIdentityHashCodeSnippets(OptionValues options, Providers providers);

    @Override
    public boolean supportsImplicitNullChecks() {
        return target.implicitNullCheckLimit > 0;
    }

    @Override
    public final TargetDescription getTarget() {
        return target;
    }

    public MetaAccessProvider getMetaAccess() {
        return metaAccess;
    }

    @Override
    public BarrierSet getBarrierSet() {
        return barrierSet;
    }

    public MetaAccessExtensionProvider getMetaAccessExtensionProvider() {
        return metaAccessExtensionProvider;
    }

    public Replacements getReplacements() {
        return replacements;
    }

    @Override
    @SuppressWarnings("try")
    public void lower(Node n, LoweringTool tool) {
        assert n instanceof Lowerable : n;
        try (DebugCloseable context = n.withNodeSourcePosition()) {
            if (n instanceof LoadFieldNode) {
                lowerLoadFieldNode((LoadFieldNode) n, tool);
            } else if (n instanceof StoreFieldNode) {
                lowerStoreFieldNode((StoreFieldNode) n, tool);
            } else if (n instanceof LoadIndexedNode) {
                lowerLoadIndexedNode((LoadIndexedNode) n, tool);
            } else if (n instanceof StoreIndexedNode) {
                lowerStoreIndexedNode((StoreIndexedNode) n, tool);
            } else if (n instanceof IndexAddressNode) {
                lowerIndexAddressNode((IndexAddressNode) n);
            } else if (n instanceof ArrayLengthNode) {
                lowerArrayLengthNode((ArrayLengthNode) n, tool);
            } else if (n instanceof LoadHubNode) {
                lowerLoadHubNode((LoadHubNode) n, tool);
            } else if (n instanceof LoadHubOrNullNode) {
                lowerLoadHubOrNullNode((LoadHubOrNullNode) n, tool);
            } else if (n instanceof LoadArrayComponentHubNode) {
                lowerLoadArrayComponentHubNode((LoadArrayComponentHubNode) n, tool);
            } else if (n instanceof UnsafeCompareAndSwapNode) {
                lowerCompareAndSwapNode((UnsafeCompareAndSwapNode) n);
            } else if (n instanceof UnsafeCompareAndExchangeNode) {
                lowerCompareAndExchangeNode((UnsafeCompareAndExchangeNode) n);
            } else if (n instanceof AtomicReadAndWriteNode) {
                lowerAtomicReadAndWriteNode((AtomicReadAndWriteNode) n);
            } else if (n instanceof AtomicReadAndAddNode) {
                lowerAtomicReadAndAddNode((AtomicReadAndAddNode) n);
            } else if (n instanceof RawLoadNode) {
                lowerUnsafeLoadNode((RawLoadNode) n, tool);
            } else if (n instanceof UnsafeMemoryLoadNode) {
                lowerUnsafeMemoryLoadNode((UnsafeMemoryLoadNode) n);
            } else if (n instanceof RawStoreNode) {
                lowerUnsafeStoreNode((RawStoreNode) n);
            } else if (n instanceof UnsafeMemoryStoreNode) {
                lowerUnsafeMemoryStoreNode((UnsafeMemoryStoreNode) n);
            } else if (n instanceof JavaReadNode) {
                lowerJavaReadNode((JavaReadNode) n);
            } else if (n instanceof JavaWriteNode) {
                lowerJavaWriteNode((JavaWriteNode) n);
            } else if (n instanceof CommitAllocationNode) {
                lowerCommitAllocationNode((CommitAllocationNode) n, tool);
            } else if (n instanceof BoxNode) {
                if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.MID_TIER) {
                    boxingSnippets.lower((BoxNode) n, tool);
                }
            } else if (n instanceof UnboxNode) {
                if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.MID_TIER) {
                    boxingSnippets.lower((UnboxNode) n, tool);
                }
            } else if (n instanceof VerifyHeapNode) {
                lowerVerifyHeap((VerifyHeapNode) n);
            } else if (n instanceof UnaryMathIntrinsicNode) {
                lowerUnaryMath((UnaryMathIntrinsicNode) n, tool);
            } else if (n instanceof BinaryMathIntrinsicNode) {
                lowerBinaryMath((BinaryMathIntrinsicNode) n, tool);
            } else if (n instanceof UnpackEndianHalfNode) {
                lowerSecondHalf((UnpackEndianHalfNode) n);
            } else if (n instanceof RegisterFinalizerNode) {
                return;
            } else if (n instanceof IdentityHashCodeNode) {
                identityHashCodeSnippets.lower((IdentityHashCodeNode) n, tool);
            } else if (n instanceof ObjectIsArrayNode || n instanceof ClassIsArrayNode) {
                isArraySnippets.lower((LogicNode) n, tool);
            } else if (n instanceof ComputeObjectAddressNode) {
                StructuredGraph graph = (StructuredGraph) n.graph();
                if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                    lowerComputeObjectAddressNode((ComputeObjectAddressNode) n);
                }
            } else if (n instanceof FloatingIntegerDivRemNode<?> && tool.getLoweringStage() == LoweringTool.StandardLoweringStage.MID_TIER) {
                lowerFloatingIntegerDivRem((FloatingIntegerDivRemNode<?>) n, tool);
            } else if (!(n instanceof LIRLowerable)) {
                // Assume that nodes that implement both Lowerable and LIRLowerable will be handled
                // at the LIR level
                throw GraalError.shouldNotReachHere("Node implementing Lowerable not handled: " + n); // ExcludeFromJacocoGeneratedReport
            }
        }
    }

    protected void lowerFloatingIntegerDivRem(FloatingIntegerDivRemNode<?> divRem, LoweringTool tool) {
        FixedWithNextNode insertAfter = tool.lastFixedNode();
        StructuredGraph graph = insertAfter.graph();
        IntegerDivRemNode divRemFixed = null;
        ValueNode dividend = divRem.getX();
        ValueNode divisor = divRem.getY();
        if (divRem instanceof SignedFloatingIntegerDivNode) {
            divRemFixed = graph.add(new SignedDivNode(dividend, divisor, divRem.getGuard()));
        } else if (divRem instanceof SignedFloatingIntegerRemNode) {
            divRemFixed = graph.add(new SignedRemNode(dividend, divisor, divRem.getGuard()));
        } else {
            throw GraalError.shouldNotReachHere("divRem is null or has unexpected type: " + divRem); // ExcludeFromJacocoGeneratedReport
        }
        divRemFixed.setCanDeopt(false);
        divRem.replaceAtUsagesAndDelete(divRemFixed);
        graph.addAfterFixed(insertAfter, divRemFixed);
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.LOW_TIER) {
            divRemFixed.lower(tool);
        }
    }

    private static void lowerComputeObjectAddressNode(ComputeObjectAddressNode n) {
        /*
         * Lower the node into a GetObjectAddressNode node and an Add but ensure that it's below any
         * potential safepoints and above it's uses.
         */
        for (Node use : n.usages().snapshot()) {
            FixedNode fixed;
            if (use instanceof FixedNode) {
                fixed = (FixedNode) use;

            } else if (use instanceof ValuePhiNode) {
                ValuePhiNode phi = (ValuePhiNode) use;
                int inputPosition = 0;
                while (inputPosition < phi.valueCount()) {
                    if (phi.valueAt(inputPosition) == n) {
                        break;
                    }
                    inputPosition++;
                }
                GraalError.guarantee(inputPosition < phi.valueCount(), "Failed to find expected input");
                fixed = phi.merge().phiPredecessorAt(inputPosition);
            } else {
                throw GraalError.shouldNotReachHere("Unexpected floating use of ComputeObjectAddressNode " + n); // ExcludeFromJacocoGeneratedReport
            }
            StructuredGraph graph = n.graph();
            GetObjectAddressNode address = graph.add(new GetObjectAddressNode(n.getObject()));
            graph.addBeforeFixed(fixed, address);
            AddNode add = graph.addOrUnique(new AddNode(address, n.getOffset()));
            use.replaceFirstInput(n, add);
        }
        GraphUtil.unlinkFixedNode(n);
        n.safeDelete();
    }

    private void lowerSecondHalf(UnpackEndianHalfNode n) {
        ByteOrder byteOrder = target.arch.getByteOrder();
        n.lower(byteOrder);
    }

    private void lowerBinaryMath(BinaryMathIntrinsicNode math, LoweringTool tool) {
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
            return;
        }
        ResolvedJavaMethod method = math.graph().method();
        if (method != null) {
            if (replacements.isSnippet(method)) {
                // In the context of SnippetStub, i.e., Graal-generated stubs, use the LIR
                // lowering to emit the stub assembly code instead of the Node lowering.
                return;
            }
            if (method.getName().equalsIgnoreCase(math.getOperation().name()) && method.getDeclaringClass().getName().equals("Ljava/lang/Math;")) {
                // A root compilation of the intrinsic method should emit the full assembly
                // implementation.
                return;
            }
            if (InlineGraalStubs.getValue(math.graph().getOptions())) {
                return;
            }
        }
        StructuredGraph graph = math.graph();
        ForeignCallNode call = graph.add(new ForeignCallNode(foreignCalls, math.getOperation().foreignCallSignature, math.getX(), math.getY()));
        graph.addAfterFixed(tool.lastFixedNode(), call);
        math.replaceAtUsages(call);
    }

    private void lowerUnaryMath(UnaryMathIntrinsicNode math, LoweringTool tool) {
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
            return;
        }
        ResolvedJavaMethod method = math.graph().method();
        if (method != null) {
            if (method.getName().equalsIgnoreCase(math.getOperation().name()) && method.getDeclaringClass().getName().equals("Ljava/lang/Math;")) {
                // A root compilation of the intrinsic method should emit the full assembly
                // implementation.
                return;
            }
            if (InlineGraalStubs.getValue(math.graph().getOptions())) {
                return;
            }
        }
        lowerUnaryMathToForeignCall(math, tool);
    }

    protected void lowerUnaryMathToForeignCall(UnaryMathIntrinsicNode math, LoweringTool tool) {
        StructuredGraph graph = math.graph();
        ForeignCallDescriptor desc = foreignCalls.getDescriptor(math.getOperation().foreignCallSignature);
        Stamp s = UnaryMathIntrinsicNode.UnaryOperation.computeStamp(math.getOperation(), math.getValue().stamp(NodeView.DEFAULT));
        ForeignCallNode call = graph.add(new ForeignCallNode(desc, s, List.of(math.getValue())));
        graph.addAfterFixed(tool.lastFixedNode(), call);
        math.replaceAtUsages(call);
    }

    protected void lowerVerifyHeap(VerifyHeapNode n) {
        GraphUtil.removeFixedWithUnusedInputs(n);
    }

    public AddressNode createOffsetAddress(StructuredGraph graph, ValueNode object, long offset) {
        ValueNode o = ConstantNode.forIntegerKind(target.wordJavaKind, offset, graph);
        return graph.unique(new OffsetAddressNode(object, o));
    }

    public AddressNode createFieldAddress(StructuredGraph graph, ValueNode object, ResolvedJavaField field) {
        int offset = fieldOffset(field);
        if (offset >= 0) {
            return createOffsetAddress(graph, object, offset);
        } else {
            throw GraalError.shouldNotReachHere("Field is missing: " + field.getDeclaringClass().toJavaName(true) + "." + field.getName());
        }
    }

    public final JavaKind getStorageKind(ResolvedJavaField field) {
        return getStorageKind(field.getType());
    }

    public final JavaKind getStorageKind(JavaType type) {
        return metaAccessExtensionProvider.getStorageKind(type);
    }

    protected void lowerLoadFieldNode(LoadFieldNode loadField, LoweringTool tool) {
        assert loadField.getStackKind() != JavaKind.Illegal : loadField;
        StructuredGraph graph = loadField.graph();
        ResolvedJavaField field = loadField.field();
        ValueNode object = loadField.isStatic() ? staticFieldBase(graph, field) : loadField.object();
        object = createNullCheckedValue(object, loadField, tool);
        Stamp loadStamp = loadStamp(loadField.stamp(NodeView.DEFAULT), getStorageKind(field));

        AddressNode address = createFieldAddress(graph, object, field);

        BarrierType barrierType = barrierSet.fieldReadBarrierType(field, getStorageKind(field));
        ReadNode memoryRead = graph.add(new ReadNode(address, overrideFieldLocationIdentity(loadField.getLocationIdentity()),
                        loadStamp, barrierType, loadField.getMemoryOrder(), loadField.field(), loadField.trustInjected()));
        ValueNode readValue = implicitLoadConvert(graph, getStorageKind(field), memoryRead);
        loadField.replaceAtUsages(readValue);
        graph.replaceFixed(loadField, memoryRead);
    }

    protected void lowerStoreFieldNode(StoreFieldNode storeField, LoweringTool tool) {
        StructuredGraph graph = storeField.graph();
        ResolvedJavaField field = storeField.field();
        ValueNode object = storeField.isStatic() ? staticFieldBase(graph, field) : storeField.object();
        object = createNullCheckedValue(object, storeField, tool);
        ValueNode value = implicitStoreConvert(graph, getStorageKind(storeField.field()), storeField.value());
        AddressNode address = createFieldAddress(graph, object, field);

        BarrierType barrierType = barrierSet.fieldWriteBarrierType(field, getStorageKind(field));
        WriteNode memoryWrite = graph.add(new WriteNode(address, overrideFieldLocationIdentity(storeField.getLocationIdentity()), value, barrierType, storeField.getMemoryOrder()));
        memoryWrite.setStateAfter(storeField.stateAfter());
        graph.replaceFixedWithFixed(storeField, memoryWrite);
    }

    public static final IntegerStamp POSITIVE_ARRAY_INDEX_STAMP = IntegerStamp.create(32, 0, Integer.MAX_VALUE - 1);

    /**
     * Create a PiNode on the index proving that the index is positive. On some platforms this is
     * important to allow the index to be used as an int in the address mode.
     */
    protected ValueNode createPositiveIndex(StructuredGraph graph, ValueNode index, GuardingNode boundsCheck) {
        return graph.addOrUnique(PiNode.create(index, POSITIVE_ARRAY_INDEX_STAMP, boundsCheck != null ? boundsCheck.asNode() : null));
    }

    public AddressNode createArrayIndexAddress(StructuredGraph graph, ValueNode array, JavaKind elementKind, ValueNode index, GuardingNode boundsCheck) {
        return createArrayAddress(graph, array, elementKind, createPositiveIndex(graph, index, boundsCheck));
    }

    public AddressNode createArrayAddress(StructuredGraph graph, ValueNode array, JavaKind elementKind, ValueNode index) {
        return createArrayAddress(graph, array, elementKind, elementKind, index);
    }

    public AddressNode createArrayAddress(StructuredGraph graph, ValueNode array, JavaKind arrayKind, JavaKind elementKind, ValueNode index) {
        int base = metaAccess.getArrayBaseOffset(arrayKind);
        return createArrayAddress(graph, array, base, elementKind, index);
    }

    public AddressNode createArrayAddress(StructuredGraph graph, ValueNode array, int arrayBaseOffset, JavaKind elementKind, ValueNode index) {
        ValueNode wordIndex;
        if (target.wordSize > 4) {
            wordIndex = graph.unique(new SignExtendNode(index, target.wordSize * 8));
        } else {
            assert target.wordSize == 4 : "unsupported word size";
            wordIndex = index;
        }
        int shift = CodeUtil.log2(metaAccess.getArrayIndexScale(elementKind));
        ValueNode scaledIndex = graph.unique(new LeftShiftNode(wordIndex, ConstantNode.forInt(shift, graph)));
        ValueNode offset = graph.unique(new AddNode(scaledIndex, ConstantNode.forIntegerKind(target.wordJavaKind, arrayBaseOffset, graph)));
        return graph.unique(new OffsetAddressNode(array, offset));
    }

    protected void lowerIndexAddressNode(IndexAddressNode indexAddress) {
        AddressNode lowered = createArrayAddress(indexAddress.graph(), indexAddress.getArray(), indexAddress.getArrayKind(), indexAddress.getElementKind(), indexAddress.getIndex());
        indexAddress.replaceAndDelete(lowered);
    }

    public void lowerLoadIndexedNode(LoadIndexedNode loadIndexed, LoweringTool tool) {
        int arrayBaseOffset = metaAccess.getArrayBaseOffset(loadIndexed.elementKind());
        lowerLoadIndexedNode(loadIndexed, tool, arrayBaseOffset);
    }

    public void lowerLoadIndexedNode(LoadIndexedNode loadIndexed, LoweringTool tool, int arrayBaseOffset) {
        StructuredGraph graph = loadIndexed.graph();
        ValueNode array = loadIndexed.array();
        array = createNullCheckedValue(array, loadIndexed, tool);
        JavaKind elementKind = loadIndexed.elementKind();
        Stamp loadStamp = loadStamp(loadIndexed.stamp(NodeView.DEFAULT), elementKind);

        GuardingNode boundsCheck = getBoundsCheck(loadIndexed, array, tool);
        ValueNode index = loadIndexed.index();
        if (SpectrePHTIndexMasking.getValue(graph.getOptions())) {
            index = graph.addOrUniqueWithInputs(proxyIndex(loadIndexed, index, array, tool));
        }
        ValueNode positiveIndex = createPositiveIndex(graph, index, boundsCheck);
        AddressNode address = createArrayAddress(graph, array, arrayBaseOffset, elementKind, positiveIndex);

        LocationIdentity arrayLocation = NamedLocationIdentity.getArrayLocation(elementKind);
        ReadNode memoryRead = graph.add(new ReadNode(address, arrayLocation, loadStamp, barrierSet.readBarrierType(arrayLocation, address, loadStamp), MemoryOrderMode.PLAIN));
        memoryRead.setGuard(boundsCheck);
        ValueNode readValue = implicitLoadConvert(graph, elementKind, memoryRead);

        loadIndexed.replaceAtUsages(readValue);
        graph.replaceFixed(loadIndexed, memoryRead);
    }

    public void lowerStoreIndexedNode(StoreIndexedNode storeIndexed, LoweringTool tool) {
        int arrayBaseOffset = metaAccess.getArrayBaseOffset(storeIndexed.elementKind());
        lowerStoreIndexedNode(storeIndexed, tool, arrayBaseOffset);
    }

    public void lowerStoreIndexedNode(StoreIndexedNode storeIndexed, LoweringTool tool, int arrayBaseOffset) {
        StructuredGraph graph = storeIndexed.graph();

        ValueNode value = storeIndexed.value();
        ValueNode array = storeIndexed.array();

        array = this.createNullCheckedValue(array, storeIndexed, tool);

        GuardingNode boundsCheck = getBoundsCheck(storeIndexed, array, tool);

        JavaKind storageKind = storeIndexed.elementKind();

        LogicNode condition = null;
        if (storeIndexed.getStoreCheck() == null && storageKind == JavaKind.Object && !StampTool.isPointerAlwaysNull(value)) {
            /* Array store check. */
            TypeReference arrayType = StampTool.typeReferenceOrNull(array);
            if (arrayType != null && arrayType.isExact()) {
                ResolvedJavaType elementType = arrayType.getType().getComponentType();
                if (!elementType.isJavaLangObject()) {
                    TypeReference typeReference = TypeReference.createTrusted(storeIndexed.graph().getAssumptions(), elementType);
                    LogicNode typeTest = graph.addOrUniqueWithInputs(InstanceOfNode.create(typeReference, value));
                    condition = LogicNode.or(graph.unique(IsNullNode.create(value)), typeTest, BranchProbabilityNode.NOT_LIKELY_PROFILE);
                }
            } else {
                /*
                 * The guard on the read hub should be the null check of the array that was
                 * introduced earlier.
                 */
                ValueNode arrayClass = createReadHub(graph, array, tool, tool.lastFixedNode());
                boolean isKnownObjectArray = arrayType != null && !arrayType.getType().getComponentType().isPrimitive();
                ValueNode componentHub = createReadArrayComponentHub(graph, arrayClass, isKnownObjectArray, storeIndexed, tool, tool.lastFixedNode());
                LogicNode typeTest = graph.unique(InstanceOfDynamicNode.create(graph.getAssumptions(), tool.getConstantReflection(), componentHub, value, false));
                condition = LogicNode.or(graph.unique(IsNullNode.create(value)), typeTest, BranchProbabilityNode.NOT_LIKELY_PROFILE);
            }
            if (condition != null && condition.isTautology()) {
                // Skip unnecessary guards
                condition = null;
            }
        }
        BarrierType barrierType = barrierSet.arrayWriteBarrierType(storageKind);
        ValueNode positiveIndex = createPositiveIndex(graph, storeIndexed.index(), boundsCheck);
        AddressNode address = createArrayAddress(graph, array, arrayBaseOffset, storageKind, positiveIndex);
        WriteNode memoryWrite = graph.add(new WriteNode(address, NamedLocationIdentity.getArrayLocation(storageKind), implicitStoreConvert(graph, storageKind, value),
                        barrierType, MemoryOrderMode.PLAIN));
        memoryWrite.setGuard(boundsCheck);
        if (condition != null) {
            tool.createGuard(storeIndexed, condition, DeoptimizationReason.ArrayStoreException, DeoptimizationAction.InvalidateReprofile);
        }
        memoryWrite.setStateAfter(storeIndexed.stateAfter());
        graph.replaceFixedWithFixed(storeIndexed, memoryWrite);
    }

    protected void lowerArrayLengthNode(ArrayLengthNode arrayLengthNode, LoweringTool tool) {
        StructuredGraph graph = arrayLengthNode.graph();
        arrayLengthNode.replaceAtUsages(createReadArrayLength(arrayLengthNode.array(), arrayLengthNode, tool));
        graph.removeFixed(arrayLengthNode);
    }

    /**
     * Creates a read node that read the array length and is guarded by a null-check.
     * <p>
     * The created node is placed before {@code before} in the CFG.
     */
    private ReadNode createReadArrayLength(ValueNode array, FixedNode before, LoweringTool tool) {
        StructuredGraph graph = array.graph();
        ValueNode canonicalArray = this.createNullCheckedValue(GraphUtil.skipPiWhileNonNullArray(array), before, tool);
        AddressNode address = createOffsetAddress(graph, canonicalArray, arrayLengthOffset());
        ReadNode readArrayLength = graph.add(new ReadNode(address, ARRAY_LENGTH_LOCATION, StampFactory.positiveInt(), BarrierType.NONE, MemoryOrderMode.PLAIN));
        graph.addBeforeFixed(before, readArrayLength);
        return readArrayLength;
    }

    protected void lowerLoadHubNode(LoadHubNode loadHub, LoweringTool tool) {
        StructuredGraph graph = loadHub.graph();
        if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
            return;
        }
        if (graph.getGuardsStage().allowsFloatingGuards()) {
            return;
        }
        ValueNode hub = createReadHub(graph, loadHub.getValue(), tool, tool.lastFixedNode());
        loadHub.replaceAtUsagesAndDelete(hub);
    }

    protected void lowerLoadHubOrNullNode(LoadHubOrNullNode loadHubOrNullNode, LoweringTool tool) {
        StructuredGraph graph = loadHubOrNullNode.graph();
        if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
            return;
        }
        if (graph.getGuardsStage().allowsFloatingGuards()) {
            return;
        }
        ValueNode object = loadHubOrNullNode.getValue();
        if (object.isConstant() && !object.asJavaConstant().isNull()) {
            /*
             * Special case: insufficient canonicalization was run since the last lowering, if we
             * are loading the hub from a constant we want to still fold it.
             */
            ValueNode synonym = LoadHubNode.findSynonym(object, loadHubOrNullNode.stamp(NodeView.DEFAULT), tool.getMetaAccess(), tool.getConstantReflection());
            if (synonym != null) {
                loadHubOrNullNode.replaceAtUsagesAndDelete(graph.addOrUnique(synonym));
                return;
            }
        }
        final FixedWithNextNode predecessor = tool.lastFixedNode();
        final ValueNode value = loadHubOrNullNode.getValue();
        AbstractPointerStamp stamp = (AbstractPointerStamp) value.stamp(NodeView.DEFAULT);
        final LogicNode isNull = graph.addOrUniqueWithInputs(IsNullNode.create(value));
        final EndNode trueEnd = graph.add(new EndNode());
        final EndNode falseEnd = graph.add(new EndNode());
        // We do not know the probability of this object being null. Assuming null is uncommon and
        // can be wrong for exact type checks and cause performance degradations.
        final IfNode ifNode = graph.add(new IfNode(isNull, trueEnd, falseEnd, BranchProbabilityData.injected(BranchProbabilityNode.NOT_FREQUENT_PROBABILITY)));
        final MergeNode merge = graph.add(new MergeNode());
        merge.addForwardEnd(trueEnd);
        merge.addForwardEnd(falseEnd);
        final AbstractPointerStamp hubStamp = (AbstractPointerStamp) loadHubOrNullNode.stamp(NodeView.DEFAULT);
        ValueNode nullHub = ConstantNode.forConstant(hubStamp.asAlwaysNull(), JavaConstant.NULL_POINTER, tool.getMetaAccess(), graph);
        final ValueNode nonNullValue = graph.addOrUniqueWithInputs(PiNode.create(value, stamp.asNonNull(), ifNode.falseSuccessor()));
        ValueNode hub = createReadHub(graph, nonNullValue, tool, ifNode.falseSuccessor());
        ValueNode[] values = new ValueNode[]{nullHub, hub};
        final PhiNode hubPhi = graph.unique(new ValuePhiNode(hubStamp, merge, values));
        final FixedNode oldNext = predecessor.next();
        predecessor.setNext(ifNode);
        merge.setNext(oldNext);
        loadHubOrNullNode.replaceAtUsagesAndDelete(hubPhi);
    }

    protected void lowerLoadArrayComponentHubNode(LoadArrayComponentHubNode loadHub, LoweringTool tool) {
        StructuredGraph graph = loadHub.graph();
        ValueNode hub = createReadArrayComponentHub(graph, loadHub.getValue(), false, loadHub, tool, tool.lastFixedNode());
        graph.replaceFixed(loadHub, hub);
    }

    protected void lowerCompareAndSwapNode(UnsafeCompareAndSwapNode cas) {
        StructuredGraph graph = cas.graph();
        JavaKind valueKind = cas.getValueKind();

        ValueNode expectedValue = implicitStoreConvert(graph, valueKind, cas.expected());
        ValueNode newValue = implicitStoreConvert(graph, valueKind, cas.newValue());

        AddressNode address = graph.unique(new OffsetAddressNode(cas.object(), cas.offset()));
        BarrierType barrierType = barrierSet.readWriteBarrier(cas.object(), newValue);
        LogicCompareAndSwapNode atomicNode = graph.add(
                        new LogicCompareAndSwapNode(address, expectedValue, newValue, cas.getKilledLocationIdentity(), barrierType, cas.getMemoryOrder()));
        atomicNode.setStateAfter(cas.stateAfter());
        graph.replaceFixedWithFixed(cas, atomicNode);
    }

    protected void lowerCompareAndExchangeNode(UnsafeCompareAndExchangeNode cas) {
        StructuredGraph graph = cas.graph();
        JavaKind valueKind = cas.getValueKind();

        ValueNode expectedValue = implicitStoreConvert(graph, valueKind, cas.expected());
        ValueNode newValue = implicitStoreConvert(graph, valueKind, cas.newValue());

        AddressNode address = graph.unique(new OffsetAddressNode(cas.object(), cas.offset()));
        BarrierType barrierType = barrierSet.readWriteBarrier(cas.object(), newValue);
        ValueCompareAndSwapNode atomicNode = graph.add(
                        new ValueCompareAndSwapNode(address, expectedValue, newValue, cas.getKilledLocationIdentity(), barrierType, cas.getMemoryOrder()));
        ValueNode coercedNode = implicitLoadConvert(graph, valueKind, atomicNode, true);
        atomicNode.setStateAfter(cas.stateAfter());
        cas.replaceAtUsages(coercedNode);
        graph.replaceFixedWithFixed(cas, atomicNode);
    }

    protected void lowerAtomicReadAndWriteNode(AtomicReadAndWriteNode n) {
        StructuredGraph graph = n.graph();
        JavaKind valueKind = n.getValueKind();

        ValueNode newValue = implicitStoreConvert(graph, valueKind, n.newValue());

        AddressNode address = graph.unique(new OffsetAddressNode(n.object(), n.offset()));
        BarrierType barrierType = barrierSet.readWriteBarrier(n.object(), newValue);
        LoweredAtomicReadAndWriteNode memoryRead = graph.add(new LoweredAtomicReadAndWriteNode(address, n.getKilledLocationIdentity(), newValue, barrierType));
        memoryRead.setStateAfter(n.stateAfter());

        ValueNode readValue = implicitLoadConvert(graph, valueKind, memoryRead);
        n.stateAfter().replaceFirstInput(n, memoryRead);
        n.replaceAtUsages(readValue);
        graph.replaceFixedWithFixed(n, memoryRead);
    }

    protected void lowerAtomicReadAndAddNode(AtomicReadAndAddNode n) {
        StructuredGraph graph = n.graph();
        JavaKind valueKind = n.getValueKind();

        ValueNode delta = implicitStoreConvert(graph, valueKind, n.delta());

        AddressNode address = graph.unique(new OffsetAddressNode(n.object(), n.offset()));
        LoweredAtomicReadAndAddNode memoryRead = graph.add(new LoweredAtomicReadAndAddNode(address, n.getKilledLocationIdentity(), delta));
        memoryRead.setStateAfter(n.stateAfter());

        ValueNode readValue = implicitLoadConvert(graph, valueKind, memoryRead);
        n.stateAfter().replaceFirstInput(n, memoryRead);
        n.replaceAtUsages(readValue);
        graph.replaceFixedWithFixed(n, memoryRead);
    }

    /**
     * @param tool utility for performing the lowering
     */
    protected void lowerUnsafeLoadNode(RawLoadNode load, LoweringTool tool) {
        StructuredGraph graph = load.graph();
        if (load instanceof GuardedUnsafeLoadNode) {
            GuardedUnsafeLoadNode guardedLoad = (GuardedUnsafeLoadNode) load;
            GuardingNode guard = guardedLoad.getGuard();
            if (guard == null) {
                // can float freely if the guard folded away
                ReadNode memoryRead = createUnsafeRead(graph, load, null);
                memoryRead.setForceFixed(false);
                graph.replaceFixedWithFixed(load, memoryRead);
            } else {
                // must be guarded, but flows below the guard
                ReadNode memoryRead = createUnsafeRead(graph, load, guard);
                graph.replaceFixedWithFixed(load, memoryRead);
            }
        } else {
            // never had a guarding condition so it must be fixed, creation of the read will force
            // it to be fixed
            ReadNode memoryRead = createUnsafeRead(graph, load, null);
            graph.replaceFixedWithFixed(load, memoryRead);
        }
    }

    protected AddressNode createUnsafeAddress(StructuredGraph graph, ValueNode object, ValueNode offset) {
        if (object.isConstant() && object.asConstant().isDefaultForKind()) {
            return graph.addOrUniqueWithInputs(OffsetAddressNode.create(offset));
        } else {
            return graph.unique(new OffsetAddressNode(object, offset));
        }
    }

    protected ReadNode createUnsafeRead(StructuredGraph graph, RawLoadNode load, GuardingNode guard) {
        boolean compressible = load.accessKind() == JavaKind.Object;
        JavaKind readKind = load.accessKind();
        Stamp loadStamp = loadStamp(load.stamp(NodeView.DEFAULT), readKind, compressible);
        AddressNode address = createUnsafeAddress(graph, load.object(), load.offset());
        LocationIdentity location = load.getLocationIdentity();
        ReadNode memoryRead = graph.add(new ReadNode(address, location, loadStamp, barrierSet.readBarrierType(location, address, loadStamp), load.getMemoryOrder()));
        if (guard == null) {
            // An unsafe read must not float otherwise it may float above
            // a test guaranteeing the read is safe.
            memoryRead.setForceFixed(true);
        } else {
            memoryRead.setGuard(guard);
        }
        ValueNode readValue = implicitUnsafeLoadConvert(graph, readKind, memoryRead, compressible);
        load.replaceAtUsages(readValue);
        return memoryRead;
    }

    protected void lowerUnsafeMemoryLoadNode(UnsafeMemoryLoadNode load) {
        StructuredGraph graph = load.graph();
        JavaKind readKind = load.getKind();
        Stamp loadStamp = loadStamp(load.stamp(NodeView.DEFAULT), readKind, false);
        AddressNode address = graph.addOrUniqueWithInputs(OffsetAddressNode.create(load.getAddress()));
        LocationIdentity location = load.getLocationIdentity();
        ReadNode memoryRead = graph.add(new ReadNode(address, location, loadStamp, barrierSet.readBarrierType(location, address, loadStamp), MemoryOrderMode.PLAIN));
        // An unsafe read must not float otherwise it may float above
        // a test guaranteeing the read is safe.
        memoryRead.setForceFixed(true);
        ValueNode readValue = implicitUnsafeLoadConvert(graph, readKind, memoryRead, false);
        load.replaceAtUsages(readValue);
        graph.replaceFixedWithFixed(load, memoryRead);
    }

    /**
     * Coerce integer values into a boolean 0 or 1 to match Java semantics. The returned nodes have
     * not been added to the graph.
     */
    private static ValueNode performBooleanCoercion(ValueNode readValue) {
        IntegerEqualsNode eq = new IntegerEqualsNode(readValue, ConstantNode.forInt(0));
        return new ConditionalNode(eq, ConstantNode.forBoolean(false), ConstantNode.forBoolean(true));
    }

    protected void lowerUnsafeStoreNode(RawStoreNode store) {
        StructuredGraph graph = store.graph();
        boolean compressible = store.value().getStackKind() == JavaKind.Object;
        JavaKind valueKind = store.accessKind();
        ValueNode value = implicitStoreConvert(graph, valueKind, store.value(), compressible);
        AddressNode address = createUnsafeAddress(graph, store.object(), store.offset());
        WriteNode write = graph.add(new WriteNode(address, store.getLocationIdentity(), value, barrierSet.writeBarrierType(store), store.getMemoryOrder()));
        write.setStateAfter(store.stateAfter());
        graph.replaceFixedWithFixed(store, write);
    }

    protected void lowerUnsafeMemoryStoreNode(UnsafeMemoryStoreNode store) {
        StructuredGraph graph = store.graph();
        assert store.getValue().getStackKind() != JavaKind.Object : Assertions.errorMessageContext("store", store);
        JavaKind valueKind = store.getKind();
        ValueNode value = implicitStoreConvert(graph, valueKind, store.getValue(), false);
        AddressNode address = graph.addOrUniqueWithInputs(OffsetAddressNode.create(store.getAddress()));
        WriteNode write = graph.add(new WriteNode(address, store.getKilledLocationIdentity(), value, BarrierType.NONE, MemoryOrderMode.PLAIN));
        write.setStateAfter(store.stateAfter());
        graph.replaceFixedWithFixed(store, write);
    }

    protected void lowerJavaReadNode(JavaReadNode read) {
        StructuredGraph graph = read.graph();
        JavaKind valueKind = read.getReadKind();
        Stamp loadStamp = loadStamp(read.stamp(NodeView.DEFAULT), valueKind, read.isCompressible());

        ReadNode memoryRead = graph.add(new ReadNode(read.getAddress(), read.getLocationIdentity(), loadStamp, read.getBarrierType(), read.getMemoryOrder()));

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
        ValueNode value = implicitStoreConvert(graph, write.getWriteKind(), write.value(), write.isCompressible());
        WriteNode memoryWrite;
        if (write.hasSideEffect()) {
            memoryWrite = graph.add(new WriteNode(write.getAddress(), write.getKilledLocationIdentity(), value, write.getBarrierType(), write.getMemoryOrder()));
        } else {
            assert !write.ordersMemoryAccesses();
            memoryWrite = graph.add(new SideEffectFreeWriteNode(write.getAddress(), write.getKilledLocationIdentity(), value, write.getBarrierType()));
        }
        memoryWrite.setStateAfter(write.stateAfter());
        graph.replaceFixedWithFixed(write, memoryWrite);
        memoryWrite.setGuard(write.getGuard());
    }

    @SuppressWarnings("try")
    protected void lowerCommitAllocationNode(CommitAllocationNode commit, LoweringTool tool) {
        StructuredGraph graph = commit.graph();
        if (graph.getGuardsStage().allowsFloatingGuards()) {
            return;
        }

        List<VirtualObjectNode> virtualObjects = commit.getVirtualObjects();
        // Record starting position for each object
        int[] valuePositions = new int[virtualObjects.size()];
        for (int objIndex = 0, valuePos = 0; objIndex < virtualObjects.size(); objIndex++) {
            valuePositions[objIndex] = valuePos;
            valuePos += virtualObjects.get(objIndex).entryCount();
        }

        /*
         * Try to emit the allocations in an order where objects are allocated before they are
         * needed by other allocations. In the worst case there might be cycles which can't be
         * broken and those stores might need to be performed as if we aren't writing to
         * INIT_MEMORY. This ensures that GC barrier assumptions aren't violated.
         */
        int[] emissionOrder = new int[virtualObjects.size()];
        computeAllocationEmissionOrder(commit, emissionOrder);

        List<AbstractNewObjectNode> recursiveLowerings = new ArrayList<>();
        ValueNode[] allocations = new ValueNode[virtualObjects.size()];
        BitSet omittedValues = new BitSet();
        for (int objIndex : emissionOrder) {
            VirtualObjectNode virtual = virtualObjects.get(objIndex);
            try (DebugCloseable nsp = graph.withNodeSourcePosition(virtual)) {
                int entryCount = virtual.entryCount();
                AbstractNewObjectNode newObject = createUninitializedObject(virtual, graph);

                recursiveLowerings.add(newObject);
                graph.addBeforeFixed(commit, newObject);
                allocations[objIndex] = newObject;
                int valuePos = valuePositions[objIndex];
                for (int i = 0; i < entryCount; i++) {
                    ValueNode value = commit.getValues().get(valuePos);
                    if (value instanceof VirtualObjectNode) {
                        value = allocations[virtualObjects.indexOf(value)];
                    }
                    if (value == null) {
                        omittedValues.set(valuePos);
                    } else if (!(value.isConstant() && value.asConstant().isDefaultForKind())) {
                        // Constant.illegal is always the defaultForKind, so it is skipped
                        JavaKind valueKind = value.getStackKind();
                        JavaKind storageKind = virtual.entryKind(tool.getMetaAccessExtensionProvider(), i);

                        // Truffle requires some leniency in terms of what can be put where:
                        assert valueKind.getStackKind() == storageKind.getStackKind() ||
                                        (valueKind == JavaKind.Long || valueKind == JavaKind.Double || (valueKind == JavaKind.Int && virtual instanceof VirtualArrayNode) ||
                                                        (valueKind == JavaKind.Float && virtual instanceof VirtualArrayNode)) : Assertions.errorMessageContext("valueKind", valueKind,
                                                                        "virtual",
                                                                        virtual);
                        AddressNode address = null;
                        BarrierType barrierType = null;
                        if (virtual instanceof VirtualInstanceNode) {
                            ResolvedJavaField field = ((VirtualInstanceNode) virtual).field(i);
                            long offset = fieldOffset(field);
                            if (offset >= 0) {
                                address = createOffsetAddress(graph, newObject, offset);
                                barrierType = barrierSet.fieldWriteBarrierType(field, getStorageKind(field));
                            }
                        } else {
                            assert virtual instanceof VirtualArrayNode : Assertions.errorMessageContext("virtual", virtual);
                            address = createOffsetAddress(graph, newObject, metaAccess.getArrayBaseOffset(storageKind) + i * metaAccess.getArrayIndexScale(storageKind));
                            barrierType = barrierSet.arrayWriteBarrierType(storageKind);
                        }
                        if (address != null) {
                            WriteNode write = graph.add(
                                            new WriteNode(address, LocationIdentity.init(), arrayImplicitStoreConvert(graph, storageKind, value, commit, virtual, valuePos), barrierType,
                                                            MemoryOrderMode.PLAIN));
                            graph.addAfterFixed(newObject, write);
                        }
                    }
                    valuePos++;
                }
            }
        }

        writeOmittedValues(commit, graph, allocations, omittedValues);
        finishAllocatedObjects(tool, commit, commit, allocations);
        graph.removeFixed(commit);

        for (AbstractNewObjectNode recursiveLowering : recursiveLowerings) {
            recursiveLowering.lower(tool);
        }
    }

    public AbstractNewObjectNode createUninitializedObject(VirtualObjectNode virtual, StructuredGraph graph) {
        AbstractNewObjectNode ret;
        if (virtual instanceof VirtualInstanceNode virtualInstance) {
            ret = graph.add(createUninitializedInstance(virtualInstance));
        } else {
            ValueNode length = ConstantNode.forInt(virtual.entryCount(), graph);
            ret = graph.add(createUninitializedArray((VirtualArrayNode) virtual, length));
        }
        // The final STORE_STORE barrier will be emitted by finishAllocatedObjects
        ret.clearEmitMemoryBarrier();
        return ret;
    }

    protected NewInstanceNode createUninitializedInstance(VirtualInstanceNode virtual) {
        return new NewInstanceNode(virtual.type(), true);
    }

    protected NewArrayNode createUninitializedArray(VirtualArrayNode virtual, ValueNode length) {
        return new NewArrayNode(virtual.componentType(), length, true);
    }

    @SuppressWarnings("try")
    public void writeOmittedValues(CommitAllocationNode commit, StructuredGraph graph, ValueNode[] allocations, BitSet omittedValues) {
        int valuePos = 0;
        for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
            VirtualObjectNode virtual = commit.getVirtualObjects().get(objIndex);
            try (DebugCloseable nsp = graph.withNodeSourcePosition(virtual)) {
                int entryCount = virtual.entryCount();
                ValueNode newObject = allocations[objIndex];
                for (int i = 0; i < entryCount; i++, valuePos++) {
                    if (!omittedValues.get(valuePos)) {
                        continue;
                    }
                    ValueNode value = commit.getValues().get(valuePos);
                    assert value instanceof VirtualObjectNode : Assertions.errorMessageContext("value", value);
                    ValueNode allocValue = allocations[commit.getVirtualObjects().indexOf(value)];
                    if (!(allocValue.isConstant() && allocValue.asConstant().isDefaultForKind())) {
                        JavaKind entryKind = virtual.entryKind(metaAccessExtensionProvider, i);
                        assert entryKind == JavaKind.Object : Assertions.errorMessageContext("entryKind", entryKind);
                        assert allocValue.getStackKind() == JavaKind.Object : Assertions.errorMessageContext("entryKind", entryKind);
                        AddressNode address = null;
                        BarrierType barrierType = null;
                        if (virtual instanceof VirtualInstanceNode virtualInstance) {
                            ResolvedJavaField field = virtualInstance.field(i);
                            if (fieldOffset(field) >= 0) {
                                address = createFieldAddress(graph, newObject, field);
                                barrierType = barrierSet.fieldWriteBarrierType(field, getStorageKind(field));
                            }
                        } else {
                            assert virtual instanceof VirtualArrayNode : Assertions.errorMessage(commit, virtual);
                            address = createArrayAddress(graph, newObject, entryKind, ConstantNode.forInt(i, graph));
                            barrierType = barrierSet.arrayWriteBarrierType(entryKind);
                        }
                        if (address != null) {
                            barrierType = barrierSet.postAllocationInitBarrier(barrierType);
                            WriteNode write = graph.add(
                                            new WriteNode(address, LocationIdentity.init(), implicitStoreConvert(graph, JavaKind.Object, allocValue), barrierType, MemoryOrderMode.PLAIN));
                            graph.addBeforeFixed(commit, write);
                        }
                    }
                }
            }
        }
    }

    private static void computeAllocationEmissionOrder(CommitAllocationNode commit, int[] order) {
        int size = commit.getVirtualObjects().size();
        boolean[] complete = new boolean[size];
        int cur = 0;
        /*
         * Visit each allocation checking whether all values for the allocation are available. If
         * they are then append the allocation to the order. Repeat this until there is no change in
         * the state. Any remaining values must be a cycle.
         */
        boolean progress = true;
        while (progress) {
            progress = false;
            int valuePos = 0;
            for (int objIndex = 0; objIndex < size; objIndex++) {
                if (complete[objIndex]) {
                    continue;
                }
                VirtualObjectNode virtual = commit.getVirtualObjects().get(objIndex);
                int entryCount = virtual.entryCount();

                boolean allValuesAvailable = true;
                for (int i = 0; i < entryCount; i++) {
                    ValueNode value = commit.getValues().get(valuePos);
                    if (value instanceof VirtualObjectNode) {
                        if (!complete[commit.getVirtualObjects().indexOf(value)]) {
                            allValuesAvailable = false;
                            break;
                        }
                    }
                    valuePos++;
                }
                if (allValuesAvailable) {
                    progress = true;
                    complete[objIndex] = true;
                    order[cur++] = objIndex;
                }
            }
        }

        // Any remaining values are part of a cycle so just emit them in the declare order.
        for (int i = 0; i < size; i++) {
            if (!complete[i]) {
                order[cur++] = i;
            }
        }
    }

    private static boolean isNestedLock(MonitorIdNode lock, CommitAllocationNode commit) {
        for (MonitorIdNode otherLock : commit.getLocks()) {
            if (otherLock.getLockDepth() < lock.getLockDepth() && commit.getObjectIndex(lock) == commit.getObjectIndex(otherLock)) {
                return true;
            }
        }
        return false;
    }

    public void finishAllocatedObjects(LoweringTool tool, FixedWithNextNode insertAfter, CommitAllocationNode commit, ValueNode[] allocations) {
        FixedWithNextNode insertionPoint = insertAfter;
        StructuredGraph graph = commit.graph();
        for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
            PublishWritesNode publish = graph.add(new PublishWritesNode(allocations[objIndex]));
            allocations[objIndex] = publish;
            graph.addAfterFixed(insertionPoint, publish);
            insertionPoint = publish;
        }
        /*
         * Note that the FrameState that is assigned to these MonitorEnterNodes isn't the correct
         * state. The FrameState on the CommitAllocationNode is the nearest previous side effecting
         * FrameState. The objects being materialized correspond to some side effecting state which
         * most likely no longer exists as the virtualization of the objects means operations like
         * storing to the virtual object are no longer side effecting.
         *
         * In Substrate and versions of HotSpot that used stack locking, acquiring these locks
         * doesn't create any global side effects so it was always ok if we deoptimized after
         * acquiring these locks.
         *
         * Starting with the introduction of lightweight locking in HotSpot and some features of
         * Loom, acquiring locks created global side effects that must be cleaned up unlocking of
         * these objects. This means PEA must treat MonitorEnterNodes as having a side effect even
         * after being virtualized to ensure that the lock is released after being acquired..
         * Additionally we must ensure that the MonitorEnterNodes can't deoptimize as it will use
         * the FrameState where the locks are still virtual and the lock acquired by the
         * MonitorEnterNode won't be released.
         */
        ArrayList<MonitorEnterNode> enters = null;
        FrameState stateBefore = GraphUtil.findLastFrameState(insertionPoint);

        List<MonitorIdNode> locks = commit.getLocks();
        if (locks.size() > 1) {
            // Ensure that the lock operations are performed in lock depth order
            ArrayList<MonitorIdNode> newList = new ArrayList<>(locks);
            newList.sort((a, b) -> Integer.compare(a.getLockDepth(), b.getLockDepth()));
            // Eliminate nested locks
            newList.removeIf(lock -> isNestedLock(lock, commit));

            for (MonitorIdNode lock : locks) {
                if (!newList.contains(lock)) {
                    // lock is nested and eliminated
                    for (Node usage : lock.usages().snapshot()) {
                        if (usage.isAlive() && usage instanceof AccessMonitorNode access) {
                            removeMonitorAccess(access);
                        }
                    }
                    lock.setEliminated();
                }
            }
            locks = newList;
        }

        insertionPoint = maybeEmitLockingCheck(locks, insertionPoint, stateBefore);

        int lastDepth = -1;
        for (MonitorIdNode monitorId : locks) {
            GraalError.guarantee(lastDepth < monitorId.getLockDepth(), Assertions.errorMessage(lastDepth, monitorId, insertAfter, commit, allocations));
            GraalError.guarantee(!monitorId.isEliminated(), Assertions.errorMessage(lastDepth, monitorId, insertAfter, commit, allocations));
            lastDepth = monitorId.getLockDepth();
            MonitorEnterNode enter = graph.add(new MonitorEnterNode(allocations[commit.getObjectIndex(monitorId)], monitorId));
            enter.setSynthetic();
            graph.addAfterFixed(insertionPoint, enter);
            enter.setStateAfter(stateBefore.duplicate());
            insertionPoint = enter;
            if (enters == null) {
                enters = new ArrayList<>();
            }
            enters.add(enter);
        }

        for (Node usage : commit.usages().snapshot()) {
            if (usage instanceof AllocatedObjectNode) {
                AllocatedObjectNode addObject = (AllocatedObjectNode) usage;
                int index = commit.getVirtualObjects().indexOf(addObject.getVirtualObject());
                addObject.replaceAtUsagesAndDelete(allocations[index]);
            } else {
                assert enters != null;
                commit.replaceAtUsages(enters.get(enters.size() - 1), InputType.Memory);
            }
        }
        if (enters != null) {
            for (MonitorEnterNode enter : enters) {
                enter.lower(tool);
            }
        }
        assert commit.hasNoUsages();

        // Insert the required ALLOCATION_INIT barrier after all objects are initialized.
        graph.addAfterFixed(insertAfter, graph.add(MembarNode.forInitialization()));
    }

    /**
     * Emit any extra checks before acquired locks on the thread local objects.
     *
     * @param locks the locks to be acquired in order
     * @param insertionPoint the fixed node to insert new nodes after
     * @param stateBefore the state used by the {@link CommitAllocationNode}
     */
    protected FixedWithNextNode maybeEmitLockingCheck(List<MonitorIdNode> locks, FixedWithNextNode insertionPoint, FrameState stateBefore) {
        return insertionPoint;
    }

    public abstract int fieldOffset(ResolvedJavaField field);

    public FieldLocationIdentity overrideFieldLocationIdentity(FieldLocationIdentity fieldIdentity) {
        return fieldIdentity;
    }

    public abstract ValueNode staticFieldBase(StructuredGraph graph, ResolvedJavaField field);

    public abstract int arrayLengthOffset();

    public Stamp loadStamp(Stamp stamp, JavaKind kind) {
        return loadStamp(stamp, kind, true);
    }

    private boolean useCompressedOops(JavaKind kind, boolean compressible) {
        return kind == JavaKind.Object && compressible && useCompressedOops;
    }

    protected abstract Stamp loadCompressedStamp(ObjectStamp stamp);

    /**
     * @param compressible whether the stamp should be compressible
     */
    protected Stamp loadStamp(Stamp stamp, JavaKind kind, boolean compressible) {
        if (useCompressedOops(kind, compressible)) {
            return loadCompressedStamp((ObjectStamp) stamp);
        }

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

    protected abstract ValueNode newCompressionNode(CompressionOp op, ValueNode value);

    /**
     * Perform sign or zero extensions for subword types, and convert potentially unsafe 8 bit
     * boolean values into 0 or 1. The nodes have already been added to the graph.
     */
    public final ValueNode implicitUnsafeLoadConvert(StructuredGraph graph, JavaKind kind, ValueNode value, boolean compressible) {
        if (compressible && kind.isObject()) {
            return implicitLoadConvert(graph, kind, value, compressible);
        } else {
            ValueNode ret = implicitUnsafePrimitiveLoadConvert(kind, value);
            if (!ret.isAlive()) {
                ret = graph.addOrUniqueWithInputs(ret);
            }
            return ret;
        }
    }

    public final ValueNode implicitLoadConvert(StructuredGraph graph, JavaKind kind, ValueNode value) {
        return implicitLoadConvert(graph, kind, value, true);
    }

    /**
     * Perform sign or zero extensions for subword types and add the nodes to the graph.
     */
    protected final ValueNode implicitLoadConvert(StructuredGraph graph, JavaKind kind, ValueNode value, boolean compressible) {
        ValueNode ret;
        if (useCompressedOops(kind, compressible)) {
            ret = newCompressionNode(CompressionOp.Uncompress, value);
        } else {
            ret = implicitPrimitiveLoadConvert(kind, value);
        }

        if (!ret.isAlive()) {
            ret = graph.addOrUniqueWithInputs(ret);
        }
        return ret;
    }

    /**
     * Perform sign or zero extensions for subword types. The caller is expected to add an resulting
     * nodes to the graph.
     */
    public static ValueNode implicitPrimitiveLoadConvert(JavaKind kind, ValueNode value) {
        return switch (kind) {
            case Byte, Short -> new SignExtendNode(value, 32);
            case Boolean, Char -> new ZeroExtendNode(value, 32);
            default -> value;
        };
    }

    /**
     * Perform sign or zero extensions for subword types, and convert potentially unsafe 8 bit
     * boolean values into 0 or 1. The caller is expected to add an resulting * nodes to the graph.
     */
    public static ValueNode implicitUnsafePrimitiveLoadConvert(JavaKind kind, ValueNode value) {
        if (kind == JavaKind.Boolean) {
            return performBooleanCoercion(new ZeroExtendNode(value, 32));
        }
        return implicitPrimitiveLoadConvert(kind, value);
    }

    public ValueNode arrayImplicitStoreConvert(StructuredGraph graph,
                    JavaKind entryKind,
                    ValueNode value,
                    CommitAllocationNode commit,
                    VirtualObjectNode virtual,
                    int valuePos) {
        if (!virtual.isVirtualByteArray(metaAccessExtensionProvider)) {
            return implicitStoreConvert(graph, entryKind, value);
        }
        // A virtual entry in a byte array can span multiple bytes. This shortens the entry to fit
        // in its declared size.
        int entryIndex = valuePos + 1;
        int bytes = 1;
        while (entryIndex < commit.getValues().size() && commit.getValues().get(entryIndex).isIllegalConstant()) {
            bytes++;
            entryIndex++;
        }
        assert bytes <= value.getStackKind().getByteCount() : Assertions.errorMessageContext("bytes", bytes, "valueStackKind", value.getStackKind());
        ValueNode entry = value;
        if (value.getStackKind() == JavaKind.Float) {
            entry = graph.addOrUnique(ReinterpretNode.create(JavaKind.Int, entry, NodeView.DEFAULT));
        } else if (value.getStackKind() == JavaKind.Double) {
            entry = graph.addOrUnique(ReinterpretNode.create(JavaKind.Long, entry, NodeView.DEFAULT));
        }
        if (bytes < value.getStackKind().getByteCount()) {
            entry = graph.unique(new NarrowNode(entry, bytes << 3));
        }
        return entry;
    }

    public final ValueNode implicitStoreConvert(StructuredGraph graph, JavaKind kind, ValueNode value) {
        return implicitStoreConvert(graph, kind, value, true);
    }

    public ValueNode implicitStoreConvert(JavaKind kind, ValueNode value) {
        return implicitStoreConvert(kind, value, true);
    }

    protected final ValueNode implicitStoreConvert(StructuredGraph graph, JavaKind kind, ValueNode value, boolean compressible) {
        ValueNode ret = implicitStoreConvert(kind, value, compressible);
        if (!ret.isAlive()) {
            ret = graph.addOrUnique(ret);
        }
        return ret;
    }

    /**
     * @param compressible whether the covert should be compressible
     */
    protected ValueNode implicitStoreConvert(JavaKind kind, ValueNode value, boolean compressible) {
        if (useCompressedOops(kind, compressible)) {
            return newCompressionNode(CompressionOp.Compress, value);
        }

        return implicitPrimitiveStoreConvert(kind, value);
    }

    public static ValueNode implicitPrimitiveStoreConvert(JavaKind kind, ValueNode value) {
        return switch (kind) {
            case Boolean, Byte -> new NarrowNode(value, 8);
            case Char, Short -> new NarrowNode(value, 16);
            default -> value;
        };
    }

    /**
     * Simulate a primitive store.
     *
     * So for code like:
     *
     * <pre>
     * static class Data {
     *     int value = 0xF0F0F0F0;
     * }
     *
     * Data data = new Data();
     * UNSAFE.putByte(data, FIELD_OFFSET, 0x0F);
     * </pre>
     *
     * The field value of the data object is 0xF0F0F0F0 before the unsafe write operation and
     * 0xF0F0F00F after the unsafe write operation. We are not allowed to touch the upper 3 bytes.
     * To simulate the write operation we extract the appropriate bytes and combine them.
     * <p>
     * Example for a byte operation, currently stored value 0xF0F0F0F0 and value to store
     * 0x0000000F:
     *
     * <pre>
     * lowerBytesMask   = 00000000 00000000 00000000 11111111
     * upperBytesMask   = 11111111 11111111 11111111 00000000
     * currentStored    = 11110000 11110000 11110000 11110000
     * valueToStore     = 00000000 00000000 00000000 00001111
     * newValue         = (currentStored & upperBytesMask) | (valueToStore & lowerBytesMask)
     *                  = 11110000 11110000 11110000 00001111
     * </pre>
     *
     */
    public static ValueNode simulatePrimitiveStore(JavaKind kind, ValueNode currentValue, ValueNode valueToStore) {
        // compute the masks
        int bitCount = kind.getByteCount() * 8;
        int lowerBytesMask = (int) CodeUtil.mask(bitCount);
        int upperBytesMask = ~lowerBytesMask;

        // extract the upper bytes from the current entry
        ValueNode upperBytes = AndNode.create(ConstantNode.forInt(upperBytesMask), currentValue, NodeView.DEFAULT);
        // extract the lower bytes from the value
        ValueNode lowerBytes = AndNode.create(ConstantNode.forInt(lowerBytesMask), valueToStore, NodeView.DEFAULT);
        // combine both
        return OrNode.create(upperBytes, lowerBytes, NodeView.DEFAULT);
    }

    protected abstract ValueNode createReadHub(StructuredGraph graph, ValueNode object, LoweringTool tool, FixedWithNextNode insertAfter);

    protected abstract ValueNode createReadArrayComponentHub(StructuredGraph graph, ValueNode arrayHub, boolean isKnownObjectArray, FixedNode anchor, LoweringTool tool, FixedWithNextNode insertAfter);

    protected ValueNode proxyIndex(AccessIndexedNode n, ValueNode index, ValueNode array, LoweringTool tool) {
        StructuredGraph graph = index.graph();
        ValueNode arrayLength = readOrCreateArrayLength(n, array, tool, graph);
        ValueNode lengthMinusOne = SubNode.create(arrayLength, ConstantNode.forInt(1), NodeView.DEFAULT);
        return branchlessMax(branchlessMin(index, lengthMinusOne, NodeView.DEFAULT), ConstantNode.forInt(0), NodeView.DEFAULT);
    }

    protected GuardingNode getBoundsCheck(AccessIndexedNode n, ValueNode array, LoweringTool tool) {
        if (n.getBoundsCheck() != null) {
            return n.getBoundsCheck();
        }
        StructuredGraph graph = n.graph();
        ValueNode arrayLength = readOrCreateArrayLength(n, array, tool, graph);
        LogicNode boundsCheck = IntegerBelowNode.create(n.index(), arrayLength, NodeView.DEFAULT);
        if (boundsCheck.isTautology()) {
            return null;
        }
        return tool.createGuard(n, graph.addOrUniqueWithInputs(boundsCheck), BoundsCheckException, InvalidateReprofile);
    }

    private ValueNode readOrCreateArrayLength(AccessIndexedNode n, ValueNode array, LoweringTool tool, StructuredGraph graph) {
        ValueNode arrayLength = readArrayLength(array, tool.getConstantReflection());
        if (arrayLength == null) {
            arrayLength = createReadArrayLength(array, n, tool);
        } else {
            arrayLength = arrayLength.isAlive() ? arrayLength : graph.addOrUniqueWithInputs(arrayLength);
        }
        return arrayLength;
    }

    protected GuardingNode createNullCheck(ValueNode object, FixedNode before, LoweringTool tool) {
        if (StampTool.isPointerNonNull(object)) {
            return null;
        }
        return tool.createGuard(before, before.graph().unique(IsNullNode.create(object)), NullCheckException, InvalidateReprofile, SpeculationLog.NO_SPECULATION, true, null);
    }

    protected ValueNode createNullCheckedValue(ValueNode object, FixedNode before, LoweringTool tool) {
        GuardingNode nullCheck = createNullCheck(object, before, tool);
        if (nullCheck == null) {
            return object;
        }
        return before.graph().addOrUnique(PiNode.create(object, StampFactory.objectNonNull(), (ValueNode) nullCheck));
    }

    @Override
    public ValueNode reconstructArrayIndex(JavaKind elementKind, AddressNode address) {
        StructuredGraph graph = address.graph();
        ValueNode offset = ((OffsetAddressNode) address).getOffset();

        int base = metaAccess.getArrayBaseOffset(elementKind);
        ValueNode scaledIndex = graph.unique(new SubNode(offset, ConstantNode.forIntegerStamp(offset.stamp(NodeView.DEFAULT), base, graph)));

        int shift = CodeUtil.log2(metaAccess.getArrayIndexScale(elementKind));
        ValueNode ret = graph.unique(new RightShiftNode(scaledIndex, ConstantNode.forInt(shift, graph)));
        return IntegerConvertNode.convert(ret, StampFactory.forKind(JavaKind.Int), graph, NodeView.DEFAULT);
    }

    @Override
    public boolean supportsOptimizedFilling(OptionValues options) {
        return false;
    }

    @Override
    public VectorArchitecture getVectorArchitecture() {
        return vectorArchitecture;
    }

    @Override
    public DefaultJavaLoweringProvider getBasicLoweringProvider() {
        return this;
    }
}
