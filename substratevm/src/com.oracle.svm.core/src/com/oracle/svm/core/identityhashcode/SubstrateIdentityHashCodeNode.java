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

import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.config.ConfigurationValues;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.replacements.nodes.IdentityHashCodeNode;

@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, cyclesRationale = "Decided depending on identity hash code storage.", //
                size = NodeSize.SIZE_UNKNOWN, sizeRationale = "Decided depending on identity hash code storage.")
public final class SubstrateIdentityHashCodeNode extends IdentityHashCodeNode {

    public static final NodeClass<SubstrateIdentityHashCodeNode> TYPE = NodeClass.create(SubstrateIdentityHashCodeNode.class);

    public static ValueNode create(ValueNode object, int bci, CoreProviders providers) {
        ValueNode result = canonical(object, providers);
        return result != null ? result : new SubstrateIdentityHashCodeNode(object, bci);
    }

    protected SubstrateIdentityHashCodeNode(ValueNode object, int bci) {
        super(TYPE, object, bci);
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        // With optional identity hash codes, we must write bits in the object header.
        return isIdentityHashFieldOptional() ? LocationIdentity.any() : IdentityHashCodeSupport.IDENTITY_HASHCODE_LOCATION;
    }

    @Override
    public NodeCycles estimatedNodeCycles() {
        return isIdentityHashFieldOptional() ? NodeCycles.CYCLES_8 : NodeCycles.CYCLES_2;
    }

    @Override
    protected NodeSize dynamicNodeSizeEstimate() {
        return isIdentityHashFieldOptional() ? NodeSize.SIZE_32 : NodeSize.SIZE_8;
    }

    @Fold
    static boolean isIdentityHashFieldOptional() {
        return ConfigurationValues.getObjectLayout().isIdentityHashFieldOptional();
    }
}
