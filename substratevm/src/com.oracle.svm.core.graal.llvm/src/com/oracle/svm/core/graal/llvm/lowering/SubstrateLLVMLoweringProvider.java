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
package com.oracle.svm.core.graal.llvm.lowering;

import com.oracle.svm.core.graal.meta.SubstrateBasicLoweringProvider;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.nodes.CodeSynchronizationNode;

import jdk.graal.compiler.core.common.memory.MemoryExtendKind;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.calc.FloatConvertNode;
import jdk.graal.compiler.nodes.calc.RemNode;
import jdk.graal.compiler.nodes.memory.ExtendableMemoryAccess;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.spi.PlatformConfigurationProvider;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.MetaAccessProvider;

public class SubstrateLLVMLoweringProvider extends SubstrateBasicLoweringProvider {

    public SubstrateLLVMLoweringProvider(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, PlatformConfigurationProvider platformConfig,
                    MetaAccessExtensionProvider metaAccessExtensionProvider,
                    TargetDescription target) {
        super(metaAccess, foreignCalls, platformConfig, metaAccessExtensionProvider, target, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void lower(Node n, LoweringTool tool) {
        @SuppressWarnings("rawtypes")
        NodeLoweringProvider lowering = getLowerings().get(n.getClass());
        if (lowering != null) {
            lowering.lower(n, tool);
        } else if (n instanceof RemNode) {
            /* No lowering necessary. */
        } else if (n instanceof FloatConvertNode) {
            // AMD64 has custom lowerings for ConvertNodes, SubstrateLoweringProvider does not
            // expect to see a ConvertNode and throws an error, just do nothing here.
        } else if (n instanceof CodeSynchronizationNode) {
            /* Remove node */
            CodeSynchronizationNode syncNode = (CodeSynchronizationNode) n;
            syncNode.graph().removeFixed(syncNode);
        } else {
            super.lower(n, tool);
        }
    }

    @Override
    public Integer smallestCompareWidth() {
        return 32;
    }

    @Override
    public boolean supportsBulkZeroingOfEden() {
        return false;
    }

    @Override
    public boolean writesStronglyOrdered() {
        return false;
    }

    @Override
    public boolean targetingLLVM() {
        return true;
    }

    @Override
    public boolean divisionOverflowIsJVMSCompliant() {
        return false;
    }

    @Override
    public boolean narrowsUseCastValue() {
        return false;
    }

    @Override
    public boolean supportsFoldingExtendIntoAccess(ExtendableMemoryAccess access, MemoryExtendKind extendKind) {
        return false;
    }

}
