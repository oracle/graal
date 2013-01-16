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

/**
 * Implements a type check against a compile-time known type.
 */
public final class CheckCastNode extends FixedWithNextNode implements Canonicalizable, Lowerable, Node.IterableNodeType, Virtualizable {

    @Input private ValueNode object;
    private final ResolvedJavaType type;
    private final JavaTypeProfile profile;

    /**
     * Creates a new CheckCast instruction.
     *
     * @param type the type being cast to
     * @param object the instruction producing the object
     */
    public CheckCastNode(ResolvedJavaType type, ValueNode object, JavaTypeProfile profile) {
        super(StampFactory.declared(type));
        assert type != null;
        this.type = type;
        this.object = object;
        this.profile = profile;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getRuntime().lower(this, tool);
    }

    @Override
    public boolean inferStamp() {
        if (object().objectStamp().alwaysNull() && objectStamp().nonNull()) {
            return false;
        }
        return updateStamp(stamp().join(object().stamp()));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        assert object() != null : this;

        if (type != null) {
            ResolvedJavaType objectType = object().objectStamp().type();
            if (objectType != null && type.isAssignableFrom(objectType)) {
                // we don't have to check for null types here because they will also pass the checkcast.
                return object();
            }
        }

        if (object().objectStamp().alwaysNull()) {
            return object();
        }
        return this;
    }

    public ValueNode object() {
        return object;
    }

    /**
     * Gets the type being cast to.
     */
    public ResolvedJavaType type() {
        return type;
    }

    public JavaTypeProfile profile() {
        return profile;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(object);
        if (state != null && state.getState() == EscapeState.Virtual) {
            tool.replaceWithVirtual(state.getVirtualObject());
        }
    }
}
