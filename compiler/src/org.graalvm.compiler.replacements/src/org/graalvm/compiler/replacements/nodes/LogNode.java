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
package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import java.io.PrintStream;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;

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

    public LogNode(@ConstantNodeParameter String message, ValueNode l1, ValueNode l2) {
        super(TYPE, StampFactory.forVoid());
        this.message = message;
        this.l1 = l1;
        this.l2 = l2;
    }

    public ValueNode getL1() {
        return l1;
    }

    public ValueNode getL2() {
        return l2;
    }

    public String message() {
        return message;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

}
