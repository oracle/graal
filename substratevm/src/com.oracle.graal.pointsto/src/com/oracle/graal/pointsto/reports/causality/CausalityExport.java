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
import com.oracle.graal.pointsto.reports.causality.events.CausalityEvent;
import com.oracle.graal.pointsto.util.AnalysisError;
import jdk.vm.ci.meta.JavaConstant;

import java.util.zip.ZipOutputStream;

public final class CausalityExport {
    private CausalityExport() { }

    public enum ActivationLevel {
        DISABLED,
        ENABLED_WITHOUT_TYPEFLOW,
        ENABLED
    }

    private static ActivationLevel requestedLevel = ActivationLevel.DISABLED;

    /**
     * Must be called before usage of {@link CausalityExport}.
     */
    public static void activate(ActivationLevel level) {
        requestedLevel = level;
        if (level != InitializationOnDemandHolder.frozenLevel) {
            throw AnalysisError.shouldNotReachHere("Causality Export must have been activated before the first usage of CausalityExport");
        }
    }

    private static final class InitializationOnDemandHolder {
        private static final ActivationLevel frozenLevel = requestedLevel;
        private static final CausalityImplementation instance = switch (frozenLevel) {
            case ENABLED -> TypeflowImpl.createWithTypeflowTracking();
            case ENABLED_WITHOUT_TYPEFLOW -> BasicImpl.create();
            case DISABLED -> new CausalityImplementation();
        };
    }

    public static boolean isEnabled() {
        return InitializationOnDemandHolder.frozenLevel != ActivationLevel.DISABLED;
    }

    public static synchronized void dump(PointsToAnalysis bb, ZipOutputStream zip, boolean exportTypeflowNames) throws java.io.IOException {
        Graph g = get().createCausalityGraph(bb);
        g.export(bb, zip, exportTypeflowNames);
    }

    private static CausalityImplementation get() {
        return InitializationOnDemandHolder.instance;
    }

    public enum HeapTracing {
        None,
        Allocations,
        Full
    }

    public static void addVirtualInvokeTypeFlow(AbstractVirtualInvokeTypeFlow invocation) {
        get().addVirtualInvokeTypeFlow(invocation);
    }

    public static void registerVirtualInvocation(PointsToAnalysis bb, AbstractVirtualInvokeTypeFlow invocation, AnalysisMethod concreteTargetMethod, AnalysisType concreteTargetType) {
        get().registerVirtualInvocation(bb, invocation, concreteTargetMethod, concreteTargetType);
    }

    public static void registerTypeFlowEdge(TypeFlow<?> from, TypeFlow<?> to) {
        get().registerTypeFlowEdge(from, to);
    }

    public static NonThrowingAutoCloseable setSaturationHappening() {
        return get().setSaturationHappening();
    }

    public static void registerEvent(CausalityEvent event) {
        registerEdge(null, event);
    }

    public static void registerEdge(CausalityEvent cause, CausalityEvent consequence) {
        get().registerEdge(cause, consequence);
    }

    public static void registerConjunctiveEdge(CausalityEvent cause1, CausalityEvent cause2, CausalityEvent consequence) {
        get().registerConjunctiveEdge(cause1, cause2, consequence);
    }

    public static void registerEdgeFromHeapObject(BigBang bb, JavaConstant heapObject, ObjectScanner.ScanReason reason, CausalityEvent consequence) {
        get().registerEdgeFromHeapObject(bb, heapObject, reason, consequence);
    }

    public static void registerEdgeFromHeapObject(Object heapObject, ObjectScanner.ScanReason reason, CausalityEvent consequence) {
        get().registerEdgeFromHeapObject(heapObject, reason, consequence);
    }

    public static CausalityEvent getHeapFieldAssigner(BigBang analysis, JavaConstant receiver, AnalysisField field, JavaConstant value) {
        return get().getHeapFieldAssigner(analysis, receiver, field, value);
    }

    public static CausalityEvent getHeapArrayAssigner(BigBang analysis, JavaConstant array, int elementIndex, JavaConstant value) {
        return get().getHeapArrayAssigner(analysis, array, elementIndex, value);
    }

    public static void registerTypeEntering(PointsToAnalysis bb, CausalityEvent cause, TypeFlow<?> destination, AnalysisType type) {
        get().registerTypeEntering(bb, cause, destination, type);
    }

    public static void registerObjectReplacement(Object source, Object destination) {
        get().registerObjectReplacement(source, destination);
    }

    public static NonThrowingAutoCloseable setCause(CausalityEvent event, HeapTracing level) {
        return get().setCause(event, level, false);
    }

    public static NonThrowingAutoCloseable setCause(CausalityEvent event) {
        return setCause(event, HeapTracing.None);
    }

    public static NonThrowingAutoCloseable overwriteCause(CausalityEvent event) {
        return get().setCause(event, HeapTracing.None, true);
    }

    public static NonThrowingAutoCloseable overwriteCause(CausalityEvent event, HeapTracing level) {
        return get().setCause(event, level, true);
    }

    public static NonThrowingAutoCloseable resetCause() {
        return overwriteCause(null);
    }

    public static CausalityEvent getCause() {
        return get().getCause();
    }

    // Allows the simple usage of accountRootRegistrationsTo() in a try-with-resources statement
    public interface NonThrowingAutoCloseable extends AutoCloseable {
        @Override
        void close();
    }
}
