/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.jdk;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;
import static jdk.graal.compiler.nodes.PiNode.piCastToSnippetReplaceeStamp;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.FAST_PATH_PROBABILITY;

import java.util.Map;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.JavaMemoryUtil;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.nodes.ForeignCallWithExceptionNode;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.heap.InstanceReferenceMapEncoder;
import com.oracle.svm.core.heap.Pod;
import com.oracle.svm.core.heap.PodReferenceMapDecoder;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.util.NonmovableByteArrayReader;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;
import jdk.graal.compiler.replacements.nodes.ObjectClone;
import jdk.graal.compiler.word.BarrieredAccess;
import jdk.graal.compiler.word.ObjectAccess;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class SubstrateObjectCloneSnippets extends SubstrateTemplates implements Snippets {
    private static final SubstrateForeignCallDescriptor CLONE = SnippetRuntime.findForeignCall(SubstrateObjectCloneSnippets.class, "doClone", NO_SIDE_EFFECT, LocationIdentity.any());
    private static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{CLONE};

    public static void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(FOREIGN_CALLS);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static Object doClone(Object original) throws CloneNotSupportedException, InstantiationException {
        if (original == null) {
            throw new NullPointerException();
        } else if (!(original instanceof Cloneable)) {
            throw new CloneNotSupportedException("Object is no instance of Cloneable.");
        }

        DynamicHub hub = KnownIntrinsics.readHub(original);
        int layoutEncoding = hub.getLayoutEncoding();
        boolean isArrayLike = LayoutEncoding.isArrayLike(layoutEncoding);

        Object result;
        if (isArrayLike) {
            if (BranchProbabilityNode.probability(FAST_PATH_PROBABILITY, LayoutEncoding.isArray(layoutEncoding))) {
                int length = ArrayLengthNode.arrayLength(original);
                Object newArray = java.lang.reflect.Array.newInstance(DynamicHub.toClass(hub.getComponentHub()), length);
                if (LayoutEncoding.isObjectArray(layoutEncoding)) {
                    JavaMemoryUtil.copyObjectArrayForward(original, 0, newArray, 0, length, layoutEncoding);
                } else {
                    JavaMemoryUtil.copyPrimitiveArrayForward(original, 0, newArray, 0, length, layoutEncoding);
                }
                return newArray;

            } else if (Pod.RuntimeSupport.isPresent() && hub.isPodInstanceClass()) {
                result = PodReferenceMapDecoder.clone(original, hub, layoutEncoding);

            } else {
                throw VMError.shouldNotReachHere("Hybrid classes do not support Object.clone().");
            }
        } else {
            result = Unsafe.getUnsafe().allocateInstance(DynamicHub.toClass(hub));
        }

        int firstFieldOffset = ConfigurationValues.getObjectLayout().getFirstFieldOffset();
        int curOffset = firstFieldOffset;

        NonmovableArray<Byte> referenceMapEncoding = DynamicHubSupport.getReferenceMapEncoding();
        int referenceSize = ConfigurationValues.getObjectLayout().getReferenceSize();
        int referenceMapIndex = hub.getReferenceMapIndex();
        int entryCount = NonmovableByteArrayReader.getS4(referenceMapEncoding, referenceMapIndex);
        assert entryCount >= 0;

        // The UniverseBuilder actively groups object references together. So, this loop will
        // typically be only executed for a very small number of iterations.
        long entryStart = referenceMapIndex + InstanceReferenceMapEncoder.MAP_HEADER_SIZE;
        for (long idx = entryStart; idx < entryStart + entryCount * InstanceReferenceMapEncoder.MAP_ENTRY_SIZE; idx += InstanceReferenceMapEncoder.MAP_ENTRY_SIZE) {
            int objectOffset = NonmovableByteArrayReader.getS4(referenceMapEncoding, idx);
            int count = NumUtil.safeToInt(NonmovableByteArrayReader.getU4(referenceMapEncoding, idx + 4));
            assert objectOffset >= firstFieldOffset : "must not overwrite the object header";

            // copy non-object data
            int primitiveDataSize = objectOffset - curOffset;
            assert primitiveDataSize >= 0;
            assert curOffset >= 0;
            JavaMemoryUtil.copyForward(original, WordFactory.unsigned(curOffset), result, WordFactory.unsigned(curOffset), WordFactory.unsigned(primitiveDataSize));
            curOffset += primitiveDataSize;

            // copy object data
            assert curOffset >= 0;
            assert count >= 0;
            JavaMemoryUtil.copyReferencesForward(original, WordFactory.unsigned(curOffset), result, WordFactory.unsigned(curOffset), WordFactory.unsigned(count));
            curOffset += count * referenceSize;
        }

        // copy remaining non-object data
        int endOffset = isArrayLike ? LayoutEncoding.getArrayBaseOffsetAsInt(layoutEncoding)
                        : UnsignedUtils.safeToInt(LayoutEncoding.getPureInstanceAllocationSize(layoutEncoding));
        int primitiveDataSize = endOffset - curOffset;
        assert primitiveDataSize >= 0;
        assert curOffset >= 0;
        JavaMemoryUtil.copyForward(original, WordFactory.unsigned(curOffset), result, WordFactory.unsigned(curOffset), WordFactory.unsigned(primitiveDataSize));
        curOffset += primitiveDataSize;
        assert curOffset == endOffset;

        // reset monitor to uninitialized values
        int monitorOffset = hub.getMonitorOffset();
        if (monitorOffset != 0) {
            BarrieredAccess.writeObject(result, monitorOffset, null);
        }

        /* Reset identity hashcode if it is potentially outside the object header. */
        if (ConfigurationValues.getObjectLayout().isIdentityHashFieldAtTypeSpecificOffset()) {
            int offset = LayoutEncoding.getIdentityHashOffset(result);
            ObjectAccess.writeInt(result, offset, 0);
        }

        return result;
    }

    static boolean canVirtualize(ObjectClone node, VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(node.getObject());
        if (alias instanceof VirtualObjectNode) {
            return true;
        }
        ResolvedJavaType type = ObjectClone.getConcreteType(alias.stamp(NodeView.DEFAULT));
        if (type instanceof SharedType) {
            // Hybrids are instances with array-like encoding; cloning virtually is unimplemented.
            int encoding = ((SharedType) type).getHub().getLayoutEncoding();
            return !LayoutEncoding.isHybrid(encoding);
        }
        if (type != null && type.isArray()) {
            return true; // cannot be a hybrid
        }
        return false;
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native Object callClone(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object thisObj);

    @Snippet
    public static Object cloneSnippet(Object thisObj) {
        Object result = callClone(CLONE, thisObj);
        return piCastToSnippetReplaceeStamp(result);
    }

    private final SnippetInfo doClone;

    @SuppressWarnings("unused")
    public static void registerLowerings(OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        new SubstrateObjectCloneSnippets(options, providers, lowerings);
    }

    private SubstrateObjectCloneSnippets(OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, providers);

        this.doClone = snippet(providers, SubstrateObjectCloneSnippets.class, "cloneSnippet");

        ObjectCloneLowering objectCloneLowering = new ObjectCloneLowering();
        lowerings.put(SubstrateObjectCloneNode.class, objectCloneLowering);
        ObjectCloneWithExceptionLowering objectCloneWithExceptionLowering = new ObjectCloneWithExceptionLowering();
        lowerings.put(SubstrateObjectCloneWithExceptionNode.class, objectCloneWithExceptionLowering);

    }

    final class ObjectCloneLowering implements NodeLoweringProvider<SubstrateObjectCloneNode> {
        @Override
        public void lower(SubstrateObjectCloneNode node, LoweringTool tool) {
            if (node.graph().getGuardsStage() != GraphState.GuardsStage.AFTER_FSA) {
                return;
            }

            Arguments args = new Arguments(doClone, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("thisObj", node.getObject());

            template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    final class ObjectCloneWithExceptionLowering implements NodeLoweringProvider<SubstrateObjectCloneWithExceptionNode> {
        @Override
        public void lower(SubstrateObjectCloneWithExceptionNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();

            ForeignCallWithExceptionNode call = graph.add(new ForeignCallWithExceptionNode(CLONE, node.getObject()));
            call.setBci(node.bci());
            call.setStamp(node.stamp(NodeView.DEFAULT));
            graph.replaceWithExceptionSplit(node, call);
        }
    }
}
