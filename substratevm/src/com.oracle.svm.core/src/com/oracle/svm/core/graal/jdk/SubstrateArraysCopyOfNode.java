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
package com.oracle.svm.core.graal.jdk;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_64;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.DeoptimizingFixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;

/**
 * Implementation for substrate Arrays.copyOf(). It is currently used only to intrinsify copying of
 * Object arrays (no primitive arrays).
 */
@NodeInfo(cycles = CYCLES_64, size = NodeSize.SIZE_64)
public final class SubstrateArraysCopyOfNode extends DeoptimizingFixedWithNextNode implements SubstrateArraysCopyOf {
    public static final NodeClass<SubstrateArraysCopyOfNode> TYPE = NodeClass.create(SubstrateArraysCopyOfNode.class);

    @Input ValueNode original;
    @Input ValueNode originalLength;
    @Input ValueNode newLength;
    /** The type of the array copy. */
    @Input ValueNode newArrayType;

    /**
     * The stamp is conservative. The concrete type will be loaded from newTypeObject.
     */
    public SubstrateArraysCopyOfNode(@InjectedNodeParameter Stamp stamp, ValueNode original, ValueNode originaLength, ValueNode newLength, ValueNode newArrayType) {
        super(TYPE, SubstrateArraysCopyOf.computeStamp(stamp));
        this.original = original;
        this.originalLength = originaLength;
        this.newLength = newLength;
        this.newArrayType = newArrayType;
    }

    @Override
    public ValueNode getOriginal() {
        return original;
    }

    @Override
    public ValueNode getOriginalLength() {
        return originalLength;
    }

    @Override
    public ValueNode getNewArrayType() {
        return newArrayType;
    }

    @Override
    public ValueNode getNewLength() {
        return newLength;
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }
}
