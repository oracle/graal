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
package com.oracle.graal.nodes;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.spi.*;

/**
 * A node that results in a platform dependent breakpoint instruction being emitted. A number of
 * arguments can be associated with such a node for placing values of interest in the Java ABI
 * specified parameter locations corresponding to the kinds of the values. That is, the arguments
 * are set up as if the breakpoint instruction was a call to a compiled Java method.
 * <p>
 * A breakpoint is usually place by defining a node intrinsic method as follows:
 *
 * <pre>
 *     {@literal @}NodeIntrinsic(BreakpointNode.class)
 *     static void breakpoint(Object object, Word mark, Word value) {
 *          throw new GraalInternalError("");
 *     }
 * </pre>
 *
 * Note that the signature is arbitrary. It's sole purpose is to capture values you may want to
 * inspect in the native debugger when the breakpoint is hit.
 */
@NodeInfo
public class BreakpointNode extends FixedWithNextNode implements LIRLowerable {

    @Input private final NodeInputList<ValueNode> arguments;

    public static BreakpointNode create(ValueNode[] arguments) {
        return new BreakpointNodeGen(arguments);
    }

    protected BreakpointNode(ValueNode... arguments) {
        super(StampFactory.forVoid());
        this.arguments = new NodeInputList<>(this, arguments);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.visitBreakpointNode(this);
    }

    public NodeInputList<ValueNode> arguments() {
        return arguments;
    }
}
