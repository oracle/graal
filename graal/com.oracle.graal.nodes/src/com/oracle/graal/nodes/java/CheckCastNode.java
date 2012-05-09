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

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.spi.types.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;

/**
 * Implements a type check that results in a {@link ClassCastException} if it fails.
 */
public final class CheckCastNode extends FixedWithNextNode implements Canonicalizable, LIRLowerable, Node.IterableNodeType, TypeFeedbackProvider, TypeCanonicalizable {

    @Input private ValueNode object;
    @Input private ValueNode targetClassInstruction;
    private final RiResolvedType targetClass;
    private final RiTypeProfile profile;

    /**
     * Creates a new CheckCast instruction.
     * @param targetClassInstruction the instruction which produces the class which is being cast to
     * @param targetClass the class being cast to
     * @param object the instruction producing the object
     */
    public CheckCastNode(ValueNode targetClassInstruction, RiResolvedType targetClass, ValueNode object) {
        this(targetClassInstruction, targetClass, object, null);
    }

    public CheckCastNode(ValueNode targetClassInstruction, RiResolvedType targetClass, ValueNode object, RiTypeProfile profile) {
        super(targetClass == null ? StampFactory.forKind(CiKind.Object) : StampFactory.declared(targetClass));
        this.targetClassInstruction = targetClassInstruction;
        this.targetClass = targetClass;
        this.object = object;
        this.profile = profile;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.visitCheckCast(this);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        assert object() != null : this;

        RiResolvedType objectDeclaredType = object().declaredType();
        if (objectDeclaredType != null && targetClass != null && objectDeclaredType.isSubtypeOf(targetClass)) {
            // we don't have to check for null types here because they will also pass the checkcast.
            return object();
        }

        CiConstant constant = object().asConstant();
        if (constant != null) {
            assert constant.kind == CiKind.Object;
            if (constant.isNull()) {
                return object();
            }
        }
        return this;
    }

    @Override
    public void typeFeedback(TypeFeedbackTool tool) {
        if (targetClass() != null) {
            tool.addObject(object()).declaredType(targetClass(), false);
        }
    }

    @Override
    public Result canonical(TypeFeedbackTool tool) {
        ObjectTypeQuery query = tool.queryObject(object());
        if (query.constantBound(Condition.EQ, CiConstant.NULL_OBJECT)) {
            return new Result(object(), query);
        } else if (targetClass() != null) {
            if (query.declaredType(targetClass())) {
                return new Result(object(), query);
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
     * This may be null in the case where the type being tested is dynamically loaded such as
     * when checking an object array store.
     *
     * @return the target class or null if not known
     */
    public RiResolvedType targetClass() {
        return targetClass;
    }

    public RiTypeProfile profile() {
        return profile;
    }
}
