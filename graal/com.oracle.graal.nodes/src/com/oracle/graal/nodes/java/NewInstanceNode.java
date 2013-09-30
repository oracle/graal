/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.java;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * The {@code NewInstanceNode} represents the allocation of an instance class object.
 */
@NodeInfo(nameTemplate = "New {p#instanceClass/s}")
public final class NewInstanceNode extends DeoptimizingFixedWithNextNode implements Canonicalizable, Lowerable, VirtualizableAllocation {

    private final ResolvedJavaType instanceClass;
    private final boolean fillContents;

    /**
     * Constructs a NewInstanceNode.
     * 
     * @param type the class being allocated
     * @param fillContents determines whether the new object's fields should be initialized to
     *            zero/null.
     */
    public NewInstanceNode(ResolvedJavaType type, boolean fillContents) {
        super(StampFactory.exactNonNull(type));
        this.instanceClass = type;
        this.fillContents = fillContents;
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
     * @return <code>true</code> if the fields of the new object will be initialized.
     */
    public boolean fillContents() {
        return fillContents;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (usages().isEmpty()) {
            return null;
        } else {
            return this;
        }
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getRuntime().lower(this, tool);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        if (instanceClass != null) {
            assert !instanceClass().isArray();
            VirtualInstanceNode virtualObject = new VirtualInstanceNode(instanceClass(), true);
            ResolvedJavaField[] fields = virtualObject.getFields();
            ValueNode[] state = new ValueNode[fields.length];
            for (int i = 0; i < state.length; i++) {
                state[i] = ConstantNode.defaultForKind(fields[i].getType().getKind(), graph());
            }
            tool.createVirtualObject(virtualObject, state, null);
            tool.replaceWithVirtual(virtualObject);
        }
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }
}
