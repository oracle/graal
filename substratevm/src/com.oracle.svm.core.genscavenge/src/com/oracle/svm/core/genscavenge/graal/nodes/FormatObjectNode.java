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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_64;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_64;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.replacements.AllocationSnippets.FillContent;
import org.graalvm.word.Pointer;

@NodeInfo(cycles = CYCLES_64, size = SIZE_64)
public class FormatObjectNode extends FixedWithNextNode implements Lowerable {
    public static final NodeClass<FormatObjectNode> TYPE = NodeClass.create(FormatObjectNode.class);

    @Input protected ValueNode memory;
    @Input protected ValueNode hub;
    @Input protected ValueNode rememberedSet;
    @Input protected ValueNode fillContents;
    @Input protected ValueNode emitMemoryBarrier;

    public FormatObjectNode(ValueNode memory, ValueNode hub, ValueNode rememberedSet, ValueNode fillContents, ValueNode emitMemoryBarrier) {
        super(TYPE, StampFactory.objectNonNull());
        this.memory = memory;
        this.hub = hub;
        this.rememberedSet = rememberedSet;
        this.fillContents = fillContents;
        this.emitMemoryBarrier = emitMemoryBarrier;
    }

    public ValueNode getMemory() {
        return memory;
    }

    public ValueNode getHub() {
        return hub;
    }

    public ValueNode getRememberedSet() {
        return rememberedSet;
    }

    public ValueNode getFillContents() {
        return fillContents;
    }

    public ValueNode getEmitMemoryBarrier() {
        return emitMemoryBarrier;
    }

    @NodeIntrinsic
    public static native Object formatObject(Pointer memory, Class<?> hub, boolean rememberedSet, FillContent fillContents, boolean emitMemoryBarrier);
}
