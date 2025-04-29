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

import static com.oracle.svm.webimage.functionintrinsics.JSCallNode.WEB_IMAGE_CYCLES_RATIONALE;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;

import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;

/**
 * Print characters stored in raw memory.
 */
@NodeInfo(cycles = CYCLES_UNKNOWN, cyclesRationale = WEB_IMAGE_CYCLES_RATIONALE, size = NodeSize.SIZE_IGNORED)
public class WasmPrintNode extends FixedWithNextNode {

    public static final NodeClass<WasmPrintNode> TYPE = NodeClass.create(WasmPrintNode.class);

    @Input ValueNode fd;

    /**
     * Number of bytes to read for each element in {@link #data}.
     * <p>
     * This node reads and prints all data in {@code [data, data + elementSize * length)}.
     */
    public final int elementSize;

    /**
     * Pointer to raw memory.
     */
    @Input ValueNode data;

    /**
     * Number of elements in {@link #data}.
     */
    @Input ValueNode length;

    public WasmPrintNode(ValueNode fd, int elementSize, ValueNode data, ValueNode length) {
        super(TYPE, StampFactory.forVoid());

        this.fd = fd;
        this.elementSize = elementSize;
        this.data = data;
        this.length = length;
    }

    public ValueNode getFd() {
        return fd;
    }

    public ValueNode input() {
        return data;
    }

    public ValueNode getLength() {
        return length;
    }

    @NodeIntrinsic
    public static native void print(int fd, @ConstantNodeParameter int elementSize, PointerBase input, UnsignedWord length);
}
