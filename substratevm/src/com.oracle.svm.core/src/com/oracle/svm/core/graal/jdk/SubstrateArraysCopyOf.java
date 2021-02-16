/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.VirtualizableAllocation;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Interface for SubstrateVM nodes implementing {@link Arrays#copyOf}.
 */
public interface SubstrateArraysCopyOf extends Lowerable, VirtualizableAllocation {

    ValueNode getOriginal();

    ValueNode getOriginalLength();

    ValueNode getNewArrayType();

    ValueNode getNewLength();

    static Stamp computeStamp(Stamp result) {
        if (result instanceof ObjectStamp) {
            return result.join(StampFactory.objectNonNull());
        }
        return result;
    }

    @Override
    default void virtualize(VirtualizerTool tool) {
        if (!getNewArrayType().isConstant()) {
            /*
             * This is an object array copy. If the new array type is not a constant then it cannot
             * be virtualized.
             */
            return;
        }

        /* from index is always 0 for Arrays.copyOf. */
        ValueNode from = ConstantNode.forInt(0);
        ResolvedJavaType newComponentType = tool.getConstantReflection().asJavaType(getNewArrayType().asConstant()).getComponentType();
        GraphUtil.virtualizeArrayCopy(tool, getOriginal(), getOriginalLength(), getNewLength(), from, newComponentType, JavaKind.Object, asNode().graph(),
                        (componentType, length) -> new VirtualArrayNode(componentType, length));
    }
}
