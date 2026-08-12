/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.amd64;

import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.DEOPT_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;

import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.graal.jdk.SubstrateArraycopySnippets.SubstrateGenericArrayCopyCallNode;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateBasicLoweringProvider;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubIntrinsics;
import com.oracle.svm.core.nodes.CodeSynchronizationNode;

import jdk.graal.compiler.core.amd64.AMD64LoweringProviderMixin;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.SnippetAnchorNode;
import jdk.graal.compiler.nodes.UnreachableNode;
import jdk.graal.compiler.nodes.calc.RemNode;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.spi.PlatformConfigurationProvider;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.DefaultJavaLoweringProvider;
import jdk.graal.compiler.replacements.ReplacementsUtil;
import jdk.graal.compiler.replacements.SnippetCounter;
import jdk.graal.compiler.replacements.arraycopy.ArrayCopyNode;
import jdk.graal.compiler.replacements.arraycopy.ArrayCopySnippets;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

public class SubstrateAMD64LoweringProvider extends SubstrateBasicLoweringProvider implements AMD64LoweringProviderMixin, AMD64MemoryMaskingAddressUsagePolicy {
    private ArrayCopySnippets.Templates arraycopySnippets;

    public SubstrateAMD64LoweringProvider(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, PlatformConfigurationProvider platformConfig,
                    MetaAccessExtensionProvider metaAccessExtensionProvider,
                    TargetDescription target, VectorArchitecture vectorArchitecture) {
        super(metaAccess, foreignCalls, platformConfig, metaAccessExtensionProvider, target, vectorArchitecture);
    }

    @Override
    public void setConfiguration(RuntimeConfiguration runtimeConfig, OptionValues options, Providers providers) {
        super.setConfiguration(runtimeConfig, options, providers);
        arraycopySnippets = providers.getReplacements().getSnippetTemplateCache(ArrayCopySnippets.Templates.class);
        if (arraycopySnippets == null) {
            arraycopySnippets = new ArrayCopySnippets.Templates(new SubstrateAMD64ArrayCopySnippets(), SnippetCounter.Group.NullFactory, options, providers);
            providers.getReplacements().registerSnippetTemplateCache(arraycopySnippets);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void lower(Node n, LoweringTool tool) {
        @SuppressWarnings("rawtypes")
        NodeLoweringProvider lowering = getLowerings().get(n.getClass());
        if (lowering != null) {
            lowering.lower(n, tool);
        } else if (n instanceof ArrayCopyNode && mayBeVectorized((ArrayCopyNode) n)) {
            arraycopySnippets.lower((ArrayCopyNode) n, true, tool);
        } else if (n instanceof RemNode) {
            /* No lowering necessary. */
        } else if (n instanceof CodeSynchronizationNode) {
            /* Remove node */
            CodeSynchronizationNode syncNode = (CodeSynchronizationNode) n;
            syncNode.graph().removeFixed(syncNode);
        } else {
            super.lower(n, tool);
        }
    }

    protected boolean mayBeVectorized(ArrayCopyNode arraycopy) {
        if (!DefaultJavaLoweringProvider.mayExpandArraycopyToLoop(arraycopy)) {
            return false;
        }
        JavaKind elementKind = arraycopy.getElementKind();
        if (!arraycopy.isExact() || elementKind == null || !elementKind.isPrimitive()) {
            return false;
        }
        Stamp elementStamp = StampFactory.forKind(elementKind);
        int maxVectorLength = getVectorArchitecture().getMaxVectorLength(elementStamp);
        return getVectorArchitecture().getSupportedVectorMoveLength(elementStamp, maxVectorLength) > 1;
    }

    private static final class SubstrateAMD64ArrayCopySnippets extends ArrayCopySnippets {
        @Override
        protected int heapWordSize() {
            return SubstrateTarget.getWordSize();
        }

        @Override
        public boolean hubsEqual(Object nonNullSrc, Object nonNullDest) {
            DynamicHub fromHub = DynamicHubIntrinsics.readHub(nonNullSrc);
            DynamicHub toHub = DynamicHubIntrinsics.readHub(nonNullDest);
            return fromHub == toHub;
        }

        @Override
        public boolean layoutHelpersEqual(Object nonNullSrc, Object nonNullDest) {
            DynamicHub fromHub = DynamicHubIntrinsics.readHub(nonNullSrc);
            DynamicHub toHub = DynamicHubIntrinsics.readHub(nonNullDest);
            return fromHub.getLayoutEncoding() == toHub.getLayoutEncoding();
        }

        @Override
        protected boolean useOriginalArraycopy() {
            return false;
        }

        @Override
        protected void doCheckcastArraycopySnippet(Object src, int srcPos, Object dest, int destPos, int length, JavaKind elementKind, LocationIdentity arrayLocation, Counters counters) {
            ReplacementsUtil.staticAssert(false, "checkcast Object[] arraycopy not implemented");
        }

        @Override
        protected void doGenericArraycopySnippet(Object src, int srcPos, Object dest, int destPos, int length, JavaKind elementKind, LocationIdentity arrayLocation, Counters counters,
                        boolean exceptionSeen) {
            ReplacementsUtil.staticAssert(false, "generic Object[] arraycopy not implemented");
        }

        @Override
        protected void doFailingArraycopySnippet(Object src, int srcPos, Object dest, int destPos, int length, JavaKind elementKind, Counters counters) {
            // Call the generic array copy which will throw an exception.
            SubstrateGenericArrayCopyCallNode.genericArraycopy(src, srcPos, dest, destPos, length, elementKind);
        }

        @Override
        protected int[] checkTypesAndLimits(Object src, int srcPos, Object dest, int destPos, int length, JavaKind elementKind, ArrayCopyTypeCheck arrayTypeCheck, Counters counters,
                        boolean exceptionSeen) {
            do {
                // using do-while(false) construct to make it PEA friendly
                // check types
                if (arrayTypeCheck != ArrayCopyTypeCheck.NO_ARRAY_TYPE_CHECK) {
                    ReplacementsUtil.staticAssert(false, "unknown array type check ", arrayTypeCheck);
                }

                // check limits
                if (probability(DEOPT_PROBABILITY, srcPos < 0)) {
                    counters.checkAIOOBECounter.inc();
                    break;
                }
                int newSrcPos = PiNode.piCastPositive(srcPos, SnippetAnchorNode.anchor());
                if (probability(DEOPT_PROBABILITY, destPos < 0)) {
                    counters.checkAIOOBECounter.inc();
                    break;
                }
                int newDestPos = PiNode.piCastPositive(destPos, SnippetAnchorNode.anchor());
                if (probability(DEOPT_PROBABILITY, length < 0)) {
                    counters.checkAIOOBECounter.inc();
                    break;
                }
                int newLength = PiNode.piCastPositive(length, SnippetAnchorNode.anchor());
                if (probability(DEOPT_PROBABILITY, newSrcPos > ArrayLengthNode.arrayLength(src) - newLength)) {
                    counters.checkAIOOBECounter.inc();
                    break;
                }
                if (probability(DEOPT_PROBABILITY, newDestPos > ArrayLengthNode.arrayLength(dest) - newLength)) {
                    counters.checkAIOOBECounter.inc();
                    break;
                }
                counters.checkSuccessCounter.inc();
                return createCheckLimitsResult(newSrcPos, newDestPos, newLength);
            } while (false);
            SubstrateGenericArrayCopyCallNode.genericArraycopy(src, srcPos, dest, destPos, length, elementKind);
            throw UnreachableNode.unreachable();
        }
    }
}
