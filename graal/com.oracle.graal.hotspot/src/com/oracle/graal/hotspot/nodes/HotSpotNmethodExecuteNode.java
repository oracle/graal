/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nodes;

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.replacements.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.common.*;

public class HotSpotNmethodExecuteNode extends AbstractCallNode implements Lowerable {

    @Input private ValueNode code;
    private final Class[] signature;

    public HotSpotNmethodExecuteNode(Kind kind, Class[] signature, ValueNode code, ValueNode arg1, ValueNode arg2, ValueNode arg3) {
        super(StampFactory.forKind(kind), new ValueNode[]{arg1, arg2, arg3});
        this.code = code;
        this.signature = signature;
    }

    @Override
    public LocationIdentity[] getLocationIdentities() {
        return new LocationIdentity[]{LocationIdentity.ANY_LOCATION};
    }

    @Override
    public void lower(LoweringTool tool, LoweringType loweringType) {
        if (code.isConstant() && code.asConstant().asObject() instanceof HotSpotNmethod) {
            HotSpotNmethod nmethod = (HotSpotNmethod) code.asConstant().asObject();
            InvokeNode invoke = replaceWithInvoke(tool.getRuntime());
            StructuredGraph graph = (StructuredGraph) nmethod.getGraph();
            if (graph != null) {
                InliningUtil.inline(invoke, graph, false);
            }
        } else {
            replaceWithInvoke(tool.getRuntime());
        }
    }

    protected InvokeNode replaceWithInvoke(MetaAccessProvider tool) {
        ResolvedJavaMethod method = null;
        ResolvedJavaField methodField = null;
        ResolvedJavaField metaspaceMethodField = null;
        ResolvedJavaField codeBlobField = null;
        try {
            method = tool.lookupJavaMethod(HotSpotNmethodExecuteNode.class.getMethod("placeholder", Object.class, Object.class, Object.class));
            methodField = tool.lookupJavaField(HotSpotNmethod.class.getDeclaredField("method"));
            codeBlobField = tool.lookupJavaField(HotSpotInstalledCode.class.getDeclaredField("codeBlob"));
            metaspaceMethodField = tool.lookupJavaField(HotSpotResolvedJavaMethod.class.getDeclaredField("metaspaceMethod"));
        } catch (NoSuchMethodException | SecurityException | NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
        ResolvedJavaType[] signatureTypes = new ResolvedJavaType[signature.length];
        for (int i = 0; i < signature.length; i++) {
            signatureTypes[i] = tool.lookupJavaType(signature[i]);
        }
        final int verifiedEntryPointOffset = HotSpotReplacementsUtil.verifiedEntryPointOffset();

        LoadFieldNode loadCodeBlob = graph().add(new LoadFieldNode(code, codeBlobField));
        UnsafeLoadNode load = graph().add(new UnsafeLoadNode(loadCodeBlob, verifiedEntryPointOffset, ConstantNode.forLong(0, graph()), graalRuntime().getTarget().wordKind));

        LoadFieldNode loadMethod = graph().add(new LoadFieldNode(code, methodField));
        LoadFieldNode loadmetaspaceMethod = graph().add(new LoadFieldNode(loadMethod, metaspaceMethodField));

        HotSpotIndirectCallTargetNode callTarget = graph().add(
                        new HotSpotIndirectCallTargetNode(loadmetaspaceMethod, load, arguments(), stamp(), signatureTypes, method, CallingConvention.Type.JavaCall));

        InvokeNode invoke = graph().add(new InvokeNode(callTarget, 0));

        invoke.setStateAfter(stateAfter());
        graph().replaceFixedWithFixed(this, invoke);

        graph().addBeforeFixed(invoke, loadmetaspaceMethod);
        graph().addBeforeFixed(loadmetaspaceMethod, loadMethod);
        graph().addBeforeFixed(invoke, load);
        graph().addBeforeFixed(load, loadCodeBlob);

        return invoke;
    }

    public static Object placeholder(@SuppressWarnings("unused") Object a1, @SuppressWarnings("unused") Object a2, @SuppressWarnings("unused") Object a3) {
        return 1;
    }

    @NodeIntrinsic
    public static native <T> T call(@ConstantNodeParameter Kind kind, @ConstantNodeParameter Class[] signature, Object code, Object arg1, Object arg2, Object arg3);

}
