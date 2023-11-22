/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_4;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_8;

import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.Lowerable;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.FrameAccess;

/**
 * Puts the VaList in a StackValue, used as a layer to access and modify it.
 */
@NodeInfo(size = SIZE_8, cycles = CYCLES_4)
public final class VaListInitializationNode extends FixedWithNextNode implements SingleMemoryKill, Lowerable, IterableNodeType {
    public static final NodeClass<VaListInitializationNode> TYPE = NodeClass.create(VaListInitializationNode.class);

    @Input protected ValueNode vaList;

    public VaListInitializationNode(ValueNode vaList) {
        super(TYPE, FrameAccess.getWordStamp());
        this.vaList = vaList;
    }

    public ValueNode getVaList() {
        return vaList;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }
}
