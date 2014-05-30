/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.amd64;

import static com.oracle.graal.amd64.AMD64.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.hotspot.amd64.AMD64HotSpotLIRGenerator.SaveRbp;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.lir.StandardOp.NoOp;
import com.oracle.graal.lir.gen.*;

public class AMD64HotSpotBytecodeLIRBuilder extends BytecodeLIRBuilder {

    public AMD64HotSpotBytecodeLIRBuilder(LIRGeneratorTool gen, BytecodeParserTool parser) {
        super(gen, parser);
    }

    private AMD64HotSpotLIRGenerator getGen() {
        return (AMD64HotSpotLIRGenerator) gen;
    }

    private SaveRbp getSaveRbp() {
        return getGen().saveRbp;
    }

    private void setSaveRbp(SaveRbp saveRbp) {
        getGen().saveRbp = saveRbp;
    }

    @Override
    public void emitPrologue(ResolvedJavaMethod method) {
        CallingConvention incomingArguments = gen.getCallingConvention();

        Value[] params = new Value[incomingArguments.getArgumentCount() + 1];
        for (int i = 0; i < params.length - 1; i++) {
            params[i] = LIRGenerator.toStackKind(incomingArguments.getArgument(i));
            if (ValueUtil.isStackSlot(params[i])) {
                StackSlot slot = ValueUtil.asStackSlot(params[i]);
                if (slot.isInCallerFrame() && !gen.getResult().getLIR().hasArgInCallerFrame()) {
                    gen.getResult().getLIR().setHasArgInCallerFrame();
                }
            }
        }
        params[params.length - 1] = rbp.asValue(Kind.Long);

        gen.emitIncomingValues(params);

        setSaveRbp(((AMD64HotSpotLIRGenerator) gen).new SaveRbp(new NoOp(gen.getCurrentBlock(), gen.getResult().getLIR().getLIRforBlock(gen.getCurrentBlock()).size())));
        gen.append(getSaveRbp().placeholder);

        Signature sig = method.getSignature();
        boolean isStatic = method.isStatic();
        for (int i = 0; i < sig.getParameterCount(!isStatic); i++) {
            Value paramValue = params[i];
            assert paramValue.getKind() == sig.getParameterKind(i).getStackKind();
            parser.storeLocal(i, gen.emitMove(paramValue));
        }
    }

    @Override
    public int getArrayLengthOffset() {
        return getGen().config.arrayLengthOffset;
    }

    @Override
    public Constant getClassConstant(ResolvedJavaType declaringClass) {
        return HotSpotObjectConstant.forObject(((HotSpotResolvedJavaType) declaringClass).mirror());
    }

    @Override
    public int getFieldOffset(ResolvedJavaField field) {
        return ((HotSpotResolvedJavaField) field).offset();
    }

}
