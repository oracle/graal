/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodes.PiNode.piCastToSnippetReplaceeStamp;

import java.util.Map;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.ForeignCallWithExceptionNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.compiler.word.BarrieredAccess;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.JavaMemoryUtil;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.heap.InstanceReferenceMapEncoder;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.util.NonmovableByteArrayReader;

public final class SubstrateObjectCloneSnippets extends SubstrateTemplates implements Snippets {
    private static final SubstrateForeignCallDescriptor CLONE = SnippetRuntime.findForeignCall(SubstrateObjectCloneSnippets.class, "doClone", true, LocationIdentity.any());
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
        if (LayoutEncoding.isArray(layoutEncoding)) {
            int length = ArrayLengthNode.arrayLength(original);
            Object newArray = java.lang.reflect.Array.newInstance(DynamicHub.toClass(hub.getComponentHub()), length);
            if (LayoutEncoding.isObjectArray(layoutEncoding)) {
                JavaMemoryUtil.copyObjectArrayForward(original, 0, newArray, 0, length, layoutEncoding);
            } else {
                JavaMemoryUtil.copyPrimitiveArrayForward(original, 0, newArray, 0, length, layoutEncoding);
            }
            return newArray;
        } else {
            sun.misc.Unsafe unsafe = GraalUnsafeAccess.getUnsafe();
            Object result = unsafe.allocateInstance(DynamicHub.toClass(hub));
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
                long count = NonmovableByteArrayReader.getU4(referenceMapEncoding, idx + 4);
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
            int objectSize = NumUtil.safeToInt(LayoutEncoding.getInstanceSize(layoutEncoding).rawValue());
            int primitiveDataSize = objectSize - curOffset;
            assert primitiveDataSize >= 0;
            assert curOffset >= 0;
            JavaMemoryUtil.copyForward(original, WordFactory.unsigned(curOffset), result, WordFactory.unsigned(curOffset), WordFactory.unsigned(primitiveDataSize));
            curOffset += primitiveDataSize;
            assert curOffset == objectSize;

            // reset monitor to uninitialized values
            int monitorOffset = hub.getMonitorOffset();
            if (monitorOffset != 0) {
                BarrieredAccess.writeObject(result, monitorOffset, null);
            }

            return result;
        }
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native Object callClone(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object thisObj);

    @Snippet
    public static Object cloneSnippet(Object thisObj) {
        Object result = callClone(CLONE, thisObj);
        return piCastToSnippetReplaceeStamp(result);
    }

    @SuppressWarnings("unused")
    public static void registerLowerings(OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        new SubstrateObjectCloneSnippets(options, providers, lowerings);
    }

    private SubstrateObjectCloneSnippets(OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, providers);

        ObjectCloneLowering objectCloneLowering = new ObjectCloneLowering();
        lowerings.put(SubstrateObjectCloneNode.class, objectCloneLowering);
        ObjectCloneWithExceptionLowering objectCloneWithExceptionLowering = new ObjectCloneWithExceptionLowering();
        lowerings.put(SubstrateObjectCloneWithExceptionNode.class, objectCloneWithExceptionLowering);
    }

    final class ObjectCloneLowering implements NodeLoweringProvider<SubstrateObjectCloneNode> {
        private final SnippetInfo doClone = snippet(SubstrateObjectCloneSnippets.class, "cloneSnippet");

        @Override
        public void lower(SubstrateObjectCloneNode node, LoweringTool tool) {
            if (node.graph().getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                return;
            }

            Arguments args = new Arguments(doClone, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("thisObj", node.getObject());

            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
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
