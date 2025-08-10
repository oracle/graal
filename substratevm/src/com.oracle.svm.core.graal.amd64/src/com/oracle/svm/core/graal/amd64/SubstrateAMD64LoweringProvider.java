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

import com.oracle.svm.core.graal.meta.SubstrateBasicLoweringProvider;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.nodes.CodeSynchronizationNode;

import jdk.graal.compiler.core.amd64.AMD64LoweringProviderMixin;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.calc.RemNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.spi.PlatformConfigurationProvider;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.MetaAccessProvider;

public class SubstrateAMD64LoweringProvider extends SubstrateBasicLoweringProvider implements AMD64LoweringProviderMixin {

    public SubstrateAMD64LoweringProvider(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, PlatformConfigurationProvider platformConfig,
                    MetaAccessExtensionProvider metaAccessExtensionProvider,
                    TargetDescription target, VectorArchitecture vectorArchitecture) {
        super(metaAccess, foreignCalls, platformConfig, metaAccessExtensionProvider, target, vectorArchitecture);
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
        } else if (n instanceof CodeSynchronizationNode) {
            /* Remove node */
            CodeSynchronizationNode syncNode = (CodeSynchronizationNode) n;
            syncNode.graph().removeFixed(syncNode);
        } else {
            super.lower(n, tool);
        }
    }
}
