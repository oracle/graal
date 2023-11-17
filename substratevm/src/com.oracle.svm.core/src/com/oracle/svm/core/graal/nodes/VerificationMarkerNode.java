/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.debug.ControlFlowAnchored;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import com.oracle.svm.core.graal.code.SubstrateLIRGenerator;
import com.oracle.svm.core.util.VMError;

@NodeInfo(cycles = NodeCycles.CYCLES_0, size = NodeSize.SIZE_0)
public final class VerificationMarkerNode extends FixedWithNextNode implements LIRLowerable, ControlFlowAnchored {
    public static final NodeClass<VerificationMarkerNode> TYPE = NodeClass.create(VerificationMarkerNode.class);

    private final Object marker;

    public VerificationMarkerNode(Object marker) {
        super(TYPE, StampFactory.forVoid());
        this.marker = marker;
    }

    @Override
    protected void afterClone(Node other) {
        throw VMError.shouldNotReachHere("Marker must be unique, therefore the node cannot be cloned");
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        SubstrateLIRGenerator sgenerator = (SubstrateLIRGenerator) generator.getLIRGeneratorTool();
        sgenerator.emitVerificationMarker(marker);
    }
}
