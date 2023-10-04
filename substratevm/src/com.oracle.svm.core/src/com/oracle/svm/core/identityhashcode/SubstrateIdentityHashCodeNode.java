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
package com.oracle.svm.core.identityhashcode;

import jdk.compiler.graal.api.replacements.Fold;
import jdk.compiler.graal.core.common.type.TypedConstant;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodeinfo.NodeCycles;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodeinfo.NodeSize;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.replacements.nodes.IdentityHashCodeNode;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.config.ConfigurationValues;

import jdk.vm.ci.meta.JavaConstant;

@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, cyclesRationale = "Decided depending on identity hash code storage.", //
                size = NodeSize.SIZE_UNKNOWN, sizeRationale = "Decided depending on identity hash code storage.")
public final class SubstrateIdentityHashCodeNode extends IdentityHashCodeNode {

    public static final NodeClass<SubstrateIdentityHashCodeNode> TYPE = NodeClass.create(SubstrateIdentityHashCodeNode.class);

    public static ValueNode create(ValueNode object, int bci) {
        /*
         * Because the canonicalization logic is in the superclass, it is easier to always create a
         * new node and then canonicalize it.
         */
        return (ValueNode) new SubstrateIdentityHashCodeNode(object, bci).canonical(null);
    }

    protected SubstrateIdentityHashCodeNode(ValueNode object, int bci) {
        super(TYPE, object, bci);
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        // Without a fixed field, we must write bits in the object header.
        return haveFixedField() ? IdentityHashCodeSupport.IDENTITY_HASHCODE_LOCATION : LocationIdentity.any();
    }

    @Override
    protected int getIdentityHashCode(JavaConstant constant) {
        return ((TypedConstant) constant).getIdentityHashCode();
    }

    @Override
    public NodeCycles estimatedNodeCycles() {
        return haveFixedField() ? NodeCycles.CYCLES_2 : NodeCycles.CYCLES_8;
    }

    @Override
    protected NodeSize dynamicNodeSizeEstimate() {
        return haveFixedField() ? NodeSize.SIZE_8 : NodeSize.SIZE_32;
    }

    @Fold
    static boolean haveFixedField() {
        return ConfigurationValues.getObjectLayout().hasFixedIdentityHashField();
    }
}
