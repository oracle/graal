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
import jdk.vm.ci.meta.JavaConstant;

public class CausalityImplementation {
    protected void addVirtualInvokeTypeFlow(AbstractVirtualInvokeTypeFlow invocation) {
    }

    protected void registerVirtualInvocation(PointsToAnalysis bb, AbstractVirtualInvokeTypeFlow invocation, AnalysisMethod concreteTargetMethod, AnalysisType concreteTargetType) {
    }

    protected void registerTypeFlowEdge(TypeFlow<?> from, TypeFlow<?> to) {
    }

    protected CausalityExport.NonThrowingAutoCloseable setSaturationHappening() {
        return null;
    }

    protected void registerEdge(CausalityEvent cause, CausalityEvent consequence) {
    }

    protected void registerConjunctiveEdge(CausalityEvent cause1, CausalityEvent cause2, CausalityEvent consequence) {
    }

    protected void registerEdgeFromHeapObject(BigBang bb, JavaConstant heapObject, ObjectScanner.ScanReason reason, CausalityEvent consequence) {
    }

    protected void registerEdgeFromHeapObject(Object heapObject, ObjectScanner.ScanReason reason, CausalityEvent consequence) {
    }

    protected CausalityEvent getHeapFieldAssigner(BigBang analysis, JavaConstant receiver, AnalysisField field, JavaConstant value) {
        return null;
    }

    protected CausalityEvent getHeapArrayAssigner(BigBang analysis, JavaConstant array, int elementIndex, JavaConstant value) {
        return null;
    }

    protected void registerTypeEntering(PointsToAnalysis bb, CausalityEvent cause, TypeFlow<?> destination, AnalysisType type) {
    }

    protected void registerObjectReplacement(Object source, Object destination) {
    }

    protected CausalityExport.NonThrowingAutoCloseable setCause(CausalityEvent event, CausalityExport.HeapTracing level, boolean overwriteSilently) {
        return null;
    }

    protected CausalityEvent getCause() {
        return null;
    }

    protected Graph createCausalityGraph(PointsToAnalysis bb) {
        throw new UnsupportedOperationException();
    }
}
