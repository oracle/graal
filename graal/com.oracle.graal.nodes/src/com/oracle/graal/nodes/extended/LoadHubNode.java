/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 */
package com.oracle.graal.nodes.extended;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.ResolvedJavaType.Representation;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Loads an object's {@linkplain Representation#ObjectHub hub}. The object is not null-checked by
 * this operation.
 */
public final class LoadHubNode extends FixedWithNextNode implements Lowerable, Canonicalizable, Virtualizable {

    @Input private ValueNode object;

    public ValueNode object() {
        return object;
    }

    public LoadHubNode(ValueNode object, Kind kind) {
        super(kind == Kind.Object ? StampFactory.objectNonNull() : StampFactory.forKind(kind));
        this.object = object;
    }

    @Override
    public void lower(LoweringTool tool, LoweringType loweringType) {
        tool.getRuntime().lower(this, tool);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        MetaAccessProvider runtime = tool.runtime();
        if (runtime != null) {
            ObjectStamp stamp = object.objectStamp();

            ResolvedJavaType exactType;
            if (stamp.isExactType()) {
                exactType = stamp.type();
            } else if (stamp.type() != null && tool.assumptions().useOptimisticAssumptions()) {
                exactType = stamp.type().findUniqueConcreteSubtype();
                if (exactType != null) {
                    tool.assumptions().recordConcreteSubtype(stamp.type(), exactType);
                }
            } else {
                exactType = null;
            }

            if (exactType != null) {
                return ConstantNode.forConstant(exactType.getEncoding(Representation.ObjectHub), runtime, graph());
            }
        }
        return this;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(object);
        if (state != null) {
            Constant constantHub = state.getVirtualObject().type().getEncoding(Representation.ObjectHub);
            tool.replaceWithValue(ConstantNode.forConstant(constantHub, tool.getMetaAccessProvider(), graph()));
        }
    }
}
