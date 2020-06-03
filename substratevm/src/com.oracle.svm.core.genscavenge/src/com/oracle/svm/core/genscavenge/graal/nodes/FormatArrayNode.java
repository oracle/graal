/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.graal.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_64;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_64;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.word.Pointer;

@NodeInfo(cycles = CYCLES_64, size = SIZE_64)
public class FormatArrayNode extends FixedWithNextNode implements Lowerable {
    public static final NodeClass<FormatArrayNode> TYPE = NodeClass.create(FormatArrayNode.class);

    @Input protected ValueNode memory;
    @Input protected ValueNode hub;
    @Input protected ValueNode length;
    @Input protected ValueNode rememberedSet;
    @Input protected ValueNode unaligned;
    @Input protected ValueNode fillContents;
    @Input protected ValueNode emitMemoryBarrier;

    public FormatArrayNode(ValueNode memory, ValueNode hub, ValueNode length, ValueNode rememberedSet, ValueNode unaligned, ValueNode fillContents, ValueNode emitMemoryBarrier) {
        super(TYPE, StampFactory.objectNonNull());
        this.memory = memory;
        this.hub = hub;
        this.length = length;
        this.rememberedSet = rememberedSet;
        this.unaligned = unaligned;
        this.fillContents = fillContents;
        this.emitMemoryBarrier = emitMemoryBarrier;
    }

    public ValueNode getMemory() {
        return memory;
    }

    public ValueNode getHub() {
        return hub;
    }

    public ValueNode getLength() {
        return length;
    }

    public ValueNode getRememberedSet() {
        return rememberedSet;
    }

    public ValueNode getUnaligned() {
        return unaligned;
    }

    public ValueNode getFillContents() {
        return fillContents;
    }

    public ValueNode getEmitMemoryBarrier() {
        return emitMemoryBarrier;
    }

    @NodeIntrinsic
    public static native Object formatArray(Pointer memory, Class<?> hub, int length, boolean rememberedSet, boolean unaligned, boolean fillContents, boolean emitMemoryBarrier);
}
