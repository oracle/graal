/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasmgc;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.graal.jdk.SubstrateObjectCloneNode;
import com.oracle.svm.core.graal.jdk.SubstrateObjectCloneWithExceptionNode;
import com.oracle.svm.core.graal.nodes.LoadMethodByIndexNode;
import com.oracle.svm.core.graal.nodes.ThrowBytecodeExceptionNode;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.hosted.webimage.WebImageLoweringProvider;
import com.oracle.svm.hosted.webimage.snippets.WebImageIdentityHashCodeSnippets;
import com.oracle.svm.hosted.webimage.wasm.WebImageWasmLMLoweringProvider;
import com.oracle.svm.hosted.webimage.wasmgc.codegen.WebImageWasmGCNodeLowerer;
import com.oracle.svm.hosted.webimage.wasmgc.snippets.WasmGCAllocationSnippets;

import jdk.graal.compiler.core.common.memory.MemoryExtendKind;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.DeadEndNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.IntegerDivRemNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.extended.ClassIsArrayNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.extended.GetClassNode;
import jdk.graal.compiler.nodes.extended.UnsafeAccessNode;
import jdk.graal.compiler.nodes.extended.UnsafeMemoryLoadNode;
import jdk.graal.compiler.nodes.extended.UnsafeMemoryStoreNode;
import jdk.graal.compiler.nodes.java.AbstractUnsafeCompareAndSwapNode;
import jdk.graal.compiler.nodes.java.AtomicReadAndAddNode;
import jdk.graal.compiler.nodes.java.AtomicReadAndWriteNode;
import jdk.graal.compiler.nodes.java.DynamicNewArrayNode;
import jdk.graal.compiler.nodes.java.LoadExceptionObjectNode;
import jdk.graal.compiler.nodes.java.ValidateNewInstanceClassNode;
import jdk.graal.compiler.nodes.memory.ExtendableMemoryAccess;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.spi.PlatformConfigurationProvider;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.IdentityHashCodeSnippets;
import jdk.graal.compiler.replacements.SnippetCounter;
import jdk.graal.compiler.replacements.arraycopy.ArrayCopyNode;
import jdk.graal.compiler.replacements.nodes.IdentityHashCodeNode;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

public class WebImageWasmGCLoweringProvider extends WebImageLoweringProvider {
    public WebImageWasmGCLoweringProvider(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, PlatformConfigurationProvider platformConfig,
                    MetaAccessExtensionProvider metaAccessExtensionProvider, TargetDescription target) {
        super(metaAccess, foreignCalls, platformConfig, metaAccessExtensionProvider, target);
    }

    /**
     * A list of node classes which should be lowered using the parent lowering providers.
     */
    private static final Set<Class<?>> DEFAULT_LOWERABLE_NODES = new HashSet<>(Arrays.asList(ClassIsArrayNode.class,
                    IdentityHashCodeNode.class,
                    AtomicReadAndAddNode.class,
                    AtomicReadAndWriteNode.class,
                    FixedAccessNode.class,
                    UnsafeAccessNode.class,
                    AbstractUnsafeCompareAndSwapNode.class,
                    UnsafeMemoryLoadNode.class,
                    UnsafeMemoryStoreNode.class,
                    DeadEndNode.class));

    /**
     * Set of node classes for which a {@link NodeLoweringProvider} should be used for lowering.
     */
    private static final Set<Class<?>> NODE_LOWERING_NODES = new HashSet<>(Arrays.asList(
                    EnsureClassInitializedNode.class,
                    ValidateNewInstanceClassNode.class,
                    GetClassNode.class,
                    ArrayCopyNode.class,
                    LoadExceptionObjectNode.class,
                    BytecodeExceptionNode.class,
                    ThrowBytecodeExceptionNode.class,
                    SubstrateObjectCloneNode.class,
                    SubstrateObjectCloneWithExceptionNode.class,
                    IntegerDivRemNode.class,
                    DeoptimizeNode.class,
                    LoadMethodByIndexNode.class));

    @Override
    public void initialize(OptionValues options, SnippetCounter.Group.Factory factory, Providers providers) {
        super.initialize(options, factory, providers);
        providers.getReplacements().registerSnippetTemplateCache(new WasmGCAllocationSnippets.Templates(options, providers));
    }

    @Override
    protected IdentityHashCodeSnippets.Templates createIdentityHashCodeSnippets(OptionValues options, Providers providers) {
        return WebImageIdentityHashCodeSnippets.createTemplates(options, providers);
    }

    private static boolean shouldUseNodeLoweringProvider(Node n) {
        for (Class<?> c : NODE_LOWERING_NODES) {
            if (c.isInstance(n)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void lower(Node n, LoweringTool tool) {
        if (shouldUseNodeLoweringProvider(n)) {
            @SuppressWarnings("rawtypes")
            NodeLoweringProvider nodeLoweringProvider = getLowerings().get(n.getClass());

            if (nodeLoweringProvider == null) {
                throw GraalError.unimplemented("No LoweringProvider found for " + n.getClass());
            }

            nodeLoweringProvider.lower(n, tool);
        } else if (n instanceof DynamicNewArrayNode newArray) {
            lowerDynamicNewArray(newArray);
        } else {
            super.lower(n, tool);
        }
    }

    @Override
    public boolean shouldLower(Node n) {
        for (Class<?> c : DEFAULT_LOWERABLE_NODES) {
            if (c.isInstance(n)) {
                return true;
            }
        }
        return false;
    }

    /**
     * If {@link DynamicNewArrayNode#getKnownElementKind()} is an object, no lowering is necessary,
     * we can later generate an allocation directly (see {@link WebImageWasmGCNodeLowerer}) because
     * there is only a single object array type in the WasmGC backend.
     * <p>
     * In all other cases, we generate a foreign call to
     * {@link WasmGCAllocationSupport#dynamicNewArray(DynamicHub, int)}, which checks for all
     * possible element kinds at runtime and instantiates the correct array type.
     *
     * @see com.oracle.svm.hosted.webimage.wasmgc.types.WasmGCUtil
     */
    private static void lowerDynamicNewArray(DynamicNewArrayNode newArray) {
        if (newArray.getKnownElementKind() == JavaKind.Object) {
            /*
             * We can directly generate code for DynamicNewArrayNodes if we know they are object
             * arrays (as opposed to primitive arrays).
             */
            return;
        }

        StructuredGraph graph = newArray.graph();

        ForeignCallNode foreignCall = graph.add(new ForeignCallNode(WasmGCAllocationSupport.DYNAMIC_NEW_ARRAY, newArray.getElementType(), newArray.length()));
        graph.replaceFixedWithFixed(newArray, foreignCall);
    }

    /**
     * @see WebImageWasmLMLoweringProvider#smallestCompareWidth()
     */
    @Override
    public Integer smallestCompareWidth() {
        return 32;
    }

    /**
     * @see WebImageWasmLMLoweringProvider#supportsBulkZeroingOfEden()
     */
    @Override
    public boolean supportsBulkZeroingOfEden() {
        return true;
    }

    /**
     * @see WebImageWasmLMLoweringProvider#writesStronglyOrdered()
     */
    @Override
    public boolean writesStronglyOrdered() {
        return true;
    }

    /**
     * @see WebImageWasmLMLoweringProvider#divisionOverflowIsJVMSCompliant()
     */
    @Override
    public boolean divisionOverflowIsJVMSCompliant() {
        return false;
    }

    /**
     * @see WebImageWasmLMLoweringProvider#narrowsUseCastValue()
     */
    @Override
    public boolean narrowsUseCastValue() {
        return false;
    }

    /**
     * @see WebImageWasmLMLoweringProvider#supportsFoldingExtendIntoAccess(ExtendableMemoryAccess,
     *      MemoryExtendKind) ()
     */
    @Override
    public boolean supportsFoldingExtendIntoAccess(ExtendableMemoryAccess access, MemoryExtendKind extendKind) {
        if (!access.isCompatibleWithExtend(extendKind)) {
            return false;
        }

        return access instanceof ReadNode;
    }
}
