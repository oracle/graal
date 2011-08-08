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
package com.oracle.max.graal.compiler.ir;

import java.util.*;

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.CanonicalizerOp;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.NotifyReProcess;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code TypeCheck} class represents an explicit type check instruction.
 */
public final class IsType extends BooleanNode {

    @Input    private Value object;

    public Value object() {
        return object;
    }

    public void setObject(Value x) {
        updateUsages(object, x);
        object = x;
    }

    private final RiType type;

    /**
     * Constructs a new IsType instruction.
     * @param object the instruction producing the object to check against the given type
     * @param graph
     */
    public IsType(Value object, RiType type, Graph graph) {
        super(CiKind.Object, graph);
        assert type.isResolved();
        assert object == null || object.kind == CiKind.Object;
        this.type = type;
        setObject(object);
    }

    public RiType type() {
        return type;
    }

    @Override
    public void accept(ValueVisitor v) {
        // Nothing to do.
    }

    @Override
    public RiType declaredType() {
        // type check does not alter the type of the object
        return object().declaredType();
    }

    @Override
    public RiType exactType() {
        return type;
    }

    @Override
    public void print(LogStream out) {
        out.print("null_check(").print(object()).print(')');
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        properties.put("type", type);
        return properties;
    }

    @Override
    public Node copy(Graph into) {
        return new IsType(null, type, into);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == CanonicalizerOp.class) {
            return (T) CANONICALIZER;
        }
        return super.lookup(clazz);
    }

    private static CanonicalizerOp CANONICALIZER = new CanonicalizerOp() {
        @Override
        public Node canonical(Node node, NotifyReProcess reProcess) {
            IsType isType = (IsType) node;
            Value object = isType.object();
            RiType exactType = object.exactType();
            if (exactType != null) {
                return Constant.forBoolean(exactType == isType.type, node.graph());
            }
            // constants return the correct exactType, so they are handled by the code above
            return isType;
        }
    };
}
