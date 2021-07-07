/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.AbstractNewArrayNode;

import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * The {@link SubstrateNewHybridInstanceNode} represents the allocation of an instance class object
 * with a hybrid layout.
 * 
 * @see com.oracle.svm.core.annotate.Hybrid
 */
@NodeInfo(nameTemplate = "NewHybridInstance {p#instanceClass/s}")
public final class SubstrateNewHybridInstanceNode extends AbstractNewArrayNode {
    public static final NodeClass<SubstrateNewHybridInstanceNode> TYPE = NodeClass.create(SubstrateNewHybridInstanceNode.class);

    private final ResolvedJavaType instanceClass;
    private final ResolvedJavaType elementType;

    public SubstrateNewHybridInstanceNode(ResolvedJavaType instanceType, ResolvedJavaType elementType, ValueNode arrayLength) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createExactTrusted(instanceType)), arrayLength, true, null);
        this.instanceClass = instanceType;
        this.elementType = elementType;
    }

    /**
     * Gets the instance class being allocated by this node.
     *
     * @return the instance class allocated
     */
    public ResolvedJavaType instanceClass() {
        return instanceClass;
    }

    /**
     * Gets the element type of the inlined array.
     *
     * @return the element type of the inlined array
     */
    public ResolvedJavaType elementType() {
        return elementType;
    }
}
