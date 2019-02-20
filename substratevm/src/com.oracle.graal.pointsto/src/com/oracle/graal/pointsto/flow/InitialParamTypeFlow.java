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
package com.oracle.graal.pointsto.flow;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;

/**
 * Parameters for entry methods.
 */
public class InitialParamTypeFlow extends TypeFlow<AnalysisMethod> {

    /** The position of the parameter in the method signature. */
    protected final int position;

    public InitialParamTypeFlow(AnalysisMethod source, AnalysisType declaredType, int position) {
        super(source, declaredType);
        this.position = position;
    }

    @Override
    public AnalysisMethod method() {
        return super.getSource();
    }

    public int position() {
        return position;
    }

    @Override
    public TypeFlow<AnalysisMethod> copy(BigBang bb, MethodFlowsGraph methodFlows) {
        return this;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("InitialParamFlow").append("[").append(position).append("]<").append(getState()).append(">");
        return str.toString();
    }

}
