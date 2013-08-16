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
package com.oracle.graal.truffle.nodes.typesystem;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.truffle.nodes.asserts.*;
import com.oracle.truffle.api.*;

/**
 * Macro node for method {@link CompilerDirectives#unsafeCast(Object, Class)} and
 * {@link CompilerDirectives#unsafeCast(Object, Class, Object, Object)}.
 */
public class TypeCastMacroNode extends NeverPartOfCompilationNode implements Canonicalizable {

    private static final int ARGUMENT_COUNT = 4;
    private static final int OBJECT_ARGUMENT_INDEX = 0;
    private static final int CLASS_ARGUMENT_INDEX = 1;
    private static final int RECEIVER_ARGUMENT_INDEX = 2;
    private static final int CUSTOM_TYPE_ARGUMENT_INDEX = 3;

    public TypeCastMacroNode(Invoke invoke) {
        super(invoke, "The class of the unsafe cast could not be reduced to a compile time constant.");
        assert arguments.size() == ARGUMENT_COUNT;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        ValueNode classArgument = arguments.get(CLASS_ARGUMENT_INDEX);
        ValueNode customTypeArgument = arguments.get(CUSTOM_TYPE_ARGUMENT_INDEX);
        if (classArgument.isConstant() && customTypeArgument.isConstant()) {
            Class c = (Class) classArgument.asConstant().asObject();
            ResolvedJavaType lookupJavaType = tool.runtime().lookupJavaType(c);
            ValueNode objectArgument = arguments.get(OBJECT_ARGUMENT_INDEX);
            ValueNode receiverArgument = arguments.get(RECEIVER_ARGUMENT_INDEX);
            Object customType = customTypeArgument.asConstant().asObject();
            return graph().add(new TypeCastNode(objectArgument, lookupJavaType, receiverArgument, customType));
        }
        return this;
    }
}
