/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.ResolvedJavaType.Representation;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.replacements.nodes.*;

/**
 * This macro node will replace itself with the correct Java {@link Class} as soon as the object's
 * type is known (exact).
 */
public class ObjectGetClassNode extends MacroNode implements Virtualizable, Canonicalizable {

    public ObjectGetClassNode(Invoke invoke) {
        super(invoke);
    }

    private ValueNode getObject() {
        return arguments.get(0);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(getObject());
        if (state != null) {
            Constant clazz = state.getVirtualObject().type().getEncoding(Representation.JavaClass);
            tool.replaceWithValue(ConstantNode.forConstant(clazz, tool.getMetaAccessProvider(), graph()));
        }
    }

    public ValueNode canonical(CanonicalizerTool tool) {
        if (usages().isEmpty()) {
            return null;
        } else {
            ObjectStamp stamp = getObject().objectStamp();
            if (stamp.isExactType()) {
                Constant clazz = stamp.type().getEncoding(Representation.JavaClass);
                return ConstantNode.forConstant(clazz, tool.runtime(), graph());
            } else {
                return this;
            }
        }
    }
}
