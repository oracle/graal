/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.RuntimeCallTarget.Descriptor;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.type.*;

/**
 * Implements a direct call to a C/C++ HotSpot function.
 */
public class CRuntimeCall extends DeoptimizingFixedWithNextNode implements LIRGenLowerable {

    @Input private final NodeInputList<ValueNode> arguments;

    private final Descriptor descriptor;

    public CRuntimeCall(Descriptor descriptor, ValueNode... arguments) {
        super(StampFactory.forKind(Kind.fromJavaClass(descriptor.getResultType())));
        this.arguments = new NodeInputList<>(this, arguments);
        this.descriptor = descriptor;
    }

    @Override
    public void generate(LIRGenerator gen) {
        RuntimeCallTarget stub = gen.getRuntime().lookupRuntimeCall(descriptor);
        Value[] args = new Value[arguments.size()];
        for (int i = 0; i < args.length; i++) {
            args[i] = gen.operand(arguments.get(i));
        }
        Variable result = gen.emitCall(stub, stub.getCallingConvention(), this, args);
        if (result != null) {
            gen.setResult(this, result);
        }
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @Override
    public DeoptimizationReason getDeoptimizationReason() {
        return null;
    }
}
