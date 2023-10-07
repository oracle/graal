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

import java.util.zip.ZipOutputStream;

public class CausalityExport {
    protected CausalityExport() {
    }

    public static synchronized void dump(PointsToAnalysis bb, ZipOutputStream zip, boolean exportTypeflowNames) throws java.io.IOException {
        Graph g = get().createCausalityGraph(bb);
        g.export(bb, zip, exportTypeflowNames);
    }

    protected static AbstractImpl get() {
        return CausalityExportActivation.get();
    }

    public static class AbstractImpl {
        protected void addVirtualInvokeTypeFlow(AbstractVirtualInvokeTypeFlow invocation) {}

        protected void registerVirtualInvocation(PointsToAnalysis bb, AbstractVirtualInvokeTypeFlow invocation, AnalysisMethod concreteTargetMethod, AnalysisType concreteTargetType) {}

        protected void registerTypeFlowEdge(TypeFlow<?> from, TypeFlow<?> to) {}

        protected NonThrowingAutoCloseable setSaturationHappening() {
            return null;
        }

        protected void registerEdge(CausalityEvent cause, CausalityEvent consequence) {}

        protected void registerConjunctiveEdge(CausalityEvent cause1, CausalityEvent cause2, CausalityEvent consequence) {}

        protected void registerEdgeFromHeapObject(BigBang bb, JavaConstant heapObject, ObjectScanner.ScanReason reason, CausalityEvent consequence) {}

        protected void registerEdgeFromHeapObject(Object heapObject, ObjectScanner.ScanReason reason, CausalityEvent consequence) {}

        protected CausalityEvent getHeapFieldAssigner(BigBang analysis, JavaConstant receiver, AnalysisField field, JavaConstant value) {
            return null;
        }

        protected CausalityEvent getHeapArrayAssigner(BigBang analysis, JavaConstant array, int elementIndex, JavaConstant value) {
            return null;
        }

        protected void registerTypeEntering(PointsToAnalysis bb, CausalityEvent cause, TypeFlow<?> destination, AnalysisType type) {}

        protected void registerObjectReplacement(Object source, Object destination) {}

        protected NonThrowingAutoCloseable setCause(CausalityEvent event, CausalityExport.HeapTracing level, boolean overwriteSilently) {
            return null;
        }

        protected CausalityEvent getCause() {
            return null;
        }

        protected Graph createCausalityGraph(PointsToAnalysis bb) {
            throw new UnsupportedOperationException();
        }
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