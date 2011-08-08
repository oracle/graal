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

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.CanonicalizerOp;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.NotifyReProcess;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code NullCheck} class represents an explicit null check instruction.
 */
public final class IsNonNull extends BooleanNode {

    @Input    private Value object;

    public Value object() {
        return object;
    }

    public void setObject(Value x) {
        updateUsages(object, x);
        object = x;
    }

    /**
     * Constructs a new NullCheck instruction.
     * @param object the instruction producing the object to check against null
     * @param graph
     */
    public IsNonNull(Value object, Graph graph) {
        super(CiKind.Object, graph);
        assert object == null || object.kind == CiKind.Object : object;
        setObject(object);
    }

    @Override
    public void accept(ValueVisitor v) {
        // Nothing to do.
    }

    @Override
    public RiType declaredType() {
        // null check does not alter the type of the object
        return object().declaredType();
    }

    @Override
    public RiType exactType() {
        // null check does not alter the type of the object
        return object().exactType();
    }

    @Override
    public void print(LogStream out) {
        out.print("null_check(").print(object()).print(')');
    }

    @Override
    public Node copy(Graph into) {
        return new IsNonNull(null, into);
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
            IsNonNull isNonNull = (IsNonNull) node;
            Value object = isNonNull.object();
            if (object instanceof NewInstance || object instanceof NewArray) {
                return Constant.forBoolean(true, node.graph());
            }
            CiConstant constant = object.asConstant();
            if (constant != null) {
                assert constant.kind == CiKind.Object;
                return Constant.forBoolean(constant.isNonNull(), node.graph());
            }
            return isNonNull;
        }
    };
}
