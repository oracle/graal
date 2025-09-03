/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.JavaKind;

/**
 * Node producing a call to the {@code memory.copy} instruction.
 */
@NodeInfo(shortName = "memory.copy", cycles = CYCLES_UNKNOWN, size = SIZE_1)
public class WasmMemoryCopyNode extends FixedWithNextNode {

    public static final NodeClass<WasmMemoryCopyNode> TYPE = NodeClass.create(WasmMemoryCopyNode.class);

    @Input ValueNode target;
    @Input ValueNode source;
    @Input ValueNode size;

    public WasmMemoryCopyNode(ValueNode target, ValueNode source, ValueNode size) {
        super(TYPE, StampFactory.forVoid());
        this.target = target;
        this.source = source;
        this.size = size;

        assert target.getStackKind() == JavaKind.Int : "memory.copy target needs to be an int";
        assert source.getStackKind() == JavaKind.Int : "memory.copy source needs to be an int";
        assert size.getStackKind() == JavaKind.Int : "memory.copy size needs to be an int";
    }

    public ValueNode getTarget() {
        return target;
    }

    public ValueNode getSource() {
        return source;
    }

    public ValueNode getSize() {
        return size;
    }
}
