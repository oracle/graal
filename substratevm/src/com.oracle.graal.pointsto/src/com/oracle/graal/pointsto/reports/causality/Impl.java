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
import com.oracle.graal.pointsto.reports.CausalityExport;
import com.oracle.graal.pointsto.reports.HeapAssignmentTracing;
import com.oracle.graal.pointsto.reports.SimulatedHeapTracing;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.ConcurrentLightHashMap;
import jdk.vm.ci.meta.JavaConstant;
import org.graalvm.collections.Pair;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class Impl extends CausalityExport {
    private static final ConcurrentHashMap<Event, Integer> uniqueEventIds = new ConcurrentHashMap<>();
    private static final AtomicInteger nextUniqueId = new AtomicInteger(1);

    private static int eventToId(Event e) {
        if (e == null)
            return 0;
        return uniqueEventIds.computeIfAbsent(e, unused -> nextUniqueId.getAndIncrement());
    }

    protected static class IntegerArrayList {
        protected int[] arr;
        protected int size = 0;

        public IntegerArrayList(int capacity) {
            arr = new int[capacity];
        }

        public void clear() {
            size = 0;
        }

        public void kill() {
            arr = null;
            size = 0;
        }

        public void addAll(IntegerArrayList other) {
            if (size + other.size > arr.length) {
                arr = Arrays.copyOf(arr, Math.max(size + other.size, arr.length * 2));
            }
            System.arraycopy(other.arr, 0, arr, size, other.size);
            size += other.size;
        }
    }

    private static class DirectEdgeList extends IntegerArrayList {
        public DirectEdgeList() {
            super(2 * 10);
        }

        public void add(int from, int to) {
            if (size >= arr.length) {
                arr = Arrays.copyOf(arr, arr.length * 2);
            }
            arr[size++] = from;
            arr[size++] = to;
        }

        public void add(Event from, Event to) {
            add(eventToId(from), eventToId(to));
        }

        public HashSet<Pair<Event, Event>> toSet(Event[] eventsById) {
            HashSet<Pair<Event, Event>> result = new HashSet<>();

            for (int i = 0; i < size; i += 2) {
                result.add(Pair.create(eventsById[arr[i]], eventsById[arr[i + 1]]));
            }

            return result;
        }
    }

    private static class HyperEdgeList extends IntegerArrayList {
        public HyperEdgeList() {
            super(3 * 10);
        }

        public void add(int from1, int from2, int to) {
            if (size + 3 > arr.length) {
                arr = Arrays.copyOf(arr, arr.length * 2);
            }
            arr[size++] = from1;
            arr[size++] = from2;
            arr[size++] = to;
        }

        public void add(Event from1, Event from2, Event to) {
            add(eventToId(from1), eventToId(from2), eventToId(to));
        }

        public HashSet<Graph.HyperEdge> toSet(Event[] eventsById) {
            HashSet<Graph.HyperEdge> result = new HashSet<>();

            for (int i = 0; i < size; i += 3) {
                result.add(new Graph.HyperEdge(eventsById[arr[i]], eventsById[arr[i + 1]], eventsById[arr[i + 2]]));
            }

            return result;
        }
    }

    private final DirectEdgeList direct_edges = new DirectEdgeList();
    private final HyperEdgeList hyper_edges = new HyperEdgeList();

    public Impl() {
    }

    public Impl(Iterable<? extends Impl> instances, PointsToAnalysis bb) {
        for (Impl i : instances) {
            direct_edges.addAll(i.direct_edges);
            hyper_edges.addAll(i.hyper_edges);
        }
    }

    private static <T> T asObject(BigBang bb, Class<T> tClass, JavaConstant constant) {
        return bb.getSnippetReflectionProvider().asObject(tClass, constant);
    }

    @Override
    public void registerConjunctiveEdge(Event cause1, Event cause2, Event consequence) {
        if(cause1 == null) {
            registerEdge(cause2, consequence);
        } else if(cause2 == null) {
            registerEdge(cause1, consequence);
        } else {
            hyper_edges.add(cause1, cause2, consequence);
        }
    }

    @Override
    public void registerEdge(Event cause, Event consequence) {
        if((cause == null || cause.root()) && !causes.empty()) {
            Event topCause = causes.peek().event;
            if (topCause != null) {
                cause = topCause;
            }
        }

        direct_edges.add(cause, consequence);
    }

    @Override
    public void registerVirtualInvocation(PointsToAnalysis bb, AbstractVirtualInvokeTypeFlow invocation, AnalysisMethod concreteTargetMethod, AnalysisType concreteTargetType) {
        AnalysisMethod callingMethod = invocation.method();

        if(callingMethod == null && invocation.getTargetMethod().getContextInsensitiveVirtualInvoke(invocation.getCallerMultiMethodKey()) != invocation)
            throw new RuntimeException("CausalityExport has made an invalid assumption!");

        CausalityExport.Event callerEvent = callingMethod != null
                ? new CausalityExport.InlinedMethodCode(callingMethod) /* TODO: Take inlining into account */
                : new RootMethodRegistration(invocation.getTargetMethod());

        registerEdge(
                callerEvent,
                new CausalityExport.VirtualMethodInvoked(invocation.getTargetMethod()));
        registerConjunctiveEdge(
                new CausalityExport.VirtualMethodInvoked(invocation.getTargetMethod()),
                new CausalityExport.TypeInstantiated(concreteTargetType),
                new CausalityExport.MethodImplementationInvoked(concreteTargetMethod)
        );
    }

    private Event getEventForHeapReason(Object customReason, Object o) {
        if (customReason == null) {
            return new UnknownHeapObject(o.getClass());
        } else if (customReason instanceof Event) {
            return (Event) customReason;
        } else if (customReason instanceof Class<?>) {
            return new BuildTimeClassInitialization((Class<?>) customReason);
        } else {
            throw AnalysisError.shouldNotReachHere("Heap Assignment Tracing Reason should not be of type " + customReason.getClass().getTypeName());
        }
    }

    private Event getHeapObjectCreator(Object heapObject, ObjectScanner.ScanReason reason) {
        Object responsible = HeapAssignmentTracing.getInstance().getResponsibleClass(heapObject);
        return getEventForHeapReason(responsible, heapObject);
    }

    private Event getHeapObjectCreator(BigBang bb, JavaConstant heapObject, ObjectScanner.ScanReason reason) {
        if (heapObject instanceof ImageHeapConstant imageHeapConstant && !imageHeapConstant.isBackedByHostedObject()) {
            return SimulatedHeapTracing.instance.getHeapObjectCreator(imageHeapConstant);
        }
        return getHeapObjectCreator(asObject(bb, Object.class, heapObject), reason);
    }

    private static Event forScanReason(ObjectScanner.ScanReason reason) {
        if (reason instanceof ObjectScanner.EmbeddedRootScan ers) {
            return new InlinedMethodCode(ers.getPosition());
        }
        if (reason instanceof ObjectScanner.FieldScan fs) {
            return new FieldRead(fs.getField());
        }
        return null;
    }

    @Override
    public void registerEdgeFromHeapObject(BigBang bb, JavaConstant heapObject, ObjectScanner.ScanReason reason, Event consequence) {
        Event writerCause = getHeapObjectCreator(bb, heapObject, reason);
        Event readerCause = forScanReason(reason);
        registerConjunctiveEdge(writerCause, readerCause, consequence);
    }

    @Override
    public void registerEdgeFromHeapObject(Object heapObject, ObjectScanner.ScanReason reason, Event consequence) {
        Event writerCause = getHeapObjectCreator(heapObject, reason);
        Event readerCause = forScanReason(reason);
        registerConjunctiveEdge(writerCause, readerCause, consequence);
    }

    @Override
    public Event getHeapFieldAssigner(BigBang bb, JavaConstant receiver, AnalysisField field, JavaConstant value) {
        Object responsible;
        Object o;

        if (field.isStatic()) {
            if (value instanceof ImageHeapConstant imageHeapConstant && !imageHeapConstant.isBackedByHostedObject()) {
                return SimulatedHeapTracing.instance.getHeapFieldAssigner(field, imageHeapConstant);
            } else {
                o = asObject(bb, Object.class, value);
                java.lang.reflect.Field f = field.getJavaField();
                Class<?> declaringClass = f.getDeclaringClass();
                responsible = HeapAssignmentTracing.getInstance().getClassResponsibleForStaticFieldWrite(declaringClass, f, o);
            }
        } else {
            if (receiver instanceof ImageHeapInstance imageHeapConstant && !imageHeapConstant.isBackedByHostedObject()) {
                return SimulatedHeapTracing.instance.getHeapFieldAssigner(imageHeapConstant, field, value);
            } else {
                Object receiverO = asObject(bb, Object.class, receiver);
                o = asObject(bb, Object.class, value);
                java.lang.reflect.Field f = field.getJavaField();
                if (f.getDeclaringClass().isAssignableFrom(receiverO.getClass())) {
                    responsible = HeapAssignmentTracing.getInstance().getClassResponsibleForNonstaticFieldWrite(receiverO, f, o);
                } else {
                    // Field must be substituted or recomputed
                    responsible = null;
                }
            }
        }

        return getEventForHeapReason(responsible, o);
    }

    @Override
    public Event getHeapArrayAssigner(BigBang bb, JavaConstant array, int elementIndex, JavaConstant value) {
        if (array instanceof ImageHeapObjectArray imageHeapArray && !imageHeapArray.isBackedByHostedObject()) {
            return SimulatedHeapTracing.instance.getHeapArrayAssigner(imageHeapArray, elementIndex, value);
        }
        Object o = asObject(bb, Object.class, value);
        Object responsible = HeapAssignmentTracing.getInstance().getClassResponsibleForArrayWrite(asObject(bb, Object[].class, array), elementIndex, o);
        return getEventForHeapReason(responsible, o);
    }

    @Override
    public void registerEvent(Event event) {
        registerEdge(null, event);
    }

    @Override
    public Event getCause() {
        return causes.empty() ? null : causes.peek().event;
    }

    private final Stack<CauseToken> causes = new Stack<>();

    private void updateHeapTracing() {
        CauseToken token = causes.empty() ? null : causes.peek();
        Event cause = token == null || token.level == HeapTracing.None ? null : token.event;
        boolean recordHeapAssignments = token != null && token.level == HeapTracing.Full;
        HeapAssignmentTracing.getInstance().setCause(cause, recordHeapAssignments);
    }

    @Override
    protected void beginCauseRegion(CauseToken token) {
        if(!causes.empty() && token.event != null && causes.peek().event != null && !causes.peek().event.equals(token.event) && token.event != Ignored.Instance && causes.peek().event != Ignored.Instance && !(causes.peek().event instanceof Feature) && !causes.peek().event.root())
            throw new RuntimeException("Stacking Rerooting requests!");
        causes.push(token);
        updateHeapTracing();
    }

    @Override
    protected void endCauseRegion(CauseToken token) {
        if(causes.empty() || causes.pop() != token) {
            throw new RuntimeException("Invalid Call to endAccountingRootRegistrationsTo()");
        }
        updateHeapTracing();
    }

    protected void forEachEvent(Consumer<Event> callback) {
    }

    public Graph createCausalityGraph(PointsToAnalysis bb) {
        Graph g = new Graph();

        Event[] eventsById = new Event[nextUniqueId.get()];
        for (var entry : uniqueEventIds.entrySet()) {
            eventsById[entry.getValue()] = entry.getKey();
        }

        var direct_edges = this.direct_edges.toSet(eventsById);
        this.direct_edges.kill();
        var hyper_edges = this.hyper_edges.toSet(eventsById);
        this.hyper_edges.kill();

        Consumer<Consumer<Event>> forEachEventLocal = (Consumer<Event> callback) -> {
            for (Pair<Event, Event> e : direct_edges) {
                callback.accept(e.getLeft());
                callback.accept(e.getRight());
            }
            for (var he : hyper_edges) {
                callback.accept(he.from1);
                callback.accept(he.from2);
                callback.accept(he.to);
            }
        };

        direct_edges.removeIf(pair -> pair.getRight() instanceof MethodReachable mr && mr.element.isClassInitializer());

        HashSet<Event> events = new HashSet<>();
        forEachEvent(events::add);
        forEachEventLocal.accept(events::add);
        for (Event e : events) {
            if (e != null && !e.unused() && e.root()) {
                g.add(new Graph.DirectEdge(null, e));
            }
        }

        for (Pair<Event, Event> e : direct_edges) {
            Event from = e.getLeft();
            Event to = e.getRight();

            if(from != null && from.unused())
                continue;

            if(to.unused())
                continue;

            g.add(new Graph.DirectEdge(from, to));
        }

        for (AnalysisType t : bb.getUniverse().getTypes()) {
            if (!t.isReachable())
                continue;

            AnalysisMethod classInitializer = t.getClassInitializer();
            if(classInitializer != null && classInitializer.isImplementationInvoked()) {
                g.add(new Graph.DirectEdge(new TypeReachable(t), new MethodReachable(classInitializer)));
            }
        }

        {
            Set<BuildTimeClassInitialization> buildTimeClinits = new HashSet<>();
            Set<BuildTimeClassInitialization> buildTimeClinitsWithReason = new HashSet<>();

            direct_edges.stream().map(Pair::getLeft).filter(l -> l instanceof BuildTimeClassInitialization).map(l -> (BuildTimeClassInitialization) l).forEach(init -> {
                if (buildTimeClinits.contains(init))
                    return;

                for (;;) {
                    buildTimeClinits.add(init);
                    Object outerInitReason = HeapAssignmentTracing.getInstance().getBuildTimeClinitResponsibleForBuildTimeClinit(init.clazz);
                    if (outerInitReason == null)
                        break;
                    buildTimeClinitsWithReason.add(init);
                    if (outerInitReason instanceof Class<?> outerInitClass) {
                        BuildTimeClassInitialization outerInit = new BuildTimeClassInitialization(outerInitClass);
                        g.add(new Graph.DirectEdge(outerInit, init));
                        init = outerInit;
                    } else {
                        g.add(new Graph.DirectEdge((Event) outerInitReason, init));
                        break;
                    }
                }
            });

            direct_edges.stream()
                    .map(Pair::getRight)
                    .filter(e -> e instanceof BuildTimeClassInitialization)
                    .map(e -> (BuildTimeClassInitialization) e)
                    .forEach(buildTimeClinitsWithReason::add);

            buildTimeClinits.stream().sorted(Comparator.comparing(init -> init.clazz.getTypeName())).forEach(init -> {
                AnalysisType t;
                try {
                    t = bb.getMetaAccess().optionalLookupJavaType(init.clazz).orElse(null);
                } catch (UnsupportedFeatureException ex) {
                    t = null;
                }

                if (t != null && t.isReachable()) {
                    TypeReachable tReachable = new TypeReachable(t);
                    g.add(new Graph.DirectEdge(tReachable, init));
                } else if(!buildTimeClinitsWithReason.contains(init)) {
                    g.add(new Graph.DirectEdge(null, init));
                }
            });
        }

        for(Graph.HyperEdge andEdge : hyper_edges) {
            if(andEdge.from1.unused() || andEdge.from2.unused() || andEdge.to.unused())
                continue;

            g.add(andEdge);
        }

        return g;
    }
}
