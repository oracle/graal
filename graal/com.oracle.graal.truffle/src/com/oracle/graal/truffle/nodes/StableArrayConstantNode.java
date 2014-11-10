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
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;

/**
 * The {@code StableArrayConstantNode} represents a stable or compilation final array constant.
 */
@NodeInfo(shortName = "StableConst", nameTemplate = "StableConst({p#rawvalue})")
public class StableArrayConstantNode extends ConstantNode {
    protected final int stableDimensions;
    protected final boolean compilationFinal;

    /**
     * Constructs a new node representing the specified stable array constant.
     *
     * @param value the constant
     * @param stableDimensions number of array dimensions that are to be considered as stable
     * @param compilationFinal if {@code true}, default values are considered constant as well
     */
    public static StableArrayConstantNode create(JavaConstant value, Stamp stamp, int stableDimensions, boolean compilationFinal) {
        return new StableArrayConstantNode(value, stamp, stableDimensions, compilationFinal);
    }

    protected StableArrayConstantNode(JavaConstant value, Stamp stamp, int stableDimensions, boolean compilationFinal) {
        super(value, stamp);
        assert value.getKind() == Kind.Object && value.isNonNull();
        assert stableDimensions <= 255;
        this.stableDimensions = stableDimensions;
        this.compilationFinal = compilationFinal;
    }

    public static ConstantNode forStableArrayConstant(JavaConstant constant, int stableDimensions, boolean compilationFinal, MetaAccessProvider metaAccess, StructuredGraph graph) {
        return graph.unique(forStableArrayConstant(constant, stableDimensions, compilationFinal, metaAccess));
    }

    public static ConstantNode forStableArrayConstant(JavaConstant constant, int stableDimensions, boolean compilationFinal, MetaAccessProvider metaAccess) {
        if (constant.getKind() == Kind.Object && constant.isNonNull()) {
            Stamp stamp = StampFactory.forConstant(constant, metaAccess);
            assert stamp.javaType(metaAccess).isArray();
            return StableArrayConstantNode.create(constant, stamp, stableDimensions, compilationFinal);
        } else {
            throw new IllegalArgumentException(constant.toString());
        }
    }

    public int getStableDimensions() {
        return stableDimensions;
    }

    public boolean isCompilationFinal() {
        return compilationFinal;
    }
}
