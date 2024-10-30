/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.reports.causality;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.AbstractVirtualInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.reports.causality.facts.Fact;
import jdk.vm.ci.meta.JavaConstant;

public class CausalityImplementation {
    protected void addVirtualInvokeTypeFlow(AbstractVirtualInvokeTypeFlow invocation) {
    }

    protected void registerVirtualInvocation(PointsToAnalysis bb, AbstractVirtualInvokeTypeFlow invocation, AnalysisMethod concreteTargetMethod, AnalysisType concreteTargetType) {
    }

    protected void registerTypeFlowEdge(TypeFlow<?> from, TypeFlow<?> to) {
    }

    protected Causality.NonThrowingAutoCloseable setSaturationHappening() {
        return null;
    }

    protected void registerEdge(Fact cause, Fact consequence) {
    }

    protected void registerConjunctiveEdge(Fact cause1, Fact cause2, Fact consequence) {
    }

    protected void registerEdgeFromHeapObject(BigBang bb, JavaConstant heapObject, ObjectScanner.ScanReason reason, Fact consequence) {
    }

    protected void registerEdgeFromHeapObject(Object heapObject, ObjectScanner.ScanReason reason, Fact consequence) {
    }

    protected Fact getHeapFieldAssigner(BigBang analysis, JavaConstant receiver, AnalysisField field, JavaConstant value) {
        return null;
    }

    protected Fact getHeapArrayAssigner(BigBang analysis, JavaConstant array, int elementIndex, JavaConstant value) {
        return null;
    }

    protected void registerTypeEntering(PointsToAnalysis bb, Fact cause, TypeFlow<?> destination, AnalysisType type) {
    }

    protected Causality.NonThrowingAutoCloseable setCause(Fact fact, boolean overwriteSilently) {
        return null;
    }

    protected Fact getCause() {
        return null;
    }

    protected Graph createCausalityGraph(PointsToAnalysis bb) {
        throw new UnsupportedOperationException();
    }
}
