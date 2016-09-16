/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.compiler.common.GraalOptions.ImmutableCode;
import static com.oracle.graal.nodeinfo.NodeCycles.CYCLES_0;
import static com.oracle.graal.nodeinfo.NodeSize.SIZE_0;

import java.lang.reflect.Method;

import com.oracle.graal.compiler.common.LocationIdentity;
import com.oracle.graal.compiler.common.type.StampPair;
import com.oracle.graal.debug.GraalError;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.nodes.spi.Replacements;
import com.oracle.graal.replacements.nodes.PureFunctionMacroNode;

import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public class IdentityHashCodeNode extends PureFunctionMacroNode {

    public static final NodeClass<IdentityHashCodeNode> TYPE = NodeClass.create(IdentityHashCodeNode.class);

    private static final Method HashCodeSnippet;

    static {
        Method snippet = null;
        try {
            snippet = HashCodeSnippets.class.getDeclaredMethod("identityHashCodeSnippet", Object.class);
        } catch (NoSuchMethodException | SecurityException e) {
            GraalError.shouldNotReachHere(e.getMessage());
        }
        HashCodeSnippet = snippet;
    }

    public IdentityHashCodeNode(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, int bci, StampPair returnStamp, ValueNode object) {
        super(TYPE, invokeKind, targetMethod, bci, returnStamp, object);
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return HotSpotReplacementsUtil.MARK_WORD_LOCATION;
    }

    @Override
    protected JavaConstant evaluate(JavaConstant param, MetaAccessProvider metaAccess) {
        if (ImmutableCode.getValue() || param.isNull()) {
            return null;
        }
        HotSpotObjectConstant c = (HotSpotObjectConstant) param;
        return JavaConstant.forInt(c.getIdentityHashCode());
    }

    @Override
    @SuppressWarnings("try")
    protected StructuredGraph getLoweredSnippetGraph(LoweringTool tool) {
        final ResolvedJavaMethod snippetMethod = tool.getMetaAccess().lookupJavaMethod(HashCodeSnippet);
        final Replacements replacements = tool.getReplacements();
        StructuredGraph snippetGraph = null;
        snippetGraph = replacements.getSnippet(snippetMethod, null);
        assert snippetGraph != null : "HashCodeSnippets should be installed";
        return lowerReplacement((StructuredGraph) snippetGraph.copy(), tool);
    }
}
