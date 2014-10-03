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

import static com.oracle.graal.hotspot.HotSpotBackend.*;
import static com.oracle.graal.hotspot.nodes.CStringNode.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.replacements.*;

/**
 * Causes the VM to exit with a description of the current Java location and an optional
 * {@linkplain Log#printf(String, long) formatted} error message specified.
 */
@NodeInfo
public class VMErrorNode extends DeoptimizingStubCall implements LIRLowerable {

    protected String format;
    @Input ValueNode value;

    public static VMErrorNode create(String format, ValueNode value) {
        return USE_GENERATED_NODES ? new VMErrorNodeGen(format, value) : new VMErrorNode(format, value);
    }

    protected VMErrorNode(String format, ValueNode value) {
        super(StampFactory.forVoid());
        this.format = format;
        this.value = value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        String whereString;
        if (stateBefore() != null) {
            String nl = CodeUtil.NEW_LINE;
            StringBuilder sb = new StringBuilder("in compiled code associated with frame state:");
            FrameState fs = stateBefore();
            while (fs != null) {
                MetaUtil.appendLocation(sb.append(nl).append("\t"), fs.method(), fs.bci());
                fs = fs.outerFrameState();
            }
            whereString = sb.toString();
        } else {
            ResolvedJavaMethod method = graph().method();
            whereString = "in compiled code for " + (method == null ? graph().toString() : method.format("%H.%n(%p)"));
        }
        Value whereArg = emitCString(gen, whereString);
        Value formatArg = emitCString(gen, format);

        ForeignCallLinkage linkage = gen.getLIRGeneratorTool().getForeignCalls().lookupForeignCall(VM_ERROR);
        gen.getLIRGeneratorTool().emitForeignCall(linkage, null, whereArg, formatArg, gen.operand(value));
    }

    @NodeIntrinsic
    public static native void vmError(@ConstantNodeParameter String format, long value);
}
