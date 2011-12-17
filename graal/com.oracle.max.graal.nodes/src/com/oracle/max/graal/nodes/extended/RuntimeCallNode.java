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
package com.oracle.max.graal.nodes.extended;

import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;
import com.sun.cri.ci.*;

public final class RuntimeCallNode extends AbstractCallNode implements LIRLowerable {

    @Data private final CiRuntimeCall call;

    public CiRuntimeCall call() {
        return call;
    }

    public RuntimeCallNode(CiRuntimeCall call) {
        this(call, new ValueNode[0]);
    }

    public RuntimeCallNode(CiRuntimeCall call, ValueNode arg1) {
        this(call, new ValueNode[] {arg1});
    }

    public RuntimeCallNode(CiRuntimeCall call, ValueNode[] arguments) {
        super(StampFactory.forKind(call.resultKind), arguments);
        this.call = call;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.emitRuntimeCall(this);
    }

    // specialized on return type (instead of public static <T> T performCall) until boxing/unboxing is sorted out in intrinsification
    @NodeIntrinsic
    public static <S> double performCall(@ConstantNodeParameter CiRuntimeCall call, S arg1) {
        throw new UnsupportedOperationException("This method may only be compiled with the Graal compiler");
    }

    @NodeIntrinsic
    public static long performCall(@ConstantNodeParameter CiRuntimeCall call) {
        throw new UnsupportedOperationException("This method may only be compiled with the Graal compiler");
    }
}
