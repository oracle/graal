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

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Performs a tail call to the specified target compiled method, with the parameter taken from the
 * supplied FrameState.
 */
public class TailcallNode extends FixedWithNextNode implements LIRGenResLowerable {

    @Input(InputType.State) private FrameState frameState;
    @Input private ValueNode target;

    /**
     * Creates a TailcallNode.
     *
     * @param target points to the start of an nmethod
     * @param frameState the parameters will be taken from this FrameState
     */
    public TailcallNode(ValueNode target, FrameState frameState) {
        super(StampFactory.forVoid());
        this.target = target;
        this.frameState = frameState;
    }

    public void generate(NodeLIRBuilderTool gen, LIRGenerationResult res) {
        HotSpotVMConfig config = runtime().getConfig();
        ResolvedJavaMethod method = frameState.method();
        boolean isStatic = Modifier.isStatic(method.getModifiers());

        JavaType[] signature = MetaUtil.signatureToTypes(method.getSignature(), isStatic ? null : method.getDeclaringClass());
        CallingConvention cc = res.getFrameMap().registerConfig.getCallingConvention(CallingConvention.Type.JavaCall, null, signature, gen.getLIRGeneratorTool().target(), false);
        List<ValueNode> parameters = new ArrayList<>();
        for (int i = 0, slot = 0; i < cc.getArgumentCount(); i++, slot += HIRFrameStateBuilder.stackSlots(frameState.localAt(slot).getKind())) {
            parameters.add(frameState.localAt(slot));
        }
        Value[] args = gen.visitInvokeArguments(cc, parameters);
        Value address = gen.getLIRGeneratorTool().emitAddress(gen.operand(target), config.nmethodEntryOffset, Value.ILLEGAL, 0);
        Value entry = gen.getLIRGeneratorTool().emitLoad(Kind.Long, address, null);
        HotSpotLIRGenerator hsgen = (HotSpotLIRGenerator) gen;
        hsgen.emitTailcall(args, entry);
    }
}
