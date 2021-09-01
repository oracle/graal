/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.typestore;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.FieldTypeFlow;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisField;

/**
 * Store for instance field access type flows. The read and write flows are separated.
 */
public class SplitFieldTypeStore extends FieldTypeStore {

    /** The holder of the field flow (null for static fields). */
    private final FieldTypeFlow writeFlow;
    private final FieldTypeFlow readFlow;

    public SplitFieldTypeStore(AnalysisField field, AnalysisObject object) {
        super(field, object);
        this.writeFlow = new FieldTypeFlow(field, field.getType(), object);
        this.readFlow = new FieldTypeFlow(field, field.getType(), object);
    }

    @Override
    public FieldTypeFlow writeFlow() {
        return writeFlow;
    }

    @Override
    public FieldTypeFlow readFlow() {
        return readFlow;
    }

    @Override
    public void init(PointsToAnalysis bb) {
        /*
         * For split field stores we link the write flows to the read flows, such that all the
         * stores in the write partition can be loaded from the read partition. Done lazily because
         * these type stores are created concurrently.
         */
        writeFlow.addUse(bb, readFlow);
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("SplitFieldStore<").append(field.format("%h.%n")).append("\n").append(object).append(">");
        return str.toString();
    }

}
