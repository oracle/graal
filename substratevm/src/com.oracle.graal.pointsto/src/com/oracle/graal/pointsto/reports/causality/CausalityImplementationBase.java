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
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapInstance;
import com.oracle.graal.pointsto.heap.ImageHeapObjectArray;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.reports.causality.facts.BuildTimeClassInitialization;
import com.oracle.graal.pointsto.reports.causality.facts.Fact;
import com.oracle.graal.pointsto.reports.causality.facts.Facts;
import com.oracle.graal.pointsto.reports.causality.facts.Feature;
import com.oracle.graal.pointsto.reports.causality.facts.InlinedMethodCode;
import com.oracle.graal.pointsto.reports.causality.facts.MethodReachable;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaConstant;

abstract class CausalityImplementationBase<TContext extends CausalityImplementationBase.ThreadContext> extends CausalityImplementation {
    private final ConcurrentHashMap<Graph.DirectEdge, Boolean> directEdges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Graph.HyperEdge, Boolean> hyperEdges = new ConcurrentHashMap<>();

    private final ThreadLocal<TContext> threadContexts;

    protected TContext getContext() {
        return threadContexts.get();
    }

    private static int getSkipCount(StackTraceElement[] stackTrace) {
        return (int) Arrays.stream(stackTrace).takeWhile(ste -> ste.getClassName().startsWith("com.oracle.graal.pointsto.reports.causality.")).count();
    }

    public static class ThreadContext {
        private final Deque<CauseToken> causes = new ArrayDeque<>();

        public Fact topCause() {
            return causes.isEmpty() ? null : causes.peek().fact;
        }

        public CauseToken topCauseToken() {
            return causes.peek();
        }

        public final class CauseToken implements Causality.NonThrowingAutoCloseable {
            private final Fact fact;
            public final StackTraceElement site;
            public final int stackDepth;

            private CauseToken(Fact fact, boolean overwriteSilently) {
                this.fact = fact;

                var stackTrace = new Throwable().getStackTrace();
                int nSkip = getSkipCount(stackTrace);
                this.site = stackTrace[nSkip];
                this.stackDepth = stackTrace.length - nSkip - 1;

                if (!overwriteSilently && !causes.isEmpty()) {
                    Fact top = causes.peek().fact;
                    if (fact != null && top != null && top != fact && fact != Facts.Ignored && top != Facts.Ignored && !(top instanceof Feature) && !top.root()) {
                        throw new RuntimeException("Stacking Rerooting requests!");
                    }
                }

                causes.push(this);
            }

            @Override
            public void close() {
                assert !causes.isEmpty();
                var popped = causes.pop();
                assert popped == this;
            }
        }
    }

    protected CausalityImplementationBase(Supplier<TContext> contextSupplier) {
        threadContexts = ThreadLocal.withInitial(contextSupplier);
    }

    @Override
    public void registerConjunctiveEdge(Fact cause1, Fact cause2, Fact consequence) {
        if (cause1 == null) {
            registerEdge(cause2, consequence);
        } else if (cause2 == null) {
            registerEdge(cause1, consequence);
        } else {
            hyperEdges.put(new Graph.HyperEdge(cause1, cause2, consequence), Boolean.TRUE);
        }
    }

    @Override
    public void registerEdge(Fact cause, Fact consequence) {
        if (cause == null || cause.root()) {
            ThreadContext.CauseToken topCauseToken = threadContexts.get().topCauseToken();
            cause = topCauseToken == null ? null : topCauseToken.fact;
        }
        if (cause != consequence) {
            directEdges.put(new Graph.DirectEdge(cause, consequence), Boolean.TRUE);
        }
    }

    private static Fact getUnknownHeapObjectFact(Object heapObject) {
        return Facts.UnknownHeapObject.create(heapObject.getClass());
    }

    private static Fact getUnknownHeapObjectFact(BigBang bb, JavaConstant heapObject) {
        return getUnknownHeapObjectFact(bb.getSnippetReflectionProvider().asObject(Object.class, heapObject).getClass());
    }

    private static Fact getHeapObjectCreator(BigBang bb, JavaConstant heapObject) {
        if (heapObject instanceof ImageHeapConstant imageHeapConstant && !imageHeapConstant.isBackedByHostedObject()) {
            return SimulatedHeapTracing.instance.getHeapObjectCreator(imageHeapConstant);
        }
        return getUnknownHeapObjectFact(bb, heapObject);
    }

    private static Fact forScanReason(ObjectScanner.ScanReason reason) {
        if (reason instanceof ObjectScanner.EmbeddedRootScan ers) {
            return Facts.InlinedMethodCode.create(ers.getReason() instanceof BytecodePosition pos ? pos : ers.getPosition());
        }
        if (reason instanceof ObjectScanner.FieldScan fs) {
            return Facts.FieldRead.create(fs.getField());
        }
        return null;
    }

    @Override
    public void registerEdgeFromHeapObject(BigBang bb, JavaConstant heapObject, ObjectScanner.ScanReason reason, Fact consequence) {
        Fact writerCause = getHeapObjectCreator(bb, heapObject);
        Fact readerCause = forScanReason(reason);
        registerConjunctiveEdge(writerCause, readerCause, consequence);
    }

    @Override
    public void registerEdgeFromHeapObject(Object heapObject, ObjectScanner.ScanReason reason, Fact consequence) {
        Fact writerCause = getUnknownHeapObjectFact(heapObject);
        Fact readerCause = forScanReason(reason);
        registerConjunctiveEdge(writerCause, readerCause, consequence);
    }

    @Override
    public Fact getHeapFieldAssigner(BigBang bb, JavaConstant receiver, AnalysisField field, JavaConstant value) {
        if (field.isStatic()) {
            if (value instanceof ImageHeapConstant imageHeapConstant && !imageHeapConstant.isBackedByHostedObject()) {
                return SimulatedHeapTracing.instance.getHeapFieldAssigner(field, imageHeapConstant);
            }
        } else {
            if (receiver instanceof ImageHeapInstance imageHeapConstant && !imageHeapConstant.isBackedByHostedObject()) {
                return SimulatedHeapTracing.instance.getHeapFieldAssigner(imageHeapConstant, field, value);
            }
        }

        return getUnknownHeapObjectFact(bb, value);
    }

    @Override
    public Fact getHeapArrayAssigner(BigBang bb, JavaConstant array, int elementIndex, JavaConstant value) {
        if (elementIndex < 0) {
            throw new ArrayIndexOutOfBoundsException(elementIndex);
        }
        if (array instanceof ImageHeapObjectArray imageHeapArray && !imageHeapArray.isBackedByHostedObject()) {
            return SimulatedHeapTracing.instance.getHeapArrayAssigner(imageHeapArray, elementIndex, value);
        }
        return getUnknownHeapObjectFact(bb, value);
    }

    @Override
    public Fact getCause() {
        return threadContexts.get().topCause();
    }

    @Override
    protected Causality.NonThrowingAutoCloseable setCause(Fact fact, boolean overwriteSilently) {
        return threadContexts.get().new CauseToken(fact, overwriteSilently);
    }

    protected void forEachEvent(Consumer<Fact> callback) {
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
        directEdges.removeIf(pair -> pair.to instanceof MethodReachable mr && mr.method.isClassInitializer());

        HashSet<Fact> rootEvents = new HashSet<>();
        Set<BuildTimeClassInitialization> initialBuildTimeClinits = new HashSet<>();
        HashSet<InlinedMethodCode> allCodeEvents = new HashSet<>();
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
                g.add(new Graph.DirectEdge(Facts.TypeReachable.create(t), Facts.MethodReachable.create(classInitializer)));
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
            g.add(new Graph.DirectEdge(e, Facts.MethodGraphParsed.create(e.context[0])));
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
        initialBuildTimeClinits.stream().sorted(Comparator.comparing(init -> init.clazz.getTypeName())).forEach(init -> {
            AnalysisType t;
            try {
                t = bb.getMetaAccess().optionalLookupJavaType(init.clazz).orElse(null);
            } catch (UnsupportedFeatureException ex) {
                t = null;
            }

            if (t != null && t.isReachable()) {
                Fact tReachable = Facts.TypeReachable.create(t);
                g.add(new Graph.DirectEdge(tReachable, init));
            } else if (!buildTimeClinitsWithReason.contains(init)) {
                g.add(new Graph.DirectEdge(null, init));
            }
        });
    }
}
