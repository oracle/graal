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
package com.oracle.graal.rawnativecall.nodes;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.word.*;

public class HotSpotInstalledCodeExecuteNode extends AbstractCallNode implements Lowerable {

    @Input private final ValueNode targetAddress;
    @Input private final ValueNode metaspaceObject;
    private final Class[] signature;

    public HotSpotInstalledCodeExecuteNode(Kind kind, ValueNode targetAddress, ValueNode metaspaceObject, Class[] signature, ValueNode arg1, ValueNode arg2, ValueNode arg3) {
        super(StampFactory.forKind(kind), new ValueNode[]{arg1, arg2, arg3});
        this.targetAddress = targetAddress;
        this.metaspaceObject = metaspaceObject;
        this.signature = signature;
    }

    @Override
    public Object[] getLocationIdentities() {
        return new Object[]{LocationNode.ANY_LOCATION};
    }

    @Override
    public void lower(LoweringTool tool) {
        replaceWithInvoke(tool);
    }

    private InvokeNode replaceWithInvoke(LoweringTool tool) {
        InvokeNode invoke = createInvoke(tool);
        ((StructuredGraph) graph()).replaceFixedWithFixed(this, invoke);
        return invoke;
    }

    protected InvokeNode createInvoke(LoweringTool tool) {
        ResolvedJavaMethod method = null;
        try {
            method = tool.getRuntime().lookupJavaMethod(HotSpotInstalledCodeExecuteNode.class.getMethod("placeholder", Object.class, Object.class, Object.class));
        } catch (NoSuchMethodException | SecurityException e) {
            throw new IllegalStateException();
        }
        ResolvedJavaType[] signatureTypes = new ResolvedJavaType[signature.length];
        for (int i = 0; i < signature.length; i++) {
            signatureTypes[i] = tool.getRuntime().lookupJavaType(signature[i]);
        }
        HotSpotIndirectCallTargetNode callTarget = graph().add(
                        new HotSpotIndirectCallTargetNode(metaspaceObject, targetAddress, arguments, stamp(), signatureTypes, method, CallingConvention.Type.JavaCall));
        InvokeNode invoke = graph().add(new InvokeNode(callTarget, 0));
        invoke.setStateAfter(stateAfter());
        return invoke;
    }

    public static Object placeholder(@SuppressWarnings("unused") Object a1, @SuppressWarnings("unused") Object a2, @SuppressWarnings("unused") Object a3) {
        return 1;
    }

    @NodeIntrinsic
    public static native <T> T call(@ConstantNodeParameter Kind kind, Word targetAddress, long metaspaceObject, @ConstantNodeParameter Class[] signature, Object arg1, Object arg2, Object arg3);

}
