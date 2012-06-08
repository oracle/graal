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

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.target.amd64.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Performs a tail call to the specified target compiled method, with the parameter taken from the supplied FrameState.
 */
public class TailcallNode extends FixedWithNextNode implements LIRLowerable {

    @Input private final FrameState frameState;
    @Input private final ValueNode target;

    /**
     * Creates a TailcallNode.
     * @param target points to the start of an nmethod
     * @param frameState the parameters will be taken from this FrameState
     */
    public TailcallNode(ValueNode target, FrameState frameState) {
        super(StampFactory.forVoid());
        this.target = target;
        this.frameState = frameState;
    }

    @Override
    public void generate(LIRGeneratorTool generator) {
        LIRGenerator gen = (LIRGenerator) generator;
        HotSpotVMConfig config = HotSpotGraalRuntime.getInstance().getConfig();
        RiResolvedMethod method = frameState.method();
        boolean isStatic = Modifier.isStatic(method.accessFlags());

        Kind[] signature = CiUtil.signatureToKinds(method.signature(), isStatic ? null : method.holder().kind());
        CiCallingConvention cc = gen.frameMap().registerConfig.getCallingConvention(CiCallingConvention.Type.JavaCall, signature, gen.target(), false);
        gen.frameMap().callsMethod(cc, CiCallingConvention.Type.JavaCall); // TODO (aw): I think this is unnecessary for a tail call.
        List<ValueNode> parameters = new ArrayList<>();
        for (int i = 0, slot = 0; i < cc.locations.length; i++, slot += FrameStateBuilder.stackSlots(frameState.localAt(slot).kind())) {
            parameters.add(frameState.localAt(slot));
        }
        List<Value> argList = gen.visitInvokeArguments(cc, parameters);

        Value entry = gen.emitLoad(new CiAddress(Kind.Long, gen.operand(target), config.nmethodEntryOffset), false);

        gen.append(new AMD64TailcallOp(argList, entry, cc.locations));
    }
}
