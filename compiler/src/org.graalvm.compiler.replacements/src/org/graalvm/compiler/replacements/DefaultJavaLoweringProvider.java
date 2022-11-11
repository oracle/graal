/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements;

import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateReprofile;
import static jdk.vm.ci.meta.DeoptimizationReason.BoundsCheckException;
import static jdk.vm.ci.meta.DeoptimizationReason.NullCheckException;
import static org.graalvm.compiler.core.common.GraalOptions.EmitStringSubstitutions;
import static org.graalvm.compiler.core.common.SpectrePHTMitigations.Options.SpectrePHTIndexMasking;
import static org.graalvm.compiler.nodes.NamedLocationIdentity.ARRAY_LENGTH_LOCATION;
import static org.graalvm.compiler.nodes.calc.BinaryArithmeticNode.branchlessMax;
import static org.graalvm.compiler.nodes.calc.BinaryArithmeticNode.branchlessMin;
import static org.graalvm.compiler.nodes.java.ArrayLengthNode.readArrayLength;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodes.CompressionNode.CompressionOp;
import org.graalvm.compiler.nodes.ComputeObjectAddressNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.GetObjectAddressNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ProfileData.BranchProbabilityData;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.FloatingIntegerDivRemNode;
import org.graalvm.compiler.nodes.calc.IntegerBelowNode;
import org.graalvm.compiler.nodes.calc.IntegerConvertNode;
import org.graalvm.compiler.nodes.calc.IntegerDivRemNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.ReinterpretNode;
import org.graalvm.compiler.nodes.calc.RightShiftNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.SignedDivNode;
import org.graalvm.compiler.nodes.calc.SignedFloatingIntegerDivNode;
import org.graalvm.compiler.nodes.calc.SignedFloatingIntegerRemNode;
import org.graalvm.compiler.nodes.calc.SignedRemNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.calc.UnpackEndianHalfNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.debug.VerifyHeapNode;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.ClassIsArrayNode;
import org.graalvm.compiler.nodes.extended.FixedValueAnchorNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.GuardedUnsafeLoadNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.extended.JavaWriteNode;
import org.graalvm.compiler.nodes.extended.LoadArrayComponentHubNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.extended.LoadHubOrNullNode;
import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.nodes.extended.MembarNode.FenceKind;
import org.graalvm.compiler.nodes.extended.ObjectIsArrayNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.extended.RawStoreNode;
import org.graalvm.compiler.nodes.extended.UnboxNode;
import org.graalvm.compiler.nodes.extended.UnsafeMemoryLoadNode;
import org.graalvm.compiler.nodes.extended.UnsafeMemoryStoreNode;
import org.graalvm.compiler.nodes.gc.BarrierSet;
import org.graalvm.compiler.nodes.java.AbstractNewObjectNode;
import org.graalvm.compiler.nodes.java.AccessIndexedNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.AtomicReadAndAddNode;
import org.graalvm.compiler.nodes.java.AtomicReadAndWriteNode;
import org.graalvm.compiler.nodes.java.InstanceOfDynamicNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.LogicCompareAndSwapNode;
import org.graalvm.compiler.nodes.java.LoweredAtomicReadAndAddNode;
import org.graalvm.compiler.nodes.java.LoweredAtomicReadAndWriteNode;
import org.graalvm.compiler.nodes.java.MonitorEnterNode;
import org.graalvm.compiler.nodes.java.MonitorIdNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.RegisterFinalizerNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.java.UnsafeCompareAndExchangeNode;
import org.graalvm.compiler.nodes.java.UnsafeCompareAndSwapNode;
import org.graalvm.compiler.nodes.java.ValueCompareAndSwapNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.SideEffectFreeWriteNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.IndexAddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.PlatformConfigurationProvider;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.nodes.virtual.AllocatedObjectNode;
import org.graalvm.compiler.nodes.virtual.CommitAllocationNode;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;
import org.graalvm.compiler.nodes.virtual.VirtualInstanceNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.IdentityHashCodeNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import org.graalvm.word.LocationIdentity;

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
public abstract class DefaultJavaLoweringProvider implements LoweringProvider {

    protected final MetaAccessProvider metaAccess;
    protected final ForeignCallsProvider foreignCalls;
    protected final BarrierSet barrierSet;
    protected final MetaAccessExtensionProvider metaAccessExtensionProvider;
    protected final TargetDescription target;
    private final boolean useCompressedOops;
    protected Replacements replacements;

    private BoxingSnippets.Templates boxingSnippets;
    protected IdentityHashCodeSnippets.Templates identityHashCodeSnippets;
    protected IsArraySnippets.Templates isArraySnippets;
    protected StringLatin1Snippets.Templates latin1Templates;
    protected StringUTF16Snippets.Templates utf16templates;

    public DefaultJavaLoweringProvider(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, PlatformConfigurationProvider platformConfig,
                    MetaAccessExtensionProvider metaAccessExtensionProvider,
                    TargetDescription target, boolean useCompressedOops) {
        this.metaAccess = metaAccess;
        this.foreignCalls = foreignCalls;
        this.barrierSet = platformConfig.getBarrierSet();
        this.metaAccessExtensionProvider = metaAccessExtensionProvider;
        this.target = target;
        this.useCompressedOops = useCompressedOops;
    }

    public void initialize(OptionValues options, SnippetCounter.Group.Factory factory, Providers providers) {
        replacements = providers.getReplacements();
        boxingSnippets = new BoxingSnippets.Templates(options, factory, providers);
        if (EmitStringSubstitutions.getValue(options)) {
            latin1Templates = new StringLatin1Snippets.Templates(options, providers);
            providers.getReplacements().registerSnippetTemplateCache(latin1Templates);
            utf16templates = new StringUTF16Snippets.Templates(options, providers);
            providers.getReplacements().registerSnippetTemplateCache(utf16templates);
        }
        providers.getReplacements().registerSnippetTemplateCache(new SnippetCounterNode.SnippetCounterSnippets.Templates(options, providers));
        providers.getReplacements().registerSnippetTemplateCache(new BigIntegerSnippets.Templates(options, providers));
    }

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
        assert n instanceof Lowerable;
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
                lowerLoadArrayComponentHubNode((LoadArrayComponentHubNode) n);
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
                throw GraalError.shouldNotReachHere("Node implementing Lowerable not handled: " + n);
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
            throw GraalError.shouldNotReachHere("divRem is null or has unexpected type: " + divRem);
        }
        divRemFixed.setCanDeopt(false);
        divRem.replaceAtUsagesAndDelete(divRemFixed);
        graph.addAfterFixed(insertAfter, divRemFixed);
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
                throw GraalError.shouldNotReachHere("Unexpected floating use of ComputeObjectAddressNode " + n);
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
            if (method.getName().equalsIgnoreCase(math.getOperation().name()) && tool.getMetaAccess().lookupJavaType(Math.class).equals(method.getDeclaringClass())) {
                // A root compilation of the intrinsic method should emit the full assembly
                // implementation.
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
            if (method.getName().equalsIgnoreCase(math.getOperation().name()) && tool.getMetaAccess().lookupJavaType(Math.class).equals(method.getDeclaringClass())) {
                // A root compilation of the intrinsic method should emit the full assembly
                // implementation.
                return;
            }
        }
        StructuredGraph graph = math.graph();
        ForeignCallNode call = math.graph().add(new ForeignCallNode(foreignCalls.getDescriptor(math.getOperation().foreignCallSignature), math.getValue()));
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
            return null;
        }
    }

    public final JavaKind getStorageKind(ResolvedJavaField field) {
        return getStorageKind(field.getType());
    }

    public final JavaKind getStorageKind(JavaType type) {
        return metaAccessExtensionProvider.getStorageKind(type);
    }

    protected void lowerLoadFieldNode(LoadFieldNode loadField, LoweringTool tool) {
        assert loadField.getStackKind() != JavaKind.Illegal;
        StructuredGraph graph = loadField.graph();
        ResolvedJavaField field = loadField.field();
        ValueNode object = loadField.isStatic() ? staticFieldBase(graph, field) : loadField.object();
        object = createNullCheckedValue(object, loadField, tool);
        Stamp loadStamp = loadStamp(loadField.stamp(NodeView.DEFAULT), getStorageKind(field));

        AddressNode address = createFieldAddress(graph, object, field);
        assert address != null : "Field that is loaded must not be eliminated: " + field.getDeclaringClass().toJavaName(true) + "." + field.getName();

        BarrierType barrierType = barrierSet.fieldLoadBarrierType(field, getStorageKind(field));
        ReadNode memoryRead = graph.add(new ReadNode(address, overrideFieldLocationIdentity(loadField.getLocationIdentity()), loadStamp, barrierType, loadField.getMemoryOrder()));
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
        assert address != null;

        BarrierType barrierType = barrierSet.fieldStoreBarrierType(field, getStorageKind(field));
        WriteNode memoryWrite = graph.add(new WriteNode(address, overrideFieldLocationIdentity(storeField.getLocationIdentity()), value, barrierType, storeField.getMemoryOrder()));
        memoryWrite.setStateAfter(storeField.stateAfter());
        graph.replaceFixedWithFixed(storeField, memoryWrite);
    }

    public static final IntegerStamp POSITIVE_ARRAY_INDEX_STAMP = StampFactory.forInteger(32, 0, Integer.MAX_VALUE - 1);

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

        ReadNode memoryRead = graph.add(new ReadNode(address, NamedLocationIdentity.getArrayLocation(elementKind), loadStamp, BarrierType.NONE, MemoryOrderMode.PLAIN));
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
                ValueNode arrayClass = createReadHub(graph, array, tool);
                boolean isKnownObjectArray = arrayType != null && !arrayType.getType().getComponentType().isPrimitive();
                ValueNode componentHub = createReadArrayComponentHub(graph, arrayClass, isKnownObjectArray, storeIndexed);
                LogicNode typeTest = graph.unique(InstanceOfDynamicNode.create(graph.getAssumptions(), tool.getConstantReflection(), componentHub, value, false));
                condition = LogicNode.or(graph.unique(IsNullNode.create(value)), typeTest, BranchProbabilityNode.NOT_LIKELY_PROFILE);
            }
            if (condition != null && condition.isTautology()) {
                // Skip unnecessary guards
                condition = null;
            }
        }
        BarrierType barrierType = barrierSet.arrayStoreBarrierType(storageKind);
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
        arrayLengthNode.replaceAtUsages(createReadArrayLength(arrayLengthNode.array(), arrayLengthNode, tool));
        StructuredGraph graph = arrayLengthNode.graph();
        graph.removeFixed(arrayLengthNode);
    }

    /**
     * Creates a read node that read the array length and is guarded by a null-check.
     *
     * The created node is placed before {@code before} in the CFG.
     */
    protected ReadNode createReadArrayLength(ValueNode array, FixedNode before, LoweringTool tool) {
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
        ValueNode hub = createReadHub(graph, loadHub.getValue(), tool);
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
        ValueNode hub = createReadHub(graph, nonNullValue, tool);
        ValueNode[] values = new ValueNode[]{nullHub, hub};
        final PhiNode hubPhi = graph.unique(new ValuePhiNode(hubStamp, merge, values));
        final FixedNode oldNext = predecessor.next();
        predecessor.setNext(ifNode);
        merge.setNext(oldNext);
        loadHubOrNullNode.replaceAtUsagesAndDelete(hubPhi);
    }

    protected void lowerLoadArrayComponentHubNode(LoadArrayComponentHubNode loadHub) {
        StructuredGraph graph = loadHub.graph();
        ValueNode hub = createReadArrayComponentHub(graph, loadHub.getValue(), false, loadHub);
        graph.replaceFixed(loadHub, hub);
    }

    protected void lowerCompareAndSwapNode(UnsafeCompareAndSwapNode cas) {
        StructuredGraph graph = cas.graph();
        JavaKind valueKind = cas.getValueKind();

        ValueNode expectedValue = implicitStoreConvert(graph, valueKind, cas.expected());
        ValueNode newValue = implicitStoreConvert(graph, valueKind, cas.newValue());

        AddressNode address = graph.unique(new OffsetAddressNode(cas.object(), cas.offset()));
        BarrierType barrierType = barrierSet.guessStoreBarrierType(cas.object(), newValue);
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
        BarrierType barrierType = barrierSet.guessStoreBarrierType(cas.object(), newValue);
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
        BarrierType barrierType = barrierSet.guessStoreBarrierType(n.object(), newValue);
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
        BarrierType barrierType = barrierSet.guessStoreBarrierType(n.object(), delta);
        LoweredAtomicReadAndAddNode memoryRead = graph.add(new LoweredAtomicReadAndAddNode(address, n.getKilledLocationIdentity(), delta, barrierType));
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
        ReadNode memoryRead = graph.add(new ReadNode(address, load.getLocationIdentity(), loadStamp, barrierSet.readBarrierType(load), load.getMemoryOrder()));
        if (guard == null) {
            // An unsafe read must not float otherwise it may float above
            // a test guaranteeing the read is safe.
            memoryRead.setForceFixed(true);
        } else {
            memoryRead.setGuard(guard);
        }
        ValueNode readValue = performBooleanCoercionIfNecessary(implicitLoadConvert(graph, readKind, memoryRead, compressible), readKind);
        load.replaceAtUsages(readValue);
        return memoryRead;
    }

    protected void lowerUnsafeMemoryLoadNode(UnsafeMemoryLoadNode load) {
        StructuredGraph graph = load.graph();
        JavaKind readKind = load.getKind();
        Stamp loadStamp = loadStamp(load.stamp(NodeView.DEFAULT), readKind, false);
        AddressNode address = graph.addOrUniqueWithInputs(OffsetAddressNode.create(load.getAddress()));
        ReadNode memoryRead = graph.add(new ReadNode(address, load.getLocationIdentity(), loadStamp, BarrierType.NONE, MemoryOrderMode.PLAIN));
        // An unsafe read must not float otherwise it may float above
        // a test guaranteeing the read is safe.
        memoryRead.setForceFixed(true);
        ValueNode readValue = performBooleanCoercionIfNecessary(implicitLoadConvert(graph, readKind, memoryRead, false), readKind);
        load.replaceAtUsages(readValue);
        graph.replaceFixedWithFixed(load, memoryRead);
    }

    private static ValueNode performBooleanCoercionIfNecessary(ValueNode readValue, JavaKind readKind) {
        if (readKind == JavaKind.Boolean) {
            StructuredGraph graph = readValue.graph();
            IntegerEqualsNode eq = graph.addOrUnique(new IntegerEqualsNode(readValue, ConstantNode.forInt(0, graph)));
            return graph.addOrUnique(new ConditionalNode(eq, ConstantNode.forBoolean(false, graph), ConstantNode.forBoolean(true, graph)));
        }
        return readValue;
    }

    protected void lowerUnsafeStoreNode(RawStoreNode store) {
        StructuredGraph graph = store.graph();
        boolean compressible = store.value().getStackKind() == JavaKind.Object;
        JavaKind valueKind = store.accessKind();
        ValueNode value = implicitStoreConvert(graph, valueKind, store.value(), compressible);
        AddressNode address = createUnsafeAddress(graph, store.object(), store.offset());
        WriteNode write = graph.add(new WriteNode(address, store.getLocationIdentity(), value, barrierSet.storeBarrierType(store), store.getMemoryOrder()));
        write.setStateAfter(store.stateAfter());
        graph.replaceFixedWithFixed(store, write);
    }

    protected void lowerUnsafeMemoryStoreNode(UnsafeMemoryStoreNode store) {
        StructuredGraph graph = store.graph();
        assert store.getValue().getStackKind() != JavaKind.Object;
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
        if (graph.getGuardsStage() == GraphState.GuardsStage.FIXED_DEOPTS) {
            List<AbstractNewObjectNode> recursiveLowerings = new ArrayList<>();

            ValueNode[] allocations = new ValueNode[commit.getVirtualObjects().size()];
            BitSet omittedValues = new BitSet();
            int valuePos = 0;
            for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
                VirtualObjectNode virtual = commit.getVirtualObjects().get(objIndex);
                try (DebugCloseable nsp = graph.withNodeSourcePosition(virtual)) {
                    int entryCount = virtual.entryCount();
                    AbstractNewObjectNode newObject;
                    if (virtual instanceof VirtualInstanceNode) {
                        newObject = graph.add(new NewInstanceNode(virtual.type(), true));
                    } else {
                        assert virtual instanceof VirtualArrayNode;
                        newObject = graph.add(new NewArrayNode(((VirtualArrayNode) virtual).componentType(), ConstantNode.forInt(entryCount, graph), true));
                    }
                    // The final STORE_STORE barrier will be emitted by finishAllocatedObjects
                    newObject.clearEmitMemoryBarrier();

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
                            JavaKind valueKind = value.getStackKind();
                            JavaKind storageKind = virtual.entryKind(tool.getMetaAccessExtensionProvider(), i);

                            // Truffle requires some leniency in terms of what can be put where:
                            assert valueKind.getStackKind() == storageKind.getStackKind() ||
                                            (valueKind == JavaKind.Long || valueKind == JavaKind.Double || (valueKind == JavaKind.Int && virtual instanceof VirtualArrayNode) ||
                                                            (valueKind == JavaKind.Float && virtual instanceof VirtualArrayNode));
                            AddressNode address = null;
                            BarrierType barrierType = null;
                            if (virtual instanceof VirtualInstanceNode) {
                                ResolvedJavaField field = ((VirtualInstanceNode) virtual).field(i);
                                long offset = fieldOffset(field);
                                if (offset >= 0) {
                                    address = createOffsetAddress(graph, newObject, offset);
                                    barrierType = barrierSet.fieldStoreBarrierType(field, getStorageKind(field));
                                }
                            } else {
                                assert virtual instanceof VirtualArrayNode;
                                address = createOffsetAddress(graph, newObject, metaAccess.getArrayBaseOffset(storageKind) + i * metaAccess.getArrayIndexScale(storageKind));
                                barrierType = barrierSet.arrayStoreBarrierType(storageKind);
                            }
                            if (address != null) {
                                WriteNode write = new WriteNode(address, LocationIdentity.init(), arrayImplicitStoreConvert(graph, storageKind, value, commit, virtual, valuePos), barrierType,
                                                MemoryOrderMode.PLAIN);
                                graph.addAfterFixed(newObject, graph.add(write));
                            }
                        }
                        valuePos++;
                    }
                }
            }
            valuePos = 0;

            for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
                VirtualObjectNode virtual = commit.getVirtualObjects().get(objIndex);
                try (DebugCloseable nsp = graph.withNodeSourcePosition(virtual)) {
                    int entryCount = virtual.entryCount();
                    ValueNode newObject = allocations[objIndex];
                    for (int i = 0; i < entryCount; i++) {
                        if (omittedValues.get(valuePos)) {
                            ValueNode value = commit.getValues().get(valuePos);
                            assert value instanceof VirtualObjectNode;
                            ValueNode allocValue = allocations[commit.getVirtualObjects().indexOf(value)];
                            if (!(allocValue.isConstant() && allocValue.asConstant().isDefaultForKind())) {
                                assert virtual.entryKind(metaAccessExtensionProvider, i) == JavaKind.Object && allocValue.getStackKind() == JavaKind.Object;
                                AddressNode address;
                                BarrierType barrierType;
                                if (virtual instanceof VirtualInstanceNode) {
                                    VirtualInstanceNode virtualInstance = (VirtualInstanceNode) virtual;
                                    ResolvedJavaField field = virtualInstance.field(i);
                                    address = createFieldAddress(graph, newObject, field);
                                    barrierType = barrierSet.fieldStoreBarrierType(field, getStorageKind(field));
                                } else {
                                    assert virtual instanceof VirtualArrayNode;
                                    address = createArrayAddress(graph, newObject, virtual.entryKind(metaAccessExtensionProvider, i), ConstantNode.forInt(i, graph));
                                    barrierType = barrierSet.arrayStoreBarrierType(virtual.entryKind(metaAccessExtensionProvider, i));
                                }
                                if (address != null) {
                                    WriteNode write = new WriteNode(address, LocationIdentity.init(), implicitStoreConvert(graph, JavaKind.Object, allocValue), barrierType, MemoryOrderMode.PLAIN);
                                    graph.addBeforeFixed(commit, graph.add(write));
                                }
                            }
                        }
                        valuePos++;
                    }
                }
            }

            finishAllocatedObjects(tool, commit, commit, allocations);
            graph.removeFixed(commit);

            for (AbstractNewObjectNode recursiveLowering : recursiveLowerings) {
                recursiveLowering.lower(tool);
            }
        }

    }

    public void finishAllocatedObjects(LoweringTool tool, FixedWithNextNode insertAfter, CommitAllocationNode commit, ValueNode[] allocations) {
        FixedWithNextNode insertionPoint = insertAfter;
        StructuredGraph graph = commit.graph();
        for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
            FixedValueAnchorNode anchor = graph.add(new FixedValueAnchorNode(allocations[objIndex]));
            allocations[objIndex] = anchor;
            graph.addAfterFixed(insertionPoint, anchor);
            insertionPoint = anchor;
        }
        /*
         * Note that the FrameState that is assigned to these MonitorEnterNodes isn't the correct
         * state. It will be the state from before the allocation occurred instead of a valid state
         * after the locking is performed. In practice this should be fine since these are newly
         * allocated objects. The bytecodes themselves permit allocating an object, doing a
         * monitorenter and then dropping all references to the object which would produce the same
         * state, though that would normally produce an IllegalMonitorStateException. In HotSpot
         * some form of fast path locking should always occur so the FrameState should never
         * actually be used.
         */
        ArrayList<MonitorEnterNode> enters = null;
        FrameState stateBefore = GraphUtil.findLastFrameState(insertionPoint);
        for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
            List<MonitorIdNode> locks = commit.getLocks(objIndex);
            if (locks.size() > 1) {
                // Ensure that the lock operations are performed in lock depth order
                ArrayList<MonitorIdNode> newList = new ArrayList<>(locks);
                newList.sort((a, b) -> Integer.compare(a.getLockDepth(), b.getLockDepth()));
                locks = newList;
            }
            int lastDepth = -1;
            for (MonitorIdNode monitorId : locks) {
                assert lastDepth < monitorId.getLockDepth();
                lastDepth = monitorId.getLockDepth();
                MonitorEnterNode enter = graph.add(new MonitorEnterNode(allocations[objIndex], monitorId));
                graph.addAfterFixed(insertionPoint, enter);
                enter.setStateAfter(stateBefore.duplicate());
                insertionPoint = enter;
                if (enters == null) {
                    enters = new ArrayList<>();
                }
                enters.add(enter);
            }
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
        insertAllocationBarrier(insertAfter, commit, graph);
    }

    /**
     * Insert the required {@link FenceKind#ALLOCATION_INIT} barrier for an allocation.
     * Alternatively, issue a {@link FenceKind#CONSTRUCTOR_FREEZE} required for final fields if any
     * final fields are being written.
     */
    private static void insertAllocationBarrier(FixedWithNextNode insertAfter, CommitAllocationNode commit, StructuredGraph graph) {
        FenceKind fence = FenceKind.ALLOCATION_INIT;
        outer: for (VirtualObjectNode vobj : commit.getVirtualObjects()) {
            for (ResolvedJavaField field : vobj.type().getInstanceFields(true)) {
                if (field.isFinal()) {
                    fence = FenceKind.CONSTRUCTOR_FREEZE;
                    break outer;
                }
            }
        }
        graph.addAfterFixed(insertAfter, graph.add(new MembarNode(fence, LocationIdentity.init())));
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

    public final ValueNode implicitLoadConvertWithBooleanCoercionIfNecessary(StructuredGraph graph, JavaKind kind, ValueNode value) {
        return performBooleanCoercionIfNecessary(implicitLoadConvert(graph, kind, value), kind);
    }

    public final ValueNode implicitLoadConvert(StructuredGraph graph, JavaKind kind, ValueNode value) {
        return implicitLoadConvert(graph, kind, value, true);
    }

    public ValueNode implicitLoadConvert(JavaKind kind, ValueNode value) {
        return implicitLoadConvert(kind, value, true);
    }

    protected final ValueNode implicitLoadConvert(StructuredGraph graph, JavaKind kind, ValueNode value, boolean compressible) {
        ValueNode ret = implicitLoadConvert(kind, value, compressible);
        if (!ret.isAlive()) {
            ret = graph.addOrUnique(ret);
        }
        return ret;
    }

    protected abstract ValueNode newCompressionNode(CompressionOp op, ValueNode value);

    /**
     * @param compressible whether the convert should be compressible
     */
    protected ValueNode implicitLoadConvert(JavaKind kind, ValueNode value, boolean compressible) {
        if (useCompressedOops(kind, compressible)) {
            return newCompressionNode(CompressionOp.Uncompress, value);
        }

        switch (kind) {
            case Byte:
            case Short:
                return new SignExtendNode(value, 32);
            case Boolean:
            case Char:
                return new ZeroExtendNode(value, 32);
        }
        return value;
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
        assert bytes <= value.getStackKind().getByteCount();
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

        switch (kind) {
            case Boolean:
            case Byte:
                return new NarrowNode(value, 8);
            case Char:
            case Short:
                return new NarrowNode(value, 16);
        }
        return value;
    }

    protected abstract ValueNode createReadHub(StructuredGraph graph, ValueNode object, LoweringTool tool);

    protected abstract ValueNode createReadArrayComponentHub(StructuredGraph graph, ValueNode arrayHub, boolean isKnownObjectArray, FixedNode anchor);

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
        return before.graph().addOrUnique(PiNode.create(object, (object.stamp(NodeView.DEFAULT)).join(StampFactory.objectNonNull()), (ValueNode) nullCheck));
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
}
