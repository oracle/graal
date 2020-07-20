/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.core.common.type.AbstractObjectStamp;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.CompressionNode.CompressionOp;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AndNode;
import org.graalvm.compiler.nodes.calc.UnsignedRightShiftNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.memory.FloatingReadNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.PlatformConfigurationProvider;
import org.graalvm.compiler.nodes.type.NarrowOopStamp;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.DefaultJavaLoweringProvider;
import org.graalvm.compiler.replacements.SnippetCounter.Group;
import org.graalvm.compiler.replacements.nodes.AssertionNode;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.graal.nodes.FloatingWordCastNode;
import com.oracle.svm.core.graal.nodes.SubstrateCompressionNode;
import com.oracle.svm.core.graal.nodes.SubstrateFieldLocationIdentity;
import com.oracle.svm.core.graal.nodes.SubstrateNarrowOopStamp;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.meta.SubstrateObjectConstant;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

public abstract class SubstrateBasicLoweringProvider extends DefaultJavaLoweringProvider implements SubstrateLoweringProvider {

    private final Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings;

    private RuntimeConfiguration runtimeConfig;
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
    }

    @Override
    public void setConfiguration(RuntimeConfiguration runtimeConfig, OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers,
                    SnippetReflectionProvider snippetReflection) {
        this.runtimeConfig = runtimeConfig;
        initialize(options, factories, Group.NullFactory, providers, snippetReflection);
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
        } else {
            super.lower(n, tool);
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
        Object fields = field.getStorageKind() == JavaKind.Object ? StaticFieldsSupport.getStaticObjectFields() : StaticFieldsSupport.getStaticPrimitiveFields();
        return ConstantNode.forConstant(SubstrateObjectConstant.forObject(fields), getProviders().getMetaAccess(), graph);
    }

    private static ValueNode maybeUncompress(ValueNode node) {
        Stamp stamp = node.stamp(NodeView.DEFAULT);
        if (stamp instanceof NarrowOopStamp) {
            return SubstrateCompressionNode.uncompress(node, ((NarrowOopStamp) stamp).getEncoding());
        }
        return node;
    }

    @Override
    protected ValueNode createReadArrayComponentHub(StructuredGraph graph, ValueNode arrayHub, FixedNode anchor) {
        ConstantNode componentHubOffset = ConstantNode.forIntegerKind(target.wordJavaKind, runtimeConfig.getComponentHubOffset(), graph);
        AddressNode componentHubAddress = graph.unique(new OffsetAddressNode(arrayHub, componentHubOffset));
        FloatingReadNode componentHubRef = graph.unique(new FloatingReadNode(componentHubAddress, NamedLocationIdentity.FINAL_LOCATION, null, hubStamp, null, BarrierType.NONE));
        return maybeUncompress(componentHubRef);
    }

    @Override
    protected ValueNode createReadHub(StructuredGraph graph, ValueNode object, LoweringTool tool) {
        if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
            return graph.unique(new LoadHubNode(tool.getStampProvider(), object));
        }

        assert !object.isConstant() || object.asJavaConstant().isNull();

        ObjectLayout objectLayout = getObjectLayout();
        Stamp headerBitsStamp = StampFactory.forUnsignedInteger(8 * objectLayout.getReferenceSize());
        ConstantNode headerOffset = ConstantNode.forIntegerKind(target.wordJavaKind, objectLayout.getHubOffset(), graph);
        AddressNode headerAddress = graph.unique(new OffsetAddressNode(object, headerOffset));
        ValueNode headerBits = graph.unique(new FloatingReadNode(headerAddress, NamedLocationIdentity.FINAL_LOCATION, null, headerBitsStamp, null, BarrierType.NONE));
        ValueNode hubBits;
        int reservedBitsMask = Heap.getHeap().getObjectHeader().getReservedBitsMask();
        if (reservedBitsMask != 0) {
            // get rid of the reserved header bits and extract the actual pointer to the hub
            int encodingShift = ReferenceAccess.singleton().getCompressEncoding().getShift();
            if ((reservedBitsMask >>> encodingShift) == 0) {
                hubBits = graph.unique(new UnsignedRightShiftNode(headerBits, ConstantNode.forInt(encodingShift, graph)));
            } else if (reservedBitsMask < objectLayout.getAlignment()) {
                hubBits = graph.unique(new AndNode(headerBits, ConstantNode.forIntegerStamp(headerBitsStamp, ~reservedBitsMask, graph)));
            } else {
                assert encodingShift > 0 : "shift below results in a compressed value";
                assert CodeUtil.isPowerOf2(reservedBitsMask + 1) : "only the lowest bits may be set";
                int numReservedBits = CodeUtil.log2(reservedBitsMask + 1);
                hubBits = graph.unique(new UnsignedRightShiftNode(headerBits, ConstantNode.forInt(numReservedBits, graph)));
            }
        } else {
            hubBits = headerBits;
        }
        FloatingWordCastNode hubRef = graph.unique(new FloatingWordCastNode(hubStamp, hubBits));
        return maybeUncompress(hubRef);
    }

    @Override
    public FieldLocationIdentity fieldLocationIdentity(ResolvedJavaField field) {
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
