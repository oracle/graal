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

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;

/**
 * A field type flow that only tracks field types, i.e., it doesn't track any context-sensitive
 * information.
 */
public class ContextInsensitiveFieldTypeFlow extends FieldTypeFlow {

    public ContextInsensitiveFieldTypeFlow(AnalysisField field, AnalysisType type) {
        super(field, type);
    }

    public ContextInsensitiveFieldTypeFlow(AnalysisField field, AnalysisType type, AnalysisObject object) {
        super(field, type, object);
    }

    @Override
    public boolean addState(PointsToAnalysis bb, TypeState add) {
        /*
         * Strip the context sensitivity from the input state to avoid marking any context-sensitive
         * objects as merged. The field sink type flow collects field concrete types, but it doesn't
         * need to track individual context-sensitive objects.
         */
        return super.addState(bb, bb.analysisPolicy().forContextInsensitiveTypeState(bb, add));
    }

    @Override
    public String toString() {
        return "ContextInsensitiveFieldTypeFlow<" + source.format("%h.%n") + System.lineSeparator() + getStateDescription() + ">";
    }

}
