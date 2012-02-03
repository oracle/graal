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
package com.oracle.max.graal.nodes.java;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;

/**
 * The {@code InstanceOfNode} represents an instanceof test.
 */
public final class InstanceOfNode extends TypeCheckNode implements Canonicalizable, LIRLowerable {

    @Data private final boolean negated;

    public boolean negated() {
        return negated;
    }

    /**
     * Constructs a new InstanceOfNode.
     *
     * @param targetClassInstruction the instruction which produces the target class of the instanceof check
     * @param targetClass the class which is the target of the instanceof check
     * @param object the instruction producing the object input to this instruction
     */
    public InstanceOfNode(ValueNode targetClassInstruction, RiResolvedType targetClass, ValueNode object, boolean negated) {
        this(targetClassInstruction, targetClass, object, EMPTY_HINTS, false, negated);
    }

    public InstanceOfNode(ValueNode targetClassInstruction, RiResolvedType targetClass, ValueNode object, RiResolvedType[] hints, boolean hintsExact, boolean negated) {
        super(targetClassInstruction, targetClass, object, hints, hintsExact, StampFactory.illegal());
        this.negated = negated;
        assert targetClass != null;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        RiResolvedType exact = object().exactType();

        if (exact == null) {
            if (object().declaredType() != null) {
                if (Modifier.isFinal(object().declaredType().accessFlags()) || object().declaredType().isArrayClass()) {
                    exact = object().declaredType();
                } else if (tool.assumptions() != null) {
                    exact = object().declaredType().uniqueConcreteSubtype();
                    if (exact != null) {
                        tool.assumptions().recordConcreteSubtype(object().declaredType(), exact);
                    }
                }
            }
        }

        if (exact != null) {
            boolean result = exact.isSubtypeOf(targetClass());
            if (result != negated) {
                // The instanceof check reduces to a null check.
                return graph().unique(new NullCheckNode(object(), false));
            } else {
                // The instanceof check can never succeed.
                return ConstantNode.forBoolean(false, graph());
            }
        }
        CiConstant constant = object().asConstant();
        if (constant != null) {
            assert constant.kind == CiKind.Object;
            if (constant.isNull()) {
                return ConstantNode.forBoolean(negated, graph());
            } else {
                assert false : "non-null constants are always expected to provide an exactType";
            }
        }
        if (tool.assumptions() != null && hints() != null && targetClass() != null) {
            if (!hintsExact() && hints().length == 1 && hints()[0] == targetClass().uniqueConcreteSubtype()) {
                tool.assumptions().recordConcreteSubtype(targetClass(), hints()[0]);
                return graph().unique(new InstanceOfNode(targetClassInstruction(), targetClass(), object(), hints(), true, negated));
            }
        }
        return this;
    }

    @Override
    public BooleanNode negate() {
        return graph().unique(new InstanceOfNode(targetClassInstruction(), targetClass(), object(), hints(), hintsExact(), !negated));
    }
}
