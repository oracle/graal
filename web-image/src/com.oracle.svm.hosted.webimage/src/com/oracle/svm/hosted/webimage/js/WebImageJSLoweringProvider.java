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

package com.oracle.svm.hosted.webimage.js;

import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.graal.nodes.LoadMethodByIndexNode;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.hosted.webimage.WebImageLoweringProvider;
import com.oracle.svm.hosted.webimage.snippets.WebImageIdentityHashCodeSnippets;

import jdk.graal.compiler.core.common.memory.MemoryExtendKind;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.extended.ClassIsArrayNode;
import jdk.graal.compiler.nodes.java.ValidateNewInstanceClassNode;
import jdk.graal.compiler.nodes.memory.ExtendableMemoryAccess;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.spi.PlatformConfigurationProvider;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.IdentityHashCodeSnippets;
import jdk.graal.compiler.replacements.nodes.IdentityHashCodeNode;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.MetaAccessProvider;

public class WebImageJSLoweringProvider extends WebImageLoweringProvider {
    public WebImageJSLoweringProvider(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, PlatformConfigurationProvider platformConfig,
                    MetaAccessExtensionProvider metaAccessExtensionProvider, TargetDescription target) {
        super(metaAccess, foreignCalls, platformConfig, metaAccessExtensionProvider, target);
    }

    @Override
    protected IdentityHashCodeSnippets.Templates createIdentityHashCodeSnippets(OptionValues options, Providers providers) {
        return WebImageIdentityHashCodeSnippets.createTemplates(options, providers);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void lower(Node n, LoweringTool tool) {
        if (n instanceof EnsureClassInitializedNode || n instanceof ValidateNewInstanceClassNode || n instanceof LoadMethodByIndexNode) {
            @SuppressWarnings("rawtypes")
            NodeLoweringProvider nodeLoweringProvider = getLowerings().get(n.getClass());

            if (nodeLoweringProvider == null) {
                throw GraalError.unimplemented("No LoweringProvider found for " + n.getClass());
            }

            nodeLoweringProvider.lower(n, tool);
        } else {
            super.lower(n, tool);
        }
    }

    @Override
    public boolean shouldLower(Node n) {
        return n instanceof ClassIsArrayNode || n instanceof IdentityHashCodeNode;
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
