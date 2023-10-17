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

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
import com.oracle.graal.pointsto.reports.causality.events.CausalityEvents;
import com.oracle.graal.pointsto.reports.causality.events.Feature;
import com.oracle.graal.pointsto.reports.causality.events.InlinedMethodCode;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.meta.JavaConstant;

class BasicImpl<TContext extends BasicImpl.ThreadContext> extends CausalityImplementation {
    private final ConcurrentHashMap<Graph.DirectEdge, Boolean> directEdges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Graph.HyperEdge, Boolean> hyperEdges = new ConcurrentHashMap<>();
    private final Map<Object, Object> originsOfReplacedObjects = Collections.synchronizedMap(new IdentityHashMap<>());

    private final ThreadLocal<TContext> threadContexts;

    protected TContext getContext() {
        return threadContexts.get();
    }

    public static class ThreadContext {
        private final Deque<CauseToken> causes = new ArrayDeque<>();

        public CausalityEvent topCause() {
            return causes.isEmpty() ? null : causes.peek().event;
        }

        public CauseToken topCauseToken() {
            return causes.peek();
        }

        private void updateHeapTracing(CauseToken top) {
            CausalityEvent cause = top == null || top.level == CausalityExport.HeapTracing.None ? null : top.event;
            boolean recordHeapAssignments = top != null && top.level == CausalityExport.HeapTracing.Full;
            HeapAssignmentTracing.getInstance().setCause(cause, recordHeapAssignments);
        }

        public final class CauseToken implements CausalityExport.NonThrowingAutoCloseable {
            private final CausalityEvent event;
            private final CausalityExport.HeapTracing level;

            private CauseToken(CausalityEvent event, CausalityExport.HeapTracing level, boolean overwriteSilently) {
                this.event = event;
                this.level = level;

                if (!overwriteSilently && !causes.isEmpty()) {
                    CausalityEvent top = causes.peek().event;
                    if (top != null && top != event && event != CausalityEvents.Ignored && top != CausalityEvents.Ignored && !(top instanceof Feature) && !top.root()) {
                        throw new RuntimeException("Stacking Rerooting requests!");
                    }
                }

                causes.push(this);
                updateHeapTracing(this);
            }

            @Override
            public void close() {
                if (causes.isEmpty() || causes.pop() != this) {
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
        if (cause1 == null) {
            registerEdge(cause2, consequence);
        } else if (cause2 == null) {
            registerEdge(cause1, consequence);
        } else {
            hyperEdges.put(new Graph.HyperEdge(cause1, cause2, consequence), Boolean.TRUE);
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
        directEdges.put(new Graph.DirectEdge(cause, consequence), Boolean.TRUE);
    }

    @Override
    public void registerVirtualInvocation(PointsToAnalysis bb, AbstractVirtualInvokeTypeFlow invocation, AnalysisMethod concreteTargetMethod, AnalysisType concreteTargetType) {
        AnalysisMethod callingMethod = invocation.method();

        if (callingMethod == null && invocation.getTargetMethod().getContextInsensitiveVirtualInvoke(invocation.getCallerMultiMethodKey()) != invocation) {
            throw new RuntimeException("CausalityExport has made an invalid assumption!");
        }

        CausalityEvent callerEvent = callingMethod != null
                        // TODO: Take inlining into account
                        ? CausalityEvents.InlinedMethodCode.create(callingMethod)
                        : CausalityEvents.RootMethodRegistration.create(invocation.getTargetMethod());

        registerEdge(
                        callerEvent,
                        CausalityEvents.VirtualMethodInvoked.create(invocation.getTargetMethod()));
        registerConjunctiveEdge(
                        CausalityEvents.VirtualMethodInvoked.create(invocation.getTargetMethod()),
                        CausalityEvents.TypeInstantiated.create(concreteTargetType),
                        CausalityEvents.MethodImplementationInvoked.create(concreteTargetMethod));
    }

    private static CausalityEvent getEventForHeapReason(Object customReason, Object o) {
        if (customReason == null) {
            return CausalityEvents.UnknownHeapObject.create(o.getClass());
        } else if (customReason instanceof CausalityEvent) {
            return (CausalityEvent) customReason;
        } else if (customReason instanceof Class<?>) {
            return CausalityEvents.BuildTimeClassInitialization.create((Class<?>) customReason);
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
            return CausalityEvents.InlinedMethodCode.create(ers.getPosition());
        }
        if (reason instanceof ObjectScanner.FieldScan fs) {
            return CausalityEvents.FieldRead.create(fs.getField());
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
    protected CausalityExport.NonThrowingAutoCloseable setCause(CausalityEvent event, CausalityExport.HeapTracing level, boolean overwriteSilently) {
        return threadContexts.get().new CauseToken(event, level, overwriteSilently);
    }

    protected void forEachEvent(Consumer<CausalityEvent> callback) {
        for (var e : directEdges.keySet()) {
            callback.accept(e.from);
            callback.accept(e.to);
        }
        for (var he : hyperEdges.keySet()) {
            callback.accept(he.from1);
            callback.accept(he.from2);
            callback.accept(he.to);
        }
    }

    @Override
    protected Graph createCausalityGraph(PointsToAnalysis bb) {
        Graph g = new Graph();

        var directEdges = this.directEdges.keySet();
        var hyperEdges = this.hyperEdges.keySet();

        directEdges.removeIf(pair -> pair.from != null && pair.from.unused() || pair.to.unused());
        directEdges.removeIf(pair -> pair.to instanceof com.oracle.graal.pointsto.reports.causality.events.MethodReachable mr && mr.element.isClassInitializer());

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

        for (var e : directEdges) {
            g.add(e);
        }

        for (AnalysisType t : bb.getUniverse().getTypes()) {
            if (!t.isReachable()) {
                continue;
            }

            AnalysisMethod classInitializer = t.getClassInitializer();
            if (classInitializer != null && classInitializer.isImplementationInvoked()) {
                g.add(new Graph.DirectEdge(CausalityEvents.TypeReachable.create(t), CausalityEvents.MethodReachable.create(classInitializer)));
            }
        }

        addEdgesForBuildTimeClassInitializers(
                        bb,
                        initialBuildTimeClinits,
                        directEdges.stream()
                                        .map(e -> e.to)
                                        .filter(e -> e instanceof BuildTimeClassInitialization)
                                        .map(e -> (BuildTimeClassInitialization) e)
                                        .collect(Collectors.toSet()),
                        g);

        for (var e : allCodeEvents) {
            g.add(new Graph.DirectEdge(e, CausalityEvents.MethodGraphParsed.create(e.context[0])));
        }

        for (Graph.HyperEdge andEdge : hyperEdges) {
            if (andEdge.from1.unused() || andEdge.from2.unused() || andEdge.to.unused()) {
                continue;
            }
            g.add(andEdge);
        }

        this.directEdges.clear();
        this.hyperEdges.clear();

        return g;
    }

    private static void addEdgesForBuildTimeClassInitializers(PointsToAnalysis bb, Set<BuildTimeClassInitialization> initialBuildTimeClinits,
                    Set<BuildTimeClassInitialization> buildTimeClinitsWithReason, Graph g) {
        Set<BuildTimeClassInitialization> visitedBuildTimeClinits = new HashSet<>();

        for (var initialInit : initialBuildTimeClinits) {
            for (BuildTimeClassInitialization init = initialInit, outerInit; visitedBuildTimeClinits.add(init); init = outerInit) {
                Object outerInitReason = HeapAssignmentTracing.getInstance().getBuildTimeClinitResponsibleForBuildTimeClinit(init.clazz);
                if (outerInitReason == null) {
                    break;
                }
                buildTimeClinitsWithReason.add(init);
                if (outerInitReason instanceof Class<?> outerInitClass) {
                    outerInit = (BuildTimeClassInitialization) CausalityEvents.BuildTimeClassInitialization.create(outerInitClass);
                    g.add(new Graph.DirectEdge(outerInit, init));
                } else {
                    g.add(new Graph.DirectEdge((CausalityEvent) outerInitReason, init));
                    break;
                }
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
                CausalityEvent tReachable = CausalityEvents.TypeReachable.create(t);
                g.add(new Graph.DirectEdge(tReachable, init));
            } else if (!buildTimeClinitsWithReason.contains(init)) {
                g.add(new Graph.DirectEdge(null, init));
            }
        });
    }
}
