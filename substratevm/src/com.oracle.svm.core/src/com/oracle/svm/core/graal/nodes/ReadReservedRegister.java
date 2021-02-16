/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.nodes;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.meta.SharedMethod;

import jdk.vm.ci.code.Register;

public class ReadReservedRegister {

    public static ValueNode createReadStackPointerNode(StructuredGraph graph) {
        return createReadNode(graph, ReservedRegisters.singleton().getFrameRegister());
    }

    public static ValueNode createReadIsolateThreadNode(StructuredGraph graph) {
        return createReadNode(graph, ReservedRegisters.singleton().getThreadRegister());
    }

    public static ValueNode createReadHeapBaseNode(StructuredGraph graph) {
        return createReadNode(graph, ReservedRegisters.singleton().getHeapBaseRegister());
    }

    private static ValueNode createReadNode(StructuredGraph graph, Register register) {
        /*
         * A floating node to access the register is more efficient: it allows value numbering of
         * multiple accesses, including floating nodes that use it as an input. But for
         * deoptimization target methods, we must not do value numbering because there is no
         * proxying at deoptimization entry points for this node, so the value is not restored
         * during deoptimization.
         */
        boolean isDeoptTarget = graph.method() instanceof SharedMethod && ((SharedMethod) graph.method()).isDeoptTarget();
        if (isDeoptTarget) {
            return new ReadReservedRegisterFixedNode(register);
        } else {
            return new ReadReservedRegisterFloatingNode(register);
        }
    }
}

@NodeInfo(cycles = NodeCycles.CYCLES_0, size = NodeSize.SIZE_0)
final class ReadReservedRegisterFloatingNode extends FloatingNode implements LIRLowerable {
    public static final NodeClass<ReadReservedRegisterFloatingNode> TYPE = NodeClass.create(ReadReservedRegisterFloatingNode.class);

    private final Register register;

    protected ReadReservedRegisterFloatingNode(Register register) {
        super(TYPE, FrameAccess.getWordStamp());
        this.register = register;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRKind lirKind = gen.getLIRGeneratorTool().getLIRKind(stamp);
        gen.setResult(this, register.asValue(lirKind));
    }
}

@NodeInfo(cycles = NodeCycles.CYCLES_0, size = NodeSize.SIZE_0)
final class ReadReservedRegisterFixedNode extends FixedWithNextNode implements LIRLowerable {
    public static final NodeClass<ReadReservedRegisterFixedNode> TYPE = NodeClass.create(ReadReservedRegisterFixedNode.class);

    private final Register register;

    protected ReadReservedRegisterFixedNode(Register register) {
        super(TYPE, FrameAccess.getWordStamp());
        this.register = register;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRKind lirKind = gen.getLIRGeneratorTool().getLIRKind(stamp);
        gen.setResult(this, register.asValue(lirKind));
    }
}
