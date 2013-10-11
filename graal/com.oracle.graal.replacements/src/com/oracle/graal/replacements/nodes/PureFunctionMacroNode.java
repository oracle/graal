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
package com.oracle.graal.replacements.nodes;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;

/**
 * This node class can be used to create {@link MacroNode}s for simple pure functions like
 * {@link System#identityHashCode(Object)}.
 */
public abstract class PureFunctionMacroNode extends MacroNode implements Canonicalizable {

    public PureFunctionMacroNode(Invoke invoke) {
        super(invoke);
    }

    /**
     * This method should return either a constant that represents the result of the function, or
     * null if no such result could be determined.
     */
    protected abstract Constant evaluate(Constant param, MetaAccessProvider metaAccess);

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (usages().isEmpty()) {
            return null;
        } else {
            ValueNode param = arguments.get(0);
            if (param.isConstant()) {
                Constant constant = evaluate(param.asConstant(), tool.getMetaAccess());
                if (constant != null) {
                    return ConstantNode.forConstant(constant, tool.getMetaAccess(), graph());
                }
            }
        }
        return this;
    }
}
