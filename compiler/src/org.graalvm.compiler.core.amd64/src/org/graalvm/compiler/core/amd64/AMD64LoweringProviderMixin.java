/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.core.amd64;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.replacements.amd64.AMD64ArrayIndexOfDispatchNode;
import org.graalvm.compiler.replacements.amd64.AMD64ArrayIndexOfWithMaskNode;
import org.graalvm.compiler.replacements.amd64.AMD64ArrayRegionEqualsWithMaskNode;
import org.graalvm.compiler.replacements.amd64.AMD64TruffleArrayUtilsWithMaskSnippets;

public interface AMD64LoweringProviderMixin extends LoweringProvider {

    @Override
    default Integer smallestCompareWidth() {
        return 8;
    }

    @Override
    default boolean supportsBulkZeroing() {
        return true;
    }

    /**
     * Performs AMD64-specific lowerings. Returns {@code true} if the given Node {@code n} was
     * lowered, {@code false} otherwise.
     */
    default boolean lowerAMD64(Node n, LoweringTool tool) {
        if (n instanceof AMD64ArrayIndexOfDispatchNode) {
            AMD64ArrayIndexOfDispatchNode dispatchNode = (AMD64ArrayIndexOfDispatchNode) n;
            StructuredGraph graph = dispatchNode.graph();
            ForeignCallNode call = graph.add(new ForeignCallNode(tool.getProviders().getForeignCalls(), dispatchNode.getStubCallDescriptor(), dispatchNode.getStubCallArgs()));
            graph.replaceFixed(dispatchNode, call);
            return true;
        }
        if (n instanceof AMD64ArrayIndexOfWithMaskNode) {
            tool.getReplacements().getSnippetTemplateCache(AMD64TruffleArrayUtilsWithMaskSnippets.Templates.class).lower((AMD64ArrayIndexOfWithMaskNode) n);
            return true;
        }
        if (n instanceof AMD64ArrayRegionEqualsWithMaskNode) {
            tool.getReplacements().getSnippetTemplateCache(AMD64TruffleArrayUtilsWithMaskSnippets.Templates.class).lower((AMD64ArrayRegionEqualsWithMaskNode) n);
            return true;
        }
        return false;
    }
}
