/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.graal.pointsto.flow;

import org.graalvm.compiler.nodes.ValueNode;
import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;

public class FilterTypeFlow extends TypeFlow<ValueNode> {

    private final AnalysisType type;
    /**
     * If the filter is exact we only compare with the {@link #type}, not including its instantiated
     * sub-types, otherwise we compare with the entire type hierarchy rooted at {@link #type}.
     */
    private final boolean isExact;
    private final boolean isAssignable;
    private final boolean includeNull;

    public FilterTypeFlow(ValueNode node, AnalysisType type, boolean isAssignable, boolean includeNull) {
        this(node, type, false, isAssignable, includeNull);
    }

    public FilterTypeFlow(ValueNode node, AnalysisType type, boolean isExact, boolean isAssignable, boolean includeNull) {
        super(node, null);
        this.type = type;
        this.isExact = isExact;
        this.isAssignable = isAssignable;
        this.includeNull = includeNull;
    }

    public FilterTypeFlow(MethodFlowsGraph methodFlows, FilterTypeFlow original) {
        super(original, methodFlows);
        this.type = original.type;
        this.isExact = original.isExact;
        this.isAssignable = original.isAssignable;
        this.includeNull = original.includeNull;
    }

    @Override
    public TypeFlow<ValueNode> copy(BigBang bb, MethodFlowsGraph methodFlows) {
        return new FilterTypeFlow(methodFlows, this);
    }

    @Override
    public TypeState filter(BigBang bb, TypeState update) {
        if (update.isUnknown()) {
            // Filtering UnknownTypeState would otherwise return EmptyTypeState.
            AnalysisMethod method = (AnalysisMethod) source.graph().method();
            bb.reportIllegalUnknownUse(method, source, "Illegal: Filter of UnknownTypeState objects.");
            return TypeState.forEmpty();
        }

        TypeState result;
        if (isExact) {
            /*
             * If the filter is exact we only check the update state against the exact type, and not
             * its entire hierarchy.
             */
            if (isAssignable) {
                result = TypeState.forIntersection(bb, update, TypeState.forExactType(bb, type, includeNull));
            } else {
                result = TypeState.forSubtraction(bb, update, TypeState.forExactType(bb, type, !includeNull));
            }
        } else {
            /*
             * If the filter is not exact we check the update state against the entire hierarchy,
             * not only the exact type (AnalysisType.getTypeFlow() returns the type plus all its
             * instantiated sub-types).
             */
            if (isAssignable) {
                result = TypeState.forIntersection(bb, update, type.getTypeFlow(bb, includeNull).getState());
            } else {
                result = TypeState.forSubtraction(bb, update, type.getTypeFlow(bb, !includeNull).getState());
            }
        }

        return result;
    }

    @Override
    public boolean addState(BigBang bb, TypeState add) {
        assert this.isClone();
        return super.addState(bb, add);
    }

    public AnalysisType getType() {
        return type;
    }

    public boolean isExact() {
        return isExact;
    }

    public boolean isAssignable() {
        return isAssignable;
    }

    public boolean includeNull() {
        return includeNull;
    }

    @Override
    public String toString() {
        return "FilterTypeFlow<" + type + ", isAssignable: " + isAssignable + ", includeNull: " + includeNull + ">";
    }
}
