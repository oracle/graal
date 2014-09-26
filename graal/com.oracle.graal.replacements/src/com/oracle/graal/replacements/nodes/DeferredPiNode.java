/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.nodes;

//JaCoCo Exclude

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.replacements.*;

/**
 * A node for use in method substitutions or snippets that changes the type of its input where the
 * type is not immediately available at {@link NodeIntrinsificationPhase intrinsification} time. It
 * is replaced by a {@link PiNode} once the type becomes constant (which <b>must</b> happen).
 */
@NodeInfo
public class DeferredPiNode extends FloatingNode implements Canonicalizable {

    @Input ValueNode object;
    @Input ValueNode type;

    public ValueNode object() {
        return object;
    }

    public static DeferredPiNode create(ValueNode type, ValueNode object) {
        return USE_GENERATED_NODES ? new DeferredPiNodeGen(type, object) : new DeferredPiNode(type, object);
    }

    protected DeferredPiNode(ValueNode type, ValueNode object) {
        super(StampFactory.object());
        this.type = type;
        this.object = object;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (type.isConstant()) {
            ResolvedJavaType javaType = tool.getConstantReflection().asJavaType(type.asConstant());
            ObjectStamp objectStamp = (ObjectStamp) stamp();
            return PiNode.create(object, javaType, objectStamp.isExactType(), objectStamp.nonNull());
        }
        return this;
    }

    @NodeIntrinsic
    public static native <T> T piCast(Class<T> type, Object object);
}
