/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
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
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AndNode;
import org.graalvm.compiler.nodes.calc.RemNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.memory.FloatingReadNode;
import org.graalvm.compiler.nodes.memory.HeapAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.DefaultJavaLoweringProvider;
import org.graalvm.compiler.replacements.SnippetCounter.Group;
import org.graalvm.compiler.replacements.nodes.AssertionNode;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.amd64.FrameAccess;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.graal.nodes.FloatingWordCastNode;
import com.oracle.svm.core.graal.nodes.SubstrateCompressionNode;
import com.oracle.svm.core.graal.nodes.SubstrateFieldLocationIdentity;
import com.oracle.svm.core.graal.nodes.SubstrateNarrowOopStamp;
import com.oracle.svm.core.graal.nodes.SubstrateNewArrayNode;
import com.oracle.svm.core.graal.nodes.SubstrateNewInstanceNode;
import com.oracle.svm.core.graal.nodes.SubstrateVirtualArrayNode;
import com.oracle.svm.core.graal.nodes.SubstrateVirtualInstanceNode;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.meta.SubstrateObjectConstant;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

public class SubstrateBasicLoweringProvider extends DefaultJavaLoweringProvider implements SubstrateLoweringProvider {

    private final Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings;

    private RuntimeConfiguration runtimeConfig;
    private final CompressEncoding compressEncoding;
    private final AbstractObjectStamp dynamicHubStamp;

    private static boolean useHeapBaseRegister() {
        return SubstrateOptions.UseHeapBaseRegister.getValue();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateBasicLoweringProvider(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, TargetDescription target) {
        super(metaAccess, foreignCalls, target, useHeapBaseRegister());
        lowerings = new HashMap<>();
        compressEncoding = ImageSingletons.lookup(CompressEncoding.class);

        AbstractObjectStamp hubStamp = StampFactory.objectNonNull(TypeReference.createExactTrusted(metaAccess.lookupJavaType(DynamicHub.class)));
        if (useHeapBaseRegister()) {
            hubStamp = SubstrateNarrowOopStamp.compressed(hubStamp, compressEncoding);
        }
        dynamicHubStamp = hubStamp;
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

    @SuppressWarnings("unchecked")
    @Override
    public void lower(Node n, LoweringTool tool) {
        @SuppressWarnings("rawtypes")
        NodeLoweringProvider lowering = lowerings.get(n.getClass());
        if (lowering != null) {
            lowering.lower(n, tool);
        } else if (n instanceof RemNode) {
            /* No lowering necessary. */
        } else if (n instanceof AssertionNode) {
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

    private static ValueNode createReadBaseWithOffset(StructuredGraph graph, ValueNode base, int offset, Stamp stamp) {
        AddressNode address = graph.unique(new OffsetAddressNode(base, ConstantNode.forIntegerKind(FrameAccess.getWordKind(), offset, graph)));
        return graph.unique(new FloatingReadNode(address, NamedLocationIdentity.FINAL_LOCATION, null, stamp, null, BarrierType.NONE));
    }

    private ValueNode uncompress(ValueNode node) {
        return useHeapBaseRegister() ? SubstrateCompressionNode.uncompress(node, compressEncoding) : node;
    }

    @Override
    protected ValueNode createReadArrayComponentHub(StructuredGraph graph, ValueNode arrayHub, FixedNode anchor) {
        return uncompress(createReadBaseWithOffset(graph, arrayHub, runtimeConfig.getComponentHubOffset(), dynamicHubStamp));
    }

    @Override
    protected ValueNode createReadHub(StructuredGraph graph, ValueNode object, LoweringTool tool) {
        if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
            return graph.unique(new LoadHubNode(tool.getStampProvider(), object));
        }

        assert !object.isConstant() || object.asJavaConstant().isNull();

        Stamp stamp = StampFactory.forUnsignedInteger(8 * getObjectLayout().getReferenceSize());
        ValueNode memoryRead = createReadBaseWithOffset(graph, object, getObjectLayout().getHubOffset(), stamp);
        ValueNode masked = graph.unique(new AndNode(memoryRead, ConstantNode.forIntegerStamp(stamp, ObjectHeader.BITS_CLEAR.rawValue(), graph)));

        return uncompress(graph.unique(new FloatingWordCastNode(dynamicHubStamp, masked)));
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

    @Override
    public int arrayBaseOffset(JavaKind kind) {
        return getObjectLayout().getArrayBaseOffset(kind);
    }

    @Override
    public int arrayScalingFactor(JavaKind elementKind) {
        return getObjectLayout().getArrayIndexScale(elementKind);
    }

    @Override
    public NewInstanceNode createNewInstanceFromVirtual(VirtualObjectNode virtual) {
        if (virtual instanceof SubstrateVirtualInstanceNode) {
            return new SubstrateNewInstanceNode(virtual.type(), true, null);
        }
        return super.createNewInstanceFromVirtual(virtual);
    }

    @Override
    protected NewArrayNode createNewArrayFromVirtual(VirtualObjectNode virtual, ValueNode length) {
        if (virtual instanceof SubstrateVirtualArrayNode) {
            return new SubstrateNewArrayNode(((VirtualArrayNode) virtual).componentType(), length, true, null);
        }
        return super.createNewArrayFromVirtual(virtual, length);
    }

    private static void lowerAssertionNode(AssertionNode n) {
        // we discard the assertion if it was not handled by any other lowering
        n.graph().removeFixed(n);
    }

    @Override
    protected Stamp loadCompressedStamp(ObjectStamp stamp) {
        return SubstrateNarrowOopStamp.compressed(stamp, compressEncoding);
    }

    @Override
    protected ValueNode newCompressionNode(CompressionOp op, ValueNode value) {
        return new SubstrateCompressionNode(op, value, compressEncoding);
    }

    @Override
    protected final JavaKind getStorageKind(ResolvedJavaField field) {
        return ((SharedField) field).getStorageKind();
    }
}
