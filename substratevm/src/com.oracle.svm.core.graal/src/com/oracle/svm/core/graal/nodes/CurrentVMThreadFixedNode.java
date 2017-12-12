/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.nodes;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.amd64.FrameAccess;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.util.VMError;

/**
 * Gets the address of the C++ JavaThread object for the current thread.
 *
 * This is a fixed node (no value numbering of multiple thread accesses) and moves the thread
 * register into a new virtual register. The virtual register is necessary because the Graal LIR is
 * currently not flexible enough to handle fixed registers, e.g., in deoptimization states. And the
 * fixed thread register would show up in the Substrate VM deoptimization meta data, where we
 * currently do not support registers (only stack slots and constants).
 */
@NodeInfo(cycles = NodeCycles.CYCLES_1, size = NodeSize.SIZE_1)
public final class CurrentVMThreadFixedNode extends FixedWithNextNode implements LIRLowerable {
    public static final NodeClass<CurrentVMThreadFixedNode> TYPE = NodeClass.create(CurrentVMThreadFixedNode.class);

    public CurrentVMThreadFixedNode() {
        super(TYPE, FrameAccess.getWordStamp());
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        VMError.guarantee(SubstrateOptions.MultiThreaded.getValue());

        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        SubstrateRegisterConfig registerConfig = (SubstrateRegisterConfig) tool.getRegisterConfig();
        gen.setResult(this, tool.emitMove(registerConfig.getThreadRegister().asValue(tool.getLIRKind(FrameAccess.getWordStamp()))));
    }
}
