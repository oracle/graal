/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.interpreter.value.InterpreterValue;
import org.graalvm.compiler.nodes.util.InterpreterState;
import org.graalvm.compiler.nodeinfo.NodeInfo;

@NodeInfo
public abstract class FixedNode extends ValueNode implements FixedNodeInterface {
    public static final NodeClass<FixedNode> TYPE = NodeClass.create(FixedNode.class);

    protected FixedNode(NodeClass<? extends FixedNode> c, Stamp stamp) {
        super(c, stamp);
    }

    @Override
    public boolean verify() {
        assertTrue(this.successors().isNotEmpty() || this.predecessor() != null, "FixedNode should not float: %s", this);
        return super.verify();
    }

    /* This method is final to ensure that it can be de-virtualized and inlined. */
    @Override
    public final FixedNode asFixedNode() {
        return this;
    }

    /**
     * Graal IR Interpreter - interpret this node during a control-flow pass.
     *
     * Each subclass should define this method according to the usual Java semantics of that node.
     *
     * The default implementation of this method throws a GraalError to say which
     * class is missing a proper implementation of this <code>interpret</code> method.
     *
     * If this control-flow node returns a value (e.g. function calls, field loads, etc.),
     * then the result value V should be stored in the interpreter local values map by calling:
     * <code>
     *  interpreter.setNodeLookupValue(this, V);
     * </code>
     * The <code>interpretExpr</code> method defined in this class simply looks up
     * and returns the value that was calculated during the control-flow pass.
     *
     * @param interpreter
     * @return the next control-flow node to be executed.
     */
    public FixedNode interpret(InterpreterState interpreter) {
        GraalError.unimplemented("interpretControlFlow: " + this.getClass());
        return null;
    }

    @Override
    public InterpreterValue interpretExpr(InterpreterState interpreter) {
        return interpreter.getNodeLookupValue(this);
    }
}
