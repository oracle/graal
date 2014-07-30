/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.compiler.sparc;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.sparc.*;
import com.oracle.graal.nodes.*;

/**
 * This class implements the SPARC specific portion of the LIR generator.
 */
public abstract class SPARCNodeLIRBuilder extends NodeLIRBuilder {

    public SPARCNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        super(graph, lirGen);
    }

    @Override
    protected boolean peephole(ValueNode valueNode) {
        // No peephole optimizations for now
        return false;
    }

    @Override
    public void visitBreakpointNode(BreakpointNode node) {
        JavaType[] sig = new JavaType[node.arguments().size()];
        for (int i = 0; i < sig.length; i++) {
            sig[i] = node.arguments().get(i).stamp().javaType(gen.getMetaAccess());
        }

        Value[] parameters = visitInvokeArguments(gen.getResult().getFrameMap().registerConfig.getCallingConvention(CallingConvention.Type.JavaCall, null, sig, gen.target(), false), node.arguments());
        append(new SPARCBreakpointOp(parameters));
    }
}
