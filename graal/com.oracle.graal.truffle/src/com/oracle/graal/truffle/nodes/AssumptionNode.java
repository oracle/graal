/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.nodes;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.truffle.*;

@NodeInfo
public class AssumptionNode extends MacroNode implements com.oracle.graal.graph.IterableNodeType, Simplifiable {

    public AssumptionNode(Invoke invoke) {
        super(invoke);
        assert super.arguments.size() == 1;
    }

    private ValueNode getAssumption() {
        return arguments.first();
    }

    private static SnippetReflectionProvider getSnippetReflection() {
        /*
         * This class requires access to the objects encapsulated in Constants, and therefore breaks
         * the compiler-VM separation of object constants.
         */
        return Graal.getRequiredCapability(SnippetReflectionProvider.class);
    }

    @Override
    public void lower(LoweringTool tool) {
        throw new GraalInternalError(GraphUtil.approxSourceException(this, new RuntimeException("assumption could not be evaluated to a constant")));
    }

    @Override
    public void simplify(SimplifierTool tool) {
        ValueNode assumption = getAssumption();
        if (tool.assumptions() != null && assumption.isConstant()) {
            Constant c = assumption.asConstant();
            assert c.getKind() == Kind.Object;
            Object object = getSnippetReflection().asObject(c);
            OptimizedAssumption assumptionObject = (OptimizedAssumption) object;
            StructuredGraph graph = graph();
            if (assumptionObject.isValid()) {
                tool.assumptions().record(new AssumptionValidAssumption(assumptionObject));
                if (super.getReturnType().getKind() == Kind.Boolean) {
                    graph.replaceFixedWithFloating(this, ConstantNode.forBoolean(true, graph()));
                } else {
                    graph.removeFixed(this);
                }
            } else {
                if (super.getReturnType().getKind() == Kind.Boolean) {
                    graph.replaceFixedWithFloating(this, ConstantNode.forBoolean(false, graph()));
                } else {
                    tool.deleteBranch(this.next());
                    this.replaceAndDelete(graph.add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.None)));
                }
            }
        }
    }
}
