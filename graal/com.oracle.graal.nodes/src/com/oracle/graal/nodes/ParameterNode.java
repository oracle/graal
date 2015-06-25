/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes;

import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.spi.*;

/**
 * The {@code Parameter} instruction is a placeholder for an incoming argument to a function call.
 */
@NodeInfo(nameTemplate = "P({p#index})")
public final class ParameterNode extends AbstractLocalNode implements IterableNodeType, UncheckedInterfaceProvider {

    public static final NodeClass<ParameterNode> TYPE = NodeClass.create(ParameterNode.class);

    public ParameterNode(int index, Stamp stamp) {
        super(TYPE, index, stamp);
    }

    public Stamp uncheckedStamp() {
        ResolvedJavaMethod method = graph().method();
        if (method != null) {
            JavaType parameterType;
            if (method.isStatic() || index() > 0) {
                int signatureIndex = method.isStatic() ? index() : index() - 1;
                parameterType = method.getSignature().getParameterType(signatureIndex, method.getDeclaringClass());
            } else {
                parameterType = method.getDeclaringClass();
            }
            return UncheckedInterfaceProvider.uncheckedOrNull(parameterType, stamp());
        }
        return null;
    }
}
