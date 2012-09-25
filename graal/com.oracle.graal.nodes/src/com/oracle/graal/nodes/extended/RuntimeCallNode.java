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
package com.oracle.graal.nodes.extended;

import com.oracle.graal.api.code.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

@NodeInfo(nameTemplate = "RuntimeCall#{p#call/s}")
public final class RuntimeCallNode extends AbstractCallNode implements LIRLowerable {

    private final RuntimeCall call;

    public RuntimeCall call() {
        return call;
    }

    public RuntimeCallNode(RuntimeCall call) {
        this(call, new ValueNode[0]);
    }

    public RuntimeCallNode(RuntimeCall call, ValueNode... arguments) {
        super(StampFactory.forKind(call.resultKind), arguments);
        this.call = call;
    }

    @Override
    public boolean hasSideEffect() {
        return call.hasSideEffect();
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.emitRuntimeCall(this);
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(verbosity) + "#" + call;
        }
        return super.toString(verbosity);
    }

    // specialized on return type (instead of public static <T> T performCall) until boxing/unboxing is sorted out in intrinsification
    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static <S> double callDouble(@ConstantNodeParameter RuntimeCall call, S arg1) {
        throw new UnsupportedOperationException("This method may only be compiled with the Graal compiler");
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static long callLong(@ConstantNodeParameter RuntimeCall call) {
        throw new UnsupportedOperationException("This method may only be compiled with the Graal compiler");
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void runtimeCall(@ConstantNodeParameter RuntimeCall call) {
        throw new UnsupportedOperationException("This method may only be compiled with the Graal compiler");
    }
}
