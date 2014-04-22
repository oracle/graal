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
package com.oracle.graal.compiler.gen;

import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.lir.gen.*;

public class BytecodeLIRBuilder {
    protected final LIRGenerator gen;
    protected final BytecodeParserTool parser;

    public BytecodeLIRBuilder(LIRGenerator gen, BytecodeParserTool parser) {
        this.gen = gen;
        this.parser = parser;
    }

    public void emitPrologue(ResolvedJavaMethod method) {
        CallingConvention incomingArguments = gen.getCallingConvention();

        Value[] params = new Value[incomingArguments.getArgumentCount()];
        for (int i = 0; i < params.length; i++) {
            params[i] = LIRGenerator.toStackKind(incomingArguments.getArgument(i));
            if (ValueUtil.isStackSlot(params[i])) {
                StackSlot slot = ValueUtil.asStackSlot(params[i]);
                if (slot.isInCallerFrame() && !gen.getResult().getLIR().hasArgInCallerFrame()) {
                    gen.getResult().getLIR().setHasArgInCallerFrame();
                }
            }
        }

        gen.emitIncomingValues(params);

        Signature sig = method.getSignature();
        boolean isStatic = Modifier.isStatic(method.getModifiers());
        for (int i = 0; i < sig.getParameterCount(!isStatic); i++) {
            Value paramValue = params[i];
            assert paramValue.getKind() == sig.getParameterKind(i).getStackKind();
            parser.storeLocal(i, gen.emitMove(paramValue));
        }

    }

}