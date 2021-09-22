/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.meta;

import static jdk.vm.ci.common.JVMCIError.shouldNotReachHere;
import static jdk.vm.ci.common.JVMCIError.unimplemented;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.BigBang;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.BytecodeParser.BytecodeParserError;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.util.GuardedAnnotationAccess;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.flow.AbstractVirtualInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.infrastructure.GraphProvider;
import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.graal.pointsto.infrastructure.WrappedSignature;
import com.oracle.graal.pointsto.results.StaticAnalysisResults;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AtomicUtils;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

public class AnalysisMethod implements WrappedJavaMethod, GraphProvider, OriginalMethodProvider {

    private final AnalysisUniverse universe;
    public final ResolvedJavaMethod wrapped;

    private final int id;
    private final boolean hasNeverInlineDirective;
    private final ExceptionHandler[] exceptionHandlers;
    private final LocalVariableTable localVariableTable;
    private final String qualifiedName;
    private MethodTypeFlow typeFlow;
    private final AnalysisType declaringClass;

    private final AtomicBoolean isRootMethod = new AtomicBoolean();
    private boolean isIntrinsicMethod;
    private Object entryPointData;
    private final AtomicBoolean isInvoked = new AtomicBoolean();
    private final AtomicBoolean isImplementationInvoked = new AtomicBoolean();
    private boolean isInlined;

    private final AtomicReference<Object> parsedGraphCacheState = new AtomicReference<>(GRAPH_CACHE_UNPARSED);
    private static final Object GRAPH_CACHE_UNPARSED = "unparsed";
    private static final Object GRAPH_CACHE_CLEARED = "cleared by cleanupAfterAnalysis";

    private StructuredGraph analyzedGraph;

    /**
     * All concrete methods that can actually be called when calling this method. This includes all
     * overridden methods in subclasses, as well as this method if it is non-abstract.
     */
    protected AnalysisMethod[] implementations;

    private ConcurrentMap<InvokeTypeFlow, Object> invokedBy;
    private ConcurrentMap<InvokeTypeFlow, Object> implementationInvokedBy;

    public AnalysisMethod(AnalysisUniverse universe, ResolvedJavaMethod wrapped) {
        this.universe = universe;
        this.wrapped = wrapped;
        this.id = universe.nextMethodId.getAndIncrement();
        declaringClass = universe.lookup(wrapped.getDeclaringClass());

        hasNeverInlineDirective = universe.hostVM().hasNeverInlineDirective(wrapped);

        if (PointstoOptions.TrackAccessChain.getValue(universe.hostVM().options())) {
            startTrackInvocations();
        }

        ExceptionHandler[] original = wrapped.getExceptionHandlers();
        exceptionHandlers = new ExceptionHandler[original.length];
        for (int i = 0; i < original.length; i++) {
            ExceptionHandler h = original[i];
            JavaType catchType = getCatchType(h);
            exceptionHandlers[i] = new ExceptionHandler(h.getStartBCI(), h.getEndBCI(), h.getHandlerBCI(), h.catchTypeCPI(), catchType);
        }

        LocalVariableTable newLocalVariableTable = null;
        if (wrapped.getLocalVariableTable() != null) {
            try {
                Local[] origLocals = wrapped.getLocalVariableTable().getLocals();
                Local[] newLocals = new Local[origLocals.length];
                ResolvedJavaType accessingClass = getDeclaringClass().getWrapped();
                for (int i = 0; i < newLocals.length; ++i) {
                    Local origLocal = origLocals[i];
                    ResolvedJavaType origLocalType = origLocal.getType() instanceof ResolvedJavaType ? (ResolvedJavaType) origLocal.getType() : origLocal.getType().resolve(accessingClass);
                    AnalysisType type = universe.lookup(origLocalType);
                    newLocals[i] = new Local(origLocal.getName(), type, origLocal.getStartBCI(), origLocal.getEndBCI(), origLocal.getSlot());
                }
                newLocalVariableTable = new LocalVariableTable(newLocals);
            } catch (LinkageError | UnsupportedFeatureException | BytecodeParserError e) {
                newLocalVariableTable = null;
            }

        }
        localVariableTable = newLocalVariableTable;

        typeFlow = new MethodTypeFlow(universe.hostVM().options(), this);

        this.qualifiedName = format("%H.%n(%P)");
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    private JavaType getCatchType(ExceptionHandler handler) {
        JavaType catchType = handler.getCatchType();
        if (catchType == null) {
            return null;
        }
        ResolvedJavaType resolvedCatchType;
        try {
            resolvedCatchType = catchType.resolve(wrapped.getDeclaringClass());
        } catch (LinkageError e) {
            /*
             * Type resolution fails if the catch type is missing. Just return the unresolved type.
             * The analysis doesn't model unresolved types, but we can reuse the JVMCI type; the
             * UniverseBuilder and the BytecodeParser know how to deal with that.
             */
            return catchType;
        }
        return universe.lookup(resolvedCatchType);
    }

    public void cleanupAfterAnalysis() {
        typeFlow = null;
        invokedBy = null;
        implementationInvokedBy = null;
        contextInsensitiveInvoke.set(null);
        if (parsedGraphCacheState.get() instanceof AnalysisParsedGraph) {
            parsedGraphCacheState.set(GRAPH_CACHE_CLEARED);
        }
    }

    public void startTrackInvocations() {
        if (invokedBy == null) {
            invokedBy = new ConcurrentHashMap<>();
        }
        if (implementationInvokedBy == null) {
            implementationInvokedBy = new ConcurrentHashMap<>();
        }
    }

    public int getId() {
        return id;
    }

    public MethodTypeFlow getTypeFlow() {
        return typeFlow;
    }

    /**
     * Registers this method as intrinsified to Graal nodes via a {@link InvocationPlugin graph
     * builder plugin}. Such a method is treated similar to an invoked method. For example, method
     * resolution must be able to find the method (otherwise the intrinsification would not work).
     */
    public void registerAsIntrinsicMethod() {
        isIntrinsicMethod = true;
    }

    public void registerAsEntryPoint(Object newEntryPointData) {
        assert newEntryPointData != null;
        if (entryPointData != null && !entryPointData.equals(newEntryPointData)) {
            throw new UnsupportedFeatureException("Method is registered as entry point with conflicting entry point data: " + entryPointData + ", " + newEntryPointData);
        }
        entryPointData = newEntryPointData;
        /* We need that to check that entry points are not invoked from other Java methods. */
        startTrackInvocations();
    }

    public boolean registerAsInvoked(InvokeTypeFlow invoke) {
        if (invokedBy != null && invoke != null) {
            invokedBy.put(invoke, Boolean.TRUE);
        }
        return AtomicUtils.atomicMark(isInvoked);
    }

    public boolean registerAsImplementationInvoked(InvokeTypeFlow invoke) {
        assert !Modifier.isAbstract(getModifiers());
        if (implementationInvokedBy != null && invoke != null) {
            implementationInvokedBy.put(invoke, Boolean.TRUE);
        }

        /*
         * The class constant of the declaring class is used for exception metadata, so marking a
         * method as invoked also makes the declaring class reachable.
         *
         * Even though the class could in theory be marked as reachable only if we successfully mark
         * the method as invoked, it would have an unwanted side effect, where this method could
         * return before the class gets marked as reachable.
         */
        getDeclaringClass().registerAsReachable();
        return AtomicUtils.atomicMark(isImplementationInvoked);
    }

    public void registerAsInlined() {
        isInlined = true;
    }

    /** Get the set of all callers for this method, as inferred by the static analysis. */
    public Set<AnalysisMethod> getCallers() {
        return getInvokeLocations().stream().map(location -> (AnalysisMethod) location.getMethod()).collect(Collectors.toSet());
    }

    /** Get the list of all invoke locations for this method, as inferred by the static analysis. */
    public List<BytecodePosition> getInvokeLocations() {
        List<BytecodePosition> locations = new ArrayList<>();
        for (InvokeTypeFlow invoke : implementationInvokedBy.keySet()) {
            if (InvokeTypeFlow.isContextInsensitiveVirtualInvoke(invoke)) {
                locations.addAll(((AbstractVirtualInvokeTypeFlow) invoke).getInvokeLocations());
            } else {
                locations.add(invoke.getSource());
            }
        }
        return locations;
    }

    public boolean isEntryPoint() {
        return entryPointData != null;
    }

    public Object getEntryPointData() {
        return entryPointData;
    }

    public boolean isIntrinsicMethod() {
        return isIntrinsicMethod;
    }

    /**
     * Registers this method as a root for the analysis.
     *
     * The class constant of the declaring class is used for exception metadata, so marking a method
     * as invoked also makes the declaring class reachable.
     *
     * Class is always marked as reachable regardless of the success of the atomic mark, same reason
     * as in {@link AnalysisMethod#registerAsImplementationInvoked(InvokeTypeFlow)}.
     */
    public boolean registerAsRootMethod() {
        getDeclaringClass().registerAsReachable();
        return AtomicUtils.atomicMark(isRootMethod);
    }

    public boolean isRootMethod() {
        return isRootMethod.get();
    }

    public boolean isSimplyInvoked() {
        return isInvoked.get();
    }

    public boolean isSimplyImplementationInvoked() {
        return isImplementationInvoked.get();
    }

    /**
     * Returns true if this method is ever used as the target of a call site.
     */
    public boolean isInvoked() {
        return isIntrinsicMethod || isRootMethod() || isInvoked.get();
    }

    /**
     * Returns true if the method body can ever be executed.
     */
    public boolean isImplementationInvoked() {
        return !Modifier.isAbstract(getModifiers()) && (isIntrinsicMethod || isRootMethod() || isImplementationInvoked.get());
    }

    public boolean isReachable() {
        return isImplementationInvoked() || isInlined;
    }

    @Override
    public ResolvedJavaMethod getWrapped() {
        return wrapped;
    }

    @Override
    public String getName() {
        return wrapped.getName();
    }

    @Override
    public WrappedSignature getSignature() {
        return universe.lookup(wrapped.getSignature(), getDeclaringClass());
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        if (wrapped instanceof GraphProvider) {
            return ((GraphProvider) wrapped).buildGraph(debug, method, providers, purpose);
        }
        return null;
    }

    @Override
    public boolean allowRuntimeCompilation() {
        if (wrapped instanceof GraphProvider) {
            return ((GraphProvider) wrapped).allowRuntimeCompilation();
        }
        return true;
    }

    @Override
    public byte[] getCode() {
        return wrapped.getCode();
    }

    @Override
    public int getCodeSize() {
        return wrapped.getCodeSize();
    }

    @Override
    public AnalysisType getDeclaringClass() {
        return declaringClass;
    }

    @Override
    public int getMaxLocals() {
        return wrapped.getMaxLocals();
    }

    @Override
    public int getMaxStackSize() {
        return wrapped.getMaxStackSize();
    }

    @Override
    public Parameter[] getParameters() {
        return wrapped.getParameters();
    }

    @Override
    public int getModifiers() {
        return wrapped.getModifiers();
    }

    @Override
    public boolean isSynthetic() {
        return wrapped.isSynthetic();
    }

    @Override
    public boolean isVarArgs() {
        throw unimplemented();
    }

    @Override
    public boolean isBridge() {
        return wrapped.isBridge();
    }

    @Override
    public boolean isClassInitializer() {
        return wrapped.isClassInitializer();
    }

    @Override
    public boolean isConstructor() {
        return wrapped.isConstructor();
    }

    @Override
    public boolean canBeStaticallyBound() {
        boolean result = wrapped.canBeStaticallyBound();
        assert !isStatic() || result : "static methods must always be statically bindable: " + format("%H.%n");
        return result;

    }

    public AnalysisMethod[] getImplementations() {
        assert universe.analysisDataValid;
        if (implementations == null) {
            return new AnalysisMethod[0];
        }
        return implementations;
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        return exceptionHandlers;
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        return wrapped.asStackTraceElement(bci);
    }

    @Override
    public ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        /*
         * This is also the profiling information used when parsing methods for static analysis, so
         * it needs to be conservative.
         */
        return StaticAnalysisResults.NO_RESULTS;
    }

    @Override
    public ConstantPool getConstantPool() {
        return universe.lookup(wrapped.getConstantPool(), getDeclaringClass());
    }

    @Override
    public Annotation[] getAnnotations() {
        return GuardedAnnotationAccess.getAnnotations(wrapped);
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return GuardedAnnotationAccess.getDeclaredAnnotations(wrapped);
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return GuardedAnnotationAccess.getAnnotation(wrapped, annotationClass);
    }

    @Override
    public Annotation[][] getParameterAnnotations() {
        return wrapped.getParameterAnnotations();
    }

    @Override
    public Type[] getGenericParameterTypes() {
        return wrapped.getGenericParameterTypes();
    }

    @Override
    public boolean canBeInlined() {
        return !hasNeverInlineDirective();
    }

    @Override
    public boolean hasNeverInlineDirective() {
        return hasNeverInlineDirective;
    }

    @Override
    public boolean shouldBeInlined() {
        throw unimplemented();
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        return wrapped.getLineNumberTable();
    }

    @Override
    public String toString() {
        return "AnalysisMethod<" + format("%H.%n") + " -> " + wrapped.toString() + ">";
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        return localVariableTable;
    }

    @Override
    public void reprofile() {
        throw unimplemented();
    }

    @Override
    public Constant getEncoding() {
        throw unimplemented();
    }

    @Override
    public boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
        return false;
    }

    @Override
    public boolean isDefault() {
        return wrapped.isDefault();
    }

    @Override
    public SpeculationLog getSpeculationLog() {
        throw shouldNotReachHere();
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public Executable getJavaMethod() {
        return OriginalMethodProvider.getJavaMethod(universe.getOriginalSnippetReflection(), wrapped);
    }

    @Override
    public boolean hasJavaMethod() {
        return OriginalMethodProvider.hasJavaMethod(universe.getOriginalSnippetReflection(), wrapped);
    }

    /**
     * Unique, per method, context insensitive invoke. The context insensitive invoke uses the
     * receiver type of the method, i.e., its declaring class. Therefore this invoke will link with
     * all possible callees.
     */
    private final AtomicReference<InvokeTypeFlow> contextInsensitiveInvoke = new AtomicReference<>();

    public InvokeTypeFlow initAndGetContextInsensitiveInvoke(PointsToAnalysis bb, BytecodePosition originalLocation) {
        if (contextInsensitiveInvoke.get() == null) {
            InvokeTypeFlow invoke = InvokeTypeFlow.createContextInsensitiveInvoke(bb, this, originalLocation);
            boolean set = contextInsensitiveInvoke.compareAndSet(null, invoke);
            if (set) {
                /*
                 * Only register the winning context insensitive invoke as an observer of the target
                 * method declaring class type flow.
                 */
                InvokeTypeFlow.initContextInsensitiveInvoke(bb, this, invoke);
            }
        }
        return contextInsensitiveInvoke.get();
    }

    public InvokeTypeFlow getContextInsensitiveInvoke() {
        InvokeTypeFlow invoke = contextInsensitiveInvoke.get();
        AnalysisError.guarantee(invoke != null);
        return invoke;
    }

    /**
     * Ensures that the method has been parsed, i.e., that the {@link StructuredGraph Graal IR} for
     * the method is available.
     */
    public AnalysisParsedGraph ensureGraphParsed(BigBang bb) {
        while (true) {
            Object curState = parsedGraphCacheState.get();

            /*-
             * This implements a state machine that ensures parsing is atomic. States: 
             * 1) unparsed: represented by the String "unparsed".
             * 2) parsing: represented by a locked ReentrantLock object that other threads can wait on.
             * 3) parsed: represented by the ParsedGraph with the parsing result
             * 4) cleared: represented by the String "cleared".
             * 5) parsing error: represented by a Throwable
             */

            if (curState == GRAPH_CACHE_UNPARSED) {
                ReentrantLock lock = new ReentrantLock();
                lock.lock();
                try {
                    /*
                     * Atomically try to claim the parsing. Note that the lock must be locked
                     * already, and remain locked until the parsing is done. Other threads will wait
                     * on this lock.
                     */
                    if (!parsedGraphCacheState.compareAndSet(GRAPH_CACHE_UNPARSED, lock)) {
                        /* We lost the race, another thread is doing the parsing. */
                        continue;
                    }

                    AnalysisParsedGraph graph = AnalysisParsedGraph.parseBytecode(bb, this);

                    /*
                     * Since we still hold the parsing lock, the transition form "parsing" to
                     * "parsed" cannot fail.
                     */
                    boolean result = parsedGraphCacheState.compareAndSet(lock, graph);
                    AnalysisError.guarantee(result, "State transition failed");

                    return graph;

                } catch (Throwable ex) {
                    parsedGraphCacheState.set(ex);
                    throw ex;

                } finally {
                    lock.unlock();
                }

            } else if (curState instanceof ReentrantLock) {
                ReentrantLock lock = (ReentrantLock) curState;
                AnalysisError.guarantee(!lock.isHeldByCurrentThread(), "Recursive parsing request, would lead to endless waiting loop");

                lock.lock();
                /*
                 * When we can acquire the lock, parsing has finished. The next loop iteration will
                 * return the result.
                 */
                AnalysisError.guarantee(parsedGraphCacheState.get() != lock, "Parsing must have finished in the thread that installed the lock");
                lock.unlock();

            } else if (curState instanceof AnalysisParsedGraph) {
                return (AnalysisParsedGraph) curState;

            } else if (curState instanceof Throwable) {
                throw AnalysisError.shouldNotReachHere("parsing had failed in another thread", (Throwable) curState);

            } else if (curState == GRAPH_CACHE_CLEARED) {
                return null;

            } else {
                throw AnalysisError.shouldNotReachHere("Unknown state: " + curState);
            }
        }
    }

    /**
     * Returns the {@link StructuredGraph Graal IR} for the method that has been processed by the
     * static analysis.
     */
    public StructuredGraph getAnalyzedGraph() {
        return analyzedGraph;
    }

    public void setAnalyzedGraph(StructuredGraph analyzedGraph) {
        this.analyzedGraph = analyzedGraph;
    }
}
