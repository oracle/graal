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

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code LoadFieldNode} represents a read of a static or instance field.
 */
public final class LoadFieldNode extends AccessFieldNode implements Canonicalizable, LIRLowerable, Node.IterableNodeType {

    /**
     * Creates a new LoadFieldNode instance.
     *
     * @param object the receiver object
     * @param field the compiler interface field
     */
    public LoadFieldNode(ValueNode object, RiResolvedField field) {
        super(createStamp(field), object, field);
    }

    private static Stamp createStamp(RiResolvedField field) {
        CiKind kind = field.kind(false);
        if (kind == CiKind.Object && field.type() instanceof RiResolvedType) {
            RiResolvedType resolvedType = (RiResolvedType) field.type();
            return StampFactory.declared(resolvedType);
        } else {
            return StampFactory.forKind(kind);
        }
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.visitLoadField(this);
    }

    @Override
    public boolean needsStateAfter() {
        return false;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        CiConstant constant = null;
        if (isStatic()) {
            constant = field().constantValue(null);
        } else if (object().isConstant()) {
            constant = field().constantValue(object().asConstant());
        }
        if (constant != null) {
            return ConstantNode.forCiConstant(constant, tool.runtime(), graph());
        }
        return this;
    }

    /**
     * Gets a constant value to which this load can be reduced.
     *
     * @return {@code null} if this load cannot be reduced to a constant
     */
    private CiConstant constantValue() {
        if (isStatic()) {
            return field.constantValue(null);
        } else if (object().isConstant()) {
            return field.constantValue(object().asConstant());
        }
        return null;
    }
}
