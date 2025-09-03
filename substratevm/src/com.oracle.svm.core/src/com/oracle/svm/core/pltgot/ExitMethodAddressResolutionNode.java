/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core.pltgot;

import org.graalvm.nativeimage.c.function.CodePointer;

import com.oracle.svm.core.graal.code.SubstrateLIRGenerator;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

@NodeInfo(cycles = NodeCycles.CYCLES_1, size = NodeSize.SIZE_1)
public final class ExitMethodAddressResolutionNode extends ControlSinkNode implements LIRLowerable {
    public static final NodeClass<ExitMethodAddressResolutionNode> TYPE = NodeClass.create(ExitMethodAddressResolutionNode.class);

    @Input protected ValueNode ip;

    public ExitMethodAddressResolutionNode(ValueNode ip) {
        super(TYPE, StampFactory.forVoid());
        this.ip = ip;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder) {
        LIRGeneratorTool gen = builder.getLIRGeneratorTool();
        ((SubstrateLIRGenerator) gen).emitExitMethodAddressResolution(builder.operand(ip));
    }

    @NodeIntrinsic
    public static native void exitMethodAddressResolution(CodePointer ip);
}
