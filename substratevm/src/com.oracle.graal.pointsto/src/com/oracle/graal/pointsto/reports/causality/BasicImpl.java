package com.oracle.graal.pointsto.reports.causality;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.flow.AbstractVirtualInvokeTypeFlow;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapInstance;
import com.oracle.graal.pointsto.heap.ImageHeapObjectArray;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.reports.causality.events.BuildTimeClassInitialization;
import com.oracle.graal.pointsto.reports.causality.events.CausalityEvent;
import com.oracle.graal.pointsto.reports.causality.events.InlinedMethodCode;
import com.oracle.graal.pointsto.util.AnalysisError;
import jdk.vm.ci.meta.JavaConstant;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.oracle.graal.pointsto.reports.causality.events.CausalityEvents.*;
import static com.oracle.graal.pointsto.reports.causality.CausalityExport.*;

class BasicImpl<TContext extends BasicImpl.ThreadContext> extends CausalityImplementation {
    private final ConcurrentHashMap<Graph.DirectEdge, Boolean> direct_edges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Graph.HyperEdge, Boolean> hyper_edges = new ConcurrentHashMap<>();
    private final Map<Object, Object> originsOfReplacedObjects = Collections.synchronizedMap(new IdentityHashMap<>());

    private final ThreadLocal<TContext> threadContexts;

    protected TContext getContext() {
        return threadContexts.get();
    }

    public static class ThreadContext {
        private final Stack<CauseToken> causes = new Stack<>();

        public CausalityEvent topCause() {
            return causes.empty() ? null : causes.peek().event;
        }

        public CauseToken topCauseToken() {
            return causes.empty() ? null : causes.peek();
        }

        private void updateHeapTracing(CauseToken top) {
            CausalityEvent cause = top == null || top.level == HeapTracing.None ? null : top.event;
            boolean recordHeapAssignments = top != null && top.level == HeapTracing.Full;
            HeapAssignmentTracing.getInstance().setCause(cause, recordHeapAssignments);
        }

        public final class CauseToken implements NonThrowingAutoCloseable {
            public final CausalityEvent event;
            public final HeapTracing level;

            public CauseToken(CausalityEvent event, HeapTracing level, boolean overwriteSilently) {
                this.event = event;
                this.level = level;

                if(!overwriteSilently && !causes.empty() && causes.peek().event != null && causes.peek().event != event && event != Ignored && causes.peek().event != Ignored && !(causes.peek().event instanceof com.oracle.graal.pointsto.reports.causality.events.Feature) && !causes.peek().event.root())
                    throw new RuntimeException("Stacking Rerooting requests!");
                causes.push(this);
                updateHeapTracing(this);
            }

            @Override
            public void close() {
                if(causes.empty() || causes.pop() != this) {
                    throw new RuntimeException("Invalid Call to endAccountingRootRegistrationsTo()");
                }
                updateHeapTracing(topCauseToken());
            }
        }
    }

    protected BasicImpl(Supplier<TContext> contextSupplier) {
        threadContexts = ThreadLocal.withInitial(contextSupplier);
    }

    public static BasicImpl<ThreadContext> create() {
        return new BasicImpl<>(ThreadContext::new);
    }

    private static <T> T asObject(BigBang bb, Class<T> tClass, JavaConstant constant) {
        return bb.getSnippetReflectionProvider().asObject(tClass, constant);
    }

    @Override
    public void registerConjunctiveEdge(CausalityEvent cause1, CausalityEvent cause2, CausalityEvent consequence) {
        if(cause1 == null) {
            registerEdge(cause2, consequence);
        } else if(cause2 == null) {
            registerEdge(cause1, consequence);
        } else {
            hyper_edges.put(new Graph.HyperEdge(cause1, cause2, consequence), Boolean.TRUE);
        }
    }

    @Override
    public void registerEdge(CausalityEvent cause, CausalityEvent consequence) {
        if (cause == null || cause.root()) {
            CausalityEvent topCause = threadContexts.get().topCause();
            if (topCause != null) {
                cause = topCause;
            }
        }
        direct_edges.put(new Graph.DirectEdge(cause, consequence), Boolean.TRUE);
    }

    @Override
    public void registerVirtualInvocation(PointsToAnalysis bb, AbstractVirtualInvokeTypeFlow invocation, AnalysisMethod concreteTargetMethod, AnalysisType concreteTargetType) {
        AnalysisMethod callingMethod = invocation.method();

        if(callingMethod == null && invocation.getTargetMethod().getContextInsensitiveVirtualInvoke(invocation.getCallerMultiMethodKey()) != invocation)
            throw new RuntimeException("CausalityExport has made an invalid assumption!");

        CausalityEvent callerEvent = callingMethod != null
                ? InlinedMethodCode.create(callingMethod) /* TODO: Take inlining into account */
                : RootMethodRegistration.create(invocation.getTargetMethod());

        registerEdge(
                callerEvent,
                VirtualMethodInvoked.create(invocation.getTargetMethod()));
        registerConjunctiveEdge(
                VirtualMethodInvoked.create(invocation.getTargetMethod()),
                TypeInstantiated.create(concreteTargetType),
                MethodImplementationInvoked.create(concreteTargetMethod)
        );
    }

    private static CausalityEvent getEventForHeapReason(Object customReason, Object o) {
        if (customReason == null) {
            return UnknownHeapObject.create(o.getClass());
        } else if (customReason instanceof CausalityEvent) {
            return (CausalityEvent) customReason;
        } else if (customReason instanceof Class<?>) {
            return BuildTimeClassInitialization.create((Class<?>) customReason);
        } else {
            throw AnalysisError.shouldNotReachHere("Heap Assignment Tracing Reason should not be of type " + customReason.getClass().getTypeName());
        }
    }

    private static CausalityEvent getHeapObjectCreator(Object heapObject) {
        Object responsible = HeapAssignmentTracing.getInstance().getResponsibleClass(heapObject);
        return getEventForHeapReason(responsible, heapObject);
    }

    private static CausalityEvent getHeapObjectCreator(BigBang bb, JavaConstant heapObject) {
        if (heapObject instanceof ImageHeapConstant imageHeapConstant && !imageHeapConstant.isBackedByHostedObject()) {
            return SimulatedHeapTracing.instance.getHeapObjectCreator(imageHeapConstant);
        }
        return getHeapObjectCreator(asObject(bb, Object.class, heapObject));
    }

    private static CausalityEvent forScanReason(ObjectScanner.ScanReason reason) {
        if (reason instanceof ObjectScanner.EmbeddedRootScan ers) {
            return InlinedMethodCode.create(ers.getPosition());
        }
        if (reason instanceof ObjectScanner.FieldScan fs) {
            return FieldRead.create(fs.getField());
        }
        return null;
    }

    @Override
    public void registerEdgeFromHeapObject(BigBang bb, JavaConstant heapObject, ObjectScanner.ScanReason reason, CausalityEvent consequence) {
        CausalityEvent writerCause = getHeapObjectCreator(bb, heapObject);
        CausalityEvent readerCause = forScanReason(reason);
        registerConjunctiveEdge(writerCause, readerCause, consequence);
    }

    @Override
    public void registerEdgeFromHeapObject(Object heapObject, ObjectScanner.ScanReason reason, CausalityEvent consequence) {
        CausalityEvent writerCause = getHeapObjectCreator(heapObject);
        CausalityEvent readerCause = forScanReason(reason);
        registerConjunctiveEdge(writerCause, readerCause, consequence);
    }

    @Override
    public CausalityEvent getHeapFieldAssigner(BigBang bb, JavaConstant receiver, AnalysisField field, JavaConstant value) {
        Object responsible;
        Object o;

        if (field.isStatic()) {
            if (value instanceof ImageHeapConstant imageHeapConstant && !imageHeapConstant.isBackedByHostedObject()) {
                return SimulatedHeapTracing.instance.getHeapFieldAssigner(field, imageHeapConstant);
            } else {
                o = asObject(bb, Object.class, value);
                Object original = originsOfReplacedObjects.getOrDefault(o, o);
                java.lang.reflect.Field f = field.getJavaField();
                Class<?> declaringClass = f.getDeclaringClass();
                responsible = HeapAssignmentTracing.getInstance().getClassResponsibleForStaticFieldWrite(declaringClass, f, original);
            }
        } else {
            if (receiver instanceof ImageHeapInstance imageHeapConstant && !imageHeapConstant.isBackedByHostedObject()) {
                return SimulatedHeapTracing.instance.getHeapFieldAssigner(imageHeapConstant, field, value);
            } else {
                Object receiverO = asObject(bb, Object.class, receiver);
                receiverO = originsOfReplacedObjects.getOrDefault(receiverO, receiverO);
                o = asObject(bb, Object.class, value);
                Object original = originsOfReplacedObjects.getOrDefault(o, o);

                java.lang.reflect.Field f = field.getJavaField();
                if (f.getDeclaringClass().isAssignableFrom(receiverO.getClass())) {
                    responsible = HeapAssignmentTracing.getInstance().getClassResponsibleForNonstaticFieldWrite(receiverO, f, original);
                } else {
                    // Field must be substituted or recomputed
                    responsible = null;
                }
            }
        }

        return getEventForHeapReason(responsible, o);
    }

    @Override
    public CausalityEvent getHeapArrayAssigner(BigBang bb, JavaConstant array, int elementIndex, JavaConstant value) {
        if (array instanceof ImageHeapObjectArray imageHeapArray && !imageHeapArray.isBackedByHostedObject()) {
            return SimulatedHeapTracing.instance.getHeapArrayAssigner(imageHeapArray, elementIndex, value);
        }
        Object o = asObject(bb, Object.class, value);
        Object responsible = HeapAssignmentTracing.getInstance().getClassResponsibleForArrayWrite(asObject(bb, Object[].class, array), elementIndex, o);
        return getEventForHeapReason(responsible, o);
    }

    @Override
    protected void registerObjectReplacement(Object source, Object destination) {
        if (destination != source) {
            originsOfReplacedObjects.put(destination, source);
        }
    }

    @Override
    public CausalityEvent getCause() {
        return threadContexts.get().topCause();
    }

    @Override
    protected NonThrowingAutoCloseable setCause(CausalityEvent event, HeapTracing level, boolean overwriteSilently) {
        return threadContexts.get().new CauseToken(event, level, overwriteSilently);
    }

    protected void forEachEvent(Consumer<CausalityEvent> callback) {
        for (var e : direct_edges.keySet()) {
            callback.accept(e.from);
            callback.accept(e.to);
        }
        for (var he : hyper_edges.keySet()) {
            callback.accept(he.from1);
            callback.accept(he.from2);
            callback.accept(he.to);
        }
    }

    @Override
    protected Graph createCausalityGraph(PointsToAnalysis bb) {
        Graph g = new Graph();

        var direct_edges = this.direct_edges.keySet();
        var hyper_edges = this.hyper_edges.keySet();

        direct_edges.removeIf(pair -> pair.from != null && pair.from.unused() || pair.to.unused());
        direct_edges.removeIf(pair -> pair.to instanceof com.oracle.graal.pointsto.reports.causality.events.MethodReachable mr && mr.element.isClassInitializer());

        HashSet<CausalityEvent> rootEvents = new HashSet<>();
        Set<com.oracle.graal.pointsto.reports.causality.events.BuildTimeClassInitialization> initialBuildTimeClinits = new HashSet<>();
        HashSet<com.oracle.graal.pointsto.reports.causality.events.InlinedMethodCode> allCodeEvents = new HashSet<>();
        forEachEvent(e -> {
            if (e != null && e.root() && !e.unused()) {
                rootEvents.add(e);
            }
            if (e instanceof BuildTimeClassInitialization clinit) {
                initialBuildTimeClinits.add(clinit);
            }
            if (e instanceof InlinedMethodCode imc) {
                allCodeEvents.add(imc);
            }
        });
        rootEvents.forEach(e -> g.add(new Graph.DirectEdge(null, e)));

        for (var e : direct_edges) {
            g.add(e);
        }

        for (AnalysisType t : bb.getUniverse().getTypes()) {
            if (!t.isReachable())
                continue;

            AnalysisMethod classInitializer = t.getClassInitializer();
            if(classInitializer != null && classInitializer.isImplementationInvoked()) {
                g.add(new Graph.DirectEdge(TypeReachable.create(t), MethodReachable.create(classInitializer)));
            }
        }

        {
            Set<BuildTimeClassInitialization> visitedBuildTimeClinits = new HashSet<>();
            Set<BuildTimeClassInitialization> buildTimeClinitsWithReason = new HashSet<>();

            for (BuildTimeClassInitialization init : initialBuildTimeClinits) {
                while (visitedBuildTimeClinits.add(init)) {
                    Object outerInitReason = HeapAssignmentTracing.getInstance().getBuildTimeClinitResponsibleForBuildTimeClinit(init.clazz);
                    if (outerInitReason == null)
                        break;
                    buildTimeClinitsWithReason.add(init);
                    if (outerInitReason instanceof Class<?> outerInitClass) {
                        BuildTimeClassInitialization outerInit = (BuildTimeClassInitialization) BuildTimeClassInitialization.create(outerInitClass);
                        g.add(new Graph.DirectEdge(outerInit, init));
                        init = outerInit;
                    } else {
                        g.add(new Graph.DirectEdge((CausalityEvent) outerInitReason, init));
                        break;
                    }
                }
            }

            for (var e : direct_edges) {
                if (e.to instanceof BuildTimeClassInitialization clinit) {
                    buildTimeClinitsWithReason.add(clinit);
                }
            }

            visitedBuildTimeClinits.stream().sorted(Comparator.comparing(init -> init.clazz.getTypeName())).forEach(init -> {
                AnalysisType t;
                try {
                    t = bb.getMetaAccess().optionalLookupJavaType(init.clazz).orElse(null);
                } catch (UnsupportedFeatureException ex) {
                    t = null;
                }

                if (t != null && t.isReachable()) {
                    CausalityEvent tReachable = TypeReachable.create(t);
                    g.add(new Graph.DirectEdge(tReachable, init));
                } else if(!buildTimeClinitsWithReason.contains(init)) {
                    g.add(new Graph.DirectEdge(null, init));
                }
            });
        }

        for (var e : allCodeEvents) {
            g.add(new Graph.DirectEdge(e, MethodGraphParsed.create(e.context[0])));
        }

        for(Graph.HyperEdge andEdge : hyper_edges) {
            if(andEdge.from1.unused() || andEdge.from2.unused() || andEdge.to.unused())
                continue;

            g.add(andEdge);
        }

        this.direct_edges.clear();
        this.hyper_edges.clear();

        return g;
    }
}
