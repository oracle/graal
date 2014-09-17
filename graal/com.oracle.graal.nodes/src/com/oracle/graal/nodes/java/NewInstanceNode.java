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

import java.lang.ref.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * The {@code NewInstanceNode} represents the allocation of an instance class object.
 */
@NodeInfo(nameTemplate = "New {p#instanceClass/s}")
public class NewInstanceNode extends AbstractNewObjectNode implements VirtualizableAllocation {

    private final ResolvedJavaType instanceClass;

    /**
     * Constructs a NewInstanceNode.
     *
     * @param type the class being allocated
     * @param fillContents determines whether the new object's fields should be initialized to
     *            zero/null.
     */
    public static NewInstanceNode create(ResolvedJavaType type, boolean fillContents) {
        return USE_GENERATED_NODES ? new NewInstanceNodeGen(type, fillContents) : new NewInstanceNode(type, fillContents);
    }

    protected NewInstanceNode(ResolvedJavaType type, boolean fillContents) {
        super(StampFactory.exactNonNull(type), fillContents);
        assert !type.isArray() && !type.isInterface() && !type.isPrimitive();
        this.instanceClass = type;
    }

    /**
     * Gets the instance class being allocated by this node.
     *
     * @return the instance class allocated
     */
    public ResolvedJavaType instanceClass() {
        return instanceClass;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        /*
         * Reference objects can escape into their ReferenceQueue at any safepoint, therefore
         * they're excluded from escape analysis.
         */
        if (!tool.getMetaAccessProvider().lookupJavaType(Reference.class).isAssignableFrom(instanceClass)) {
            VirtualInstanceNode virtualObject = createVirtualInstanceNode(true);
            ResolvedJavaField[] fields = virtualObject.getFields();
            ValueNode[] state = new ValueNode[fields.length];
            for (int i = 0; i < state.length; i++) {
                state[i] = defaultFieldValue(fields[i]);
            }
            tool.createVirtualObject(virtualObject, state, Collections.<MonitorIdNode> emptyList());
            tool.replaceWithVirtual(virtualObject);
        }
    }

    protected VirtualInstanceNode createVirtualInstanceNode(boolean hasIdentity) {
        return VirtualInstanceNode.create(instanceClass(), hasIdentity);
    }

    /* Factored out in a separate method so that subclasses can override it. */
    protected ConstantNode defaultFieldValue(ResolvedJavaField field) {
        return ConstantNode.defaultForKind(field.getType().getKind(), graph());
    }
}
