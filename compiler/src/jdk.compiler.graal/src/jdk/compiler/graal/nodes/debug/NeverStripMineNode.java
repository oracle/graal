/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.nodes.debug;

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_IGNORED;

import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.FixedWithNextNode;
import jdk.compiler.graal.nodes.spi.LIRLowerable;
import jdk.compiler.graal.nodes.spi.NodeLIRBuilderTool;

/**
 * Compiler directives node that disabled strip mining of enclosing loops.
 */
@NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED, cyclesRationale = "Directives node", sizeRationale = "Directives node")
public final class NeverStripMineNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<NeverStripMineNode> TYPE = NodeClass.create(NeverStripMineNode.class);

    private static class Unique {
    }

    protected Unique unique;

    public NeverStripMineNode() {
        super(TYPE, StampFactory.forVoid());
        this.unique = new Unique();
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        // do nothing
    }

}
