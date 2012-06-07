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

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.spi.types.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.max.cri.ri.*;

/**
 * The {@code InstanceOfNode} represents an instanceof test.
 */
public final class InstanceOfNode extends BooleanNode implements Canonicalizable, LIRLowerable, ConditionalTypeFeedbackProvider, TypeCanonicalizable {

    @Input private ValueNode object;
    @Input private ValueNode targetClassInstruction;
    private final RiResolvedType targetClass;
    private final RiTypeProfile profile;

    /**
     * Constructs a new InstanceOfNode.
     *
     * @param targetClassInstruction the instruction which produces the target class of the instanceof check
     * @param targetClass the class which is the target of the instanceof check
     * @param object the instruction producing the object input to this instruction
     */
    public InstanceOfNode(ValueNode targetClassInstruction, RiResolvedType targetClass, ValueNode object) {
        this(targetClassInstruction, targetClass, object, null);
    }

    public InstanceOfNode(ValueNode targetClassInstruction, RiResolvedType targetClass, ValueNode object, RiTypeProfile profile) {
        super(StampFactory.condition());
        this.targetClassInstruction = targetClassInstruction;
        this.targetClass = targetClass;
        this.object = object;
        this.profile = profile;
        assert targetClass != null;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        assert object() != null : this;

        ObjectStamp stamp = object().objectStamp();
        RiResolvedType type = stamp.type();

        if (stamp.isExactType()) {
            boolean subType = type.isSubtypeOf(targetClass());

            if (subType) {
                if (stamp.nonNull()) {
                    // the instanceOf matches, so return true
                    return ConstantNode.forBoolean(true, graph());
                } else {
                    // the instanceof matches if the object is non-null, so return true depending on the null-ness.
                    negateUsages();
                    return graph().unique(new IsNullNode(object()));
                }
            } else {
                // since this type check failed for an exact type we know that it can never succeed at run time.
                // we also don't care about null values, since they will also make the check fail.
                return ConstantNode.forBoolean(false, graph());
            }
        } else if (type != null) {
            boolean subType = type.isSubtypeOf(targetClass());

            if (subType) {
                if (stamp.nonNull()) {
                    // the instanceOf matches, so return true
                    return ConstantNode.forBoolean(true, graph());
                } else {
                    // the instanceof matches if the object is non-null, so return true depending on the null-ness.
                    negateUsages();
                    return graph().unique(new IsNullNode(object()));
                }
            } else {
                // since the subtype comparison was only performed on a declared type we don't really know if it might be true at run time...
            }
        }

        RiConstant constant = object().asConstant();
        if (constant != null) {
            assert constant.kind == RiKind.Object;
            if (constant.isNull()) {
                return ConstantNode.forBoolean(false, graph());
            } else {
                assert false : "non-null constants are always expected to provide an exact type";
            }
        }
        return this;
    }

    @Override
    public void typeFeedback(TypeFeedbackTool tool) {
        tool.addObject(object()).declaredType(targetClass(), true);
    }

    @Override
    public Result canonical(TypeFeedbackTool tool) {
        ObjectTypeQuery query = tool.queryObject(object());
        if (query.constantBound(Condition.EQ, RiConstant.NULL_OBJECT)) {
            return new Result(ConstantNode.forBoolean(false, graph()), query);
        } else if (targetClass() != null) {
            if (query.notDeclaredType(targetClass())) {
                return new Result(ConstantNode.forBoolean(false, graph()), query);
            }
            if (query.constantBound(Condition.NE, RiConstant.NULL_OBJECT)) {
                if (query.declaredType(targetClass())) {
                    return new Result(ConstantNode.forBoolean(true, graph()), query);
                }
            }
        }
        return null;
    }

    public ValueNode object() {
        return object;
    }

    public ValueNode targetClassInstruction() {
        return targetClassInstruction;
    }

    /**
     * Gets the target class, i.e. the class being cast to, or the class being tested against.
     * @return the target class
     */
    public RiResolvedType targetClass() {
        return targetClass;
    }

    public RiTypeProfile profile() {
        return profile;
    }
}
