/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.replacements.arraycopy;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.*;
import com.oracle.graal.loop.phases.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.replacements.nodes.*;

@NodeInfo
public final class ArrayCopyNode extends BasicArrayCopyNode implements Virtualizable, Lowerable {

    public static final NodeClass<ArrayCopyNode> TYPE = NodeClass.create(ArrayCopyNode.class);

    public ArrayCopyNode(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, int bci, JavaType returnType, ValueNode src, ValueNode srcPos, ValueNode dst, ValueNode dstPos, ValueNode length) {
        super(TYPE, invokeKind, targetMethod, bci, returnType, src, srcPos, dst, dstPos, length);
    }

    private StructuredGraph selectSnippet(LoweringTool tool, final Replacements replacements) {
        ResolvedJavaType srcType = StampTool.typeOrNull(getSource().stamp());
        ResolvedJavaType destType = StampTool.typeOrNull(getDestination().stamp());

        if (srcType == null || !srcType.isArray() || destType == null || !destType.isArray()) {
            return null;
        }
        if (!destType.getComponentType().isAssignableFrom(srcType.getComponentType())) {
            return null;
        }
        if (!isExact()) {
            return null;
        }
        Kind componentKind = srcType.getComponentType().getKind();
        final ResolvedJavaMethod snippetMethod = tool.getMetaAccess().lookupJavaMethod(ArrayCopySnippets.getSnippetForKind(componentKind, shouldUnroll(), isExact()));
        try (Scope s = Debug.scope("ArrayCopySnippet", snippetMethod)) {
            return replacements.getSnippet(snippetMethod, null, null);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    private static void unrollFixedLengthLoop(StructuredGraph snippetGraph, int length, LoweringTool tool) {
        ParameterNode lengthParam = snippetGraph.getParameter(4);
        if (lengthParam != null) {
            snippetGraph.replaceFloating(lengthParam, ConstantNode.forInt(length, snippetGraph));
        }
        // the canonicalization before loop unrolling is needed to propagate the length into
        // additions, etc.
        PhaseContext context = new PhaseContext(tool.getMetaAccess(), tool.getConstantReflection(), tool.getLowerer(), tool.getReplacements(), tool.getStampProvider());
        new CanonicalizerPhase().apply(snippetGraph, context);
        new LoopFullUnrollPhase(new CanonicalizerPhase()).apply(snippetGraph, context);
        new CanonicalizerPhase().apply(snippetGraph, context);
    }

    @Override
    protected StructuredGraph getLoweredSnippetGraph(final LoweringTool tool) {
        final Replacements replacements = tool.getReplacements();
        StructuredGraph snippetGraph = selectSnippet(tool, replacements);
        if (snippetGraph == null) {
            ResolvedJavaType srcType = StampTool.typeOrNull(getSource().stamp());
            ResolvedJavaType destType = StampTool.typeOrNull(getDestination().stamp());
            ResolvedJavaType srcComponentType = srcType == null ? null : srcType.getComponentType();
            ResolvedJavaType destComponentType = destType == null ? null : destType.getComponentType();
            ResolvedJavaMethod snippetMethod = null;
            if (srcComponentType != null && destComponentType != null && !srcComponentType.isPrimitive() && !destComponentType.isPrimitive()) {
                snippetMethod = tool.getMetaAccess().lookupJavaMethod(ArrayCopySnippets.checkcastArraycopySnippet);
            } else {
                snippetMethod = tool.getMetaAccess().lookupJavaMethod(ArrayCopySnippets.genericArraycopySnippet);
            }
            snippetGraph = null;
            try (Scope s = Debug.scope("ArrayCopySnippet", snippetMethod)) {
                snippetGraph = (StructuredGraph) replacements.getSnippet(snippetMethod, getTargetMethod(), null).copy();
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
            replaceSnippetInvokes(snippetGraph);
        } else {
            assert snippetGraph != null : "ArrayCopySnippets should be installed";
            snippetGraph = (StructuredGraph) snippetGraph.copy();
            if (shouldUnroll()) {
                final StructuredGraph copy = snippetGraph;
                try (Scope s = Debug.scope("ArrayCopySnippetSpecialization", snippetGraph.method())) {
                    unrollFixedLengthLoop(copy, getLength().asJavaConstant().asInt(), tool);
                } catch (Throwable e) {
                    throw Debug.handle(e);
                }
            }
        }
        return lowerReplacement(snippetGraph, tool);
    }

    private boolean shouldUnroll() {
        return getLength().isConstant() && getLength().asJavaConstant().asInt() <= GraalOptions.MaximumEscapeAnalysisArrayLength.getValue();
    }

    /*
     * Returns true if this copy doesn't require store checks. Trivially true for primitive arrays.
     */
    private boolean isExact() {
        ResolvedJavaType srcType = StampTool.typeOrNull(getSource().stamp());
        if (srcType.getComponentType().getKind().isPrimitive() || getSource() == getDestination()) {
            return true;
        }

        ResolvedJavaType destType = StampTool.typeOrNull(getDestination().stamp());
        if (StampTool.isExactType(getDestination().stamp())) {
            if (destType != null && destType.isAssignableFrom(srcType)) {
                return true;
            }
        }
        return false;
    }
}
