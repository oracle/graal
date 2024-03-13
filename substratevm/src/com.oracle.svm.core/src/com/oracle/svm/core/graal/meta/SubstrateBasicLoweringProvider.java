/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.meta;

import java.util.HashMap;
import java.util.Map;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.nodes.FloatingWordCastNode;
import com.oracle.svm.core.graal.nodes.LoadOpenTypeWorldDispatchTableStartingOffset;
import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;
import com.oracle.svm.core.graal.nodes.SubstrateCompressionNode;
import com.oracle.svm.core.graal.nodes.SubstrateFieldLocationIdentity;
import com.oracle.svm.core.graal.nodes.SubstrateNarrowOopStamp;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.identityhashcode.IdentityHashCodeSupport;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.snippets.SubstrateIsArraySnippets;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.CompressionNode.CompressionOp;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeadEndNode;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.AndNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.UnsignedRightShiftNode;
import jdk.graal.compiler.nodes.extended.LoadHubNode;
import jdk.graal.compiler.nodes.extended.LoadMethodNode;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.spi.PlatformConfigurationProvider;
import jdk.graal.compiler.nodes.type.NarrowOopStamp;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.DefaultJavaLoweringProvider;
import jdk.graal.compiler.replacements.IsArraySnippets;
import jdk.graal.compiler.replacements.SnippetCounter.Group;
import jdk.graal.compiler.replacements.nodes.AssertionNode;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

public abstract class SubstrateBasicLoweringProvider extends DefaultJavaLoweringProvider implements SubstrateLoweringProvider {

    private final Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings;

    private RuntimeConfiguration runtimeConfig;
    private final KnownOffsets knownOffsets;
    private final AbstractObjectStamp hubStamp;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateBasicLoweringProvider(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, PlatformConfigurationProvider platformConfig,
                    MetaAccessExtensionProvider metaAccessExtensionProvider,
                    TargetDescription target) {
        super(metaAccess, foreignCalls, platformConfig, metaAccessExtensionProvider, target, ReferenceAccess.singleton().haveCompressedReferences());
        lowerings = new HashMap<>();

        AbstractObjectStamp hubRefStamp = StampFactory.objectNonNull(TypeReference.createExactTrusted(metaAccess.lookupJavaType(DynamicHub.class)));
        if (ReferenceAccess.singleton().haveCompressedReferences()) {
            hubRefStamp = SubstrateNarrowOopStamp.compressed(hubRefStamp, ReferenceAccess.singleton().getCompressEncoding());
        }
        hubStamp = hubRefStamp;
        knownOffsets = KnownOffsets.singleton();
    }

    @Override
    public void setConfiguration(RuntimeConfiguration runtimeConfig, OptionValues options, Providers providers) {
        this.runtimeConfig = runtimeConfig;
        this.identityHashCodeSnippets = IdentityHashCodeSupport.createSnippetTemplates(options, providers);
        this.isArraySnippets = new IsArraySnippets.Templates(new SubstrateIsArraySnippets(), options, providers);
        initialize(options, Group.NullFactory, providers);
    }

    protected Providers getProviders() {
        return runtimeConfig.getProviders();
    }

    protected ObjectLayout getObjectLayout() {
        return ConfigurationValues.getObjectLayout();
    }

    @Override
    public Map<Class<? extends Node>, NodeLoweringProvider<?>> getLowerings() {
        return lowerings;
    }

    @Override
    public void lower(Node n, LoweringTool tool) {
        if (n instanceof AssertionNode) {
            lowerAssertionNode((AssertionNode) n);
        } else if (n instanceof DeadEndNode) {
            lowerDeadEnd((DeadEndNode) n);
        } else if (n instanceof LoadMethodNode) {
            lowerLoadMethodNode((LoadMethodNode) n, tool);
        } else {
            super.lower(n, tool);
        }
    }

    private void lowerLoadMethodNode(LoadMethodNode loadMethodNode, LoweringTool tool) {
        StructuredGraph graph = loadMethodNode.graph();
        SharedMethod method = (SharedMethod) loadMethodNode.getMethod();
        ValueNode hub = loadMethodNode.getHub();

        if (SubstrateOptions.closedTypeWorld()) {

            int vtableEntryOffset = knownOffsets.getVTableOffset(method.getVTableIndex(), true);
            assert vtableEntryOffset > 0;
            /*
             * Method pointer will always exist in the vtable due to the fact that all reachable
             * methods through method pointer constant references will be compiled.
             */
            AddressNode address = createOffsetAddress(graph, hub, vtableEntryOffset);
            ReadNode virtualMethod = graph.add(new ReadNode(address, SubstrateBackend.getVTableIdentity(), loadMethodNode.stamp(NodeView.DEFAULT), BarrierType.NONE, MemoryOrderMode.PLAIN));
            graph.replaceFixed(loadMethodNode, virtualMethod);

        } else {
            // First compute the dispatch table starting offset
            LoadOpenTypeWorldDispatchTableStartingOffset tableStartingOffset = graph.add(new LoadOpenTypeWorldDispatchTableStartingOffset(hub, method));

            // Add together table starting offset and index offset
            ValueNode methodAddress = graph.unique(
                            new AddNode(tableStartingOffset, ConstantNode.forIntegerKind(ConfigurationValues.getWordKind(), knownOffsets.getVTableOffset(method.getVTableIndex(), false), graph)));

            // The load the method address for the dispatch table
            AddressNode dispatchTableAddress = graph.unique(new OffsetAddressNode(hub, methodAddress));
            ReadNode virtualMethod = graph
                            .add(new ReadNode(dispatchTableAddress, SubstrateBackend.getVTableIdentity(), loadMethodNode.stamp(NodeView.DEFAULT), BarrierType.NONE, MemoryOrderMode.PLAIN));

            // wire in the new nodes
            FixedWithNextNode predecessor = (FixedWithNextNode) loadMethodNode.predecessor();
            predecessor.setNext(tableStartingOffset);
            tableStartingOffset.setNext(virtualMethod);
            graph.replaceFixed(loadMethodNode, virtualMethod);

            // Lower logic associated with loading starting offset
            tableStartingOffset.lower(tool);
        }
    }

    @Override
    public int arrayLengthOffset() {
        return getObjectLayout().getArrayLengthOffset();
    }

    @Override
    public ValueNode staticFieldBase(StructuredGraph graph, ResolvedJavaField f) {
        SharedField field = (SharedField) f;
        assert field.isStatic();
        return graph.unique(StaticFieldsSupport.createStaticFieldBaseNode(field.getStorageKind() != JavaKind.Object));
    }

    private static ValueNode maybeUncompress(ValueNode node) {
        Stamp stamp = node.stamp(NodeView.DEFAULT);
        if (stamp instanceof NarrowOopStamp) {
            return SubstrateCompressionNode.uncompress(node.graph(), node, ((NarrowOopStamp) stamp).getEncoding());
        }
        return node;
    }

    @Override
    protected ValueNode createReadArrayComponentHub(StructuredGraph graph, ValueNode arrayHub, boolean isKnownObjectArray, FixedNode anchor) {
        ConstantNode componentHubOffset = ConstantNode.forIntegerKind(target.wordJavaKind, knownOffsets.getComponentHubOffset(), graph);
        AddressNode componentHubAddress = graph.unique(new OffsetAddressNode(arrayHub, componentHubOffset));
        FloatingReadNode componentHubRef = graph.unique(new FloatingReadNode(componentHubAddress, NamedLocationIdentity.FINAL_LOCATION, null, hubStamp, null, BarrierType.NONE));
        return maybeUncompress(componentHubRef);
    }

    @Override
    protected ValueNode createReadHub(StructuredGraph graph, ValueNode object, LoweringTool tool) {
        if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
            return graph.unique(new LoadHubNode(tool.getStampProvider(), object));
        }

        if (object.isConstant() && !object.asJavaConstant().isNull()) {
            /*
             * Special case: insufficient canonicalization was run since the last lowering, if we
             * are loading the hub from a constant we want to still fold it.
             */
            Stamp loadHubStamp = tool.getStampProvider().createHubStamp(((ObjectStamp) object.stamp(NodeView.DEFAULT)));
            ValueNode synonym = LoadHubNode.findSynonym(object, loadHubStamp, tool.getMetaAccess(), tool.getConstantReflection());
            if (synonym != null) {
                return synonym;
            }
        }

        GraalError.guarantee(!object.isConstant() || object.asJavaConstant().isNull(), "Object should either not be a constant or the null constant %s", object);

        ObjectLayout objectLayout = getObjectLayout();
        Stamp headerBitsStamp = StampFactory.forUnsignedInteger(8 * objectLayout.getReferenceSize());
        ConstantNode headerOffset = ConstantNode.forIntegerKind(target.wordJavaKind, objectLayout.getHubOffset(), graph);
        AddressNode headerAddress = graph.unique(new OffsetAddressNode(object, headerOffset));
        ValueNode headerBits = graph.unique(new FloatingReadNode(headerAddress, NamedLocationIdentity.FINAL_LOCATION, null, headerBitsStamp, null, BarrierType.NONE));
        ValueNode hubBits;
        int reservedBitsMask = Heap.getHeap().getObjectHeader().getReservedBitsMask();
        if (reservedBitsMask != 0) {
            // get rid of the reserved header bits and extract the actual pointer to the hub
            assert CodeUtil.isPowerOf2(reservedBitsMask + 1) : "only the lowest bits may be set";
            int numReservedBits = CodeUtil.log2(reservedBitsMask + 1);
            int compressionShift = ReferenceAccess.singleton().getCompressionShift();
            int numAlignmentBits = CodeUtil.log2(objectLayout.getAlignment());
            assert compressionShift <= numAlignmentBits : "compression discards bits";
            if (numReservedBits == numAlignmentBits && compressionShift == 0) {
                hubBits = graph.unique(new AndNode(headerBits, ConstantNode.forIntegerStamp(headerBitsStamp, ~reservedBitsMask, graph)));
            } else {
                hubBits = graph.unique(new UnsignedRightShiftNode(headerBits, ConstantNode.forInt(numReservedBits, graph)));
                if (compressionShift != numAlignmentBits) {
                    int shift = numAlignmentBits - compressionShift;
                    hubBits = graph.unique(new LeftShiftNode(hubBits, ConstantNode.forInt(shift, graph)));
                }
            }
        } else {
            hubBits = headerBits;
        }
        FloatingWordCastNode hubRef = graph.unique(new FloatingWordCastNode(hubStamp, hubBits));
        return maybeUncompress(hubRef);
    }

    @Override
    public FieldLocationIdentity overrideFieldLocationIdentity(FieldLocationIdentity field) {
        return new SubstrateFieldLocationIdentity(field);
    }

    @Override
    public int fieldOffset(ResolvedJavaField f) {
        SharedField field = (SharedField) f;
        return field.isAccessed() ? field.getLocation() : -1;
    }

    private static void lowerAssertionNode(AssertionNode n) {
        // we discard the assertion if it was not handled by any other lowering
        n.graph().removeFixed(n);
    }

    protected void lowerDeadEnd(DeadEndNode deadEnd) {
        deadEnd.replaceAndDelete(deadEnd.graph().add(new LoweredDeadEndNode()));
    }

    @Override
    protected Stamp loadCompressedStamp(ObjectStamp stamp) {
        return SubstrateNarrowOopStamp.compressed(stamp, ReferenceAccess.singleton().getCompressEncoding());
    }

    @Override
    protected ValueNode newCompressionNode(CompressionOp op, ValueNode value) {
        return new SubstrateCompressionNode(op, value, ReferenceAccess.singleton().getCompressEncoding());
    }

    public boolean targetingLLVM() {
        return false;
    }
}
