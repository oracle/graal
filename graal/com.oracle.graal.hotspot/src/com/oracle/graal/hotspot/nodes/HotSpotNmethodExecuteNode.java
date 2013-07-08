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

import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.replacements.nodes.*;

public class HotSpotNmethodExecuteNode extends MacroNode implements Lowerable {

    public HotSpotNmethodExecuteNode(Invoke invoke) {
        super(invoke);
    }

    @Override
    public void lower(LoweringTool tool, LoweringType loweringType) {
        if (loweringType == LoweringType.AFTER_GUARDS) {

            ValueNode hotspotNmethod = arguments.get(3);

            ReadNode readNode = graph().add(new ReadNode(hotspotNmethod, 16, LocationIdentity.ANY_LOCATION, Kind.Long));
            graph().addBeforeFixed(this, readNode);
            readNode.setNullCheck(true);

            int verifiedEntryOffset = HotSpotGraalRuntime.graalRuntime().getConfig().nmethodEntryOffset;
            ReadNode readAddressNode = graph().add(new ReadNode(readNode, verifiedEntryOffset, LocationIdentity.ANY_LOCATION, Kind.Long));
            graph().addBeforeFixed(this, readAddressNode);
            readAddressNode.setNullCheck(true);

            JavaType[] signatureTypes = new JavaType[getTargetMethod().getSignature().getParameterCount(false)];
            for (int i = 0; i < signatureTypes.length; ++i) {
                signatureTypes[i] = getTargetMethod().getSignature().getParameterType(i, getTargetMethod().getDeclaringClass());
            }

            IndirectCallTargetNode callTarget = graph().add(new IndirectCallTargetNode(readAddressNode, arguments, StampFactory.object(), signatureTypes, super.getTargetMethod(), Type.JavaCall));
            InvokeNode invoke = graph().add(new InvokeNode(callTarget, super.getBci()));
            invoke.setStateAfter(stateAfter());
            graph().replaceFixedWithFixed(this, invoke);
        }
    }
}
