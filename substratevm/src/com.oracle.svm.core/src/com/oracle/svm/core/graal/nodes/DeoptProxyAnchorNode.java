/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.AbstractStateSplit;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * The anchor for DeoptProxyNode when no full {@link DeoptEntryNode deoptimization entry} is
 * required.
 */
@NodeInfo(allowedUsageTypes = InputType.Anchor, cycles = NodeCycles.CYCLES_0, size = NodeSize.SIZE_0)
public class DeoptProxyAnchorNode extends AbstractStateSplit implements DeoptEntrySupport {
    public static final NodeClass<DeoptProxyAnchorNode> TYPE = NodeClass.create(DeoptProxyAnchorNode.class);

    private final int proxifiedInvokeBci;

    public DeoptProxyAnchorNode(int proxifiedInvokeBci) {
        super(TYPE, StampFactory.forVoid());
        assert proxifiedInvokeBci >= 0 : "DeoptProxyAnchorNode should be proxing an invoke";

        this.proxifiedInvokeBci = proxifiedInvokeBci;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        /* No-op */
    }

    @Override
    public int getProxifiedInvokeBci() {
        return proxifiedInvokeBci;
    }
}
