/*
 * Copyright (c) 2022, Alibaba Group Holding Limited. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */
package org.graalvm.compiler.nodes.extended;

import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.AbstractMemoryCheckpoint;
import org.graalvm.compiler.nodes.spi.Lowerable;

@NodeInfo
public final class ArrayFillNode extends AbstractMemoryCheckpoint implements Lowerable {
    public static final NodeClass<ArrayFillNode> TYPE = NodeClass.create(ArrayFillNode.class);
    private final JavaKind elementType;
    @Input
    ValueNode dst;
    @Input
    ValueNode dstOffset;
    @Input
    ValueNode value;
    @Input
    ValueNode count;

    public ArrayFillNode(ValueNode dst, ValueNode dstOffset, ValueNode value, ValueNode count, JavaKind elementType) {
        super(TYPE, StampFactory.forKind(JavaKind.Void));
        this.value = value;
        this.dstOffset = dstOffset;
        this.count = count;
        this.dst = dst;
        this.elementType = elementType;
    }

    public ValueNode getValue() {
        return value;
    }

    public ValueNode getCount() {
        return count;
    }

    public ValueNode getDst() {
        return dst;
    }

    public ValueNode getDstOffset() {
        return dstOffset;
    }

    public JavaKind getElementType() {
        return elementType;
    }
}