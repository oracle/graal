/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.gen.NodeLIRBuilder;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.extended.ForeignCall;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.JavaKind;

/**
 * Represents a read from an argument location of a calling convention. This node is associated with
 * an {@link Invoke} or {@link ForeignCall}, denoting a call site that allows multiple return
 * values, either by defining new values or preserving old values in the original argument
 * locations. This is necessary to ensure that the LIR operation for this class is generated in the
 * same LIR basic block as the invocation, allowing for correct interval creation during register
 * allocation.
 *
 * @see MultiReturnNode
 * @see NodeLIRBuilder#emitInvoke(Invoke)
 * @see NodeLIRBuilder#emitForeignCall
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class ReadArgumentNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<ReadArgumentNode> TYPE = NodeClass.create(ReadArgumentNode.class);

    @Input(InputType.Association) FixedNode invoke;

    private final int index;

    public ReadArgumentNode(FixedNode invoke, JavaKind javaKind, int index) {
        super(TYPE, StampFactory.forKind(javaKind));
        this.invoke = invoke;
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        GraalError.guarantee(generator.hasOperand(this), "ReadArgumentNode should have been generated when the associated invoke is generated");
    }
}
