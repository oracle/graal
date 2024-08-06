/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * A node that results in a platform-dependent breakpoint instruction being emitted. A number of
 * arguments can be associated with such a node for placing values of interest in the Java ABI
 * specified parameter locations corresponding to the kinds of the values. That is, the arguments
 * are set up as if the breakpoint instruction was a call to a compiled Java method.
 * <p>
 * A breakpoint is usually placed by defining a node intrinsic method as follows:
 *
 * <pre>
 *     {@literal @}NodeIntrinsic(BreakpointNode.class)
 *     static native void breakpoint(Object object, Word mark, Word value);
 * </pre>
 *
 * Note that the signature is arbitrary. Its sole purpose is to capture values you may want to
 * inspect in the native debugger when the breakpoint is hit. These values are placed in the
 * parameter registers (see the platform-specific {@link jdk.vm.ci.code.RegisterConfig}). In gdb,
 * these registers can be inspected using (for instance):
 *
 * <pre>
 *     (gdb) info registers
 *     (gdb) pp @($rdi)
 * </pre>
 */
@NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
public final class BreakpointNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<BreakpointNode> TYPE = NodeClass.create(BreakpointNode.class);
    @Input NodeInputList<ValueNode> arguments;

    public BreakpointNode(ValueNode... arguments) {
        super(TYPE, StampFactory.forVoid());
        this.arguments = new NodeInputList<>(this, arguments);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.visitBreakpointNode(this);
    }

    public NodeInputList<ValueNode> arguments() {
        return arguments;
    }

    @NodeIntrinsic
    public static native void breakpoint();
}
