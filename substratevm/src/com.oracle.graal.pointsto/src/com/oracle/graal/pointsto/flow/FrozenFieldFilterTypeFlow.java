/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.typestate.TypeState;

/**
 * The frozen field filter flow is used for unsafe writes to frozen type state fields. An unsafe
 * write to an object can write to any of the unsafe accessed fields of that object, however the
 * written values may not be compatible with all the fields. Thus the written values need to be
 * filtered using the field FrozenFieldFilterTypeFlow type. The FrozenFieldFilterTypeFlow is a
 * simplified version of FilterTypeFlow in that it is 'not-exact', i.e., it allows types of that are
 * sub-types of the field declared type, it is always 'assignable', i.e., it allows types that are
 * assignable to the field declared type, and it 'includes-null', i.e., it allows null values to
 * pass through. However it's 'source' is an AnalysisField and not a ValueNode, thus it's a
 * completely different class. The difference between this and The FieldFilterTypeFlow is that this
 * one filters on the types reachable through normal writes only and not by the declared type.
 */
public class FrozenFieldFilterTypeFlow extends TypeFlow<AnalysisField> {

    private final UnsafeWriteSinkTypeFlow unsafeSink;

    public FrozenFieldFilterTypeFlow(PointsToAnalysis bb, AnalysisField field, UnsafeWriteSinkTypeFlow unsafeSink) {
        super(field, field.getType());
        this.unsafeSink = unsafeSink;
        this.source.getInstanceFieldFlow().addObserver(bb, this);
    }

    @Override
    public TypeState filter(PointsToAnalysis bb, TypeState update) {
        /*
         * Filter using the current type state of the field, i.e., the type state constructed
         * through normal writes and non frozen unsafe writes, if any.
         */
        TypeState fieldState = this.source.getInstanceFieldFlow().getState();
        /*
         * Strip the context, if any, of the field state.
         */
        TypeState filter = TypeState.forContextInsensitiveTypeState(bb, fieldState);
        return TypeState.forIntersection(bb, update, filter);
    }

    @Override
    public void onObservedUpdate(PointsToAnalysis bb) {
        /*
         * The field type state has changed. Since the type states can only increase, the new filter
         * will be more coarse, i.e., will let more objects go through. Push the unsafe writes,
         * collected in the sink, through the new filter.
         */
        addState(bb, unsafeSink.getState());
    }

    @Override
    public String toString() {
        return "FrozenFieldFilterTypeFlow<" + source + ">";
    }
}
