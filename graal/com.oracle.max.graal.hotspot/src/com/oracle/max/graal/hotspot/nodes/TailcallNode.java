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
package com.oracle.max.graal.hotspot.nodes;

 import static com.sun.cri.ci.CiCallingConvention.Type.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.max.graal.compiler.gen.*;
import com.oracle.max.graal.hotspot.*;
import com.oracle.max.graal.hotspot.target.amd64.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

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
        super(StampFactory.illegal());
        this.target = target;
        this.frameState = frameState;
    }

    @Override
    public void generate(LIRGeneratorTool generator) {
        LIRGenerator gen = (LIRGenerator) generator;
        HotSpotVMConfig config = CompilerImpl.getInstance().getConfig();
        RiResolvedMethod method = frameState.method();
        boolean isStatic = Modifier.isStatic(method.accessFlags());


        CiKind[] signature = CiUtil.signatureToKinds(method.signature(), isStatic ? null : method.holder().kind(true));
        CiCallingConvention cc = gen.compilation.registerConfig.getCallingConvention(JavaCall, signature, gen.compilation.compiler.target, false);
        gen.compilation.frameMap().adjustOutgoingStackSize(cc, JavaCall);
        List<ValueNode> parameters = new ArrayList<ValueNode>();
        for (int i = 0; i < cc.locations.length; i++) {
            parameters.add(frameState.localAt(i));
        }
        List<CiValue> argList = gen.visitInvokeArguments(cc, parameters, null);

        CiVariable entry = gen.emitLoad(new CiAddress(CiKind.Long, gen.operand(target), config.nmethodEntryOffset), CiKind.Long, false);

        gen.append(AMD64TailcallOpcode.TAILCALL.create(argList, entry, cc.locations));
    }
}
