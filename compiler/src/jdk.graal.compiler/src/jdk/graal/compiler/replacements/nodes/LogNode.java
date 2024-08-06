/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import java.io.PrintStream;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;

/**
 * High-level node for simple low level logging. This node can be used early on in high-tier to
 * produce simple {@linkplain PrintStream}-like logging of values to stdout. This can be useful in
 * debugging / logging value flow throughout a compiler graph. A simple example would be: new
 * optimization added that reads a value and computes a mathematical function on it then this node
 * can be used to print all original and transformed values to stdout.
 */
@NodeInfo(cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
public final class LogNode extends FixedWithNextNode implements Lowerable {
    public static final NodeClass<LogNode> TYPE = NodeClass.create(LogNode.class);

    protected final String message;
    @OptionalInput ValueNode l1;
    @OptionalInput ValueNode l2;
    @OptionalInput ValueNode l3;

    public LogNode(@ConstantNodeParameter String message, ValueNode l1, ValueNode l2, ValueNode l3) {
        super(TYPE, StampFactory.forVoid());
        this.message = message;
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
    }

    public LogNode(@ConstantNodeParameter String message, ValueNode l1, ValueNode l2) {
        this(message, l1, l2, null);
    }

    public LogNode(@ConstantNodeParameter String message, ValueNode l1) {
        this(message, l1, null, null);
    }

    public LogNode(@ConstantNodeParameter String message) {
        this(message, null, null, null);
    }

    public ValueNode getL1() {
        return l1;
    }

    public ValueNode getL2() {
        return l2;
    }

    public ValueNode getL3() {
        return l3;
    }

    public String message() {
        return message;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

}
